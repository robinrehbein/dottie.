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
 * Die Minen der Falle und ihr Lauflicht ([trapMines]): Die Zahl kommt aus
 * [TrapPaint.count] mit der Grundbreite und bleibt unter PULS stehen, jede
 * Mine hat ihren eigenen Maskeneintrag, und das Bild berührt den
 * Engine-Zufall nicht.
 */
class MineFieldTest {

    private val segments = 60
    private val cell = 2f * PI.toFloat() / segments

    /** Ein laufendes Spiel, dessen erste Zone eine Falle hat. */
    private fun gameWithTrap(twists: Set<Twist> = setOf(Twist.FAKE), from: Long = 1L): TimingGame {
        for (seed in from..from + 200L) {
            val game = TimingGame(Random(seed)).apply { twistOverride = twists }
            game.start()
            if (game.hasFakeZone) return game
        }
        fail("Kein Seed mit Falle gefunden")
    }

    /**
     * Spielt [seeds] Spiele mit [twists] je [frames] Frames ohne Tippen
     * durch und ruft [check] für jeden Frame mit Falle.
     */
    private fun eachTrapFrame(
        twists: Set<Twist>,
        seeds: LongRange = 1L..60L,
        frames: Int = 400,
        check: (TimingGame, List<TrapMine>) -> Unit
    ): Int {
        var checked = 0
        for (seed in seeds) {
            val game = TimingGame(Random(seed)).apply { twistOverride = twists }
            game.start()
            repeat(frames) {
                if (game.phase != GamePhase.RUNNING) return@repeat
                if (game.hasFakeZone) {
                    check(game, trapMines(game, segments, game.effectiveZoneHalf()))
                    checked++
                }
                game.update(1f / 120f)
            }
        }
        return checked
    }

    /** Berührt die Falle in diesem Frame die Zone? Dann dürfen Minen fehlen. */
    private fun touchesZone(game: TimingGame): Boolean =
        abs(TimingGame.wrapToPi(game.fakeZoneCenter - game.zoneCenter)) <=
            game.fakeZoneHalf() + game.effectiveZoneHalf()

    @Test
    fun `die Zahl der Minen kommt aus TrapPaint count`() {
        val count = TrapPaint.count(gameWithTrap().zoneHalfWidth, cell)
        val checked = eachTrapFrame(setOf(Twist.FAKE)) { game, mines ->
            if (touchesZone(game)) return@eachTrapFrame
            assertEquals(TrapPaint.count(game.zoneHalfWidth, cell), mines.size)
            assertEquals((0 until mines.size).toList(), mines.map { it.index })
        }
        assertTrue(checked > 1000, "genug Frames geprüft: $checked")
        assertEquals(7, count, "Grundbreite ergibt sieben Minen (AP-02)")
    }

    @Test
    fun `unter PULS schwankt die Zahl der Minen nicht`() {
        var pulsed = false
        val checked = eachTrapFrame(setOf(Twist.FAKE, Twist.PULSE)) { game, mines ->
            if (touchesZone(game)) return@eachTrapFrame
            if (game.fakeZoneHalf() < game.zoneHalfWidth * 0.8f) pulsed = true
            assertEquals(TrapPaint.count(game.zoneHalfWidth, cell), mines.size)
        }
        assertTrue(checked > 1000, "genug Frames geprüft: $checked")
        assertTrue(pulsed, "die Falle hat tatsächlich geatmet")
    }

    @Test
    fun `die Minen liegen in der Breite der Falle und in Laufrichtung`() {
        eachTrapFrame(setOf(Twist.FAKE, Twist.PULSE)) { game, mines ->
            val half = game.fakeZoneHalf()
            val direction = TrapPaint.direction(game.fakeZoneCenter)
            var last = Float.NEGATIVE_INFINITY
            for (mine in mines) {
                val d = TimingGame.wrapToPi(mine.angle - game.fakeZoneCenter)
                assertTrue(abs(d) <= half + 1e-4f, "Mine außerhalb der Falle: $d > $half")
                assertTrue(d * direction > last, "Minen in Laufrichtung sortiert")
                last = d * direction
            }
        }
    }

    @Test
    fun `rot ist genau was die Maske sagt, im ersten Schritt eine Mine`() {
        var firstSteps = 0
        val checked = eachTrapFrame(setOf(Twist.FAKE, Twist.PULSE)) { game, mines ->
            if (touchesZone(game)) return@eachTrapFrame
            val count = TrapPaint.count(game.zoneHalfWidth, cell)
            val step = floor(game.zoneAge / TrapPaint.LIGHT_STEP_SECONDS).toInt()
            val mask = TrapPaint.redMask(count, step)
            assertEquals(mask, mines.map { it.red }, "Schritt $step")
            val period = count + (count + 1) / 2
            if (((step % period) + period) % period == 0) {
                assertEquals(1, mines.count { it.red }, "Schritt 0: genau eine Mine rot")
                assertTrue(mines.first().red, "Schritt 0: die erste Mine in Laufrichtung")
                firstSteps++
            }
        }
        assertTrue(checked > 1000, "genug Frames geprüft: $checked")
        assertTrue(firstSteps > 0, "Schritt 0 kam vor")
    }

    @Test
    fun `ohne Falle gibt es keine Minen`() {
        val game = TimingGame(Random(1L)).apply { twistOverride = emptySet() }
        game.start()
        assertTrue(trapMines(game, segments, game.effectiveZoneHalf()).isEmpty())
    }

    @Test
    fun `das Lauflicht zieht keine Zufallszahl`() {
        // Zwei gleiche Spiele: eins wird zusätzlich gezeichnet, danach
        // müssen beide denselben Verlauf haben.
        val a = gameWithTrap()
        val b = gameWithTrap()
        repeat(600) {
            trapMines(a, segments, a.effectiveZoneHalf())
            a.update(1f / 120f)
            b.update(1f / 120f)
            if (a.isInZone) a.tap()
            if (b.isInZone) b.tap()
        }
        assertEquals(b.zoneCenter, a.zoneCenter, 0f)
        assertEquals(b.score, a.score)
    }
}
