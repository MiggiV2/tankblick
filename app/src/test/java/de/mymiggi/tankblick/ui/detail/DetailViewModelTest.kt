package de.mymiggi.tankblick.ui.detail

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import de.mymiggi.tankblick.MainDispatcherRule
import de.mymiggi.tankblick.data.prefs.ApiKeyStore
import de.mymiggi.tankblick.data.prefs.FakeSecretCipher
import de.mymiggi.tankblick.data.remote.ApiResult
import de.mymiggi.tankblick.data.remote.dto.OpeningTimeDto
import de.mymiggi.tankblick.data.remote.dto.StationDetailDto
import de.mymiggi.tankblick.data.repo.FakeStationRepository
import de.mymiggi.tankblick.domain.ApiKey
import de.mymiggi.tankblick.domain.Prices
import de.mymiggi.tankblick.domain.Station
import de.mymiggi.tankblick.ui.nearby.NearbyMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
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
class DetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storeScope: TestScope
    private lateinit var apiKeyStore: ApiKeyStore
    private lateinit var repository: FakeStationRepository

    @Before
    fun setUp() {
        storeScope = TestScope(UnconfinedTestDispatcher())
        val secrets: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = storeScope) {
            tempFolder.newFile("secrets.preferences_pb")
        }
        apiKeyStore = ApiKeyStore(secrets, FakeSecretCipher())
        repository = FakeStationRepository()
        repository.nearby.value = listOf(cachedStation())
    }

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    private suspend fun TestScope.viewModel(
        stationId: String = "a",
        withKey: Boolean = true,
    ): DetailViewModel {
        if (withKey) apiKeyStore.save(ApiKey.parse("d4f1a2b3-1111-4222-8333-abcdefabcdef")!!)
        return DetailViewModel(stationId, repository, apiKeyStore).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.uiState.collect {}
            }
        }
    }

    private fun cachedStation() = Station(
        id = "a",
        name = "TotalEnergies Berlin",
        brand = "TotalEnergies",
        street = "Margarete-Sommer-Str.",
        houseNumber = "2",
        postCode = "10407",
        place = "Berlin",
        latitude = 52.53,
        longitude = 13.44,
        isOpen = true,
        prices = Prices(e5 = 1.859, e10 = 1.799, diesel = 1.749),
        fetchedAt = 1_000L,
    )

    private fun detailDto(
        wholeDay: Boolean = false,
        openingTimes: List<OpeningTimeDto> = listOf(OpeningTimeDto("Mo-Fr", "05:00:00", "23:30:00")),
        overrides: List<String> = emptyList(),
    ) = StationDetailDto(
        id = "a",
        name = "TotalEnergies Berlin",
        wholeDay = wholeDay,
        openingTimes = openingTimes,
        overrides = overrides,
    )

    /** The list already has the essentials, so waiting on a request would be a worse screen. */
    @Test
    fun `shows the cached station straight away`() = runTest {
        repository.detailResult = ApiResult.Success(detailDto())

        val state = viewModel().uiState.value

        assertEquals("TotalEnergies Berlin", state.station?.name)
        assertEquals("Margarete-Sommer-Str. 2, 10407 Berlin", state.station?.address)
        assertEquals(1.799, state.station?.prices?.e10!!, 1e-9)
    }

    @Test
    fun `loads the opening hours the list does not carry`() = runTest {
        repository.detailResult = ApiResult.Success(
            detailDto(
                openingTimes = listOf(
                    OpeningTimeDto("Mo-Fr", "05:00:00", "23:30:00"),
                    OpeningTimeDto("Sa, So", "06:00:00", "22:00:00"),
                ),
            ),
        )

        val hours = viewModel().uiState.value.openingHours!!

        assertEquals(listOf("Mo-Fr", "Sa, So"), hours.rows.map { it.days })
        assertEquals("05:00 – 23:30", hours.rows.first().hours)
    }

    @Test
    fun `reports a station that never closes`() = runTest {
        repository.detailResult = ApiResult.Success(detailDto(wholeDay = true))

        assertTrue(viewModel().uiState.value.openingHours!!.isWholeDay)
    }

    @Test
    fun `passes holiday exceptions through`() = runTest {
        repository.detailResult =
            ApiResult.Success(detailDto(overrides = listOf("24.12.2026, 06:00-14:00")))

        assertEquals(
            listOf("24.12.2026, 06:00-14:00"),
            viewModel().uiState.value.openingHours!!.exceptions,
        )
    }

    /** Failing to load the hours must not take the cached station off screen. */
    @Test
    fun `keeps the station when the detail request fails`() = runTest {
        repository.detailResult = ApiResult.Offline

        val state = viewModel().uiState.value

        assertEquals(NearbyMessage.Offline, state.message)
        assertEquals("TotalEnergies Berlin", state.station?.name)
        assertNull(state.openingHours)
    }

    @Test
    fun `surfaces the wait when the rate limiter blocks the lookup`() = runTest {
        repository.detailResult = ApiResult.RateLimited(2)

        assertEquals(NearbyMessage.RateLimited(2), viewModel().uiState.value.message)
    }

    @Test
    fun `refuses to load without an api key`() = runTest {
        val state = viewModel(withKey = false).uiState.value

        assertEquals(NearbyMessage.MissingApiKey, state.message)
    }

    @Test
    fun `shows nothing but a message for a station that is not cached`() = runTest {
        repository.detailResult = ApiResult.Offline

        val state = viewModel(stationId = "unknown").uiState.value

        assertNull(state.station)
    }

    @Test
    fun `is not loading once the lookup finished`() = runTest {
        repository.detailResult = ApiResult.Success(detailDto())

        assertFalse(viewModel().uiState.value.isLoading)
    }

    @Test
    fun `toggling makes the station a favourite`() = runTest {
        repository.detailResult = ApiResult.Success(detailDto())
        val viewModel = viewModel()

        viewModel.toggleFavorite()

        assertTrue(viewModel.uiState.value.station!!.isFavorite)
    }

    @Test
    fun `stores a label for the favourite`() = runTest {
        repository.detailResult = ApiResult.Success(detailDto())
        val viewModel = viewModel()
        viewModel.toggleFavorite()

        viewModel.setLabel("Zuhause")

        assertEquals("Zuhause", viewModel.uiState.value.station!!.favoriteLabel)
    }

    @Test
    fun `a dismissed message stays dismissed`() = runTest {
        repository.detailResult = ApiResult.Offline
        val viewModel = viewModel()

        viewModel.dismissMessage()

        assertNull(viewModel.uiState.value.message)
    }

    /** The detail screen loads on open, so it can be the first to see a dead key. */
    @Test
    fun `hands over to onboarding when the build key is rejected`() = runTest {
        val buildKey = ApiKey.parse("11111111-2222-3333-4444-555555555555")!!
        val secrets: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = storeScope) {
            tempFolder.newFile("build-key.preferences_pb")
        }
        val store = ApiKeyStore(secrets, FakeSecretCipher(), buildKey)
        repository.detailResult = ApiResult.InvalidKey

        DetailViewModel("a", repository, store)

        assertNull(store.apiKey.first())
    }
}
