package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random

/**
 * SPIEGEL und TEMPO (ab v2.36): die zwei späten Twists für alle, die
 * über Score 35 hinauskommen.
 */
class LateTwistTest {

    private val dt = 1f / 480f

    private fun spiel(twists: Set<Twist>, seed: Int = 11): TimingGame =
        TimingGame(Random(seed)).apply {
            twistOverride = twists
            start()
        }

    /** Läuft bis tief in die Zone und tappt dort. */
    private fun TimingGame.triffMitte(): GameEvent? {
        var guard = 0
        while (abs(relativeToZone()) > zoneHalfWidth * 0.1f && guard++ < 100_000) {
            update(dt)
            assertEquals("Lauf endete vor der Zone", GamePhase.RUNNING, phase)
        }
        return tap()
    }

    @Test
    fun `Schwellen 35 und 45`() {
        assertEquals(35, TimingGame.unlockScore(Twist.MIRROR))
        assertEquals(45, TimingGame.unlockScore(Twist.TEMPO))
    }

    @Test
    fun `SPIEGEL dreht nicht um und legt die naechste Zone gegenueber`() {
        val s = spiel(setOf(Twist.MIRROR))
        repeat(20) {
            assertTrue(s.isMirrorZone)
            val richtung = s.direction
            s.triffMitte()
            assertEquals("keine Wende", richtung, s.direction)
            val abstand = -s.relativeToZone()
            assertTrue(
                "gegenüber: $abstand",
                abstand in (PI.toFloat() - TimingGame.MIRROR_GAP - TimingGame.MIRROR_SPREAD - 1e-4f)..
                    (PI.toFloat() - TimingGame.MIRROR_GAP + 1e-4f)
            )
        }
        assertEquals(20, s.hits)
    }

    @Test
    fun `ohne SPIEGEL dreht der Punkt wie immer um`() {
        val s = spiel(emptySet())
        val richtung = s.direction
        s.triffMitte()
        assertEquals(-richtung, s.direction)
        assertFalse(s.isMirrorZone)
    }

    @Test
    fun `TEMPO aendert das Tempo nur im Band vor der Zone`() {
        val s = spiel(setOf(Twist.TEMPO))
        var schnell = 0
        var langsam = 0
        repeat(30) {
            assertTrue(s.hasTempo)
            assertTrue(s.tempoFactor == TimingGame.TEMPO_FAST || s.tempoFactor == TimingGame.TEMPO_SLOW)
            if (s.isTempoFast) schnell++ else langsam++
            var sahBand = false
            var guard = 0
            while (abs(s.relativeToZone()) > s.zoneHalfWidth * 0.1f && guard++ < 100_000) {
                if (s.isInTempoBand) {
                    sahBand = true
                    assertEquals(s.currentSpeed() * s.tempoFactor, s.dotSpeed(), 1e-5f)
                } else {
                    assertEquals(s.currentSpeed(), s.dotSpeed(), 1e-6f)
                }
                s.update(dt)
                assertEquals(GamePhase.RUNNING, s.phase)
            }
            assertTrue("das Band muss durchflogen werden", sahBand)
            // In der Zone gilt wieder das normale Tempo.
            assertEquals(s.currentSpeed(), s.dotSpeed(), 1e-6f)
            s.tap()
        }
        assertTrue("beide Tempi kommen vor: $schnell/$langsam", schnell > 0 && langsam > 0)
    }

    @Test
    fun `das Tempo-Band endet an der Zonenkante`() {
        val s = spiel(setOf(Twist.TEMPO))
        assertEquals(-s.zoneHalfWidth, s.tempoEnd(), 1e-6f)
        assertEquals(-s.zoneHalfWidth - s.currentSpeed() * TimingGame.TEMPO_SECONDS, s.tempoStart(), 1e-6f)
    }

    @Test
    fun `ohne TEMPO kein Faktor`() {
        val s = spiel(emptySet())
        assertEquals(1f, s.tempoFactor)
        assertFalse(s.hasTempo)
    }

    @Test
    fun `im echten Lauf nie Nebel mit Tempo und nie Kette mit Spiegel`() {
        var spiegel = 0
        var tempo = 0
        for (seed in 0 until 40) {
            val s = TimingGame(Random(seed))
            s.start()
            var guard = 0
            while (s.hits < 60 && s.phase == GamePhase.RUNNING && guard++ < 400_000) {
                s.update(dt)
                if (s.phase != GamePhase.RUNNING) break
                val t = s.activeTwists
                assertFalse("Nebel + Tempo (Seed $seed)", Twist.GHOST in t && Twist.TEMPO in t)
                assertFalse("Kette + Spiegel (Seed $seed)", Twist.CHAIN in t && Twist.MIRROR in t)
                if (abs(s.relativeToZone()) <= s.zoneHalfWidth * 0.1f) {
                    if (Twist.MIRROR in t) spiegel++
                    if (Twist.TEMPO in t) tempo++
                    s.tap()
                }
            }
            assertEquals("Bot stirbt nicht (Seed $seed)", 60, s.hits)
        }
        assertTrue("SPIEGEL kommt vor", spiegel > 20)
        assertTrue("TEMPO kommt vor", tempo > 20)
    }
}
