package de.mymiggi.tankblick.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import de.mymiggi.tankblick.R
import de.mymiggi.tankblick.domain.Age
import kotlinx.coroutines.delay

/**
 * How old the numbers on screen are, plus the attribution the CC BY licence
 * requires wherever the data is shown.
 */
@Composable
fun DataFooter(
    lastUpdatedAt: Long?,
    modifier: Modifier = Modifier,
    now: Long = rememberTickingClock(),
) {
    Column(
        modifier = modifier
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
            // Read once in the about screen is enough; on every list it is noise.
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Composable
fun Age.text(): String = when (this) {
    Age.JustNow -> stringResource(R.string.age_just_now)
    is Age.Minutes -> pluralStringResource(R.plurals.age_minutes, value, value)
    is Age.Hours -> pluralStringResource(R.plurals.age_hours, value, value)
    is Age.Days -> pluralStringResource(R.plurals.age_days, value, value)
}

/**
 * A clock that advances once a minute, so "as of 3 minutes ago" does not sit
 * there getting quietly wrong while the screen is open. Recomposing once a
 * minute is cheap; reading the system clock every frame is not.
 */
@Composable
fun rememberTickingClock(): Long {
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
