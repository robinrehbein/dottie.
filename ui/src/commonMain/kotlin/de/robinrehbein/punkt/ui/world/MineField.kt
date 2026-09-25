package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.robinrehbein.punkt.game.DeathCause
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.game.TrapPaint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

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
    val rim = (u * 0.6f).roundToInt().coerceAtLeast(1).toFloat()
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
 * Lage der Falle zum Segment-Raster und bleibt unter PULS stehen. Die n
 * Minen liegen gleichmäßig über die Breite von [TimingGame.fakeZoneHalf]
 * verteilt (je eine in der Mitte von n gleich breiten Feldern): Unter PULS
 * atmet die Kette, die Zahl nicht.
 *
 * Mine `i` (in Laufrichtung des Lichts, [TrapPaint.direction]) bekommt
 * genau den Maskeneintrag `i` aus [TrapPaint.redMask]; die Uhr ist
 * [TimingGame.zoneAge]. Nur wenn die Falle die Zone ([zoneHalf]) berührt,
 * entfallen die Minen darin (Grün bleibt Grün), ihre Einträge ebenso.
 * Nichts hier zieht eine Zufallszahl.
 */
internal fun trapMines(game: TimingGame, segments: Int, zoneHalf: Float): List<TrapMine> {
    if (!game.hasFakeZone) return emptyList()
    val cell = 2f * PI.toFloat() / segments
    val count = TrapPaint.count(game.zoneHalfWidth, cell)
    val mask = TrapPaint.redMask(
        count,
        floor(game.zoneAge / TrapPaint.LIGHT_STEP_SECONDS).toInt()
    )
    val fakeHalf = game.fakeZoneHalf()
    val direction = TrapPaint.direction(game.fakeZoneCenter)
    val pitch = 2f * fakeHalf / count
    val mines = ArrayList<TrapMine>(count)
    for (i in 0 until count) {
        val offset = (-fakeHalf + (i + 0.5f) * pitch) * direction
        val angle = TimingGame.wrapTwoPi(game.fakeZoneCenter + offset)
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
