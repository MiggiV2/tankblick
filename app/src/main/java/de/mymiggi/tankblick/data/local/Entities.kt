package de.mymiggi.tankblick.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Everything known about a station, including its current prices.
 *
 * Prices live on the station rather than being joined from the history table on
 * every read: the list screen shows the current price for hundreds of rows, and
 * a per-row lookup of "newest snapshot" would be the slowest query in the app.
 * [PriceSnapshotEntity] keeps the history separately.
 */
@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String,
    val street: String,
    val houseNumber: String,
    val postCode: String,
    val place: String,
    val latitude: Double,
    val longitude: Double,
    val isOpen: Boolean,
    val e5: Double?,
    val e10: Double?,
    val diesel: Double?,
    /** Epoch millis of the response these prices came from. */
    val fetchedAt: Long,
)

/**
 * The most recent nearby search, replaced wholesale on every refresh.
 *
 * Kept apart from [StationEntity] because "is near me right now" is a property
 * of the last search, not of the station - a favourite in another city must not
 * disappear just because it was not in the last result set.
 */
@Entity(tableName = "nearby_results")
data class NearbyResultEntity(
    @PrimaryKey val stationId: String,
    val distanceKm: Double,
    /** Position in the API's ordering, so the chosen sort survives a restart. */
    val position: Int,
    val searchedAt: Long,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val stationId: String,
    /** User-chosen name such as "Zuhause"; `null` keeps the station's own. */
    val label: String? = null,
    val createdAt: Long,
)

/**
 * Price history. Nothing reads this yet; it exists so the price alerts and the
 * "is the detour worth it" feature have data to work with when they arrive,
 * instead of starting from an empty table on the day they ship.
 */
@Entity(
    tableName = "price_snapshots",
    indices = [Index(value = ["stationId", "fuelType", "recordedAt"])],
)
data class PriceSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stationId: String,
    /** [de.mymiggi.tankblick.domain.FuelType] name. */
    val fuelType: String,
    val price: Double,
    val recordedAt: Long,
)
