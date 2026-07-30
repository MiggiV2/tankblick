package de.mymiggi.tankblick.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import de.mymiggi.tankblick.MainDispatcherRule
import de.mymiggi.tankblick.data.prefs.ApiKeyStore
import de.mymiggi.tankblick.data.prefs.FakeSecretCipher
import de.mymiggi.tankblick.domain.ApiKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val key = ApiKey.parse("d4f1a2b3-1111-4222-8333-abcdefabcdef")!!

    private lateinit var storeScope: TestScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var apiKeyStore: ApiKeyStore
    private lateinit var cipher: FakeSecretCipher

    @Before
    fun setUp() {
        storeScope = TestScope(UnconfinedTestDispatcher())
        dataStore = PreferenceDataStoreFactory.create(scope = storeScope) {
            tempFolder.newFile("secrets.preferences_pb")
        }
        cipher = FakeSecretCipher()
        apiKeyStore = ApiKeyStore(dataStore, cipher)
    }

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    /** Reading DataStore is asynchronous, so the UI must not flash onboarding first. */
    @Test
    fun `starts in loading so onboarding does not flash for an existing user`() {
        assertEquals(RootUiState.Loading, RootViewModel(apiKeyStore).uiState.value)
    }

    @Test
    fun `asks for a key when none is stored`() = runTest {
        val viewModel = RootViewModel(apiKeyStore)

        assertEquals(RootUiState.NeedsApiKey, viewModel.uiState.first { it != RootUiState.Loading })
    }

    @Test
    fun `is ready once a key is stored`() = runTest {
        apiKeyStore.save(key)

        val viewModel = RootViewModel(apiKeyStore)

        assertEquals(RootUiState.Ready, viewModel.uiState.first { it != RootUiState.Loading })
    }

    /** A wiped Keystore key must send the user back to onboarding, not to a broken screen. */
    @Test
    fun `falls back to onboarding when the stored key becomes unreadable`() = runTest {
        apiKeyStore.save(key)
        cipher.failDecryption = true

        val viewModel = RootViewModel(apiKeyStore)

        assertEquals(RootUiState.NeedsApiKey, viewModel.uiState.first { it != RootUiState.Loading })
    }
}
