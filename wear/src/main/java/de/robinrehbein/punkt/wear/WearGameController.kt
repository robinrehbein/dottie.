package de.robinrehbein.punkt.wear

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.robinrehbein.punkt.game.DailyChallenge
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SyncState
import de.robinrehbein.punkt.game.TimingGame
import java.time.LocalDate
import java.time.LocalDateTime

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

/** Wann der Skin zuletzt bewusst gewechselt wurde — nur für den Abgleich. */
private const val KEY_SKIN_CHANGED = "skin_changed_at"

/**
 * Kulissen-Wahl des Telefons, gespiegelt wie der Skin — die Uhr wählt
 * selbst nie eine Kulisse (siehe README, Abschnitt "Kulissen"), sie zieht
 * nur die Himmelsfarben aus ScenePaint. Ohne diese beiden Keys bliebe die
 * Uhr für immer bei WIESE, egal was am Telefon gewählt ist.
 */
private const val KEY_SCENE = "selected_scene"
private const val KEY_SCENE_CHANGED = "scene_changed_at"

/**
 * Ausdauer-Zähler. Sie hängen nicht am Können und sind der einzige Weg,
 * auf dem nach dem letzten Rekord noch Skins dazukommen (EI, TIGER,
 * DONUT, TAGESZEIT ...). Die Uhr zählt sie seit den neuen Skins selbst,
 * statt sie nur vom Telefon durchzureichen — sonst wären die Ausdauer-
 * Skins auf einer Uhr ohne Telefon unerreichbar.
 *
 * [KEY_MONTHS_PLAYED] ist eine 12-Bit-Maske (Bit 0 = Januar): Derselbe
 * Monat in zwei Jahren darf nicht doppelt zählen.
 */
private const val KEY_RUNS = "run_count"
private const val KEY_TOTAL_SCORE = "total_score"
private const val KEY_DAYS_PLAYED = "days_played"
private const val KEY_LAST_PLAYED_DAY = "last_played_day"
private const val KEY_MONTHS_PLAYED = "months_played"

/**
 * Saison: [KEY_SEASON_EARNED] ist die dauerhafte Maske (Season.bit) und
 * wird nie wieder gelöscht. Die drei anderen sind nur der Fortschritt im
 * laufenden Fenster — Schlüssel Jahr*100+Monat, damit der Zähler im
 * nächsten Oktober von vorn anfängt.
 */
private const val KEY_SEASON_EARNED = "season_earned"
private const val KEY_SEASON_WINDOW = "season_window"
private const val KEY_SEASON_DAYS = "season_days"
private const val KEY_SEASON_LAST_DAY = "season_last_day"

/**
 * Lokaler Spiegel des Gönner-Kaufs. Die Wahrheit ist Play (siehe
 * [WearPatron]) — gespiegelt wird sie, damit die Uhr beim Start ohne Netz
 * sofort Bescheid weiß, genau wie adsRemoved im Phone-Store.
 */
