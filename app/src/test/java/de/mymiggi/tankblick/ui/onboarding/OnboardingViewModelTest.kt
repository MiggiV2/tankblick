package de.mymiggi.tankblick.ui.onboarding

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val validKeyText = "d4f1a2b3-1111-4222-8333-abcdefabcdef"

    private lateinit var storeScope: TestScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var apiKeyStore: ApiKeyStore
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        storeScope = TestScope(UnconfinedTestDispatcher())
        dataStore = PreferenceDataStoreFactory.create(scope = storeScope) {
            tempFolder.newFile("secrets.preferences_pb")
        }
        apiKeyStore = ApiKeyStore(dataStore, FakeSecretCipher())
        viewModel = OnboardingViewModel(apiKeyStore)
    }

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    @Test
    fun `starts empty and without an error`() {
        val state = viewModel.uiState.value

        assertEquals("", state.input)
        assertFalse(state.showInvalidKeyError)
    }

    @Test
    fun `typing updates the input`() {
        viewModel.onInputChange("abc")

        assertEquals("abc", viewModel.uiState.value.input)
    }

    @Test
    fun `rejects a malformed key and stores nothing`() = runTest {
        viewModel.onInputChange("not-a-key")

        viewModel.onSubmit()

        assertTrue(viewModel.uiState.value.showInvalidKeyError)
        assertNull(apiKeyStore.apiKey.first())
    }

    @Test
    fun `clears the error as soon as the user edits the input again`() {
        viewModel.onInputChange("not-a-key")
        viewModel.onSubmit()

        viewModel.onInputChange("not-a-key!")

        assertFalse(viewModel.uiState.value.showInvalidKeyError)
    }

    @Test
    fun `stores a valid key`() = runTest {
        viewModel.onInputChange(validKeyText)

        viewModel.onSubmit()

        assertEquals(ApiKey.parse(validKeyText), apiKeyStore.apiKey.first())
        assertFalse(viewModel.uiState.value.showInvalidKeyError)
    }

    /** Keys arrive by copy-paste from a confirmation mail, often with stray whitespace. */
    @Test
    fun `accepts a pasted key with surrounding whitespace`() = runTest {
        viewModel.onInputChange("  $validKeyText\n")

        viewModel.onSubmit()

        assertEquals(ApiKey.parse(validKeyText), apiKeyStore.apiKey.first())
    }

    @Test
    fun `submitting an empty input reports an error rather than storing nothing silently`() =
        runTest {
            viewModel.onSubmit()

            assertTrue(viewModel.uiState.value.showInvalidKeyError)
            assertNull(apiKeyStore.apiKey.first())
        }

    /** Personal keys are reviewed by hand and can take days, so the demo key is a way in. */
    @Test
    fun `stores the demo key on request`() = runTest {
        viewModel.onUseDemoKey()

        val stored = apiKeyStore.apiKey.first()
        assertEquals(ApiKey.DEMO_KEY, stored?.value)
        assertTrue(stored!!.isDemo)
    }
}
