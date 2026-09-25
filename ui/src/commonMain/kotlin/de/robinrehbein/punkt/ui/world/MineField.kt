package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.robinrehbein.punkt.game.DeathCause
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.game.TrapPaint
import de.robinrehbein.punkt.game.Twist
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Die Falle (Twist FAKE) als Kette von Minesweeper-Minen, dazu das rote
 * Lauflicht und die Explosion beim Hineintippen (Plan 3.4).
 *
 * Sprite, Farben und Takt stehen in [TrapPaint] in `:core`; hier wird nur
 * gezeichnet. Nichts in dieser Datei zieht eine Zufallszahl (Plan 8.1
 * Punkt 7): Die Uhr des Lauflichts ist [TimingGame.zoneAge], die Richtung
 * kommt aus [TrapPaint.direction].
 */

/** Dauer der Explosion in Sekunden. */
internal const val TRAP_BOOM_SECONDS = 0.45f

/** Zahl der Funken der Explosion. */
internal const val TRAP_BOOM_SPARKS = 12

/**
 * Pixelmaß einer Mine: ein Sprite-Pixel ist 0,6 Bahnzellen breit, ganzzahlig
 * gerundet und mindestens 1 px, damit die Kanten scharf bleiben. Der Rand
 * steht 0,6 Sprite-Pixel über.
 */
internal fun minePixel(cell: Float): Int = (cell * 0.6f).roundToInt().coerceAtLeast(1)

/** Breite des hellen Rands in Bildpunkten bei Sprite-Pixeln der Kante [px]. */
internal fun mineRim(px: Int): Int = (px * 0.6f).roundToInt().coerceAtLeast(1)

/**
 * Passen zwei Minen mit Sprite-Pixeln [px] im Abstand [distance] (Bildpunkte,
 * beliebige Richtung) nebeneinander, ohne dass sich ihre Kugeln berühren?
 *
 * Zwischen zwei Kugeln bleibt mindestens eine Randbreite plus ein Bildpunkt
 * für das Runden auf ganze Pixel; die Ränder selbst dürfen sich teilen.
 * Das Sprite ist ein 5×5-Kern mit vier Zacken: Schräg (45°) begrenzt der
 * Kern (`√2 · (5 px + rim)`), längs der Achsen die Zacken (`7 px + rim`).
 */
internal fun minesFit(px: Int, distance: Float): Boolean = distance >= mineMineDistance(px)

/** Der kleinste Abstand zweier Minen, den [minesFit] zulässt, in Bildpunkten. */
internal fun mineMineDistance(px: Int): Float {
    val rim = mineRim(px)
    val diagonal = SQRT2 * (5 * px + rim)
    val axis = (TrapPaint.MINE_SIZE * px + rim).toFloat()
    return max(diagonal, axis) + 1f
}

private const val SQRT2 = 1.4142135f

/**
 * Wie weit (Bildpunkte, beliebige Richtung) die Mitte einer Mine mit
 * Sprite-Pixeln [px] von der Mitte eines Sandblocks der halben Kante
 * [blockHalf] mindestens entfernt sein muss, damit Mine samt Rand den
 * Block nicht berührt.
 *
 * Die Mine ist ein 5×5-Kern mit je einem Zacken oben, unten, links und
 * rechts, jeder Pixel mit Rand. Mit dem Block zusammen ergibt das drei
 * Rechtecke um die Minenmitte (Kern, senkrechter und waagrechter Zacken),
 * die die Blockmitte nicht treffen darf — in welcher Richtung auch immer
 * der Block liegt. Ein Bildpunkt Luft für das Runden auf ganze Pixel.
 */
internal fun mineBlockDistance(px: Int, blockHalf: Float): Float {
    val rim = mineRim(px)
    val core = 2.5f * px + rim + blockHalf + 1f
    val thin = 0.5f * px + rim + blockHalf + 1f
    val long = 3.5f * px + rim + blockHalf + 1f
    return max(SQRT2 * core, sqrt(thin * thin + long * long))
}

/** Winkel des Bahn-Blocks [k] von [segments] — dieselbe Rechnung wie in `drawTrack`. */
internal fun trackSlotAngle(k: Int, segments: Int): Float =
    k.toFloat() / segments * (2f * PI.toFloat())

/**
 * Die Kette der Minen einer Falle, gemessen in Bahn-Blöcken: Block `k`
 * liegt bei `k` (Winkel `k · 2π / segments`).
 *
 * [at]: die Lage der Minen, aufsteigend nach Winkel. [before] und [after]:
 * die Blöcke direkt vor und hinter der Kette, auf denen wieder Sand liegt
 * (oder die Zone), oder null, wenn die Falle keinen Block belegt.
 */
