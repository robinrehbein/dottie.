package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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

    /**
     * Wie [eachTrapFrame], aber ein Bot tippt mitten in jede echte Zone:
     * Die Zonen werden mit jedem Treffer schmaler, die Fallen tragen
     * weniger Minen (sieben bis zwei). Geprüft wird jeder achte Frame mit
     * Falle, bis [hits] Treffer erreicht sind. Liefert die Minenzahlen,
     * die vorkamen.
     */
    private fun eachTrapFrameWhilePlaying(
        twists: Set<Twist>,
        seeds: LongRange = 1L..4L,
        hits: Int = 60,
        check: (TimingGame) -> Unit
    ): Set<Int> {
        val counts = HashSet<Int>()
        for (seed in seeds) {
            val game = TimingGame(Random(seed)).apply { twistOverride = twists }
            game.start()
            var frame = 0
            while (game.phase == GamePhase.RUNNING && game.hits < hits && frame < 400_000) {
                if (game.hasFakeZone && frame % 8 == 0) {
                    check(game)
                    counts.add(TrapPaint.count(game.zoneHalfWidth, cell))
                }
                game.update(1f / 240f)
                val rel = game.relativeToZone()
                if (abs(rel) <= game.perfectHalf() * 0.5f && !game.isInFakeZoneNow()) game.tap()
                frame++
            }
        }
        return counts
    }

    /** Steht der Punkt gerade in der Falle? Dann tippt der Bot nicht. */
    private fun TimingGame.isInFakeZoneNow(): Boolean =
        hasFakeZone && abs(TimingGame.wrapToPi(angle - fakeZoneCenter)) <= fakeZoneHalf()

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

    /** Eine Bahn auf dem Bild: Mitte, Radius und Zellgröße wie in drawTimingWorld. */
    private class Track(val name: String, val cx: Float, val cy: Float, val radius: Float, val cell: Float)

    /** Die Bahn eines Bildschirms [w]×[h], gerechnet wie drawTimingWorld. */
    private fun screen(w: Int, h: Int): Track {
        val (cx, cy, radius) = ringGeometry(Size(w.toFloat(), h.toFloat()))
        return Track("${w}x$h", cx, cy, radius, floor(h / 220f).coerceAtLeast(2f))
    }

    /** Die Telefone aus der Meldung und das kleine Format der Bilder. */
    private val screens = listOf(screen(1080, 2400), screen(1080, 2340), screen(720, 1280))

    private val tracks = geometries.map { (r, c) -> Track("r=$r", 0f, 0f, r, c) } + screens

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

    private fun Track.at(angle: Float): Offset = Offset(cx + cos(angle) * radius, cy + sin(angle) * radius)

    /** Die Minen, wie drawTrack sie auf [t] legt. */
    private fun layout(game: TimingGame, t: Track): TrapLayout =
        trapLayout(game, segments, game.effectiveZoneHalf(), t.radius, t.cell)

    /** Die Plätze für Sand: jeder Block außerhalb von Zone und Falle (Regel wie in drawTrack). */
    private fun sandSlots(game: TimingGame): List<Int> = (0 until segments).filter { k ->
        val a = trackSlotAngle(k, segments)
        abs(TimingGame.wrapToPi(a - game.zoneCenter)) > game.effectiveZoneHalf() &&
            !(abs(TimingGame.wrapToPi(a - game.fakeZoneCenter)) <= game.fakeZoneHalf())
    }

    /**
     * Die Sandblöcke, die drawTrack auf [t] wirklich zeichnet: [sandSlots]
     * ohne die, an die im Notfall eine Mine heranreicht (mineTouchesBlock).
     */
    private fun drawnSand(game: TimingGame, t: Track, trap: TrapLayout): List<Int> {
        val centers = trap.mines.map { t.at(it.angle) }
        return sandSlots(game).filter { k ->
            val b = t.at(trackSlotAngle(k, segments))
            centers.none { mineTouchesBlock(it.x - b.x, it.y - b.y, trap.px, t.cell * 3f / 2f) }
        }
    }

    /** Keine Kugel kommt einer anderen näher als eine Randbreite. */
    private fun assertMinesApart(game: TimingGame, t: Track, trap: TrapLayout = layout(game, t)) {
        val px = trap.px
        assertTrue(px in 1..minePixel(t.cell), "Pixelmaß $px")
        val rim = mineRim(px).toFloat()
        val centers = trap.mines.map { t.at(it.angle) }
        val reach = 2f * (TrapPaint.MINE_SIZE * px + 2f * rim)
        val bodies = centers.map { bodyRects(it, px) }
        // Zwischen zwei Kugeln bleibt mindestens eine Randbreite.
        val grownBodies = bodies.map { rects ->
            rects.map { Rect(it.left - rim, it.top - rim, it.right + rim, it.bottom + rim) }
        }
        for (i in bodies.indices) for (j in i + 1 until bodies.size) {
            if ((centers[i] - centers[j]).getDistance() > reach) continue
            val box = bodies[j].reduce { a, b ->
                Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
            }
            for (grown in grownBodies[i]) {
                if (!grown.hits(box)) continue
                for (b in bodies[j]) assertTrue(
                    !grown.hits(b),
                    "Minen $i/$j überdecken sich (${t.name}, cell=${t.cell}, px=$px, " +
                        "Breite ${game.fakeZoneHalf()})"
                )
            }
        }
    }

    /** Kein gezeichneter Sandblock berührt eine Mine samt Rand. */
    private fun assertSandClear(game: TimingGame, t: Track, trap: TrapLayout = layout(game, t)) {
        val px = trap.px
        val rim = mineRim(px).toFloat()
        val blockHalf = t.cell * 3f / 2f
        val centers = trap.mines.map { t.at(it.angle) }
        val reach = (TrapPaint.MINE_SIZE * px / 2f + rim + blockHalf) * 1.5f + 2f
        for (k in drawnSand(game, t, trap)) {
            val b = t.at(trackSlotAngle(k, segments))
            // Ein Viertelpixel Luft: Block und Mine teilen sich keinen Bildpunkt.
            val block = Rect(
                b.x - blockHalf - 0.25f,
                b.y - blockHalf - 0.25f,
                b.x + blockHalf + 0.25f,
                b.y + blockHalf + 0.25f
            )
            for (c in centers) {
                if ((c - b).getDistance() > reach) continue
                for (m in bodyRects(c, px)) {
                    val grown = Rect(m.left - rim, m.top - rim, m.right + rim, m.bottom + rim)
                    assertTrue(
                        !block.hits(grown),
                        "Sandblock $k unter einer Mine (${t.name}, px=$px, Breite ${game.fakeZoneHalf()})"
                    )
                }
            }
        }
    }

    @Test
    fun `die Kugeln benachbarter Minen berühren sich nie, auch nicht unter PULS`() {
        for (t in tracks) {
            var narrow = false
            eachTrapFrame(setOf(Twist.FAKE, Twist.PULSE), seeds = 1L..30L, frames = 300) { game, _ ->
                if (game.fakeZoneHalf() < game.zoneHalfWidth * 0.7f) narrow = true
                assertMinesApart(game, t)
            }
            assertTrue(narrow, "die Falle war eng (${t.name})")
            eachTrapFrame(setOf(Twist.FAKE), seeds = 1L..20L, frames = 300) { game, _ ->
                assertMinesApart(game, t)
            }
        }
    }

    @Test
    fun `keine Mine liegt auf einem Sandblock`() {
        // Früher blieben Sandblöcke neben der Kette frei, wenn eine Mine sie
        // berührt hätte — das war die große Lücke. Heute bleibt nur frei,
        // was in der Falle liegt, und trotzdem berührt keine Mine den Sand.
        for (t in tracks) {
            eachTrapFrame(setOf(Twist.FAKE, Twist.PULSE), seeds = 1L..30L, frames = 300) { game, _ ->
                assertSandClear(game, t)
            }
            eachTrapFrame(setOf(Twist.FAKE), seeds = 1L..20L, frames = 300) { game, _ ->
                assertSandClear(game, t)
            }
        }
    }

    @Test
    fun `auch schmale Fallen mit wenigen Minen überdecken nichts`() {
        for (twists in listOf(setOf(Twist.FAKE), setOf(Twist.FAKE, Twist.PULSE))) {
            val counts = eachTrapFrameWhilePlaying(twists) { game ->
                for (t in tracks) {
                    val trap = layout(game, t)
                    assertMinesApart(game, t, trap)
                    assertSandClear(game, t, trap)
                }
            }
            assertTrue(7 in counts && 2 in counts, "Fallen mit sieben bis zwei Minen geprüft: $counts ($twists)")
        }
    }

    /**
     * Die Lücke zwischen den Enden der Kette und dem nächsten gezeichneten
     * Sandblock, in Blockabständen (Mitte zu Mitte), dazu der Abstand der
     * Minen in der Kette.
     */
    private class EndGaps(
        val before: Float,
        val after: Float,
        val pitch: Float,
        val count: Int,
        val hidden: Int
    )

    private fun endGaps(game: TimingGame, t: Track): EndGaps {
        val trap = layout(game, t)
        val rel = trap.mines.map { TimingGame.wrapToPi(it.angle - game.fakeZoneCenter) }.sorted()
        val drawn = drawnSand(game, t, trap)
        val sand = drawn.map {
            TimingGame.wrapToPi(trackSlotAngle(it, segments) - game.fakeZoneCenter)
        }
        val before = sand.filter { it < rel.first() }.max()
        val after = sand.filter { it > rel.last() }.min()
        return EndGaps(
            before = (rel.first() - before) / cell,
            after = (after - rel.last()) / cell,
            pitch = if (rel.size > 1) (rel[1] - rel[0]) / cell else 1f,
            count = rel.size,
            hidden = sandSlots(game).size - drawn.size
        )
    }

    @Test
    fun `zwischen Kette und Sand liegt ein Blockabstand, wie zwischen zwei Sandblöcken`() {
        for (t in screens) {
            var exact = 0
            var frames = 0
            fun check(game: TimingGame) {
                if (touchesZone(game)) return
                val g = endGaps(game, t)
                assertEquals(0, g.hidden, "kein Sandblock neben der Kette fällt weg (${t.name})")
                // Belegt die Falle einen Block mehr, als sie Minen hat, rücken
                // Minen und Lücken gleichmäßig um höchstens 1/(n+1) auseinander.
                val loose = 1f / (g.count + 1) + 0.01f
                for ((side, gap) in listOf("vorn" to g.before, "hinten" to g.after)) {
                    assertTrue(
                        gap >= 0.99f && gap <= 1f + loose,
                        "Lücke $side ${gap}× Blockabstand statt 1 (${t.name}, ${g.count} Minen)"
                    )
                    // Die Lücke hat denselben Takt wie die Kette.
                    assertEquals(g.pitch, gap, 0.01f, "Lücke $side ungleich Takt der Minen (${t.name})")
                }
                if (abs(g.before - 1f) < 0.01f && abs(g.after - 1f) < 0.01f) exact++
                frames++
            }
            eachTrapFrame(setOf(Twist.FAKE)) { game, _ -> check(game) }
            val counts = eachTrapFrameWhilePlaying(setOf(Twist.FAKE)) { check(it) }
            assertTrue(7 in counts && 2 in counts, "Fallen mit sieben bis zwei Minen geprüft: $counts")
            // Meist belegt die Falle genau so viele Blöcke, wie sie Minen trägt.
            assertTrue(exact * 2 > frames, "genau ein Blockabstand in $exact von $frames Frames (${t.name})")
        }
    }

    @Test
    fun `unter PULS wird die Lücke zum Sand nie größer als ein Blockabstand`() {
        for (t in screens) {
            var squeezed = false
            fun check(game: TimingGame) {
                if (touchesZone(game)) return
                val g = endGaps(game, t)
                assertEquals(0, g.hidden, "kein Sandblock neben der Kette fällt weg (${t.name})")
                val loose = 1f / (g.count + 1) + 0.01f
                if (g.pitch < 0.9f) {
                    squeezed = true
                    // Zusammengedrückt: höchstens ein Blockabstand Luft.
                    assertTrue(g.before <= 1.01f && g.after <= 1.01f, "Lücke ${g.before}/${g.after} (${t.name})")
                }
                assertTrue(g.before <= 1f + loose && g.after <= 1f + loose, "Lücke ${g.before}/${g.after}")
                // Mindestens so weit, dass die Mine den Sand nicht berührt.
                val min = trapEndGap(layout(game, t).px, t.radius, t.cell, segments) - 0.02f
                assertTrue(g.before >= min && g.after >= min, "Lücke ${g.before}/${g.after} < $min (${t.name})")
            }
            eachTrapFrame(setOf(Twist.FAKE, Twist.PULSE)) { game, _ -> check(game) }
            eachTrapFrameWhilePlaying(setOf(Twist.FAKE, Twist.PULSE)) { check(it) }
            assertTrue(squeezed, "PULS hat die Kette zusammengedrückt (${t.name})")
        }
    }

    @Test
    fun `ohne PULS ist eine Mine fast so groß wie ein Sandblock`() {
        for (t in screens) {
            eachTrapFrame(setOf(Twist.FAKE), seeds = 1L..10L) { game, _ ->
                val px = layout(game, t).px
                val block = 3f * t.cell
                assertTrue(
                    TrapPaint.MINE_SIZE * px >= 0.9f * block,
                    "Mine ${TrapPaint.MINE_SIZE * px} px, Block $block px (${t.name})"
                )
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
