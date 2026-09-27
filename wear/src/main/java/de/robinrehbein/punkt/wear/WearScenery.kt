package de.robinrehbein.punkt.wear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import de.robinrehbein.punkt.game.Backdrop
import de.robinrehbein.punkt.game.BackdropKind
import de.robinrehbein.punkt.game.BevelPaint
import de.robinrehbein.punkt.game.Ground
import de.robinrehbein.punkt.game.Scene
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Die Kulisse der gewählten Welt auf dem runden Display: Hintergrund
 * (Gebirge, Sternenhimmel), Wolken und Bodenstreifen — alles aus
 * [de.robinrehbein.punkt.game.ScenePaint], also dieselben Farben wie am
 * Telefon.
 *
 * Was die Uhr anders macht als das Telefon, und warum:
 *
 * - **Die Bahn liegt in der Mitte und füllt das Bild.** Am Telefon steht
 *   die Kulisse unter dem Ring, auf der Uhr bleibt dafür nur der schmale
 *   Rand ringsum. Gebirge und Wolken werden deshalb aus dem Ring
 *   ausgeschnitten ([trackOuter]): Die Bahn liegt überall vor ruhigem
 *   Himmel, wie es die Kulissen-Regel verlangt (README, „Kulissen“).
 *   Wolken ziehen dafür oben durch den schmalen Streifen über dem Ring.
 * - **Der Boden beginnt unter der Bahn, nicht bei 88 %.** Auf der Uhr
 *   reicht die Bahn bis 88 % hinunter; an der Telefon-Bodenkante läge die
 *   Grasnarbe der WIESE — exakt Zonengrün — auf den Bahnblöcken. Der
 *   Streifen setzt deshalb erst unter den Blöcken an ([wearGroundTop]);
 *   nur der Vogel streift ihn ganz unten, wie er am Telefon die
 *   Requisiten streift.
 * - **Keine Requisiten.** Bäume, Kakteen, Hochhäuser brauchen am Telefon
 *   gut 500 Zeilen Zeichencode je Form; auf dem Rand der Uhr blieben
 *   davon ein paar Pixel übrig. Die Welt erkennt man hier an Himmel,
 *   Boden, Wolken, Gebirge und Sternen.
 * - **Bevel nur, wo Platz ist.** Die Wolken bekommen wie am Telefon eine
 *   weiße Oberkante und eine kühlere Unterkante, der Boden eine helle
 *   Oberkante auf der Narbe (docs/bevel-look.md, Abschnitt 0 Punkte 8
 *   und 12). Die Bodenmuster der Welten (Dünen, Wellen, Felsmauer,
 *   Bordstein) bleiben dem Telefon vorbehalten — keine neuen Formen auf
 *   dem schmalen Rand. Gebirge und Sterne bleiben unberührt.
 */

/**
 * Zellgröße der Kulisse in Bildpunkten: das Pixel-Raster, in dem Boden,
 * Wolken und Berge liegen. Am Telefon `floor(h / 220)`; auf der Uhr wäre
 * das 2 und alles Staub — der Teiler ist deshalb kleiner.
 */
internal fun wearCell(d: Float): Float = floor(d / 160f).coerceAtLeast(2f)

/**
 * Oberkante des Bodens: zwei Zellen unter der Außenkante der Bahnblöcke
 * ([trackOuter] = Bahnradius plus halber Zonenblock), im Zellraster.
 */
internal fun wearGroundTop(cy: Float, trackOuter: Float, cell: Float): Float =
    floor((cy + trackOuter) / cell) * cell + 2f * cell

/**
 * Hinter der Bahn: Hintergrund, Wolken und Boden der Welt. [time] ist die
 * Laufuhr des Spiels (TimingGame.elapsed), an der am Telefon Wolken und
 * Berge driften. [trackOuter] ist der Radius, ab dem außen kein Bahnblock
 * mehr liegt (Bahnradius plus halber Zonenblock).
 */
internal fun DrawScope.drawWearScenery(
    scene: Scene,
    time: Float,
    cx: Float,
    cy: Float,
    trackOuter: Float,
    cell: Float
) {
    val groundTop = wearGroundTop(cy, trackOuter, cell)
    // Sterne dürfen überall stehen — sie sind einzelne Pixel und liegen
    // am Telefon ebenfalls hinter der Bahn.
    if (scene.backdrop?.kind == BackdropKind.STERNENHIMMEL) {
        drawWearStarfield(scene.backdrop!!.colors, time, cell)
    }
    val ring = Path().apply {
        addOval(Rect(center = Offset(cx, cy), radius = trackOuter + cell))
    }
    clipPath(ring, clipOp = ClipOp.Difference) {
        if (scene.backdrop?.kind == BackdropKind.GEBIRGE) {
            drawWearMountains(scene.backdrop!!, time, groundTop, cell)
        }
        scene.cloud?.let { cloud ->
            // Zwei Wolken im Streifen über dem Ring, in derselben Drift
            // wie am Telefon; sie wickeln rechts wieder ein.
            val w = size.width
            val drift = time * size.height * 0.01f
            val lap = w * 1.4f
            val y = floor((cy - trackOuter) * 0.45f / cell) * cell
            for (x0 in listOf(w * 0.3f, w * 1.0f)) {
                val x = ((x0 - drift) % lap + lap) % lap - w * 0.2f
                drawWearCloud(x, y, cell, Color(cloud))
            }
        }
    }
    scene.ground?.let { drawWearGround(it, groundTop, cell) }
}

