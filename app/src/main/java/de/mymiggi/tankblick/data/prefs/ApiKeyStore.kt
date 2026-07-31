package de.mymiggi.tankblick.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.mymiggi.tankblick.domain.ApiKey
import kotlinx.coroutines.flow.Flow
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

    /** The key to make requests with: the user's own, otherwise [buildKey]. */
    val apiKey: Flow<ApiKey?> = userApiKey.map { it ?: buildKey }

    suspend fun save(key: ApiKey) {
        dataStore.edit { it[API_KEY] = cipher.encrypt(key.value) }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(API_KEY) }
    }

    companion object {
        val API_KEY = stringPreferencesKey("api_key")
    }
}
