package de.robinrehbein.punkt.wear

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.robinrehbein.punkt.game.DailyChallenge
import de.robinrehbein.punkt.game.SyncState
import de.robinrehbein.punkt.game.TimingGame
import java.time.LocalDate

/** SharedPreferences-Datei und -Schlüssel für den lokalen Uhren-Rekord. */
private const val PREFS_NAME = "punkt_wear"
private const val KEY_BEST = "best"

/** Ton an/aus — gleicher Mechanismus wie der Rekord, gleiche Prefs-Datei. */
private const val KEY_SOUND_MUTED = "sound_muted"

/**
 * Perfekt-Serie, Skin und Daily-Stand — gleiche Key-Namen wie im
 * Phone-Store (ScoreStore.kt), nur eben in der Uhren-Prefs-Datei. Der
 * Daily-Stand der Uhr ist bewusst lokal und unabhängig vom Phone, genau
 * wie der Rekord.
 */
private const val KEY_BEST_PERFECT = "best_perfect_streak"
private const val KEY_SKIN = "selected_skin"
private const val KEY_DAILY_BEST = "daily_best"
private const val KEY_DAILY_DAY = "daily_day"
private const val KEY_DAILY_STREAK = "daily_streak"

/**
 * Nur für den Abgleich mit dem Telefon: Wann der Skin zuletzt bewusst
 * gewechselt wurde, und die Lauf-Zahl des Telefons. Die Uhr zählt ihre
 * Läufe selbst nicht — sie trägt den Wert nur weiter, damit er beim
 * Hin- und Herschicken nicht verloren geht.
 */
private const val KEY_SKIN_CHANGED = "skin_changed_at"
private const val KEY_RUNS = "run_count"

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
internal class WearGameController(context: Context) {

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

    /** Daily-Modus an/aus — Session-Zustand wie am Phone, nicht persistent. */
    var dailyMode by mutableStateOf(false)
        private set

    /** Tagesbest von HEUTE (0, solange heute noch kein Daily-Lauf lief). */
    var dailyBestToday by mutableIntStateOf(0)
        private set

    /** Anzeige-Serie: 0, wenn der letzte Daily-Lauf länger als einen Tag her ist. */
    var dailyStreak by mutableIntStateOf(0)
        private set

    /** Gewählter Punkt-Skin; persistent wie der Rekord. */
    var skin by mutableStateOf(WearDotSkin.fromName(prefs.getString(KEY_SKIN, null)))
        private set

    /** Beste jemals erreichte Perfekt-Serie (für Skin-Freischaltungen). */
    private var bestPerfectStreak = prefs.getInt(KEY_BEST_PERFECT, 0)

    /** Höchste Perfekt-Serie des laufenden Versuchs. */
    private var runMaxPerfect = 0

