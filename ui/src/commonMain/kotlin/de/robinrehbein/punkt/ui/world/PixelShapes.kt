package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Die Bausteine des Pixel-Looks: ein blockiger Kreis und eine Wolke.
 *
 * Sie sind der kleinste gemeinsame Nenner beider Oberflaechen — die
 * Spielwelt zeichnet damit den Vogel, die Overlays damit die Muenzen und
 * Vorschaukacheln.
 */

const val GRID = 13f

/**
 * Zeichnet einen blockigen "Pixel"-Kreis aus Rasterzellen. Die Füllfarbe
 * kommt pro Feld aus [cell] — so zeichnet dieselbe Routine einfarbige,
 * gemusterte und animierte Skins (siehe SkinPaint in :core).
 */
fun DrawScope.drawPixelCircle(
    outline: Color,
    centerX: Float,
    centerY: Float,
    radius: Float,
    alpha: Float = 1f,
    cell: (col: Int, row: Int) -> Color
) {
    val n = GRID.toInt()
    val u = (radius * 2f) / GRID
    val mid = (GRID - 1f) / 2f
    val rr = GRID / 2f - 0.25f

    for (row in 0 until n) {
        for (col in 0 until n) {
            val dx = col - mid
            val dy = row - mid
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            if (dist <= rr) {
                val cellColor = if (dist > rr - 1.1f) outline else cell(col, row)
                drawRect(
                    color = cellColor,
                    topLeft = Offset(centerX - radius + col * u, centerY - radius + row * u),
                    size = Size(u + 0.5f, u + 0.5f),
                    alpha = alpha
                )
            }
        }
    }
}

/** Einfarbige Variante mit Schattenseite — für Münzen und Deko. */
fun DrawScope.drawPixelCircle(
    color: Color,
    outline: Color,
    centerX: Float,
    centerY: Float,
    radius: Float,
    shade: Color = color
) {
    drawPixelCircle(outline, centerX, centerY, radius) { col, row ->
        if (col + row > GRID * 1.15f) shade else color
    }
}

/**
 * Blockige Retro-Wolke aus drei gestapelten Rechtecken. Die Farbe kommt
 * seit den Kulissen von außen (ScenePaint) — der Standard ist die Wolke
 * der WIESE, damit Aufrufer ohne Kulisse unverändert bleiben.
 */
fun DrawScope.drawCloud(x: Float, y: Float, cell: Float, color: Color = CloudColor) {
    val u = cell * 2f
    drawRect(color = color, topLeft = Offset(x, y + u * 2), size = Size(u * 14, u * 3))
    drawRect(color = color, topLeft = Offset(x + u * 2, y), size = Size(u * 7, u * 2))
    drawRect(color = color, topLeft = Offset(x + u * 4, y - u * 1.5f), size = Size(u * 4, u * 1.5f))
}
