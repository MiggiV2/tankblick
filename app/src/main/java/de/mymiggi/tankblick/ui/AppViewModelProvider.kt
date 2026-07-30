package de.mymiggi.tankblick.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mymiggi.tankblick.TankblickApplication
import de.mymiggi.tankblick.ui.nearby.NearbyViewModel
import de.mymiggi.tankblick.ui.onboarding.OnboardingViewModel

/** Single place where ViewModels are handed their dependencies from [AppContainer]. */
object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer {
            RootViewModel(tankblickApplication().container.apiKeyStore)
        }
        initializer {
            OnboardingViewModel(tankblickApplication().container.apiKeyStore)
        }
        initializer {
            val container = tankblickApplication().container
            NearbyViewModel(
                stationRepository = container.stationRepository,
                apiKeyStore = container.apiKeyStore,
                settingsStore = container.settingsStore,
                locationSource = container.locationSource,
            )
        }
    }
}

private fun androidx.lifecycle.viewmodel.CreationExtras.tankblickApplication(): TankblickApplication =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TankblickApplication
