package de.robinrehbein.punkt.ui.screens

import androidx.compose.ui.geometry.Offset
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SoundSetId
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.game.Twist
import de.robinrehbein.punkt.ui.data.DeviceCalendar
import de.robinrehbein.punkt.ui.data.FakeKeyValueStore
import de.robinrehbein.punkt.ui.data.GameStore
import de.robinrehbein.punkt.ui.data.fixedCalendarForTools
import de.robinrehbein.punkt.ui.platform.GameFeedback
import de.robinrehbein.punkt.ui.platform.GameSounds
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test

/**
 * Rohbilder für den Play-Store: der echte GameScreen, von einem Bot
 * gespielt, in Deutsch und Englisch. Anders als `store/generate_*.py`
 * zeichnet hier nichts nach — was auf den Store-Bildern steht, ist
 * Pixel für Pixel das Spiel. Die Werbe-Zeilen und den Rahmen legt danach
 * `store/compose_store_assets.py` darum.
 *
 * Wie der ScreenshotRenderer ein Werkzeug, kein Prüfstein: Ohne SHOTS_DIR
 * tut der Test nichts, und Gradle braucht --rerun. Die Bilder landen in
 * SHOTS_DIR/store/<sprache>/.
 */
class StoreShots {

    private class NoSounds : GameSounds {
        override var muted = false
        override var soundSet = SoundSetId.KLASSIK
        override fun start() {}
        override fun hit(score: Int) {}
        override fun perfect(streak: Int) {}
        override fun chain() {}
        override fun unlock() {}
        override fun death() {}
        override fun thud() {}
        override fun newRecord() {}
        override fun preview(set: SoundSetId) {}
        override fun release() {}
    }

    private class NoFeedback : GameFeedback {
        override fun score() {}
        override fun perfect() {}
        override fun unlock() {}
        override fun death() {}
        override fun thud() {}
        override fun newRecord() {}
    }

    @Test
    fun render() {
        val root = System.getenv("SHOTS_DIR") ?: return
        val locale = Locale.getDefault()
        val clock = fixedCalendarForTools
        fixedCalendarForTools = DeviceCalendar(epochDay = TODAY, month = 6, year = 2026, hour = 12)
        try {
            for ((lang, loc) in listOf("de" to Locale.GERMANY, "en" to Locale.US)) {
                Locale.setDefault(loc)
                val dir = File(root, "store/$lang").apply { mkdirs() }
                renderAll(dir)
            }
            // Die Zahlen für die Werbe-Zeilen kommen aus dem Code, nicht aus
            // einer zweiten Liste im Python-Skript. Gönner-Skins zählen nicht:
            // Die kauft man, die sammelt man nicht.
            val collectable = SkinId.entries.count { !SkinPaint.isPatron(it) }
            File(root, "store/counts.json").writeText(
                """{"skins": $collectable, "scenes": ${SceneId.entries.size}, """ +
                    """"sounds": ${SoundSetId.entries.size}, "twists": ${Twist.entries.size}}"""
            )
        } finally {
            Locale.setDefault(locale)
            fixedCalendarForTools = clock
        }
    }

    /** Ein Stammspieler-Stand: alles offen, keine Stützräder, keine Rückfragen. */
    private fun store(skin: SkinId, scene: SceneId, dailyStreak: Int = 0): GameStore {
        val prefs = FakeKeyValueStore()
        prefs.edit {
            putInt("run_count_timing", 6_000)
            putInt("best_score_timing", 150)
            putInt("total_score", 90_000)
            putInt("best_perfect_streak", 40)
            putInt("best_daily_streak", 60)
            putInt("days_played", 400)
            putInt("months_played", 0xFFF)
            putInt("missions_done", 120)
            putBoolean("daily_intro_seen", true)
            putBoolean("reminder_asked", true)
            if (dailyStreak > 0) {
                putInt("daily_streak", dailyStreak)
                putLong("daily_day", TODAY - 1)
            }
        }
        val store = GameStore(prefs)
        store.collectionNew()
        store.selectedSkin = skin
        store.selectedScene = scene
        return store
    }

    private fun scene(store: GameStore, game: TimingGame) = ProbeScene(W, H, DENSITY) {
        GameScreen(
            store = store,
            sounds = NoSounds(),
            feedback = NoFeedback(),
            game = game,
            runSeed = SEED
        )
    }

    private fun game(vararg twists: Twist) = TimingGame(Random(SEED)).apply {
        twistOverride = twists.toSet()
    }

    private fun renderAll(dir: File) {
        // 1 — Das Spiel selbst: Punkt fliegt auf die Zone zu.
        shootRun(dir, "01-gameplay", store(SkinId.KLASSIK, SceneId.WIESE), game(), target = 61) { g ->
            val half = g.effectiveZoneHalf()
            g.relativeToZone() in (-half - 0.55f)..(-half - 0.3f)
        }
        // 2 — Bomben: Köder-Kette neben der Zone.
        shootRun(dir, "02-bomben", store(SkinId.BIENE, SceneId.WUESTE), game(Twist.FAKE), target = 20) { g ->
            val half = g.effectiveZoneHalf()
            g.hasFakeZone && g.relativeToZone() in (-half - 1.4f)..(-half - 0.3f)
        }
        // 3 — Nebel: Der Vogel gleitet in die Wolke.
        shootRun(dir, "03-nebel", store(SkinId.PINGUIN, SceneId.BERG), game(Twist.GHOST), target = 17) { g ->
            val rel = g.relativeToZone()
            rel in (g.fogStart() + 0.01f)..(g.fogStart() + 0.12f)
        }
        // 4 — Knapp daneben: der Moment, der „noch mal“ auslöst.
        shootNearMiss(dir, "04-knapp", store(SkinId.LAVA, SceneId.STADT), game(), target = 31)
        // 5 — Tageslauf mit Serie.
        shootDaily(dir, "05-daily", store(SkinId.MELONE, SceneId.MEER, dailyStreak = 12))
        // 6 — Späte Twists im Weltraum: TEMPO-Pfeile vor der Zone.
        shootRun(dir, "06-tempo", store(SkinId.GOLD, SceneId.WELTRAUM), game(Twist.TEMPO), target = 46) { g ->
            g.hasTempo && g.isInTempoBand
        }
        // 7 — Sammlung.
        shootCollection(dir, "07-sammlung", store(SkinId.KOI, SceneId.WIESE))
    }

