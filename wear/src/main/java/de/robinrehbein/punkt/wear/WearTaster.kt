package de.robinrehbein.punkt.wear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.robinrehbein.punkt.game.BevelPaint

/**
 * Die Bedienflächen der Uhr im Bevel-Look des Telefons (PixelButton,
 * FaceBevel und Panels in :ui): Sandfläche im Pixelrahmen, harter
 * Schatten unten rechts, die Kante aus [BevelPaint]. Gedrückt sinkt die
 * Fläche genau um den Schatten ein und das Licht kehrt sich um.
 *
 * Wie [wearBevelRect] eine eigene kleine Fassung, weil :wear nicht von
 * :ui abhängt. Die Maße sind auf die Uhr verkleinert, die Regel ist
 * dieselbe. Reine Zeichenfunktionen ohne Layout — die Knöpfe selbst
 * baut WearGameScreen.
 */

/** Grundfarbe aller Taster und Panels, wie PanelSand am Telefon. */
internal val WearPanelSand = Color(0xFFDED895)

/** Schrift und Symbole auf Sand, wie TextDark am Telefon. */
internal val WearTextDark = WearOutlineColor

/** Der Pixelschatten unter Tastern und Panels. */
internal val WearTasterShadow = WearOutlineColor.copy(alpha = 0.55f)

/**
 * Die Maße eines Tasters in Bildpunkten: Randstufe [border], Schatten
 * [shadow] (so weit sinkt er ein) und Bevel-Kante [edge].
 */
internal data class WearTasterMetrics(val border: Float, val shadow: Float, val edge: Float)

/**
 * Pixelrahmen mit einer Stufe an jeder Ecke: zwei überlappende Rechtecke
 * in [outline], die Ecke selbst bleibt frei — wie der gestufte Rand von
 * PixelButton, nur mit einer statt mehreren Stufen.
 */
internal fun DrawScope.drawWearPixelFrame(
    topLeft: Offset,
    size: Size,
    border: Float,
    outline: Color = WearOutlineColor
) {
    drawRect(outline, Offset(topLeft.x + border, topLeft.y), Size(size.width - 2 * border, size.height))
    drawRect(outline, Offset(topLeft.x, topLeft.y + border), Size(size.width, size.height - 2 * border))
}

/**
 * Die Fläche eines Tasters: Rahmen, Fläche in [face], Bevel-Kante. Ist
 * [pressed] gesetzt, kehrt sich das Licht um — dunkel oben und links —,
 * die Reihenfolge der Kanten bleibt wie in [wearBevelRect].
 */
internal fun DrawScope.drawWearTasterFace(
    topLeft: Offset,
    size: Size,
    m: WearTasterMetrics,
    face: Color = WearPanelSand,
    pressed: Boolean = false
) {
    drawWearPixelFrame(topLeft, size, m.border)
    val inner = Offset(topLeft.x + m.border, topLeft.y + m.border)
    val innerSize = Size(size.width - 2 * m.border, size.height - 2 * m.border)
    if (innerSize.width <= 0f || innerSize.height <= 0f) return
    val light = Color(BevelPaint.light(face.toArgbLong()))
    val dark = Color(BevelPaint.dark(face.toArgbLong()))
    wearBevelRect(
        face,
        inner,
        innerSize,
        m.edge,
        light = if (pressed) dark else light,
        dark = if (pressed) light else dark
    )
}

/**
 * Ein Taster auf der ganzen Zeichenfläche: ungedrückt oben links mit dem
 * Schatten darunter, gedrückt genau auf dem Schatten — wie
 * drawPressedFrame am Telefon. Zu kleine Flächen zeichnen nichts.
 */
internal fun DrawScope.drawWearTaster(
    m: WearTasterMetrics,
    pressed: Boolean,
    face: Color = WearPanelSand
) {
    val w = size.width - m.shadow
    val h = size.height - m.shadow
    if (w <= 2 * m.border || h <= 2 * m.border) return
    if (!pressed) drawWearPixelFrame(Offset(m.shadow, m.shadow), Size(w, h), m.border, WearTasterShadow)
    val press = if (pressed) m.shadow else 0f
    drawWearTasterFace(Offset(press, press), Size(w, h), m, face, pressed)
}

/**
 * Der Modus-Schalter CLASSIC/DAILY als ein Taster mit zwei Hälften: Die
 * aktive steht erhaben in Sand, die andere liegt eingedrückt und dunkler
 * daneben — wie ein Kippschalter. [activeRight] wählt die rechte Hälfte.
 */
internal fun DrawScope.drawWearModeSwitch(m: WearTasterMetrics, activeRight: Boolean) {
    val w = size.width - m.shadow
    val h = size.height - m.shadow
    if (w <= 4 * m.border || h <= 2 * m.border) return
    drawWearPixelFrame(Offset(m.shadow, m.shadow), Size(w, h), m.border, WearTasterShadow)
    drawWearPixelFrame(Offset.Zero, Size(w, h), m.border)
    val half = (w - m.border) / 2f
    val sunken = Color(BevelPaint.dark(WearPanelSand.toArgbLong()))
    for (right in listOf(false, true)) {
        val x = if (right) half else 0f
        val active = right == activeRight
        // Die Hälften teilen sich die Mittelstufe des Rahmens.
        val topLeft = Offset(x + m.border, m.border)
        val faceSize = Size(half - m.border, h - 2 * m.border)
        val face = if (active) WearPanelSand else sunken
        val light = Color(BevelPaint.light(face.toArgbLong()))
        val dark = Color(BevelPaint.dark(face.toArgbLong()))
        wearBevelRect(
            face,
            topLeft,
            faceSize,
            m.edge,
            light = if (active) light else dark,
            dark = if (active) dark else light
        )
    }
}

/** Ein Panel wie am Telefon (Game-Over-Karte): Taster ohne Drücken. */
internal fun DrawScope.drawWearPanel(m: WearTasterMetrics) = drawWearTaster(m, pressed = false)