private const val KEY_PATRON = "patron_owned"

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

    /**
     * Kulisse des Telefons, nur gespiegelt (siehe [KEY_SCENE]). Die Uhr
     * bietet dafür keinen eigenen Wähler — anders als [skin] gibt es hier
     * also keine öffentliche Setter-Funktion.
     */
    var scene by mutableStateOf(ScenePaint.fromName(prefs.getString(KEY_SCENE, null)))
        private set

    /** Ist der Skin-Wähler offen? Nur aus dem READY-Overlay erreichbar. */
    var skinPickerOpen by mutableStateOf(false)
        private set

    /**
     * Gönner-Paket gekauft. Play ist die Wahrheit ([WearPatron] fragt sie
     * ab), dies ist nur der lokale Spiegel — damit die Gönner-Skins beim
     * Start ohne Netz sofort dastehen.
     */
    var patronOwned by mutableStateOf(prefs.getBoolean(KEY_PATRON, false))
        private set

    /**
     * Die aktuell wählbaren Skins, beim Öffnen des Wählers einmal aus den
     * Ständen abgeleitet. Während der Wähler offen ist, ändert sich daran
     * nichts — es läuft ja gerade kein Versuch.
     */
    var unlockedSkins by mutableStateOf(listOf(WearDotSkin.KLASSIK))
        private set

    /** Sammlungsstand für die Kopfzeile des Wählers (ohne Saison/Gönner). */
    var collectedSkins by mutableIntStateOf(0)
        private set

    /**
     * Stunde (0-23) und Monat (1-12) der Geräte-Uhr — TAGESZEIT und
     * JAHRESZEIT ziehen daraus ihr Kleid. Höchstens minütlich frisch
     * geholt: Ein LocalDateTime.now() je Frame wäre für diese Auflösung
     * nur Müll für den Sammler.
     */
    var clockHour = 12
        private set
    var clockMonth = 6
        private set
    private var clockCheckedAt = 0L

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
        refreshClock(force = true)
    }

    /** Stunde und Monat nachziehen, höchstens einmal je CLOCK_REFRESH_MS. */
    private fun refreshClock(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - clockCheckedAt < CLOCK_REFRESH_MS) return
        clockCheckedAt = now
        val stamp = LocalDateTime.now()
        clockHour = stamp.hour
        clockMonth = stamp.monthValue
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

    // ===== Gönner-Kauf =====

    /**
     * Meldung aus der Play-Abfrage ([WearPatron]). Nur der Zuwachs zählt:
     * Eine ausbleibende Antwort — kein Play, kein Netz — darf einen
     * bekannten Kauf nie zurücknehmen. Liefert true, wenn sich dadurch
     * hier etwas geändert hat; dann lohnt ein frischer Abgleich, weil
     * jetzt auch ein Gönner-Skin des Telefons durchgeht.
     */
    fun onPatronResolved(owned: Boolean): Boolean {
        if (!owned || patronOwned) return false
        patronOwned = true
        prefs.edit().putBoolean(KEY_PATRON, true).apply()
        return true
    }

    // ===== Skin-Wahl =====

    /**
     * Alle Stände, aus denen sich Freischaltungen ableiten — wie stats()
     * im Phone-Store. Für die Daily-Serie zählt der gespeicherte Stand,
     * nicht die Anzeige-Vorschau (die fällt nach einer Lücke auf 0).
     *
     * patronOwned kommt aus dem lokalen Spiegel des Play-Kaufs: Wer das
     * Gönner-Paket gekauft hat, sieht seine Skins auch auf der Uhr — der
     * Kauf hängt am Konto, nicht am Gerät.
     */
    private fun skinStats(): WearDotSkin.Stats = WearDotSkin.Stats(
        bestScore = bestScore,
        bestPerfectStreak = bestPerfectStreak,
        bestDailyStreak = prefs.getInt(KEY_DAILY_STREAK, 0),
        runCount = prefs.getInt(KEY_RUNS, 0),
        totalScore = prefs.getInt(KEY_TOTAL_SCORE, 0),
        daysPlayed = prefs.getInt(KEY_DAYS_PLAYED, 0),
        // SkinStats will die ANZAHL der Monate, nicht die Maske.
        monthsPlayed = Integer.bitCount(prefs.getInt(KEY_MONTHS_PLAYED, 0)),
        seasonEarned = prefs.getInt(KEY_SEASON_EARNED, 0),
        patronOwned = patronOwned
    )

    /**
     * Öffnet den Skin-Wähler. Die Liste wird hier einmal frisch aus den
     * Ständen abgeleitet — ein neuer Rekord macht einen Skin also ab dem
     * nächsten Öffnen wählbar, ganz ohne Unlock-Popup.
     */
    fun openSkinPicker() {
        val stats = skinStats()
        unlockedSkins = WearDotSkin.entries.filter { it.isUnlocked(stats) }
        collectedSkins = SkinPaint.unlockedCount(stats.toSkinStats())
        skinPickerOpen = true
    }

    /** Ein Tap auf eine Zeile: Skin übernehmen und den Wähler schließen. */
    fun chooseSkin(next: WearDotSkin) {
        previewSkin(next)
        closeSkinPicker()
    }

    /**
     * Krone im Wähler: Cursor um [steps] Skins weiter, zyklisch. Der Skin
     * wird sofort sichtbar, aber noch nicht festgeschrieben — siehe
     * [closeSkinPicker].
     */
    fun moveSkinCursor(steps: Int) {
        val list = unlockedSkins
        if (list.size <= 1) return
        val at = list.indexOf(skin).coerceAtLeast(0)
        previewSkin(list[((at + steps) % list.size + list.size) % list.size])
    }

    /** Zeigt einen Skin an, ohne ihn zu speichern. */
    private fun previewSkin(next: WearDotSkin) {
        if (next == skin) return
        skin = next
        // Kurzes Klick-Feedback, damit der Wechsel auch haptisch ankommt.
        haptics.hit()
    }

    /**
     * Schließt den Wähler und schreibt die Wahl fest — erst hier, nicht
     * bei jedem Rasten der Krone: Sonst ginge für jeden übersprungenen
     * Skin ein Abgleich ans Telefon raus, und der Zeitstempel des letzten
     * Wechsels wäre der eines Skins, den niemand gewählt hat.
     */
    fun closeSkinPicker() {
        skinPickerOpen = false
        if (skin.name == prefs.getString(KEY_SKIN, null)) return
        prefs.edit()
            .putString(KEY_SKIN, skin.name)
            .putLong(KEY_SKIN_CHANGED, System.currentTimeMillis())
            .apply()
        onStateChanged?.invoke()
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
        totalScore = prefs.getInt(KEY_TOTAL_SCORE, 0),
        daysPlayed = prefs.getInt(KEY_DAYS_PLAYED, 0),
        lastPlayedDay = prefs.getLong(KEY_LAST_PLAYED_DAY, 0L),
        // Hier die MASKE, nicht der Zähler: SyncState verodert sie mit der
        // des Telefons, damit kein Monat verlorengeht (siehe SyncState).
        monthsPlayed = prefs.getInt(KEY_MONTHS_PLAYED, 0),
        seasonEarned = prefs.getInt(KEY_SEASON_EARNED, 0),
        skin = skin.name,
        skinChangedAt = prefs.getLong(KEY_SKIN_CHANGED, 0L),
        // Die Uhr wählt nie selbst eine Kulisse — hier steht nur zurück,
        // was das Telefon zuletzt geschickt hat (siehe applySync). Damit
        // bleibt SyncState.mergedWith stabil: Ohne dieses Zurückmelden
        // würde ein Abgleich die Kulisse jedes Mal neu "verlieren".
        scene = scene.name,
        sceneChangedAt = prefs.getLong(KEY_SCENE_CHANGED, 0L)
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
        if (state.totalScore > before.totalScore) {
            editor.putInt(KEY_TOTAL_SCORE, state.totalScore)
        }
        if (state.daysPlayed > before.daysPlayed) editor.putInt(KEY_DAYS_PLAYED, state.daysPlayed)
        if (state.lastPlayedDay > before.lastPlayedDay) {
            editor.putLong(KEY_LAST_PLAYED_DAY, state.lastPlayedDay)
        }
        // Masken werden verodert statt maximiert — jede Seite kennt
        // Monate und Saison-Erfolge, die die andere nie gesehen hat.
        val months = before.monthsPlayed or state.monthsPlayed
        if (months != before.monthsPlayed) editor.putInt(KEY_MONTHS_PLAYED, months)
        val seasons = before.seasonEarned or state.seasonEarned
        if (seasons != before.seasonEarned) editor.putInt(KEY_SEASON_EARNED, seasons)
        if (state.skinChangedAt > before.skinChangedAt) {
            // Freischaltungen leitet die Uhr aus den Ständen ab — mit den
            // zusammengeführten Zahlen, nicht mit den alten (siehe
            // WearSyncMerge). Für die Gönner-Skins zählt der lokale
            // Spiegel des Play-Kaufs.
            val merged = WearSyncMerge.skinStats(before, state, patronOwned)
            val incoming = WearDotSkin.fromName(state.skin)
            if (incoming.isUnlocked(merged)) {
                editor.putString(KEY_SKIN, incoming.name)
                editor.putLong(KEY_SKIN_CHANGED, state.skinChangedAt)
            }
            // Sonst bleibt der Zeitstempel bewusst stehen: Ein Gönner-Skin,
            // von dessen Kauf die Uhr noch nichts weiß, soll beim nächsten
            // Abgleich erneut ankommen — sobald Play den Kauf bestätigt hat,
            // geht die Wahl dann durch und beide Geräte zeigen wieder
            // dasselbe. Mit fortgeschriebenem Zeitstempel bliebe die Uhr
            // für immer auf ihrem alten Skin sitzen.
        }
        // Kulisse nach derselben Neuer-gewinnt-Regel wie der Skin — die
        // Rechnung steckt in WearSyncMerge, damit sie ohne Prefs testbar
        // bleibt. null heißt: nichts übernehmen, Zeitstempel bleibt stehen
        // (siehe WearSyncMerge.sceneToAdopt).
        WearSyncMerge.sceneToAdopt(before, state, patronOwned)?.let { adopted ->
            editor.putString(KEY_SCENE, adopted.name)
            editor.putLong(KEY_SCENE_CHANGED, state.sceneChangedAt)
        }
        editor.apply()

        // Angezeigte Werte nachziehen — die Compose-Felder lesen die Prefs
        // nur beim Erzeugen.
        bestScore = prefs.getInt(KEY_BEST, 0)
        bestPerfectStreak = prefs.getInt(KEY_BEST_PERFECT, 0)
        skin = WearDotSkin.fromName(prefs.getString(KEY_SKIN, null))
        scene = ScenePaint.fromName(prefs.getString(KEY_SCENE, null))
        refreshDailyDisplay()
        return true
    }

    /** Ein Frame der Spiel-Loop; wird aus WearGameScreens LaunchedEffect gerufen. */
    fun update(dt: Float) {
        blinkClock += dt
        refreshClock()
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
                    // Ausdauer-Zähler fortschreiben (Läufe, Punktesumme,
                    // Tage, Monate, Saison) — sie tragen die Skins, die
                    // nicht am Rekord hängen.
                    recordRun(game.score)
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
        // Offener Wähler: Die Wahl jetzt festschreiben, sonst ginge sie
        // mit der Composition verloren (sie wird erst beim Schließen
        // gespeichert).
        if (skinPickerOpen) closeSkinPicker()
        if (game.phase == TimingGame.Phase.RUNNING || game.phase == TimingGame.Phase.DYING) {
            game.reset()
            phase = TimingGame.Phase.READY
            score = 0
            recordBannerTimeLeft = 0f
        }
    }

    /**
     * Ausdauer-Buchhaltung eines beendeten Laufs. Gerechnet wird mit dem
     * Tag, dem der Lauf zugerechnet ist (fixiert beim Start), nicht mit
     * "jetzt" — ein Lauf über Mitternacht darf nicht in zwei Töpfe fallen.
     */
    private fun recordRun(score: Int) {
        val before = readProgress()
        // Gerechnet wird in WearProgress — ohne Prefs und damit prüfbar.
        writeProgress(before, before.afterRun(score, LocalDate.ofEpochDay(runEpochDay)))
    }

    /** Der gespeicherte Ausdauer-Stand als reine Zahlen. */
    private fun readProgress(): WearProgress = WearProgress(
        runCount = prefs.getInt(KEY_RUNS, 0),
        totalScore = prefs.getInt(KEY_TOTAL_SCORE, 0),
        daysPlayed = prefs.getInt(KEY_DAYS_PLAYED, 0),
        lastPlayedDay = prefs.getLong(KEY_LAST_PLAYED_DAY, Long.MIN_VALUE),
        monthsPlayed = prefs.getInt(KEY_MONTHS_PLAYED, 0),
        seasonEarned = prefs.getInt(KEY_SEASON_EARNED, 0),
        seasonWindow = prefs.getInt(KEY_SEASON_WINDOW, 0),
        seasonDays = prefs.getInt(KEY_SEASON_DAYS, 0),
        seasonLastDay = prefs.getLong(KEY_SEASON_LAST_DAY, Long.MIN_VALUE)
    )

    /** Nur schreiben, was sich geändert hat — der Rest ist schon da. */
    private fun writeProgress(before: WearProgress, after: WearProgress) {
        if (after == before) return
        val editor = prefs.edit()
        if (after.runCount != before.runCount) editor.putInt(KEY_RUNS, after.runCount)
        if (after.totalScore != before.totalScore) {
            editor.putInt(KEY_TOTAL_SCORE, after.totalScore)
        }
        if (after.daysPlayed != before.daysPlayed) {
            editor.putInt(KEY_DAYS_PLAYED, after.daysPlayed)
        }
        if (after.lastPlayedDay != before.lastPlayedDay) {
            editor.putLong(KEY_LAST_PLAYED_DAY, after.lastPlayedDay)
        }
        if (after.monthsPlayed != before.monthsPlayed) {
            editor.putInt(KEY_MONTHS_PLAYED, after.monthsPlayed)
        }
        if (after.seasonEarned != before.seasonEarned) {
            editor.putInt(KEY_SEASON_EARNED, after.seasonEarned)
        }
        if (after.seasonWindow != before.seasonWindow) {
            editor.putInt(KEY_SEASON_WINDOW, after.seasonWindow)
        }
        if (after.seasonDays != before.seasonDays) {
            editor.putInt(KEY_SEASON_DAYS, after.seasonDays)
        }
        if (after.seasonLastDay != before.seasonLastDay) {
            editor.putLong(KEY_SEASON_LAST_DAY, after.seasonLastDay)
        }
        editor.apply()
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

        /** Wie oft Stunde und Monat der Geräte-Uhr neu geholt werden. */
        const val CLOCK_REFRESH_MS = 60_000L
    }
}
