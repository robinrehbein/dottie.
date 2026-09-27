package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.robinrehbein.punkt.game.Backdrop
import de.robinrehbein.punkt.game.BackdropKind
import de.robinrehbein.punkt.game.BevelPaint
import de.robinrehbein.punkt.game.ScenePaint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Die Ebene hinter Wolken und Requisiten: Hügel (WIESE), Tafelberge
 * (WÜSTE), der Horizont (MEER), das Gebirge (BERG), die Skyline (STADT)
 * und der Sternenhimmel (WELTRAUM). Sie bewegt sich langsamer als alles
 * davor (Parallaxe) und zieht keine Zufallszahlen — Lage und Form kommen
 * aus einem festen Hash über ihre Nummer.
 *
 * [colors] sind die Farben der Ebene in der aktuellen Himmelsstufe, mit
 * dem Dunst des Himmels darüber ([DayCycle.backdrop]); [backdrop] trägt
 * die ungetönten, die für Lichter gebraucht werden — ein Fenster
 * leuchtet durch den Dunst hindurch. [night] ist 0 am Tag und 1 in
 * tiefer Nacht ([DayCycle.night]).
 *
 * [time] ist die Laufuhr des Spiels ([de.robinrehbein.punkt.game.TimingGame.elapsed]),
 * dieselbe, an der Wolken und Requisiten driften.
 *
 * Alles hier gleitet stufenlos: Formen liegen im Zellraster relativ zu
 * ihrem Ursprung, der Ursprung selbst auf ganzen Bildpixeln. Auf das
 * absolute Raster gerundet sprang eine Ebene bei jeder Zelle.
 */
internal fun DrawScope.drawBackdrop(
    backdrop: Backdrop?,
    colors: List<Long>,
    time: Float,
    cell: Float,
    night: Float = 0f
) {
    when (backdrop?.kind) {
        BackdropKind.GEBIRGE -> drawMountains(colors, time, cell)
        BackdropKind.STERNENHIMMEL -> drawStarfield(colors, time, cell)
        BackdropKind.HUEGEL -> drawHills(colors, time, cell)
        BackdropKind.TAFELBERGE -> drawMesas(colors, time, cell)
        BackdropKind.HORIZONT -> drawHorizon(colors, backdrop.colors, time, cell, night)
        BackdropKind.SKYLINE -> drawSkyline(colors, backdrop.colors, time, cell, night)
        null -> Unit
    }
}

/** Grundlinie der fernen Ebene: knapp unter der Bodenkante, wie die Requisiten. */
private fun DrawScope.backdropBase(cell: Float): Float = ScenePaint.groundY(size.height) + cell * 2f

/**
 * Ein Höhenzug als Treppe aus Zellspalten: [height] liefert die Höhe der
 * Spalte mit der Weltnummer c (Pixel). Die Spalten wandern mit [drift]
 * stufenlos nach links; ihre Höhen hängen an der Weltnummer, damit die
 * Form beim Wandern dieselbe bleibt. Gleich hohe Nachbarn werden zu
 * einem Rechteck zusammengefasst — eine Hügelkette ist so ein paar
 * Dutzend Rechtecke statt zweihundert.
 */
private inline fun DrawScope.drawRidge(
    drift: Float,
    cell: Float,
    base: Float,
    body: Color,
    edge: Color,
    height: (Int) -> Float
) {
    val shift = floor(drift / cell).toInt()
    val off = (drift - shift * cell).roundToInt().toFloat()
    val cols = (size.width / cell).toInt() + 2
    var start = 0
    var runH = snap(height(shift), cell)
    for (i in 1..cols) {
        val hh = if (i == cols) -1f else snap(height(i + shift), cell)
        if (hh != runH) {
            val x = start * cell - off
            val wRun = (i - start) * cell
            drawRect(body, Offset(x, base - runH), Size(wRun, runH))
            drawRect(edge, Offset(x, base - runH), Size(wRun, cell))
            start = i
            runH = hh
        }
    }
}

// ===== Hügel (WIESE) =====

