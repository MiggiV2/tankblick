package de.mymiggi.tankblick.di

import android.content.Context
import de.mymiggi.tankblick.BuildConfig
import de.mymiggi.tankblick.data.prefs.AndroidKeystoreCipher
import de.mymiggi.tankblick.data.prefs.ApiKeyStore
import de.mymiggi.tankblick.data.prefs.SettingsStore
import de.mymiggi.tankblick.data.prefs.secretsDataStore
import de.mymiggi.tankblick.data.prefs.settingsDataStore
import de.mymiggi.tankblick.data.local.TankblickDatabase
import de.mymiggi.tankblick.data.repo.StartupTasks
import de.mymiggi.tankblick.data.repo.DefaultStationRepository
import de.mymiggi.tankblick.data.repo.StationRepository
import de.mymiggi.tankblick.location.LocationManagerSource
import de.mymiggi.tankblick.location.LocationSource
import de.mymiggi.tankblick.navapp.NavAppLauncher
import de.mymiggi.tankblick.data.remote.RateLimiter
import de.mymiggi.tankblick.data.remote.TankerkoenigApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

/**
 * The app's object graph, wired by hand.
 *
 * Deliberately not Hilt: this app has a handful of singletons, and avoiding an
 * annotation processor keeps the build simple and easier to reproduce
 * byte-for-byte for F-Droid.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Outlives every screen; for work that belongs to the process, not a view. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val apiKeyStore: ApiKeyStore by lazy {
        ApiKeyStore(appContext.secretsDataStore, AndroidKeystoreCipher())
    }

    val settingsStore: SettingsStore by lazy {
        SettingsStore(appContext.settingsDataStore)
    }

    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            // No logging plugin, in any build type. Request URLs carry the
            // user's coordinates and their API key, and a log is one adb pull
            // away from being someone else's.
            expectSuccess = false

            install(ContentNegotiation) {
                json(
                    Json {
                        // The API is free to add fields; an installed app must
                        // not break when it does.
                        ignoreUnknownKeys = true
                    },
                )
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
            install(UserAgent) {
                // Identifies the app to Tankerkoenig without saying anything
                // about the device or the person using it. Ktor's default would
                // otherwise leak the OkHttp and JVM versions.
                agent = "Tankblick/${BuildConfig.VERSION_NAME}"
            }
        }
    }

    private val tankerkoenigApi: TankerkoenigApi by lazy {
        TankerkoenigApi(
            httpClient = httpClient,
            refreshLimiter = RateLimiter(RateLimiter.REFRESH_INTERVAL_MILLIS),
            detailLimiter = RateLimiter(RateLimiter.DETAIL_INTERVAL_MILLIS),
        )
    }

    private val database: TankblickDatabase by lazy { TankblickDatabase.create(appContext) }

    val stationRepository: StationRepository by lazy {
        DefaultStationRepository(dao = database.stationDao(), api = tankerkoenigApi)
    }

    val startupTasks: StartupTasks by lazy { StartupTasks(stationRepository) }

    val locationSource: LocationSource by lazy { LocationManagerSource(appContext) }

    val navAppLauncher: NavAppLauncher by lazy { NavAppLauncher(appContext) }
}
