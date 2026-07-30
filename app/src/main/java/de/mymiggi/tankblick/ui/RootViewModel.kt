package de.mymiggi.tankblick.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mymiggi.tankblick.data.prefs.ApiKeyStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Whether the app can do anything yet, which comes down to having an API key. */
enum class RootUiState {
    /** Still reading the key store; showing nothing beats flashing onboarding. */
    Loading,

    NeedsApiKey,
    Ready,
}

class RootViewModel(
    apiKeyStore: ApiKeyStore,
) : ViewModel() {

    val uiState: StateFlow<RootUiState> = apiKeyStore.apiKey
        .map { if (it == null) RootUiState.NeedsApiKey else RootUiState.Ready }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = RootUiState.Loading,
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