/**
 * Zwei Hügelketten: hinten hoch, hell und langsam, vorn flacher und
 * satter, mit runden Baumkronen auf dem Kamm. Die Farben sind blaustichig
 * — Luftdunst, und zugleich weit genug weg vom Grün der Zielzone.
 */
private fun DrawScope.drawHills(colors: List<Long>, time: Float, cell: Float) {
    val h = size.height
    val base = backdropBase(cell)
    val farDrift = time * h * 0.004f
    drawRidge(farDrift, cell, base, Color(colors[0]), Color(colors[1])) { c ->
        h * (0.085f + 0.024f * sin(c * 0.05f) + 0.014f * sin(c * 0.13f + 1.3f))
    }
    val nearDrift = time * h * 0.008f
    val near = Color(colors[2])
    drawRidge(nearDrift, cell, base, near, Color(colors[3])) { c -> nearHill(c, h) }

    // Baumkronen auf dem nahen Kamm: an festen Weltspalten, sie wandern
    // mit dem Hügel. Rund aus drei Zeilen, eine Lichtzelle oben links.
    val crown = Color(colors[4])
    val crownLight = Color(colors[3])
    val shift = floor(nearDrift / cell).toInt()
    val off = (nearDrift - shift * cell).roundToInt().toFloat()
    val cols = (size.width / cell).toInt() + 2
    for (i in -3..cols) {
        val c = i + shift
        if (hash(c * 7 + 3) < 0.9f) continue
        val x = i * cell - off
        val top = base - snap(nearHill(c, h), cell) - cell * 2f
        drawRect(crown, Offset(x - cell, top), Size(cell * 3f, cell * 3f))
        drawRect(crown, Offset(x - cell * 2f, top + cell), Size(cell * 5f, cell * 2f))
        drawRect(crownLight, Offset(x - cell, top), Size(cell, cell))
    }
}

private fun nearHill(c: Int, h: Float): Float =
    h * (0.045f + 0.016f * sin(c * 0.07f + 2f) + 0.009f * sin(c * 0.17f + 0.4f))

// ===== Tafelberge (WÜSTE) =====

/** Tafelberge: Breite als Anteil der Bildbreite, Höhe als Anteil der Bildhöhe. */
private val MESA_W = floatArrayOf(0.2f, 0.12f, 0.26f, 0.15f)
private val MESA_H = floatArrayOf(0.11f, 0.08f, 0.13f, 0.09f)

/**
 * Tafelberge in drei Stufen, jede Stufe mit heller Gesteinsschicht an der
 * Oberkante und Schattenseite rechts; davor eine Reihe flacher Dünen.
 */
private fun DrawScope.drawMesas(colors: List<Long>, time: Float, cell: Float) {
    val w = size.width
    val h = size.height
    val base = backdropBase(cell)
    val body = Color(colors[0])
    val shade = Color(colors[1])
    val strata = Color(colors[2])
    val spacing = w * 0.42f
    val drift = time * h * 0.003f
    val perLap = MESA_W.size
    var count = perLap
    while (count * spacing < w + spacing * 3f) count += perLap
    val total = count * spacing
    for (k in 0 until count) {
        val x = ((k * spacing - drift) % total + total) % total - spacing
        drawMesa(x.roundToInt().toFloat(), base, w * MESA_W[k % perLap], h * MESA_H[k % perLap], cell, body, shade, strata)
    }
    drawRidge(time * h * 0.007f, cell, base, Color(colors[3]), Color(colors[4])) { c ->
        h * (0.034f + 0.012f * sin(c * 0.09f) + 0.006f * sin(c * 0.21f + 1f))
    }
}