    /** Tag, dem der laufende Versuch zugerechnet wird (fixiert beim Start). */
    private var runEpochDay = 0L

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
        refreshDailyDisplay()
    }

    /**
     * Gemeinsamer Tap-Einstieg für Touch UND Hardware-Zusatztasten. tap()
     * selbst puffert nur das Event in TimingGame — die eigentliche Aus-
     * wertung (Haptik, Sound, Rekord) passiert zentral in update(), damit beide
     * Eingabewege exakt denselben Weg durch die Spiel-Loop nehmen und kein
     * Event doppelt verarbeitet wird.
     */
    fun tap() {
        // Ein Tap in READY/OVER startet gleich einen Lauf — vorher Tag und
        // Seed für den aktuellen Modus setzen (wie prepareRun am Phone).
        if (game.phase == TimingGame.Phase.READY || game.phase == TimingGame.Phase.OVER) {
            prepareRun()
        }
        game.tap()
    }

    /**
     * Vor jedem Lauf-Start: Tag fixieren und die Zufallsquelle passend zum
     * Modus setzen. Die Daily bekommt exakt den Tages-Seed aus :core
     * (DailyChallenge.seedFor) — also dieselbe Zonen- und Twist-Abfolge
     * wie jeder Daily-Versuch des Tages, auch auf dem Phone.
     */
    private fun prepareRun() {
        runEpochDay = LocalDate.now().toEpochDay()
        game.reseed(if (dailyMode) DailyChallenge.seedFor(runEpochDay) else null)
    }

    /** Schaltet zwischen CLASSIC und DAILY um (READY-/OVER-Overlay). */
    fun toggleDailyMode() {
        dailyMode = !dailyMode
        refreshDailyDisplay()
    }

    /**
     * Nächster freigeschalteter Skin (zyklisch), gesperrte werden über-
     * sprungen. Die Freischaltungen werden wie am Phone bei jedem Aufruf
     * frisch aus den Bestleistungen abgeleitet — ein neuer Rekord macht
     * einen Skin also ab sofort wählbar, ganz ohne Unlock-Popup. Für die
     * Daily-Serie zählt der gespeicherte Stand (nicht die Anzeige-
     * Vorschau), exakt wie stats() im Phone-Store.
     */
    fun cycleSkin() {
        val stats = WearDotSkin.Stats(
            bestScore = bestScore,
            bestPerfectStreak = bestPerfectStreak,
            bestDailyStreak = prefs.getInt(KEY_DAILY_STREAK, 0)
        )
        val unlocked = WearDotSkin.entries.filter { it.isUnlocked(stats) }
        if (unlocked.size <= 1) return
        skin = unlocked[(unlocked.indexOf(skin) + 1) % unlocked.size]
        prefs.edit()
            .putString(KEY_SKIN, skin.name)
            .putLong(KEY_SKIN_CHANGED, System.currentTimeMillis())
            .apply()
        onStateChanged?.invoke()
        // Kurzes Klick-Feedback, damit der Wechsel auch haptisch ankommt.
        haptics.hit()
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

    // ===== Abgleich mit dem Telefon =====

    /**
     * Wird gerufen, wenn sich hier etwas geändert hat, das die andere
     * Seite angeht. MainActivity hängt daran das Veröffentlichen im Data
     * Layer — der Controller selbst kennt den Abgleich nicht und bleibt
     * damit ohne Android-Dienste testbar.
     */
    var onStateChanged: (() -> Unit)? = null

    /** Der lokale Stand als Austauschformat für den Data Layer. */
    fun syncState(): SyncState = SyncState(
        bestScore = bestScore,
        runCount = prefs.getInt(KEY_RUNS, 0),
        bestPerfectStreak = bestPerfectStreak,
        dailyDay = prefs.getLong(KEY_DAILY_DAY, 0L),
        dailyBest = prefs.getInt(KEY_DAILY_BEST, 0),
        dailyStreak = prefs.getInt(KEY_DAILY_STREAK, 0),
        skin = skin.name,
        skinChangedAt = prefs.getLong(KEY_SKIN_CHANGED, 0L)
    )

    /**
     * Übernimmt einen zusammengeführten Stand und meldet, ob sich dabei
     * hier etwas geändert hat. Nur bei einem echten Zuwachs antwortet der
     * Abgleich der Gegenseite — sonst würden sich beide Geräte endlos
     * gegenseitig bestätigen.
     */
    fun applySync(state: SyncState): Boolean {
        val before = syncState()
        if (before == state) return false
        val editor = prefs.edit()
        if (state.bestScore > before.bestScore) editor.putInt(KEY_BEST, state.bestScore)
        if (state.runCount > before.runCount) editor.putInt(KEY_RUNS, state.runCount)
        if (state.bestPerfectStreak > before.bestPerfectStreak) {
            editor.putInt(KEY_BEST_PERFECT, state.bestPerfectStreak)
        }
        if (state.dailyDay != before.dailyDay ||
            state.dailyBest != before.dailyBest ||
            state.dailyStreak != before.dailyStreak
        ) {
            editor.putLong(KEY_DAILY_DAY, state.dailyDay)
            editor.putInt(KEY_DAILY_BEST, state.dailyBest)
            editor.putInt(KEY_DAILY_STREAK, state.dailyStreak)
        }
        if (state.skinChangedAt > before.skinChangedAt) {
            editor.putLong(KEY_SKIN_CHANGED, state.skinChangedAt)
            // Freischaltungen leitet die Uhr aus den Bestleistungen ab —
            // mit den zusammengeführten Zahlen, nicht mit den alten.
            val merged = WearDotSkin.Stats(
                bestScore = maxOf(state.bestScore, before.bestScore),
                bestPerfectStreak = maxOf(state.bestPerfectStreak, before.bestPerfectStreak),
                bestDailyStreak = maxOf(state.dailyStreak, before.dailyStreak)
            )
            val incoming = WearDotSkin.fromName(state.skin)
            if (incoming.isUnlocked(merged)) editor.putString(KEY_SKIN, incoming.name)
        }
        editor.apply()

        // Angezeigte Werte nachziehen — die Compose-Felder lesen die Prefs
        // nur beim Erzeugen.
        bestScore = prefs.getInt(KEY_BEST, 0)
        bestPerfectStreak = prefs.getInt(KEY_BEST_PERFECT, 0)
        skin = WearDotSkin.fromName(prefs.getString(KEY_SKIN, null))
        refreshDailyDisplay()
        return true
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
                    runMaxPerfect = 0
                }
                TimingGame.GameEvent.Hit -> {
                    haptics.hit()
                    audio.hit(game.score)
                }
                TimingGame.GameEvent.PerfectHit -> {
                    haptics.perfectHit()
                    audio.perfect(game.perfectStreak)
                    runMaxPerfect = maxOf(runMaxPerfect, game.perfectStreak)
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
                    // Beste Perfekt-Serie fortschreiben (Skin-Freischaltung
                    // SCHATTEN), wie submitPerfectStreak im Phone-Store.
                    if (runMaxPerfect > bestPerfectStreak) {
                        bestPerfectStreak = runMaxPerfect
                        prefs.edit().putInt(KEY_BEST_PERFECT, bestPerfectStreak).apply()
                    }
                    if (dailyMode) {
                        submitDailyRun(runEpochDay, game.score)
                    }
                    // Jeder beendete Lauf ist ein moeglicher neuer Stand
                    // fuers Telefon. Ohne Aenderung ist das ein No-op.
                    onStateChanged?.invoke()
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
     * Beendeter Daily-Lauf, gleiche Regeln wie submitDailyRun im
     * Phone-Store: Nur der erste Lauf des Tages schreibt die Tages-Serie
     * fort (DailyChallenge.nextStreak aus :core — eine Lücke reißt sie)
     * und setzt den Tagesbest neu; danach zählt für den Tag nur noch ein
     * besserer Tagesbest.
     */
    private fun submitDailyRun(epochDay: Long, score: Int) {
        val storedDay = prefs.getLong(KEY_DAILY_DAY, 0L)
        if (storedDay != epochDay) {
            val streak = DailyChallenge.nextStreak(
                lastPlayedEpochDay = storedDay,
                currentStreak = prefs.getInt(KEY_DAILY_STREAK, 0),
                todayEpochDay = epochDay
            )
            prefs.edit()
                .putInt(KEY_DAILY_STREAK, streak)
                .putLong(KEY_DAILY_DAY, epochDay)
                .putInt(KEY_DAILY_BEST, score)
                .apply()
        } else if (score > prefs.getInt(KEY_DAILY_BEST, 0)) {
            prefs.edit().putInt(KEY_DAILY_BEST, score).apply()
        }
        refreshDailyDisplay()
    }

    /**
     * Anzeige-Stände fürs Overlay aus den Prefs ableiten — wie
     * dailyBestFor/dailyStreakPreviewFor im Phone-Store: Der Tagesbest
     * gilt nur für den gespeicherten Tag; die Serie gilt als laufend,
     * wenn der letzte Daily-Lauf heute oder gestern war, sonst als
     * gerissen (angezeigt wird dann 0).
     */
    private fun refreshDailyDisplay() {
        val today = LocalDate.now().toEpochDay()
        val storedDay = prefs.getLong(KEY_DAILY_DAY, 0L)
        dailyBestToday = if (storedDay == today) prefs.getInt(KEY_DAILY_BEST, 0) else 0
        dailyStreak = if (storedDay == today || storedDay == today - 1) {
            prefs.getInt(KEY_DAILY_STREAK, 0)
        } else {
            0
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
