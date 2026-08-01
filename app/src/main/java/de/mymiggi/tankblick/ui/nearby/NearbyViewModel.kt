package de.mymiggi.tankblick.ui.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mymiggi.tankblick.data.prefs.ApiKeyStore
import de.mymiggi.tankblick.data.prefs.Settings
import de.mymiggi.tankblick.data.prefs.SettingsStore
import de.mymiggi.tankblick.data.remote.ApiResult
import de.mymiggi.tankblick.data.repo.StationRepository
import de.mymiggi.tankblick.domain.FuelType
import de.mymiggi.tankblick.domain.SortMode
import de.mymiggi.tankblick.domain.Station
import de.mymiggi.tankblick.location.LocationResult
import de.mymiggi.tankblick.location.LocationSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Something the user needs to know about, shown as a banner above the list. */
sealed interface NearbyMessage {

    data object MissingApiKey : NearbyMessage
    data object NeedsLocationPermission : NearbyMessage
    data object LocationDisabled : NearbyMessage
    data object LocationUnavailable : NearbyMessage
    data object NoResults : NearbyMessage
    data object Offline : NearbyMessage
    data object InvalidKey : NearbyMessage
    data class RateLimited(val retryInSeconds: Long) : NearbyMessage
    data class ServerError(val statusCode: Int) : NearbyMessage
    data class Failed(val detail: String?) : NearbyMessage
}

data class NearbyUiState(
    val stations: List<Station> = emptyList(),
    val fuelType: FuelType = FuelType.E10,
    val radiusKm: Int = Settings.DEFAULT_RADIUS_KM,
    val sortMode: SortMode = SortMode.PRICE,
    val isRefreshing: Boolean = false,
    val message: NearbyMessage? = null,
    /** Epoch millis of the oldest price on screen, or null when there is nothing to show. */
    val lastUpdatedAt: Long? = null,
)

class NearbyViewModel(
    private val stationRepository: StationRepository,
    private val apiKeyStore: ApiKeyStore,
    private val settingsStore: SettingsStore,
    private val locationSource: LocationSource,
) : ViewModel() {

    private val transientState = MutableStateFlow(TransientState())

    val uiState: StateFlow<NearbyUiState> = combine(
        stationRepository.observeNearby(),
        settingsStore.settings,
        transientState,
    ) { stations, settings, transient ->
        NearbyUiState(
            stations = stations.sortedFor(settings.fuelType, settings.sortMode),
            fuelType = settings.fuelType,
            radiusKm = settings.radiusKm,
            sortMode = settings.sortMode,
            isRefreshing = transient.isRefreshing,
            message = transient.message,
            lastUpdatedAt = stations.minOfOrNull { it.fetchedAt },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = NearbyUiState(),
    )

    /**
     * Fetches a position and a fresh result set.
     *
     * The only place in the nearby screen that touches the network. Changing
     * the fuel type or the sort order deliberately does not: one response
     * carries all three prices, and spending a request on a re-sort would burn
     * a minute of the user's rate-limit budget for nothing.
     */
    fun refresh() {
        if (transientState.value.isRefreshing) return

        viewModelScope.launch {
            transientState.value = TransientState(isRefreshing = true)

            val apiKey = apiKeyStore.apiKey.first()
            if (apiKey == null) {
                transientState.value = TransientState(message = NearbyMessage.MissingApiKey)
                return@launch
            }

            // A provider that never calls back would otherwise leave the
            // screen refreshing forever, and refresh() ignores further taps
            // while one is running - the button would stay dead until restart.
            val location = withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
                locationSource.currentLocation()
            } ?: LocationResult.Unavailable

            if (location !is LocationResult.Available) {
                transientState.value = TransientState(message = location.toMessage())
                return@launch
            }

            val settings = settingsStore.settings.first()
            val result = stationRepository.refreshNearby(
                apiKey = apiKey,
                latitude = location.latitude,
                longitude = location.longitude,
                radiusKm = settings.radiusKm,
            )

            if (result is ApiResult.InvalidKey) apiKeyStore.reportRejected(apiKey)

            transientState.value = TransientState(message = result.toMessage())
        }
    }

    fun setFuelType(fuelType: FuelType) {
        viewModelScope.launch { settingsStore.setFuelType(fuelType) }
    }

    fun setSortMode(sortMode: SortMode) {
        viewModelScope.launch { settingsStore.setSortMode(sortMode) }
    }

    /** Takes effect on the next refresh; the cached result was fetched at the old radius. */
    fun setRadiusKm(radiusKm: Int) {
        viewModelScope.launch { settingsStore.setRadiusKm(radiusKm) }
    }

    fun toggleFavorite(stationId: String) {
        viewModelScope.launch { stationRepository.toggleFavorite(stationId) }
    }

    fun dismissMessage() {
        transientState.value = transientState.value.copy(message = null)
    }

    private fun List<Station>.sortedFor(fuelType: FuelType, sortMode: SortMode): List<Station> =
        when (sortMode) {
            // Stations without a price for the chosen fuel go last: they are
            // useless at the top of a list the user is reading for prices.
            SortMode.PRICE -> sortedWith(
                compareBy(nullsLast()) { it.prices[fuelType] },
            )

            SortMode.DISTANCE -> sortedWith(compareBy(nullsLast()) { it.distanceKm })
        }

    private fun LocationResult.toMessage(): NearbyMessage = when (this) {
        is LocationResult.Available -> error("a position is not a message")
        LocationResult.PermissionMissing -> NearbyMessage.NeedsLocationPermission
        LocationResult.LocationDisabled -> NearbyMessage.LocationDisabled
        LocationResult.Unavailable -> NearbyMessage.LocationUnavailable
    }

    private fun ApiResult<Int>.toMessage(): NearbyMessage? = when (this) {
        is ApiResult.Success -> if (value == 0) NearbyMessage.NoResults else null
        is ApiResult.RateLimited -> NearbyMessage.RateLimited(retryInSeconds)
        ApiResult.InvalidKey -> NearbyMessage.InvalidKey
        ApiResult.Offline -> NearbyMessage.Offline
        is ApiResult.ServerError -> NearbyMessage.ServerError(statusCode)
        is ApiResult.ApiError -> NearbyMessage.Failed(message)
        ApiResult.MalformedResponse -> NearbyMessage.Failed(null)
    }

    /** State that belongs to this screen rather than to the cache or the settings. */
    private data class TransientState(
        val isRefreshing: Boolean = false,
        val message: NearbyMessage? = null,
    )

    companion object {
        /**
         * Long enough for a cold GPS fix on a bad day, short enough that a
         * silent provider does not look like a frozen app.
         */
        const val LOCATION_TIMEOUT_MILLIS = 20_000L

        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
