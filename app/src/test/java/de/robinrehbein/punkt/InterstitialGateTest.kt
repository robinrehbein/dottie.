package de.robinrehbein.punkt

import de.robinrehbein.punkt.ads.InterstitialGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Frequenz-Deckel entscheidet, wie aufdringlich sich das Spiel
 * anfühlt — deshalb ist er die einzige Ads-Logik, die wirklich getestet
 * wird (der Rest ist SDK-Verdrahtung).
 */
class InterstitialGateTest {

    /** Testuhr in Millisekunden, von Hand weitergedreht. */
    private class Clock(var now: Long = 0L) {
        fun advanceSeconds(seconds: Long) {
            now += seconds * 1000L
        }
    }

    private fun gateWith(clock: Clock) = InterstitialGate { clock.now }

    @Test
    fun `the first five deaths of a session stay ad-free`() {
        val gate = gateWith(Clock())

        repeat(5) { assertFalse(gate.onDeathShouldShow()) }
        assertEquals(5, gate.deathCount)
    }

    @Test
    fun `the sixth death may show an interstitial`() {
        val gate = gateWith(Clock())
        repeat(5) { gate.onDeathShouldShow() }

        assertTrue(gate.onDeathShouldShow())
    }

    @Test
    fun `no second interstitial right after one was shown`() {
        val clock = Clock()
        val gate = gateWith(clock)
        repeat(5) { gate.onDeathShouldShow() }
        assertTrue(gate.onDeathShouldShow())
        gate.markShown()

        // Mehrere schnelle Läufe direkt danach — alle bleiben werbefrei.
        clock.advanceSeconds(8)
        assertFalse(gate.onDeathShouldShow())
        clock.advanceSeconds(30)
        assertFalse(gate.onDeathShouldShow())
        clock.advanceSeconds(60)
        assertFalse(gate.onDeathShouldShow())
    }

    @Test
    fun `after the interval another interstitial is allowed`() {
        val clock = Clock()
        val gate = gateWith(clock)
        repeat(5) { gate.onDeathShouldShow() }
        gate.onDeathShouldShow()
        gate.markShown()

        clock.advanceSeconds(179)
        assertFalse(gate.onDeathShouldShow())

        clock.advanceSeconds(1) // genau 180 s Abstand
        assertTrue(gate.onDeathShouldShow())
    }

    @Test
    fun `an interstitial that never ran does not burn the time window`() {
        val clock = Clock()
        val gate = gateWith(clock)
        repeat(5) { gate.onDeathShouldShow() }

        // Erlaubnis erteilt, aber nichts geladen → kein markShown().
        assertTrue(gate.onDeathShouldShow())

        // Der nächste Tod darf es deshalb sofort wieder versuchen.
        assertTrue(gate.onDeathShouldShow())
    }

    @Test
    fun `the death counter keeps running across shown ads`() {
        val clock = Clock()
        val gate = gateWith(clock)
        repeat(8) {
            if (gate.onDeathShouldShow()) gate.markShown()
            clock.advanceSeconds(20)
        }

        assertEquals(8, gate.deathCount)
    }
}
