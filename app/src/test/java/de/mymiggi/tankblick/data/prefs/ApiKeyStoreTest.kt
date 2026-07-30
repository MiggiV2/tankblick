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
}
