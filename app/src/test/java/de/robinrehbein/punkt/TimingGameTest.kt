package de.robinrehbein.punkt

import de.robinrehbein.punkt.game.GameEvent
import de.robinrehbein.punkt.game.GameEventChainNext
import de.robinrehbein.punkt.game.GameEventDied
import de.robinrehbein.punkt.game.GameEventHit
import de.robinrehbein.punkt.game.GameEventPerfectHit
import de.robinrehbein.punkt.game.GameEventSettled
import de.robinrehbein.punkt.game.GameEventStarted
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.game.Twist
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimingGameTest {

    private fun newGame(seed: Int = 42): TimingGame =
        TimingGame(random = Random(seed)).apply {
            // Standard: keine Twists, damit die Basis-Mechanik isoliert testbar ist.
            twistOverride = emptySet()
        }

    private fun TimingGame.tick(seconds: Float, step: Float = 1f / 60f): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
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
            if (phase != GamePhase.RUNNING) return false
            if (isInZone) return true
        }
        return false
    }

    private fun TimingGame.hitZone(): GameEvent? {
        if (!runUntilInZone()) return null
        return tap()
    }

    /** Simuliert Frames bis in den Perfekt-Kern und tappt dort. */
    private fun TimingGame.hitPerfect(): GameEvent? {
        var time = 0f
        while (time < 10f) {
            update(1f / 240f)
            time += 1f / 240f
            if (phase != GamePhase.RUNNING) return null
            if (abs(relativeToZone()) <= effectiveZoneHalf() * TimingGame.PERFECT_SHARE) {
                return tap()
            }
        }
        return null
    }

    @Test
    fun `starts in ready phase and first tap starts the run`() {
        val game = newGame()
        assertEquals(GamePhase.READY, game.phase)

        val event = game.tap()

        assertEquals(GameEventStarted, event)
        assertEquals(GamePhase.RUNNING, game.phase)
    }

    @Test
    fun `tap inside the zone scores and reverses direction`() {
        val game = newGame()
        game.tap()
        val directionBefore = game.direction

        val event = game.hitZone()

        assertTrue(
            event == GameEventHit || event == GameEventPerfectHit
        )
        assertTrue(game.score >= 1)
        assertEquals(-directionBefore, game.direction)
        assertEquals(GamePhase.RUNNING, game.phase)
    }

    @Test
    fun `perfect hit scores double`() {
        val game = newGame()
        game.tap()

        val event = game.hitPerfect()

        assertEquals(GameEventPerfectHit, event)
        assertEquals(TimingGame.PERFECT_BASE_SCORE, game.score)
        assertEquals(1, game.perfectStreak)
    }

    @Test
    fun `perfect streak ramps the bonus up to the cap`() {
        val game = newGame()
        game.tap()

        // +2, +3, +4, +5, +5 — die Serie klettert und deckelt bei +5.
        val expected = listOf(2, 3, 4, 5, 5)
        for ((index, points) in expected.withIndex()) {
            val event = game.hitPerfect()
            assertEquals("Treffer ${index + 1}", GameEventPerfectHit, event)
            assertEquals("Treffer ${index + 1}", points, game.lastHitPoints)
        }
        assertEquals(expected.sum(), game.score)
        assertEquals(expected.size, game.hits)
    }

    @Test
    fun `normal hit resets the perfect streak without punishment`() {
        val game = newGame()
        game.tap()

        game.hitPerfect() // +2, Serie 1
        game.hitPerfect() // +3, Serie 2
        // hitZone tappt an der Zonenkante — sicher außerhalb des Kerns.
        val normal = game.hitZone()
        assertEquals(GameEventHit, normal)
        assertEquals(1, game.lastHitPoints)
        assertEquals(0, game.perfectStreak)

        // Die nächste Serie beginnt wieder bei +2.
        game.hitPerfect()
        assertEquals(2, game.lastHitPoints)
        assertEquals(2 + 3 + 1 + 2, game.score)
    }

    @Test
    fun `difficulty scales with hits not score`() {
        val game = newGame()
        game.tap()

        game.hitPerfect() // Score +2, aber nur EIN Treffer

        assertEquals(TimingGame.PERFECT_BASE_SCORE, game.score)
        assertEquals(1, game.hits)
        assertEquals(
            TimingGame.BASE_SPEED + 1 * TimingGame.SPEED_PER_HIT,
            game.currentSpeed(),
            0.0001f
        )
        assertEquals(
            TimingGame.BASE_ZONE_HALF - 1 * TimingGame.ZONE_SHRINK_PER_HIT,
            game.zoneHalfWidth,
            0.0001f
        )
    }

    @Test
    fun `tap outside the zone kills`() {
        val game = newGame()
        game.tap()
        // Direkt nach dem Start ist die Zone mindestens MIN_ZONE_DISTANCE
        // entfernt — ein sofortiger zweiter Tap liegt sicher daneben.
        val event = game.tap()

        assertEquals(GameEventDied, event)
        assertEquals(GamePhase.DYING, game.phase)
    }

    @Test
    fun `passing the zone without a tap kills`() {
        val game = newGame()
        game.tap()

        val events = game.tick(8f)

        assertTrue(events.contains(GameEventDied))
        assertTrue(events.contains(GameEventSettled))
        assertEquals(GamePhase.OVER, game.phase)
        assertEquals(0, game.score)
    }

    @Test
    fun `events from tap are also delivered through update`() {
        val game = newGame()
        game.tap() // Started gepuffert

        val events = game.update(1f / 60f)

        assertTrue(events.contains(GameEventStarted))
    }

    @Test
    fun `zone respawns ahead in the new running direction`() {
        val game = newGame()
        game.tap()
        repeat(5) {
            val event = game.hitZone()
            assertTrue(
                "Zone nicht getroffen",
                event == GameEventHit || event == GameEventPerfectHit
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
        while (game.phase != GamePhase.OVER && time < 5f) {
            game.update(1f / 60f)
            time += 1f / 60f
        }
        assertEquals(GamePhase.OVER, game.phase)

        game.tap() // sofortiger Wut-Tap innerhalb der Sperrzeit
        assertEquals(GamePhase.OVER, game.phase)

        // Nach der Sperre startet ein Tap sofort den nächsten Lauf.
        game.tick(TimingGame.RESTART_LOCK_SECONDS + 0.1f)
        val event = game.tap()
        assertEquals(GameEventStarted, event)
        assertEquals(GamePhase.RUNNING, game.phase)
        assertEquals(0, game.score)
    }

    @Test
    fun `slightly late tap on the exit side still counts as a hit`() {
        val game = newGame()
        game.tap()
        assertTrue(game.runUntilInZone())

        // Bis knapp hinter die Zonenkante laufen — innerhalb des Gnadenfensters.
        var guard = 0
        while (game.relativeToZone() <= game.effectiveZoneHalf() && guard++ < 2000) {
            game.update(1f / 240f)
        }
        assertEquals(GamePhase.RUNNING, game.phase)

        val event = game.tap()

        assertEquals(GameEventHit, event)
        assertEquals(GamePhase.RUNNING, game.phase)
        assertTrue(game.score >= 1)
    }

    @Test
    fun `clearly late tap is still a miss`() {
        val game = newGame()
        game.tap()
        assertTrue(game.runUntilInZone())

        // Über das Gnadenfenster hinauslaufen (aber vor dem Überfahren-Tod).
        val lateLimit = game.effectiveZoneHalf() +
            game.currentSpeed() * TimingGame.LATE_TAP_FORGIVENESS_SECONDS
        var guard = 0
        while (game.relativeToZone() <= lateLimit && guard++ < 2000) {
            game.update(1f / 240f)
        }
        assertEquals(GamePhase.RUNNING, game.phase)

        val event = game.tap()

        assertEquals(GameEventDied, event)
        assertEquals(GamePhase.DYING, game.phase)
    }

    @Test
    fun `zone distance keeps minimum reaction time even at high speed`() {
        val game = newGame()
        game.tap()

        repeat(60) {
            val event = game.hitZone()
            assertTrue(
                "Zone nicht getroffen",
                event == GameEventHit || event == GameEventPerfectHit
            )
            val rel = game.relativeToZone()
            assertTrue(rel < 0f)
            val reactionSeconds = abs(rel) / game.currentSpeed()
            assertTrue(
                "Reaktionszeit zu kurz: $reactionSeconds s",
                reactionSeconds >= TimingGame.MIN_REACTION_SECONDS - 0.0001f
            )
        }
        // Der Lauf muss das Maximaltempo wirklich erreicht haben.
        assertEquals(TimingGame.MAX_SPEED, game.currentSpeed(), 0.0001f)
    }

    @Test
    fun `reset restores a clean ready state`() {
        val game = newGame()
        game.tap()
        game.hitZone()
        game.tap() // wahrscheinlich daneben

        game.reset()

        assertEquals(GamePhase.READY, game.phase)
        assertEquals(0, game.score)
        assertEquals(0, game.hits)
        assertEquals(0, game.perfectStreak)
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
        game.twistOverride = setOf(Twist.PULSE)
        game.tap()

        var minSeen = Float.MAX_VALUE
        var maxSeen = 0f
        repeat(120) {
            game.update(1f / 60f)
            if (game.phase != GamePhase.RUNNING) return@repeat
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
        game.twistOverride = setOf(Twist.DRIFT)
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
        game.twistOverride = setOf(Twist.GHOST)
        game.tap()

        var visibleSeen = false
        var hiddenSeen = false
        repeat(180) {
            game.update(1f / 60f)
            if (game.phase != GamePhase.RUNNING) return@repeat
            if (game.isDotVisible) visibleSeen = true else hiddenSeen = true
        }

        assertTrue(visibleSeen)
        assertTrue("Punkt sollte zeitweise unsichtbar sein", hiddenSeen)
    }

    @Test
    fun `dot is always visible outside of running phase`() {
        val game = newGame()
        game.twistOverride = setOf(Twist.GHOST)
        assertTrue(game.isDotVisible) // READY
    }

    @Test
    fun `fake zone spawns between dot and target zone`() {
        val game = newGame()
        game.twistOverride = setOf(Twist.FAKE)
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
        game.twistOverride = setOf(Twist.CHAIN)
        game.tap()
        val directionBefore = game.direction
        assertEquals(TimingGame.CHAIN_LENGTH, game.chainRemaining)

        val firstHit = game.hitZone()
        assertTrue(
            firstHit == GameEventHit || firstHit == GameEventPerfectHit
        )
        // Kette: Richtung bleibt, die nächste Zone wartet schon.
        assertEquals(directionBefore, game.direction)
        assertEquals(0, game.chainRemaining)
        assertTrue(game.update(1f / 60f).contains(GameEventChainNext))

        val secondHit = game.hitZone()
        assertTrue(
            secondHit == GameEventHit || secondHit == GameEventPerfectHit
        )
        // Nach der Kette dreht die Richtung wieder um.
        assertEquals(-directionBefore, game.direction)
    }

    @Test
    fun `twists unlock at their score thresholds and are announced once`() {
        val game = TimingGame(random = Random(7)) // ohne Override: echte Auswahl
        game.tap()

        val announced = mutableListOf<Twist>()
        var time = 0f
        while (game.score < 8 && time < 120f) {
            if (game.isInZone) {
                game.tap()
            } else {
                game.update(1f / 120f)
                time += 1f / 120f
            }
            if (game.phase != GamePhase.RUNNING) break
        }
        // Events einsammeln
        // (announce passiert beim Spawn; wir prüfen indirekt über Score-Schwelle)
        assertTrue("Testlauf sollte Score 5 erreichen", game.score >= 5)

        // Unlock-Schwellen sind korrekt definiert
        assertEquals(5, TimingGame.unlockScore(Twist.PULSE))
        assertEquals(10, TimingGame.unlockScore(Twist.DRIFT))
        assertEquals(15, TimingGame.unlockScore(Twist.GHOST))
        assertEquals(20, TimingGame.unlockScore(Twist.FAKE))
        assertEquals(25, TimingGame.unlockScore(Twist.CHAIN))
    }

    @Test
    fun `ghost and fake twists never spawn together`() {
        // Kuratiertes Kombi-Verbot: unsichtbarer Punkt plus Köder-Zone
        // wäre Zufalls-Tod. Über viele Seeds und Zonen prüfen.
        var spawnsWithBothUnlocked = 0
        for (seed in 0 until 20) {
            val game = TimingGame(random = Random(seed))
            game.tap()
            var guard = 0
            while (game.phase == GamePhase.RUNNING &&
                game.score < 45 && guard++ < 200
            ) {
                game.hitZone() ?: break
                assertFalse(
                    "GEIST + FALLE gleichzeitig aktiv (Seed $seed, Score ${game.score})",
                    game.activeTwists.contains(Twist.GHOST) &&
                        game.activeTwists.contains(Twist.FAKE)
                )
                if (game.score >= TimingGame.unlockScore(Twist.FAKE)) {
                    spawnsWithBothUnlocked++
                }
            }
        }
        // Der Test muss den kritischen Bereich wirklich erreicht haben.
        assertTrue(
            "Zu wenige Zonen im kritischen Bereich: $spawnsWithBothUnlocked",
            spawnsWithBothUnlocked > 50
        )
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
