package de.robinrehbein.punkt.ui

import androidx.compose.ui.window.ComposeUIViewController
import de.robinrehbein.punkt.ui.data.GameStore
import de.robinrehbein.punkt.ui.data.IosKeyValueStore
import de.robinrehbein.punkt.ui.platform.IosFeedback
import de.robinrehbein.punkt.ui.platform.IosShare
import de.robinrehbein.punkt.ui.platform.IosSounds
import de.robinrehbein.punkt.ui.platform.PlatformHooks
import de.robinrehbein.punkt.ui.screens.GameScreen
import de.robinrehbein.punkt.ui.theme.PunktTheme
import platform.UIKit.UIViewController

/**
 * Der Einstiegspunkt der iPhone-App.
 *
 * Mehr ist es nicht: derselbe [GameScreen], den auch die Android-App
 * zeigt, mit den Diensten dieser Plattform. Was hier fehlt, heisst "kein
 * Store-Oekosystem" — keine Werbung, kein Kauf, keine Bestenlisten. Die
 * Oberflaeche zeigt diese Zeilen dann gar nicht erst an, statt tote
 * Knoepfe zu bieten.
 *
 * Das Teilen war bis v2.26 in derselben Liste, aus einem anderen Grund:
 * Die Score-Karte wurde mit `android.graphics` gezeichnet und gab es
 * deshalb nur einmal. Seit sie in `:ui` entsteht, gibt es sie hier
 * auch — und der TEILEN-Knopf im Game-Over erscheint mit ihr.
 *
 * Bis v2.24 lagen hier 2 900 Zeilen SpriteKit, die dieselbe Oberflaeche
 * ein zweites Mal bauten.
 */
fun MainViewController(): UIViewController {
    val store = GameStore(IosKeyValueStore())
    val sounds = IosSounds()
    val feedback = IosFeedback()
    // Das Teilen-Blatt braucht einen Controller, von dem aus es
    // aufgeht — und das ist genau der, den wir hier bauen. Deshalb die
    // Schleife ueber sich selbst: Beim Bauen gibt es ihn noch nicht,
    // beim Tippen auf TEILEN laengst.
    var wurzel: UIViewController? = null
    val controller = ComposeUIViewController {
        PunktTheme {
            GameScreen(
                store = store,
                sounds = sounds,
                feedback = feedback,
                hooks = PlatformHooks(
                    onShare = { anfrage ->
                        wurzel?.let { IosShare.present(it, anfrage.image, anfrage.text) }
                    }
                )
            )
        }
    }
    wurzel = controller
    return controller
}
