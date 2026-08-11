package de.robinrehbein.punkt.wear

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.robinrehbein.punkt.game.TimingGame

/** SharedPreferences-Datei und -Schlüssel für den lokalen Uhren-Rekord. */
private const val PREFS_NAME = "punkt_wear"
private const val KEY_BEST = "best"

/**
 * Zustands-Holder außerhalb der Composition. MainActivity braucht ihn in
 * onKeyDown (Hardware-Zusatztasten wie der Quick-Button der Galaxy Watch
 * Ultra), WearGameScreen für Touch-Taps und fürs Zeichnen — game.tap() +
 * Event-Auswertung + Haptik + Rekord-Handling laufen dadurch für beide
 * Eingabewege über exakt denselben Code, statt sich zu duplizieren.
 *
 * Compose-State-Felder funktionieren auch außerhalb einer Composable-
 * Funktion (Snapshot-State ist unabhängig von der Composition) — Lese-
 * zugriffe in WearGameScreen lösen trotzdem ganz normal Recomposition aus.
 */
class WearGameController(context: Context) {

    val game = TimingGame()

    private val haptics = WearHaptics(context)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var phase by mutableStateOf(TimingGame.Phase.READY)
        private set
    var score by mutableIntStateOf(0)
        private set
    var bestScore by mutableIntStateOf(prefs.getInt(KEY_BEST, 0))
        private set
    var isNewRecord by mutableStateOf(false)
        private set

    /** Sekunden seit dem letzten Phasenwechsel — für die RESTART_LOCK-Anzeige. */
    var phaseElapsed by mutableFloatStateOf(0f)
        private set

    /** Reiner Invalidierungs-Trigger fürs Canvas-Neuzeichnen pro Frame. */
    var frameTick by mutableLongStateOf(0L)
        private set

    /** Eigene Uhr für die TAP-Blink-Animation (siehe WearGameScreen). */
    var blinkClock by mutableFloatStateOf(0f)
        private set

    /**
     * Gemeinsamer Tap-Einstieg für Touch UND Hardware-Zusatztasten. tap()
     * selbst puffert nur das Event in TimingGame — die eigentliche Aus-
     * wertung (Haptik, Rekord) passiert zentral in update(), damit beide
     * Eingabewege exakt denselben Weg durch die Spiel-Loop nehmen und kein
     * Event doppelt verarbeitet wird.
     */
    fun tap() {
        game.tap()
    }

    /** Ein Frame der Spiel-Loop; wird aus WearGameScreens LaunchedEffect gerufen. */
    fun update(dt: Float) {
        blinkClock += dt
        val events = game.update(dt)
        events.forEach { event ->
            when (event) {
                TimingGame.GameEvent.Hit -> haptics.hit()
                TimingGame.GameEvent.PerfectHit -> haptics.perfectHit()
                TimingGame.GameEvent.Died -> {
                    haptics.died()
                    val newBest = game.score > bestScore
                    isNewRecord = newBest
                    if (newBest) {
                        bestScore = game.score
                        prefs.edit().putInt(KEY_BEST, bestScore).apply()
                    }
                }
                else -> Unit
            }
        }
        phase = game.phase
        score = game.score
        phaseElapsed = game.elapsed
        frameTick++
    }

    /**
     * Bei ON_PAUSE während eines laufenden Versuchs: Lauf hart abbrechen
     * statt ihn im Hintergrund weiterlaufen zu lassen — sonst kreist die
     * Bahn blind weiter und man stirbt unsichtbar, bis man zurückkommt.
     */
    fun onAppPaused() {
        if (game.phase == TimingGame.Phase.RUNNING || game.phase == TimingGame.Phase.DYING) {
            game.reset()
            phase = TimingGame.Phase.READY
            score = 0
        }
    }
}
