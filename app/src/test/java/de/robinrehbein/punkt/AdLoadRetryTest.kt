package de.robinrehbein.punkt

import de.robinrehbein.punkt.ads.AdLoadRetry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die zweite Ads-Regel neben dem Frequenz-Deckel: Wann darf eine
 * Anzeige nachgeladen werden?
 *
 * Sie entscheidet über zwei gegenläufige Fehler — eine Sitzung ganz ohne
 * Anzeigen, weil der erste Ladeversuch danebenging, und ein Spiel, das
 * bei dauerhaftem "no fill" pausenlos Anfragen absetzt.
 */
class AdLoadRetryTest {

    /** Testuhr in Millisekunden, von Hand weitergedreht. */
    private class Clock(var now: Long = 0L) {
        fun advanceSeconds(seconds: Long) {
            now += seconds * 1000L
        }
    }

    private fun retryWith(clock: Clock) = AdLoadRetry { clock.now }

    @Test
    fun `der erste Versuch darf sofort los`() {
        assertTrue(retryWith(Clock()).shouldStart())
    }

    @Test
    fun `eine laufende Anfrage wird nicht verdoppelt`() {
        val retry = retryWith(Clock())
        assertTrue(retry.shouldStart())

        // Zweiter Anstoß, während die erste Anfrage noch läuft — etwa der
        // nächste Tod wenige Sekunden später.
        assertFalse(retry.shouldStart())
    }

    @Test
    fun `nach einem Fehlschlag wird gewartet`() {
        val clock = Clock()
        val retry = retryWith(clock)
        retry.shouldStart()
        retry.onFailed()

        assertFalse(retry.shouldStart())
        clock.advanceSeconds(59)
        assertFalse(retry.shouldStart())
    }

    @Test
    fun `nach der Wartezeit darf es wieder versucht werden`() {
        val clock = Clock()
        val retry = retryWith(clock)
        retry.shouldStart()
        retry.onFailed()

        clock.advanceSeconds(60)
        assertTrue(retry.shouldStart())
    }

    @Test
    fun `eine geladene Anzeige gibt den Weg sofort wieder frei`() {
        val clock = Clock()
        val retry = retryWith(clock)
        retry.shouldStart()
        retry.onFailed()
        clock.advanceSeconds(60)
        retry.shouldStart()
        retry.onLoaded()

        // Die Anzeige wurde gezeigt, gleich darauf wird nachgeladen — das
        // darf keine Wartezeit aus einem alten Fehlschlag erben.
        assertTrue(retry.shouldStart())
    }

    @Test
    fun `dauerhaftes no fill fuehrt nicht zu Dauerfeuer`() {
        val clock = Clock()
        val retry = retryWith(clock)
        var versuche = 0

        // Zehn Minuten lang alle fünf Sekunden ein Tod — mehr als ein
        // Versuch pro Minute darf dabei nicht herauskommen.
        repeat(120) {
            if (retry.shouldStart()) {
                versuche++
                retry.onFailed()
            }
            clock.advanceSeconds(5)
        }

        assertTrue("Zu viele Anfragen: $versuche", versuche <= 11)
        assertTrue("Gar nicht mehr versucht", versuche >= 10)
    }
}
