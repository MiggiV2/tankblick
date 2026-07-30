package de.mymiggi.tankblick.ui.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import de.mymiggi.tankblick.R
import de.mymiggi.tankblick.domain.PriceFormatter
import de.mymiggi.tankblick.ui.theme.PriceTextStyle

/**
 * A price the way it looks on a German pump: 1,79 with a small raised 9.
 *
 * Screen readers get the plain number instead - "one comma seven nine
 * superscript nine" is not what anyone wants to hear.
 */
@Composable
fun PriceText(
    price: Double?,
    modifier: Modifier = Modifier,
) {
    if (price == null) {
        Text(
            text = stringResource(R.string.price_unavailable),
            style = LocalTextStyle.current,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    val (main, tenth) = PriceFormatter.split(price)
    val spoken = stringResource(R.string.price_spoken, PriceFormatter.format(price))

    Text(
        text = buildAnnotatedString {
            append(main)
            withStyle(SpanStyle(fontSize = 15.sp, baselineShift = SuperscriptShift)) {
                append(tenth)
            }
        },
        style = PriceTextStyle,
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    )
}

private val SuperscriptShift = androidx.compose.ui.text.style.BaselineShift.Superscript
