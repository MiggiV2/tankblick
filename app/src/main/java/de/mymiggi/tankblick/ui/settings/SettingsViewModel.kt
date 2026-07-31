package de.mymiggi.tankblick.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mymiggi.tankblick.data.prefs.ApiKeyStore
import de.mymiggi.tankblick.data.prefs.Settings
import de.mymiggi.tankblick.data.prefs.SettingsStore
import de.mymiggi.tankblick.domain.ApiKey
import de.mymiggi.tankblick.domain.ColorSchemePreference
import de.mymiggi.tankblick.domain.DarkModePreference
import de.mymiggi.tankblick.navapp.NavApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: Settings = Settings(),
    /** Masked, so the key cannot be read off a shoulder or a screenshot. */
    val maskedApiKey: String? = null,
    val isDemoKey: Boolean = false,
    val navApps: List<NavApp> = emptyList(),
)

class SettingsViewModel(
    private val apiKeyStore: ApiKeyStore,
    private val settingsStore: SettingsStore,
    /** Queried lazily: apps come and go, so the list is not cached. */
    private val loadNavApps: () -> List<NavApp>,
) : ViewModel() {

    private val navApps = MutableStateFlow<List<NavApp>>(emptyList())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsStore.settings,
        apiKeyStore.apiKey,
        navApps,
    ) { settings, apiKey, apps ->
        SettingsUiState(
            settings = settings,
            maskedApiKey = apiKey?.masked(),
            isDemoKey = apiKey?.isDemo == true,
            navApps = apps,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(),
    )

    init {
        refreshNavApps()
    }

    /** Re-queried on every visit, because apps get installed and removed. */
    fun refreshNavApps() {
        navApps.value = loadNavApps()
    }

    fun setDarkMode(darkMode: DarkModePreference) {
        viewModelScope.launch { settingsStore.setDarkMode(darkMode) }
    }

    fun setColorScheme(colorScheme: ColorSchemePreference) {
        viewModelScope.launch { settingsStore.setColorScheme(colorScheme) }
    }

    fun setRadiusKm(radiusKm: Int) {
        viewModelScope.launch { settingsStore.setRadiusKm(radiusKm) }
    }

    /** `null` means "ask every time", which is also the fallback for an uninstalled app. */
    fun setNavApp(packageName: String?) {
        viewModelScope.launch { settingsStore.setNavAppPackage(packageName) }
    }

    /** @return false if the key was rejected, in which case nothing was stored. */
    suspend fun replaceApiKey(raw: String): Boolean {
        val key = ApiKey.parse(raw) ?: return false
        apiKeyStore.save(key)
        return true
    }

    /** Sends the user back to onboarding. */
    fun forgetApiKey() {
        viewModelScope.launch { apiKeyStore.clear() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
