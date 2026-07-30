package de.mymiggi.tankblick.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.mymiggi.tankblick.TankblickApplication
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
    }
}

private fun androidx.lifecycle.viewmodel.CreationExtras.tankblickApplication(): TankblickApplication =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TankblickApplication