internal class TrapChain(val at: FloatArray, val before: Int?, val after: Int?)

/**
 * Die Bahn-Blöcke, die eine Falle der halben Breite [half] um [center]
 * belegt — genau die, die `drawTrack` frei lässt
 * (`|wrapToPi(Winkel − center)| ≤ half`). Als fortlaufender Bereich,
 * gezählt vom Block, der [center] am nächsten liegt; die Grenzen dürfen
 * unter 0 und über [segments] hinaus reichen. Null, wenn kein Block darin
 * liegt.
 */
internal fun trapSlotRun(center: Float, half: Float, segments: Int): IntRange? {
    val slot = 2f * PI.toFloat() / segments
    fun inside(k: Int): Boolean =
        abs(TimingGame.wrapToPi(trackSlotAngle(k.mod(segments), segments) - center)) <= half
    val mid = (center / slot).roundToInt()
    if (!inside(mid)) return null
    var first = mid
    var last = mid
    while (last - first + 1 < segments && inside(first - 1)) first--
    while (last - first + 1 < segments && inside(last + 1)) last++
    return first..last
}

/**
 * Wo die [count] Minen einer Falle (Mitte [center], halbe Breite [half])
 * liegen: im Takt der Bahn-Blöcke, wie die grünen Zonenblöcke.
 *
 * Die Falle belegt die Blöcke `first..last` ([trapSlotRun]); dort bleibt
 * die Bahn frei, davor und dahinter liegt wieder Sand.
 * - Passen die Minen hinein (`k = last − first + 1 ≥ count`), liegen sie
 *   im Blockabstand auf den Blöcken, die Lücke zum Sand ist genau ein
 *   Blockabstand. Ist ein Block übrig (`k = count + 1`), rücken Minen und
 *   Lücken gleichmäßig um `(k + 1) / (count + 1)` auseinander.
 * - Unter PULS wird die Falle schmaler als die Kette (`k < count`): Die
 *   Minen rücken zusammen, die Lücke zum Sand bleibt höchstens ein
 *   Blockabstand und mindestens [endGap] (in Blöcken; so weit, dass eine
 *   Mine den Sandblock nicht berührt, siehe [trapEndGap]).
 *
 * Keine Mine liegt außerhalb der Falle. Nur wenn selbst die kleinsten
 * Minen so nicht nebeneinander passen (winzige Bahn unter PULS), spreizt
 * sich die Kette auf [minPitch] Blöcke Abstand, so weit die Falle reicht;
 * dann darf sie den Sand daneben berühren, und `drawTrack` lässt diesen
 * Block frei ([mineTouchesBlock]). Ist die Falle schmaler als ein halber
 * Block (kein Block liegt darin), verteilen sich die Minen gleichmäßig
 * über die Breite.
 */
internal fun trapChain(
    center: Float,
    half: Float,
    count: Int,
    segments: Int,
    endGap: Float,
    minPitch: Float = 0f
): TrapChain {
    val slot = 2f * PI.toFloat() / segments
    val run = trapSlotRun(center, half, segments)
        ?: return TrapChain(
            FloatArray(count) { i -> (center - half + (i + 0.5f) * 2f * half / count) / slot },
            null,
            null
        )
    val first = run.first
    val last = run.last
    if (count == 1) return TrapChain(floatArrayOf((first + last) / 2f), first - 1, last + 1)
    // Mitte und Breite der Falle in derselben Zählung wie first (siehe trapSlotRun).
    val c = center / slot
    val h = half / slot
    val k = last - first + 1
    var lo: Float
    var hi: Float
    if (k >= count) {
        val gap = max(1f, (k + 1f) / (count + 1f))
        lo = first - 1 + gap
        hi = last + 1 - gap
    } else {
        val gap = min(1f, max(endGap, (k + 1f) / (count + 1f)))
        lo = max(c - h, first - 1 + gap)
        hi = min(c + h, last + 1 - gap)
    }
    val need = minPitch * (count - 1)
    if (hi - lo < need) {
        // Notfall: Minen vor Sand. Um die Mitte der Kette spreizen,
        // höchstens bis an die Ränder der Falle.
        val mid = (lo + hi) / 2f
        lo = mid - need / 2f
        hi = mid + need / 2f
        if (lo < c - h) {
            hi += c - h - lo
            lo = c - h
        }
        if (hi > c + h) {
            lo -= hi - (c + h)
            hi = c + h
        }
        lo = max(lo, c - h)
    }
    val step = (hi - lo) / (count - 1)
    return TrapChain(FloatArray(count) { i -> lo + i * step }, first - 1, last + 1)
}

