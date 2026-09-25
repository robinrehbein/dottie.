package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.robinrehbein.punkt.game.Backdrop
import de.robinrehbein.punkt.game.BackdropKind
import de.robinrehbein.punkt.game.ScenePaint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Die Ebene hinter Wolken und Requisiten: das Gebirge am BERG, der
 * Sternenhimmel im WELTRAUM. Sie bewegt sich langsamer als alles davor
 * (Parallaxe) und zieht keine Zufallszahlen — Lage und Funkeln der Sterne
 * kommen aus einem festen Hash über ihre Nummer.
 *
 * [time] ist die Laufuhr des Spiels ([de.robinrehbein.punkt.game.TimingGame.elapsed]),
 * dieselbe, an der Wolken und Requisiten driften.
 */
internal fun DrawScope.drawBackdrop(backdrop: Backdrop?, time: Float, cell: Float) {
    when (backdrop?.kind) {
        BackdropKind.GEBIRGE -> drawMountains(backdrop.colors, time, cell)
        BackdropKind.STERNENHIMMEL -> drawStarfield(backdrop.colors, time, cell)
        null -> Unit
    }
}

// ===== Gebirge =====

/**
 * Zwei Ketten aus gestuften Pixel-Bergen: hinten heller und langsamer
 * (Dunst), vorn dunkler mit Kontur. Die oberen Stufen tragen Schnee mit
 * gezackter Unterkante. Die Gipfel bleiben unter der Kreisbahn, damit die
 * Bahn vor ruhigem Himmel liegt.
 */
private fun DrawScope.drawMountains(colors: List<Long>, time: Float, cell: Float) {
    val h = size.height
    val w = size.width
    val base = ScenePaint.groundY(h) + cell * 2f
    val snow = Color(colors[4])
    val snowShade = Color(colors[5])
    // Ferne Kette: breite, hohe Gipfel, kaum Bewegung.
    drawRange(
        peaks = FAR_PEAKS, spacing = w * 0.36f, drift = time * h * 0.003f,
        base = base, cell = cell, body = Color(colors[0]), shade = Color(colors[1]),
        snow = snow, snowShade = snowShade, outline = false
    )
    // Nahe Kette: niedriger, dunkler, mit Kontur wie die Requisiten.
    drawRange(
        peaks = NEAR_PEAKS, spacing = w * 0.5f, drift = time * h * 0.006f,
        base = base, cell = cell, body = Color(colors[2]), shade = Color(colors[3]),
        snow = snow, snowShade = snowShade, outline = true
    )
}

/** Gipfelhöhen als Anteil der Bildhöhe, zyklisch. */
private val FAR_PEAKS = listOf(0.15f, 0.11f, 0.13f, 0.10f)
private val NEAR_PEAKS = listOf(0.09f, 0.115f, 0.075f)

private fun DrawScope.drawRange(
    peaks: List<Float>,
    spacing: Float,
    drift: Float,
    base: Float,
    cell: Float,
    body: Color,
    shade: Color,
    snow: Color,
    snowShade: Color,
    outline: Boolean
) {
    val w = size.width
    // Genug Berge, dass die Kette über den Rand hinaus reicht, und eine
    // Anzahl, die ein Vielfaches der Gipfelliste ist — sonst stünden am
    // Umbruch zwei gleiche Gipfel nebeneinander.
    val perLap = peaks.size
    var count = perLap
    while (count * spacing < w + spacing * 3f) count += perLap
    val total = count * spacing
    for (k in 0 until count) {
        val x = ((k * spacing - drift) % total + total) % total - spacing
        drawMountain(x, base, size.height * peaks[k % perLap], cell, body, shade, snow, snowShade, outline)
    }
}

/**
 * Ein Berg als Treppe aus waagrechten Stufen, Gipfel bei [cx]. Rechte
 * Hälfte im Schatten, obere Stufen verschneit.
 */