    // Im Lauf wird am unteren Rand getippt: Das Tipp-Echo wächst dort aus
    // dem Bild, statt als Quadrat in der Ringmitte zu stehen. Im
    // Startbildschirm liegen dort die Taster, also startet der Lauf in der
    // Ringmitte.
    private fun tapPoint(g: TimingGame) =
        if (g.phase == GamePhase.READY) Offset(W / 2f, H * 0.42f) else Offset(2f, H - 2f)

    /**
     * Spielt bis [target] Punkte und löst aus, sobald [ready] passt. Der Bot
     * tippt nur in den PERFEKT-Kern, der Lauf ist also sicher.
     */
    private fun play(
        s: ProbeScene,
        g: TimingGame,
        target: Int,
        maxSeconds: Double = 90.0,
        ready: (TimingGame) -> Boolean
    ) {
        var t = 0.0
        // Ausgelöst wird erst, wenn Treffer-Text, Tipp-Echo und das Banner
        // samt Funkenring der neuen Himmelsstufe abgeklungen sind — sonst
        // steht „NEUE STUFE!“ auf jedem Bild.
        var lastHit = 0.0
        var lastStage = 0.0
        var score = g.score
        s.step(0.8)
        while (t < maxSeconds) {
            check(g.phase != GamePhase.DYING && g.phase != GamePhase.OVER) { "Bot gestorben bei ${g.score} (${g.activeTwists})" }
            if (g.score != score) {
                if (g.score / 5 != score / 5) lastStage = t
                lastHit = t
                score = g.score
            }
            val quiet = t - lastHit > QUIET_HIT && t - lastStage > QUIET_STAGE
            if (g.score >= target && g.phase == GamePhase.RUNNING && quiet && ready(g)) return
            // Bis zum Ziel PERFEKT (schnell hoch), danach normale Treffer
            // hinter dem Kern: +1 statt +5, sonst wäre jeder Treffer eine
            // neue Himmelsstufe und das Bild käme nie zur Ruhe.
            val rel = g.relativeToZone()
            val perfect = g.perfectHalf()
            val half = g.effectiveZoneHalf()
            val hit = if (g.score < target || perfect * 1.3f >= half * 0.75f) {
                abs(rel) <= perfect * 0.5f
            } else {
                rel in (perfect * 1.3f)..half
            }
            if (hit) {
                s.tapAt(tapPoint(g))
                t += 0.1
            } else {
                s.step(0.016)
                t += 0.016
            }
        }
        error("Motiv nicht erreicht (Score ${g.score})")
    }

    private fun shootRun(
        dir: File,
        name: String,
        store: GameStore,
        g: TimingGame,
        target: Int,
        ready: (TimingGame) -> Boolean
    ) {
        scene(store, g).use { s ->
            play(s, g, target, ready = ready)
            s.save(File(dir, "$name.png"))
        }
    }

    private fun shootNearMiss(dir: File, name: String, store: GameStore, g: TimingGame, target: Int) {
        scene(store, g).use { s ->
            play(s, g, target) { true }
            // Kurz vor der Zone tippen: ZU FRÜH, aber nur um Millisekunden.
            while (g.phase == GamePhase.RUNNING) {
                val half = g.effectiveZoneHalf()
                val rel = g.relativeToZone()
                if (rel < -half && rel > -half - g.dotSpeed() * 0.045f) {
                    s.tapAt(tapPoint(g))
                    break
                }
                s.step(0.016)
            }
            s.step(0.25)
            s.save(File(dir, "$name.png"))
        }
    }

    private fun shootDaily(dir: File, name: String, store: GameStore) {
        val g = game()
        scene(store, g).use { s ->
            s.step(1.0)
            s.tap("DAILY")
            s.step(0.4)
            play(s, g, target = 17) { gg ->
                val half = gg.effectiveZoneHalf()
                gg.relativeToZone() in (-half - 0.6f)..(-half - 0.3f)
            }
            s.save(File(dir, "$name.png"))
        }
    }

    private fun shootCollection(dir: File, name: String, store: GameStore) {
        scene(store, game()).use { s ->
            s.step(1.0)
            s.tap(if (Locale.getDefault().language == "de") "SAMMLUNG" else "COLLECTION")
            s.step(0.8)
            s.save(File(dir, "$name.png"))
        }
    }

    private companion object {
        const val W = 1080
        const val H = 2340
        const val DENSITY = 2.625f
        const val SEED = 20260928L
        const val QUIET_HIT = 0.25
        const val QUIET_STAGE = 2.0
        val TODAY = java.time.LocalDate.of(2026, 6, 17).toEpochDay()
    }
}
