package de.mymiggi.tankblick.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.mymiggi.tankblick.domain.ApiKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Persists the user's Tankerkönig API key, encrypted with [cipher].
 *
 * The DataStore file this writes to is excluded from cloud backup and device
 * transfer (see res/xml/data_extraction_rules.xml): the ciphertext is bound to
 * a Keystore key that only exists on this device, so copying it elsewhere would
 * produce something undecryptable.
 *
 * [buildKey] is the key baked in at build time, if the build had one. It is not
 * encrypted and not stored here: it lives in BuildConfig, where anyone with the
 * APK can read it anyway, so this class only ever falls back to it.
 */
class ApiKeyStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    private val buildKey: ApiKey? = null,
) {

    /**
     * The key the user entered, or `null` if there is none or it can no longer
     * be read. Ignores [buildKey], so callers can tell whose key is in play -
     * only this one can be replaced or forgotten.
     *
     * An unreadable blob is dropped as it is encountered. Keeping it would mean
     * retrying a doomed decryption on every read, and it can never become
     * valid again once the Keystore key is gone.
     */
    val userApiKey: Flow<ApiKey?> = dataStore.data
        .map { it[API_KEY] }
        .distinctUntilChanged()
        .map { stored ->
            if (stored == null) return@map null

            val key = cipher.decrypt(stored)?.let(ApiKey::parse)
            if (key == null) clear()
            key
        }

    /**
     * [buildKey], unless the API has already refused it.
     *
     * The rejection is remembered against the key's value rather than as a bare
     * flag, so a later version that ships a working key is not locked out by
     * something its predecessor wrote.
     */
    private val usableBuildKey: Flow<ApiKey?> = dataStore.data
        .map { it[REJECTED_BUILD_KEY] }
        .distinctUntilChanged()
        .map { rejected -> buildKey?.takeIf { it.value != rejected } }

    /** The key to make requests with: the user's own, otherwise [buildKey]. */
    val apiKey: Flow<ApiKey?> = combine(userApiKey, usableBuildKey) { user, build -> user ?: build }

    /** True once [buildKey] has been refused, so onboarding can say why it is back. */
    val buildKeyRejected: Flow<Boolean> = usableBuildKey.map { buildKey != null && it == null }

    suspend fun save(key: ApiKey) {
        dataStore.edit { it[API_KEY] = cipher.encrypt(key.value) }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(API_KEY) }
    }

    /**
     * Records that Tankerkönig refused [key].
     *
     * Only [buildKey] is acted on. It is the one the user never chose and
     * cannot replace from the settings screen, so forgetting it is what sends
     * them to onboarding instead of leaving them with a banner and no way out.
     * A rejected key they typed themselves stays: that one they can correct.
     */
    suspend fun reportRejected(key: ApiKey) {
        if (buildKey == null || key != buildKey) return
        dataStore.edit { it[REJECTED_BUILD_KEY] = key.value }
    }

    companion object {
        val API_KEY = stringPreferencesKey("api_key")

        /** The build key that stopped working, kept in plaintext - it is public anyway. */
        val REJECTED_BUILD_KEY = stringPreferencesKey("rejected_build_key")
    }
}
