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
import de.robinrehbein.punkt.data.ScoreStore
import de.robinrehbein.punkt.game.GameMode
import de.robinrehbein.punkt.ui.screens.GravityGameScreen
import de.robinrehbein.punkt.ui.screens.TimingGameScreen
import de.robinrehbein.punkt.ui.theme.PunktTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = ScoreStore(this)
        setContent {
            PunktTheme {
                // Hyper-Casual: kein Menü, die App startet direkt im Spiel —
                // und zwar im zuletzt gespielten Modus.
                var mode by remember { mutableStateOf(store.lastMode) }
                fun switchTo(newMode: GameMode) {
                    mode = newMode
                    store.lastMode = newMode
                }
                when (mode) {
                    GameMode.GRAVITY_FLIP -> GravityGameScreen(
                        onSwitchMode = { switchTo(GameMode.TIME_STOP) },
                        modifier = Modifier.fillMaxSize()
                    )
                    GameMode.TIME_STOP -> TimingGameScreen(
                        onSwitchMode = { switchTo(GameMode.GRAVITY_FLIP) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
