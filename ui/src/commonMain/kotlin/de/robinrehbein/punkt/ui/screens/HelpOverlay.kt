package de.robinrehbein.punkt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.game.TrapPaint
import de.robinrehbein.punkt.ui.components.MineIcon
import de.robinrehbein.punkt.ui.resources.Res
import de.robinrehbein.punkt.ui.resources.help_line1
import de.robinrehbein.punkt.ui.resources.help_line2
import de.robinrehbein.punkt.ui.resources.help_line3
import de.robinrehbein.punkt.ui.resources.help_line4
import de.robinrehbein.punkt.ui.resources.help_line5
import de.robinrehbein.punkt.ui.resources.help_max_twists
import de.robinrehbein.punkt.ui.resources.help_title
import de.robinrehbein.punkt.ui.resources.help_twists
import de.robinrehbein.punkt.ui.resources.twist_chain_text
import de.robinrehbein.punkt.ui.resources.twist_chain_title
import de.robinrehbein.punkt.ui.resources.twist_drift_text
import de.robinrehbein.punkt.ui.resources.twist_drift_title
import de.robinrehbein.punkt.ui.resources.twist_fake_text
import de.robinrehbein.punkt.ui.resources.twist_fake_title
import de.robinrehbein.punkt.ui.resources.twist_ghost_text
import de.robinrehbein.punkt.ui.resources.twist_ghost_title
import de.robinrehbein.punkt.ui.resources.twist_pulse_text
import de.robinrehbein.punkt.ui.resources.twist_pulse_title
import de.robinrehbein.punkt.ui.theme.Bytesized
import de.robinrehbein.punkt.ui.world.CloudColor
import de.robinrehbein.punkt.ui.world.DotBody
import de.robinrehbein.punkt.ui.world.GrassLight
import de.robinrehbein.punkt.ui.world.OutlineColor
import org.jetbrains.compose.resources.stringResource
// AP-14: X-Knopf der Hilfe (am Ende, damit AP-12 oben ungestört ergänzt).
import de.robinrehbein.punkt.ui.components.CORNER_BUTTON_PADDING
import de.robinrehbein.punkt.ui.components.OverlayCloseButton
import de.robinrehbein.punkt.ui.resources.ctl_close

// Die Spiel-Erklärung. Bis v2.27 Teil von GameOverlays.kt
// (aufgeteilt nach Plan 8.4).

// ===== Hilfe / Anleitung =====

/**
 * Vollflächige Spiel-Erklärung über dunklem Scrim. Ein Tap irgendwo
 * schließt sie — und wird dabei konsumiert, damit er nicht gleichzeitig
 * als Spiel-Tap (Sofort-Neustart!) durchschlägt.
 */
@Composable
fun HelpOverlay(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OutlineColor.copy(alpha = 0.92f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClose() })
            }
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 32.dp)
        ) {
            StopHelpContent()
        }

        // Der sichtbare Ausgang statt „TIPPEN ZUM SCHLIESSEN“ (Plan 7.2).
        // Tippen daneben schließt weiterhin.
        OverlayCloseButton(
            onClose = onClose,
            contentDescription = stringResource(Res.string.ctl_close),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(CORNER_BUTTON_PADDING)
        )
    }
}

@Composable
private fun StopHelpContent() {
    HelpHeading(stringResource(Res.string.help_title))
    HelpLine(stringResource(Res.string.help_line1))
    HelpLine(stringResource(Res.string.help_line2))
    HelpLine(stringResource(Res.string.help_line3), DotBody)
    HelpLine(stringResource(Res.string.help_line4), DotBody)
    HelpLine(stringResource(Res.string.help_line5))

    Spacer(modifier = Modifier.height(18.dp))
    Text(
        text = stringResource(Res.string.help_twists),
        style = ScoreShadowStyle,
        fontSize = 24.sp,
        color = Color(0xFFFF8A3C)
    )
    Spacer(modifier = Modifier.height(8.dp))

    TwistHelpRow(
        GrassLight,
        stringResource(Res.string.twist_pulse_title),
        stringResource(Res.string.twist_pulse_text)
    )
    TwistHelpRow(
        Color(0xFF5B9BD5),
        stringResource(Res.string.twist_drift_title),
        stringResource(Res.string.twist_drift_text)
    )
    TwistHelpRow(
        CloudColor,
        stringResource(Res.string.twist_ghost_title),
        stringResource(Res.string.twist_ghost_text)
    )
    TwistHelpRow(
        Color(TrapPaint.RED),
        stringResource(Res.string.twist_fake_title),
        stringResource(Res.string.twist_fake_text),
        icon = { MineIcon(size = 16.dp) }
    )
    TwistHelpRow(
        Color(0xFFFF8A3C),
        stringResource(Res.string.twist_chain_title),
        stringResource(Res.string.twist_chain_text)
    )

    Spacer(modifier = Modifier.height(10.dp))
    HelpLine(stringResource(Res.string.help_max_twists))
}

@Composable
private fun HelpHeading(text: String) {
    Text(
        text = text,
        style = ScoreShadowStyle,
        fontSize = 32.sp,
        color = Color.White,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun HelpLine(text: String, color: Color = Color.White) {
    Text(
        text = text,
        fontFamily = Bytesized,
        fontSize = 15.sp,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
    )
}

/**
 * Eine Zeile der Twist-Liste: Symbol, Titel in der Farbe des Twists,
 * darunter der Satz. Das Symbol ist ein Farbquadrat, außer ein Twist
 * bringt ein eigenes Bild mit ([icon], etwa die Mine der Falle).
 */
@Composable
private fun TwistHelpRow(
    color: Color,
    title: String,
    text: String,
    icon: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 5.dp)
    ) {
        if (icon != null) {
            icon()
        } else {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontFamily = Bytesized,
                fontSize = 15.sp,
                color = color
            )
            Text(
                text = text,
                fontFamily = Bytesized,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}
