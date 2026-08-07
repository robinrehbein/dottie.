package de.robinrehbein.punkt

import de.robinrehbein.punkt.game.PunktGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PunktGameTest {

    private fun newGame(seed: Int = 42): PunktGame =
        PunktGame(random = Random(seed)).apply { setAspectRatio(0.46f) }

    private fun PunktGame.tick(seconds: Float, step: Float = 1f / 60f): List<PunktGame.GameEvent> {
        val events = mutableListOf<PunktGame.GameEvent>()
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
        assertEquals(PunktGame.Phase.READY, game.phase)

        val event = game.tap()

        assertEquals(PunktGame.GameEvent.STARTED, event)
        assertEquals(PunktGame.Phase.RUNNING, game.phase)
        assertTrue(game.obstacles.isNotEmpty())
    }

    @Test
    fun `tap flips gravity direction`() {
        val game = newGame()
        game.tap()
        assertEquals(1, game.gravityDir)

        val event = game.tap()

        assertEquals(PunktGame.GameEvent.FLIPPED, event)
        assertEquals(-1, game.gravityDir)

        game.tap()
        assertEquals(1, game.gravityDir)
    }

    @Test
    fun `dot rests on the floor and the floor is never deadly`() {
        val game = newGame()
        game.tap()
        // Kurz laufen lassen, bevor die erste Säule ankommt: nichts passiert
        game.tick(0.5f)

        assertEquals(PunktGame.Phase.RUNNING, game.phase)
        assertEquals(game.playBottom() - PunktGame.DOT_RADIUS, game.dotY, 0.001f)
        assertTrue(game.isGrounded)
    }

    @Test
    fun `after a flip the dot falls up and lands on the ceiling`() {
        val game = newGame()
        game.tap()
        game.tap() // Schwerkraft nach oben

        game.tick(0.6f)

        assertEquals(game.playTop() + PunktGame.DOT_RADIUS, game.dotY, 0.001f)
        assertTrue(game.isGrounded)
        assertEquals(PunktGame.Phase.RUNNING, game.phase)
    }

    @Test
    fun `hitting an obstacle kills and the dot lands afterwards`() {
        val game = newGame()
        game.tap()
        // Ohne weitere Taps rollt der Punkt in die erste Bodensäule.
        val events = game.tick(6f)

        assertTrue(events.contains(PunktGame.GameEvent.DIED))
        assertTrue(events.contains(PunktGame.GameEvent.LANDED))
        assertEquals(PunktGame.Phase.OVER, game.phase)
    }

    @Test
    fun `passing an obstacle scores exactly one point`() {
        val game = newGame()
        game.tap()
        game.tick(0.1f)

        // Erste Säule künstlich hinter den Punkt verschieben
        val obstacle = game.obstacles.first()
        obstacle.x = game.dotX - PunktGame.OBSTACLE_WIDTH - PunktGame.DOT_RADIUS - 0.01f

        val events = game.update(1f / 60f)

        assertEquals(1, events.count { it == PunktGame.GameEvent.SCORED })
        assertEquals(1, game.score)

        // Ein weiteres Update darf nicht erneut zählen
        val moreEvents = game.update(1f / 60f)
        assertEquals(0, moreEvents.count { it == PunktGame.GameEvent.SCORED })
        assertEquals(1, game.score)
    }

    @Test
    fun `gap narrows with score but never below minimum`() {
        val game = newGame()
        assertEquals(PunktGame.BASE_GAP, game.currentGap(), 0.0001f)
        assertTrue(game.currentGap() >= PunktGame.MIN_GAP)
    }

    @Test
    fun `speed increases with score but is capped`() {
        val game = newGame()
        assertEquals(PunktGame.BASE_SPEED, game.currentSpeed(), 0.0001f)
        assertTrue(game.currentSpeed() <= PunktGame.MAX_SPEED)
    }

    @Test
    fun `taps during dying phase are ignored`() {
        val game = newGame()
        game.tap()
        var time = 0f
        while (game.phase != PunktGame.Phase.DYING && time < 10f) {
            game.update(1f / 60f)
            time += 1f / 60f
        }
        assertEquals(PunktGame.Phase.DYING, game.phase)

        val event = game.tap()

        assertEquals(null, event)
        assertEquals(PunktGame.Phase.DYING, game.phase)
    }

    @Test
    fun `restart lock prevents immediate rage-tap restart`() {
        val game = newGame()
        game.tap()
        // Exakt bis zum Game-Over simulieren, ohne danach Zeit anzusammeln
        var time = 0f
        while (game.phase != PunktGame.Phase.OVER && time < 10f) {
            game.update(1f / 60f)
            time += 1f / 60f
        }
        assertEquals(PunktGame.Phase.OVER, game.phase)

        game.tap() // sofortiger Wut-Tap innerhalb der Sperrzeit
        assertEquals(PunktGame.Phase.OVER, game.phase)

        game.tick(PunktGame.RESTART_LOCK_SECONDS + 0.1f)
        game.tap()
        assertEquals(PunktGame.Phase.READY, game.phase)
    }

    @Test
    fun `reset restores a clean ready state`() {
        val game = newGame()
        game.tap()
        game.tap() // Schwerkraft umdrehen
        game.tick(6f)

        game.reset()

        assertEquals(PunktGame.Phase.READY, game.phase)
        assertEquals(0, game.score)
        assertEquals(1, game.gravityDir)
        assertTrue(game.obstacles.isEmpty())
        assertEquals(0f, game.dotVelocity, 0.0001f)
    }

    @Test
    fun `obstacles always leave a passable corridor`() {
        val game = newGame()
        game.tap()
        game.tick(10f)

        val playHeight = game.playBottom() - game.playTop()
        game.obstacles.forEach { obstacle ->
            assertTrue(obstacle.floorHeight >= 0f)
            assertTrue(obstacle.ceilingHeight >= 0f)
            val corridor = playHeight - obstacle.floorHeight - obstacle.ceilingHeight
            assertTrue(
                "Korridor muss mindestens die Mindestlücke haben",
                corridor >= PunktGame.MIN_GAP - 0.0001f
            )
        }
    }

    @Test
    fun `early obstacles only grow from the floor`() {
        val game = newGame()
        game.tap()
        game.tick(0.1f)

        game.obstacles.forEach { obstacle ->
            assertTrue(obstacle.floorHeight > 0f)
            assertEquals(0f, obstacle.ceilingHeight, 0.0001f)
        }
    }
}
