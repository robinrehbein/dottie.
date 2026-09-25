package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.TimingGame
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * Der Startbildschirm als Tutorial (Plan 3.1, 8.5 AP-22).
 *
 * Unter der Startregel ist der erste Tap im Grün schon Treffer 1, ein Tap
 * daneben kostet nichts ([de.robinrehbein.punkt.game.GameEventNotYet]).
 * Damit das niemand erklären muss, zeigt der Startbildschirm es vor:
 *
 * - Eine Pixel-Hand in der Ringmitte drückt genau dann, wenn der Punkt
 *   im Grün ist, und lässt los, wenn er draußen ist. Beim Drücken läuft
 *   ein Echo an der Fingerspitze. Nur in READY und nur während der
 *   Stützräder ([START_COACH_RUNS]).
 * - Die Zone leuchtet, solange der Punkt drin ist, in READY und RUNNING,
 *   ebenfalls nur während der Stützräder ([drawZoneGlow]).
 * - Jeder freie Tap hinterlässt ein Tipp-Echo, einen wachsenden Umriss,
 *   in allen Phasen und für alle Spieler ([drawTapEchoes]).
 * - Ein Tap daneben in READY zeigt „NOCH NICHT“ im Ring über der Hand und
 *   wackelt 0,7 s ([drawNotYet]).
 *
 * Nichts davon zieht Zufallszahlen oder ändert das Spiel (Plan 8.1).
 */

/** So viele Läufe lang helfen Hand und Leuchten (Plan 8.6 #2). */
const val START_COACH_RUNS = 5

/** Die Stützräder gelten, solange weniger als [START_COACH_RUNS] Läufe gezählt sind. */
fun startCoachActive(runCount: Int): Boolean = runCount < START_COACH_RUNS

/** So lange steht „NOCH NICHT“ und wackelt (Plan 3.1). */
const val NOT_YET_SECONDS = 0.7f

/** Dauer eines Tipp-Echos (Plan 8.7, feedback-check.html:616-619). */
const val TAP_ECHO_SECONDS = 0.45f

/** Dauer des Echos an der Fingerspitze der Hand. */
const val HAND_ECHO_SECONDS = 0.5f

/** Mehr Echos gleichzeitig zeichnet niemand ab, der Rest fällt weg. */
private const val MAX_TAP_ECHOES = 8

/** Ein Tipp-Echo: Stelle des Taps und sein Alter in Sekunden. */
class TapEcho(val x: Float, val y: Float) {
    var age = 0f
}

/** Merkt einen Tap für das Echo. Aufgerufen aus `onPress`, in jeder Phase. */
fun FxState.addTapEcho(position: Offset) {
    if (tapEchoes.size >= MAX_TAP_ECHOES) tapEchoes.removeAt(0)
    tapEchoes.add(TapEcho(position.x, position.y))
}

/**
 * Schreibt die Uhren des Startbildschirms um [dt] fort: „NOCH NICHT“,
 * die Tipp-Echos und das Drücken der Hand. Die Hand drückt, solange der
 * Punkt im Grün ist; beim Wechsel auf „gedrückt“ beginnt das Echo an der
 * Fingerspitze.
 */
fun advanceStartCoach(fx: FxState, game: TimingGame, dt: Float) {
    fx.notYetTime = (fx.notYetTime - dt).coerceAtLeast(0f)
    val iterator = fx.tapEchoes.iterator()
    while (iterator.hasNext()) {
        val echo = iterator.next()
        echo.age += dt
        if (echo.age > TAP_ECHO_SECONDS) iterator.remove()
    }
    if (fx.handEchoTime >= 0f) {
        fx.handEchoTime += dt
        if (fx.handEchoTime > HAND_ECHO_SECONDS) fx.handEchoTime = -1f
    }
    val pressed = game.phase == GamePhase.READY && game.isInZone
    if (pressed && !fx.handPressed) fx.handEchoTime = 0f
    fx.handPressed = pressed
}

/** Die waagrechte Auslenkung beim Wackeln, in Vielfachen von [unit] (Mockup :605). */
fun notYetWobble(notYetTime: Float, unit: Float): Float =
    if (notYetTime <= 0f) 0f else sin(notYetTime * 40f) * 4f * unit * notYetTime

// ===== Hand =====

/**
 * Pixel-Hand (16×17), Zeigefinger nach oben, wie `HAND` in
 * docs/feedback-check.html. O = Kontur, W = Haut, S = Schatten, Y = Ärmel.
 */
internal val HAND = listOf(
    "....OO..........",
    "...OWWO.........",
    "...OWWO.........",
    "...OWWO.........",
    "...OWWOOO.......",
    "...OWWOWWOOO....",
    "...OWWOWWOWWOO..",
    "OO.OWWOWWOWWOWO.",
    "OWOOWWWWWWWWOWO.",
    "OWWOWWWWWWWWWWO.",
    ".OWWWWWWWWWWWWO.",
    ".OWWWWWWWWWWWSO.",
    "..OWWWWWWWWWSO..",
    "..OWWWWWWWWWSO..",
    "...OWWWWWWWSO...",
    "...OYYYYYYYYO...",
    "...OOOOOOOOOO..."
)

