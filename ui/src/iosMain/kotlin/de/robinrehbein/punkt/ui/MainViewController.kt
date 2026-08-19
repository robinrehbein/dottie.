package de.robinrehbein.punkt.ui

import androidx.compose.ui.window.ComposeUIViewController
import de.robinrehbein.punkt.ui.data.GameStore
import de.robinrehbein.punkt.ui.data.IosKeyValueStore
import de.robinrehbein.punkt.ui.platform.IosFeedback
import de.robinrehbein.punkt.ui.platform.IosReminder
import de.robinrehbein.punkt.ui.platform.IosSounds
import de.robinrehbein.punkt.ui.platform.PlatformHooks
import de.robinrehbein.punkt.ui.screens.GameScreen
import de.robinrehbein.punkt.ui.theme.PunktTheme
import platform.UIKit.UIViewController

/**
 * Der Einstiegspunkt der iPhone-App.
 *
 * Mehr ist es nicht: derselbe [GameScreen], den auch die Android-App
 * zeigt, mit den Diensten dieser Plattform. Was `PlatformHooks` nicht
 * bekommt, heisst "kein Store-Oekosystem" — keine Werbung, kein Kauf,
 * keine Bestenlisten, kein Teilen. Die Oberflaeche zeigt diese Zeilen
 * dann gar nicht erst an, statt tote Knoepfe zu bieten.
 *
 * Die eine Zeile, die iOS fuellt, ist die Tages-Erinnerung: Sie braucht
 * keinen Store, nur das Betriebssystem (siehe [IosReminder]).
 *
 * Bis v2.24 lagen hier 2 900 Zeilen SpriteKit, die dieselbe Oberflaeche
 * ein zweites Mal bauten.
 */
fun MainViewController(): UIViewController {
    val store = GameStore(IosKeyValueStore())
    val sounds = IosSounds()
    val feedback = IosFeedback()
    val reminder = IosReminder(store)
    // Wie die Android-Schale beim Start: Wer erinnert werden will,
    // bekommt seine Anmeldung idempotent aufgefrischt — und verliert den
    // Wunsch, wenn die Berechtigung inzwischen zurueckgenommen wurde.
    reminder.refresh()
    return ComposeUIViewController {
        PunktTheme {
            GameScreen(
                store = store,
                sounds = sounds,
                feedback = feedback,
                hooks = PlatformHooks(
                    reminderSupported = true,
                    setReminder = reminder::set
                )
            )
        }
    }
}
