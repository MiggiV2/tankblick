package de.mymiggi.tankblick.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import de.mymiggi.tankblick.MainDispatcherRule
import de.mymiggi.tankblick.data.prefs.ApiKeyStore
import de.mymiggi.tankblick.data.prefs.FakeSecretCipher
import de.mymiggi.tankblick.data.prefs.SettingsStore
import de.mymiggi.tankblick.domain.ApiKey
import de.mymiggi.tankblick.domain.ColorSchemePreference
import de.mymiggi.tankblick.domain.DarkModePreference
import de.mymiggi.tankblick.navapp.NavApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val keyText = "d4f1a2b3-1111-4222-8333-abcdefabcdef"
    private val buildKeyText = "11111111-2222-3333-4444-555555555555"

    private lateinit var storeScope: TestScope
    private lateinit var secrets: DataStore<Preferences>
    private lateinit var apiKeyStore: ApiKeyStore
    private lateinit var settingsStore: SettingsStore
    private var installedApps = listOf(
        NavApp("app.organicmaps", "Organic Maps"),
        NavApp("net.osmand", "OsmAnd"),
    )

    @Before
    fun setUp() {
        storeScope = TestScope(UnconfinedTestDispatcher())
        secrets = PreferenceDataStoreFactory.create(scope = storeScope) {
            tempFolder.newFile("secrets.preferences_pb")
        }
        val settings: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = storeScope) {
                tempFolder.newFile("settings.preferences_pb")
            }
        apiKeyStore = ApiKeyStore(secrets, FakeSecretCipher())
        settingsStore = SettingsStore(settings)
    }

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    private suspend fun TestScope.viewModel(withKey: Boolean = true): SettingsViewModel {
        if (withKey) apiKeyStore.save(ApiKey.parse(keyText)!!)
        return SettingsViewModel(apiKeyStore, settingsStore) { installedApps }.also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.uiState.collect {}
            }
        }
    }

    /** Shoulder-surfing and screenshots both leak a key shown in full. */
    @Test
    fun `never shows the key in full`() = runTest {
        val state = viewModel().uiState.value

        assertEquals("d4f1a2b3-…-abcdefabcdef", state.maskedApiKey)
        assertFalse(state.maskedApiKey!!.contains("1111-4222-8333"))
    }

    @Test
    fun `flags the demo key so the user knows why prices look odd`() = runTest {
        apiKeyStore.save(ApiKey.parse(ApiKey.DEMO_KEY)!!)

        assertTrue(viewModel(withKey = false).uiState.value.isDemoKey)
    }

    @Test
    fun `a personal key is not flagged as the demo key`() = runTest {
        assertFalse(viewModel().uiState.value.isDemoKey)
    }

    /**
     * A key that came from the build cannot be forgotten - it is compiled in.
     * The screen needs to know so it does not offer a button that does nothing.
     */
    @Test
    fun `flags a key that came from the build`() = runTest {
        apiKeyStore = ApiKeyStore(secrets, FakeSecretCipher(), ApiKey.parse(buildKeyText)!!)

        val state = viewModel(withKey = false).uiState.value

        assertTrue(state.isBuildKey)
        assertEquals("11111111-…-555555555555", state.maskedApiKey)
    }

    @Test
    fun `does not flag the build key once the user has entered their own`() = runTest {
        apiKeyStore = ApiKeyStore(secrets, FakeSecretCipher(), ApiKey.parse(buildKeyText)!!)

        val state = viewModel().uiState.value

        assertFalse(state.isBuildKey)
        assertEquals("d4f1a2b3-…-abcdefabcdef", state.maskedApiKey)
    }

    @Test
    fun `does not flag a build key when the build had none`() = runTest {
        assertFalse(viewModel().uiState.value.isBuildKey)
    }

    @Test
    fun `lists the installed navigation apps`() = runTest {
        assertEquals(
            listOf("Organic Maps", "OsmAnd"),
            viewModel().uiState.value.navApps.map { it.label },
        )
    }

    /** Apps get installed and removed, so the list is re-queried rather than cached. */
    @Test
    fun `picks up a newly installed navigation app`() = runTest {
        val viewModel = viewModel()
        installedApps = installedApps + NavApp("com.google.android.apps.maps", "Maps")

        viewModel.refreshNavApps()

        assertEquals(3, viewModel.uiState.value.navApps.size)
    }

    @Test
    fun `remembers the chosen navigation app`() = runTest {
        val viewModel = viewModel()

        viewModel.setNavApp("app.organicmaps")

        assertEquals("app.organicmaps", viewModel.uiState.value.settings.navAppPackage)
    }

    /** Null is "ask every time" - the default, and the fallback after an uninstall. */
    @Test
    fun `clearing the navigation app means ask every time`() = runTest {
        val viewModel = viewModel()
        viewModel.setNavApp("app.organicmaps")

        viewModel.setNavApp(null)

        assertNull(viewModel.uiState.value.settings.navAppPackage)
    }

    @Test
    fun `remembers the dark mode choice`() = runTest {
        val viewModel = viewModel()

        viewModel.setDarkMode(DarkModePreference.DARK)

        assertEquals(DarkModePreference.DARK, viewModel.uiState.value.settings.darkMode)
    }

    @Test
    fun `remembers the colour scheme`() = runTest {
        val viewModel = viewModel()

        viewModel.setColorScheme(ColorSchemePreference.AMBER)

        assertEquals(ColorSchemePreference.AMBER, viewModel.uiState.value.settings.colorScheme)
    }

    @Test
    fun `remembers the radius`() = runTest {
        val viewModel = viewModel()

        viewModel.setRadiusKm(15)

        assertEquals(15, viewModel.uiState.value.settings.radiusKm)
    }

    @Test
    fun `clamps a radius the api would reject`() = runTest {
        val viewModel = viewModel()

        viewModel.setRadiusKm(99)

        assertEquals(25, viewModel.uiState.value.settings.radiusKm)
    }

    @Test
    fun `replaces the key with a new one`() = runTest {
        val viewModel = viewModel()
        val other = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"

        assertTrue(viewModel.replaceApiKey(other))

        assertEquals(other, apiKeyStore.apiKey.first()?.value)
    }

    @Test
    fun `rejects a malformed key and keeps the old one`() = runTest {
        val viewModel = viewModel()

        assertFalse(viewModel.replaceApiKey("nope"))

        assertEquals(keyText, apiKeyStore.apiKey.first()?.value)
    }

    @Test
    fun `forgetting the key sends the user back to onboarding`() = runTest {
        val viewModel = viewModel()

        viewModel.forgetApiKey()

        assertNull(apiKeyStore.apiKey.first())
    }
}
