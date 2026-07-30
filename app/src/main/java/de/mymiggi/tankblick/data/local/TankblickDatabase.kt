package de.mymiggi.tankblick.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        StationEntity::class,
        NearbyResultEntity::class,
        FavoriteEntity::class,
        PriceSnapshotEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TankblickDatabase : RoomDatabase() {

    abstract fun stationDao(): StationDao

    companion object {
        private const val NAME = "tankblick.db"

        fun create(context: Context): TankblickDatabase =
            Room.databaseBuilder(context, TankblickDatabase::class.java, NAME).build()
    }
}
