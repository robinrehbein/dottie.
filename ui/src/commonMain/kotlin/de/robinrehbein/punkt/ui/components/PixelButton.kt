package de.robinrehbein.punkt.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.ui.theme.Bytesized

/**
 * Ein Text-Knopf im Pixel-Stil mit gestuftem Rand.
 *
 * Alle Knöpfe drücken sich gleich: ein harter Pixelschatten ([shadow],
 * 4 dp) unten rechts, beim Drücken sinkt der Knopf genau um diesen
 * Schatten ein. Keine Material-Welle, dafür ein kurzer Haptik-Tick über
 * [LocalPressFeedback] (siehe [pixelPressable]).
 *
 * Der Schatten liegt außerhalb von [width] × [height]: Der Knopf belegt
 * [shadow] mehr Platz nach rechts und unten als vorher. Das ist gewollt,
 * der Knopf selbst behält seine Maße.
 *
 * @param contentDescription Vorlesetext, falls [text] allein nicht reicht.
 * @param enabled Falsch: kein Tap, kein Einsinken, kein Tick.
 */
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFFE8B4E8), // Light pink/purple
    borderColor: Color = Color(0xFF5555FF), // Blue/purple
    textColor: Color = borderColor,
    width: Dp = 200.dp,
    height: Dp = 60.dp,
    borderWidth: Dp = 4.dp,
    contentDescription: String? = null,
    enabled: Boolean = true,
    shadow: Dp = PIXEL_SHADOW,
    shadowColor: Color = borderColor.copy(alpha = 0.55f)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val cd = contentDescription
    Box(
        modifier = modifier
            .size(width + shadow, height + shadow)
            .pixelPressable(
                enabled = enabled,
                interactionSource = interactionSource,
                onClick = onClick
            )
            .then(
                if (cd != null) Modifier.semantics { this.contentDescription = cd }
                else Modifier
            )
    ) {
        Canvas(modifier = Modifier.size(width + shadow, height + shadow)) {
            drawPressedFrame(
                shadow = shadow.toPx(),
                pressed = pressed,
                shadowColor = shadowColor
            ) {
                drawPixelBorder(
                    backgroundColor = backgroundColor,
                    borderColor = borderColor,
                    borderWidth = borderWidth.toPx()
                )
            }
        }

        // Die Schrift sinkt mit dem Knopf ein.
        Box(
            modifier = Modifier
                .size(width, height)
                .offset(
                    x = if (pressed) shadow else 0.dp,
                    y = if (pressed) shadow else 0.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontFamily = Bytesized,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

/** Der Pixelschatten aller Knöpfe: so weit sinkt ein Knopf beim Drücken ein. */
val PIXEL_SHADOW: Dp = 4.dp

/**
 * Schatten und Einsinken für einen Knopf, der die ganze Zeichenfläche
 * minus [shadow] belegt: ungedrückt liegt er oben links mit dem Schatten
 * darunter, gedrückt liegt er genau auf dem Schatten. [face] zeichnet
 * den Knopf selbst.
 */
internal fun DrawScope.drawPressedFrame(
    shadow: Float,
    pressed: Boolean,
    shadowColor: Color,
    face: DrawScope.() -> Unit
) {
    val press = if (pressed) shadow else 0f
    if (shadow > 0f && !pressed) {
        drawRect(
            color = shadowColor,
            topLeft = Offset(shadow, shadow),
            size = Size(size.width - shadow, size.height - shadow)
        )
    }
    inset(left = press, top = press, right = shadow - press, bottom = shadow - press) {
        face()
    }
}

/** Icon motifs for [PixelIconButton], drawn as blocky shapes on a 16-unit grid. */
enum class PixelIcon { SPEAKER_ON, SPEAKER_OFF, BELL_ON, BELL_OFF, SLIDERS }

/**
 * A square pixel art button showing an icon instead of text — same border
 * style as [PixelButton]. The "off" variants draw a stepped diagonal strike.
 * Taps tick through [LocalPressFeedback] like every [pixelPressable].
 *
 * [shadow] > 0 draws a hard pixel drop shadow (like the title text) and
 * lets the button visually sink into it while pressed; [highlightColor]
 * adds a light edge just below the top border. Both default to off, so
 * existing call sites render unchanged.
 */
@Composable
fun PixelIconButton(
    icon: PixelIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFFE8B4E8),
    borderColor: Color = Color(0xFF5555FF),
    iconColor: Color = borderColor,
    strikeColor: Color = Color(0xFFE53935),
    buttonSize: Dp = 48.dp,
    borderWidth: Dp = 4.dp,
    shadow: Dp = 0.dp,
    shadowColor: Color = borderColor.copy(alpha = 0.45f),
    highlightColor: Color? = null
) {
    val cd = contentDescription
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(buttonSize + shadow)
            .pixelPressable(
                interactionSource = interactionSource,
                onClick = onClick
            )
            .semantics { this.contentDescription = cd },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(buttonSize + shadow)) {
            drawPressedFrame(
                shadow = shadow.toPx(),
                pressed = pressed,
                shadowColor = shadowColor
            ) {
                drawPixelBorder(
                    backgroundColor = backgroundColor,
                    borderColor = borderColor,
                    borderWidth = borderWidth.toPx()
                )
                if (highlightColor != null) {
                    val border = borderWidth.toPx()
                    drawRect(
                        color = highlightColor,
                        topLeft = Offset(border * 2f, border),
                        size = Size(size.width - border * 4f, border)
                    )
                }
                drawPixelIcon(icon, iconColor, strikeColor, backgroundColor)
            }
        }
    }
}

