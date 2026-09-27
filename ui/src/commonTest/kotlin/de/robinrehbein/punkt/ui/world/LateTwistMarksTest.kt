package de.robinrehbein.punkt.ui.world

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

/** Die Pfeile der Bahn-Markierung zeigen in Laufrichtung (ab v2.36). */
class LateTwistMarksTest {

    @Test
    fun `die Laufrichtung wird auf acht Richtungen gerundet`() {
        // Ganz rechts auf dem Ring (Winkel 0), im Uhrzeigersinn: nach unten.
        assertEquals(2, travelOctant(0f, 1))
        // Gegen den Uhrzeigersinn: nach oben.
        assertEquals(6, travelOctant(0f, -1))
        // Ganz unten (Winkel PI/2), im Uhrzeigersinn: nach links.
        assertEquals(4, travelOctant((PI / 2).toFloat(), 1))
        // Ganz oben, im Uhrzeigersinn: nach rechts.
        assertEquals(0, travelOctant((-PI / 2).toFloat(), 1))
    }

    @Test
    fun `die Pfeilspitze dreht sich mit`() {
        assertEquals(listOf(0 to 0, 1 to 1, 0 to 2), arrowCells(0))
        // Eine Vierteldrehung: der Winkel zeigt nach unten.
        assertEquals(setOf(2 to 0, 1 to 1, 0 to 0), arrowCells(2).toSet())
        // Alle acht bleiben im 3×3-Raster.
        (0 until 8).forEach { o ->
            arrowCells(o).forEach { (x, y) -> assert(x in 0..2 && y in 0..2) }
        }
    }
}
