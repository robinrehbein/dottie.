package de.robinrehbein.punkt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import de.robinrehbein.punkt.ui.screens.GameScreen
import de.robinrehbein.punkt.ui.theme.PunktTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PunktTheme {
                // Hyper-Casual: kein Menü, die App startet direkt im Spiel.
                GameScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