internal const val HAND_COLUMNS = 16

/** Die Farben der Hand (feedback-check.html:813, `P.finger`). */
internal val HandSkin = Color(0xFFFFFFFF)
internal val HandShade = Color(0xFFD9CFD6)
internal val HandSleeve = Color(0xFFFFD847)

/** Die Fingerspitze beim Drücken (feedback-check.html:825). */
internal val HandPressedTip = Color(0xFFFFE9A8)

/** Schlagschatten: Kontur #543847 mit 35 % Deckkraft. */
internal val HandDropShadow = Color(0x59543847)

/** Ein Hand-Pixel für einen Ringradius: wie im Mockup (R = 0,34 W, u = W/90), auf ganze Pixel. */
internal fun handUnit(radius: Float): Float = max(1f, floor(radius / 30.6f))

/**
 * Linke obere Ecke des Hand-Sprites, auf ganze Pixel abgerundet. Die
 * Fingerspitze (Spalte 4–5, Zeile 0) liegt bei [tipX]/[tipY].
 */
internal fun handOrigin(tipX: Float, tipY: Float, u: Float): Offset =
    Offset(floor(tipX - u * 5f), floor(tipY))

/**
 * Hand und Echo an der Fingerspitze. Die Spitze (Spalte 4–5, Zeile 0)
 * steht knapp über der Ringmitte; gedrückt sinkt sie um zwei Pixel und
 * färbt sich gelblich.
 */
fun DrawScope.drawStartHand(
    fx: FxState,
    cx: Float,
    cy: Float,
    radius: Float,
    wobble: Float
) {
    val u = handUnit(radius)
    val pressed = fx.handPressed
    val tipX = cx + wobble
    val tipY = cy - u + if (pressed) u * 2f else 0f
    // Auf ganze Pixel gerundet: u ist ganzzahlig, liegt auch der Ursprung
    // im Raster, stoßen die Hand-Pixel ohne Naht aneinander, auch wenn die
    // Ringmitte gebrochen ist (720×1280: cy = 563,2).
    val origin = handOrigin(tipX, tipY, u)
    val ox = origin.x
    val oy = origin.y
    for (row in HAND.indices) {
        for (col in 0 until HAND_COLUMNS) {
            if (HAND[row][col] == '.') continue
            drawRect(
                color = HandDropShadow,
                topLeft = Offset(ox + (col + 1) * u, oy + (row + 1) * u),
                size = Size(u, u)
            )
        }
    }
    for (row in HAND.indices) {
        for (col in 0 until HAND_COLUMNS) {
            val ch = HAND[row][col]
            val color = when (ch) {
                'O' -> OutlineColor
                'W' -> if (pressed && row < 4) HandPressedTip else HandSkin
                'S' -> HandShade
                'Y' -> HandSleeve
                else -> continue
            }
            drawRect(color = color, topLeft = Offset(ox + col * u, oy + row * u), size = Size(u, u))
        }
    }
    val t = fx.handEchoTime
    if (t >= 0f && t < HAND_ECHO_SECONDS) {
        val q = t / HAND_ECHO_SECONDS
        val s = u * (3f + q * 9f)
        drawRect(
            color = Color.White.copy(alpha = 1f - q),
            topLeft = Offset(tipX - s / 2f, tipY - s / 2f),
            size = Size(s, s),
            style = Stroke(width = u * 0.9f)
        )
    }
}

/**
 * Was vom Startbildschirm in die Welt gehört: Leuchten der Zone und die
 * Hand. Aufgerufen aus [drawTimingWorld] nach der Bahn und vor dem Vogel.
 */
fun DrawScope.drawStartCoach(
    game: TimingGame,
    fx: FxState,
    cx: Float,
    cy: Float,
    radius: Float,
    cell: Float
) {
    if (!fx.trainingWheels) return
    val playing = game.phase == GamePhase.READY || game.phase == GamePhase.RUNNING
    if (playing && game.isInZone) drawZoneGlow(game, cx, cy, radius, cell)
    if (game.phase == GamePhase.READY) {
        drawStartHand(fx, cx, cy, radius, notYetWobble(fx.notYetTime, 1.dp.toPx()))
    }
}

// ===== Tipp-Echos und NOCH NICHT =====

/**
 * Die Tipp-Echos: ein weißer Umriss, der von 8 auf 38 dp wächst und dabei
 * verblasst, Strich 3 dp, 0,45 s (feedback-check.html:616-619).
 */
