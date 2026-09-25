package de.robinrehbein.punkt.ui.screens

import androidx.compose.ui.geometry.Offset
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.SoundSetId
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.ui.data.DeviceCalendar
import de.robinrehbein.punkt.ui.data.FakeKeyValueStore
import de.robinrehbein.punkt.ui.data.GameStore
import de.robinrehbein.punkt.ui.data.fixedCalendarForTools
import de.robinrehbein.punkt.ui.platform.GameFeedback
import de.robinrehbein.punkt.ui.platform.GameSounds
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * DAILY scharf im echten GameScreen (Plan 8.6 #4): Die Karte schaltet
 * mit START scharf, der Tageslauf startet per Tap im Grün, und MENÜ im
 * Game-Over schaltet DAILY wieder ab. [DailyIntroTest] prüft die
 * Entscheidung, dieser Test den Weg durch GameScreen und backToMenu().
 */
class DailyMenuTest {

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
    fun menueImGameOverSchaltetDailyAb() {
        val vorherLocale = java.util.Locale.getDefault()
        val vorherUhr = fixedCalendarForTools
        java.util.Locale.setDefault(java.util.Locale.GERMANY)
        fixedCalendarForTools = TODAY
        try {
            val store = GameStore(FakeKeyValueStore())
            val game = TimingGame(Random(SEED))
            ProbeScene(WIDTH, HEIGHT, DENSITY) {
                GameScreen(
                    store = store,
                    sounds = NoSounds(),
                    feedback = NoFeedback(),
                    game = game,
                    runSeed = SEED
                )
            }.use { scene ->
                scene.step(0.4)
                assertTrue(ARMED !in scene.labels(), "DAILY ist anfangs aus: ${scene.labels()}")

                // Erster Tap auf DAILY: die Karte, START schaltet scharf.
                scene.tap("DAILY")
                scene.step(0.3)
                scene.tap("START")
                scene.step(0.3)
                assertTrue(ARMED in scene.labels(), "START schaltet scharf: ${scene.labels()}")
                assertEquals(GamePhase.READY, game.phase, "DAILY allein startet nicht")

                // Tageslauf per Tap im Grün, dann die Zone überfahren lassen.
                var frames = 0
                while (!game.isInZone && frames++ < 1_000) scene.step(0.016)
                scene.tapAt(Offset(WIDTH / 2f, HEIGHT * 0.5f))
                assertEquals(GamePhase.RUNNING, game.phase, "der Tap im Grün startet")
                frames = 0
                while (game.phase != GamePhase.OVER && frames++ < 2_000) scene.step(0.016)
                assertEquals(GamePhase.OVER, game.phase, "kein Game-Over erreicht")
                assertEquals(TODAY.epochDay, store.dailyDay, "der Lauf zählte als Tageslauf")

                // Die Leiste ist nach der Sperre frei: MENÜ.
                while (game.phase == GamePhase.OVER && game.elapsed < 1.0f) scene.step(0.016)
                scene.tap("MENÜ")
                scene.step(0.3)
                assertEquals(GamePhase.READY, game.phase, "MENÜ führt in den Startbildschirm")
                assertFalse(ARMED in scene.labels(), "MENÜ schaltet DAILY ab: ${scene.labels()}")
                assertTrue("DAILY" in scene.labels(), "DAILY steht wieder aus da: ${scene.labels()}")
            }
        } finally {
            fixedCalendarForTools = vorherUhr
            java.util.Locale.setDefault(vorherLocale)
        }
    }

    private companion object {
        const val SEED = 20260925L
        const val WIDTH = 720
        const val HEIGHT = 1280
        const val DENSITY = 2f
        const val ARMED = "DAILY: AN"
        val TODAY = DeviceCalendar(
            epochDay = java.time.LocalDate.of(2026, 6, 17).toEpochDay(),
            month = 6,
            year = 2026,
            hour = 12
        )
    }
}
