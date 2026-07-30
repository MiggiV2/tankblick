package de.mymiggi.tankblick

import android.app.Application
import de.mymiggi.tankblick.di.AppContainer
import kotlinx.coroutines.launch

/**
 * Owns the app-wide object graph. Dependency wiring is done by hand in
 * [AppContainer] rather than with Hilt or Koin: the app has a handful of
 * singletons, and skipping the annotation processor keeps the F-Droid build
 * simple and reproducible.
 */
class TankblickApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Retention has to actually run, or "we only keep 30 days" is just a
        // sentence in the README. Off the main thread, and never blocking start-up.
        container.applicationScope.launch {
            container.startupTasks.run()
        }
    }
}
