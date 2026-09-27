package de.robinrehbein.punkt.wear

import androidx.compose.ui.graphics.Color
import de.robinrehbein.punkt.game.BevelPaint
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SkinState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.round

/**
 * Bevel-Look auf der Uhr: die Kantenregel der eigenen bevelRect-Fassung,
 * die Kantenbreite der Bahn-Blöcke und die Kugel des Vogels. Die Farben
 * selbst prüft :core (BevelPaintTest) — hier geht es darum, dass die Uhr
 * sie so abgreift, dass auf jeder Displaygröße etwas davon sichtbar ist.
 */
class WearBevelTest {

    /** Runde Uhren von 384 (Pixel Watch) bis 480 px (Galaxy Watch Ultra). */
    private val displays = listOf(384f, 396f, 416f, 450f, 454f, 466f, 480f)

    @Test
    fun `Flaechen unter drei Kanten bleiben flach`() {
        assertTrue(wearBevelFits(6f, 6f, 2f))
        assertFalse(wearBevelFits(5f, 6f, 2f))
        assertFalse(wearBevelFits(6f, 5f, 2f))
        // Gerundet wird wie beim Zeichnen: 2,5 zählt als 3.
        assertFalse(wearBevelFits(8f, 8f, 2.5f))
        // Nie eine Kante unter einem Bildpunkt.
        assertEquals(1f, wearBevelEdge(0.2f))
        assertTrue(wearBevelFits(3f, 3f, 0.2f))
    }

    @Test
    fun `Die Sandbloecke tragen auf jeder Uhr eine Kante`() {
        // Dieselbe Rechnung wie drawWearTrack: Ohne Kante sähe die Bahn
        // der Uhr wieder aus wie vor dem Bevel-Look.
        for (d in displays) {
            val radius = d * 0.38f
            val spacing = 2f * PI.toFloat() * radius / WEAR_TRACK_SEGMENTS
            val outer = round(spacing * 0.74f).coerceAtLeast(2f)
            val inner = round(outer * 0.6f).coerceAtLeast(1f)
            val edge = wearTrackEdge(inner)
            assertTrue("d=$d inner=$inner edge=$edge", wearBevelFits(inner, inner, edge))
            assertTrue("d=$d: Kante ganze Pixel", edge == round(edge) && edge >= 1f)
        }
    }

    @Test
    fun `Farben laufen verlustfrei nach core und zurueck`() {
        for (argb in listOf(0xFFD3C87EL, 0xFF543847L, 0xFFFFFFFFL, 0xFF000000L)) {
            assertEquals(argb, Color(argb).toArgbLong())
        }
    }

    @Test
    fun `Der Vogel der Uhr ist dieselbe Kugel wie am Telefon`() {
        val state = SkinState()
        for (skin in WearDotSkin.entries) {
            val shine = skin.shineArgb(state)
            for (row in 0 until 13) for (col in 0 until 13) {
                val expected = BevelPaint.kugel(col, row, SkinPaint.cell(skin.id, col, row, state), shine)
                assertEquals("$skin ($col, $row)", expected, skin.kugelCell(col, row, state, shine).toArgbLong())
            }
        }
    }

    @Test
    fun `Oben links ist der Vogel heller als unten rechts`() {
        val state = SkinState()
        val skin = WearDotSkin.KLASSIK
        val shine = skin.shineArgb(state)
        fun sum(c: Long) = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
        val lit = skin.kugelCell(3, 3, state, shine).toArgbLong()
        val mid = skin.kugelCell(6, 6, state, shine).toArgbLong()
        val shade = skin.kugelCell(10, 10, state, shine).toArgbLong()
        assertTrue(sum(lit) > sum(mid))
        assertTrue(sum(mid) > sum(shade))
    }
}
