package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import de.robinrehbein.punkt.game.BevelPaint
import kotlin.math.roundToInt

// Die zwei Zeichenmittel des Bevel-Looks (docs/bevel-look.md). Sie
// rechnen keine Farben: Die Kanten kommen aus BevelPaint in :core, damit
// Telefon, iOS und Uhr dieselben Töne treffen. Hier steht nur, WO eine
// Kante liegt.

/** Compose-Farbe als ARGB-Long, wie :core sie erwartet. */
internal fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

/**
 * Fläche mit Bevel: Grundfarbe, dann die dunkle Kante unten und rechts,
 * darüber die helle oben und links — an den Ecken oben rechts und unten
 * links gewinnt damit das Licht, wie in drawZoneBlock.
 *
 * [edge] ist die Kantenbreite, auf ganze Pixel gerundet. Flächen unter
 * drei Kantenbreiten bleiben flach: Dort bliebe zwischen Licht und
 * Schatten keine Grundfarbe übrig, und die Form läse sich als Streifen.
 */
internal fun DrawScope.bevelRect(
    color: Color,
    topLeft: Offset,
    size: Size,
    edge: Float,
    light: Color = Color(BevelPaint.light(color.toArgbLong())),
    dark: Color = Color(BevelPaint.dark(color.toArgbLong())),
    alpha: Float = 1f
) {
    drawRect(color, topLeft, size, alpha = alpha)
    val e = edge.roundToInt().coerceAtLeast(1).toFloat()
    if (size.width < e * 3f || size.height < e * 3f) return
    drawRect(dark, Offset(topLeft.x, topLeft.y + size.height - e), Size(size.width, e), alpha = alpha)
    drawRect(dark, Offset(topLeft.x + size.width - e, topLeft.y), Size(e, size.height), alpha = alpha)
    drawRect(light, topLeft, Size(size.width - e, e), alpha = alpha)
    drawRect(light, topLeft, Size(e, size.height - e), alpha = alpha)
}

/**
 * Pixel-Maske zeichnen, wie TrapPaint.MINE oder PropSprites: eine Zelle
 * der Größe [u] pro Zeichen, (x, y) ist die Ecke oben links. Zeichen, die
 * nicht in [pal] stehen (mindestens '.'), bleiben frei.
 */
internal fun DrawScope.pixelMap(x: Float, y: Float, u: Float, rows: List<String>, pal: Map<Char, Color>) {
    for (r in rows.indices) {
        val row = rows[r]
        for (k in row.indices) {
            val col = pal[row[k]] ?: continue
            drawRect(col, Offset(x + k * u, y + r * u), Size(u, u))
        }
    }
}
