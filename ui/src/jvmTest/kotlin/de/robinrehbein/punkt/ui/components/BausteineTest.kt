package de.robinrehbein.punkt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import de.robinrehbein.punkt.ui.world.PanelSand
import de.robinrehbein.punkt.ui.world.TextDark
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * Die Bedien-Bausteine aus Plan 7.1/7.2 headless gedrückt: Tick über
 * [LocalPressFeedback], Einsinken um den Pixelschatten, Schalter und X.
 *
 * Mit SHOTS_DIR schreibt der Test zusätzlich `bausteine.png` und
 * `bausteine-gedrueckt.png` dorthin.
 */
class BausteineTest {

    private val density = 2f
    private fun dp(v: Float) = v * density

    private class Zaehler : PressFeedback {
        var ticks = 0
        override fun tap() {
            ticks++
        }
    }

    private var now = 0L
    private fun ImageComposeScene.frame(): Image {
        now += 16_000_000L
        return render(now)
    }

    private fun ImageComposeScene.press(x: Float, y: Float) {
        sendPointerEvent(PointerEventType.Press, Offset(x, y))
        frame()
        frame()
    }

    private fun ImageComposeScene.release(x: Float, y: Float) {
        sendPointerEvent(PointerEventType.Release, Offset(x, y))
        frame()
        frame()
    }

    private fun ImageComposeScene.tapAt(x: Float, y: Float) {
        press(x, y)
        release(x, y)
    }

    private fun Image.pixel(x: Float, y: Float): Int =
        Bitmap.makeFromImage(this).getColor(x.toInt(), y.toInt())

    // Aufbau: Knopf oben links bei (0,0) 120×40 dp + 4 dp Schatten,
    // X-Knopf bei (0,60) 48 dp, Schalterzeile bei (0,120) 244 dp breit.
    private fun scene(
        zaehler: Zaehler,
        clicks: IntArray,
        switch: () -> Boolean,
        onSwitch: (Boolean) -> Unit,
        enabled: Boolean = true
    ) = ImageComposeScene(width = 600, height = 420, density = Density(density)) {
        CompositionLocalProvider(LocalPressFeedback provides zaehler) {
            Box(Modifier.fillMaxSize().background(Color(0xFF4EC0CA))) {
                PixelButton(
                    text = "MENÜ",
                    onClick = { clicks[0]++ },
                    backgroundColor = PanelSand,
                    borderColor = TextDark,
                    textColor = TextDark,
                    width = 120.dp,
                    height = 40.dp,
                    borderWidth = 3.dp,
                    enabled = enabled
                )
                OverlayCloseButton(
                    onClose = { clicks[1]++ },
                    modifier = Modifier.padding(top = 60.dp),
                    contentDescription = "SCHLIESSEN"
                )
                PixelSwitchRow(
                    label = "TON",
                    checked = switch(),
                    icon = PixelIcon.SPEAKER_ON,
                    onCheckedChange = onSwitch,
                    modifier = Modifier.padding(top = 120.dp)
                )
            }
        }
    }

    @Test
    fun knopfTicktSinktEinUndKlickt() {
        val zaehler = Zaehler()
        val clicks = IntArray(2)
        scene(zaehler, clicks, { false }, {}).use { s ->
            val vorher = s.frame()
            // Oben links am Rand: ungedrückt dunkler Rand, gedrückt
            // (4 dp eingesunken) liegt dort der Hintergrund.
            val probeX = dp(1.5f)
            val probeY = dp(1.5f)
            val randFarbe = vorher.pixel(probeX, probeY)
            // Die Schattenecke unten rechts: ungedrückt Schatten, gedrückt Knopf.
            val schattenX = dp(122f)
            val schattenY = dp(42f)
            val schatten = vorher.pixel(schattenX, schattenY)

            s.press(dp(60f), dp(20f))
            val gedrueckt = s.frame()
            assertNotEquals(randFarbe, gedrueckt.pixel(probeX, probeY), "Knopf sinkt nicht ein")
            assertNotEquals(schatten, gedrueckt.pixel(schattenX, schattenY), "Schatten bleibt beim Drücken")
            assertEquals(0, zaehler.ticks, "Tick erst beim Loslassen")

            s.release(dp(60f), dp(20f))
            assertEquals(1, clicks[0])
            assertEquals(1, zaehler.ticks)
            assertEquals(randFarbe, s.frame().pixel(probeX, probeY), "Knopf kommt nicht zurück")
        }
    }

