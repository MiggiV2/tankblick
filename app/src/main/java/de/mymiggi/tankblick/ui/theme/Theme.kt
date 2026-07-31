package de.mymiggi.tankblick.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import de.mymiggi.tankblick.domain.ColorSchemePreference
import de.mymiggi.tankblick.domain.DarkModePreference

@Composable
fun TankblickTheme(
    darkMode: DarkModePreference = DarkModePreference.SYSTEM,
    colorScheme: ColorSchemePreference = ColorSchemePreference.DYNAMIC,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (darkMode) {
        DarkModePreference.SYSTEM -> isSystemInDarkTheme()
        DarkModePreference.LIGHT -> false
        DarkModePreference.DARK -> true
    }

    // Dynamic colour only exists from Android 12. On anything older the choice
    // silently becomes Petrol rather than failing or showing an empty theme.
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDynamic = colorScheme == ColorSchemePreference.DYNAMIC && dynamicAvailable

    val colors = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(LocalContext.current)
        useDynamic -> dynamicLightColorScheme(LocalContext.current)
        darkTheme -> colorScheme.darkScheme()
        else -> colorScheme.lightScheme()
    }

    MaterialTheme(
        colorScheme = colors,
        typography = TankblickTypography,
        content = content,
    )
}

/**
 * For previews and tests, which want a fixed look rather than whatever the
 * device or the stored settings say. A separate name rather than an overload:
 * two overloads with all-default parameters would make a bare
 * `TankblickTheme { }` ambiguous.
 */
@Composable
fun TankblickPreviewTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    TankblickTheme(
        darkMode = if (darkTheme) DarkModePreference.DARK else DarkModePreference.LIGHT,
        colorScheme = if (dynamicColor) {
            ColorSchemePreference.DYNAMIC
        } else {
            ColorSchemePreference.PETROL
        },
        content = content,
    )
}
