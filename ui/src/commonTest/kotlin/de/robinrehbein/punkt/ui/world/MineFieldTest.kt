package de.robinrehbein.punkt.ui.world

import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.game.TrapPaint
import de.robinrehbein.punkt.game.Twist
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Das Lauflicht der Bomben ([trapRedSegments]): rot wird nur, was auch
 * als Mine gezeichnet wird, und die Zahl der roten Minen folgt der Maske
 * aus [TrapPaint] — ohne dass das Bild den Engine-Zufall berührt.
 */
class MineFieldTest {

    private val segments = 60

    /** Ein laufendes Spiel, dessen erste Zone eine Falle hat. */
    private fun gameWithTrap(): TimingGame {
        for (seed in 1L..200L) {
            val game = TimingGame(Random(seed)).apply { twistOverride = setOf(Twist.FAKE) }
            game.start()
            if (game.hasFakeZone) return game
        }
        fail("Kein Seed mit Falle gefunden")
    }

    private fun mineSegments(game: TimingGame): List<Int> = (0 until segments).filter { k ->
        val a = k.toFloat() / segments * (2f * PI.toFloat())
        abs(TimingGame.wrapToPi(a - game.fakeZoneCenter)) <= game.fakeZoneHalf() &&
            abs(TimingGame.wrapToPi(a - game.zoneCenter)) > game.effectiveZoneHalf()
    }

    @Test
    fun `rot sind nur Minen und die Zahl folgt der Maske`() {
        val game = gameWithTrap()
        val mines = mineSegments(game)
        val count = TrapPaint.count(game.zoneHalfWidth, 2f * PI.toFloat() / segments)
        assertTrue(mines.isNotEmpty(), "die Falle liegt auf der Bahn")

        var steps = 0
        while (game.phase == GamePhase.RUNNING && steps < count) {
            val red = trapRedSegments(game, segments, game.effectiveZoneHalf())
            val redIdx = red.indices.filter { red[it] }
            assertTrue(redIdx.all { it in mines }, "rot außerhalb der Falle: $redIdx")

            val step = floor(game.zoneAge / TrapPaint.LIGHT_STEP_SECONDS).toInt()
            val maskRed = TrapPaint.redMask(count, step).count { it }
            if (mines.size == count) {
                assertEquals(maskRed, redIdx.size, "Schritt $step: jede Mine hat ihren Eintrag")
            } else {
                assertTrue(redIdx.size <= maskRed + 1, "Schritt $step: höchstens eine Mine zu viel rot")
            }
            // Einen Schritt weiter, ohne zu tippen.
            val target = step + 1
            while (game.phase == GamePhase.RUNNING &&
                floor(game.zoneAge / TrapPaint.LIGHT_STEP_SECONDS).toInt() < target
            ) {
                game.update(1f / 240f)
            }
            steps++
        }
        assertTrue(steps > 0, "mindestens ein Schritt geprüft")
    }

    @Test
    fun `ohne Falle ist nichts rot`() {
        val game = TimingGame(Random(1L)).apply { twistOverride = emptySet() }
        game.start()
        assertTrue(trapRedSegments(game, segments, game.effectiveZoneHalf()).none { it })
    }

    @Test
    fun `das Lauflicht zieht keine Zufallszahl`() {
        // Zwei gleiche Spiele: eins wird zusätzlich gezeichnet, danach
        // müssen beide denselben Verlauf haben.
        val a = gameWithTrap()
        val b = gameWithTrap()
        repeat(600) {
            trapRedSegments(a, segments, a.effectiveZoneHalf())
            a.update(1f / 120f)
            b.update(1f / 120f)
            if (a.isInZone) a.tap()
            if (b.isInZone) b.tap()
        }
        assertEquals(b.zoneCenter, a.zoneCenter, 0f)
        assertEquals(b.score, a.score)
    }
}