/**
 * Berührt eine Mine mit Sprite-Pixeln [px] einen Sandblock der halben
 * Kante [blockHalf], dessen Mitte um ([dx], [dy]) Bildpunkte neben der
 * Minenmitte liegt? Dieselben drei Rechtecke wie in [mineBlockDistance],
 * hier für die tatsächliche Lage statt für jede Richtung.
 */
internal fun mineTouchesBlock(dx: Float, dy: Float, px: Int, blockHalf: Float): Boolean {
    val rim = mineRim(px)
    val core = 2.5f * px + rim + blockHalf + 1f
    val thin = 0.5f * px + rim + blockHalf + 1f
    val long = 3.5f * px + rim + blockHalf + 1f
    val x = abs(dx)
    val y = abs(dy)
    return (x < core && y < core) || (x < thin && y < long) || (x < long && y < thin)
}

/** Sehne auf der Bahn mit Radius [radius] → Winkel in Bahn-Blöcken. */
private fun chordToSlots(chord: Float, radius: Float, segments: Int): Float =
    2f * asin((chord / (2f * radius)).coerceIn(0f, 1f)) / (2f * PI.toFloat() / segments)

/**
 * Die kleinste Lücke zwischen Mine und Sandblock, in Bahn-Blöcken: so
 * weit, dass eine Mine mit Sprite-Pixeln [px] den Sandblock (Kante
 * `3 · cell`) auf der Bahn mit Radius [radius] nicht berührt
 * ([mineBlockDistance], dazu ein halber Bildpunkt gegen Rundung).
 */
internal fun trapEndGap(px: Int, radius: Float, cell: Float, segments: Int): Float =
    chordToSlots(mineBlockDistance(px, cell * 3f / 2f) + 0.5f, radius, segments)

/**
 * Sprite-Pixelmaß der Minen der aktuellen Falle (Plan 3.4: Mine in
 * Blockgröße). Höchstens [minePixel] ([cell]), und so klein, dass sich
 * weder zwei Minen ([minesFit]) noch Mine und Sandblock
 * ([mineBlockDistance]) je berühren, auch nicht an der engsten Stelle
 * unter PULS. Geprüft wird die Kette ([trapChain]) über alle Breiten, die
 * die Falle in dieser Runde annehmen kann; so bleibt die Größe beim Atmen
 * stehen, nur der Abstand atmet. Mindestens 1.
 */
internal fun trapMinePixel(game: TimingGame, segments: Int, radius: Float, cell: Float): Int =
    trapMineFit(game, segments, radius, cell).first

/** [trapMinePixel] und ob die Minen dieser Größe überall hineinpassen. */
private fun trapMineFit(game: TimingGame, segments: Int, radius: Float, cell: Float): Pair<Int, Boolean> {
    val slot = 2f * PI.toFloat() / segments
    val count = TrapPaint.count(game.zoneHalfWidth, slot)
    val halves = if (Twist.PULSE in game.activeTwists) {
        val narrowest = min(game.fakeZoneHalf(), game.zoneHalfWidth * TimingGame.PULSE_MIN_SHARE)
        List(PULSE_SAMPLES + 1) { j ->
            narrowest + (game.zoneHalfWidth - narrowest) * j / PULSE_SAMPLES
        } + game.fakeZoneHalf()
    } else {
        listOf(game.fakeZoneHalf())
    }
    // Sehne statt Bogen: So weit liegen zwei Punkte der Bahn auf dem Bild auseinander.
    fun chord(slots: Float): Float = 2f * radius * sin(abs(slots) * slot / 2f)
    fun fits(px: Int): Boolean {
        val endGap = trapEndGap(px, radius, cell, segments)
        val blockDistance = mineBlockDistance(px, cell * 3f / 2f)
        return halves.all { half ->
            val chain = trapChain(game.fakeZoneCenter, half, count, segments, endGap)
            val at = chain.at
            val pitchOk = (1 until at.size).all { minesFit(px, chord(at[it] - at[it - 1])) }
            val sandOk = chain.before == null || chain.after == null ||
                (chord(at.first() - chain.before) >= blockDistance &&
                    chord(chain.after - at.last()) >= blockDistance)
            pitchOk && sandOk
        }
    }
    var px = minePixel(cell)
    while (px > 1 && !fits(px)) px--
    return px to (px > 1 || fits(1))
}

/** Stützstellen über die Atembreite der Falle unter PULS. */
private const val PULSE_SAMPLES = 16

