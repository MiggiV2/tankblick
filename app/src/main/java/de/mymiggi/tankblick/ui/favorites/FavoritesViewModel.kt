package de.mymiggi.tankblick.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mymiggi.tankblick.data.prefs.ApiKeyStore
import de.mymiggi.tankblick.data.prefs.SettingsStore
import de.mymiggi.tankblick.data.remote.ApiResult
import de.mymiggi.tankblick.data.repo.StationRepository
import de.mymiggi.tankblick.domain.FuelType
import de.mymiggi.tankblick.domain.Station
import de.mymiggi.tankblick.ui.nearby.NearbyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val stations: List<Station> = emptyList(),
    val fuelType: FuelType = FuelType.E10,
    val isRefreshing: Boolean = false,
    val message: NearbyMessage? = null,
    val lastUpdatedAt: Long? = null,
)

/**
 * The stations someone actually cares about.
 *
 * Refreshing these costs a single request no matter how many there are: the
 * API takes ten ids at a time and the client chunks anything longer. Ordering
 * is the order they were added, not by price - a favourites list that
 * rearranged itself would be useless for the "is my usual station cheap today"
 * question it exists to answer.
 */
class FavoritesViewModel(
    private val stationRepository: StationRepository,
    private val apiKeyStore: ApiKeyStore,
    settingsStore: SettingsStore,
) : ViewModel() {

    private val transientState = MutableStateFlow(TransientState())

    val uiState: StateFlow<FavoritesUiState> = combine(
        stationRepository.observeFavorites(),
        settingsStore.settings,
        transientState,
    ) { stations, settings, transient ->
        FavoritesUiState(
            stations = stations,
            fuelType = settings.fuelType,
            isRefreshing = transient.isRefreshing,
            message = transient.message,
            lastUpdatedAt = stations.minOfOrNull { it.fetchedAt },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = FavoritesUiState(),
    )

    fun refresh() {
        if (transientState.value.isRefreshing) return

        viewModelScope.launch {
            transientState.value = TransientState(isRefreshing = true)

            val apiKey = apiKeyStore.apiKey.first()
            if (apiKey == null) {
                transientState.value = TransientState(message = NearbyMessage.MissingApiKey)
                return@launch
            }

            val result = stationRepository.refreshFavorites(apiKey)
            if (result is ApiResult.InvalidKey) apiKeyStore.reportRejected(apiKey)

            transientState.value = TransientState(message = result.toMessage())
        }
    }

    fun toggleFavorite(stationId: String) {
        viewModelScope.launch { stationRepository.toggleFavorite(stationId) }
    }

    fun dismissMessage() {
        transientState.value = transientState.value.copy(message = null)
    }

    private fun ApiResult<Int>.toMessage(): NearbyMessage? = when (this) {
        // Zero updated with an empty list is not a problem worth a banner.
        is ApiResult.Success -> null
        is ApiResult.RateLimited -> NearbyMessage.RateLimited(retryInSeconds)
        ApiResult.InvalidKey -> NearbyMessage.InvalidKey
        ApiResult.Offline -> NearbyMessage.Offline
        is ApiResult.ServerError -> NearbyMessage.ServerError(statusCode)
        is ApiResult.ApiError -> NearbyMessage.Failed(message)
        ApiResult.MalformedResponse -> NearbyMessage.Failed(null)
    }

    private data class TransientState(
        val isRefreshing: Boolean = false,
        val message: NearbyMessage? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