private fun DrawScope.drawMountain(
    cx: Float,
    base: Float,
    height: Float,
    cell: Float,
    body: Color,
    shade: Color,
    snow: Color,
    snowShade: Color,
    outline: Boolean
) {
    val step = cell * 3f
    val rows = (height / step).toInt().coerceAtLeast(3)
    val halfBase = height * 1.2f
    val snowRows = (rows * 0.34f).toInt().coerceAtLeast(1)
    // Erst die Kontur (eine Zelle rundum), dann die Flächen darüber.
    if (outline) {
        for (r in 0 until rows) {
            val half = snap(halfBase * (rows - r) / rows, cell)
            val top = base - (r + 1) * step
            drawRect(
                color = OutlineColor,
                topLeft = Offset(cx - half - cell, top - cell),
                size = Size(half * 2f + cell * 2f, step + cell)
            )
        }
    }
    for (r in 0 until rows) {
        val half = snap(halfBase * (rows - r) / rows, cell)
        val top = base - (r + 1) * step
        val snowy = r >= rows - snowRows
        // Die Schneegrenze ist gezackt: Auf der untersten Schneestufe
        // liegt der Schnee nur in der Mitte.
        val firstSnow = r == rows - snowRows
        drawRect(color = body, topLeft = Offset(cx - half, top), size = Size(half, step))
        drawRect(color = shade, topLeft = Offset(cx, top), size = Size(half, step))
        if (snowy) {
            val snowHalf = if (firstSnow) snap(half * 0.55f, cell) else half
            drawRect(color = snow, topLeft = Offset(cx - snowHalf, top), size = Size(snowHalf, step))
            drawRect(color = snowShade, topLeft = Offset(cx, top), size = Size(snowHalf, step))
        }
    }
}

/** Auf ganze Zellen runden, damit die Stufen im Pixelraster liegen. */
private fun snap(v: Float, cell: Float): Float = floor(v / cell) * cell

// ===== Sternenhimmel =====

/**
 * Sterne über das ganze Bild, die meisten eine Zelle groß, einige zwei,
 * ein paar helle als Kreuz. Jeder funkelt in seinem eigenen Takt. Dazu
 * zwei Galaxien, die sich sehr langsam drehen, und alle paar Sekunden
 * eine Sternschnuppe.
 */
private fun DrawScope.drawStarfield(colors: List<Long>, time: Float, cell: Float) {
    val w = size.width
    val h = size.height
    val starColors = listOf(Color(colors[0]), Color(colors[1]), Color(colors[2]))

    drawGalaxy(
        cx = w * 0.22f, cy = h * 0.11f, radius = w * 0.17f, turn = time * 0.06f,
        cell = cell, core = Color(colors[3]), arm = Color(colors[4]), tilt = 0.5f
    )
    drawGalaxy(
        cx = w * 0.76f, cy = h * 0.84f, radius = w * 0.2f, turn = -time * 0.045f + 1.3f,
        cell = cell, core = Color(colors[3]), arm = Color(colors[5]), tilt = 0.42f
    )

    val drift = time * h * 0.002f
    for (i in 0 until STAR_COUNT) {
        val hx = hash(i * 2 + 1)
        val hy = hash(i * 2 + 2)
        val x = ((hx * w - drift) % w + w) % w
        val y = hy * h
        val kind = hash(i + 7919)
        val color = starColors[(kind * 3f).toInt().coerceIn(0, 2)]
        // Funkeln: eigener Takt und eigene Phase je Stern.
        val speed = 1.1f + hash(i + 104729) * 2.2f
        val twinkle = 0.7f + 0.3f * sin(time * speed + hash(i + 1299709) * 6.28f)
        val px = snap(x, cell)
        val py = snap(y, cell)
        when {
            kind > 0.9f -> {
                // Heller Stern als Kreuz, sein Arm atmet mit.
                val arm = cell * (1f + (twinkle > 0.85f).toInt())
                drawRect(color, Offset(px - arm, py), Size(cell * 1f + arm * 2f, cell), alpha = twinkle)
                drawRect(color, Offset(px, py - arm), Size(cell, cell * 1f + arm * 2f), alpha = twinkle)
            }
            kind > 0.7f -> drawRect(color, Offset(px, py), Size(cell * 2f, cell * 2f), alpha = twinkle)
            else -> drawRect(color, Offset(px, py), Size(cell, cell), alpha = twinkle)
        }
    }

    drawShootingStar(time, cell, starColors[0])
}