/**
 * Eine Pixel-Wolke, dieselbe Form wie drawCloud am Telefon — und
 * dieselbe Kante: Unterkante und rechter Rand in [BevelPaint.cloudShade],
 * darüber die Oberkanten der drei Stufen und der linke Rand in Weiß.
 * Ohne Outline, wie bisher; eine Kontur machte aus der Wolke einen Stein.
 */
private fun DrawScope.drawWearCloud(x: Float, y: Float, cell: Float, color: Color) {
    val u = cell
    drawRect(color = color, topLeft = Offset(x, y + u * 2), size = Size(u * 14, u * 3))
    drawRect(color = color, topLeft = Offset(x + u * 2, y), size = Size(u * 7, u * 2))
    drawRect(color = color, topLeft = Offset(x + u * 4, y - u * 1.5f), size = Size(u * 4, u * 1.5f))
    val shade = Color(BevelPaint.cloudShade(color.toArgbLong()))
    drawRect(color = shade, topLeft = Offset(x, y + u * 5 - cell), size = Size(u * 14, cell))
    drawRect(color = shade, topLeft = Offset(x + u * 14 - cell, y + u * 2), size = Size(cell, u * 3))
    drawRect(color = Color.White, topLeft = Offset(x, y + u * 2), size = Size(u * 2, cell))
    drawRect(color = Color.White, topLeft = Offset(x + u * 2, y), size = Size(u * 2, cell))
    drawRect(color = Color.White, topLeft = Offset(x + u * 4, y - u * 1.5f), size = Size(u * 4, cell))
    drawRect(color = Color.White, topLeft = Offset(x, y + u * 2), size = Size(cell, u * 3 - cell))
}

/**
 * Der Bodenstreifen wie drawGroundStrip am Telefon: Fläche mit dunklerem
 * Band, darüber die Grasnarbe mit hellen Zähnen und eine Konturlinie —
 * nur eben ab [top] statt ab der Telefon-Bodenkante.
 *
 * Die oberste Zeile der Narbe ist die helle Kante aus
 * [BevelPaint.light], je über dem Zahn oder der Lücke darunter: Licht
 * von oben, wie auf den Blöcken. Mehr Bevel gibt es hier nicht — die
 * Narbe ist drei Zellen hoch, eine dunkle Unterkante ließe von der
 * Grasfarbe nur eine Zeile übrig.
 */
private fun DrawScope.drawWearGround(ground: Ground, top: Float, cell: Float) {
    val w = size.width
    val h = size.height
    if (top >= h) return
    drawRect(color = Color(ground.sand), topLeft = Offset(0f, top), size = Size(w, h - top))
    drawRect(color = Color(ground.sandShade), topLeft = Offset(0f, top + cell * 6), size = Size(w, cell * 2))
    drawRect(color = Color(ground.turfDark), topLeft = Offset(0f, top), size = Size(w, cell * 3))
    val tooth = cell * 3f
    val turfLight = Color(ground.turfLight)
    drawRect(color = Color(BevelPaint.light(ground.turfDark)), topLeft = Offset(0f, top), size = Size(w, cell))
    val toothEdge = Color(BevelPaint.light(ground.turfLight))
    var x = 0f
    while (x < w) {
        drawRect(color = turfLight, topLeft = Offset(x, top), size = Size(tooth, cell * 2))
        drawRect(color = toothEdge, topLeft = Offset(x, top), size = Size(tooth, cell))
        x += tooth * 2
    }
    drawRect(color = WearOutlineColor, topLeft = Offset(0f, top - cell), size = Size(w, cell))
}

// ===== Gebirge (BERG) =====

/** Gipfelhöhen als Anteil der Bildhöhe, zyklisch — wie am Telefon. */
private val WEAR_FAR_PEAKS = listOf(0.15f, 0.11f, 0.13f, 0.10f)
private val WEAR_NEAR_PEAKS = listOf(0.09f, 0.115f, 0.075f)

/**
 * Zwei Ketten gestufter Berge auf dem Boden, die hintere heller. Am
 * Telefon bleiben die Gipfel unter der Bahn; hier schneidet der Aufrufer
 * das Ringband aus, übrig bleiben die Hänge links und rechts unten.
 */
