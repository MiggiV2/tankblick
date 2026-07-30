package de.mymiggi.tankblick.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mymiggi.tankblick.TankblickApplication
import de.mymiggi.tankblick.ui.detail.DetailViewModel
import de.mymiggi.tankblick.ui.favorites.FavoritesViewModel
import de.mymiggi.tankblick.ui.nearby.NearbyViewModel
import de.mymiggi.tankblick.ui.onboarding.OnboardingViewModel

/** Single place where ViewModels are handed their dependencies from the AppContainer. */
object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer {
            RootViewModel(container().apiKeyStore)
        }
        initializer {
            OnboardingViewModel(container().apiKeyStore)
        }
        initializer {
            val container = container()
            NearbyViewModel(
                stationRepository = container.stationRepository,
                apiKeyStore = container.apiKeyStore,
                settingsStore = container.settingsStore,
                locationSource = container.locationSource,
            )
        }
        initializer {
            val container = container()
            FavoritesViewModel(
                stationRepository = container.stationRepository,
                apiKeyStore = container.apiKeyStore,
                settingsStore = container.settingsStore,
            )
        }
    }

    /**
     * The detail screen needs the station id at construction time, so it gets
     * its own factory rather than a shared one.
     */
    fun detailFactory(stationId: String) = viewModelFactory {
        initializer {
            val container = container()
            DetailViewModel(
                stationId = stationId,
                stationRepository = container.stationRepository,
                apiKeyStore = container.apiKeyStore,
            )
        }
    }
}

private fun CreationExtras.container() =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TankblickApplication)
        .container
