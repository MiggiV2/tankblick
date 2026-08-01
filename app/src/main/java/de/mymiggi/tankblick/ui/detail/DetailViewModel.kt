package de.mymiggi.tankblick.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mymiggi.tankblick.data.prefs.ApiKeyStore
import de.mymiggi.tankblick.data.remote.ApiResult
import de.mymiggi.tankblick.data.repo.StationRepository
import de.mymiggi.tankblick.domain.OpeningEntry
import de.mymiggi.tankblick.domain.OpeningHours
import de.mymiggi.tankblick.domain.Station
import de.mymiggi.tankblick.ui.nearby.NearbyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val station: Station? = null,
    val openingHours: OpeningHours? = null,
    val isLoading: Boolean = false,
    val message: NearbyMessage? = null,
)

/**
 * One station in full.
 *
 * Opens from the cache immediately - the list already has name, address and
 * prices - and asks the API only for what the list does not carry: opening
 * hours. Waiting on a request to show data we already have would be a worse
 * screen and a wasted call.
 */
class DetailViewModel(
    private val stationId: String,
    private val stationRepository: StationRepository,
    private val apiKeyStore: ApiKeyStore,
) : ViewModel() {

    private val transientState = MutableStateFlow(TransientState())

    val uiState: StateFlow<DetailUiState> = combine(
        stationRepository.observeStation(stationId),
        transientState,
    ) { station, transient ->
        DetailUiState(
            station = station,
            openingHours = transient.openingHours,
            isLoading = transient.isLoading,
            message = transient.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DetailUiState(isLoading = true),
    )

    init {
        loadDetails()
    }

    fun loadDetails() {
        if (transientState.value.isLoading) return

        viewModelScope.launch {
            transientState.value = transientState.value.copy(isLoading = true, message = null)

            val apiKey = apiKeyStore.apiKey.first()
            if (apiKey == null) {
                transientState.value = TransientState(message = NearbyMessage.MissingApiKey)
                return@launch
            }

            when (val result = stationRepository.refreshStationDetail(apiKey, stationId)) {
                is ApiResult.Success -> {
                    val detail = result.value
                    transientState.value = TransientState(
                        openingHours = OpeningHours.of(
                            wholeDay = detail.wholeDay,
                            entries = detail.openingTimes.map {
                                OpeningEntry(it.text, it.start, it.end)
                            },
                            overrides = detail.overrides,
                        ),
                    )
                }

                // The cached station stays on screen; only the hours are missing.
                else -> {
                    if (result is ApiResult.InvalidKey) apiKeyStore.reportRejected(apiKey)
                    transientState.value = TransientState(message = result.toMessage())
                }
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch { stationRepository.toggleFavorite(stationId) }
    }

    fun setLabel(label: String?) {
        viewModelScope.launch { stationRepository.setFavoriteLabel(stationId, label) }
    }

    fun dismissMessage() {
        transientState.value = transientState.value.copy(message = null)
    }

    private fun ApiResult<*>.toMessage(): NearbyMessage? = when (this) {
        is ApiResult.Success -> null
        is ApiResult.RateLimited -> NearbyMessage.RateLimited(retryInSeconds)
        ApiResult.InvalidKey -> NearbyMessage.InvalidKey
        ApiResult.Offline -> NearbyMessage.Offline
        is ApiResult.ServerError -> NearbyMessage.ServerError(statusCode)
        is ApiResult.ApiError -> NearbyMessage.Failed(message)
        ApiResult.MalformedResponse -> NearbyMessage.Failed(null)
    }

    private data class TransientState(
        val openingHours: OpeningHours? = null,
        val isLoading: Boolean = false,
        val message: NearbyMessage? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
