package de.mymiggi.tankblick.data.repo

import de.mymiggi.tankblick.data.local.FavoriteEntity
import de.mymiggi.tankblick.data.local.NearbyResultEntity
import de.mymiggi.tankblick.data.local.PriceSnapshotEntity
import de.mymiggi.tankblick.data.local.StationDao
import de.mymiggi.tankblick.data.local.StationEntity
import de.mymiggi.tankblick.data.local.StationWithContext
import de.mymiggi.tankblick.data.remote.ApiResult
import de.mymiggi.tankblick.data.remote.TankerkoenigApi
import de.mymiggi.tankblick.data.remote.dto.StationDetailDto
import de.mymiggi.tankblick.data.remote.dto.StationSummaryDto
import de.mymiggi.tankblick.domain.ApiKey
import de.mymiggi.tankblick.domain.FuelType
import de.mymiggi.tankblick.domain.Prices
import de.mymiggi.tankblick.domain.SortMode
import de.mymiggi.tankblick.domain.Station
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for station data.
 *
 * Offline-first: every read comes from the database, and the network is only
 * touched when the user explicitly refreshes. That is not just a nicety here -
 * Tankerkönig's terms ask clients not to poll, so "no request without a user
 * action" is a rule rather than an optimisation.
 *
 * A failed refresh never clears the cache. Stale prices with an honest
 * timestamp beat an empty screen.
 */
