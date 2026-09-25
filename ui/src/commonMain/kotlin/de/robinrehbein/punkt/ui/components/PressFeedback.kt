package de.robinrehbein.punkt.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role

/**
 * Der Haptik-Tick eines Knopfdrucks, wie ihn die Bausteine auslösen.
 *
 * Ein eigener, schmaler Typ statt [de.robinrehbein.punkt.ui.platform.GameFeedback]:
 * Knöpfe brauchen nur den Tick, nicht die Spiel-Muster. Angebunden wird
 * er im Spielbildschirm, etwa so:
 *
 * ```
 * CompositionLocalProvider(LocalPressFeedback provides PressFeedback { feedback.tap() }) { … }
 * ```
 */
fun interface PressFeedback {
    fun tap()

    companion object {
        /** Kein Tick, der Standard ohne Anbindung (Tests, Screenshots). */
        val None: PressFeedback = PressFeedback { }
    }
}

/** Der Tick für alle Bausteine darunter. Ohne Anbindung: [PressFeedback.None]. */
val LocalPressFeedback = staticCompositionLocalOf { PressFeedback.None }

/**
 * Macht ein Element antippbar im Pixel-Stil: keine Material-Welle
 * (keine Anzeige beim Drücken), eine Rolle für Vorleser und ein Haptik-Tick
 * über [LocalPressFeedback], bevor [onClick] läuft.
 *
 * Das Einsinken zeichnet der Baustein selbst. Dafür reicht er eine
 * eigene [interactionSource] herein und liest daraus `collectIsPressedAsState()`.
 *
 * Mit `enabled = false` löst der Tap nichts aus, wird aber auch nicht
 * verbraucht: Er geht an Gesten weiter außen. Wer einen gesperrten Knopf
 * Taps schlucken lassen will, muss das selbst tun.
 */
fun Modifier.pixelPressable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
): Modifier = composed {
    val feedback = LocalPressFeedback.current
    val source = interactionSource ?: remember { MutableInteractionSource() }
    clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        role = role
    ) {
        feedback.tap()
        onClick()
    }
}
