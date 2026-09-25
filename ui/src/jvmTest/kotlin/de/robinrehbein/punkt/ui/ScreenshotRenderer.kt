package de.robinrehbein.punkt.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.SoundSetId
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.ui.data.DeviceCalendar
import de.robinrehbein.punkt.ui.data.FakeKeyValueStore
import de.robinrehbein.punkt.ui.data.GameStore
import de.robinrehbein.punkt.ui.data.fixedCalendarForTools
import de.robinrehbein.punkt.ui.platform.GameFeedback
import de.robinrehbein.punkt.ui.platform.GameSounds
import de.robinrehbein.punkt.ui.screens.GameScreen
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Headless-Screenshots der geteilten Oberflaeche: rendert GameScreen mit
 * Skia in Geraetegroesse (Standard 1080x2400 bei 2.625x) und schreibt PNGs
 * in das Verzeichnis aus der Umgebungsvariable SHOTS_DIR. Ohne sie tut der
 * Test nichts — er ist ein Werkzeug, kein Pruefstein. Gradle kennt
 * SHOTS_DIR nicht als Eingabe, deshalb immer mit --rerun starten.
 *
 * Reproduzierbar: Das Spiel ist geseedet (auch jeder freie Lauf, siehe
 * GameScreen.runSeed), die Uhr steht fest ([FIXED_CALENDAR]) und die
 * Bildzeit laeuft in festen Schritten. Zwei Laeufe mit demselben Seed
 * liefern dieselben Bilder.
 */
class ScreenshotRenderer {

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
        val dir = System.getProperty("shots.dir") ?: System.getenv("SHOTS_DIR") ?: return
        renderSet(File(dir), width = 1080, height = 2400, density = 2.625f, seed = SEED)
    }

    /**
     * Der Screenshot-Satz fuer eine Geraetegroesse. Breite und Hoehe in
     * Pixeln, [density] in Pixeln je dp — alle Tippstellen rechnen damit,
     * damit derselbe Ablauf auch auf 720x1280 trifft.
     */
    fun renderSet(dir: File, width: Int, height: Int, density: Float, seed: Long) {
        dir.mkdirs()
        java.util.Locale.setDefault(java.util.Locale.GERMANY)
        val vorher = fixedCalendarForTools
        fixedCalendarForTools = FIXED_CALENDAR
        try {
            renderSetWithFixedClock(dir, width, height, density, seed)
        } finally {
            fixedCalendarForTools = vorher
        }
    }

    private fun renderSetWithFixedClock(
        dir: File,
        width: Int,
        height: Int,
        density: Float,
        seed: Long
    ) {
        val w = width
        val h = height
        val d = density
        var now = 0L
        fun ImageComposeScene.step(seconds: Double, everyMs: Long = 16) {
            val until = now + (seconds * 1_000_000_000L).toLong()
            while (now < until) {
                now += everyMs * 1_000_000L
                render(now)
            }
        }
        fun ImageComposeScene.tap(x: Float, y: Float) {
            sendPointerEvent(PointerEventType.Press, Offset(x, y))
            step(0.05)
            sendPointerEvent(PointerEventType.Release, Offset(x, y))
            step(0.05)
        }
        fun ImageComposeScene.save(name: String) {
            val img = render(now)
            val data = img.encodeToData(EncodedImageFormat.PNG)!!
            File(dir, name).writeBytes(data.bytes)
            println("-> $name")
        }

        val game = TimingGame(Random(seed))
        ImageComposeScene(
            width = w,
            height = h,
            density = Density(d)
        ) {
            GameScreen(
                store = GameStore(FakeKeyValueStore()),
                sounds = NoSounds(),
                feedback = NoFeedback(),
                game = game,
                runSeed = seed
            )
        }.use { scene ->
            // READY: kurz laufen lassen, damit Vogel und Blinken stehen
            scene.step(1.2)
            scene.save("01-ready.png")

            // Lauf starten, ~1 s kreisen lassen
            scene.tap(w / 2f, h * 0.5f)
            scene.step(1.0)
            scene.save("02-running.png")

            // Sterben: Zone einfach nicht antippen und ueberfahren lassen,
            // dann Freeze + Sturz abwarten -> Game-Over steht.
            // == AP-14 bedienung ==
            // Zwei Zeitpunkte nach dem Aufschlag: bei 0,3 s ist die Leiste
            // unten noch gesperrt (blass), bei 1,0 s ist sie frei.
            var frames = 0
            while (game.phase != GamePhase.OVER && frames++ < 1_000) scene.step(0.016)
            scene.step(0.3)
            scene.save("03a-gameover-gesperrt.png")
            scene.step(0.7)
            scene.save("03-gameover.png")
            // == /AP-14 ==

            // Hilfe aus dem Game-Over: "?" oben rechts (16dp Rand, 48dp Knopf)
            scene.tap(w - (16 + 24) * d, (16 + 24) * d)
            scene.step(0.6)
            scene.save("04-help.png")
            // Hilfe schliessen (Tap konsumiert, kein Neustart)
            scene.tap(w / 2f, h * 0.85f)
            scene.step(0.3)

            // == AP-14 bedienung ==
            // Zurueck ins Menue: MENUE in der Leiste am unteren Rand, links
            // (ohne TEILEN ueber die ganze Breite).
            scene.tap(w / 4f, h - 32 * d)
            scene.step(0.5)
            // == /AP-14 ==
            scene.save("05-menu-back.png")

            // Einstellungen: Regler-Knopf oben rechts im READY
            scene.tap(w - (16 + 24) * d, (16 + 24) * d)
            scene.step(0.6)
            scene.save("06-settings.png")
            scene.tap(w / 2f, 200f)
            scene.step(0.3)

            // Sammlung: Taster-Leiste unten, mittleres Drittel
            scene.tap(w / 2f, h - 32 * d)
            scene.step(0.8)
            scene.save("07-skins.png")
            // Tief in die Liste ziehen: die Skin-Familien unterhalb der
            // Rahmen — der lange Mittelteil der Sammlung.
            repeat(40) {
                scene.sendPointerEvent(
                    PointerEventType.Scroll,
                    Offset(w / 2f, h / 2f),
                    scrollDelta = Offset(0f, 10f)
                )
                scene.step(0.05)
            }
            scene.save("07b-skins-scrolled.png")
            scene.tap(w / 2f, 150f)
            scene.step(0.3)

            // Statistik: Taster-Leiste unten, rechtes Drittel
            scene.tap(w * 5f / 6f, h - 32 * d)
            scene.step(0.8)
            scene.save("08-stats.png")
        }
    }

    private companion object {
        /** Der Seed der Standard-Bilder. */
        const val SEED = 20260925L

        /** Mittwoch, 17. Juni 2026, 12 Uhr: Tageshimmel, kein Saison-Monat. */
        val FIXED_CALENDAR = DeviceCalendar(
            epochDay = java.time.LocalDate.of(2026, 6, 17).toEpochDay(),
            month = 6,
            year = 2026,
            hour = 12
        )
    }
}
