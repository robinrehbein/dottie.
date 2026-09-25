package de.robinrehbein.punkt

import de.robinrehbein.punkt.ui.world.FxState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Effekt-Zustand überlebt den Lauf: Wer nach dem Tod ins Menü geht,
 * darf keine laufende Sturz-Animation mitnehmen — sonst wäre der Vogel im
 * Startbild schon aus dem Kader gefallen und damit unsichtbar.
 */
class FxStateTest {

    // == AP-11 todesursache ==
    // == /AP-11 ==
    @Test
    fun `reset beendet die Sturz-Animation`() {
        val fx = FxState()
        fx.deathTime = 1.4f

        fx.reset()

        assertTrue("Sturz muss als beendet gelten", fx.deathTime < 0f)
    }

    // == AP-12 bomben ==
    // == /AP-12 ==
    @Test
    fun `reset raeumt alle Effekte ab`() {
        val fx = FxState()
        fx.flashAlpha = 1f
        // == AP-11 todesursache ==
        // == /AP-11 ==
        fx.shakeTime = 0.4f
        // == AP-12 bomben ==
        // == /AP-12 ==
        fx.celebrateTime = 1.1f
        // == AP-22 start ==
        // == /AP-22 ==
        fx.deathTime = 0.2f
        // == AP-23 nebel ==
        // == /AP-23 ==

        fx.reset()

        assertEquals(0f, fx.flashAlpha, 0f)
        // == AP-11 todesursache ==
        // == /AP-11 ==
        assertEquals(0f, fx.shakeTime, 0f)
        // == AP-12 bomben ==
        // == /AP-12 ==
        assertEquals(0f, fx.celebrateTime, 0f)
        // == AP-22 start ==
        // == /AP-22 ==
        assertEquals(-1f, fx.deathTime, 0f)
        // == AP-23 nebel ==
        // == /AP-23 ==
    }

    // == AP-22 start ==
    // == /AP-22 ==
    /**
     * Ein frischer Zustand ist schon der Ruhezustand: Das Startbild darf
     * ohne vorheriges [FxState.reset] keinen Blitz und keinen Sturz zeigen.
     */
    @Test
    fun `frischer Zustand ist der Ruhezustand`() {
        val fx = FxState()

        assertEquals(0f, fx.flashAlpha, 0f)
        assertEquals(0f, fx.shakeTime, 0f)
        assertEquals(0f, fx.celebrateTime, 0f)
        assertEquals(-1f, fx.deathTime, 0f)
    }

    // == AP-23 nebel ==
    // == /AP-23 ==
}
