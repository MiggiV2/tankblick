package de.mymiggi.tankblick.data.repo

import de.mymiggi.tankblick.data.remote.ApiResult
import de.mymiggi.tankblick.data.remote.dto.StationDetailDto
import de.mymiggi.tankblick.domain.ApiKey
import de.mymiggi.tankblick.domain.FuelType
import de.mymiggi.tankblick.domain.SortMode
import de.mymiggi.tankblick.domain.Station
import kotlinx.coroutines.flow.Flow

/**
 * What the app needs from station storage.
 *
 * An interface rather than the class directly, so ViewModels can be tested on
 * the JVM in milliseconds instead of needing a device for SQLite.
 * [DefaultStationRepository] is the only production implementation.
 */
interface StationRepository {

    fun observeNearby(): Flow<List<Station>>

    fun observeFavorites(): Flow<List<Station>>

    fun observeStation(stationId: String): Flow<Station?>

    /** Replaces the cached nearby result. @return the number of stations found. */
    suspend fun refreshNearby(
        apiKey: ApiKey,
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
    ): ApiResult<Int>

    /** Updates prices for every favourite. @return how many were updated. */
    suspend fun refreshFavorites(apiKey: ApiKey): ApiResult<Int>

    suspend fun refreshStationDetail(apiKey: ApiKey, stationId: String): ApiResult<StationDetailDto>

    /** @return true if the station is a favourite afterwards. */
    suspend fun toggleFavorite(stationId: String): Boolean

    suspend fun setFavoriteLabel(stationId: String, label: String?)

    /** @return how many price snapshots were removed. */
    suspend fun purgeOldSnapshots(retentionDays: Int = SNAPSHOT_RETENTION_DAYS): Int

    companion object {
        /** Long enough to be useful for price alerts, short enough to stay frugal. */
        const val SNAPSHOT_RETENTION_DAYS = 30
    }
}
