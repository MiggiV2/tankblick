package de.mymiggi.tankblick.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.mymiggi.tankblick.R
import de.mymiggi.tankblick.domain.Prices
import de.mymiggi.tankblick.domain.Station
import de.mymiggi.tankblick.ui.common.DataFooter
import de.mymiggi.tankblick.ui.common.MessageBanner
import de.mymiggi.tankblick.ui.common.StationRow
import de.mymiggi.tankblick.ui.theme.TankblickPreviewTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    uiState: FavoritesUiState,
    onRefresh: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onStationClick: (String) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            uiState.message?.let { message ->
                item {
                    MessageBanner(
                        message = message,
                        onDismiss = onDismissMessage,
                        onRetry = onRefresh,
                    )
                }
            }

            if (uiState.stations.isEmpty()) {
                item { EmptyState() }
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
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.favorites_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.favorites_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FavoritesScreenPreview() {
    TankblickPreviewTheme() {
        FavoritesScreen(
            uiState = FavoritesUiState(
                stations = listOf(
                    Station(
                        id = "a",
                        name = "ARAL Berlin",
                        brand = "ARAL",
                        street = "Hauptstraße",
                        houseNumber = "1",
                        postCode = "10407",
                        place = "Berlin",
                        latitude = 52.5,
                        longitude = 13.4,
                        isOpen = true,
                        prices = Prices(e5 = 1.859, e10 = 1.799, diesel = 1.749),
                        isFavorite = true,
                        favoriteLabel = "Zuhause",
                    ),
                ),
                lastUpdatedAt = 0L,
            ),
            onRefresh = {},
            onToggleFavorite = {},
            onStationClick = {},
            onDismissMessage = {},
        )
    }
}
