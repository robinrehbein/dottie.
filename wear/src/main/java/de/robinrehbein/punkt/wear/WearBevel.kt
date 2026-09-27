package de.robinrehbein.punkt.wear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import de.robinrehbein.punkt.game.BevelPaint
import kotlin.math.roundToInt

/**
 * Bevel-Look auf der Uhr (docs/bevel-look.md, Abschnitt 0 Punkt 12).
 *
 * :wear hängt bewusst nicht von :ui ab, deshalb steht hier eine eigene
 * kleine Fassung von `bevelRect` aus ui/.../world/Bevel.kt — mit derselben
 * Regel und denselben Farben aus [BevelPaint]. Gerechnet wird nur in
 * :core; hier wird nur gezeichnet.
 */

/** ARGB-Long einer Compose-Farbe, wie ihn [BevelPaint] erwartet. */
internal fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

/**
 * Kantenbreite in ganzen Bildpunkten, mindestens einer — wie das
 * Runden in drawZoneBlock am Telefon, damit die Kanten hart bleiben.
 */
internal fun wearBevelEdge(edge: Float): Float = edge.roundToInt().coerceAtLeast(1).toFloat()

/**
 * Bekommt eine Fläche von [width] × [height] eine Kante der Breite
 * [edge]? Erst ab drei Kantenbreiten je Richtung: darunter bliebe
 * zwischen Licht und Schatten keine Grundfarbe übrig, und die Fläche
 * sähe gestreift statt plastisch aus.
 */
internal fun wearBevelFits(width: Float, height: Float, edge: Float): Boolean {
    val e = wearBevelEdge(edge)
    return width >= e * 3f && height >= e * 3f
}

/**
 * Fläche mit Bevel: Grundfarbe, dann die dunkle Kante unten und rechts,
 * darüber die helle oben und links — so gewinnt an den Ecken oben rechts
 * und unten links das Licht, wie in drawZoneBlock. Zu kleine Flächen
 * ([wearBevelFits]) bleiben flach.
 */
internal fun DrawScope.wearBevelRect(
    color: Color,
    topLeft: Offset,
    size: Size,
    edge: Float,
    light: Color = Color(BevelPaint.light(color.toArgbLong())),
    dark: Color = Color(BevelPaint.dark(color.toArgbLong())),
    alpha: Float = 1f
) {
    drawRect(color, topLeft, size, alpha = alpha)
    if (!wearBevelFits(size.width, size.height, edge)) return
    val e = wearBevelEdge(edge)
    drawRect(dark, Offset(topLeft.x, topLeft.y + size.height - e), Size(size.width, e), alpha = alpha)
    drawRect(dark, Offset(topLeft.x + size.width - e, topLeft.y), Size(e, size.height), alpha = alpha)
    drawRect(light, topLeft, Size(size.width - e, e), alpha = alpha)
    drawRect(light, topLeft, Size(e, size.height - e), alpha = alpha)
}

/**
 * Die Lichtkante einer Wolkenfläche, wie `drawCloud` am Telefon: oben
 * über die ganze Breite, links bis über die Unterkante — dort bleibt der
 * Schatten stehen, den der Aufrufer vorher gezeichnet hat. Für Flächen,
 * die sich zu einem Band überlappen (Nebelband): Erst alle Schatten,
 * dann alle Lichtkanten, dann das Innere darüber.
 */
internal fun DrawScope.wearCloudLight(light: Color, topLeft: Offset, size: Size, edge: Float) {
    if (!wearBevelFits(size.width, size.height, edge)) return
    val e = wearBevelEdge(edge)
    drawRect(light, topLeft, Size(size.width, e))
    drawRect(light, topLeft, Size(e, size.height - e))
}
