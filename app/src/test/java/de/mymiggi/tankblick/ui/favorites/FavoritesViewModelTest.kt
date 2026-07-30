package de.mymiggi.tankblick.ui.favorites

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
import de.mymiggi.tankblick.domain.Prices
import de.mymiggi.tankblick.domain.Station
import de.mymiggi.tankblick.ui.nearby.NearbyMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class FavoritesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storeScope: TestScope
    private lateinit var apiKeyStore: ApiKeyStore
    private lateinit var settingsStore: SettingsStore
    private lateinit var repository: FakeStationRepository

    @Before
    fun setUp() {
        storeScope = TestScope(UnconfinedTestDispatcher())
        val secrets: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = storeScope) {
            tempFolder.newFile("secrets.preferences_pb")
        }
        val settings: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = storeScope) {
                tempFolder.newFile("settings.preferences_pb")
            }
        apiKeyStore = ApiKeyStore(secrets, FakeSecretCipher())
        settingsStore = SettingsStore(settings)
        repository = FakeStationRepository()
    }

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    private suspend fun TestScope.viewModel(withKey: Boolean = true): FavoritesViewModel {
        if (withKey) apiKeyStore.save(ApiKey.parse("d4f1a2b3-1111-4222-8333-abcdefabcdef")!!)
        return FavoritesViewModel(repository, apiKeyStore, settingsStore).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.uiState.collect {}
            }
        }
    }

    private fun station(id: String, label: String? = null, fetchedAt: Long = 1_000L) = Station(
        id = id,
        name = "Station $id",
        brand = "ARAL",
        street = "Hauptstr.",
        houseNumber = "1",
        postCode = "10407",
        place = "Berlin",
        latitude = 52.5,
        longitude = 13.4,
        isOpen = true,
        prices = Prices(e5 = 1.859, e10 = 1.799, diesel = 1.749),
        isFavorite = true,
        favoriteLabel = label,
        fetchedAt = fetchedAt,
    )

    @Test
    fun `starts empty`() = runTest {
        assertTrue(viewModel().uiState.value.stations.isEmpty())
    }

    @Test
    fun `shows the stored favourites`() = runTest {
        repository.favorites.value = listOf(station("a", "Zuhause"), station("b"))

        val state = viewModel().uiState.value

        assertEquals(listOf("a", "b"), state.stations.map { it.id })
        assertEquals("Zuhause", state.stations.first().favoriteLabel)
    }

    /**
     * The order is the order they were added. Re-sorting by price would break
     * the "is my usual station cheap today" glance this screen exists for.
     */
    @Test
    fun `keeps the order the repository provides`() = runTest {
        repository.favorites.value = listOf(station("added-first"), station("added-second"))

        assertEquals(
            listOf("added-first", "added-second"),
            viewModel().uiState.value.stations.map { it.id },
        )
    }

    @Test
    fun `refreshing prices reports nothing when it worked`() = runTest {
        repository.favoritesResult = ApiResult.Success(3)
        val viewModel = viewModel()

        viewModel.refresh()

        assertNull(viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `surfaces the wait when the rate limiter blocks the refresh`() = runTest {
        repository.favoritesResult = ApiResult.RateLimited(17)
        val viewModel = viewModel()

        viewModel.refresh()

        assertEquals(NearbyMessage.RateLimited(17), viewModel.uiState.value.message)
    }

    @Test
    fun `keeps the list when the refresh fails`() = runTest {
        repository.favorites.value = listOf(station("a"))
        repository.favoritesResult = ApiResult.Offline
        val viewModel = viewModel()

        viewModel.refresh()

        assertEquals(NearbyMessage.Offline, viewModel.uiState.value.message)
        assertEquals(listOf("a"), viewModel.uiState.value.stations.map { it.id })
    }

    @Test
    fun `refuses to refresh without an api key`() = runTest {
        val viewModel = viewModel(withKey = false)

        viewModel.refresh()

        assertEquals(NearbyMessage.MissingApiKey, viewModel.uiState.value.message)
    }

    /** An empty favourites list is not an error, so it gets no banner. */
    @Test
    fun `says nothing when there is nothing to refresh`() = runTest {
        repository.favoritesResult = ApiResult.Success(0)
        val viewModel = viewModel()

        viewModel.refresh()

        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `reports the age of the oldest price on screen`() = runTest {
        repository.favorites.value =
            listOf(station("a", fetchedAt = 9_000L), station("b", fetchedAt = 4_000L))

        assertEquals(4_000L, viewModel().uiState.value.lastUpdatedAt)
    }

    @Test
    fun `follows the fuel type chosen on the nearby screen`() = runTest {
        settingsStore.setFuelType(de.mymiggi.tankblick.domain.FuelType.DIESEL)

        assertEquals(
            de.mymiggi.tankblick.domain.FuelType.DIESEL,
            viewModel().uiState.value.fuelType,
        )
    }

    @Test
    fun `removing a favourite takes it off the list`() = runTest {
        repository.nearby.value = listOf(station("a"))
        repository.favorites.value = listOf(station("a"))
        val viewModel = viewModel()

        viewModel.toggleFavorite("a")

        assertTrue(viewModel.uiState.value.stations.isEmpty())
    }

    @Test
    fun `a dismissed message stays dismissed`() = runTest {
        repository.favoritesResult = ApiResult.Offline
        val viewModel = viewModel()
        viewModel.refresh()

        viewModel.dismissMessage()

        assertNull(viewModel.uiState.value.message)
    }
}
