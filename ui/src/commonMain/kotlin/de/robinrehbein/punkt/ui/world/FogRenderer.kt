package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.game.Twist
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

/**
 * NEBEL (Twist GHOST, Plan 3.3): eine Pixel-Wolke direkt vor der Zone,
 * dazu die Wölkchen beim Ein- und Austritt des Punkts.
 *
 * Wo der Nebel liegt, sagt allein die Engine ([TimingGame.fogStart] bis
 * [TimingGame.fogEnd]); ob der Punkt verdeckt ist, ebenfalls
 * ([TimingGame.isDotVisible]). Die Wolke ist Bild: Sie liegt über dem
 * Vogel, deshalb gleitet er an ihrer Kontur hinein und wieder heraus,
 * statt auf einen Schlag zu verschwinden. Das ist der Schnitt am Telefon
 * (Plan 8.6 Punkt 14) — ohne eigenen Clip, die Wolke ist deckend.
 *
 * Beim Eintritt steht die erste Bausche mit ihrer Mitte auf dem
 * Nebelanfang und ist breiter als der Vogel: Er ist schon ganz in der
 * Wolke, bevor die Engine ihn ausblendet. Am Ende hört die Wolke auf der
 * Bahn mit dem Nebel auf, damit der PERFEKT-Kern frei bleibt; dort taucht
 * der Vogel vorn aus der Wolke auf und zieht den Rest heraus.
 *
 * Kein Zufall (Plan 8.1 Punkt 7): Die Form hängt nur an [fogSeed], also
 * an Treffern und Laufrichtung. Unter DRIFT wandert die Zone, die Form
 * bleibt. Die Uhr fürs Einrollen ist [TimingGame.zoneAge].
 */

/** Dauer des Einrollens einer neuen Nebelbank in Sekunden. */
internal const val FOG_ROLL_IN_SECONDS = 0.18f

/** Lebensdauer der Wölkchen beim Ein- und Austritt in Sekunden. */
internal const val FOG_PUFF_SECONDS = 0.35f

/** Zahl der Wölkchen-Pixel je Ein- oder Austritt. */
internal const val FOG_PUFF_COUNT = 6

/** Kleinster und größter Bauschradius in Wolkenpixeln (Plan 8.5: 8-12 u). */
internal const val FOG_BALL_MIN = 9f
internal const val FOG_BALL_MAX = 12f

/** Größter Abstand zweier Bauschmitten auf der Bahn, in Wolkenpixeln. */
internal const val FOG_BALL_SPACING = 6f

/** So weit ragt die letzte Bausche über das Nebelende, in Wolkenpixeln. */
private const val FOG_END_OVERHANG = 0.5f

// Schattierung nach Abstand zur Unterkante (Plan 3.3).
internal val FogBottom = Color(0xFFA0BEDA)
internal val FogLow = Color(0xFFBED4EA)
internal val FogMid = Color(0xFFD6E5F4)
internal val FogTop = Color(0xFFFFFFFF)
internal val FogInner = Color(0xFFF4F8FD)

/** Zeigt diese Zone gerade eine Nebelbank? Nur in RUNNING und nur unter NEBEL. */
internal fun isFogShown(game: TimingGame): Boolean =
    game.phase == GamePhase.RUNNING && Twist.GHOST in game.activeTwists

/**
 * Der Seed der Wolkenform: aus Treffern und Laufrichtung, nicht aus
 * `zoneCenter` — der wandert unter DRIFT, und die Wolke flackerte.
 * Jeder Treffer setzt eine neue Zone und damit eine neue Form.
 */
internal fun fogSeed(game: TimingGame): Int = game.hits * 2 + if (game.direction > 0) 1 else 0

/** Eine Bausche: Lage auf der Bahn relativ zur Zone (Radiant) und Radius in Wolkenpixeln. */
internal data class FogBall(val rel: Float, val radius: Float)

/**
 * Wolkenpixel in Bildpunkten: ein Wolkenpixel = ein Vogelpixel
 * (`2r / GRID` mit `r = DOT_RADIUS_SHARE · Bildhöhe`).
 */
internal fun fogUnit(height: Float): Float = 2f * height * DOT_RADIUS_SHARE / GRID

/**
 * Die Bauschen entlang der Bahn von [from] bis [to] (relativ zur Zone,
 * wie [TimingGame.fogStart]/[TimingGame.fogEnd]). [unitAngle] ist ein
 * Wolkenpixel als Winkel auf der Bahn.
 *
 * Die erste Bausche sitzt auf dem Nebelanfang, die letzte endet kurz
 * hinter dem Nebelende; dazwischen höchstens [FOG_BALL_SPACING] Abstand,
 * damit der Vogel überall ganz in der Wolke steckt. Die Radien schwanken
 * nur mit dem Seed, in der Mitte ist die Wolke dicker.
 */
