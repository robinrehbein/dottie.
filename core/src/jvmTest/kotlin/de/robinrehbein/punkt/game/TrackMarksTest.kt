package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Die Bahn-Markierungen von SPIEGEL und TEMPO (ab v2.36) stehen genau
 * dort, wo der Twist wirkt, und nirgends sonst.
 */
class TrackMarksTest {

    private val segments = 60

    private fun spiel(twists: Set<Twist>, seed: Int = 5) =
        TimingGame(Random(seed)).apply { twistOverride = twists; start() }

    private fun TimingGame.winkel(rel: Float) =
        TimingGame.wrapTwoPi(zoneCenter + direction * rel)

    @Test
    fun `ohne spaete Twists keine Markierung`() {
        val s = spiel(setOf(Twist.PULSE, Twist.DRIFT))
        for (k in 0 until segments) {
            assertNull(TrackMarks.at(s, k * 2f * Math.PI.toFloat() / segments, segments))
        }
    }

    @Test
    fun `TEMPO markiert genau das Band vor der Zone`() {
        val s = spiel(setOf(Twist.TEMPO))
        val erwartet = if (s.isTempoFast) TrackMark.FAST else TrackMark.SLOW
        val mitte = (s.tempoStart() + s.tempoEnd()) / 2f
        assertEquals(erwartet, TrackMarks.at(s, s.winkel(mitte), segments))
        assertNull(TrackMarks.at(s, s.winkel(s.tempoStart() - 0.05f), segments))
        assertNull("in der Zone nicht", TrackMarks.at(s, s.winkel(0f), segments))
    }

    @Test
    fun `SPIEGEL markiert hinter der Zone, nicht davor`() {
        val s = spiel(setOf(Twist.MIRROR))
        val h = s.effectiveZoneHalf()
        assertEquals(TrackMark.ONWARD, TrackMarks.at(s, s.winkel(h + 0.05f), segments))
        assertNull(TrackMarks.at(s, s.winkel(-h - 0.05f), segments))
        val ende = h + TrackMarks.MIRROR_BLOCKS * 2f * Math.PI.toFloat() / segments
        assertNull(TrackMarks.at(s, s.winkel(ende + 0.05f), segments))
    }

    @Test
    fun `keine Markierung vor dem Start`() {
        val s = TimingGame(Random(1)).apply { twistOverride = setOf(Twist.TEMPO, Twist.MIRROR) }
        assertNull(TrackMarks.at(s, s.zoneCenter, segments))
    }

    @Test
    fun `die Farben halten Abstand zum Gruen der Zone`() {
        fun abstand(a: Long, b: Long): Float {
            fun c(x: Long, s: Int) = ((x shr s) and 0xFF).toFloat()
            val dr = c(a, 16) - c(b, 16); val dg = c(a, 8) - c(b, 8); val db = c(a, 0) - c(b, 0)
            return sqrt(dr * dr + dg * dg + db * db)
        }
        TrackMark.entries.forEach { m ->
            listOf(0xFF74BF2EL, 0xFF9DE85AL).forEach { gruen ->
                assertTrue("$m zu nah am Grün", abstand(TrackMarks.face(m), gruen) > 60f)
            }
        }
        assertTrue(abs(TrackMarks.face(TrackMark.FAST) - TrackMarks.face(TrackMark.SLOW)) > 0)
    }
}
