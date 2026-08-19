package de.robinrehbein.punkt.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.ui.theme.Bytesized

/**
 * A pixel art styled button component with retro gaming aesthetics.
 * Features a pixelated border with stepped edges and customizable colors.
 *
 * @param text The text to display on the button
 * @param onClick Callback invoked when the button is clicked
 * @param modifier Modifier to be applied to the button
 * @param backgroundColor Background color of the button (default: light pink/purple)
 * @param borderColor Border color of the button (default: blue/purple)
 * @param textColor Text color (default: same as border color)
 * @param width Width of the button
 * @param height Height of the button
 * @param borderWidth Width of the pixel border
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
    borderWidth: Dp = 4.dp
) {
    Box(
        modifier = modifier
            .size(width, height)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Custom Canvas for drawing the pixelated border
        Canvas(
            modifier = Modifier.size(width, height)
        ) {
            drawPixelBorder(
                backgroundColor = backgroundColor,
                borderColor = borderColor,
                borderWidth = borderWidth.toPx()
            )
        }
        
        // Text overlay
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

/** Icon motifs for [PixelIconButton], drawn as blocky shapes on a 16-unit grid. */
enum class PixelIcon { SPEAKER_ON, SPEAKER_OFF, BELL_ON, BELL_OFF, GEAR }

/**
 * A square pixel art button showing an icon instead of text — same border
 * style as [PixelButton]. The "off" variants draw a stepped diagonal strike.
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
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button
            ) { onClick() }
            .semantics { this.contentDescription = cd },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(buttonSize + shadow)) {
            val s = shadow.toPx()
            val press = if (pressed) s else 0f
            if (s > 0f && !pressed) {
                drawRect(
                    color = shadowColor,
                    topLeft = Offset(s, s),
                    size = Size(size.width - s, size.height - s)
                )
            }
            inset(left = press, top = press, right = s - press, bottom = s - press) {
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

private fun DrawScope.drawPixelIcon(
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
        PixelIcon.GEAR -> {
            // Body plus four straight and four diagonal teeth; the hub is
            // punched back out in the button colour so the ring reads as a gear.
            block(4.5f, 4.5f, 7f, 7f)
            block(6.5f, 2.4f, 3f, 2.6f)
            block(6.5f, 11f, 3f, 2.6f)
            block(2.4f, 6.5f, 2.6f, 3f)
            block(11f, 6.5f, 2.6f, 3f)
            block(3.2f, 3.2f, 2.6f, 2.6f)
            block(10.2f, 3.2f, 2.6f, 2.6f)
            block(3.2f, 10.2f, 2.6f, 2.6f)
            block(10.2f, 10.2f, 2.6f, 2.6f)
            block(6.6f, 6.6f, 2.8f, 2.8f, backgroundColor)
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