internal fun fogBalls(seed: Int, from: Float, to: Float, unitAngle: Float): List<FogBall> {
    val firstRadius = FOG_BALL_MIN + fogNoise(seed, 0) * 1.5f
    val lastRadius = FOG_BALL_MIN + fogNoise(seed, 1) * 1f
    val first = from
    val last = max(first, to - (lastRadius - FOG_END_OVERHANG) * unitAngle)
    val gaps = max(1, ceil((last - first) / (FOG_BALL_SPACING * unitAngle)).toInt())
    return List(gaps + 1) { i ->
        val rel = first + (last - first) * i / gaps
        val radius = when (i) {
            0 -> firstRadius
            gaps -> lastRadius
            else -> {
                val middle = min(i, gaps - i).toFloat() / gaps * 2f
                (FOG_BALL_MIN + middle * 1.8f + fogNoise(seed, i + 2) * 1.2f)
                    .coerceAtMost(FOG_BALL_MAX)
            }
        }
        FogBall(rel, radius)
    }
}

/** Fester Wert in [0, 1) aus Seed und Index — ohne Zufallsquelle. */
internal fun fogNoise(seed: Int, index: Int): Float {
    var x = seed * -0x61c88647 + index * -0x7a143595
    x = x xor (x ushr 15)
    x *= 0x2c1b3c6d
    x = x xor (x ushr 12)
    x *= 0x297a2d39
    x = x xor (x ushr 15)
    return (x ushr 8 and 0xFFFF) / 65536f
}

/**
 * Die Nebelbank auf der Bahn. [from] und [to] kommen aus der Engine,
 * der Aufruf lautet `drawFogBank(game, cx, cy, radius, game.fogStart(),
 * game.fogEnd())`. Gezeichnet wird nur, wenn [isFogShown] gilt — beim Tod
 * verschwindet die Wolke.
 */
internal fun DrawScope.drawFogBank(
    game: TimingGame,
    cx: Float,
    cy: Float,
    radius: Float,
    from: Float,
    to: Float
) {
    if (!isFogShown(game)) return
    val u = fogUnit(size.height)
    val unitAngle = u / radius
    val seed = fogSeed(game)
    val balls = fogBalls(seed, from, to, unitAngle)
    val grow = (game.zoneAge / FOG_ROLL_IN_SECONDS).coerceIn(0f, 1f)

    // Kugeln im Bild (Mitte x, Mitte y, Radius in Bildpunkten). Beim
    // Einrollen wachsen sie nacheinander vom Nebelanfang her.
    val circles = ArrayList<FloatArray>(balls.size * 2)
    val last = (balls.size - 1).coerceAtLeast(1)
    balls.forEachIndexed { i, ball ->
        val scale = (grow * 2f - i.toFloat() / last).coerceIn(0f, 1f)
        if (scale <= 0f) return@forEachIndexed
        val a = game.zoneCenter + game.direction * ball.rel
        val bx = cx + cos(a) * radius
        val by = cy + sin(a) * radius
        circles += floatArrayOf(bx, by, ball.radius * u * scale)
        // Kleine Bauschen obendrauf, abwechselnd außen und innen am Ring,
        // nicht an der letzten Kugel (dort liegt die Zone). Sie wabern
        // leicht; die tragenden Kugeln bleiben fest.
        if (i < balls.size - 1) {
            val outside = (i + seed) % 2 == 0
            val wob = sin(game.elapsed * 2f + i * 1.7f) * 0.6f
            val lift = (ball.radius * (if (outside) 0.8f else 0.7f) + wob) * if (outside) 1f else -1f
            val small = ball.radius * (if (outside) 0.5f else 0.42f) + fogNoise(seed, i + 40) * 1.5f
            circles += floatArrayOf(
                bx + cos(a) * lift * u,
                by + sin(a) * lift * u,
                small * u * scale
            )
        }
    }
    if (circles.isEmpty()) return

    // Raster: ein Wolkenpixel je Zelle, verankert an der Mitte der Bank,
    // damit das Muster mit der Wolke wandert und nicht über sie kriecht.
    val midAngle = game.zoneCenter + game.direction * (from + to) / 2f
    val ax = cx + cos(midAngle) * radius
    val ay = cy + sin(midAngle) * radius
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (c in circles) {
        minX = min(minX, c[0] - c[2]); maxX = max(maxX, c[0] + c[2])
        minY = min(minY, c[1] - c[2]); maxY = max(maxY, c[1] + c[2])
    }
    val gx0 = floor((minX - ax) / u).toInt() - 1
    val gy0 = floor((minY - ay) / u).toInt() - 1
    val cols = floor((maxX - ax) / u).toInt() + 2 - gx0
    val rows = floor((maxY - ay) / u).toInt() + 2 - gy0
    val on = BooleanArray(cols * rows)
    for (row in 0 until rows) {
        val py = ay + (gy0 + row + 0.5f) * u
        for (col in 0 until cols) {
            val px = ax + (gx0 + col + 0.5f) * u
            for (c in circles) {
                val dx = px - c[0]
                val dy = py - c[1]
                if (dx * dx + dy * dy <= c[2] * c[2]) {
                    on[row * cols + col] = true
                    break
                }
            }
        }
    }
    fun isOn(col: Int, row: Int) = col in 0 until cols && row in 0 until rows && on[row * cols + col]

    fun shade(col: Int, row: Int): Color {
        var d = 1
        while (d < 5 && isOn(col, row + d)) d++
        return when {
            d == 1 -> FogBottom
            d == 2 -> FogLow
            d == 3 && (gx0 + col).mod(3) != 0 -> FogMid
            !isOn(col + 1, row) -> FogMid
            !isOn(col, row - 1) || !isOn(col - 1, row) -> FogTop
            else -> FogInner
        }
    }

    // Zeilenweise, gleiche Farben am Stück: ein Rechteck je Lauf. Die
    // Kanten liegen auf ganzen Bildpunkten — sonst glätten die Nähte
    // zwischen zwei Läufen, und der Vogel schimmerte durch die Wolke.
    fun edgeX(col: Int) = round(ax + (gx0 + col) * u)
    fun edgeY(row: Int) = round(ay + (gy0 + row) * u)
    for (row in 0 until rows) {
        val top = edgeY(row)
        val bottom = edgeY(row + 1)
        var col = 0
        while (col < cols) {
            if (!isOn(col, row)) { col++; continue }
            val color = shade(col, row)
            var end = col + 1
            while (end < cols && isOn(end, row) && shade(end, row) == color) end++
            val left = edgeX(col)
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(edgeX(end) - left, bottom - top)
            )
            col = end
        }
    }
}

