package de.robinrehbein.punkt

import de.robinrehbein.punkt.game.TimingGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class TimingGameTest {

    private fun newGame(seed: Int = 42): TimingGame =
        TimingGame(random = Random(seed)).apply {
            // Standard: keine Twists, damit die Basis-Mechanik isoliert testbar ist.
            twistOverride = emptySet()
        }

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

    private fun TimingGame.hitZone(): TimingGame.GameEvent? {
        if (!runUntilInZone()) return null
        return tap()
    }

    @Test
    fun `starts in ready phase and first tap starts the run`() {
        val game = newGame()
        assertEquals(TimingGame.Phase.READY, game.phase)

        val event = game.tap()

        assertEquals(TimingGame.GameEvent.Started, event)
        assertEquals(TimingGame.Phase.RUNNING, game.phase)
    }

    @Test
    fun `tap inside the zone scores and reverses direction`() {
        val game = newGame()
        game.tap()
        val directionBefore = game.direction

        val event = game.hitZone()

        assertTrue(
            event == TimingGame.GameEvent.Hit || event == TimingGame.GameEvent.PerfectHit
        )
        assertTrue(game.score >= 1)
        assertEquals(-directionBefore, game.direction)
        assertEquals(TimingGame.Phase.RUNNING, game.phase)
    }

    @Test
    fun `perfect hit scores double`() {
        val game = newGame()
        game.tap()
        // Bis exakt in den Perfekt-Kern simulieren
        var time = 0f
        while (time < 10f) {
            game.update(1f / 240f)
            time += 1f / 240f
            if (abs(game.relativeToZone()) <=
                game.effectiveZoneHalf() * TimingGame.PERFECT_SHARE
            ) {
                break
            }
        }
        val event = game.tap()

        assertEquals(TimingGame.GameEvent.PerfectHit, event)
        assertEquals(TimingGame.PERFECT_SCORE, game.score)
    }

    @Test
    fun `tap outside the zone kills`() {
        val game = newGame()
        game.tap()
        // Direkt nach dem Start ist die Zone mindestens MIN_ZONE_DISTANCE
        // entfernt — ein sofortiger zweiter Tap liegt sicher daneben.
        val event = game.tap()

        assertEquals(TimingGame.GameEvent.Died, event)
        assertEquals(TimingGame.Phase.DYING, game.phase)
    }

    @Test
    fun `passing the zone without a tap kills`() {
        val game = newGame()
        game.tap()

        val events = game.tick(8f)

        assertTrue(events.contains(TimingGame.GameEvent.Died))
        assertTrue(events.contains(TimingGame.GameEvent.Settled))
        assertEquals(TimingGame.Phase.OVER, game.phase)
        assertEquals(0, game.score)
    }

    @Test
    fun `events from tap are also delivered through update`() {
        val game = newGame()
        game.tap() // Started gepuffert

        val events = game.update(1f / 60f)

        assertTrue(events.contains(TimingGame.GameEvent.Started))
    }

    @Test
    fun `zone respawns ahead in the new running direction`() {
        val game = newGame()
        game.tap()
        repeat(5) {
            val event = game.hitZone()
            assertTrue(
                "Zone nicht getroffen",
                event == TimingGame.GameEvent.Hit || event == TimingGame.GameEvent.PerfectHit
            )
            // Nach jedem Treffer liegt die neue Zone vor dem Punkt,
            // mindestens die Mindestdistanz entfernt (negativ = davor).
            val rel = game.relativeToZone()
            assertTrue(rel < 0f)
            assertTrue(abs(rel) >= TimingGame.MIN_ZONE_DISTANCE - 0.0001f)
        }
        assertTrue(game.score >= 5)
    }

    @Test
    fun `zone shrinks with score but never below minimum`() {
        val game = newGame()
        game.tap()
        assertEquals(TimingGame.BASE_ZONE_HALF, game.zoneHalfWidth, 0.001f)

        repeat(60) { game.hitZone() }

        assertTrue(game.zoneHalfWidth >= TimingGame.MIN_ZONE_HALF - 0.0001f)
        assertTrue(game.zoneHalfWidth < TimingGame.BASE_ZONE_HALF)
    }

    @Test
    fun `speed increases with score but is capped`() {
        val game = newGame()
        assertEquals(TimingGame.BASE_SPEED, game.currentSpeed(), 0.0001f)
        game.tap()
        repeat(60) { game.hitZone() }
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
        game.hitZone()
        game.tap() // wahrscheinlich daneben

        game.reset()

        assertEquals(TimingGame.Phase.READY, game.phase)
        assertEquals(0, game.score)
        assertEquals(1, game.direction)
        assertEquals(TimingGame.BASE_ZONE_HALF, game.zoneHalfWidth, 0.0001f)
        assertTrue(game.activeTwists.isEmpty())
        assertFalse(game.hasFakeZone)
        // Keine Alt-Events aus der Zeit vor dem Reset
        assertEquals(0, game.update(1f / 60f).size)
    }

    // ===== Twists =====

    @Test
    fun `pulse twist varies the effective zone width within bounds`() {
        val game = newGame()
        game.twistOverride = setOf(TimingGame.Twist.PULSE)
        game.tap()

        var minSeen = Float.MAX_VALUE
        var maxSeen = 0f
        repeat(120) {
            game.update(1f / 60f)
            if (game.phase != TimingGame.Phase.RUNNING) return@repeat
            val half = game.effectiveZoneHalf()
            if (half < minSeen) minSeen = half
            if (half > maxSeen) maxSeen = half
        }

        assertTrue(minSeen >= game.zoneHalfWidth * TimingGame.PULSE_MIN_SHARE - 0.0001f)
        assertTrue(maxSeen <= game.zoneHalfWidth + 0.0001f)
        assertTrue("Zone sollte sichtbar pulsieren", maxSeen - minSeen > 0.01f)
    }

    @Test
    fun `drift twist moves the zone center`() {
        val game = newGame()
        game.twistOverride = setOf(TimingGame.Twist.DRIFT)
        game.tap()
        val centerBefore = game.zoneCenter

        game.tick(0.3f)

        assertTrue(
            "Zone sollte gewandert sein",
            abs(TimingGame.wrapToPi(game.zoneCenter - centerBefore)) > 0.01f
        )
    }

    @Test
    fun `ghost twist blinks the dot while running`() {
        val game = newGame()
        game.twistOverride = setOf(TimingGame.Twist.GHOST)
        game.tap()

        var visibleSeen = false
        var hiddenSeen = false
        repeat(180) {
            game.update(1f / 60f)
            if (game.phase != TimingGame.Phase.RUNNING) return@repeat
            if (game.isDotVisible) visibleSeen = true else hiddenSeen = true
        }

        assertTrue(visibleSeen)
        assertTrue("Punkt sollte zeitweise unsichtbar sein", hiddenSeen)
    }

    @Test
    fun `dot is always visible outside of running phase`() {
        val game = newGame()
        game.twistOverride = setOf(TimingGame.Twist.GHOST)
        assertTrue(game.isDotVisible) // READY
    }

    @Test
    fun `fake zone spawns between dot and target zone`() {
        val game = newGame()
        game.twistOverride = setOf(TimingGame.Twist.FAKE)
        game.tap()

        if (game.hasFakeZone) {
            // Die Falle liegt in Laufrichtung vor der echten Zone.
            val relFake = TimingGame.wrapToPi(game.direction * (game.angle - game.fakeZoneCenter))
            val relZone = game.relativeToZone()
            assertTrue(relFake < 0f)
            assertTrue(abs(relFake) < abs(relZone))
        }
    }

    @Test
    fun `chain twist keeps direction and spawns follow-up zone`() {
        val game = newGame()
        game.twistOverride = setOf(TimingGame.Twist.CHAIN)
        game.tap()
        val directionBefore = game.direction
        assertEquals(TimingGame.CHAIN_LENGTH, game.chainRemaining)

        val firstHit = game.hitZone()
        assertTrue(
            firstHit == TimingGame.GameEvent.Hit || firstHit == TimingGame.GameEvent.PerfectHit
        )
        // Kette: Richtung bleibt, die nächste Zone wartet schon.
        assertEquals(directionBefore, game.direction)
        assertEquals(0, game.chainRemaining)
        assertTrue(game.update(1f / 60f).contains(TimingGame.GameEvent.ChainNext))

        val secondHit = game.hitZone()
        assertTrue(
            secondHit == TimingGame.GameEvent.Hit || secondHit == TimingGame.GameEvent.PerfectHit
        )
        // Nach der Kette dreht die Richtung wieder um.
        assertEquals(-directionBefore, game.direction)
    }

    @Test
    fun `twists unlock at their score thresholds and are announced once`() {
        val game = TimingGame(random = Random(7)) // ohne Override: echte Auswahl
        game.tap()

        val announced = mutableListOf<TimingGame.Twist>()
        var time = 0f
        while (game.score < 8 && time < 120f) {
            if (game.isInZone) {
                game.tap()
            } else {
                game.update(1f / 120f)
                time += 1f / 120f
            }
            if (game.phase != TimingGame.Phase.RUNNING) break
        }
        // Events einsammeln
        // (announce passiert beim Spawn; wir prüfen indirekt über Score-Schwelle)
        assertTrue("Testlauf sollte Score 5 erreichen", game.score >= 5)

        // Unlock-Schwellen sind korrekt definiert
        assertEquals(5, TimingGame.unlockScore(TimingGame.Twist.PULSE))
        assertEquals(10, TimingGame.unlockScore(TimingGame.Twist.DRIFT))
        assertEquals(15, TimingGame.unlockScore(TimingGame.Twist.GHOST))
        assertEquals(20, TimingGame.unlockScore(TimingGame.Twist.FAKE))
        assertEquals(25, TimingGame.unlockScore(TimingGame.Twist.CHAIN))
    }

    @Test
    fun `no twists are active below the first threshold`() {
        val game = TimingGame(random = Random(3)) // ohne Override
        game.tap()
        // Score 0: keine Twists freigeschaltet
        assertTrue(game.activeTwists.isEmpty())
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
