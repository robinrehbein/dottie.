package de.robinrehbein.punkt.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.robinrehbein.punkt.game.BevelPaint
import de.robinrehbein.punkt.ui.world.toArgbLong
import kotlin.math.roundToInt

// Die Bevel-Kante der Menü-Flächen (docs/bevel-look.md, Abschnitt 0):
// Knöpfe, Taster, Schalter-Knebel und Panels. Dieselbe Regel wie in der
// Welt — erst die dunkle Kante unten und rechts, dann die helle oben und
// links darüber — und dieselben Töne aus BevelPaint. Nur die Fläche ist
// hier kein Rechteck im Bahnraster, sondern die Innenfläche eines
// Pixelrahmens.

/**
 * Eine Stufe des Menü-Pixelrasters: so breit wie die Bevel-Kante des
 * Eckknopfs ([CornerButton], halber 3-dp-Rand). Alle Menü-Flächen nehmen
 * diese eine Breite, damit Knopf, Taster und Panel nebeneinander gleich
 * geschliffen aussehen.
 */
internal val UI_BEVEL_EDGE: Dp = 1.5.dp

/**
 * Nur die Kanten einer Fläche bei [topLeft] mit [size] in der Grundfarbe
 * [base] — die Fläche selbst hat der Aufrufer schon gezeichnet.
 *
 * [pressed] kehrt das Licht um: dunkel oben und links, hell unten und
 * rechts. So liest sich ein gedrückter Knopf als eingedrückt, ohne dass
 * sich an seinem Einsinken etwas ändert.
 */
internal fun DrawScope.drawFaceBevel(
    base: Color,
    topLeft: Offset,
    size: Size,
    edge: Float,
    pressed: Boolean = false
) = drawBandBevel(
    base = base,
    bands = listOf(Rect(topLeft, size)),
    edge = edge,
    pressed = pressed
)

/**
 * Kanten für eine Fläche aus übereinander liegenden Bändern, etwa die
 * Innenfläche des gestuften [PixelButton]-Rands: oben und unten
 * schmaler, in der Mitte breiter. Jedes Band bekommt links Licht und
 * rechts Schatten; oben und unten nur dort, wo kein Nachbarband
 * anschließt. So läuft die Kante die Treppe des Rahmens mit, statt unter
 * ihm zu verschwinden.
 *
 * Wie [de.robinrehbein.punkt.ui.world.bevelRect]: Die helle Kante endet
 * eine Stufe vor der dunklen, die Ecken oben rechts und unten links
 * bleiben dunkel. Unter drei Stufen Breite oder Höhe bleibt die Fläche
 * flach.
 */
internal fun DrawScope.drawBandBevel(
    base: Color,
    bands: List<Rect>,
    edge: Float,
    pressed: Boolean = false
) {
    if (bands.isEmpty()) return
    val e = edge.roundToInt().coerceAtLeast(1).toFloat()
    val hoehe = bands.last().bottom - bands.first().top
    val breite = bands.maxOf { it.width }
    if (hoehe < e * 3f || breite < e * 3f) return
    val light = Color(BevelPaint.light(base.toArgbLong()))
    val dark = Color(BevelPaint.dark(base.toArgbLong()))
    // Gedrückt tauschen nur die Farben, die Reihenfolge bleibt: oben und
    // links liegt zuletzt oben.
    val obenLinks = if (pressed) dark else light
    val untenRechts = if (pressed) light else dark

    fun strip(c: Color, x0: Float, y0: Float, x1: Float, y1: Float) {
        if (x1 > x0 && y1 > y0) drawRect(c, Offset(x0, y0), Size(x1 - x0, y1 - y0))
    }

    /** Die Teile von [x0]..[x1], die [nachbar] nicht überdeckt. */
    fun frei(x0: Float, x1: Float, nachbar: Rect?): List<Pair<Float, Float>> {
        if (nachbar == null) return listOf(x0 to x1)
        return listOf(x0 to minOf(x1, nachbar.left), maxOf(x0, nachbar.right) to x1)
            .filter { (a, b) -> b > a }
    }

    // Erst der Schatten: rechts ganz, unten, wo nichts anschließt.
    bands.forEachIndexed { i, b ->
        strip(untenRechts, b.right - e, b.top, b.right, b.bottom)
        for ((a, z) in frei(b.left, b.right, bands.getOrNull(i + 1))) {
            strip(untenRechts, a, b.bottom - e, z, b.bottom)
        }
    }
    // Dann das Licht darüber: links, oben, wo nichts anschließt.
    bands.forEachIndexed { i, b ->
        val unten = bands.getOrNull(i + 1)
        val eckeUnten = unten == null || unten.left > b.left
        strip(obenLinks, b.left, b.top, b.left + e, if (eckeUnten) b.bottom - e else b.bottom)
        for ((a, z) in frei(b.left, b.right, bands.getOrNull(i - 1))) {
            strip(obenLinks, a, b.top, if (z >= b.right) z - e else z, b.top + e)
        }
    }
}
