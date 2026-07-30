package de.mymiggi.tankblick.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mymiggi.tankblick.R
import de.mymiggi.tankblick.ui.nearby.NearbyScreen
import de.mymiggi.tankblick.ui.nearby.NearbyViewModel
import de.mymiggi.tankblick.ui.onboarding.OnboardingScreen
import kotlinx.coroutines.delay

/**
 * Root of the Compose tree. Decides between onboarding and the app proper; the
 * navigation graph moves in here once the favourites and detail screens exist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TankblickApp(
    modifier: Modifier = Modifier,
    viewModel: RootViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (uiState == RootUiState.Ready) {
                TopAppBar(title = { Text(stringResource(R.string.app_name)) })
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (uiState) {
                // Nothing on purpose: the key store answers within a frame or
                // two, and a spinner would only flicker.
                RootUiState.Loading -> Unit
                RootUiState.NeedsApiKey -> OnboardingScreen()
                RootUiState.Ready -> NearbyRoute()
            }
        }
    }
}

@Composable
private fun NearbyRoute(
    viewModel: NearbyViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NearbyScreen(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onFuelTypeChange = viewModel::setFuelType,
        onSortModeChange = viewModel::setSortMode,
        onToggleFavorite = viewModel::toggleFavorite,
        // The detail screen arrives in the next milestone.
        onStationClick = {},
        onDismissMessage = viewModel::dismissMessage,
        now = rememberTickingClock(),
    )
}

/**
 * A clock that advances once a minute, so "as of 3 minutes ago" does not sit
 * there getting quietly wrong while the screen is open. Recomposing once a
 * minute is cheap; reading the system clock on every frame is not.
 */
@Composable
private fun rememberTickingClock(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_INTERVAL_MILLIS)
            now = System.currentTimeMillis()
        }
    }
    return now
}

private const val TICK_INTERVAL_MILLIS = 60_000L
