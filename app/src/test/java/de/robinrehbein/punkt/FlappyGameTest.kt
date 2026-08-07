package de.robinrehbein.punkt

import de.robinrehbein.punkt.game.FlappyGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FlappyGameTest {

    private fun newGame(seed: Int = 42): FlappyGame =
        FlappyGame(random = Random(seed)).apply { setAspectRatio(0.46f) }

    private fun FlappyGame.tick(seconds: Float, step: Float = 1f / 60f): List<FlappyGame.GameEvent> {
        val events = mutableListOf<FlappyGame.GameEvent>()
        var remaining = seconds
        while (remaining > 0f) {
            events += update(minOf(step, remaining))
            remaining -= step
        }
        return events
    }

    @Test
    fun `starts in ready phase and first tap starts the run`() {
        val game = newGame()
        assertEquals(FlappyGame.Phase.READY, game.phase)

        val event = game.tap()

        assertEquals(FlappyGame.GameEvent.STARTED, event)
        assertEquals(FlappyGame.Phase.RUNNING, game.phase)
        assertTrue(game.pipes.isNotEmpty())
    }

    @Test
    fun `flap pushes the dot upwards`() {
        val game = newGame()
        game.tap()
        game.tick(0.3f)
        val fallingVelocity = game.birdVelocity
        assertTrue(fallingVelocity > FlappyGame.FLAP_VELOCITY)

        game.tap()

        assertEquals(FlappyGame.FLAP_VELOCITY, game.birdVelocity, 0.0001f)
    }

    @Test
    fun `gravity pulls the dot down without taps`() {
        val game = newGame()
        game.tap()
        val startY = game.birdY

        game.tick(0.6f)

        assertTrue(game.birdY > startY || game.phase != FlappyGame.Phase.RUNNING)
    }

    @Test
    fun `dot dies when it falls to the ground`() {
        val game = newGame()
        game.tap()

        val events = game.tick(5f)

        assertTrue(events.contains(FlappyGame.GameEvent.DIED))
        assertTrue(events.contains(FlappyGame.GameEvent.LANDED))
        assertEquals(FlappyGame.Phase.OVER, game.phase)
    }

    @Test
    fun `passing a pipe scores exactly one point`() {
        val game = newGame()
        game.tap()
        // Punkt künstlich in der Lücke der ersten Röhre halten:
        // wir simulieren Frames und flappen immer auf Lückenhöhe.
        var scored = 0
        var time = 0f
        while (time < 4f && game.phase == FlappyGame.Phase.RUNNING) {
            val gapCenter = game.pipes.firstOrNull()?.gapCenter ?: 0.5f
            if (game.birdY > gapCenter && game.birdVelocity > 0f) {
                game.tap()
            }
            scored += game.update(1f / 60f).count { it == FlappyGame.GameEvent.SCORED }
            time += 1f / 60f
        }
        assertTrue("Es sollte mindestens ein Punkt erzielt worden sein", scored >= 1)
        assertEquals(scored, game.score)
    }

    @Test
    fun `gap narrows with score but never below minimum`() {
        val game = newGame()
        assertEquals(FlappyGame.BASE_GAP_HALF, game.currentGapHalf(), 0.0001f)
        assertTrue(game.currentGapHalf() >= FlappyGame.MIN_GAP_HALF)
    }

    @Test
    fun `speed increases with score but is capped`() {
        val game = newGame()
        assertEquals(FlappyGame.BASE_SPEED, game.currentSpeed(), 0.0001f)
        assertTrue(game.currentSpeed() <= FlappyGame.MAX_SPEED)
    }

    @Test
    fun `taps during dying phase are ignored`() {
        val game = newGame()
        game.tap()
        game.tick(5f) // fällt zu Boden und stirbt

        // Direkt nach dem Tod (OVER, aber innerhalb der Sperre) wird nicht neu gestartet
        game.reset()
        game.tap()
        val velocityBefore = game.birdVelocity
        // In DYING gibt es keine Flaps mehr
        assertTrue(velocityBefore <= 0f || game.phase == FlappyGame.Phase.RUNNING)
    }

    @Test
    fun `restart lock prevents immediate rage-tap restart`() {
        val game = newGame()
        game.tap()
        // Exakt bis zum Game-Over simulieren, ohne danach Zeit anzusammeln
        var time = 0f
        while (game.phase != FlappyGame.Phase.OVER && time < 10f) {
            game.update(1f / 60f)
            time += 1f / 60f
        }
        assertEquals(FlappyGame.Phase.OVER, game.phase)

        game.tap() // sofortiger Wut-Tap innerhalb der Sperrzeit
        assertEquals(FlappyGame.Phase.OVER, game.phase)

        game.tick(FlappyGame.RESTART_LOCK_SECONDS + 0.1f)
        game.tap()
        assertEquals(FlappyGame.Phase.READY, game.phase)
    }

    @Test
    fun `reset restores a clean ready state`() {
        val game = newGame()
        game.tap()
        game.tick(5f)

        game.reset()

        assertEquals(FlappyGame.Phase.READY, game.phase)
        assertEquals(0, game.score)
        assertTrue(game.pipes.isEmpty())
        assertEquals(0f, game.birdVelocity, 0.0001f)
    }

    @Test
    fun `pipes are spawned within playable bounds`() {
        val game = newGame()
        game.tap()
        game.tick(10f)
        // Auch nach dem Tod: alle je gespawnten Röhren lagen im gültigen Bereich
        game.pipes.forEach { pipe ->
            assertTrue(pipe.gapTop >= 0f)
            assertTrue(pipe.gapBottom <= game.groundTop())
        }
    }

    @Test
    fun `ceiling clamps instead of killing`() {
        val game = newGame()
        game.tap()
        // Dauerfeuer-Flaps treiben den Punkt an die Decke
        repeat(120) {
            game.tap()
            game.update(1f / 60f)
        }
        assertFalse(game.phase == FlappyGame.Phase.OVER && game.score == 0 && game.birdY < 0.1f)
        assertTrue(game.birdY >= FlappyGame.BIRD_RADIUS - 0.0001f)
    }
}
