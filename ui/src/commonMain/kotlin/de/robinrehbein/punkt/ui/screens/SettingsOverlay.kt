package de.robinrehbein.punkt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
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
import de.robinrehbein.punkt.ui.components.OverlayCloseButton
import de.robinrehbein.punkt.ui.components.PixelButton
import de.robinrehbein.punkt.ui.components.PixelIcon
import de.robinrehbein.punkt.ui.components.PixelSwitchRow
import de.robinrehbein.punkt.ui.components.pixelPressable
import de.robinrehbein.punkt.ui.resources.Res
import de.robinrehbein.punkt.ui.resources.ad_privacy
import de.robinrehbein.punkt.ui.resources.ctl_close
import de.robinrehbein.punkt.ui.resources.ctl_reminder
import de.robinrehbein.punkt.ui.resources.ctl_sound
import de.robinrehbein.punkt.ui.resources.help
import de.robinrehbein.punkt.ui.resources.remove_ads
import de.robinrehbein.punkt.ui.resources.settings
import de.robinrehbein.punkt.ui.resources.streak_many
import de.robinrehbein.punkt.ui.resources.streak_one
import de.robinrehbein.punkt.ui.world.OutlineColor
import de.robinrehbein.punkt.ui.world.PanelSand
import de.robinrehbein.punkt.ui.world.TextDark
import org.jetbrains.compose.resources.stringResource

// Die Einstellungen hinter dem Regler-Knopf. Bis v2.27 Teil von
// GameOverlays.kt (aufgeteilt nach Plan 8.4).

// ===== Einstellungen =====

/**
 * Alles, was der Startbildschirm nicht mehr trägt: Ton, Erinnerung,
 * Hilfe, der Werbe-Kauf und der Datenschutz-Widerruf.
 *
 * Aufbau wie Hilfe, Skins und Statistik: dunkler Scrim, ein Tap
 * daneben schließt (und wird konsumiert, damit er nicht als Spiel-Tap
 * durchschlägt). Die Sichtbarkeits-Regeln der beiden unteren Zeilen sind
 * unverändert die alten — sie stehen nur woanders: [removeAdsPrice] ist
 * genau dann gesetzt, wenn Werbung läuft UND Google ein kaufbares
 * Produkt liefert; [privacyVisible] sagt Google selbst.
 */
@Composable
internal fun SettingsOverlay(
    soundOn: Boolean,
    onToggleSound: () -> Unit,
    reminderOn: Boolean,
    onToggleReminder: () -> Unit,
    onHelp: () -> Unit,
    onClose: () -> Unit,
    removeAdsPrice: String? = null,
    onRemoveAds: () -> Unit = {},
    privacyVisible: Boolean = false,
    onPrivacy: () -> Unit = {},
    // Ohne Tages-Erinnerung (iOS) faellt die Zeile ganz weg. Ein
    // Schalter, der nichts schaltet, ist schlimmer als keiner.
    reminderSupported: Boolean = true
) {
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
            Text(
                text = stringResource(Res.string.settings),
                style = ScoreShadowStyle,
                fontSize = 32.sp,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Schalter statt Zustands-Knöpfe (Plan 7.2): Die Beschriftung
            // sagt, WAS geschaltet wird, der Schalter, WIE es steht. Bei
            // „TON: AN“ blieb offen, ob das der Zustand oder die Aktion ist.
            PixelSwitchRow(
                label = stringResource(Res.string.ctl_sound),
                checked = soundOn,
                icon = if (soundOn) PixelIcon.SPEAKER_ON else PixelIcon.SPEAKER_OFF,
                onCheckedChange = { onToggleSound() }
            )
            if (reminderSupported) {
                Spacer(modifier = Modifier.height(12.dp))
                // Tägliche Daily-Challenge-Erinnerung (Opt-in, lokal). Ob
                // sie wirklich an ist, sagt die Plattform (Berechtigung) —
                // der Schalter zeigt [reminderOn], nicht den Wunsch.
                PixelSwitchRow(
                    label = stringResource(Res.string.ctl_reminder),
                    checked = reminderOn,
                    icon = if (reminderOn) PixelIcon.BELL_ON else PixelIcon.BELL_OFF,
                    onCheckedChange = { onToggleReminder() }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            PixelButton(
                text = stringResource(Res.string.help),
                onClick = onHelp,
                backgroundColor = PanelSand,
                borderColor = TextDark,
                textColor = TextDark,
                width = 244.dp,
                height = 48.dp,
                borderWidth = 3.dp
            )

            // Bewusst nur eine kleine Zeile statt eines vierten Knopfs:
            // Der Kauf soll auffindbar sein, aber nicht um Aufmerksamkeit
            // mit den Schaltern konkurrieren. Klein heißt aber nicht schwer
            // zu treffen: Die Tippfläche ist trotzdem 48 dp hoch.
            if (removeAdsPrice != null) {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsLink(
                    text = stringResource(Res.string.remove_ads, removeAdsPrice),
                    fontSize = 14,
                    alpha = 0.75f,
                    onClick = onRemoveAds
                )
            }
            // Noch eine Spur zurückhaltender als die Kauf-Zeile: Der
            // Widerruf muss dauerhaft erreichbar sein, aber niemand sucht
            // ihn — deshalb klein und blass.
            if (privacyVisible) {
                SettingsLink(
                    text = stringResource(Res.string.ad_privacy),
                    fontSize = 12,
                    alpha = 0.55f,
                    onClick = onPrivacy
                )
            }
        }

        // Der sichtbare Ausgang (Plan 7.2). Tippen daneben schließt weiterhin.
        OverlayCloseButton(
            onClose = onClose,
            contentDescription = stringResource(Res.string.ctl_close),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        )
    }
}

/**
 * Eine Text-Zeile als Knopf: blass und klein wie bisher, aber mit einer
 * Tippfläche von mindestens 48 dp Höhe und der Rolle „Schaltfläche“ für
 * Vorleser (Plan 8.7: vorher 26–30 dp ohne Rolle).
 */
@Composable
private fun SettingsLink(text: String, fontSize: Int, alpha: Float, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .pixelPressable(onClick = onClick)
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = text,
            style = ScoreShadowStyle,
            fontSize = fontSize.sp,
            color = Color.White.copy(alpha = alpha),
            textAlign = TextAlign.Center
        )
    }
}

/** "SERIE: n TAG/TAGE" bzw. "STREAK: n DAY/DAYS", sprachrichtig. */
@Composable
fun streakLabel(days: Int): String =
    if (days == 1) stringResource(Res.string.streak_one)
    else stringResource(Res.string.streak_many, days)