/** Was `drawTrack` von der Falle zeichnet: die Minen und ihr Pixelmaß. */
internal class TrapLayout(val mines: List<TrapMine>, val px: Int)

/**
 * Die Minen der Falle so, wie `drawTrack` sie auf die Bahn mit Radius
 * [radius] und Zellgröße [cell] legt: Pixelmaß aus [trapMinePixel], Lage
 * aus [trapMines] mit der Lücke [trapEndGap] dieses Pixelmaßes. Passen
 * selbst Minen mit einem Bildpunkt nicht (siehe [trapChain]), halten sie
 * wenigstens untereinander Abstand.
 */
internal fun trapLayout(
    game: TimingGame,
    segments: Int,
    zoneHalf: Float,
    radius: Float,
    cell: Float
): TrapLayout {
    val (px, fits) = trapMineFit(game, segments, radius, cell)
    val minPitch = if (fits) 0f else chordToSlots(mineMineDistance(px) + 0.25f, radius, segments)
    val endGap = trapEndGap(px, radius, cell, segments)
    return TrapLayout(trapMines(game, segments, zoneHalf, endGap, minPitch), px)
}

/**
 * Eine Mine, mittig auf ([cx], [cy]), mit Sprite-Pixeln der Kantenlänge
 * [px]. [red]: Das Lauflicht steht gerade auf ihr, die Kugel ist rot.
 *
 * Erst der helle Rand um jeden gesetzten Pixel (für dunkle Himmel), dann
 * Kugel und Glanz darüber. Alle Koordinaten auf ganze Pixel gerundet.
 */
internal fun DrawScope.drawMine(cx: Float, cy: Float, px: Int, red: Boolean) {
    drawMineRim(cx, cy, px)
    drawMineBody(cx, cy, px, red)
}

/** Linke obere Ecke des Sprites, auf ganze Pixel gerundet. */
private fun mineOrigin(c: Float, u: Float): Float =
    (c - u * TrapPaint.MINE_SIZE / 2f).roundToInt().toFloat()

/**
 * Nur der helle Rand einer Mine. Liegen Minen dicht (PULS drückt die
 * Kette zusammen), zeichnet `drawTrack` erst alle Ränder, dann alle
 * Kugeln, damit kein Rand über eine Nachbarkugel fällt.
 */
internal fun DrawScope.drawMineRim(cx: Float, cy: Float, px: Int) {
    val u = px.toFloat()
    val size = TrapPaint.MINE_SIZE
    val ox = mineOrigin(cx, u)
    val oy = mineOrigin(cy, u)
    val rim = mineRim(px).toFloat()
    val rimColor = Color(TrapPaint.RIM)
    for (r in 0 until size) {
        val row = TrapPaint.MINE[r]
        for (k in 0 until size) {
            if (row[k] == '.') continue
            drawRect(
                color = rimColor,
                topLeft = Offset(ox + k * u - rim, oy + r * u - rim),
                size = Size(u + 2f * rim, u + 2f * rim)
            )
        }
    }
}

/** Kugel und Glanz einer Mine, ohne Rand (siehe [drawMineRim]). */
internal fun DrawScope.drawMineBody(cx: Float, cy: Float, px: Int, red: Boolean) {
    val u = px.toFloat()
    val size = TrapPaint.MINE_SIZE
    val ox = mineOrigin(cx, u)
    val oy = mineOrigin(cy, u)
    val ball = Color(if (red) TrapPaint.RED else TrapPaint.BALL)
    val gloss = Color(TrapPaint.GLOSS)
    for (r in 0 until size) {
        val row = TrapPaint.MINE[r]
        for (k in 0 until size) {
            val ch = row[k]
            if (ch == '.') continue
            drawRect(
                color = if (ch == 'W') gloss else ball,
                topLeft = Offset(ox + k * u, oy + r * u),
                size = Size(u, u)
            )
        }
    }
}

/**
 * Eine Mine der Falle: Winkel auf der Bahn, ihr Platz [index] in der
 * Lauflicht-Maske und ob sie gerade rot ist.
 */
internal class TrapMine(val angle: Float, val index: Int, val red: Boolean)

