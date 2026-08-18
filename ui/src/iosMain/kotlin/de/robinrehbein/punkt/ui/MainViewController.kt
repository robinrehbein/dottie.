package de.robinrehbein.punkt.ui

import androidx.compose.ui.window.ComposeUIViewController
import de.robinrehbein.punkt.ui.data.GameStore
import de.robinrehbein.punkt.ui.data.IosKeyValueStore
import de.robinrehbein.punkt.ui.platform.IosFeedback
import de.robinrehbein.punkt.ui.platform.IosSounds
import de.robinrehbein.punkt.ui.platform.PlatformHooks
import de.robinrehbein.punkt.ui.screens.GameScreen
import de.robinrehbein.punkt.ui.theme.PunktTheme
import platform.UIKit.UIViewController

/**
 * Der Einstiegspunkt der iPhone-App.
 *
 * Mehr ist es nicht: derselbe [GameScreen], den auch die Android-App
 * zeigt, mit den drei Diensten dieser Plattform. `PlatformHooks()` ohne
 * Argumente heisst "kein Store-Oekosystem" — keine Werbung, kein Kauf,
 * keine Bestenlisten, kein Teilen. Die Oberflaeche zeigt diese Zeilen
 * dann gar nicht erst an, statt tote Knoepfe zu bieten.
 *
 * Bis v2.24 lagen hier 2 900 Zeilen SpriteKit, die dieselbe Oberflaeche
 * ein zweites Mal bauten.
 */
fun MainViewController(): UIViewController {
    val store = GameStore(IosKeyValueStore())
    val sounds = IosSounds()
    val feedback = IosFeedback()
    return ComposeUIViewController {
        PunktTheme {
            GameScreen(store = store, sounds = sounds, feedback = feedback)
        }
    }
}
