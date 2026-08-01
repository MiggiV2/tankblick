package de.mymiggi.tankblick.ui.nearby

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import de.mymiggi.tankblick.MainDispatcherRule
import de.mymiggi.tankblick.data.prefs.ApiKeyStore
import de.mymiggi.tankblick.data.prefs.FakeSecretCipher
import de.mymiggi.tankblick.data.prefs.SettingsStore
import de.mymiggi.tankblick.data.remote.ApiResult
import de.mymiggi.tankblick.data.repo.FakeStationRepository
import de.mymiggi.tankblick.domain.ApiKey
import de.mymiggi.tankblick.domain.FuelType
import de.mymiggi.tankblick.domain.Prices
import de.mymiggi.tankblick.domain.SortMode
import de.mymiggi.tankblick.domain.Station
import de.mymiggi.tankblick.location.FakeLocationSource
import de.mymiggi.tankblick.location.LocationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storeScope: TestScope
    private lateinit var secretsStore: DataStore<Preferences>
    private lateinit var settingsDataStore: DataStore<Preferences>
    private lateinit var apiKeyStore: ApiKeyStore
    private lateinit var settingsStore: SettingsStore
    private lateinit var repository: FakeStationRepository
    private lateinit var locationSource: FakeLocationSource

    @Before
    fun setUp() {
        storeScope = TestScope(UnconfinedTestDispatcher())
        secretsStore = PreferenceDataStoreFactory.create(scope = storeScope) {
            tempFolder.newFile("secrets.preferences_pb")
        }
        settingsDataStore = PreferenceDataStoreFactory.create(scope = storeScope) {
            tempFolder.newFile("settings.preferences_pb")
        }
        apiKeyStore = ApiKeyStore(secretsStore, FakeSecretCipher())
        settingsStore = SettingsStore(settingsDataStore)
        repository = FakeStationRepository()
        locationSource = FakeLocationSource()
    }

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    /**
     * The state flow only recomputes while something collects it, so the test
     * has to subscribe the way the screen does. Without that, uiState.value
     * would sit on its initial value and every assertion would pass or fail for
     * the wrong reason.
     */
    private suspend fun TestScope.viewModel(withKey: Boolean = true): NearbyViewModel {
        if (withKey) apiKeyStore.save(ApiKey.parse("d4f1a2b3-1111-4222-8333-abcdefabcdef")!!)
        return NearbyViewModel(repository, apiKeyStore, settingsStore, locationSource).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.uiState.collect {}
            }
        }
    }

    private fun station(
        id: String,
        e5: Double? = 1.859,
        e10: Double? = 1.799,
        diesel: Double? = 1.749,
        distanceKm: Double = 1.0,
        isOpen: Boolean = true,
    ) = Station(
        id = id,
        name = "Station $id",
        brand = "ARAL",
        street = "Hauptstr.",
        houseNumber = "1",
        postCode = "10407",
        place = "Berlin",
        latitude = 52.5,
        longitude = 13.4,
        isOpen = isOpen,
        distanceKm = distanceKm,
        prices = Prices(e5 = e5, e10 = e10, diesel = diesel),
        fetchedAt = 1_000L,
    )

    @Test
    fun `shows the cached list without touching the network`() = runTest {
        repository.nearby.value = listOf(station("a"), station("b"))

        val state = viewModel().uiState.first { it.stations.isNotEmpty() }

        assertEquals(listOf("a", "b"), state.stations.map { it.id })
        assertTrue(repository.nearbyRequests.isEmpty())
    }

    @Test
    fun `refresh asks for the user's position and passes the settings through`() = runTest {
        locationSource.result = LocationResult.Available(52.52, 13.44)
        settingsStore.setRadiusKm(12)
        val viewModel = viewModel()

        viewModel.refresh()

        val request = repository.nearbyRequests.single()
        assertEquals(52.52, request.latitude, 1e-9)
        assertEquals(13.44, request.longitude, 1e-9)
        assertEquals(12, request.radiusKm)
    }

    @Test
    fun `asks for permission instead of failing silently`() = runTest {
        locationSource.result = LocationResult.PermissionMissing
        val viewModel = viewModel()

        viewModel.refresh()

        assertEquals(NearbyMessage.NeedsLocationPermission, viewModel.uiState.value.message)
        assertTrue(repository.nearbyRequests.isEmpty())
    }

    @Test
    fun `points at the system setting when location services are off`() = runTest {
        locationSource.result = LocationResult.LocationDisabled
        val viewModel = viewModel()

        viewModel.refresh()

        assertEquals(NearbyMessage.LocationDisabled, viewModel.uiState.value.message)
        assertTrue(repository.nearbyRequests.isEmpty())
    }

    @Test
    fun `reports that no position could be obtained`() = runTest {
        locationSource.result = LocationResult.Unavailable
        val viewModel = viewModel()

        viewModel.refresh()

        assertEquals(NearbyMessage.LocationUnavailable, viewModel.uiState.value.message)
    }

    /**
     * A key baked into the build is not something the user can correct from the
     * settings screen, so a rejection has to hand them onboarding rather than a
     * banner they can only dismiss.
     */
    @Test
    fun `hands over to onboarding when the build key is rejected`() = runTest {
        val buildKey = ApiKey.parse("11111111-2222-3333-4444-555555555555")!!
        val store = ApiKeyStore(secretsStore, FakeSecretCipher(), buildKey)
        locationSource.result = LocationResult.Available(52.5, 13.4)
        repository.nearbyResult = ApiResult.InvalidKey
        val viewModel = NearbyViewModel(repository, store, settingsStore, locationSource)

        viewModel.refresh()
        runCurrent()

        assertNull(store.apiKey.first())
    }

    /** A countdown is understandable; a bare error is not. */
    @Test
    fun `surfaces the wait when the rate limiter blocks a refresh`() = runTest {
        locationSource.result = LocationResult.Available(52.5, 13.4)
        repository.nearbyResult = ApiResult.RateLimited(42)
        val viewModel = viewModel()

        viewModel.refresh()

        assertEquals(NearbyMessage.RateLimited(42), viewModel.uiState.value.message)
    }

    @Test
    fun `keeps the cached list when a refresh fails`() = runTest {
        repository.nearby.value = listOf(station("a"))
        locationSource.result = LocationResult.Available(52.5, 13.4)
        repository.nearbyResult = ApiResult.Offline
        val viewModel = viewModel()
        viewModel.uiState.first { it.stations.isNotEmpty() }

        viewModel.refresh()

        assertEquals(NearbyMessage.Offline, viewModel.uiState.value.message)
        assertEquals(listOf("a"), viewModel.uiState.value.stations.map { it.id })
    }

    @Test
    fun `says so when the search found nothing`() = runTest {
        locationSource.result = LocationResult.Available(52.5, 13.4)
        repository.nearbyResult = ApiResult.Success(0)
        val viewModel = viewModel()

        viewModel.refresh()

        assertEquals(NearbyMessage.NoResults, viewModel.uiState.value.message)
    }

    @Test
    fun `clears the message after a successful refresh`() = runTest {
        locationSource.result = LocationResult.Available(52.5, 13.4)
        repository.nearbyResult = ApiResult.Offline
        val viewModel = viewModel()
        viewModel.refresh()

        repository.nearbyResult = ApiResult.Success(3)
        viewModel.refresh()

        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `refuses to refresh without an api key`() = runTest {
        locationSource.result = LocationResult.Available(52.5, 13.4)
        val viewModel = viewModel(withKey = false)

        viewModel.refresh()

        assertEquals(NearbyMessage.MissingApiKey, viewModel.uiState.value.message)
        assertTrue(repository.nearbyRequests.isEmpty())
    }

    /**
     * All three prices come back in one response, so switching fuel is a
     * re-render. Spending a request on it would burn the one-per-minute budget.
     */
    @Test
    fun `switching fuel type does not cost a request`() = runTest {
        repository.nearby.value = listOf(station("a"))
        val viewModel = viewModel()
        viewModel.uiState.first { it.stations.isNotEmpty() }

        viewModel.setFuelType(FuelType.DIESEL)

        assertEquals(FuelType.DIESEL, viewModel.uiState.value.fuelType)
        assertTrue(repository.nearbyRequests.isEmpty())
    }

    @Test
    fun `switching sort order does not cost a request`() = runTest {
        repository.nearby.value = listOf(station("a"))
        val viewModel = viewModel()
        viewModel.uiState.first { it.stations.isNotEmpty() }

        viewModel.setSortMode(SortMode.DISTANCE)

        assertEquals(SortMode.DISTANCE, viewModel.uiState.value.sortMode)
        assertTrue(repository.nearbyRequests.isEmpty())
    }

    @Test
    fun `sorts by the price of the selected fuel`() = runTest {
        repository.nearby.value = listOf(
            station("far-but-cheap", e10 = 1.599, distanceKm = 9.0),
            station("near-but-dear", e10 = 1.899, distanceKm = 0.5),
        )
        val viewModel = viewModel()

        val state = viewModel.uiState.first { it.stations.size == 2 }

        assertEquals(listOf("far-but-cheap", "near-but-dear"), state.stations.map { it.id })
    }

    @Test
    fun `sorts by distance on request`() = runTest {
        repository.nearby.value = listOf(
            station("far-but-cheap", e10 = 1.599, distanceKm = 9.0),
            station("near-but-dear", e10 = 1.899, distanceKm = 0.5),
        )
        val viewModel = viewModel()
        viewModel.uiState.first { it.stations.size == 2 }

        viewModel.setSortMode(SortMode.DISTANCE)

        assertEquals(
            listOf("near-but-dear", "far-but-cheap"),
            viewModel.uiState.value.stations.map { it.id },
        )
    }

    /** A station with no price for the chosen fuel is useless at the top of a price list. */
    @Test
    fun `puts stations without a price for the chosen fuel last`() = runTest {
        repository.nearby.value = listOf(
            station("no-e10", e10 = null, distanceKm = 0.1),
            station("has-e10", e10 = 1.899, distanceKm = 5.0),
        )
        val viewModel = viewModel()

        val state = viewModel.uiState.first { it.stations.size == 2 }

        assertEquals(listOf("has-e10", "no-e10"), state.stations.map { it.id })
    }

    @Test
    fun `reports the age of the data it is showing`() = runTest {
        repository.nearby.value = listOf(station("a").copy(fetchedAt = 5_000L))

        val state = viewModel().uiState.first { it.stations.isNotEmpty() }

        assertEquals(5_000L, state.lastUpdatedAt)
    }

    @Test
    fun `a dismissed message stays dismissed`() = runTest {
        locationSource.result = LocationResult.PermissionMissing
        val viewModel = viewModel()
        viewModel.refresh()

        viewModel.dismissMessage()

        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `is not refreshing once a refresh has finished`() = runTest {
        locationSource.result = LocationResult.Available(52.5, 13.4)
        val viewModel = viewModel()

        viewModel.refresh()

        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    /**
     * A location provider that never calls back would otherwise leave the
     * screen refreshing forever, and since refresh() ignores a second call
     * while one is running, the button would stay dead until the app restarts.
     */
    @Test
    fun `gives up when the location provider never answers`() = runTest {
        locationSource.hangForever = true
        val viewModel = viewModel()

        viewModel.refresh()
        advanceTimeBy(NearbyViewModel.LOCATION_TIMEOUT_MILLIS + 1_000)

        assertEquals(NearbyMessage.LocationUnavailable, viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    /**
     * Two taps must not cost two requests: the API allows one per minute, so a
     * duplicate would spend the whole budget for nothing.
     */
    @Test
    fun `ignores a second refresh while the first is still running`() = runTest {
        locationSource.block()
        val viewModel = viewModel()

        viewModel.refresh()
        viewModel.refresh()
        locationSource.release()
        runCurrent()

        assertEquals(1, repository.nearbyRequests.size)
    }

    @Test
    fun `can refresh again after a hung attempt gave up`() = runTest {
        locationSource.hangForever = true
        val viewModel = viewModel()
        viewModel.refresh()
        advanceTimeBy(NearbyViewModel.LOCATION_TIMEOUT_MILLIS + 1_000)

        locationSource.hangForever = false
        locationSource.result = LocationResult.Available(52.5, 13.4)
        viewModel.refresh()

        assertEquals(1, repository.nearbyRequests.size)
    }

    @Test
    fun `toggling a favourite updates the row`() = runTest {
        repository.nearby.value = listOf(station("a"))
        val viewModel = viewModel()
        viewModel.uiState.first { it.stations.isNotEmpty() }

        viewModel.toggleFavorite("a")

        assertTrue(viewModel.uiState.value.stations.single().isFavorite)
    }
}
