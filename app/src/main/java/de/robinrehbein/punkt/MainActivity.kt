package de.robinrehbein.punkt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import de.robinrehbein.punkt.notify.DailyReminder
import de.robinrehbein.punkt.ui.screens.TimingGameScreen
import de.robinrehbein.punkt.ui.theme.PunktTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Über eine Erinnerung geöffnet? Nur für die Messung, ob die
        // Erinnerung jemanden zurückholt. Nach einer Drehung o. Ä. nicht
        // noch einmal zählen.
        val fromReminder = if (savedInstanceState == null) {
            intent?.getStringExtra(DailyReminder.EXTRA_FROM_REMINDER)
        } else {
            null
        }
        setContent {
            PunktTheme {
                // Hyper-Casual: kein Menü, die App startet direkt im Spiel.
                TimingGameScreen(
                    modifier = Modifier.fillMaxSize(),
                    openedFromReminder = fromReminder
                )
            }
        }
    }
}
