package de.mymiggi.tankblick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mymiggi.tankblick.data.prefs.Settings
import de.mymiggi.tankblick.ui.TankblickApp
import de.mymiggi.tankblick.ui.theme.TankblickTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsStore = (application as TankblickApplication).container.settingsStore
            // Defaults until the first read lands, so the very first frame is
            // themed rather than flashing an unstyled one.
            val settings by settingsStore.settings
                .collectAsStateWithLifecycle(initialValue = Settings())

            TankblickTheme(
                darkMode = settings.darkMode,
                colorScheme = settings.colorScheme,
            ) {
                TankblickApp()
            }
        }
    }
}
