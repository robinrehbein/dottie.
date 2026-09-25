package de.robinrehbein.punkt.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.ui.theme.Bytesized
import de.robinrehbein.punkt.ui.world.PanelSand
import de.robinrehbein.punkt.ui.world.TextDark

/**
 * Eine Einstellungszeile: Beschriftung links, Pixel-Schalter rechts.
 *
 * Ersetzt die Zustands-Knöpfe „TON: AN“, bei denen offen blieb, ob der
 * Text den Zustand oder die Aktion meint (Plan 7.2). Die Beschriftung
 * bleibt gleich („TON“), den Zustand zeigt der Schalter: aus = Knebel
 * links auf Graugrün, an = Knebel rechts auf Grün.
 *
 * Die ganze Zeile ist antippbar (mindestens 48 dp hoch), ohne
 * Material-Welle, mit Haptik-Tick über [LocalPressFeedback] und der
 * Rolle „Schalter“ für Vorleser.
 *
 * @param icon Optionales Symbol vor der Beschriftung, etwa
 *   [PixelIcon.SPEAKER_ON] oder [PixelIcon.BELL_ON]. Es wird so gezeichnet,
 *   wie es übergeben wird; die Aus-Varianten streichen es durch.
 */
@Composable
fun PixelSwitchRow(
    label: String,
    checked: Boolean,
    icon: PixelIcon?,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 244.dp
) {
    val feedback = LocalPressFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .width(width)
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Switch
            ) { on ->
                feedback.tap()
                onCheckedChange(on)
            }
            .drawBehind {
                val b = ROW_BORDER.toPx()
                drawRect(color = TextDark)
                drawRect(
                    color = PanelSand,
                    topLeft = Offset(b, b),
                    size = Size(size.width - 2f * b, size.height - 2f * b)
                )
            }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (icon != null) {
            Canvas(modifier = Modifier.size(24.dp)) {
                drawPixelIcon(icon, TextDark, SwitchStrike, PanelSand)
            }
        }
        Text(
            text = label,
            fontFamily = Bytesized,
            fontSize = 16.sp,
            color = TextDark,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Canvas(modifier = Modifier.size(SWITCH_W, SWITCH_H)) {
            drawPixelSwitch(checked)
        }
    }
}

private val ROW_BORDER = 3.dp
private val SWITCH_W = 52.dp
private val SWITCH_H = 26.dp

/** Grün für „an“, wie die Zone im Spiel. */
private val SwitchOn = Color(0xFF74BF2E)

/** Gedecktes Graugrün für „aus“: der Sand, eine Stufe dunkler. */
private val SwitchOff = Color(0xFFB9B28A)

private val SwitchStrike = Color(0xFFE53935)

/**
 * Der Schalter nach dem Mockup (docs/bedienelemente.html, `.sw`):
 * 52 × 26 mit 3er-Rand; der Knebel 16 × 16 steht 2 vom Rand, aus links
 * dunkel, an rechts weiß mit dunklem Rand.
 */
private fun DrawScope.drawPixelSwitch(checked: Boolean) {
    val u = size.height / 26f
    val border = 3f * u
    drawRect(color = TextDark)
    drawRect(
        color = if (checked) SwitchOn else SwitchOff,
        topLeft = Offset(border, border),
        size = Size(size.width - 2f * border, size.height - 2f * border)
    )
    val knob = 16f * u
    val top = border + 2f * u
    val left = if (checked) size.width - border - 2f * u - knob else border + 2f * u
    drawRect(color = TextDark, topLeft = Offset(left, top), size = Size(knob, knob))
    if (checked) {
        drawRect(
            color = Color.White,
            topLeft = Offset(left + border, top + border),
            size = Size(knob - 2f * border, knob - 2f * border)
        )
    }
}
