package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.random.Random

/** Sprite, Farben und Lauflicht der Bomben-Falle. */
class TrapPaintTest {

    private fun zeile(mask: List<Boolean>): String =
        mask.joinToString("") { if (it) "R" else "·" }

    @Test
    fun `Lauflicht bei sechs Bomben wie im Plan`() {
        val erwartet = listOf(
            "R·····", "RR····", "RRR···", "·RRR··", "··RRR·",
            "···RRR", "····RR", "·····R", "······"
        )
        assertEquals(erwartet, (0 until 9).map { zeile(TrapPaint.redMask(6, it)) })
        // Danach von vorn.
        assertEquals("R·····", zeile(TrapPaint.redMask(6, 9)))
    }

    @Test
    fun `Periode ist n plus ceil(n halbe) fuer n = 1 bis 10`() {
        for (n in 1..10) {
            val wB = ceil(n / 2.0).toInt()
            val periode = n + wB
            val folge = (0 until periode * 3).map { TrapPaint.redMask(n, it) }
            for (s in folge.indices) {
                assertEquals("n=$n Länge", n, folge[s].size)
                assertEquals("n=$n Schritt $s", folge[s % periode], folge[s])
            }
            // Keine kürzere Periode: Die Folge wiederholt sich nicht früher.
            for (p in 1 until periode) {
                assertTrue(
                    "n=$n hätte schon Periode $p",
                    (0 until periode).any { folge[it] != folge[it + p] }
                )
            }
            // Genau ein ganz dunkler Schritt, und der rote Block wird nie
            // breiter als wB.
            assertEquals("n=$n", 1, folge.take(periode).count { m -> m.none { it } })
            assertTrue(folge.all { m -> m.count { it } <= wB })
            assertTrue(folge.any { m -> m.count { it } == wB })
        }
    }

    @Test
    fun `negative und grosse Schritte werden gefaltet`() {
        assertEquals(TrapPaint.redMask(6, 8), TrapPaint.redMask(6, -1))
        assertEquals(TrapPaint.redMask(5, 2), TrapPaint.redMask(5, 2 + 8 * 1000))
        assertEquals(emptyList<Boolean>(), TrapPaint.redMask(0, 3))
    }

    @Test
    fun `Richtung ist immer plus oder minus eins und kommt ohne Zufall aus`() {
        val r = Random(7)
        val gesehen = mutableSetOf<Int>()
        repeat(2000) {
            val c = r.nextFloat() * 7f
            val d = TrapPaint.direction(c)
            assertTrue("direction($c) = $d", d == 1 || d == -1)
            assertEquals("gleiche Falle, gleiche Richtung", d, TrapPaint.direction(c))
            gesehen += d
        }
        assertEquals("beide Richtungen kommen vor", setOf(1, -1), gesehen)
        for (c in listOf(0f, -0f, 1.8f, 6.283f, Float.MIN_VALUE)) {
            assertTrue(TrapPaint.direction(c) in setOf(1, -1))
        }
    }

    @Test
    fun `Mine ist ein 7x7-Raster aus Kugel, Glanz und leer`() {
        assertEquals(TrapPaint.MINE_SIZE, TrapPaint.MINE.size)
        assertEquals(7, TrapPaint.MINE.size)
        for (z in TrapPaint.MINE) {
            assertEquals(7, z.length)
            assertTrue(z, z.all { it in ".OW" })
        }
        // Vier Zacken: Mitte oben, unten, links, rechts.
        assertEquals('O', TrapPaint.MINE[0][3])
        assertEquals('O', TrapPaint.MINE[6][3])
        assertEquals('O', TrapPaint.MINE[3][0])
        assertEquals('O', TrapPaint.MINE[3][6])
        assertTrue(TrapPaint.MINE.any { 'W' in it })
    }

    @Test
    fun `Farben wie im Plan`() {
        assertEquals(0xFF1E1A22, TrapPaint.BALL)
        assertEquals(0xFFF4E9EC, TrapPaint.RIM)
        assertEquals(0xFFFFFFFF, TrapPaint.GLOSS)
        assertEquals(0xFFE53935, TrapPaint.RED)
        assertEquals(0.09f, TrapPaint.LIGHT_STEP_SECONDS, 0f)
    }

    @Test
    fun `Bombenzahl haengt an der Grundbreite und ist mindestens eins`() {
        val block = (2 * Math.PI / TimingGame.TRACK_SEGMENTS).toFloat()
        assertEquals(7, TrapPaint.count(TimingGame.BASE_ZONE_HALF, block))
        assertEquals(2, TrapPaint.count(TimingGame.MIN_ZONE_HALF, block))
        assertEquals(1, TrapPaint.count(0.01f, block))
        assertEquals(1, TrapPaint.count(0.4f, 0f))
        // Monoton: breitere Falle, nie weniger Bomben.
        var vorher = 0
        var h = TimingGame.MIN_ZONE_HALF
        while (h <= TimingGame.BASE_ZONE_HALF) {
            val n = TrapPaint.count(h, block)
            assertTrue(n >= vorher)
            vorher = n
            h += 0.005f
        }
    }
}
