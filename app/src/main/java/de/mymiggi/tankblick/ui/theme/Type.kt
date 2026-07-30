package de.mymiggi.tankblick.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Material 3 defaults plus one deviation: prices are the reason this app exists,
 * so they get a dedicated tabular style that stays aligned across list rows.
 */
val TankblickTypography = Typography()

/** Large price readout on list rows and the detail screen, e.g. "1,679". */
val PriceTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 26.sp,
    lineHeight = 30.sp,
    textAlign = TextAlign.End,
)
