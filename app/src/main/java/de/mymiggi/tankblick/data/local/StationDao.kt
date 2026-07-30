package de.mymiggi.tankblick.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A station together with whatever the last search and the favourites say about it. */
data class StationWithContext(
    @Embedded val station: StationEntity,
    val distanceKm: Double?,
    val favoriteLabel: String?,
    val isFavorite: Boolean,
)

@Dao
interface StationDao {

    /**
     * The last nearby result, in the order the API returned it.
     *
     * Ordering by [NearbyResultEntity.position] rather than re-sorting locally
     * keeps the user's chosen sort intact offline, including the price ordering
     * that the API computes across stations we may not have prices for.
     */
    @Query(
        """
        SELECT s.*, n.distanceKm AS distanceKm, f.label AS favoriteLabel,
               (f.stationId IS NOT NULL) AS isFavorite
        FROM nearby_results n
        JOIN stations s ON s.id = n.stationId
        LEFT JOIN favorites f ON f.stationId = s.id
        ORDER BY n.position
        """,
    )
    fun observeNearby(): Flow<List<StationWithContext>>

    @Query(
        """
        SELECT s.*, NULL AS distanceKm, f.label AS favoriteLabel, 1 AS isFavorite
        FROM favorites f
        JOIN stations s ON s.id = f.stationId
        ORDER BY f.createdAt
        """,
    )
    fun observeFavorites(): Flow<List<StationWithContext>>

    @Query(
        """
        SELECT s.*, NULL AS distanceKm, f.label AS favoriteLabel,
               (f.stationId IS NOT NULL) AS isFavorite
        FROM stations s
        LEFT JOIN favorites f ON f.stationId = s.id
        WHERE s.id = :stationId
        """,
    )
    fun observeStation(stationId: String): Flow<StationWithContext?>

    @Query("SELECT stationId FROM favorites ORDER BY createdAt")
    suspend fun favoriteIds(): List<String>

    @Upsert
    suspend fun upsertStations(stations: List<StationEntity>)

    @Upsert
    suspend fun upsertStation(station: StationEntity)

    /**
     * Updates only the price columns.
     *
     * prices.php answers with prices and nothing else, so a full upsert would
     * overwrite name and address with empty strings.
     */
    @Query(
        """
        UPDATE stations
        SET e5 = :e5, e10 = :e10, diesel = :diesel, isOpen = :isOpen, fetchedAt = :fetchedAt
        WHERE id = :stationId
        """,
    )
    suspend fun updatePrices(
        stationId: String,
        e5: Double?,
        e10: Double?,
        diesel: Double?,
        isOpen: Boolean,
        fetchedAt: Long,
    )

    @Query("DELETE FROM nearby_results")
    suspend fun clearNearby()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNearby(results: List<NearbyResultEntity>)

    /** Replaces the previous search atomically, so the list never shows a mix of two. */
    @Transaction
    suspend fun replaceNearby(stations: List<StationEntity>, results: List<NearbyResultEntity>) {
        upsertStations(stations)
        clearNearby()
        insertNearby(results)
    }

    @Insert
    suspend fun insertSnapshots(snapshots: List<PriceSnapshotEntity>)

    @Query("DELETE FROM price_snapshots WHERE recordedAt < :olderThan")
    suspend fun deleteSnapshotsOlderThan(olderThan: Long): Int

    /**
     * Drops cached stations that are neither a favourite nor in the last search.
     * Keeps the database from growing with every place the user has ever been.
     */
    @Query(
        """
        DELETE FROM stations
        WHERE id NOT IN (SELECT stationId FROM favorites)
          AND id NOT IN (SELECT stationId FROM nearby_results)
        """,
    )
    suspend fun deleteOrphanedStations(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE stationId = :stationId")
    suspend fun deleteFavorite(stationId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE stationId = :stationId)")
    suspend fun isFavorite(stationId: String): Boolean

    @Query("UPDATE favorites SET label = :label WHERE stationId = :stationId")
    suspend fun updateFavoriteLabel(stationId: String, label: String?)
}
