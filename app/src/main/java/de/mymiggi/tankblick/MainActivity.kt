package de.mymiggi.tankblick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.mymiggi.tankblick.ui.TankblickApp
import de.mymiggi.tankblick.ui.theme.TankblickTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TankblickTheme {
                TankblickApp()
            }
        }
    }
}