private fun DrawScope.drawMesa(
    cx: Float,
    base: Float,
    width: Float,
    height: Float,
    cell: Float,
    body: Color,
    shade: Color,
    strata: Color
) {
    val tiers = 3
    val hh = snap(height, cell)
    // Von unten nach oben: breiter Fuß, Mitte, Plateau.
    val tierH = floatArrayOf(snap(hh * 0.3f, cell), snap(hh * 0.3f, cell), 0f)
    tierH[2] = hh - tierH[0] - tierH[1]
    var bottom = base
    for (t in 0 until tiers) {
        val half = snap(width / 2f, cell) + cell * 2f * (tiers - 1 - t)
        val top = bottom - tierH[t]
        val left = cx - half
        val full = half * 2f
        val lit = snap(full * 0.7f, cell)
        drawRect(body, Offset(left, top), Size(lit, tierH[t]))
        drawRect(shade, Offset(left + lit, top), Size(full - lit, tierH[t]))
        drawRect(strata, Offset(left, top), Size(lit, cell))
        // Eine zweite, dünnere Schicht mitten in der Stufe.
        if (tierH[t] >= cell * 5f) {
            drawRect(strata, Offset(left + cell * 2f, top + snap(tierH[t] * 0.55f, cell)), Size(lit - cell * 4f, cell))
        }
        bottom = top
    }
}

// ===== Horizont (MEER) =====

/**
 * Das offene Meer bis zum Horizont: ein Wasserband über dem Boden mit
 * glitzernden Strichen, dahinter zieht sehr langsam eine Insel mit
 * Leuchtturm vorbei. Nachts dreht der Leuchtturm sein Licht.
 */
private fun DrawScope.drawHorizon(colors: List<Long>, raw: List<Long>, time: Float, cell: Float, night: Float) {
    val w = size.width
    val h = size.height
    val ground = ScenePaint.groundY(h)
    val horizon = snap(ground - h * 0.04f, cell)

    // Ferne Insel mit Leuchtturm, halb eingetaucht in den Horizont.
    val lap = w * 1.7f
    val drift = time * h * 0.0035f
    val ix = (((w * 0.72f - drift) % lap + lap) % lap - w * 0.35f).roundToInt().toFloat()
    drawFarIsland(ix, horizon, cell, colors, raw, time, night)

    val sea = Color(colors[0])
    val glint = Color(colors[1])
    drawRect(sea, Offset(0f, horizon), Size(w, ground - horizon + cell * 2f))
    drawRect(glint, Offset(0f, horizon), Size(w, cell), alpha = 0.45f)
    // Glitzern: kurze helle Striche, die langsam mitziehen und aufblitzen.
    val rows = ((ground - horizon) / cell).toInt() - 2
    if (rows > 0) {
        val gDrift = time * h * 0.006f
        val fade = 1f - night * 0.5f
        for (i in 0 until 16) {
            val gx = ((hash(i * 3 + 11) * w - gDrift) % w + w) % w
            val gy = horizon + cell * (2 + (hash(i * 3 + 12) * rows).toInt())
            val len = cell * (2 + (hash(i * 3 + 13) * 3f).toInt())
            val a = (0.2f + 0.6f * sin(time * (1.3f + hash(i + 77) * 1.5f) + i * 1.7f)).coerceIn(0f, 1f)
            drawRect(glint, Offset(gx.roundToInt().toFloat(), gy), Size(len, cell), alpha = a * fade)
        }
    }
}

private fun DrawScope.drawFarIsland(
    x: Float,
    horizon: Float,
    cell: Float,
    colors: List<Long>,
    raw: List<Long>,
    time: Float,
    night: Float
) {
    val land = Color(colors[2])
    val landShade = Color(colors[3])
    // Drei Stufen, die unterste reicht unter die Horizontlinie.
    val widths = intArrayOf(24, 17, 10)
    var top = horizon + cell
    for (t in widths.indices) {
        top -= cell * 2f
        val half = widths[t] / 2 * cell
        drawRect(land, Offset(x - half, top), Size(half * 1.4f, cell * 2f))
        drawRect(landShade, Offset(x - half + half * 1.4f, top), Size(half * 0.6f, cell * 2f))
    }
    // Leuchtturm rechts auf dem Plateau: rot-weiß gestreift, oben die Lampe.
    val white = Color(colors[4])
    val red = Color(colors[5])
    val tx = x + cell
    val towerH = 10
    for (r in 0 until towerH) {
        drawRect(if ((r / 2) % 2 == 0) white else red, Offset(tx, top - cell * (r + 1)), Size(cell * 3f, cell))
    }
    val lampY = top - cell * (towerH + 2)
    val lampRaw = Color(raw[6])
    drawRect(Color(colors[6]), Offset(tx, lampY), Size(cell * 3f, cell * 2f))
    drawRect(red, Offset(tx - cell, lampY - cell), Size(cell * 5f, cell))
    drawRect(red, Offset(tx + cell, lampY - cell * 2f), Size(cell, cell))
    // Nachts: Die Lampe leuchtet und ihr Strahl streicht über das Meer.
    if (night > 0.25f) {
        val a = ((night - 0.25f) / 0.5f).coerceIn(0f, 1f)
        drawRect(lampRaw, Offset(tx, lampY), Size(cell * 3f, cell * 2f), alpha = a)
        drawRect(lampRaw, Offset(tx - cell * 2f, lampY - cell), Size(cell * 7f, cell * 4f), alpha = a * 0.25f)
        val sweep = sin(time * 1.1f)
        val len = size.width * 0.16f * abs(sweep)
        val bx = if (sweep > 0f) tx + cell * 3f else tx - len
        drawRect(lampRaw, Offset(bx.roundToInt().toFloat(), lampY), Size(len, cell * 2f), alpha = a * 0.35f)
        drawRect(lampRaw, Offset(bx.roundToInt().toFloat(), lampY - cell), Size(len, cell * 4f), alpha = a * 0.12f)
    }
}