/**
 * Hält die Wölkchen im Takt: Kippt [TimingGame.isInFog] unter NEBEL,
 * entsteht am Punkt ein Wölkchen — beim Eintritt und beim Austritt. Es
 * zeigt, WANN der Punkt verschwindet, nicht wo er dazwischen ist.
 * Außerhalb von RUNNING ist nichts zu sehen. Einmal je Frame aufrufen.
 */
fun trackFog(fx: FxState, game: TimingGame, dt: Float) {
    if (fx.fogInTime >= 0f) {
        fx.fogInTime += dt
        if (fx.fogInTime > FOG_PUFF_SECONDS) fx.fogInTime = -1f
    }
    if (fx.fogOutTime >= 0f) {
        fx.fogOutTime += dt
        if (fx.fogOutTime > FOG_PUFF_SECONDS) fx.fogOutTime = -1f
    }
    if (game.phase != GamePhase.RUNNING) {
        fx.fogWasIn = -1
        fx.fogInTime = -1f
        fx.fogOutTime = -1f
        return
    }
    val now = if (Twist.GHOST in game.activeTwists && game.isInFog) 1 else 0
    if (fx.fogWasIn >= 0 && now != fx.fogWasIn) {
        if (now == 1) {
            fx.fogInTime = 0f
            fx.fogInAngle = game.angle
        } else {
            fx.fogOutTime = 0f
            fx.fogOutAngle = game.angle
        }
    }
    fx.fogWasIn = now
}

/** Die Wölkchen beim Ein- und Austritt, nur in RUNNING. */
internal fun DrawScope.drawFogPuffs(game: TimingGame, fx: FxState, cx: Float, cy: Float, radius: Float) {
    if (game.phase != GamePhase.RUNNING) return
    val u = fogUnit(size.height)
    if (fx.fogInTime >= 0f) drawFogPuff(fx.fogInAngle, fx.fogInTime, cx, cy, radius, u)
    if (fx.fogOutTime >= 0f) drawFogPuff(fx.fogOutAngle, fx.fogOutTime, cx, cy, radius, u)
}

/** Sechs weiße Pixel fliegen vom Punkt aus der Wolke nach außen und verblassen. */
private fun DrawScope.drawFogPuff(angle: Float, time: Float, cx: Float, cy: Float, radius: Float, u: Float) {
    val q = (time / FOG_PUFF_SECONDS).coerceIn(0f, 1f)
    val px = cx + cos(angle) * radius
    val py = cy + sin(angle) * radius
    val distance = (FOG_BALL_MIN + 1f + q * 6f) * u
    val side = u * 1.6f * (1f - q)
    if (side <= 0f) return
    for (k in 0 until FOG_PUFF_COUNT) {
        val a = (k + 0.5f) / FOG_PUFF_COUNT * 2f * PI.toFloat()
        val x = px + cos(a) * distance
        val y = py + sin(a) * distance
        drawRect(
            color = OutlineColor,
            topLeft = Offset(x - side / 2f - u * 0.25f, y - side / 2f - u * 0.25f),
            size = Size(side + u * 0.5f, side + u * 0.5f),
            alpha = 1f - q
        )
        drawRect(
            color = FogTop,
            topLeft = Offset(x - side / 2f, y - side / 2f),
            size = Size(side, side),
            alpha = 1f - q
        )
    }
}

/**
 * Soll gerade der BLIND!-Pop stehen? Nur nach einem gewerteten
 * Blindtreffer ([TimingGame.lastHitBlind]), so lange wie PERFEKT.
 */
fun isBlindPopShown(game: TimingGame): Boolean =
    game.lastHitBlind && game.timeSinceHit < BLIND_POP_SECONDS && game.phase == GamePhase.RUNNING

/** Standzeit des BLIND!-Pops in Sekunden — dieselbe wie bei PERFEKT. */
internal const val BLIND_POP_SECONDS = 0.6f
