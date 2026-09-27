package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.robinrehbein.punkt.game.BevelPaint

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
 * Blockige Retro-Wolke aus gestapelten Rechtecken. Die Farbe kommt
 * seit den Kulissen von außen (ScenePaint) — der Standard ist die Wolke
 * der WIESE, damit Aufrufer ohne Kulisse unverändert bleiben.
 *
 * Zwei Formen ([shape]): 0 ist die Bestandswolke aus drei Stufen, 1 eine
 * breitere mit zwei Buckeln. [u] ist die Einheit der Form (Standard zwei
 * Zellen; eine kleine, ferne Wolke nimmt eine).
 *
 * Bevel-Look ohne Kontur: Licht oben links in Weiß, an der Unterkante
 * und rechts am Sockel ein kühler Schatten ([BevelPaint.cloudShade]).
 * Die Kanten sind eine [cell] breit, nicht die doppelte Wolkenzelle —
 * breiter wirkte die Wolke wie ein Kasten mit Rahmen.
 */
fun DrawScope.drawCloud(
    x: Float,
    y: Float,
    cell: Float,
    color: Color = CloudColor,
    shape: Int = 0,
    u: Float = cell * 2f,
    light: Color = Color.White
) {
    val parts = CLOUD_PARTS[shape]
    for (p in parts.indices step 4) {
        drawRect(color = color, topLeft = Offset(x + parts[p] * u, y + parts[p + 1] * u), size = Size(parts[p + 2] * u, parts[p + 3] * u))
    }
    // Erst der Schatten unten und rechts am Sockel (dem ersten Teil),
    // dann das Licht auf den Oberkanten und links am Sockel.
    val shade = Color(BevelPaint.cloudShade(color.toArgbLong()))
    val bx = x + parts[0] * u
    val by = y + parts[1] * u
    val bw = parts[2] * u
    val bh = parts[3] * u
    drawRect(color = shade, topLeft = Offset(bx, by + bh - cell), size = Size(bw, cell))
    drawRect(color = shade, topLeft = Offset(bx + bw - cell, by), size = Size(cell, bh))
    val lights = CLOUD_LIGHTS[shape]
    for (p in lights.indices step 3) {
        drawRect(color = light, topLeft = Offset(x + lights[p] * u, y + lights[p + 1] * u), size = Size(lights[p + 2] * u, cell))
    }
    drawRect(color = light, topLeft = Offset(bx, by), size = Size(cell, bh - cell))
}

/**
 * Die Teile der Wolkenformen in Einheiten: je x, y, Breite, Höhe; das
 * erste Teil ist der Sockel. Form 0 ist Pixel für Pixel die Bestandswolke.
 */
private val CLOUD_PARTS: Array<FloatArray> = arrayOf(
    floatArrayOf(0f, 2f, 14f, 3f, 2f, 0f, 7f, 2f, 4f, -1.5f, 4f, 1.5f),
    floatArrayOf(0f, 2f, 16f, 2.5f, 1.5f, 0.5f, 6f, 1.5f, 7f, -0.5f, 7f, 2.5f, 9f, -1.5f, 3f, 1f)
)

/** Lichtkanten der Formen: je x, y, Breite (Höhe ist eine Zelle). */
private val CLOUD_LIGHTS: Array<FloatArray> = arrayOf(
    floatArrayOf(0f, 2f, 2f, 2f, 0f, 2f, 4f, -1.5f, 4f),
    floatArrayOf(0f, 2f, 1.5f, 1.5f, 0.5f, 5.5f, 7f, -0.5f, 2f, 9f, -1.5f, 3f)
)
