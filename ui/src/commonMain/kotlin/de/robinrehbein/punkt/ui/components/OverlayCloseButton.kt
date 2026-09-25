package de.robinrehbein.punkt.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.robinrehbein.punkt.ui.world.PanelSand
import de.robinrehbein.punkt.ui.world.TextDark

/**
 * Der sichtbare Ausgang eines Overlays: ein X-Knopf, gedacht für oben
 * rechts. Tippen neben das Overlay schließt weiterhin, das X zeigt nur,
 * dass es geht (Plan 7.2).
 *
 * Sichtbar 40 dp mit 4 dp Pixelschatten, antippbar auf 48 dp. Sandfarben
 * mit dunklem Rand wie die Taster, das X aus Pixelblöcken, damit es nicht
 * an einem Zeichen der Schrift hängt. Sinkt beim Drücken ein und tickt
 * über [LocalPressFeedback].
 *
 * Den Platz legt der Aufrufer fest, etwa
 * `Modifier.align(Alignment.TopEnd).padding(8.dp)`.
 *
 * @param contentDescription Vorlesetext („SCHLIESSEN“). Ohne ihn liest
 *   ein Vorleser nur „Schaltfläche“.
 */
@Composable
fun OverlayCloseButton(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val cd = contentDescription
    Box(
        modifier = modifier
            .size(CLOSE_TOUCH)
            .pixelPressable(interactionSource = interactionSource, onClick = onClose)
            .then(
                if (cd != null) Modifier.semantics { this.contentDescription = cd }
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(CLOSE_FACE + PIXEL_SHADOW)) {
            drawPressedFrame(
                shadow = PIXEL_SHADOW.toPx(),
                pressed = pressed,
                shadowColor = CloseShadow
            ) {
                drawCloseFace(border = 3.dp.toPx())
            }
        }
    }
}

private val CLOSE_TOUCH = 48.dp
private val CLOSE_FACE = 40.dp
private val CloseShadow = Color(0xFF2D1E27)

/** Sandfläche, gerader 3-dp-Rand und ein X aus 5 × 5 Blöcken auf einem 9er-Raster. */
private fun DrawScope.drawCloseFace(border: Float) {
    drawRect(color = TextDark)
    drawRect(
        color = PanelSand,
        topLeft = Offset(border, border),
        size = Size(size.width - 2f * border, size.height - 2f * border)
    )
    val cell = size.minDimension / 9f
    val origin = 2f * cell
    for (i in 0 until 5) {
        for (j in intArrayOf(i, 4 - i)) {
            drawRect(
                color = TextDark,
                topLeft = Offset(origin + j * cell, origin + i * cell),
                size = Size(cell, cell)
            )
        }
    }
}