private const val STAR_COUNT = 110

private fun Boolean.toInt(): Int = if (this) 1 else 0

/**
 * Spiralgalaxie aus zwei Armen, als Pixelblöcke auf dem Raster. [turn]
 * dreht sie, [tilt] staucht sie senkrecht (schräg von der Seite gesehen).
 * Die Arme werden nach außen blasser.
 */
private fun DrawScope.drawGalaxy(
    cx: Float,
    cy: Float,
    radius: Float,
    turn: Float,
    cell: Float,
    core: Color,
    arm: Color,
    tilt: Float
) {
    val block = cell * 2f
    val steps = 40
    for (a in 0 until 2) {
        for (k in 0 until steps) {
            val t = k / (steps - 1f)
            val theta = turn + a * PI.toFloat() + t * 4.6f
            val r = radius * (0.1f + t * 0.9f)
            val x = snap(cx + cos(theta) * r, block)
            val y = snap(cy + sin(theta) * r * tilt, block)
            val alpha = (1f - t) * 0.8f + 0.2f
            drawRect(arm, Offset(x, y), Size(block, block), alpha = alpha)
            // Staub und Sterne am Arm: versetzte Einzelzellen.
            if (k % 3 == 0) {
                drawRect(core, Offset(x + block, y - cell), Size(cell, cell), alpha = alpha)
            }
        }
    }
    // Kern: helles Kreuz.
    val kx = snap(cx, cell)
    val ky = snap(cy, cell)
    // Schimmer als Kreuz, nicht als Kasten: Ein Rechteck mit Deckkraft
    // läse sich als Fenster im Himmel.
    drawRect(arm, Offset(kx - block * 2f, ky - cell), Size(block * 5f, block * 2f), alpha = 0.5f)
    drawRect(arm, Offset(kx - cell, ky - block * 1.5f), Size(block * 2f, block * 4f), alpha = 0.5f)
    drawRect(core, Offset(kx - block, ky - cell), Size(block * 3f, block * 2f))
    drawRect(core, Offset(kx - cell, ky - block), Size(block * 2f, block * 3f))
}

/**
 * Alle [SHOOTING_PERIOD] Sekunden zieht für kurze Zeit eine Sternschnuppe
 * schräg nach unten links. Start und Richtung hängen am Zyklus, nicht am
 * Zufall.
 */
private fun DrawScope.drawShootingStar(time: Float, cell: Float, color: Color) {
    val cycle = floor(time / SHOOTING_PERIOD).toInt()
    val t = time - cycle * SHOOTING_PERIOD
    if (t > SHOOTING_DURATION) return
    val q = t / SHOOTING_DURATION
    val w = size.width
    val h = size.height
    val startX = w * (0.45f + hash(cycle + 31) * 0.5f)
    val startY = h * (0.04f + hash(cycle + 57) * 0.3f)
    val len = w * 0.35f
    val headX = startX - len * q
    val headY = startY + len * 0.45f * q
    val block = cell * 2f
    for (k in 0 until 8) {
        val back = k * block
        val x = snap(headX + back, cell)
        val y = snap(headY - back * 0.45f, cell)
        val fade = (1f - k / 8f) * (1f - q * 0.6f)
        drawRect(color, Offset(x, y), Size(if (k == 0) block else cell * 1.5f, if (k == 0) block else cell), alpha = fade)
    }
}

private const val SHOOTING_PERIOD = 7.5f
private const val SHOOTING_DURATION = 0.7f

/** Fester Hash 0..1 aus einer Ganzzahl — Sternlage ohne Zufallsquelle. */
private fun hash(n: Int): Float {
    var x = n * 0x9E3779B1.toInt()
    x = x xor (x ushr 16)
    x *= 0x85EBCA6B.toInt()
    x = x xor (x ushr 13)
    x *= 0xC2B2AE35.toInt()
    x = x xor (x ushr 16)
    return (x ushr 8) / 16_777_216f
}
