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

/** Ton an/aus — gleicher Mechanismus wie der Rekord, gleiche Prefs-Datei. */
private const val KEY_SOUND_MUTED = "sound_muted"

/**
 * Zustands-Holder außerhalb der Composition. MainActivity braucht ihn in
 * onKeyDown (Hardware-Zusatztasten wie der Quick-Button der Galaxy Watch
 * Ultra), WearGameScreen für Touch-Taps und fürs Zeichnen — game.tap() +
 * Event-Auswertung + Haptik/Sound + Rekord-Handling laufen dadurch für beide
 * Eingabewege über exakt denselben Code, statt sich zu duplizieren.
 *
 * Compose-State-Felder funktionieren auch außerhalb einer Composable-
 * Funktion (Snapshot-State ist unabhängig von der Composition) — Lese-
 * zugriffe in WearGameScreen lösen trotzdem ganz normal Recomposition aus.
 */
class WearGameController(context: Context) {

    val game = TimingGame()

    private val haptics = WearHaptics(context)
    private val audio = WearAudio(context)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Für die Spott-Text-Ressourcen; ApplicationContext leakt nicht. */
    private val appContext = context.applicationContext

    var phase by mutableStateOf(TimingGame.Phase.READY)
        private set
    var score by mutableIntStateOf(0)
        private set
    var bestScore by mutableIntStateOf(prefs.getInt(KEY_BEST, 0))
        private set
    var isNewRecord by mutableStateOf(false)
        private set

    /** Spott-Text des letzten Todes, fürs OVER-Overlay (leer bei Rekord). */
    var taunt by mutableStateOf("")
        private set

    /** Restzeit des "REKORD GEKNACKT!"-Banners im Lauf, 0 = ausgeblendet. */
    var recordBannerTimeLeft by mutableFloatStateOf(0f)
        private set

    /** Ton an/aus; persistent, damit die Wahl App-Neustarts überlebt. */
    var soundOn by mutableStateOf(!prefs.getBoolean(KEY_SOUND_MUTED, false))
        private set

    /** Zuletzt gesehene Himmels-Stufe (score / 5), für die Stufen-Fanfare. */
    private var lastStage = 0

    /** Wurde der Rekord in diesem Lauf schon live gefeiert? */
    private var recordCelebrated = false

    /** Sekunden seit dem letzten Phasenwechsel — für die RESTART_LOCK-Anzeige. */
    var phaseElapsed by mutableFloatStateOf(0f)
        private set

    /** Reiner Invalidierungs-Trigger fürs Canvas-Neuzeichnen pro Frame. */
    var frameTick by mutableLongStateOf(0L)
        private set

    /** Eigene Uhr für die TAP-Blink-Animation (siehe WearGameScreen). */
    var blinkClock by mutableFloatStateOf(0f)
        private set

    init {
        audio.muted = !soundOn
    }

    /**
     * Gemeinsamer Tap-Einstieg für Touch UND Hardware-Zusatztasten. tap()
     * selbst puffert nur das Event in TimingGame — die eigentliche Aus-
     * wertung (Haptik, Sound, Rekord) passiert zentral in update(), damit beide
     * Eingabewege exakt denselben Weg durch die Spiel-Loop nehmen und kein
     * Event doppelt verarbeitet wird.
     */
    fun tap() {
        game.tap()
    }

    /** Schaltet den Ton um und merkt sich die Wahl in den Prefs. */
    fun toggleSound() {
        soundOn = !soundOn
        audio.muted = !soundOn
        prefs.edit().putBoolean(KEY_SOUND_MUTED, !soundOn).apply()
    }

    /** Gibt den SoundPool frei; aus MainActivity.onDestroy gerufen. */
    fun release() {
        audio.release()
    }

