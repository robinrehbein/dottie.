package de.robinrehbein.punkt

import de.robinrehbein.punkt.ui.world.FxState
import androidx.compose.ui.geometry.Offset
import de.robinrehbein.punkt.ui.world.addTapEcho
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        fx.notYetTime = 0.5f
        fx.handPressed = true
        fx.handEchoTime = 0.2f
        fx.addTapEcho(Offset(10f, 10f))
        fx.trainingWheels = true
        // == /AP-22 ==
        fx.deathTime = 0.2f
        // == AP-23 nebel ==
        fx.fogInTime = 0.1f
        fx.fogOutTime = 0.2f
        fx.fogWasIn = 1
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
        assertEquals(0f, fx.notYetTime, 0f)
        assertFalse(fx.handPressed)
        assertTrue(fx.handEchoTime < 0f)
        assertTrue(fx.tapEchoes.isEmpty())
        // Die Stützräder sind eine Einstellung, kein Effekt: Sie bleiben.
        assertTrue(fx.trainingWheels)
        // == /AP-22 ==
        assertEquals(-1f, fx.deathTime, 0f)
        // == AP-23 nebel ==
        assertTrue("Eintritts-Wölkchen bleibt", fx.fogInTime < 0f)
        assertTrue("Austritts-Wölkchen bleibt", fx.fogOutTime < 0f)
        assertEquals(-1, fx.fogWasIn)
        // == /AP-23 ==
    }

    // == AP-22 start ==
    /**
     * Der Startbildschirm beginnt ohne NOCH NICHT, ohne Echos und mit
     * losgelassener Hand; die Stützräder schaltet erst GameScreen ein.
     */
    @Test
    fun `frischer Zustand hat keine Start-Effekte`() {
        val fx = FxState()

        assertEquals(0f, fx.notYetTime, 0f)
        assertFalse(fx.handPressed)
        assertTrue(fx.handEchoTime < 0f)
        assertTrue(fx.tapEchoes.isEmpty())
        assertFalse(fx.trainingWheels)
    }
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
    /**
     * Ein frischer Zustand zeigt keine Nebel-Wölkchen, und der erste Frame
     * eines Laufs darf keinen Wechsel in den Nebel melden (unbekannt).
     */
    @Test
    fun `frischer Zustand ohne Nebel-Woelkchen`() {
        val fx = FxState()

        assertTrue(fx.fogInTime < 0f)
        assertTrue(fx.fogOutTime < 0f)
        assertEquals(-1, fx.fogWasIn)
    }
    // == /AP-23 ==
}
