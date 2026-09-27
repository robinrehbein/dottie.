package de.robinrehbein.punkt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import de.robinrehbein.punkt.game.CardFrame
import de.robinrehbein.punkt.game.Goal
import de.robinrehbein.punkt.game.GoalAxis
import de.robinrehbein.punkt.game.SkinId
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
import kotlin.random.Random
import kotlin.test.Test

/**
 * Bilder der Menü-Flächen im Bevel-Look: Startbildschirm mit Taster-
 * Leiste (auch gedrückt), Einstellungen mit Schaltern, Statistik mit
 * Balken und das Game-Over mit Panel und Twist-Schild (schlicht und mit
 * verziertem Rahmen). Wie der ScreenshotRenderer ein Werkzeug, kein
 * Prüfstein: Ohne SHOTS_DIR tut der Test nichts, und Gradle braucht
 * --rerun. Die Bilder landen in SHOTS_DIR/menue.
 */
class MenuBevelShots {

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
        val dir = File(root, "menue").apply { mkdirs() }
        val locale = Locale.getDefault()
        val clock = fixedCalendarForTools
        Locale.setDefault(Locale.GERMANY)
        fixedCalendarForTools = DeviceCalendar(
            epochDay = java.time.LocalDate.of(2026, 6, 17).toEpochDay(),
            month = 6,
            year = 2026,
            hour = 12
        )
        try {
            menue(dir, 1080, 2400, 2.625f)
            gameOver(dir, 1080, 2400, 2.625f)
        } finally {
            Locale.setDefault(locale)
            fixedCalendarForTools = clock
        }
    }

    /** Ein Bestand mit Rekord, Läufen und offenen Zielen, damit Balken zu sehen sind. */
    private fun bestand(): GameStore {
        val prefs = FakeKeyValueStore()
        prefs.edit {
            putInt("run_count_timing", 130)
            putInt("best_score_timing", 45)
            putInt("total_score", 1_800)
        }
        val store = GameStore(prefs)
        store.collectionNew()
        return store
    }

    private fun menue(dir: File, w: Int, h: Int, d: Float) {
        ProbeScene(w, h, d) {
            GameScreen(
                store = bestand(),
                sounds = NoSounds(),
                feedback = NoFeedback(),
                game = TimingGame(Random(SEED)),
                runSeed = SEED
            )
        }.use { s ->
            s.step(1.5)
            s.save(File(dir, "start.png"))
            // STATISTIK gehalten: der Taster sinkt ein.
            s.press("STATISTIK")
            s.step(0.05)
            s.save(File(dir, "start-gedrueckt.png"))
            s.release()
            s.step(0.8)
            s.save(File(dir, "statistik.png"))
            s.tap("SCHLIESSEN")
            s.step(0.4)
            s.tap("EINSTELLUNGEN")
            s.step(0.6)
            s.save(File(dir, "einstellungen.png"))
        }
    }

    private fun gameOver(dir: File, w: Int, h: Int, d: Float) {
        for ((name, frame) in listOf("gameover" to CardFrame.SCHLICHT, "gameover-rahmen" to CardFrame.ZINNEN)) {
            ProbeScene(w, h, d) {
                Box(Modifier.fillMaxSize().background(Color(0xFF4EC0CA))) {
                    GameOverOverlay(
                        score = 23,
                        bestScore = 45,
                        isNewRecord = false,
                        taunt = "FAST!",
                        daily = false,
                        dailyBest = 0,
                        dailyStreak = 0,
                        skinUnlocked = false,
                        newMedal = false,
                        newTwist = Twist.FAKE,
                        goal = Goal(skin = SkinId.GOLD, scene = null, axis = GoalAxis.BEST_SCORE, current = 23, target = 50),
                        onShare = {},
                        onMenu = {},
                        cardFrame = frame
                    )
                }
            }.use { s ->
                s.step(1.2)
                s.save(File(dir, "$name.png"))
            }
        }
    }

    private companion object {
        const val SEED = 20260925L
    }
}