    /** Ein Frame der Spiel-Loop; wird aus WearGameScreens LaunchedEffect gerufen. */
    fun update(dt: Float) {
        blinkClock += dt
        recordBannerTimeLeft = (recordBannerTimeLeft - dt).coerceAtLeast(0f)
        val events = game.update(dt)
        var twistUnlockedThisFrame = false
        events.forEach { event ->
            when (event) {
                TimingGame.GameEvent.Started -> {
                    lastStage = 0
                    recordCelebrated = false
                    recordBannerTimeLeft = 0f
                }
                TimingGame.GameEvent.Hit -> {
                    haptics.hit()
                    audio.hit(game.score)
                }
                TimingGame.GameEvent.PerfectHit -> {
                    haptics.perfectHit()
                    audio.perfect(game.perfectStreak)
                }
                is TimingGame.GameEvent.TwistUnlocked -> {
                    twistUnlockedThisFrame = true
                    audio.unlock()
                }
                TimingGame.GameEvent.Died -> {
                    haptics.died()
                    audio.death()
                    val previousBest = bestScore
                    val newBest = game.score > previousBest
                    isNewRecord = newBest
                    // Spott-Text vor dem Rekord-Update wählen — die Pools
                    // hängen am Abstand zum ALTEN Bestwert (wie am Phone).
                    taunt = pickTaunt(game.score, previousBest)
                    if (newBest) {
                        bestScore = game.score
                        prefs.edit().putInt(KEY_BEST, bestScore).apply()
                    }
                }
                TimingGame.GameEvent.Settled -> {
                    // Der Rekord-Jingle lief meist schon live im Lauf; sonst
                    // (z. B. allererster Lauf) kommt er jetzt — wie am Phone.
                    if (isNewRecord && !recordCelebrated) {
                        recordCelebrated = true
                        audio.newRecord()
                    }
                }
                else -> Unit
            }
        }

        // Rekord live feiern: In dem Moment, in dem der Lauf den alten
        // Bestwert überholt — nicht erst beim Tod (wie am Phone).
        if (game.phase == TimingGame.Phase.RUNNING &&
            !recordCelebrated && bestScore > 0 && game.score > bestScore
        ) {
            recordCelebrated = true
            recordBannerTimeLeft = RECORD_BANNER_SECONDS
            audio.newRecord()
        }

        // Stufen-Fanfare: jede 5er-Stufe färbt den Himmel um — hörbar
        // gefeiert, sofern nicht gerade ohnehin ein Twist freigeschaltet
        // wurde (dann lief die Fanfare schon).
        val stage = game.score / 5
        if (game.phase == TimingGame.Phase.RUNNING && stage > lastStage) {
            lastStage = stage
            if (!twistUnlockedThisFrame) audio.unlock()
        }
        if (game.phase == TimingGame.Phase.READY) {
            lastStage = 0
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
            recordBannerTimeLeft = 0f
        }
    }

    /**
     * Spott-Text pro Tod, gleiche Logik wie pickTaunt in GameOverlays.kt:
     * Pool nach Situation (Null-Runde, knapp dran, weit drunter, sonst),
     * Auswahl deterministisch über score+best statt echtem Zufall — fühlt
     * sich zufällig an, bleibt aber testbar. Die Wear-Arrays sind eine
     * gekürzte Teilmenge der Phone-Texte (Platz auf dem runden Display).
     */
    private fun pickTaunt(score: Int, previousBest: Int): String {
        val gap = previousBest - score
        val pool = appContext.resources.getStringArray(
            when {
                score == 0 -> R.array.taunts_zero
                gap in 1..3 -> R.array.taunts_close
                score < previousBest / 2 -> R.array.taunts_low
                else -> R.array.taunts_default
            }
        )
        val line = pool[(score + previousBest) % pool.size]
        // Nur die "knapp daneben"-Zeilen tragen einen %1$d-Platzhalter.
        return if (line.contains("%1\$d")) line.format(gap) else line
    }

    private companion object {
        /** Anzeigedauer des Rekord-Banners im Lauf, wie am Phone (2,2s). */
        const val RECORD_BANNER_SECONDS = 2.2f
    }
}
