package de.robinrehbein.punkt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import de.robinrehbein.punkt.ui.screens.GameScreen
import de.robinrehbein.punkt.ui.screens.StartScreen
import de.robinrehbein.punkt.ui.theme.PunktTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PunktTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PunktApp()
                }
            }
        }
    }
}

@Composable
fun PunktApp() {
    var currentScreen by remember { mutableStateOf("start") }
    
    when (currentScreen) {
        "start" -> {
            StartScreen(
                onStartGame = {
                    currentScreen = "game"
                }
            )
        }
        "game" -> {
            GameScreen(
                onBackToStart = {
                    currentScreen = "start"
                }
            )
        }
    }
}
