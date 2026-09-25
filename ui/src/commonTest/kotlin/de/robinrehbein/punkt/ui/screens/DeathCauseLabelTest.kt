package de.robinrehbein.punkt.ui.screens

import androidx.compose.ui.geometry.Size
import de.robinrehbein.punkt.game.DeathCause
import de.robinrehbein.punkt.ui.world.DOT_RADIUS_SHARE
import de.robinrehbein.punkt.ui.world.ringGeometry
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Die Todesursache steht am Ring, aber nie auf ihm: Dort liegen Zone und
 * Falle, und genau die soll man im Freeze noch sehen. Geprüft gegen
 * [ringGeometry], also dieselbe Quelle, aus der die Kreisbahn gezeichnet
 * wird, auf dem Zielformat 1080×2400 und dem kleinen 720×1280.
 */
class DeathCauseLabelTest {

    private data class Format(val width: Float, val height: Float, val density: Float)

    private val formats = listOf(
        Format(1080f, 2400f, 2.625f),
        Format(720f, 1280f, 2f)
    )

    @Test
    fun `die Fläche der Ursache liegt außerhalb des Rings`() {
        formats.forEach { f ->
            val size = Size(f.width, f.height)
            val ring = ringGeometry(size)
            val area = deathCauseLabelArea(size)
            // Nächster Punkt der Fläche zur Ringmitte.
            val nx = ring.cx.coerceIn(area.left, area.right)
            val ny = ring.cy.coerceIn(area.top, area.bottom)
            val dx = nx - ring.cx
            val dy = ny - ring.cy
            val distance = sqrt(dx * dx + dy * dy)
            // Die Bahn plus der Vogel, der über sie hinausragt.
            val outer = ring.radius + f.height * DOT_RADIUS_SHARE
            assertTrue(
                distance > outer,
                "${f.width}×${f.height}: Abstand $distance, Ring reicht bis $outer"
            )
        }
    }

    @Test
    fun `die Fläche liegt im Bild und fasst Ursache und Lektion`() {
        formats.forEach { f ->
            val area = deathCauseLabelArea(Size(f.width, f.height))
            val name = "${f.width}×${f.height}"
            assertTrue(area.left >= 0f && area.right <= f.width, "$name: seitlich außerhalb")
            assertTrue(area.top >= 0f && area.bottom <= f.height, "$name: unten außerhalb")
            val needed = DEATH_LABEL_CONTENT_HEIGHT.value * f.density
            assertTrue(area.height >= needed, "$name: ${area.height} px Platz, gebraucht $needed px")
            assertTrue(area.width >= f.width * 0.8f, "$name: Fläche zu schmal")
        }
    }

    @Test
    fun `jede Ursache hat einen eigenen Text, NONE keinen`() {
        assertNull(deathCauseText(DeathCause.NONE))
        val texts = DeathCause.entries.filter { it != DeathCause.NONE }.map { deathCauseText(it) }
        assertTrue(texts.none { it == null })
        assertEquals(texts.size, texts.toSet().size, "zwei Ursachen mit demselben Text")
    }
}
