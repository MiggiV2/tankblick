package de.mymiggi.tankblick.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.mymiggi.tankblick.R
import de.mymiggi.tankblick.domain.FuelType
import de.mymiggi.tankblick.domain.OpeningEntry
import de.mymiggi.tankblick.domain.OpeningHours
import de.mymiggi.tankblick.domain.Prices
import de.mymiggi.tankblick.domain.Station
import de.mymiggi.tankblick.ui.common.DataFooter
import de.mymiggi.tankblick.ui.common.MessageBanner
import de.mymiggi.tankblick.ui.common.PriceText
import de.mymiggi.tankblick.ui.nearby.labelRes
import de.mymiggi.tankblick.ui.theme.TankblickPreviewTheme

@Composable
fun DetailScreen(
    uiState: DetailUiState,
    onToggleFavorite: () -> Unit,
    onLabelChange: (String) -> Unit,
    onNavigate: () -> Unit,
    onRetry: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val station = uiState.station

    Column(
        modifier = modifier
            .fillMaxSize()
            // With edge-to-edge, adjustResize no longer shrinks the content on
            // its own. Without this the label field sat behind the keyboard and
            // you could not see what you were typing.
            .imePadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        uiState.message?.let {
            MessageBanner(message = it, onDismiss = onDismissMessage, onRetry = onRetry)
        }

        if (station == null) {
            Text(
                text = stringResource(R.string.detail_unknown_station),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }

        Header(station)
        PriceTable(station)
        OpeningHoursSection(uiState.openingHours)
        Actions(
            station = station,
            onToggleFavorite = onToggleFavorite,
            onNavigate = onNavigate,
        )
        if (station.isFavorite) {
            LabelField(current = station.favoriteLabel, onLabelChange = onLabelChange)
        }
        DataFooter(lastUpdatedAt = station.fetchedAt)
    }
}

@Composable
private fun Header(station: Station) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = station.displayName, style = MaterialTheme.typography.headlineSmall)
        if (station.name.isNotBlank() && station.name != station.displayName) {
            Text(
                text = station.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = station.address, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(
                if (station.isOpen) R.string.station_open else R.string.station_closed,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (station.isOpen) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

@Composable
private fun PriceTable(station: Station) {
    Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            FuelType.entries.forEachIndexed { index, fuelType ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(fuelType.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    PriceText(price = station.prices[fuelType])
                }
                if (index < FuelType.entries.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun OpeningHoursSection(openingHours: OpeningHours?) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.detail_opening_hours),
            style = MaterialTheme.typography.titleMedium,
        )

        when {
            openingHours == null -> Text(
                text = stringResource(R.string.detail_opening_hours_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            openingHours.isWholeDay -> Text(
                text = stringResource(R.string.detail_open_all_day),
                style = MaterialTheme.typography.bodyMedium,
            )

            openingHours.isUnknown -> Text(
                text = stringResource(R.string.detail_opening_hours_unknown),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> openingHours.rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = row.days,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = row.hours, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        openingHours?.exceptions?.forEach { exception ->
            Text(
                text = exception,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Actions(
    station: Station,
    onToggleFavorite: () -> Unit,
    onNavigate: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onNavigate, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Place, contentDescription = null)
            Text(
                text = stringResource(R.string.action_navigate),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        OutlinedButton(onClick = onToggleFavorite, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = if (station.isFavorite) {
                    Icons.Filled.Favorite
                } else {
                    Icons.Outlined.FavoriteBorder
                },
                contentDescription = null,
            )
            Text(
                text = stringResource(
                    if (station.isFavorite) R.string.favorite_remove else R.string.favorite_add,
                ),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun LabelField(current: String?, onLabelChange: (String) -> Unit) {
    var text by remember(current) { mutableStateOf(current.orEmpty()) }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onLabelChange(it)
        },
        label = { Text(stringResource(R.string.detail_label)) },
        supportingText = { Text(stringResource(R.string.detail_label_hint)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    )
}

@Preview(showBackground = true)
@Composable
private fun DetailScreenPreview() {
    TankblickPreviewTheme() {
        DetailScreen(
            uiState = DetailUiState(
                station = Station(
                    id = "a",
                    name = "TotalEnergies Berlin",
                    brand = "TotalEnergies",
                    street = "Margarete-Sommer-Str.",
                    houseNumber = "2",
                    postCode = "10407",
                    place = "Berlin",
                    latitude = 52.53,
                    longitude = 13.44,
                    isOpen = true,
                    prices = Prices(e5 = 1.859, e10 = 1.799, diesel = null),
                    isFavorite = true,
                    favoriteLabel = "Zuhause",
                ),
                openingHours = OpeningHours.of(
                    wholeDay = false,
                    entries = listOf(
                        OpeningEntry("Mo-Fr", "05:00:00", "23:30:00"),
                        OpeningEntry("Samstag, Sonntag, Feiertag", "06:00:00", "23:30:00"),
                    ),
                    overrides = listOf("24.12.2026, 06:00-14:00"),
                ),
            ),
            onToggleFavorite = {},
            onLabelChange = {},
            onNavigate = {},
            onRetry = {},
            onDismissMessage = {},
        )
    }
}