fun DrawScope.drawTapEchoes(fx: FxState) {
    if (fx.tapEchoes.isEmpty()) return
    val stroke = Stroke(width = 3.dp.toPx())
    for (echo in fx.tapEchoes) {
        val q = (echo.age / TAP_ECHO_SECONDS).coerceIn(0f, 1f)
        val s = (8f + q * 30f).dp.toPx()
        drawRect(
            color = Color.White.copy(alpha = 1f - q),
            topLeft = Offset(echo.x - s / 2f, echo.y - s / 2f),
            size = Size(s, s),
            style = stroke
        )
    }
}

/** Mitte von „NOCH NICHT“: im Ring über der Hand, bei cy − 0,45 R (Plan 8.7). */
fun notYetCenter(size: Size): Offset {
    val ring = ringGeometry(size)
    return Offset(ring.cx, ring.cy - ring.radius * 0.45f)
}

/**
 * „NOCH NICHT“ nach einem Tap daneben in READY. Weiß mit dunklem
 * Schatten wie alle Texte über der Welt, und es wackelt, solange es steht.
 */
fun DrawScope.drawNotYet(
    fx: FxState,
    text: String,
    measurer: TextMeasurer,
    style: TextStyle
) {
    if (fx.notYetTime <= 0f || text.isEmpty()) return
    val layout = measurer.measure(text, style)
    val center = notYetCenter(size)
    val x = center.x - layout.size.width / 2f + notYetWobble(fx.notYetTime, 1.dp.toPx())
    val y = center.y - layout.size.height / 2f
    drawText(layout, topLeft = Offset(x, y))
}

// ===== Hinweis unter dem Ring =====

/** Luft zwischen Vogel-Unterkante und Hinweis, relativ zur Bildhöhe. */
private const val HINT_GAP_SHARE = 0.012f

/** Höhe der Hinweis-Fläche, relativ zur Bildhöhe (zwei Zeilen). */
private const val HINT_HEIGHT_SHARE = 0.08f

/** Seitlicher Rand der Fläche, relativ zur Bildbreite. */
private const val HINT_SIDE_SHARE = 0.06f

/**
 * Die Fläche für „TIPPE, WENN DER PUNKT IM GRÜNEN IST“: unter dem Ring,
 * mit Abstand für den Vogel, der über die Bahn hinausragt, und damit
 * auch für die Zonenblöcke (die kleiner sind als der Vogel). Koordinaten
 * im ganzen Bild, wie [ringGeometry]; wer innerhalb der Systemleisten
 * zeichnet, zieht den oberen Inset ab.
 */
fun readyHintArea(size: Size): Rect {
    val ring = ringGeometry(size)
    val top = ring.cy + ring.radius + size.height * (DOT_RADIUS_SHARE + HINT_GAP_SHARE)
    return Rect(
        left = size.width * HINT_SIDE_SHARE,
        top = top,
        right = size.width * (1f - HINT_SIDE_SHARE),
        bottom = top + size.height * HINT_HEIGHT_SHARE
    )
}

/**
 * Wie weit Ring, Zone und Vogel reichen: das Quadrat um die Bahn plus
 * den größten Überstand (Vogelradius oder halber Zonenblock).
 */
fun ringExtent(size: Size): Rect {
    val ring = ringGeometry(size)
    val cell = floor(size.height / 220f).coerceAtLeast(2f)
    val reach = ring.radius + max(size.height * DOT_RADIUS_SHARE, cell * 2.5f)
    return Rect(ring.cx - reach, ring.cy - reach, ring.cx + reach, ring.cy + reach)
}

/** Das Leuchten der Zone während der Stützräder (feedback-check.html:589-595). */
internal val GlowCore = Color(0xFFE4FFC4)
internal val GlowZone = Color(0xFFB8F27A)
internal val GlowEdge = Color(0xFFFFFFFF)

/**
 * Die Zone leuchtet (Plan 3.1): dieselben Blöcke wie in [drawTrack],
 * heller gefüllt und mit weißer Kontur. Nur als Stützräder, solange der
 * Punkt im Grün ist (siehe drawStartCoach). Liegt die Falle auf der
 * Zone, gewinnt wie in [drawTrack] die Zone.
 */
internal fun DrawScope.drawZoneGlow(
    game: TimingGame,
    cx: Float,
    cy: Float,
    radius: Float,
    cell: Float
) {
    val segments = 60
    val zoneHalf = game.effectiveZoneHalf()
    val coreHalf = game.perfectHalf()
    val outer = cell * 5f
    val inner = cell * 3.4f
    for (k in 0 until segments) {
        val a = k.toFloat() / segments * (2f * PI.toFloat())
        val relativeZone = abs(TimingGame.wrapToPi(a - game.zoneCenter))
        if (relativeZone > zoneHalf) continue
        val px = cx + cos(a) * radius
        val py = cy + sin(a) * radius
        drawRect(
            color = GlowEdge,
            topLeft = Offset(px - outer / 2f, py - outer / 2f),
            size = Size(outer, outer)
        )
        drawRect(
            color = if (relativeZone <= coreHalf) GlowCore else GlowZone,
            topLeft = Offset(px - inner / 2f, py - inner / 2f),
            size = Size(inner, inner)
        )
    }
}