    @Test
    fun gesperrterKnopfTicktNicht() {
        val zaehler = Zaehler()
        val clicks = IntArray(2)
        scene(zaehler, clicks, { false }, {}, enabled = false).use { s ->
            val vorher = s.frame().pixel(dp(1.5f), dp(1.5f))
            s.press(dp(60f), dp(20f))
            assertEquals(vorher, s.frame().pixel(dp(1.5f), dp(1.5f)), "gesperrt sinkt nicht ein")
            s.release(dp(60f), dp(20f))
            assertEquals(0, clicks[0])
            assertEquals(0, zaehler.ticks)
        }
    }

    @Test
    fun schliessenUndSchalterTicken() {
        val zaehler = Zaehler()
        val clicks = IntArray(2)
        var an by mutableStateOf(false)
        scene(zaehler, clicks, { an }, { an = it }).use { s ->
            s.frame()
            s.tapAt(dp(24f), dp(84f))
            assertEquals(1, clicks[1])
            assertEquals(1, zaehler.ticks)

            // Schalter rechts in der Zeile (12 dp Innenabstand, 52 dp breit,
            // Zeile 48 dp hoch): aus = Knebel links dunkel.
            val mitteY = dp(120f + 24f)
            val links = Offset(dp(244f - 12f - 52f + 12f), mitteY)
            val rechts = Offset(dp(244f - 12f - 12f), mitteY)
            val ausBild = s.frame()
            val linksAus = ausBild.pixel(links.x, links.y)
            val rechtsAus = ausBild.pixel(rechts.x, rechts.y)

            // Auf die Beschriftung tippen: die ganze Zeile schaltet.
            s.tapAt(dp(80f), mitteY)
            assertEquals(true, an)
            assertEquals(2, zaehler.ticks)
            val anBild = s.frame()
            assertNotEquals(linksAus, anBild.pixel(links.x, links.y), "Knebel links bleibt")
            assertNotEquals(rechtsAus, anBild.pixel(rechts.x, rechts.y), "Knebel rechts fehlt")

            s.tapAt(dp(80f), mitteY)
            assertEquals(false, an)
            assertEquals(3, zaehler.ticks)
        }
    }

    /** Werkzeug, kein Prüfstein: Bilder der Bausteine, nur mit SHOTS_DIR. */
    @Test
    fun bilder() {
        val dir = System.getenv("SHOTS_DIR") ?: return
        File(dir).mkdirs()
        val d = 2.625f
        fun bild(name: String, gedrueckt: Boolean) {
            ImageComposeScene(width = 1080, height = 1100, density = Density(d)) {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF4EC0CA)).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PixelButton(
                            "MENÜ", {}, backgroundColor = PanelSand, borderColor = TextDark,
                            textColor = TextDark, width = 120.dp, height = 44.dp, borderWidth = 3.dp
                        )
                        PixelButton(
                            "TEILEN", {}, backgroundColor = Color(0xFFFFD847), borderColor = TextDark,
                            textColor = TextDark, width = 120.dp, height = 44.dp, borderWidth = 3.dp
                        )
                        PixelIconButton(
                            PixelIcon.SLIDERS, "Einstellungen", {}, backgroundColor = PanelSand,
                            borderColor = TextDark, shadow = 4.dp
                        )
                    }
                    OverlayCloseButton(onClose = {}, contentDescription = "SCHLIESSEN")
                    PixelSwitchRow("TON", true, PixelIcon.SPEAKER_ON, {})
                    PixelSwitchRow("ERINNERUNG", false, PixelIcon.BELL_OFF, {})
                    PixelButton(
                        "HILFE", {}, backgroundColor = PanelSand, borderColor = TextDark,
                        textColor = TextDark, width = 244.dp, height = 48.dp, borderWidth = 3.dp
                    )
                }
            }.use { s ->
                var t = 0L
                s.render(t)
                if (gedrueckt) {
                    // Gehalten wird MENÜ, oben links in der ersten Zeile.
                    s.sendPointerEvent(PointerEventType.Press, Offset((24 + 60) * d, (24 + 22) * d))
                    t += 16_000_000L
                    s.render(t)
                }
                t += 16_000_000L
                val data = s.render(t).encodeToData(EncodedImageFormat.PNG)!!
                File(dir, name).writeBytes(data.bytes)
                println("-> $name")
            }
        }
        bild("bausteine.png", gedrueckt = false)
        bild("bausteine-gedrueckt.png", gedrueckt = true)
    }
}
