package de.robinrehbein.punkt

import de.robinrehbein.punkt.game.TimingGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class TimingGameTest {

    private fun newGame(seed: Int = 42): TimingGame = TimingGame(random = Random(seed))

    private fun TimingGame.tick(seconds: Float, step: Float = 1f / 60f): List<TimingGame.GameEvent> {
        val events = mutableListOf<TimingGame.GameEvent>()
        var remaining = seconds
        while (remaining > 0f) {
            events += update(minOf(step, remaining))
            remaining -= step
        }
        return events
    }

    /** Simuliert Frames, bis der Punkt in der Zone steht. */
    private fun TimingGame.runUntilInZone(maxSeconds: Float = 5f): Boolean {
        var time = 0f
        while (time < maxSeconds) {
            update(1f / 120f)
            time += 1f / 120f
            if (phase != TimingGame.Phase.RUNNING) return false
            if (isInZone) return true
        }
        return false
    }

    @Test
    fun `starts in ready phase and first tap starts the run`() {
        val game = newGame()
        assertEquals(TimingGame.Phase.READY, game.phase)

        val event = game.tap()

        assertEquals(TimingGame.GameEvent.STARTED, event)
        assertEquals(TimingGame.Phase.RUNNING, game.phase)
    }

    @Test
    fun `tap inside the zone scores and reverses direction`() {
        val game = newGame()
        game.tap()
        val directionBefore = game.direction
        assertTrue(game.runUntilInZone())

        val event = game.tap()

        assertTrue(event == TimingGame.GameEvent.HIT || event == TimingGame.GameEvent.PERFECT_HIT)
        assertEquals(1, game.score)
        assertEquals(-directionBefore, game.direction)
        assertEquals(TimingGame.Phase.RUNNING, game.phase)
    }

    @Test
    fun `tap outside the zone kills`() {
        val game = newGame()
        game.tap()
        // Direkt nach dem Start ist die Zone mindestens MIN_ZONE_DISTANCE
        // entfernt — ein sofortiger zweiter Tap liegt sicher daneben.
        val event = game.tap()

        assertEquals(TimingGame.GameEvent.DIED, event)
        assertEquals(TimingGame.Phase.DYING, game.phase)
    }

    @Test
    fun `passing the zone without a tap kills`() {
        val game = newGame()
        game.tap()

        val events = game.tick(8f)

        assertTrue(events.contains(TimingGame.GameEvent.DIED))
        assertTrue(events.contains(TimingGame.GameEvent.SETTLED))
        assertEquals(TimingGame.Phase.OVER, game.phase)
        assertEquals(0, game.score)
    }

    @Test
    fun `events from tap are also delivered through update`() {
        val game = newGame()
        game.tap() // STARTED gepuffert

        val events = game.update(1f / 60f)

        assertTrue(events.contains(TimingGame.GameEvent.STARTED))
    }

    @Test
    fun `zone respawns ahead in the new running direction`() {
        val game = newGame()
        game.tap()
        repeat(5) {
            assertTrue("Zone nicht erreicht", game.runUntilInZone())
            val event = game.tap()
            assertTrue(
                event == TimingGame.GameEvent.HIT || event == TimingGame.GameEvent.PERFECT_HIT
            )
            // Nach jedem Treffer liegt die neue Zone vor dem Punkt,
            // mindestens die Mindestdistanz entfernt (negativ = davor).
            val rel = game.relativeToZone()
            assertTrue(rel < 0f)
            assertTrue(abs(rel) >= TimingGame.MIN_ZONE_DISTANCE - 0.0001f)
        }
        assertEquals(5, game.score)
    }

    @Test
    fun `zone shrinks with score but never below minimum`() {
        val game = newGame()
        game.tap()
        assertEquals(TimingGame.BASE_ZONE_HALF, game.zoneHalfWidth, 0.001f)

        repeat(60) {
            if (game.runUntilInZone()) game.tap()
        }

        assertTrue(game.zoneHalfWidth >= TimingGame.MIN_ZONE_HALF - 0.0001f)
        assertTrue(game.zoneHalfWidth < TimingGame.BASE_ZONE_HALF)
    }

    @Test
    fun `speed increases with score but is capped`() {
        val game = newGame()
        assertEquals(TimingGame.BASE_SPEED, game.currentSpeed(), 0.0001f)
        game.tap()
        repeat(60) {
            if (game.runUntilInZone()) game.tap()
        }
        assertTrue(game.currentSpeed() <= TimingGame.MAX_SPEED)
        assertTrue(game.currentSpeed() > TimingGame.BASE_SPEED)
    }

    @Test
    fun `restart lock prevents immediate rage-tap restart`() {
        val game = newGame()
        game.tap()
        game.tap() // daneben → DYING
        var time = 0f
        while (game.phase != TimingGame.Phase.OVER && time < 5f) {
            game.update(1f / 60f)
            time += 1f / 60f
        }
        assertEquals(TimingGame.Phase.OVER, game.phase)

        game.tap() // sofortiger Wut-Tap innerhalb der Sperrzeit
        assertEquals(TimingGame.Phase.OVER, game.phase)

        game.tick(TimingGame.RESTART_LOCK_SECONDS + 0.1f)
        game.tap()
        assertEquals(TimingGame.Phase.READY, game.phase)
    }

    @Test
    fun `reset restores a clean ready state`() {
        val game = newGame()
        game.tap()
        if (game.runUntilInZone()) game.tap()
        game.tap() // wahrscheinlich daneben

        game.reset()

        assertEquals(TimingGame.Phase.READY, game.phase)
        assertEquals(0, game.score)
        assertEquals(1, game.direction)
        assertEquals(TimingGame.BASE_ZONE_HALF, game.zoneHalfWidth, 0.0001f)
        // Keine Alt-Events aus der Zeit vor dem Reset
        assertEquals(0, game.update(1f / 60f).size)
    }

    @Test
    fun `wrap helpers normalize angles correctly`() {
        assertEquals(0f, TimingGame.wrapToPi(0f), 0.0001f)
        assertEquals(-1f, TimingGame.wrapToPi((2 * Math.PI).toFloat() - 1f), 0.0001f)
        assertEquals(1f, TimingGame.wrapToPi((-2 * Math.PI).toFloat() + 1f), 0.0001f)
        assertTrue(TimingGame.wrapTwoPi(-0.5f) > 0f)
        assertTrue(TimingGame.wrapTwoPi(7f) < (2 * Math.PI).toFloat())
    }
}
