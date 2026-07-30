package de.mymiggi.tankblick.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.mymiggi.tankblick.R
import de.mymiggi.tankblick.TankblickApplication
import de.mymiggi.tankblick.navapp.NavAppLauncher
import de.mymiggi.tankblick.ui.detail.DetailScreen
import de.mymiggi.tankblick.ui.detail.DetailViewModel
import de.mymiggi.tankblick.ui.favorites.FavoritesScreen
import de.mymiggi.tankblick.ui.favorites.FavoritesViewModel
import de.mymiggi.tankblick.ui.nearby.NearbyScreen
import de.mymiggi.tankblick.ui.nearby.NearbyViewModel
import de.mymiggi.tankblick.ui.onboarding.OnboardingScreen
import de.mymiggi.tankblick.ui.settings.SettingsScreen
import de.mymiggi.tankblick.ui.settings.SettingsViewModel
import kotlinx.serialization.Serializable

@Serializable
object NearbyRoute

@Serializable
object FavoritesRoute

@Serializable
object SettingsRoute

@Serializable
data class DetailRoute(val stationId: String)

/**
 * Root of the Compose tree. Decides between onboarding and the app proper.
 */
@Composable
fun TankblickApp(
    modifier: Modifier = Modifier,
    viewModel: RootViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (uiState) {
        // Nothing on purpose: the key store answers within a frame or two, and
        // a spinner would only flicker.
        RootUiState.Loading -> Box(modifier.fillMaxSize())
        RootUiState.NeedsApiKey -> Box(modifier.fillMaxSize()) { OnboardingScreen() }
        RootUiState.Ready -> MainScaffold(modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isDetail = currentDestination?.hasRoute<DetailRoute>() == true

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    if (isDetail) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            // Hidden on the detail screen: it is a place you came from
            // somewhere, not a tab.
            if (!isDetail) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentDestination?.hasRoute<NearbyRoute>() == true,
                        onClick = { navController.switchTab(NearbyRoute) },
                        icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_nearby)) },
                    )
                    NavigationBarItem(
                        selected = currentDestination?.hasRoute<FavoritesRoute>() == true,
                        onClick = { navController.switchTab(FavoritesRoute) },
                        icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_favorites)) },
                    )
                    NavigationBarItem(
                        selected = currentDestination?.hasRoute<SettingsRoute>() == true,
                        onClick = { navController.switchTab(SettingsRoute) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_settings)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NearbyRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<NearbyRoute> {
                NearbyTab(onStationClick = { navController.navigate(DetailRoute(it)) })
            }
            composable<FavoritesRoute> {
                FavoritesTab(onStationClick = { navController.navigate(DetailRoute(it)) })
            }
            composable<SettingsRoute> {
                SettingsTab()
            }
            composable<DetailRoute> { entry ->
                StationDetailPane(stationId = entry.toRoute<DetailRoute>().stationId)
            }
        }
    }
}

/** Tab behaviour: never stack tabs, and keep each tab's scroll position. */
private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun NearbyTab(
    onStationClick: (String) -> Unit,
    viewModel: NearbyViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NearbyScreen(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onFuelTypeChange = viewModel::setFuelType,
        onSortModeChange = viewModel::setSortMode,
        onToggleFavorite = viewModel::toggleFavorite,
        onStationClick = onStationClick,
        onDismissMessage = viewModel::dismissMessage,
    )
}

@Composable
private fun FavoritesTab(
    onStationClick: (String) -> Unit,
    viewModel: FavoritesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FavoritesScreen(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onToggleFavorite = viewModel::toggleFavorite,
        onStationClick = onStationClick,
        onDismissMessage = viewModel::dismissMessage,
    )
}

@Composable
private fun SettingsTab(
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Apps get installed and removed while the app sits in the background.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshNavApps()
        onPauseOrDispose {}
    }

    SettingsScreen(
        uiState = uiState,
        onRadiusChange = viewModel::setRadiusKm,
        onNavAppChange = viewModel::setNavApp,
        onReplaceApiKey = viewModel::replaceApiKey,
        onForgetApiKey = viewModel::forgetApiKey,
    )
}

@Composable
private fun StationDetailPane(stationId: String) {
    val context = LocalContext.current
    val application = context.applicationContext as TankblickApplication
    val viewModel: DetailViewModel = viewModel(
        key = stationId,
        factory = AppViewModelProvider.detailFactory(stationId),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by application.container.settingsStore.settings
        .collectAsStateWithLifecycle(initialValue = null)

    DetailScreen(
        uiState = uiState,
        onToggleFavorite = viewModel::toggleFavorite,
        onLabelChange = viewModel::setLabel,
        onNavigate = {
            uiState.station?.let { station ->
                NavAppLauncher(context).launch(
                    latitude = station.latitude,
                    longitude = station.longitude,
                    name = station.displayName,
                    preferredPackage = settings?.navAppPackage,
                )
            }
        },
        onRetry = viewModel::loadDetails,
        onDismissMessage = viewModel::dismissMessage,
    )
}
