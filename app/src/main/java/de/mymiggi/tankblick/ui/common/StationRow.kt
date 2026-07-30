package de.mymiggi.tankblick.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mymiggi.tankblick.R
import de.mymiggi.tankblick.domain.DistanceFormatter
import de.mymiggi.tankblick.domain.FuelType
import de.mymiggi.tankblick.domain.Station

/** One station, as it appears in both the nearby list and the favourites list. */
@Composable
fun StationRow(
    station: Station,
    fuelType: FuelType,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
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
                imageVector = if (station.isFavorite) {
                    Icons.Filled.Favorite
                } else {
                    Icons.Outlined.FavoriteBorder
                },
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
    // The label replaces the name in the title, so the name moves down here.
    val name = if (favoriteLabel != null) displayName else place.takeIf { it.isNotBlank() }
    return listOfNotNull(distance, status, name).joinToString(" · ")
}
