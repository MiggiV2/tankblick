package de.mymiggi.tankblick.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mymiggi.tankblick.data.prefs.ApiKeyStore
import de.mymiggi.tankblick.domain.ApiKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val input: String = "",
    val showInvalidKeyError: Boolean = false,
    /** This screen is only back because the key shipped with the build died. */
    val buildKeyRejected: Boolean = false,
)

class OnboardingViewModel(
    private val apiKeyStore: ApiKeyStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            apiKeyStore.buildKeyRejected.collect { rejected ->
                _uiState.update { it.copy(buildKeyRejected = rejected) }
            }
        }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value, showInvalidKeyError = false) }
    }

    fun onSubmit() {
        val key = ApiKey.parse(_uiState.value.input)
        if (key == null) {
            _uiState.update { it.copy(showInvalidKeyError = true) }
            return
        }
        store(key)
    }

    fun onUseDemoKey() {
        store(ApiKey.parse(ApiKey.DEMO_KEY)!!)
    }

    private fun store(key: ApiKey) {
        viewModelScope.launch { apiKeyStore.save(key) }
    }
}
