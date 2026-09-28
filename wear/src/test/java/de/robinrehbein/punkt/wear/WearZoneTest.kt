package de.robinrehbein.punkt.wear

import de.robinrehbein.punkt.game.TimingGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.round

/**
 * Die Zone der Uhr wie am Telefon (WearZone.kt). Geprüft wird die
 * Geometrie, aus der gezeichnet wird: Größenverlauf, Raster, Reihenfolge
 * und der Kern aus der Engine.
 */
class WearZoneTest {

    private val size = 454f
    private val radius = size * WEAR_TRACK_RADIUS_SHARE
    private val spacing = wearTrackSpacing(radius)
    private val zoneOuter = round(spacing * 1.23f)

    private fun running(): TimingGame = TimingGame().apply {
        twistOverride = emptySet()
        reseed(7L)
        start()
    }

    @Test
    fun `Größenverlauf wie am Telefon - Mitte voll, Rand wie Sand`() {
        assertEquals(1f, wearZoneBlockScale(0f), 1e-6f)
        assertEquals(0.6f, wearZoneBlockScale(1f), 1e-6f)
        assertEquals(0.6f, wearZoneBlockScale(3f), 1e-6f)
        var last = 1f
        for (i in 1..10) {
            val s = wearZoneBlockScale(i / 10f)
            assertTrue("fällt monoton", s <= last)
            last = s
        }
    }

    @Test
    fun `Rasterstufe hält das Verhältnis des Telefons`() {
        assertEquals(2, wearZoneUnit(spacing))
        assertEquals(1, wearZoneUnit(3f))
    }

    @Test
    fun `Blöcke liegen im Raster und haben mindestens vier Stufen`() {
        val game = running()
        val slots = wearZoneSlots(game, size / 2f, size / 2f, radius, game.perfectHalf(), spacing, zoneOuter)
        assertTrue(slots.isNotEmpty())
        for (z in slots) {
            val b = z.block
            val u = b.unit
            assertEquals(0f, b.left % u, 1e-4f)
            assertEquals(0f, b.top % u, 1e-4f)
            assertEquals(0f, b.outer % u, 1e-4f)
            assertTrue(b.outer >= 4 * u)
            assertTrue(b.outer <= zoneOuter + u)
        }
    }

    @Test
    fun `kleine Blöcke zuerst, damit die großen oben liegen`() {
        val game = running()
        val slots = wearZoneSlots(game, size / 2f, size / 2f, radius, game.perfectHalf(), spacing, zoneOuter)
        for (i in 1 until slots.size) assertTrue(slots[i - 1].scale <= slots[i].scale)
        // Der größte Block liegt in der Mitte und ist Kern.
        assertTrue(slots.last().core)
    }

    @Test
    fun `Kern und Zonenbreite kommen aus der Engine`() {
        val game = running()
        val slots = wearZoneSlots(game, 0f, 0f, radius, game.perfectHalf(), spacing, zoneOuter)
        val coreSlots = (0 until WEAR_TRACK_SEGMENTS).count { k ->
            val a = k.toFloat() / WEAR_TRACK_SEGMENTS * (2f * Math.PI.toFloat())
            abs(TimingGame.wrapToPi(a - game.zoneCenter)) <= game.perfectHalf()
        }
        val zoneSlots = (0 until WEAR_TRACK_SEGMENTS).count { k ->
            val a = k.toFloat() / WEAR_TRACK_SEGMENTS * (2f * Math.PI.toFloat())
            abs(TimingGame.wrapToPi(a - game.zoneCenter)) <= game.effectiveZoneHalf()
        }
        assertEquals(zoneSlots, slots.size)
        assertEquals(coreSlots, slots.count { it.core })
    }
}