internal fun DrawScope.drawPixelIcon(
    icon: PixelIcon,
    color: Color,
    strikeColor: Color,
    backgroundColor: Color
) {
    val u = size.minDimension / 16f
    fun block(x: Float, y: Float, w: Float, h: Float, c: Color = color) {
        drawRect(color = c, topLeft = Offset(x * u, y * u), size = Size(w * u, h * u))
    }
    when (icon) {
        PixelIcon.SPEAKER_ON, PixelIcon.SPEAKER_OFF -> {
            // Driver box plus cone opening to the right
            block(3f, 6f, 2.5f, 4f)
            block(5.5f, 5f, 1.5f, 6f)
            block(7f, 4f, 1.5f, 8f)
            if (icon == PixelIcon.SPEAKER_ON) {
                // Two blocky sound waves
                block(10f, 6f, 1.2f, 4f)
                block(12f, 4.5f, 1.2f, 7f)
            }
        }
        PixelIcon.BELL_ON, PixelIcon.BELL_OFF -> {
            // Knob, dome, body, lip, clapper
            block(7.2f, 2.5f, 1.6f, 1.5f)
            block(5.5f, 3.8f, 5f, 2.2f)
            block(4.5f, 6f, 7f, 3.5f)
            block(3.5f, 9.3f, 9f, 1.6f)
            block(7.2f, 11.2f, 1.6f, 1.6f)
        }
        PixelIcon.SLIDERS -> {
            // Three sliders with staggered knobs — the settings icon.
            // Deliberately not a gear: at 48 dp the gear's teeth blur into
            // a blob, while three bars with knobs stay unmistakable.
            block(3f, 4f, 10f, 1.5f)
            block(3f, 7.25f, 10f, 1.5f)
            block(3f, 10.5f, 10f, 1.5f)
            block(9.2f, 3.1f, 2.6f, 3.3f)
            block(4.8f, 6.35f, 2.6f, 3.3f)
            block(9.8f, 9.6f, 2.6f, 3.3f)
        }
    }
    if (icon == PixelIcon.SPEAKER_OFF || icon == PixelIcon.BELL_OFF) {
        // Stepped diagonal strike, top-left to bottom-right
        for (i in 0 until 6) {
            block(2.5f + i * 1.9f, 2.5f + i * 1.9f, 2.2f, 2.2f, strikeColor)
        }
    }
}

/**
 * Draws the pixel art border with stepped/staircase pattern on the sides
 */
private fun DrawScope.drawPixelBorder(
    backgroundColor: Color,
    borderColor: Color,
    borderWidth: Float
) {
    val width = size.width
    val height = size.height
    val pixelSize = borderWidth
    
    // Fill background
    drawRect(
        color = backgroundColor,
        
        topLeft = Offset(0f, 0f),
        size = Size(width, height)
    )
    
    // Draw top border (solid)
    drawRect(
        color = borderColor,
        topLeft = Offset(0f, 0f),
        size = Size(width, pixelSize)
    )
    
    // Draw bottom border (solid)
    drawRect(
        color = borderColor,
        topLeft = Offset(0f, height - pixelSize),
        size = Size(width, pixelSize)
    )
    
    // Draw left stepped border
    drawLeftSteppedBorder(borderColor, pixelSize, width, height)
    
    // Draw right stepped border
    drawRightSteppedBorder(borderColor, pixelSize, width, height)
}

/**
 * Draws the left side stepped/staircase border pattern
 */
private fun DrawScope.drawLeftSteppedBorder(
    borderColor: Color,
    pixelSize: Float,
    width: Float,
    height: Float
) {
    val steps = ((height - 2 * pixelSize) / pixelSize).toInt()
    val stepHeight = (height - 2 * pixelSize) / steps
    
    for (i in 0 until steps) {
        val y = pixelSize + i * stepHeight
        val stepWidth = when {
            i < steps / 4 -> pixelSize * 2 // Wider at top
            i < steps * 3 / 4 -> pixelSize // Normal width in middle
            else -> pixelSize * 2 // Wider at bottom
        }
        
        drawRect(
            color = borderColor,
            topLeft = Offset(0f, y),
            size = Size(stepWidth, stepHeight + 1f) // +1 to avoid gaps
        )
    }
}

/**
 * Draws the right side stepped/staircase border pattern
 */
private fun DrawScope.drawRightSteppedBorder(
    borderColor: Color,
    pixelSize: Float,
    width: Float,
    height: Float
) {
    val steps = ((height - 2 * pixelSize) / pixelSize).toInt()
    val stepHeight = (height - 2 * pixelSize) / steps
    
    for (i in 0 until steps) {
        val y = pixelSize + i * stepHeight
        val stepWidth = when {
            i < steps / 4 -> pixelSize * 2 // Wider at top
            i < steps * 3 / 4 -> pixelSize // Normal width in middle
            else -> pixelSize * 2 // Wider at bottom
        }
        
        drawRect(
            color = borderColor,
            topLeft = Offset(width - stepWidth, y),
            size = Size(stepWidth, stepHeight + 1f) // +1 to avoid gaps
        )
    }
}