/**
 * Die Minen der aktuellen Falle samt Lauflicht (Plan 3.4, 8.5, 8.7).
 *
 * Die Zahl ist `n = TrapPaint.count(zoneHalfWidth, cell)` mit der
 * Grundbreite [TimingGame.zoneHalfWidth] und [cell] = Winkel eines
 * Bahn-Blocks ([segments] Blöcke im Kreis). Sie hängt also nicht an der
 * Lage der Falle zum Segment-Raster und bleibt unter PULS stehen. Wo die
 * n Minen liegen, sagt [trapChain] mit der Breite
 * [TimingGame.fakeZoneHalf]: im Takt der Bahn-Blöcke, ein Blockabstand
 * Luft zum Sand. Unter PULS atmet die Kette, die Zahl nicht. [endGap] ist
 * die kleinste Lücke zum Sand in Blöcken, wenn PULS die Kette
 * zusammendrückt, [minPitch] der Notfall-Abstand der Minen (beides setzt
 * [trapLayout] passend zum Pixelmaß der Minen).
 *
 * Mine `i` (in Laufrichtung des Lichts, [TrapPaint.direction]) bekommt
 * genau den Maskeneintrag `i` aus [TrapPaint.redMask]; die Uhr ist
 * [TimingGame.zoneAge]. Nur wenn die Falle die Zone ([zoneHalf]) berührt,
 * entfallen die Minen darin (Grün bleibt Grün), ihre Einträge ebenso.
 * Nichts hier zieht eine Zufallszahl.
 */
internal fun trapMines(
    game: TimingGame,
    segments: Int,
    zoneHalf: Float,
    endGap: Float = 1f,
    minPitch: Float = 0f
): List<TrapMine> {
    if (!game.hasFakeZone) return emptyList()
    val cell = 2f * PI.toFloat() / segments
    val count = TrapPaint.count(game.zoneHalfWidth, cell)
    val mask = TrapPaint.redMask(
        count,
        floor(game.zoneAge / TrapPaint.LIGHT_STEP_SECONDS).toInt()
    )
    val direction = TrapPaint.direction(game.fakeZoneCenter)
    val at = trapChain(game.fakeZoneCenter, game.fakeZoneHalf(), count, segments, endGap, minPitch).at
    val mines = ArrayList<TrapMine>(count)
    for (i in 0 until count) {
        val slot = if (direction > 0) at[i] else at[count - 1 - i]
        val angle = TimingGame.wrapTwoPi(slot * cell)
        if (abs(TimingGame.wrapToPi(angle - game.zoneCenter)) <= zoneHalf) continue
        mines.add(TrapMine(angle, i, mask[i]))
    }
    return mines
}

/**
 * Die Explosion beim Hineintippen in die Falle: 12 Pixel-Funken, erst
 * gelb/orange, dann rot/grau, über [TRAP_BOOM_SECONDS], dazu ein kleiner
 * heller Kern, der schrumpft.
 *
 * Nur bei [TimingGame.lastDeathCause] == TRAP. Ohne eigenen Blitz: Jeder
 * Tod setzt schon `flashAlpha = 1` (Plan 8.7), ein zweiter wäre doppelt.
 * [time] ist die Zeit seit dem Tod (`FxState.deathTime`), negativ = kein Tod.
 */
internal fun DrawScope.drawTrapBoom(
    game: TimingGame,
    time: Float,
    cx: Float,
    cy: Float,
    radius: Float,
    cell: Float
) {
    if (game.lastDeathCause != DeathCause.TRAP) return
    if (time < 0f || time >= TRAP_BOOM_SECONDS) return
    val x = cx + cos(game.angle) * radius
    val y = cy + sin(game.angle) * radius
    val q = (time / TRAP_BOOM_SECONDS).coerceIn(0f, 1f)
    for (i in 0 until TRAP_BOOM_SPARKS) {
        val odd = i % 2 == 1
        val a = i.toFloat() / TRAP_BOOM_SPARKS * (2f * PI.toFloat()) + (if (odd) 0.2f else 0f)
        val d = cell * (3f + q * (if (odd) 11f else 15f))
        val s = max(1f, (cell * (4.5f - q * 2.5f)).roundToInt().toFloat())
        val color = if (q < 0.5f) {
            if (odd) BoomYellow else BoomOrange
        } else {
            if (odd) BoomRed else BoomSmoke
        }
        drawRect(
            color = color,
            topLeft = Offset(
                (x + cos(a) * d - s / 2f).roundToInt().toFloat(),
                (y + sin(a) * d - s / 2f).roundToInt().toFloat()
            ),
            size = Size(s, s)
        )
    }
    val core = (cell * 6f * (1f - q)).roundToInt().toFloat()
    if (core >= 1f) {
        drawRect(
            color = DotShine,
            topLeft = Offset((x - core / 2f).roundToInt().toFloat(), (y - core / 2f).roundToInt().toFloat()),
            size = Size(core, core)
        )
    }
}

private val BoomYellow = DotBody
private val BoomOrange = DotShade
private val BoomRed = Color(TrapPaint.RED)
private val BoomSmoke = Color(0xFF8A8A8A)
