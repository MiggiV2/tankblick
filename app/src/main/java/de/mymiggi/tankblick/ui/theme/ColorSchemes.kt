package de.mymiggi.tankblick.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import de.mymiggi.tankblick.domain.ColorSchemePreference

/**
 * The named colour schemes, for people who do not want dynamic colour.
 *
 * Each is a Material 3 tonal palette: the 40/80 tones carry the primary, 90/30
 * the containers, and the neutrals are pulled towards the hue so the surfaces
 * do not look grey next to a saturated primary. Only the roles the app
 * actually paints with are overridden; the rest keeps the Material defaults.
 */
internal fun ColorSchemePreference.lightScheme(): ColorScheme = when (this) {
    ColorSchemePreference.DYNAMIC, ColorSchemePreference.PETROL -> lightColorScheme(
        primary = Color(0xFF006874),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF97F0FF),
        onPrimaryContainer = Color(0xFF001F24),
        secondary = Color(0xFF4A6267),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCDE7EC),
        onSecondaryContainer = Color(0xFF051F23),
        tertiary = Color(0xFF525E7D),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFDAE2FF),
        onTertiaryContainer = Color(0xFF0E1B37),
        background = Color(0xFFF5FAFB),
        onBackground = Color(0xFF171D1E),
        surface = Color(0xFFF5FAFB),
        onSurface = Color(0xFF171D1E),
        surfaceVariant = Color(0xFFDBE4E6),
        onSurfaceVariant = Color(0xFF3F484A),
        outline = Color(0xFF6F797A),
        outlineVariant = Color(0xFFBFC8CA),
    )

    ColorSchemePreference.FOREST -> lightColorScheme(
        primary = Color(0xFF2E6A45),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFB1F1C2),
        onPrimaryContainer = Color(0xFF00210F),
        secondary = Color(0xFF4F6353),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD2E8D4),
        onSecondaryContainer = Color(0xFF0C1F13),
        tertiary = Color(0xFF3A656F),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFBEEAF6),
        onTertiaryContainer = Color(0xFF001F25),
        background = Color(0xFFF6FBF3),
        onBackground = Color(0xFF181D18),
        surface = Color(0xFFF6FBF3),
        onSurface = Color(0xFF181D18),
        surfaceVariant = Color(0xFFDDE5DA),
        onSurfaceVariant = Color(0xFF414941),
        outline = Color(0xFF717970),
        outlineVariant = Color(0xFFC1C9BE),
    )

    ColorSchemePreference.AMBER -> lightColorScheme(
        primary = Color(0xFF7C5800),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDEA6),
        onPrimaryContainer = Color(0xFF271900),
        secondary = Color(0xFF6C5C3F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF5E0BB),
        onSecondaryContainer = Color(0xFF241A04),
        tertiary = Color(0xFF4C6545),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFCDEBC2),
        onTertiaryContainer = Color(0xFF0A2007),
        background = Color(0xFFFFFBF7),
        onBackground = Color(0xFF1E1B16),
        surface = Color(0xFFFFFBF7),
        onSurface = Color(0xFF1E1B16),
        surfaceVariant = Color(0xFFEDE1CF),
        onSurfaceVariant = Color(0xFF4D4639),
        outline = Color(0xFF7F7667),
        outlineVariant = Color(0xFFD0C5B4),
    )

    ColorSchemePreference.PLUM -> lightColorScheme(
        primary = Color(0xFF7B4E7F),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD6FE),
        onPrimaryContainer = Color(0xFF310937),
        secondary = Color(0xFF6B586C),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF4DBF2),
        onSecondaryContainer = Color(0xFF241727),
        tertiary = Color(0xFF825248),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDBD2),
        onTertiaryContainer = Color(0xFF33110B),
        background = Color(0xFFFFF7FA),
        onBackground = Color(0xFF1E1A1E),
        surface = Color(0xFFFFF7FA),
        onSurface = Color(0xFF1E1A1E),
        surfaceVariant = Color(0xFFECDFE8),
        onSurfaceVariant = Color(0xFF4C444C),
        outline = Color(0xFF7E747D),
        outlineVariant = Color(0xFFCFC3CC),
    )
}

