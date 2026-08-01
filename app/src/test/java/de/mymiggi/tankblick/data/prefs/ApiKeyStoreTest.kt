package de.mymiggi.tankblick.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.mymiggi.tankblick.domain.ApiKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ApiKeyStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val validKey = ApiKey.parse("d4f1a2b3-1111-4222-8333-abcdefabcdef")!!
    private val buildKey = ApiKey.parse("11111111-2222-3333-4444-555555555555")!!

    private lateinit var scope: TestScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var cipher: FakeSecretCipher
    private lateinit var store: ApiKeyStore

    @Before
    fun setUp() {
        scope = TestScope(UnconfinedTestDispatcher())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            tempFolder.newFile("secrets.preferences_pb")
        }
        cipher = FakeSecretCipher()
        store = ApiKeyStore(dataStore, cipher)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `returns null when no key has been stored`() = runTest {
        assertNull(store.apiKey.first())
    }

    @Test
    fun `stores and reads a key back`() = runTest {
        store.save(validKey)

        assertEquals(validKey, store.apiKey.first())
    }

    @Test
    fun `never writes the key in plaintext`() = runTest {
        store.save(validKey)

        val raw = dataStore.data.first()[stringPreferencesKey("api_key")]
        assertNotEquals(validKey.value, raw)
        assertEquals(cipher.encrypt(validKey.value), raw)
    }

    @Test
    fun `overwriting replaces the previous key`() = runTest {
        val other = ApiKey.parse("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")!!

        store.save(validKey)
        store.save(other)

        assertEquals(other, store.apiKey.first())
    }

    @Test
    fun `clear removes the key`() = runTest {
        store.save(validKey)

        store.clear()

        assertNull(store.apiKey.first())
        assertNull(dataStore.data.first()[stringPreferencesKey("api_key")])
    }

    /**
     * Changing the lock screen can wipe the Keystore key, which leaves an
     * undecryptable blob behind. That must surface as "no key" so the user is
     * sent back to onboarding, never as a crash.
     */
    @Test
    fun `reports no key when decryption fails`() = runTest {
        store.save(validKey)
        cipher.failDecryption = true

        assertNull(store.apiKey.first())
    }

    @Test
    fun `discards the stored blob once it can no longer be decrypted`() = runTest {
        store.save(validKey)
        cipher.failDecryption = true

        store.apiKey.first()

        assertNull(dataStore.data.first()[stringPreferencesKey("api_key")])
    }

    /** A decrypted value that is no longer a valid key is just as unusable. */
    @Test
    fun `reports no key when the decrypted value is not a valid api key`() = runTest {
        dataStore.edit { it[stringPreferencesKey("api_key")] = cipher.encrypt("garbage") }

        assertNull(store.apiKey.first())
        assertNull(dataStore.data.first()[stringPreferencesKey("api_key")])
    }

    @Test
    fun `falls back to the key baked into the build`() = runTest {
        val store = ApiKeyStore(dataStore, cipher, buildKey)

        assertEquals(buildKey, store.apiKey.first())
    }

    /** A key the user entered is theirs; it must win over whatever was shipped. */
    @Test
    fun `prefers the stored key over the one baked into the build`() = runTest {
        val store = ApiKeyStore(dataStore, cipher, buildKey)
        store.save(validKey)

        assertEquals(validKey, store.apiKey.first())
    }

    @Test
    fun `falls back to the build key again after the stored key is cleared`() = runTest {
        val store = ApiKeyStore(dataStore, cipher, buildKey)
        store.save(validKey)

        store.clear()

        assertEquals(buildKey, store.apiKey.first())
    }

    /** An unreadable blob must not strand a build that ships a usable key. */
    @Test
    fun `falls back to the build key when the stored key cannot be decrypted`() = runTest {
        val store = ApiKeyStore(dataStore, cipher, buildKey)
        store.save(validKey)
        cipher.failDecryption = true

        assertEquals(buildKey, store.apiKey.first())
    }

    /**
     * The settings screen has to tell the two apart: a build key cannot be
     * forgotten, and offering to would be a button that does nothing.
     */
    @Test
    fun `reports only the stored key as the user's own`() = runTest {
        val store = ApiKeyStore(dataStore, cipher, buildKey)

        assertNull(store.userApiKey.first())

        store.save(validKey)
        assertEquals(validKey, store.userApiKey.first())
    }

    /**
     * A build key the API has stopped accepting leaves the app with nothing to
     * work with, and nobody can fix it from inside: the user never entered it
     * and cannot replace what they were not asked for. Forgetting it is what
     * puts onboarding back in front of them.
     */
    @Test
    fun `stops using the build key once the api rejects it`() = runTest {
        val store = ApiKeyStore(dataStore, cipher, buildKey)

        store.reportRejected(buildKey)

        assertNull(store.apiKey.first())
    }

    /** A rejection of the user's own key is theirs to fix, so it stays. */
    @Test
    fun `keeps the stored key when the api rejects it`() = runTest {
        val store = ApiKeyStore(dataStore, cipher, buildKey)
        store.save(validKey)

        store.reportRejected(validKey)

        assertEquals(validKey, store.apiKey.first())
    }

    @Test
    fun `uses the key the user enters after the build key was rejected`() = runTest {
        val store = ApiKeyStore(dataStore, cipher, buildKey)
        store.reportRejected(buildKey)

        store.save(validKey)

        assertEquals(validKey, store.apiKey.first())
    }

    /**
     * The rejection is pinned to the key that failed rather than to "this build
     * has a key". An update shipping a working one must not stay locked out by
     * a flag its predecessor wrote.
     */
    @Test
    fun `a newer build key is unaffected by an earlier rejection`() = runTest {
        ApiKeyStore(dataStore, cipher, buildKey).reportRejected(buildKey)

        val updated = ApiKeyStore(dataStore, cipher, validKey)

        assertEquals(validKey, updated.apiKey.first())
    }

    /** Nothing to forget, and nothing that should disturb a working key. */
    @Test
    fun `ignores a rejection for a key the app is not using`() = runTest {
        val store = ApiKeyStore(dataStore, cipher, buildKey)
        val stranger = ApiKey.parse("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")!!

        store.reportRejected(stranger)

        assertEquals(buildKey, store.apiKey.first())
    }
}
