package de.robinrehbein.punkt

import de.robinrehbein.punkt.ui.screens.FxState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Effekt-Zustand überlebt den Lauf: Wer nach dem Tod ins Menü geht,
 * darf keine laufende Sturz-Animation mitnehmen — sonst wäre der Vogel im
 * Startbild schon aus dem Kader gefallen und damit unsichtbar.
 */
class FxStateTest {

    @Test
    fun `reset beendet die Sturz-Animation`() {
        val fx = FxState()
        fx.deathTime = 1.4f

        fx.reset()

        assertTrue("Sturz muss als beendet gelten", fx.deathTime < 0f)
    }

    @Test
    fun `reset raeumt alle Effekte ab`() {
        val fx = FxState()
        fx.flashAlpha = 1f
        fx.shakeTime = 0.4f
        fx.celebrateTime = 1.1f
        fx.deathTime = 0.2f

        fx.reset()

        assertEquals(0f, fx.flashAlpha, 0f)
        assertEquals(0f, fx.shakeTime, 0f)
        assertEquals(0f, fx.celebrateTime, 0f)
        assertEquals(-1f, fx.deathTime, 0f)
    }
}
