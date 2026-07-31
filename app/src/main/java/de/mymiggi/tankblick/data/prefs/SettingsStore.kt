package de.mymiggi.tankblick.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import de.mymiggi.tankblick.domain.ColorSchemePreference
import de.mymiggi.tankblick.domain.DarkModePreference
import de.mymiggi.tankblick.domain.FuelType
import de.mymiggi.tankblick.domain.SortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User preferences. Nothing in here is a secret, so nothing here is encrypted. */
data class Settings(
    val fuelType: FuelType = FuelType.E10,
    val radiusKm: Int = DEFAULT_RADIUS_KM,
    val sortMode: SortMode = SortMode.PRICE,
    /** Package of the preferred navigation app; `null` means "ask every time". */
    val navAppPackage: String? = null,
    val darkMode: DarkModePreference = DarkModePreference.SYSTEM,
    val colorScheme: ColorSchemePreference = ColorSchemePreference.DYNAMIC,
) {
    companion object {
        const val DEFAULT_RADIUS_KM = 5

        /** Tankerkönig rejects anything larger. */
        const val MIN_RADIUS_KM = 1
        const val MAX_RADIUS_KM = 25
    }
}

class SettingsStore(
    private val dataStore: DataStore<Preferences>,
) {

    val settings: Flow<Settings> = dataStore.data.map { prefs ->
        Settings(
            fuelType = prefs[FUEL_TYPE].toFuelType(),
            // Clamped on read as well as on write: a value could have been
            // persisted by an older build with a different range.
            radiusKm = (prefs[RADIUS_KM] ?: Settings.DEFAULT_RADIUS_KM).clampRadius(),
            sortMode = prefs[SORT_MODE].toSortMode(),
            navAppPackage = prefs[NAV_APP_PACKAGE],
            darkMode = prefs[DARK_MODE].toDarkMode(),
            colorScheme = ColorSchemePreference.fromName(prefs[COLOR_SCHEME]),
        )
    }

    suspend fun setFuelType(fuelType: FuelType) {
        dataStore.edit { it[FUEL_TYPE] = fuelType.name }
    }

    suspend fun setRadiusKm(radiusKm: Int) {
        dataStore.edit { it[RADIUS_KM] = radiusKm.clampRadius() }
    }

    suspend fun setSortMode(sortMode: SortMode) {
        dataStore.edit { it[SORT_MODE] = sortMode.name }
    }

    suspend fun setDarkMode(darkMode: DarkModePreference) {
        dataStore.edit { it[DARK_MODE] = darkMode.name }
    }

    suspend fun setColorScheme(colorScheme: ColorSchemePreference) {
        dataStore.edit { it[COLOR_SCHEME] = colorScheme.name }
    }

    suspend fun setNavAppPackage(packageName: String?) {
        dataStore.edit {
            if (packageName == null) it.remove(NAV_APP_PACKAGE) else it[NAV_APP_PACKAGE] = packageName
        }
    }

    private fun Int.clampRadius(): Int =
        coerceIn(Settings.MIN_RADIUS_KM, Settings.MAX_RADIUS_KM)

    private fun String?.toFuelType(): FuelType =
        FuelType.entries.firstOrNull { it.name == this } ?: FuelType.E10

    private fun String?.toSortMode(): SortMode =
        SortMode.entries.firstOrNull { it.name == this } ?: SortMode.PRICE

    private fun String?.toDarkMode(): DarkModePreference =
        DarkModePreference.entries.firstOrNull { it.name == this } ?: DarkModePreference.SYSTEM

    companion object {
        val FUEL_TYPE = stringPreferencesKey("fuel_type")
        val RADIUS_KM = intPreferencesKey("radius_km")
        val SORT_MODE = stringPreferencesKey("sort_mode")
        val NAV_APP_PACKAGE = stringPreferencesKey("nav_app_package")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val COLOR_SCHEME = stringPreferencesKey("color_scheme")
    }
}
