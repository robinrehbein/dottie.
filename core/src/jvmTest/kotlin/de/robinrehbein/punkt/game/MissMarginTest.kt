package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * „Knapp daneben" (ab v2.36): Die Engine hält fest, um wie viele Sekunden
 * ein tödlicher Tap danebenlag ([TimingGame.lastMissSeconds]). Die Zahl
 * muss ehrlich sein — wer „0,02 S ZU FRÜH" liest, hätte mit 0,02 s später
 * wirklich getroffen.
 */
class MissMarginTest {

    private val dt = 1f / 480f

    private fun spiel(): TimingGame =
        TimingGame(Random(7)).apply {
            twistOverride = emptySet()
            start()
        }

    /** Läuft, bis [bedingung] gilt — oder bis der Lauf von selbst endet. */
    private fun TimingGame.laufeBis(bedingung: TimingGame.() -> Boolean) {
        var guard = 0
        while (!bedingung() && guard++ < 50_000) {
            update(dt)
            assertEquals("Lauf endete zu früh", GamePhase.RUNNING, phase)
        }
        assertTrue(bedingung())
    }

    @Test
    fun `ein knapp zu frueher Tap misst den Abstand zur Zonenkante`() {
        val s = spiel()
        // 30 ms vor der Kante tappen.
        val vorKante = -s.effectiveZoneHalf() - s.currentSpeed() * 0.03f
        s.laufeBis { relativeToZone() >= vorKante }
        val rel = s.relativeToZone()
        assertEquals(GameEventDied, s.tap())
        assertEquals(DeathCause.EARLY, s.lastDeathCause)
        val erwartet = (-rel - s.effectiveZoneHalf()) / s.currentSpeed()
        assertEquals(erwartet, s.lastMissSeconds, 1e-4f)
        assertTrue("etwa 30 ms: ${s.lastMissSeconds}", s.lastMissSeconds in 0.025f..0.031f)
        assertTrue(s.lastMissWasNear)
    }

    @Test
    fun `ein weit zu frueher Tap ist nicht knapp`() {
        val s = spiel()
        // Direkt nach dem Start liegt die Zone mindestens MIN_REACTION_SECONDS voraus.
        assertEquals(GameEventDied, s.tap())
        assertEquals(DeathCause.EARLY, s.lastDeathCause)
        assertTrue(s.lastMissSeconds > TimingGame.NEAR_MISS_SECONDS)
        assertFalse(s.lastMissWasNear)
    }

    @Test
    fun `ZU SPAET misst gegen das Ende der Spaet-Gnade, nicht gegen die sichtbare Kante`() {
        val s = spiel()
        val gnade = s.effectiveZoneHalf() + s.currentSpeed() * TimingGame.LATE_TAP_FORGIVENESS_SECONDS
        // 10 ms hinter der Gnade — sichtbar schon 80 ms hinter der Kante.
        val ziel = gnade + s.currentSpeed() * 0.01f
        s.laufeBis { relativeToZone() >= ziel }
        val rel = s.relativeToZone()
        assertEquals(GameEventDied, s.tap())
        assertEquals(DeathCause.LATE, s.lastDeathCause)
        val erwartet = (rel - gnade) / s.currentSpeed()
        assertEquals(erwartet, s.lastMissSeconds, 1e-4f)
        assertTrue("etwa 10 ms: ${s.lastMissSeconds}", s.lastMissSeconds < 0.02f)
        assertTrue(s.lastMissWasNear)
    }

    @Test
    fun `VERPASST und ein Neustart kennen keinen Abstand`() {
        val s = spiel()
        var guard = 0
        while (s.phase == GamePhase.RUNNING && guard++ < 50_000) s.update(dt)
        assertEquals(DeathCause.MISSED, s.lastDeathCause)
        assertEquals(0f, s.lastMissSeconds)
        assertFalse(s.lastMissWasNear)

        val t = spiel()
        t.tap()
        assertTrue(t.lastMissSeconds > 0f)
        t.reset()
        assertEquals(0f, t.lastMissSeconds)
    }
}
