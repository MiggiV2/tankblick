package de.mymiggi.tankblick.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.mymiggi.tankblick.data.local.StationDao
import de.mymiggi.tankblick.data.local.TankblickDatabase
import de.mymiggi.tankblick.data.remote.ApiResult
import de.mymiggi.tankblick.data.remote.RateLimiter
import de.mymiggi.tankblick.data.remote.TankerkoenigApi
import de.mymiggi.tankblick.domain.ApiKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a device because Room needs SQLite. The API is driven through Ktor's
 * MockEngine so the repository's real request path is exercised without going
 * near the network.
 */
@RunWith(AndroidJUnit4::class)
class StationRepositoryTest {

    private val key = ApiKey.parse("d4f1a2b3-1111-4222-8333-abcdefabcdef")!!

    private lateinit var database: TankblickDatabase
    private lateinit var dao: StationDao
    private var now = 1_000_000L
    private var responses = mutableListOf<String>()
    private var requestCount = 0

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TankblickDatabase::class.java,
        ).build()
        dao = database.stationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun repository(vararg bodies: String): StationRepository {
        responses = bodies.toMutableList()
        requestCount = 0
        val engine = MockEngine {
            val body = responses.getOrElse(requestCount) { responses.last() }
            requestCount++
            respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return DefaultStationRepository(
            dao = dao,
            api = TankerkoenigApi(client, RateLimiter(0L, clock = { now }), RateLimiter(0L, clock = { now })),
            now = { now },
        )
    }

    private fun listBody(vararg stations: String) = """
        {"ok":true,"status":"ok","stations":[${stations.joinToString(",")}]}
    """.trimIndent()

    private fun station(
        id: String,
        name: String = "Station $id",
        dist: Double = 1.0,
        e5: String = "1.859",
        isOpen: Boolean = true,
    ) = """
        {"id":"$id","name":"$name","brand":"ARAL","street":"Hauptstr.","houseNumber":"1",
         "postCode":10407,"place":"Berlin","lat":52.5,"lng":13.4,"dist":$dist,
         "isOpen":$isOpen,"e5":$e5,"e10":1.799,"diesel":1.749}
    """.trimIndent()

    @Test
    fun nearbyStartsEmpty() = runTest {
        assertTrue(repository().observeNearby().first().isEmpty())
    }

    @Test
    fun refreshStoresTheResult() = runTest {
        val repo = repository(listBody(station("a"), station("b", dist = 2.0)))

        val result = repo.refreshNearby(key, 52.5, 13.4, 5)

        assertEquals(ApiResult.Success(2), result)
        val nearby = repo.observeNearby().first()
        assertEquals(listOf("a", "b"), nearby.map { it.id })
        assertEquals(1.0, nearby[0].distanceKm!!, 1e-9)
        assertEquals(1.859, nearby[0].prices.e5!!, 1e-9)
        assertEquals(now, nearby[0].fetchedAt)
    }

    /** The API orders by distance, and that order has to survive SQLite. */
    @Test
    fun keepsTheOrderTheApiReturned() = runTest {
        val repo = repository(
            listBody(
                station("first", e5 = "1.499"),
                station("second", e5 = "1.699"),
                station("third", e5 = "1.999"),
            ),
        )

        repo.refreshNearby(key, 52.5, 13.4, 5)

        assertEquals(listOf("first", "second", "third"), repo.observeNearby().first().map { it.id })
    }

    @Test
    fun aSecondRefreshReplacesTheFirst() = runTest {
        val repo = repository(listBody(station("a"), station("b")), listBody(station("c")))

        repo.refreshNearby(key, 52.5, 13.4, 5)
        repo.refreshNearby(key, 53.5, 14.4, 5)

        assertEquals(listOf("c"), repo.observeNearby().first().map { it.id })
    }

    /** Stale prices with an honest timestamp beat an empty screen. */
    @Test
    fun aFailedRefreshLeavesTheCacheAlone() = runTest {
        val repo = repository(
            listBody(station("a")),
            """{"ok":false,"status":"error","message":"parameter error"}""",
        )
        repo.refreshNearby(key, 52.5, 13.4, 5)

        val second = repo.refreshNearby(key, 52.5, 13.4, 5)

        assertEquals(ApiResult.ApiError("parameter error"), second)
        assertEquals(listOf("a"), repo.observeNearby().first().map { it.id })
    }

    @Test
    fun togglingAFavoriteAddsAndRemovesIt() = runTest {
        val repo = repository(listBody(station("a")))
        repo.refreshNearby(key, 52.5, 13.4, 5)

        assertTrue(repo.toggleFavorite("a"))
        assertEquals(listOf("a"), repo.observeFavorites().first().map { it.id })
        assertTrue(repo.observeNearby().first().single().isFavorite)

        assertFalse(repo.toggleFavorite("a"))
        assertTrue(repo.observeFavorites().first().isEmpty())
    }

    /** A favourite in another city must not vanish because it was not in the last search. */
    @Test
    fun favoritesSurviveASearchSomewhereElse() = runTest {
        val repo = repository(listBody(station("home")), listBody(station("elsewhere")))
        repo.refreshNearby(key, 52.5, 13.4, 5)
        repo.toggleFavorite("home")

        repo.refreshNearby(key, 48.1, 11.5, 5)

        assertEquals(listOf("home"), repo.observeFavorites().first().map { it.id })
        assertEquals(listOf("elsewhere"), repo.observeNearby().first().map { it.id })
    }

    /** Otherwise the cache grows with every place the user has ever been. */
    @Test
    fun forgetsStationsThatAreNeitherNearbyNorFavorite() = runTest {
        val repo = repository(listBody(station("a"), station("b")), listBody(station("c")))
        repo.refreshNearby(key, 52.5, 13.4, 5)
        repo.toggleFavorite("a")

        repo.refreshNearby(key, 48.1, 11.5, 5)

        assertNotNull(repo.observeStation("a").first())
        assertNull(repo.observeStation("b").first())
        assertNotNull(repo.observeStation("c").first())
    }

    @Test
    fun refreshingFavoritesUpdatesOnlyThePrices() = runTest {
        val repo = repository(
            listBody(station("a")),
            """{"ok":true,"prices":{"a":{"status":"open","e5":1.111,"e10":1.222,"diesel":1.333}}}""",
        )
        repo.refreshNearby(key, 52.5, 13.4, 5)
        repo.toggleFavorite("a")
        now += 60_000

        assertEquals(ApiResult.Success(1), repo.refreshFavorites(key))

        val station = repo.observeStation("a").first()!!
        assertEquals(1.111, station.prices.e5!!, 1e-9)
        assertEquals("Station a", station.name)
        assertEquals("Hauptstr. 1, 10407 Berlin", station.address)
        assertEquals(now, station.fetchedAt)
    }

    @Test
    fun refreshingFavoritesWithoutAnyMakesNoRequest() = runTest {
        val repo = repository("""{"ok":true,"prices":{}}""")

        assertEquals(ApiResult.Success(0), repo.refreshFavorites(key))
        assertEquals(0, requestCount)
    }

    /**
     * A station dropping out of the MTS-K feed should not wipe the last prices
     * we did have for it.
     */
    @Test
    fun keepsTheLastPricesForAStationTheApiNoLongerKnows() = runTest {
        val repo = repository(
            listBody(station("a")),
            """{"ok":true,"prices":{"a":{"status":"not found"}}}""",
        )
        repo.refreshNearby(key, 52.5, 13.4, 5)
        repo.toggleFavorite("a")

        assertEquals(ApiResult.Success(0), repo.refreshFavorites(key))

        assertEquals(1.859, repo.observeStation("a").first()!!.prices.e5!!, 1e-9)
    }

    @Test
    fun marksAClosedStationClosedAndDropsItsPrices() = runTest {
        val repo = repository(
            listBody(station("a")),
            """{"ok":true,"prices":{"a":{"status":"closed"}}}""",
        )
        repo.refreshNearby(key, 52.5, 13.4, 5)
        repo.toggleFavorite("a")

        repo.refreshFavorites(key)

        val station = repo.observeStation("a").first()!!
        assertFalse(station.isOpen)
        assertNull(station.prices.e5)
    }

    @Test
    fun storesAFavoriteLabel() = runTest {
        val repo = repository(listBody(station("a")))
        repo.refreshNearby(key, 52.5, 13.4, 5)
        repo.toggleFavorite("a")

        repo.setFavoriteLabel("a", "Zuhause")

        assertEquals("Zuhause", repo.observeFavorites().first().single().favoriteLabel)
    }

    @Test
    fun treatsABlankLabelAsNoLabel() = runTest {
        val repo = repository(listBody(station("a")))
        repo.refreshNearby(key, 52.5, 13.4, 5)
        repo.toggleFavorite("a")
        repo.setFavoriteLabel("a", "Zuhause")

        repo.setFavoriteLabel("a", "   ")

        assertNull(repo.observeFavorites().first().single().favoriteLabel)
    }

    @Test
    fun purgesPriceHistoryOlderThanTheRetentionWindow() = runTest {
        val repo = repository(listBody(station("a")), listBody(station("a")))
        repo.refreshNearby(key, 52.5, 13.4, 5)

        now += 31L * 24 * 60 * 60 * 1000
        repo.refreshNearby(key, 52.5, 13.4, 5)

        // Three prices per refresh, two refreshes; only the recent three survive.
        assertEquals(3, repo.purgeOldSnapshots())
        assertEquals(0, repo.purgeOldSnapshots())
    }

    @Test
    fun detailRefreshCachesTheStation() = runTest {
        val repo = repository(
            """
            {"ok":true,"status":"ok","station":{"id":"z","name":"Autohof","brand":"",
             "street":"Bundesstr.","houseNumber":"7","postCode":1067,"place":"Dresden",
             "lat":51.05,"lng":13.73,"isOpen":true,"wholeDay":true,"openingTimes":[],
             "overrides":[],"e5":1.659,"e10":false,"diesel":1.549}}
            """.trimIndent(),
        )

        val result = repo.refreshStationDetail(key, "z")

        assertTrue(result is ApiResult.Success)
        val station = repo.observeStation("z").first()!!
        assertEquals("Bundesstr. 7, 01067 Dresden", station.address)
        assertEquals(1.659, station.prices.e5!!, 1e-9)
        assertNull(station.prices.e10)
    }
}
