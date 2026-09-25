package de.robinrehbein.punkt.ui.screens

import androidx.compose.ui.geometry.Offset
import de.robinrehbein.punkt.game.SoundSetId
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.ui.data.DeviceCalendar
import de.robinrehbein.punkt.ui.data.FakeKeyValueStore
import de.robinrehbein.punkt.ui.data.GameStore
import de.robinrehbein.punkt.ui.data.fixedCalendarForTools
import de.robinrehbein.punkt.ui.platform.GameFeedback
import de.robinrehbein.punkt.ui.platform.GameSounds
import de.robinrehbein.punkt.ui.platform.PlatformHooks
import java.io.File
import java.util.Locale
import kotlin.random.Random
import kotlin.test.Test

/**
 * Screenshots der Sammlung (AP-15): die vier Reiter, eine gesperrte
 * Kachel im Schaufenster, NEU-Markierung und das Banner „NEUE WELT“.
 * Wie der ScreenshotRenderer ein Werkzeug, kein Prüfstein: Ohne SHOTS_DIR
 * tut der Test nichts, und Gradle braucht --rerun.
 *
 * Der Spielstand ist ein Bestand von vor dem Update (130 Läufe, Rekord
 * 45: WÜSTE ist NEU), danach ein Lauf mit 52 Punkten (GALAXIE ist NEU).
 */
class CollectionShots {

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
        val dir = System.getenv("SHOTS_DIR") ?: return
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
            renderSet(File(dir, "sammlung"), 1080, 2400, 2.625f, "")
            renderSet(File(dir, "sammlung"), 720, 1280, 2f, "-720")
        } finally {
            Locale.setDefault(locale)
            fixedCalendarForTools = clock
        }
    }

    private fun bestand(): GameStore {
        val prefs = FakeKeyValueStore()
        prefs.edit {
            putInt("run_count_timing", 130)
            putInt("best_score_timing", 45)
            putInt("total_score", 1_800)
        }
        val store = GameStore(prefs)
        // Erstes Lesen: die Übernahme läuft, WÜSTE ist NEU.
        store.collectionNew()
        // Ein Lauf danach: Rekord 52 schaltet GALAXIE frei.
        store.submitRun(score = 52, epochDay = 20_600L, month = 6, year = 2026)
        return store
    }

    private fun renderSet(dir: File, w: Int, h: Int, d: Float, suffix: String) {
        val store = bestand()
        ProbeScene(w, h, d) {
            GameScreen(
                store = store,
                sounds = NoSounds(),
                feedback = NoFeedback(),
                hooks = PlatformHooks(adsEnabled = true, rewardedReady = true, patronPrice = "2,99 €"),
                game = TimingGame(Random(SEED)),
                runSeed = SEED
            )
        }.use { s ->
            s.step(1.0)
            s.save(File(dir, "30-banner$suffix.png"))

            s.tap("SAMMLUNG")
            s.step(0.6)
            s.save(File(dir, "31-vogel$suffix.png"))

            // Gesperrte Kachel ansehen: Schaufenster, Bedingung, Stand, Spot.
            s.tap("SCHATTEN")
            s.step(0.4)
            s.save(File(dir, "32-gesperrt$suffix.png"))

            // Die neue Kachel weiter unten (GEMUSTERT): NEU-Schild.
            s.scroll(Offset(w / 2f, h * 0.8f), 10f, 3)
            s.step(0.3)
            s.save(File(dir, "33-neu$suffix.png"))

            s.tap("WELT")
            s.step(0.4)
            s.save(File(dir, "34-welt$suffix.png"))
            s.tap("MEER")
            s.step(0.4)
            s.save(File(dir, "35-welt-gesperrt$suffix.png"))

            s.tap("TON")
            s.tap("GLOCKE")
            s.step(0.4)
            s.save(File(dir, "36-ton$suffix.png"))

            s.tap("RAHMEN")
            s.step(0.4)
            s.save(File(dir, "37-rahmen$suffix.png"))
            s.tap("ZINNEN")
            s.step(0.4)
            s.save(File(dir, "38-rahmen-gesperrt$suffix.png"))

            // Zu, und über das Banner wieder auf: Reiter WELT, WÜSTE vorn.
            s.tap("SCHLIESSEN")
            s.step(0.4)
            s.save(File(dir, "39-zu$suffix.png"))
            s.tap("NEUE WELT")
            s.step(0.5)
            s.save(File(dir, "40-banner-welt$suffix.png"))
            s.tap("SCHLIESSEN")
            s.step(0.4)
            s.save(File(dir, "41-nach-ansehen$suffix.png"))
        }
    }

    private companion object {
        const val SEED = 20260925L
    }
}