private fun DrawScope.drawWearMountains(backdrop: Backdrop, time: Float, base: Float, cell: Float) {
    val c = backdrop.colors
    val w = size.width
    val h = size.height
    fun range(peaks: List<Float>, spacing: Float, drift: Float, body: Color, shade: Color, outline: Boolean) {
        var count = peaks.size
        while (count * spacing < w + spacing * 3f) count += peaks.size
        val total = count * spacing
        for (k in 0 until count) {
            val x = ((k * spacing - drift) % total + total) % total - spacing
            drawWearMountain(
                x, base, h * peaks[k % peaks.size], cell, body, shade,
                Color(c[4]), Color(c[5]), outline
            )
        }
    }
    range(WEAR_FAR_PEAKS, w * 0.36f, time * h * 0.003f, Color(c[0]), Color(c[1]), outline = false)
    range(WEAR_NEAR_PEAKS, w * 0.5f, time * h * 0.006f, Color(c[2]), Color(c[3]), outline = true)
}

/** Ein Berg als Treppe aus Stufen, Gipfel bei [cx], rechte Seite im Schatten. */
private fun DrawScope.drawWearMountain(
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
    fun snap(v: Float) = floor(v / cell) * cell
    if (outline) {
        for (r in 0 until rows) {
            val half = snap(halfBase * (rows - r) / rows)
            val top = base - (r + 1) * step
            drawRect(WearOutlineColor, Offset(cx - half - cell, top - cell), Size(half * 2f + cell * 2f, step + cell))
        }
    }
    for (r in 0 until rows) {
        val half = snap(halfBase * (rows - r) / rows)
        val top = base - (r + 1) * step
        drawRect(body, Offset(cx - half, top), Size(half, step))
        drawRect(shade, Offset(cx, top), Size(half, step))
        if (r >= rows - snowRows) {
            val snowHalf = if (r == rows - snowRows) snap(half * 0.55f) else half
            drawRect(snow, Offset(cx - snowHalf, top), Size(snowHalf, step))
            drawRect(snowShade, Offset(cx, top), Size(snowHalf, step))
        }
    }
}

// ===== Sternenhimmel (WELTRAUM) =====

/** Weniger Sterne als am Telefon (110): Die Uhr hat ein Zehntel der Fläche. */
private const val WEAR_STAR_COUNT = 48

/**
 * Funkelnde Sterne und ab und zu eine Sternschnuppe — Lage und Takt aus
 * einem festen Hash, wie am Telefon; ohne die beiden Galaxien, die dort
 * in Ecken stehen, die das runde Display abschneidet.
 */
private fun DrawScope.drawWearStarfield(colors: List<Long>, time: Float, cell: Float) {
    val w = size.width
    val h = size.height
    val tones = listOf(Color(colors[0]), Color(colors[1]), Color(colors[2]))
    val drift = time * h * 0.002f
    for (i in 0 until WEAR_STAR_COUNT) {
        val x = ((wearHash(i * 2 + 1) * w - drift) % w + w) % w
        val y = wearHash(i * 2 + 2) * h
        val kind = wearHash(i + 7919)
        val color = tones[(kind * 3f).toInt().coerceIn(0, 2)]
        val speed = 1.1f + wearHash(i + 104729) * 2.2f
        val twinkle = 0.7f + 0.3f * sin(time * speed + wearHash(i + 1299709) * 6.28f)
        // Waagrecht auf ganze Pixel statt auf ganze Zellen: Die Sterne
        // driften langsam und sprangen sonst alle paar Sekunden eine Zelle.
        val px = x.roundToInt().toFloat()
        val py = floor(y / cell) * cell
        if (kind > 0.9f) {
            drawRect(color, Offset(px - cell, py), Size(cell * 3f, cell), alpha = twinkle)
            drawRect(color, Offset(px, py - cell), Size(cell, cell * 3f), alpha = twinkle)
        } else {
            drawRect(color, Offset(px, py), Size(cell, cell), alpha = twinkle)
        }
    }
    // Sternschnuppe alle 7,5 s für 0,7 s, schräg nach unten links.
    val period = 7.5f
    val cycle = floor(time / period).toInt()
    val t = time - cycle * period
    if (t <= 0.7f) {
        val q = t / 0.7f
        val headX = w * (0.45f + wearHash(cycle + 31) * 0.4f) - w * 0.35f * q
        val headY = h * (0.12f + wearHash(cycle + 57) * 0.2f) + w * 0.35f * 0.45f * q
        for (k in 0 until 6) {
            val back = k * cell * 2f
            drawRect(
                tones[0],
                Offset((headX + back).roundToInt().toFloat(), (headY - back * 0.45f).roundToInt().toFloat()),
                Size(cell, cell),
                alpha = (1f - k / 6f) * (1f - q * 0.6f)
            )
        }
    }
}

/** Fester Hash 0..1 aus einer Ganzzahl (derselbe wie am Telefon). */
internal fun wearHash(n: Int): Float {
    var x = n * 0x9E3779B1.toInt()
    x = x xor (x ushr 16)
    x *= 0x85EBCA6B.toInt()
    x = x xor (x ushr 13)
    x *= 0xC2B2AE35.toInt()
    x = x xor (x ushr 16)
    return (x ushr 8) / 16_777_216f
}