// ===== Skyline (STADT) =====

/**
 * Zwei Reihen Hochhäuser als Silhouette. Die hintere steht im Dunst und
 * zeigt am Tag keine Fenster; die vordere trägt Fensterbänder, auf manchen
 * Dächern eine Antenne. Nachts gehen Fenster an — je dunkler, desto mehr —
 * und auf den Antennen blinkt ein rotes Licht.
 */
private fun DrawScope.drawSkyline(colors: List<Long>, raw: List<Long>, time: Float, cell: Float, night: Float) {
    val h = size.height
    val base = backdropBase(cell)
    val lit = Color(raw[4])
    val share = litShare(night)
    drawTowerRow(
        time * h * 0.004f, cell * 11f, cell, base, 0.05f, 0.06f, seed = 1,
        body = Color(colors[0]), shade = Color(colors[0]), windowDay = null,
        lit = lit, share = share * 0.6f, beacon = null, time = time
    )
    drawTowerRow(
        time * h * 0.007f, cell * 16f, cell, base, 0.07f, 0.065f, seed = 2,
        body = Color(colors[2]), shade = Color(colors[3]), windowDay = Color(colors[1]),
        lit = lit, share = share, beacon = if (night > 0.25f) Color(raw[5]) else null, time = time
    )
}

/** Anteil der Fenster, die in dieser Nachtstärke leuchten. */
internal fun litShare(night: Float): Float = ((night - 0.2f) / 0.6f).coerceIn(0f, 1f) * 0.6f

