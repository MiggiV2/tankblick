package de.mymiggi.tankblick.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import de.mymiggi.tankblick.domain.FuelType
import de.mymiggi.tankblick.domain.SortMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: TestScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        scope = TestScope(UnconfinedTestDispatcher())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            tempFolder.newFile("settings.preferences_pb")
        }
        store = SettingsStore(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `starts with usable defaults`() = runTest {
        val settings = store.settings.first()

        assertEquals(FuelType.E10, settings.fuelType)
        assertEquals(5, settings.radiusKm)
        assertEquals(SortMode.PRICE, settings.sortMode)
        assertNull(settings.navAppPackage)
    }

    @Test
    fun `remembers the selected fuel type`() = runTest {
        store.setFuelType(FuelType.DIESEL)

        assertEquals(FuelType.DIESEL, store.settings.first().fuelType)
    }

    @Test
    fun `remembers the selected sort mode`() = runTest {
        store.setSortMode(SortMode.DISTANCE)

        assertEquals(SortMode.DISTANCE, store.settings.first().sortMode)
    }

    @Test
    fun `remembers the radius`() = runTest {
        store.setRadiusKm(12)

        assertEquals(12, store.settings.first().radiusKm)
    }

    /**
     * Tankerkönig refuses anything above 25 km, so the store clamps instead of
     * letting a bad value reach the API and come back as an error.
     */
    @Test
    fun `clamps the radius to the range the api allows`() = runTest {
        store.setRadiusKm(99)
        assertEquals(25, store.settings.first().radiusKm)

        store.setRadiusKm(0)
        assertEquals(1, store.settings.first().radiusKm)
    }

    @Test
    fun `clamps a radius that was persisted out of range`() = runTest {
        dataStore.edit { it[SettingsStore.RADIUS_KM] = 500 }

        assertEquals(25, store.settings.first().radiusKm)
    }

    @Test
    fun `falls back to defaults when a stored enum name is unknown`() = runTest {
        dataStore.edit {
            it[SettingsStore.FUEL_TYPE] = "LPG"
            it[SettingsStore.SORT_MODE] = "RANDOM"
        }

        val settings = store.settings.first()
        assertEquals(FuelType.E10, settings.fuelType)
        assertEquals(SortMode.PRICE, settings.sortMode)
    }

    @Test
    fun `remembers the chosen navigation app`() = runTest {
        store.setNavAppPackage("app.organicmaps")

        assertEquals("app.organicmaps", store.settings.first().navAppPackage)
    }

    /** Null means "ask every time", which is also what an uninstall falls back to. */
    @Test
    fun `clears the chosen navigation app`() = runTest {
        store.setNavAppPackage("app.organicmaps")

        store.setNavAppPackage(null)

        assertNull(store.settings.first().navAppPackage)
    }
}
