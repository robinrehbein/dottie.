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
import de.robinrehbein.punkt.ui.world.RecordRed
import de.robinrehbein.punkt.ui.world.TextDark

/**
 * Der Knopf oben rechts: im Startbildschirm die Einstellungen, in jedem
 * Overlay das X (Plan 7.2). Beide sind derselbe Knopf an derselben
 * Stelle — öffnet man die Einstellungen, liegt das X genau dort, wo eben
 * die Schieber waren, gleich groß und mit demselben Schatten.
 *
 * Sichtbar 48 dp mit 4 dp Pixelschatten. Sandfläche, gerader dunkler
 * Rand, innen eine Bevel-Kante (siehe [drawSandBevel]). Der Schatten ist
 * deckend, damit der Knopf auf hellem Himmel und auf dem dunklen Overlay
 * gleich aussieht. Sinkt beim Drücken ein und tickt über
 * [LocalPressFeedback].
 *
 * Platz: `Modifier.align(Alignment.TopEnd).padding(CORNER_BUTTON_PADDING)`
 * innerhalb der Systemleisten — das tun alle Aufrufer, sonst sitzt das X
 * woanders als die Schieber.
 */
@Composable
fun CornerButton(
    icon: PixelIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val cd = contentDescription
    Box(
        modifier = modifier
            .size(CORNER_FACE + PIXEL_SHADOW)
            .pixelPressable(interactionSource = interactionSource, onClick = onClick)
            .semantics { this.contentDescription = cd },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(CORNER_FACE + PIXEL_SHADOW)) {
            drawPressedFrame(
                shadow = PIXEL_SHADOW.toPx(),
                pressed = pressed,
                shadowColor = CornerShadow
            ) {
                drawCornerFace(border = CORNER_BORDER.toPx())
                drawPixelIcon(icon, TextDark, RecordRed, PanelSand)
            }
        }
    }
}

/**
 * Der sichtbare Ausgang eines Overlays: der [CornerButton] mit X.
 * Tippen neben das Overlay schließt weiterhin, das X zeigt nur, dass es
 * geht.
 *
 * @param contentDescription Vorlesetext („SCHLIESSEN“).
 */
@Composable
fun OverlayCloseButton(
    onClose: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) = CornerButton(
    icon = PixelIcon.CLOSE,
    contentDescription = contentDescription,
    onClick = onClose,
    modifier = modifier
)

/** Abstand des Eckknopfs zum Rand innerhalb der Systemleisten. */
val CORNER_BUTTON_PADDING = 16.dp

private val CORNER_FACE = 48.dp
private val CORNER_BORDER = 3.dp
private val CornerShadow = Color(0xFF2D1E27)
/**
 * Helle Innenkante oben und links, dunkle unten und rechts: Das Licht
 * fällt von oben links, genau gegenüber dem Pixelschatten unten rechts.
 *
 * Vorher lag hier ein heller Streifen in voller Rahmenstärke nur unter
 * der Oberkante. Bündig am dunklen Rand las er sich als zweiter, heller
 * Rahmen statt als Glanz. Die Bevel-Kante ist halb so stark und
 * umläuft die Fläche, dadurch gehört sie sichtbar zur Sandfläche.
 */
internal val SandBevelLight = Color(0xFFEFE9C2)
internal val SandBevelDark = Color(0xFFC5BB87)

/** Dunkler gerader Rand, Sandfläche, Bevel-Kante innen. */
private fun DrawScope.drawCornerFace(border: Float) {
    drawRect(color = TextDark)
    drawRect(
        color = PanelSand,
        topLeft = Offset(border, border),
        size = Size(size.width - 2f * border, size.height - 2f * border)
    )
    drawSandBevel(
        topLeft = Offset(border, border),
        size = Size(size.width - 2f * border, size.height - 2f * border),
        edge = border / 2f
    )
}

/**
 * Die Bevel-Kante auf einer Sandfläche bei [topLeft] mit [size]:
 * [edge] breit, hell oben und links, dunkel unten und rechts. Die helle
 * Kante liegt zuletzt oben, damit die Ecken oben rechts und unten links
 * je zur Hälfte hell und dunkel sind wie bei einem gedrehten Pixel-Knopf.
 */
internal fun DrawScope.drawSandBevel(topLeft: Offset, size: Size, edge: Float) {
    drawRect(
        color = SandBevelDark,
        topLeft = Offset(topLeft.x, topLeft.y + size.height - edge),
        size = Size(size.width, edge)
    )
    drawRect(
        color = SandBevelDark,
        topLeft = Offset(topLeft.x + size.width - edge, topLeft.y),
        size = Size(edge, size.height)
    )
    drawRect(
        color = SandBevelLight,
        topLeft = topLeft,
        size = Size(size.width - edge, edge)
    )
    drawRect(
        color = SandBevelLight,
        topLeft = topLeft,
        size = Size(edge, size.height - edge)
    )
}