internal fun ColorSchemePreference.darkScheme(): ColorScheme = when (this) {
    ColorSchemePreference.DYNAMIC, ColorSchemePreference.PETROL -> darkColorScheme(
        primary = Color(0xFF4FD8EB),
        onPrimary = Color(0xFF00363D),
        primaryContainer = Color(0xFF004F58),
        onPrimaryContainer = Color(0xFF97F0FF),
        secondary = Color(0xFFB1CBD0),
        onSecondary = Color(0xFF1B3438),
        secondaryContainer = Color(0xFF334B4F),
        onSecondaryContainer = Color(0xFFCDE7EC),
        tertiary = Color(0xFFBAC6EA),
        onTertiary = Color(0xFF243047),
        tertiaryContainer = Color(0xFF3A4665),
        onTertiaryContainer = Color(0xFFDAE2FF),
        background = Color(0xFF0E1415),
        onBackground = Color(0xFFDEE3E5),
        surface = Color(0xFF0E1415),
        onSurface = Color(0xFFDEE3E5),
        surfaceVariant = Color(0xFF3F484A),
        onSurfaceVariant = Color(0xFFBFC8CA),
        outline = Color(0xFF899294),
        outlineVariant = Color(0xFF3F484A),
    )

    ColorSchemePreference.FOREST -> darkColorScheme(
        primary = Color(0xFF96D5A7),
        onPrimary = Color(0xFF00391E),
        primaryContainer = Color(0xFF13512F),
        onPrimaryContainer = Color(0xFFB1F1C2),
        secondary = Color(0xFFB6CCB8),
        onSecondary = Color(0xFF223527),
        secondaryContainer = Color(0xFF384B3C),
        onSecondaryContainer = Color(0xFFD2E8D4),
        tertiary = Color(0xFFA2CEDA),
        onTertiary = Color(0xFF01363F),
        tertiaryContainer = Color(0xFF204D56),
        onTertiaryContainer = Color(0xFFBEEAF6),
        background = Color(0xFF101510),
        onBackground = Color(0xFFDFE4DB),
        surface = Color(0xFF101510),
        onSurface = Color(0xFFDFE4DB),
        surfaceVariant = Color(0xFF414941),
        onSurfaceVariant = Color(0xFFC1C9BE),
        outline = Color(0xFF8B9389),
        outlineVariant = Color(0xFF414941),
    )

    ColorSchemePreference.AMBER -> darkColorScheme(
        primary = Color(0xFFF6BE48),
        onPrimary = Color(0xFF412D00),
        primaryContainer = Color(0xFF5E4200),
        onPrimaryContainer = Color(0xFFFFDEA6),
        secondary = Color(0xFFD8C4A0),
        onSecondary = Color(0xFF3B2F15),
        secondaryContainer = Color(0xFF53452A),
        onSecondaryContainer = Color(0xFFF5E0BB),
        tertiary = Color(0xFFB1CFA7),
        onTertiary = Color(0xFF1E361A),
        tertiaryContainer = Color(0xFF344D2F),
        onTertiaryContainer = Color(0xFFCDEBC2),
        background = Color(0xFF16130E),
        onBackground = Color(0xFFE9E1D9),
        surface = Color(0xFF16130E),
        onSurface = Color(0xFFE9E1D9),
        surfaceVariant = Color(0xFF4D4639),
        onSurfaceVariant = Color(0xFFD0C5B4),
        outline = Color(0xFF999080),
        outlineVariant = Color(0xFF4D4639),
    )

    ColorSchemePreference.PLUM -> darkColorScheme(
        primary = Color(0xFFEBB5EC),
        onPrimary = Color(0xFF49204E),
        primaryContainer = Color(0xFF613766),
        onPrimaryContainer = Color(0xFFFFD6FE),
        secondary = Color(0xFFD7BFD6),
        onSecondary = Color(0xFF3A2B3C),
        secondaryContainer = Color(0xFF524153),
        onSecondaryContainer = Color(0xFFF4DBF2),
        tertiary = Color(0xFFF5B8AB),
        onTertiary = Color(0xFF4C251D),
        tertiaryContainer = Color(0xFF663B32),
        onTertiaryContainer = Color(0xFFFFDBD2),
        background = Color(0xFF161216),
        onBackground = Color(0xFFE9E0E5),
        surface = Color(0xFF161216),
        onSurface = Color(0xFFE9E0E5),
        surfaceVariant = Color(0xFF4C444C),
        onSurfaceVariant = Color(0xFFCFC3CC),
        outline = Color(0xFF988E96),
        outlineVariant = Color(0xFF4C444C),
    )
}
