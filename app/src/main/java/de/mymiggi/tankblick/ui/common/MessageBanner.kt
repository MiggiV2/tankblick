package de.mymiggi.tankblick.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mymiggi.tankblick.R
import de.mymiggi.tankblick.ui.nearby.NearbyMessage

/**
 * The one place a [NearbyMessage] turns into words.
 *
 * Shared by every screen that can hit these conditions, so "no connection"
 * never reads differently on the favourites screen than on the list.
 */
@Composable
fun MessageBanner(
    message: NearbyMessage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onRequestPermission: (() -> Unit)? = null,
    onOpenLocationSettings: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
) {
    val isError = message !is NearbyMessage.NoResults

    Card(
        modifier = modifier
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
                    NearbyMessage.NeedsLocationPermission -> onRequestPermission?.let {
                        TextButton(onClick = it) {
                            Text(stringResource(R.string.action_grant_permission))
                        }
                    }

                    NearbyMessage.LocationDisabled -> onOpenLocationSettings?.let {
                        TextButton(onClick = it) {
                            Text(stringResource(R.string.action_open_location_settings))
                        }
                    }

                    // Retrying a rate limit or a missing key changes nothing,
                    // so no button pretends otherwise.
                    is NearbyMessage.RateLimited, NearbyMessage.MissingApiKey -> Unit

                    else -> onRetry?.let {
                        TextButton(onClick = it) { Text(stringResource(R.string.action_retry)) }
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
fun NearbyMessage.text(): String = when (this) {
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
