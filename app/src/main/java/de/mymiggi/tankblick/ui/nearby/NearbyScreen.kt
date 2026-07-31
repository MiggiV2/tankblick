package de.mymiggi.tankblick.ui.nearby

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.mymiggi.tankblick.R
import de.mymiggi.tankblick.domain.FuelType
import de.mymiggi.tankblick.domain.Prices
import de.mymiggi.tankblick.domain.SortMode
import de.mymiggi.tankblick.domain.Station
import de.mymiggi.tankblick.location.LocationManagerSource
import de.mymiggi.tankblick.ui.common.DataFooter
import de.mymiggi.tankblick.ui.common.MessageBanner
import de.mymiggi.tankblick.ui.common.StationRow
import de.mymiggi.tankblick.ui.theme.TankblickPreviewTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(
    uiState: NearbyUiState,
    onRefresh: () -> Unit,
    onFuelTypeChange: (FuelType) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onStationClick: (String) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        // Retry straight away when the user says yes, so granting the
        // permission has the effect they expected.
        if (granted.values.any { it }) onRefresh()
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                FuelAndSortControls(
                    fuelType = uiState.fuelType,
                    sortMode = uiState.sortMode,
                    onFuelTypeChange = onFuelTypeChange,
                    onSortModeChange = onSortModeChange,
                )
            }

            uiState.message?.let { message ->
                item {
                    MessageBanner(
                        message = message,
                        onDismiss = onDismissMessage,
                        onRequestPermission = {
                            permissionLauncher.launch(LocationManagerSource.LOCATION_PERMISSIONS)
                        },
                        onOpenLocationSettings = {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        },
                        onRetry = onRefresh,
                    )
                }
            }

            if (uiState.stations.isEmpty()) {
                item { EmptyState(onRefresh = onRefresh) }
            }

            items(uiState.stations, key = { it.id }) { station ->
                StationRow(
                    station = station,
                    fuelType = uiState.fuelType,
                    onClick = { onStationClick(station.id) },
                    onToggleFavorite = { onToggleFavorite(station.id) },
                )
                HorizontalDivider()
            }

            item { DataFooter(lastUpdatedAt = uiState.lastUpdatedAt) }
        }
    }
}

@Composable
private fun FuelAndSortControls(
    fuelType: FuelType,
    sortMode: SortMode,
    onFuelTypeChange: (FuelType) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FuelType.entries.forEach { type ->
                FilterChip(
                    selected = type == fuelType,
                    onClick = { onFuelTypeChange(type) },
                    label = { Text(stringResource(type.labelRes)) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SortMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == sortMode,
                    onClick = { onSortModeChange(mode) },
                    label = { Text(stringResource(mode.labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.nearby_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.nearby_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(R.string.action_search_nearby),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

internal val FuelType.labelRes: Int
    get() = when (this) {
        FuelType.E5 -> R.string.fuel_e5
        FuelType.E10 -> R.string.fuel_e10
        FuelType.DIESEL -> R.string.fuel_diesel
    }

private val SortMode.labelRes: Int
    get() = when (this) {
        SortMode.PRICE -> R.string.sort_price
        SortMode.DISTANCE -> R.string.sort_distance
    }

@Preview(showBackground = true)
@Composable
private fun NearbyScreenPreview() {
    TankblickPreviewTheme() {
        NearbyScreen(
            uiState = NearbyUiState(
                stations = listOf(
                    previewStation("a", "ARAL", 1.799, 0.4, true),
                    previewStation("b", "Shell", 1.829, 1.2, true),
                    previewStation("c", "JET", null, 3.8, false),
                ),
                lastUpdatedAt = 0L,
            ),
            onRefresh = {},
            onFuelTypeChange = {},
            onSortModeChange = {},
            onToggleFavorite = {},
            onStationClick = {},
            onDismissMessage = {},
        )
    }
}

private fun previewStation(
    id: String,
    brand: String,
    e10: Double?,
    distanceKm: Double,
    isOpen: Boolean,
) = Station(
    id = id,
    name = "$brand Berlin",
    brand = brand,
    street = "Hauptstraße",
    houseNumber = "1",
    postCode = "10407",
    place = "Berlin",
    latitude = 52.5,
    longitude = 13.4,
    isOpen = isOpen,
    distanceKm = distanceKm,
    prices = Prices(e5 = e10?.plus(0.06), e10 = e10, diesel = e10?.minus(0.05)),
    fetchedAt = 0L,
)
