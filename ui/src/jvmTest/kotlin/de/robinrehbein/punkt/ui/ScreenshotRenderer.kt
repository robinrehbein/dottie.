package de.robinrehbein.punkt.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import de.robinrehbein.punkt.game.SoundSetId
import de.robinrehbein.punkt.ui.data.FakeKeyValueStore
import de.robinrehbein.punkt.ui.data.GameStore
import de.robinrehbein.punkt.ui.platform.GameFeedback
import de.robinrehbein.punkt.ui.platform.GameSounds
import de.robinrehbein.punkt.ui.screens.GameScreen
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Headless-Screenshots der geteilten Oberflaeche: rendert GameScreen mit
 * Skia in Geraetegroesse (1080x2400 bei 2.625x) und schreibt PNGs in das
 * Verzeichnis aus -Dshots.dir. Ohne dieses Property tut der Test nichts —
 * er ist ein Werkzeug, kein Pruefstein.
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
        File(dir).mkdirs()
        java.util.Locale.setDefault(java.util.Locale.GERMANY)

        val w = 1080
        val h = 2400
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

        ImageComposeScene(
            width = w,
            height = h,
            density = Density(2.625f)
        ) {
            GameScreen(
                store = GameStore(FakeKeyValueStore()),
                sounds = NoSounds(),
                feedback = NoFeedback()
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
            // dann Freeze + Sturz + Settle abwarten -> Game-Over steht.
            scene.step(6.0)
            scene.save("03-gameover.png")

            // Hilfe aus dem Game-Over: "?" oben rechts (16dp Rand, 48dp Knopf)
            scene.tap(w - (16 + 24) * 2.625f, (16 + 24) * 2.625f)
            scene.step(0.6)
            scene.save("04-help.png")
            // Hilfe schliessen (Tap konsumiert, kein Neustart)
            scene.tap(w / 2f, h * 0.85f)
            scene.step(0.3)

            // Zurueck ins Menue: MENUE-Knopf (ohne TEILEN zentriert, unter
            // "TIPPEN = NOCHMAL")
            scene.tap(w / 2f, h * 0.7375f)
            scene.step(0.5)
            scene.save("05-menu-back.png")

            // Einstellungen: Regler-Knopf oben rechts im READY
            scene.tap(w - (16 + 24) * 2.625f, (16 + 24) * 2.625f)
            scene.step(0.6)
            scene.save("06-settings.png")
            scene.tap(w / 2f, 200f)
            scene.step(0.3)

            // Skins: Taster-Leiste unten, mittleres Drittel
            scene.tap(w / 2f, h - 32 * 2.625f)
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
            scene.tap(w * 5f / 6f, h - 32 * 2.625f)
            scene.step(0.8)
            scene.save("08-stats.png")
        }
    }
}
