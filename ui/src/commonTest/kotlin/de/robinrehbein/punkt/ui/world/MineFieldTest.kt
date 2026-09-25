package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.game.TrapPaint
import de.robinrehbein.punkt.game.Twist
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
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

    /**
     * Bildgrößen wie im Spiel: Bahnradius und Zellgröße für ein kleines
     * Telefon, ein übliches Telefon (1080×2340), ein Tablet und die Maße
     * des ScreenshotRenderers.
     */
    private val geometries = listOf(
        150f to 2f,
        388.8f to 10f,
        576f to 14f,
        259.2f to 7f
    )

    /** Die Pixel-Rechtecke der Kugel einer Mine, wie drawMineBody sie setzt. */
    private fun bodyRects(c: Offset, px: Int): List<Rect> {
        val u = px.toFloat()
        val ox = (c.x - u * TrapPaint.MINE_SIZE / 2f).roundToInt().toFloat()
        val oy = (c.y - u * TrapPaint.MINE_SIZE / 2f).roundToInt().toFloat()
        val out = ArrayList<Rect>()
        for (r in 0 until TrapPaint.MINE_SIZE) for (k in 0 until TrapPaint.MINE_SIZE) {
            if (TrapPaint.MINE[r][k] == '.') continue
            out.add(Rect(ox + k * u, oy + r * u, ox + (k + 1) * u, oy + (r + 1) * u))
        }
        return out
    }

    private fun Rect.hits(o: Rect): Boolean =
        left < o.right && o.left < right && top < o.bottom && o.top < bottom

    @Test
    fun `die Kugeln benachbarter Minen berühren sich nie, auch nicht unter PULS`() {
        for ((radius, cellPx) in geometries) {
            var narrow = false
            eachTrapFrame(setOf(Twist.FAKE, Twist.PULSE), seeds = 1L..30L, frames = 300) { game, mines ->
                val px = trapMinePixel(game, segments, radius, cellPx)
                assertTrue(px in 1..minePixel(cellPx), "Pixelmaß $px")
                val rim = mineRim(px).toFloat()
                if (game.fakeZoneHalf() < game.zoneHalfWidth * 0.7f) narrow = true
                val bodies = mines.map {
                    bodyRects(Offset(cos(it.angle) * radius, sin(it.angle) * radius), px)
                }
                for (i in bodies.indices) for (j in i + 1 until bodies.size) {
                    for (a in bodies[i]) for (b in bodies[j]) {
                        // Zwischen zwei Kugeln bleibt mindestens eine Randbreite.
                        val grown = Rect(a.left - rim, a.top - rim, a.right + rim, a.bottom + rim)
                        assertTrue(
                            !grown.hits(b),
                            "Minen $i/$j überdecken sich (r=$radius, cell=$cellPx, px=$px, " +
                                "Breite ${game.fakeZoneHalf()})"
                        )
                    }
                }
            }
            assertTrue(narrow, "die Falle war eng (r=$radius)")
        }
    }

    @Test
    fun `keine Mine liegt auf einem Sandblock`() {
        for ((radius, cellPx) in geometries) {
            eachTrapFrame(setOf(Twist.FAKE, Twist.PULSE), seeds = 1L..30L, frames = 300) { game, mines ->
                val zoneHalf = game.effectiveZoneHalf()
                val px = trapMinePixel(game, segments, radius, cellPx)
                val half = mineHalfExtent(px)
                val centers = mines.map { Offset(cos(it.angle) * radius, sin(it.angle) * radius) }
                val rim = mineRim(px).toFloat()
                // Das Bild der Minen: jeder Kugel-Pixel samt Rand.
                val mineRects = centers.flatMap { c ->
                    bodyRects(c, px).map { Rect(it.left - rim, it.top - rim, it.right + rim, it.bottom + rim) }
                }
                var extraFree = 0
                for (k in 0 until segments) {
                    val a = k * cell
                    val inZone = abs(TimingGame.wrapToPi(a - game.zoneCenter)) <= zoneHalf
                    if (inZone) continue
                    val inFake = abs(TimingGame.wrapToPi(a - game.fakeZoneCenter)) <= game.fakeZoneHalf()
                    if (inFake) continue
                    val bx = cos(a) * radius
                    val by = sin(a) * radius
                    val blockHalf = cellPx * 3f / 2f
                    if (blockHitsMine(bx, by, blockHalf, centers, half)) {
                        extraFree++
                        continue
                    }
                    val block = Rect(bx - blockHalf, by - blockHalf, bx + blockHalf, by + blockHalf)
                    for (m in mineRects) {
                        assertTrue(!block.hits(m), "Sandblock $k unter einer Mine (r=$radius)")
                    }
                }
                // Frei bleibt höchstens ein Block je Ende der Kette.
                assertTrue(extraFree <= 2, "zu viele Blöcke frei: $extraFree (r=$radius)")
            }
        }
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
