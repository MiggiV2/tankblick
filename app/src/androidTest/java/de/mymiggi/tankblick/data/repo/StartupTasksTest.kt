package de.mymiggi.tankblick.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.mymiggi.tankblick.data.local.PriceSnapshotEntity
import de.mymiggi.tankblick.data.local.StationDao
import de.mymiggi.tankblick.data.local.TankblickDatabase
import de.mymiggi.tankblick.data.remote.RateLimiter
import de.mymiggi.tankblick.data.remote.TankerkoenigApi
import de.mymiggi.tankblick.domain.FuelType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Proves retention actually runs, not just that the query would work if called. */
@RunWith(AndroidJUnit4::class)
class StartupTasksTest {

    private lateinit var database: TankblickDatabase
    private lateinit var dao: StationDao
    private lateinit var startupTasks: StartupTasks
    private var now = 100L * 24 * 60 * 60 * 1000

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TankblickDatabase::class.java,
        ).build()
        dao = database.stationDao()
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.OK) })
        startupTasks = StartupTasks(
            StationRepository(
                dao = dao,
                api = TankerkoenigApi(client, RateLimiter(0L) { now }, RateLimiter(0L) { now }),
                now = { now },
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun snapshotAgedDays(days: Int) {
        dao.insertSnapshots(
            listOf(
                PriceSnapshotEntity(
                    stationId = "a",
                    fuelType = FuelType.E10.name,
                    price = 1.799,
                    recordedAt = now - days * 24L * 60 * 60 * 1000,
                ),
            ),
        )
    }

    @Test
    fun dropsPriceHistoryPastTheRetentionWindow() = runTest {
        snapshotAgedDays(31)
        snapshotAgedDays(60)

        assertEquals(2, startupTasks.run())
    }

    @Test
    fun keepsRecentPriceHistory() = runTest {
        snapshotAgedDays(0)
        snapshotAgedDays(29)

        assertEquals(0, startupTasks.run())
    }

    @Test
    fun isSafeToRunOnAnEmptyDatabase() = runTest {
        assertEquals(0, startupTasks.run())
    }
}