private fun DrawScope.drawTowerRow(
    drift: Float,
    slot: Float,
    cell: Float,
    base: Float,
    minH: Float,
    rangeH: Float,
    seed: Int,
    body: Color,
    shade: Color,
    windowDay: Color?,
    lit: Color,
    share: Float,
    beacon: Color?,
    time: Float
) {
    val h = size.height
    val shift = floor(drift / slot).toInt()
    val off = (drift - shift * slot).roundToInt().toFloat()
    val slots = (size.width / slot).toInt() + 2
    for (i in -1..slots) {
        val n = i + shift
        val bw = snap(slot * (0.6f + 0.35f * hash(n * 5 + seed)), cell)
        val bh = snap(h * (minH + rangeH * hash(n * 5 + seed + 101)), cell)
        val x = i * slot - off + snap((slot - bw) * hash(n * 5 + seed + 202), cell)
        val top = base - bh
        val lip = snap(bw * 0.3f, cell)
        drawRect(body, Offset(x, top), Size(bw - lip, bh))
        drawRect(shade, Offset(x + bw - lip, top), Size(lip, bh))
        // Antenne auf jedem dritten Dach, nachts mit Blinklicht.
        if (hash(n * 5 + seed + 303) > 0.66f) {
            val ax = x + snap(bw * 0.4f, cell)
            drawRect(shade, Offset(ax, top - cell * 5f), Size(cell, cell * 5f))
            if (beacon != null && sin(time * 3f + n) > 0.3f) {
                drawRect(beacon, Offset(ax, top - cell * 6f), Size(cell, cell))
                drawRect(beacon, Offset(ax - cell, top - cell * 7f), Size(cell * 3f, cell * 3f), alpha = 0.3f)
            }
        }
        // Am Tag: senkrechte Fensterbänder. Nachts: einzelne helle Fenster.
        if (windowDay != null && share < 0.3f) {
            var wx = x + cell * 2f
            while (wx < x + bw - lip - cell) {
                drawRect(windowDay, Offset(wx, top + cell * 3f), Size(cell, bh - cell * 5f))
                wx += cell * 3f
            }
        }
        if (share > 0f) {
            var row = 0
            var wy = top + cell * 3f
            while (wy < base - cell * 4f) {
                var col = 0
                var wx = x + cell * 2f
                while (wx < x + bw - cell * 2f) {
                    if (hash(n * 977 + row * 31 + col * 7 + seed) < share) {
                        drawRect(lit, Offset(wx, wy), Size(cell, cell * 2f))
                    }
                    wx += cell * 3f
                    col++
                }
                wy += cell * 4f
                row++
            }
        }
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
        cell = cell, core = Color(colors[3]), arm = colors[4], tilt = 0.5f
    )
    drawGalaxy(
        cx = w * 0.76f, cy = h * 0.84f, radius = w * 0.2f, turn = -time * 0.045f + 1.3f,
        cell = cell, core = Color(colors[3]), arm = colors[5], tilt = 0.42f
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
        // Waagrecht auf ganze Pixel, nicht auf ganze Zellen: Die Sterne
        // driften langsam und sprangen sonst alle zwei Sekunden eine Zelle.
        val px = x.roundToInt().toFloat()
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
 * Die Arme werden nach außen blasser — in drei deckenden Stufen zum
 * festen Galaxienhimmel hin ([BevelPaint.galaxyTone]), nicht
 * durchsichtig: Rosé halb durchsichtig über Dunkelblau wurde ein
 * schmutziges Grau-Lila.
 */
private fun DrawScope.drawGalaxy(
    cx: Float,
    cy: Float,
    radius: Float,
    turn: Float,
    cell: Float,
    core: Color,
    arm: Long,
    tilt: Float
) {
    val block = cell * 2f
    val steps = 40
    val dust = Color(BevelPaint.galaxyDust(arm))
    for (a in 0 until 2) {
        for (k in 0 until steps) {
            val t = k / (steps - 1f)
            val theta = turn + a * PI.toFloat() + t * 4.6f
            val r = radius * (0.1f + t * 0.9f)
            val x = snap(cx + cos(theta) * r, block)
            val y = snap(cy + sin(theta) * r * tilt, block)
            drawRect(Color(BevelPaint.galaxyTone(arm, t)), Offset(x, y), Size(block, block))
            // Staub und Sterne am Arm: versetzte Einzelzellen, nur auf der
            // inneren Hälfte und deckend. Außen wirkten sie wie Bildrauschen.
            if (k % 3 == 0 && t < 0.5f) {
                drawRect(dust, Offset(x + block, y - cell), Size(cell, cell))
            }
        }
    }
    // Kern: helles Kreuz.
    val kx = snap(cx, cell)
    val ky = snap(cy, cell)
    // Schimmer als Kreuz, nicht als Kasten: Ein Rechteck mit Deckkraft
    // läse sich als Fenster im Himmel. Deckend in der mittleren Armstufe.
    val glow = Color(BevelPaint.galaxyGlow(arm))
    drawRect(glow, Offset(kx - block * 2f, ky - cell), Size(block * 5f, block * 2f))
    drawRect(glow, Offset(kx - cell, ky - block * 1.5f), Size(block * 2f, block * 4f))
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
        val x = (headX + back).roundToInt().toFloat()
        val y = (headY - back * 0.45f).roundToInt().toFloat()
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

/** Derselbe feste Hash für die übrigen Ebenen der Welt (Life.kt). */
internal fun worldHash(n: Int): Float = hash(n)