class StationRepository(
    private val dao: StationDao,
    private val api: TankerkoenigApi,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun observeNearby(): Flow<List<Station>> =
        dao.observeNearby().map { rows -> rows.map { it.toStation() } }

    fun observeFavorites(): Flow<List<Station>> =
        dao.observeFavorites().map { rows -> rows.map { it.toStation() } }

    fun observeStation(stationId: String): Flow<Station?> =
        dao.observeStation(stationId).map { it?.toStation() }

    /** Replaces the cached nearby result. Returns the number of stations found. */
    suspend fun refreshNearby(
        apiKey: ApiKey,
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
        fuelType: FuelType,
        sortMode: SortMode,
    ): ApiResult<Int> {
        val result = api.findNearby(apiKey, latitude, longitude, radiusKm, fuelType, sortMode)
        if (result !is ApiResult.Success) return result.asFailure()

        val timestamp = now()
        val stations = result.value.map { it.toEntity(timestamp) }
        val nearby = result.value.mapIndexed { index, dto ->
            NearbyResultEntity(
                stationId = dto.id,
                distanceKm = dto.distanceKm ?: 0.0,
                position = index,
                searchedAt = timestamp,
            )
        }

        dao.replaceNearby(stations, nearby)
        dao.insertSnapshots(stations.toSnapshots(timestamp))
        dao.deleteOrphanedStations()
        return ApiResult.Success(result.value.size)
    }

    /** Updates prices for every favourite in one go. Returns how many were updated. */
    suspend fun refreshFavorites(apiKey: ApiKey): ApiResult<Int> {
        val ids = dao.favoriteIds()
        if (ids.isEmpty()) return ApiResult.Success(0)

        val result = api.prices(apiKey, ids)
        if (result !is ApiResult.Success) return result.asFailure()

        val timestamp = now()
        val snapshots = mutableListOf<PriceSnapshotEntity>()
        var updated = 0

        for ((stationId, price) in result.value) {
            // "not found" means the station is gone from the MTS-K feed; there
            // is nothing to write and overwriting with nulls would throw away
            // the last prices we did have.
            if (price.status == STATUS_NOT_FOUND) continue

            dao.updatePrices(
                stationId = stationId,
                e5 = price.e5,
                e10 = price.e10,
                diesel = price.diesel,
                isOpen = price.status == STATUS_OPEN,
                fetchedAt = timestamp,
            )
            updated++
            snapshots += snapshotsFor(stationId, price.e5, price.e10, price.diesel, timestamp)
        }

        dao.insertSnapshots(snapshots)
        return ApiResult.Success(updated)
    }

    /** Fetches the full record for one station and caches it. */
    suspend fun refreshStationDetail(apiKey: ApiKey, stationId: String): ApiResult<StationDetailDto> {
        val result = api.stationDetail(apiKey, stationId)
        if (result !is ApiResult.Success) return result

        val timestamp = now()
        val detail = result.value
        dao.upsertStation(detail.toEntity(timestamp))
        dao.insertSnapshots(
            snapshotsFor(detail.id, detail.e5, detail.e10, detail.diesel, timestamp),
        )
        return result
    }

    /** @return true if the station is a favourite afterwards. */
    suspend fun toggleFavorite(stationId: String): Boolean {
        return if (dao.isFavorite(stationId)) {
            dao.deleteFavorite(stationId)
            dao.deleteOrphanedStations()
            false
        } else {
            dao.insertFavorite(FavoriteEntity(stationId = stationId, createdAt = now()))
            true
        }
    }

    suspend fun setFavoriteLabel(stationId: String, label: String?) {
        dao.updateFavoriteLabel(stationId, label?.takeIf { it.isNotBlank() })
    }

    /**
     * Drops price history the app has no use for. Run once per process start by
     * [StartupTasks]: keeping years of prices for stations someone drove past
     * once is neither useful nor in the spirit of collecting as little as
     * possible.
     */
    suspend fun purgeOldSnapshots(retentionDays: Int = SNAPSHOT_RETENTION_DAYS): Int =
        dao.deleteSnapshotsOlderThan(now() - retentionDays * MILLIS_PER_DAY)

    private fun snapshotsFor(
        stationId: String,
        e5: Double?,
        e10: Double?,
        diesel: Double?,
        timestamp: Long,
    ): List<PriceSnapshotEntity> = buildList {
        e5?.let { add(PriceSnapshotEntity(stationId = stationId, fuelType = FuelType.E5.name, price = it, recordedAt = timestamp)) }
        e10?.let { add(PriceSnapshotEntity(stationId = stationId, fuelType = FuelType.E10.name, price = it, recordedAt = timestamp)) }
        diesel?.let { add(PriceSnapshotEntity(stationId = stationId, fuelType = FuelType.DIESEL.name, price = it, recordedAt = timestamp)) }
    }

    private fun List<StationEntity>.toSnapshots(timestamp: Long): List<PriceSnapshotEntity> =
        flatMap { snapshotsFor(it.id, it.e5, it.e10, it.diesel, timestamp) }

    private companion object {
        const val STATUS_OPEN = "open"
        const val STATUS_NOT_FOUND = "not found"
        const val SNAPSHOT_RETENTION_DAYS = 30
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}

/**
 * Narrows a non-success result to any payload type.
 *
 * Spelled out rather than cast: every failure case is an `ApiResult<Nothing>`,
 * which the compiler accepts for any `R` because the type is covariant. If a
 * future case ever carries a real payload, this stops compiling instead of
 * failing silently at runtime.
 */
private fun <T, R> ApiResult<T>.asFailure(): ApiResult<R> = when (this) {
    is ApiResult.Success -> error("asFailure called on a successful result")
    is ApiResult.RateLimited -> this
    is ApiResult.InvalidKey -> this
    is ApiResult.Offline -> this
    is ApiResult.ServerError -> this
    is ApiResult.ApiError -> this
    is ApiResult.MalformedResponse -> this
}

private fun StationWithContext.toStation() = Station(
    id = station.id,
    name = station.name,
    brand = station.brand,
    street = station.street,
    houseNumber = station.houseNumber,
    postCode = station.postCode,
    place = station.place,
    latitude = station.latitude,
    longitude = station.longitude,
    isOpen = station.isOpen,
    distanceKm = distanceKm,
    prices = Prices(e5 = station.e5, e10 = station.e10, diesel = station.diesel),
    isFavorite = isFavorite,
    favoriteLabel = favoriteLabel,
    fetchedAt = station.fetchedAt,
)

private fun StationSummaryDto.toEntity(timestamp: Long) = StationEntity(
    id = id,
    name = name,
    brand = brand,
    street = street,
    houseNumber = houseNumber,
    postCode = postCode,
    place = place,
    latitude = lat,
    longitude = lng,
    isOpen = isOpen,
    e5 = e5,
    e10 = e10,
    diesel = diesel,
    fetchedAt = timestamp,
)

private fun StationDetailDto.toEntity(timestamp: Long) = StationEntity(
    id = id,
    name = name,
    brand = brand,
    street = street,
    houseNumber = houseNumber,
    postCode = postCode,
    place = place,
    latitude = lat,
    longitude = lng,
    isOpen = isOpen,
    e5 = e5,
    e10 = e10,
    diesel = diesel,
    fetchedAt = timestamp,
)
