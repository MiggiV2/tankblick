package de.mymiggi.tankblick.ui.nearby

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.mymiggi.tankblick.R
import de.mymiggi.tankblick.domain.Age
import de.mymiggi.tankblick.domain.DistanceFormatter
import de.mymiggi.tankblick.domain.FuelType
import de.mymiggi.tankblick.domain.Prices
import de.mymiggi.tankblick.domain.SortMode
import de.mymiggi.tankblick.domain.Station
import de.mymiggi.tankblick.location.LocationManagerSource
import de.mymiggi.tankblick.ui.common.PriceText
import de.mymiggi.tankblick.ui.theme.TankblickTheme

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
    now: Long,
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

            item {
                Footer(lastUpdatedAt = uiState.lastUpdatedAt, now = now)
            }
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
private fun StationRow(
    station: Station,
    fuelType: FuelType,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = station.favoriteLabel ?: station.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = station.subtitle(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        PriceText(price = station.prices[fuelType])

        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (station.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(
                    if (station.isFavorite) R.string.favorite_remove else R.string.favorite_add,
                ),
                tint = if (station.isFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun Station.subtitle(): String {
    val distance = distanceKm?.let { DistanceFormatter.format(it) }
    val status = stringResource(if (isOpen) R.string.station_open else R.string.station_closed)
    return listOfNotNull(distance, status, place.takeIf { it.isNotBlank() })
        .joinToString(" · ")
}

@Composable
private fun MessageBanner(
    message: NearbyMessage,
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    val isError = message !is NearbyMessage.NoResults

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = message.text(), style = MaterialTheme.typography.bodyMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (message) {
                    NearbyMessage.NeedsLocationPermission -> TextButton(onClick = onRequestPermission) {
                        Text(stringResource(R.string.action_grant_permission))
                    }

                    NearbyMessage.LocationDisabled -> TextButton(onClick = onOpenLocationSettings) {
                        Text(stringResource(R.string.action_open_location_settings))
                    }

                    is NearbyMessage.RateLimited, NearbyMessage.MissingApiKey -> Unit

                    else -> TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_dismiss))
                }
            }
        }
    }
}

@Composable
private fun NearbyMessage.text(): String = when (this) {
    NearbyMessage.MissingApiKey -> stringResource(R.string.message_missing_api_key)
    NearbyMessage.NeedsLocationPermission -> stringResource(R.string.message_needs_location_permission)
    NearbyMessage.LocationDisabled -> stringResource(R.string.message_location_disabled)
    NearbyMessage.LocationUnavailable -> stringResource(R.string.message_location_unavailable)
    NearbyMessage.NoResults -> stringResource(R.string.message_no_results)
    NearbyMessage.Offline -> stringResource(R.string.message_offline)
    NearbyMessage.InvalidKey -> stringResource(R.string.message_invalid_key)
    is NearbyMessage.RateLimited ->
        pluralStringResource(R.plurals.message_rate_limited, retryInSeconds.toInt(), retryInSeconds)
    is NearbyMessage.ServerError -> stringResource(R.string.message_server_error, statusCode)
    is NearbyMessage.Failed -> detail?.let { stringResource(R.string.message_failed_with_detail, it) }
        ?: stringResource(R.string.message_failed)
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
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.action_search_nearby),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun Footer(lastUpdatedAt: Long?, now: Long) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lastUpdatedAt?.let {
            Text(
                text = stringResource(R.string.last_updated, Age.of(it, now).text()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.attribution_short),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Composable
private fun Age.text(): String = when (this) {
    Age.JustNow -> stringResource(R.string.age_just_now)
    is Age.Minutes -> pluralStringResource(R.plurals.age_minutes, value, value)
    is Age.Hours -> pluralStringResource(R.plurals.age_hours, value, value)
    is Age.Days -> pluralStringResource(R.plurals.age_days, value, value)
}

private val FuelType.labelRes: Int
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
    TankblickTheme(dynamicColor = false) {
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
            now = 11 * 60_000L,
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
