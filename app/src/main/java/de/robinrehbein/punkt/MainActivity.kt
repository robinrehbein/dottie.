package de.robinrehbein.punkt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.robinrehbein.punkt.game.GameMode
import de.robinrehbein.punkt.ui.screens.GravityGameScreen
import de.robinrehbein.punkt.ui.screens.TimingGameScreen
import de.robinrehbein.punkt.ui.theme.PunktTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PunktTheme {
                // Hyper-Casual: kein Menü, die App startet direkt im Spiel.
                // Zwei Spielprinzip-Kandidaten, umschaltbar auf dem Ready-Screen.
                var mode by remember { mutableStateOf(GameMode.GRAVITY_FLIP) }
                when (mode) {
                    GameMode.GRAVITY_FLIP -> GravityGameScreen(
                        onSwitchMode = { mode = GameMode.TIME_STOP },
                        modifier = Modifier.fillMaxSize()
                    )
                    GameMode.TIME_STOP -> TimingGameScreen(
                        onSwitchMode = { mode = GameMode.GRAVITY_FLIP },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
