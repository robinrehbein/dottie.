package de.robinrehbein.punkt.wear

import de.robinrehbein.punkt.game.GameEventHit
import de.robinrehbein.punkt.game.GameEventNotYet
import de.robinrehbein.punkt.game.GameEventPerfectHit
import de.robinrehbein.punkt.game.GameEventStarted
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.TimingGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Die Startregel auf der Uhr (Plan 3.1, 8.5 AP-24): Erster Tap im Grün ist
 * Treffer 1, ein Tap daneben kostet nichts und antwortet mit „NOCH NICHT“.
 *
 * Geprüft wird der Weg, den WearGameController.tap() für Touch, Taste und
 * Drehring gleichermaßen nimmt: erst [WearNotYet.startsRun] (Seed nur,
 * wenn der Tap startet), dann `game.tap()`, die Auswertung im nächsten
 * `update()`.
 */
class WearStartRuleTest {

    private val frame = 1f / 120f

    /** Lässt den Punkt in READY kreisen, bis [until] gilt. */
    private fun TimingGame.runUntil(until: TimingGame.() -> Boolean) {
        repeat(20_000) {
            if (until()) return
            update(frame)
        }
        throw AssertionError("Zustand nicht erreicht")
    }

    @Test
    fun `Tap daneben in READY laesst Phase Score und Zone stehen`() {
        val game = TimingGame()
        game.reseed(7L)
        game.runUntil { !isInZone && abs(relativeToZone()) > effectiveZoneHalf() * 2f }
        val zone = game.zoneCenter

        assertFalse("Ein Tap daneben startet keinen Lauf", WearNotYet.startsRun(game))
        assertEquals(GameEventNotYet, game.tap())
        val events = game.update(frame)

        assertEquals(listOf(GameEventNotYet), events)
        assertEquals(GamePhase.READY, game.phase)
        assertEquals(0, game.score)
        assertEquals(zone, game.zoneCenter, 0f)
    }

    @Test
    fun `Erster Tap im Gruen ist Treffer 1`() {
        val game = TimingGame()
        game.reseed(7L)
        game.runUntil { isInZone }

        assertTrue("Ein Tap im Grün startet den Lauf", WearNotYet.startsRun(game))
        val hit = game.tap()
        val events = game.update(frame)

        assertEquals(GamePhase.RUNNING, game.phase)
        assertTrue(hit == GameEventHit || hit == GameEventPerfectHit)
        // Started vor dem Treffer, sonst setzte der Controller die frische
        // Perfekt-Serie gleich wieder zurück.
        assertEquals(listOf(GameEventStarted, hit), events)
        assertTrue("Score 1 oder 2, war ${game.score}", game.score in 1..2)
    }

    @Test
    fun `Nach einem NOCH NICHT startet der naechste Tap im Gruen normal`() {
        val game = TimingGame()
        game.reseed(3L)
        game.runUntil { abs(relativeToZone()) > effectiveZoneHalf() * 2f }
        game.tap()
        game.update(frame)
        game.runUntil { isInZone }
        game.tap()
        assertEquals(GamePhase.RUNNING, game.phase)
        assertTrue(game.score >= 1)
    }

    @Test
    fun `Daily-Seed vor dem Start-Tap ergibt dieselbe Folge`() {
        fun zonesAfterStart(): List<Float> {
            val game = TimingGame()
            game.runUntil { isInZone }
            // Wie prepareRun: Seed erst, wenn der Tap wirklich startet.
            assertTrue(WearNotYet.startsRun(game))
            game.reseed(20_260_925L)
            game.tap()
            return listOf(game.zoneCenter, game.zoneHalfWidth)
        }
        assertEquals(zonesAfterStart(), zonesAfterStart())
    }

    @Test
    fun `In OVER startet ein Tap wie bisher`() {
        val game = TimingGame()
        game.reseed(1L)
        game.runUntil { isInZone }
        game.tap()
        // Nicht mehr tippen: Der Punkt überfährt die Zone und stirbt.
        game.runUntil { phase == GamePhase.OVER }
        assertTrue(WearNotYet.startsRun(game))
        assertFalse(WearNotYet.startsRun(TimingGame().apply { reseed(1L); start() }))
    }

    @Test
    fun `Wackeln klingt aus und bleibt klein`() {
        assertEquals(0f, WearNotYet.wobble(0f), 0f)
        var peak = 0f
        var t = WearNotYet.SECONDS
        while (t > 0f) {
            val w = WearNotYet.wobble(t)
            assertTrue(abs(w) <= WearNotYet.WOBBLE_PER_SECOND * WearNotYet.SECONDS)
            peak = maxOf(peak, abs(w))
            t = WearNotYet.after(t, frame)
        }
        assertTrue("Der Vogel wackelt sichtbar", peak > 1f)
        assertEquals(0f, WearNotYet.after(0.01f, 0.5f), 0f)
    }
}
