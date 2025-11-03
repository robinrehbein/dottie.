package de.robinrehbein.punkt.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.R

/**
 * Bytesized FontFamily for the button text
 */
private val BytesizedFont = FontFamily(
    Font(R.font.bytesized_regular, FontWeight.Normal)
)

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
            fontFamily = BytesizedFont,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
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

/**
 * Preview of the PixelArtButton component
 */
@Preview(showBackground = true)
@Composable
private fun PixelArtButtonPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            PixelButton(
                text = "START GAME",
                onClick = { /* Preview action */ }
            )
        }
    }
}

/**
 * Preview with custom colors
 */
@Preview(showBackground = true)
@Composable
private fun PixelArtButtonCustomColorsPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            PixelButton(
                text = "SETTINGS",
                onClick = { /* Preview action */ },
                backgroundColor = Color(0xFFFFB4B4), // Light red
                borderColor = Color(0xFF8B0000), // Dark red
                textColor = Color(0xFF8B0000)
            )
        }
    }
}

/**
 * Preview with different sizes
 */
@Preview(showBackground = true)
@Composable
private fun PixelArtButtonSizesPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
            ) {
                PixelButton(
                    text = "SMALL",
                    onClick = { },
                    width = 120.dp,
                    height = 40.dp,
                    borderWidth = 3.dp
                )
                
                PixelButton(
                    text = "MEDIUM",
                    onClick = { },
                    width = 160.dp,
                    height = 50.dp
                )
                
                PixelButton(
                    text = "LARGE",
                    onClick = { },
                    width = 240.dp,
                    height = 70.dp,
                    borderWidth = 5.dp
                )
            }
        }
    }
}