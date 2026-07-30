package de.mymiggi.tankblick.data.repo

import de.mymiggi.tankblick.data.remote.ApiResult
import de.mymiggi.tankblick.data.remote.dto.StationDetailDto
import de.mymiggi.tankblick.domain.ApiKey
import de.mymiggi.tankblick.domain.FuelType
import de.mymiggi.tankblick.domain.SortMode
import de.mymiggi.tankblick.domain.Station
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory stand-in so ViewModel tests need neither SQLite nor a device. */
class FakeStationRepository : StationRepository {

    val nearby = MutableStateFlow<List<Station>>(emptyList())
    val favorites = MutableStateFlow<List<Station>>(emptyList())

    var nearbyResult: ApiResult<Int> = ApiResult.Success(0)
    var favoritesResult: ApiResult<Int> = ApiResult.Success(0)
    var detailResult: ApiResult<StationDetailDto> = ApiResult.MalformedResponse

    /** Every refreshNearby call, so tests can assert what was requested - and what was not. */
    val nearbyRequests = mutableListOf<NearbyRequest>()

    data class NearbyRequest(
        val latitude: Double,
        val longitude: Double,
        val radiusKm: Int,
    )

    override fun observeNearby(): Flow<List<Station>> = nearby

    override fun observeFavorites(): Flow<List<Station>> = favorites

    override fun observeStation(stationId: String): Flow<Station?> =
        nearby.map { stations -> stations.firstOrNull { it.id == stationId } }

    override suspend fun refreshNearby(
        apiKey: ApiKey,
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
    ): ApiResult<Int> {
        nearbyRequests += NearbyRequest(latitude, longitude, radiusKm)
        return nearbyResult
    }

    override suspend fun refreshFavorites(apiKey: ApiKey): ApiResult<Int> = favoritesResult

    override suspend fun refreshStationDetail(
        apiKey: ApiKey,
        stationId: String,
    ): ApiResult<StationDetailDto> = detailResult

    override suspend fun toggleFavorite(stationId: String): Boolean {
        val updated = nearby.value.map {
            if (it.id == stationId) it.copy(isFavorite = !it.isFavorite) else it
        }
        nearby.value = updated
        favorites.value = updated.filter { it.isFavorite }
        return updated.first { it.id == stationId }.isFavorite
    }

    override suspend fun setFavoriteLabel(stationId: String, label: String?) {
        nearby.value = nearby.value.map {
            if (it.id == stationId) it.copy(favoriteLabel = label) else it
        }
    }

    override suspend fun purgeOldSnapshots(retentionDays: Int): Int = 0
}
