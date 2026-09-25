package de.robinrehbein.punkt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.ui.components.OverlayCloseButton
import de.robinrehbein.punkt.ui.components.PixelButton
import de.robinrehbein.punkt.ui.resources.Res
import de.robinrehbein.punkt.ui.resources.ctl_close
import de.robinrehbein.punkt.ui.resources.daily_intro_how
import de.robinrehbein.punkt.ui.resources.daily_intro_start
import de.robinrehbein.punkt.ui.resources.daily_intro_text
import de.robinrehbein.punkt.ui.resources.daily_intro_title
import de.robinrehbein.punkt.ui.theme.Bytesized
import de.robinrehbein.punkt.ui.world.DotBody
import de.robinrehbein.punkt.ui.world.OutlineColor
import de.robinrehbein.punkt.ui.world.TextDark
import org.jetbrains.compose.resources.stringResource

/**
 * DAILY als Umschalter mit einmaliger Erklärung (Plan 7.2, 8.6 #4).
 *
 * Früher startete DAILY sofort einen Tageslauf, bei beliebigem Winkel.
 * Unter der Startregel (erster Tap im Grün ist Treffer 1) kam dann meist
 * „NOCH NICHT“, und die Daily startete nicht (Plan 8.7). Jetzt schaltet
 * DAILY nur scharf; gestartet wird wie jeder Lauf per Tap im Grün.
 */

/** Was ein Tap auf DAILY bewirkt. */
enum class DailyTap {
    /** Die einmalige Karte zeigen. Scharf wird erst START auf der Karte. */
    SHOW_INTRO,

    /** Scharf schalten: Der nächste Lauf ist der Tageslauf. */
    ARM,

    /** Wieder abschalten: Der nächste Lauf ist ein freier. */
    DISARM
}

/**
 * Die Entscheidung für einen Tap auf DAILY: beim allerersten Mal die
 * Karte, danach ein Umschalter aus ↔ scharf.
 */
fun dailyTap(armed: Boolean, introSeen: Boolean): DailyTap = when {
    armed -> DailyTap.DISARM
    !introSeen -> DailyTap.SHOW_INTRO
    else -> DailyTap.ARM
}

/**
 * Die einmalige Karte: „TAGESLAUF: HEUTE FÜR ALLE GLEICH. DEIN BESTER
 * VERSUCH ZÄHLT.“ mit START. START schaltet DAILY scharf, der Lauf
 * beginnt danach mit dem ersten Tap im Grün. X, ein Tap daneben und die
 * Zurück-Geste schließen die Karte, ohne scharf zu schalten.
 */
@Composable
fun DailyIntroCard(onStart: () -> Unit, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OutlineColor.copy(alpha = 0.92f))
            .pointerInput(Unit) {
                // Ein Tap daneben schließt und wird verbraucht, damit er
                // nicht als Spiel-Tap durchschlägt.
                detectTapGestures(onTap = { onClose() })
            }
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center
    ) {
        PixelPanel {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    // Taps auf die Karte selbst schließen nicht.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Text(
                    text = stringResource(Res.string.daily_intro_title),
                    fontFamily = Bytesized,
                    fontSize = 26.sp,
                    color = TextDark,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.daily_intro_text),
                    fontFamily = Bytesized,
                    fontSize = 17.sp,
                    color = TextDark,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(Res.string.daily_intro_how),
                    fontFamily = Bytesized,
                    fontSize = 13.sp,
                    color = TextDark.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(18.dp))
                PixelButton(
                    text = stringResource(Res.string.daily_intro_start),
                    onClick = onStart,
                    backgroundColor = DotBody,
                    borderColor = TextDark,
                    textColor = TextDark,
                    width = 200.dp,
                    height = 52.dp,
                    borderWidth = 3.dp
                )
            }
        }

        OverlayCloseButton(
            onClose = onClose,
            contentDescription = stringResource(Res.string.ctl_close),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        )
    }
}

/** Farbe des scharfen DAILY-Tasters: das Gelb des Vogels, sonst Sand. */
internal val DailyArmedColor: Color = DotBody
