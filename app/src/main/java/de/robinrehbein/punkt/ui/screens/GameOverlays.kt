package de.robinrehbein.punkt.ui.screens

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.R
import de.robinrehbein.punkt.game.DotSkin
import de.robinrehbein.punkt.ui.components.PixelButton
import de.robinrehbein.punkt.ui.theme.Bytesized

// ===== Gemeinsame Retro-Farbpalette =====
internal val SkyColor = Color(0xFF4EC0CA)
internal val CloudColor = Color(0xFFE9FCFD)
internal val BushColor = Color(0xFF71C837)
internal val BushShadeColor = Color(0xFF5AA82C)
internal val TrunkColor = Color(0xFF9C6B3C)
internal val TrunkShade = Color(0xFF7A4E2A)
internal val GroundSand = Color(0xFFDED895)
internal val GroundSandShade = Color(0xFFD3C87E)
internal val GrassLight = Color(0xFF9DE85A)
internal val GrassDark = Color(0xFF74BF2E)
internal val OutlineColor = Color(0xFF543847)
internal val BlockBody = Color(0xFFE0862E)
internal val BlockLight = Color(0xFFF2A959)
internal val BlockDark = Color(0xFFA65E1E)
internal val BlockCap = Color(0xFFFFD28A)
internal val DotBody = Color(0xFFFFD847)
internal val DotShade = Color(0xFFF5A623)
internal val DotShine = Color(0xFFFFF3B8)
internal val PanelSand = Color(0xFFDED895)
internal val TextDark = Color(0xFF543847)
internal val RecordRed = Color(0xFFE53935)

internal val ScoreShadowStyle = TextStyle(
    fontFamily = Bytesized,
    shadow = Shadow(color = OutlineColor, offset = Offset(4f, 4f), blurRadius = 0f)
)

/** Nicht-Compose-State für Effekte, wird pro Frame im Canvas gelesen. */
internal class FxState {
    var flashAlpha = 0f
    var shakeTime = 0f

    /** Restzeit der Freischalt-Zelebration (goldener Ring + Schimmer). */
    var celebrateTime = 0f
}

// ===== Overlays =====

@Composable
internal fun ScoreHud(score: Int, daily: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
        ) {
            Text(
                text = score.toString(),
                style = ScoreShadowStyle,
                fontSize = 72.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            if (daily) {
                Text(
                    text = stringResource(R.string.daily),
                    style = ScoreShadowStyle,
                    fontSize = 18.sp,
                    color = DotBody
                )
            }
        }
    }
}

/** Kleiner "?"-Knopf oben rechts, öffnet die Spiel-Erklärung. */
@Composable
private fun HelpCornerButton(onHelp: () -> Unit, modifier: Modifier = Modifier) {
    PixelButton(
        text = "?",
        onClick = onHelp,
        backgroundColor = PanelSand,
        borderColor = TextDark,
        textColor = TextDark,
        width = 48.dp,
        height = 48.dp,
        borderWidth = 3.dp,
        modifier = modifier
    )
}

@Composable
internal fun ReadyOverlay(
    bestScore: Int,
    runNumber: Int,
    hint: String,
    dailyBest: Int,
    dailyStreak: Int,
    onDaily: () -> Unit,
    onSkins: () -> Unit,
    leaderboardAvailable: Boolean,
    onLeaderboard: () -> Unit,
    onHelp: () -> Unit,
    soundOn: Boolean,
    onToggleSound: () -> Unit,
    reminderOn: Boolean,
    onToggleReminder: () -> Unit
) {
    val blink by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        HelpCornerButton(
            onHelp = onHelp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )

        Column(modifier = Modifier
            .align(Alignment.TopStart)
            .padding(16.dp)
        ) {
            PixelButton(
                text = stringResource(if (soundOn) R.string.sound_on else R.string.sound_off),
                onClick = onToggleSound,
                backgroundColor = PanelSand,
                borderColor = TextDark,
                textColor = TextDark,
                width = 116.dp,
                height = 48.dp,
                borderWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(10.dp))
            // Tägliche Daily-Challenge-Erinnerung (Opt-in, lokal).
            PixelButton(
                text = stringResource(if (reminderOn) R.string.reminder_on else R.string.reminder_off),
                onClick = onToggleReminder,
                backgroundColor = PanelSand,
                borderColor = TextDark,
                textColor = TextDark,
                width = 168.dp,
                height = 48.dp,
                borderWidth = 3.dp
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        ) {
            Text(
                text = "PUNKT.",
                style = ScoreShadowStyle,
                fontSize = 64.sp,
                color = Color.White
            )
            if (bestScore > 0) {
                Text(
                    text = stringResource(R.string.best_score, bestScore),
                    style = ScoreShadowStyle,
                    fontSize = 22.sp,
                    color = Color.White
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 140.dp)
        ) {
            Text(
                text = hint,
                style = ScoreShadowStyle,
                fontSize = 22.sp,
                color = Color.White.copy(alpha = blink),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            // Nur sichtbar, wenn Play Games konfiguriert und angemeldet ist.
            if (leaderboardAvailable) {
                PixelButton(
                    text = stringResource(R.string.leaderboard),
                    onClick = onLeaderboard,
                    backgroundColor = PanelSand,
                    borderColor = TextDark,
                    textColor = TextDark,
                    width = 244.dp,
                    height = 48.dp,
                    borderWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            Row {
                PixelButton(
                    text = stringResource(R.string.daily),
                    onClick = onDaily,
                    backgroundColor = DotBody,
                    borderColor = TextDark,
                    textColor = TextDark,
                    width = 116.dp,
                    height = 52.dp,
                    borderWidth = 3.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                PixelButton(
                    text = stringResource(R.string.skins),
                    onClick = onSkins,
                    backgroundColor = PanelSand,
                    borderColor = TextDark,
                    textColor = TextDark,
                    width = 116.dp,
                    height = 52.dp,
                    borderWidth = 3.dp
                )
            }
            if (dailyBest > 0 || dailyStreak > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = listOfNotNull(
                        if (dailyBest > 0) stringResource(R.string.today_score, dailyBest) else null,
                        if (dailyStreak > 0) streakLabel(dailyStreak) else null
                    ).joinToString("  ·  "),
                    style = ScoreShadowStyle,
                    fontSize = 15.sp,
                    color = DotBody
                )
            }
            if (runNumber > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.run_number, runNumber + 1),
                    style = ScoreShadowStyle,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
internal fun GameOverOverlay(
    score: Int,
    bestScore: Int,
    isNewRecord: Boolean,
    taunt: String,
    daily: Boolean,
    dailyBest: Int,
    dailyStreak: Int,
    skinUnlocked: Boolean,
    onShare: () -> Unit,
    onMenu: () -> Unit,
    onHelp: () -> Unit
) {
    val blink by rememberInfiniteTransition(label = "overBlink").animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "overBlinkAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        HelpCornerButton(
            onHelp = onHelp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = stringResource(R.string.game_over),
                style = ScoreShadowStyle,
                fontSize = 48.sp,
                color = Color(0xFFFF8A3C)
            )

            Spacer(modifier = Modifier.height(16.dp))

            PixelPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MedalBadge(score = score)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.medal),
                            fontFamily = Bytesized,
                            fontSize = 12.sp,
                            color = TextDark
                        )
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.points_label),
                            fontFamily = Bytesized,
                            fontSize = 16.sp,
                            color = TextDark
                        )
                        Text(
                            text = score.toString(),
                            fontFamily = Bytesized,
                            fontSize = 40.sp,
                            color = TextDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.record_label),
                            fontFamily = Bytesized,
                            fontSize = 16.sp,
                            color = if (isNewRecord) RecordRed else TextDark
                        )
                        Text(
                            text = bestScore.toString(),
                            fontFamily = Bytesized,
                            fontSize = 40.sp,
                            color = if (isNewRecord) RecordRed else TextDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isNewRecord) stringResource(R.string.new_record) else taunt,
                style = ScoreShadowStyle,
                fontSize = 24.sp,
                color = if (isNewRecord) Color(0xFFFFE95E) else Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            if (daily) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = listOfNotNull(
                        stringResource(R.string.daily),
                        stringResource(R.string.today_score, dailyBest),
                        if (dailyStreak > 0) streakLabel(dailyStreak) else null
                    ).joinToString("  ·  "),
                    style = ScoreShadowStyle,
                    fontSize = 16.sp,
                    color = DotBody
                )
            }

            if (skinUnlocked) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.new_skin_unlocked),
                    style = ScoreShadowStyle,
                    fontSize = 18.sp,
                    color = Color(0xFFFFE95E)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Kein NOCHMAL-Button: Tap irgendwo startet sofort neu (nach
            // kurzer Wut-Tap-Sperre) — der blinkende Hinweis ist die
            // einzige Restart-Affordanz und darf deshalb auffallen.
            Text(
                text = stringResource(R.string.tap_retry),
                style = ScoreShadowStyle,
                fontSize = 26.sp,
                color = Color.White.copy(alpha = blink)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row {
                PixelButton(
                    text = stringResource(R.string.share),
                    onClick = onShare,
                    backgroundColor = DotBody,
                    borderColor = TextDark,
                    textColor = TextDark,
                    width = 116.dp,
                    height = 48.dp,
                    borderWidth = 3.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                PixelButton(
                    text = stringResource(R.string.menu),
                    onClick = onMenu,
                    backgroundColor = PanelSand,
                    borderColor = TextDark,
                    textColor = TextDark,
                    width = 116.dp,
                    height = 48.dp,
                    borderWidth = 3.dp
                )
            }
        }
    }
}

/** Beiger Panel-Hintergrund mit dunklem Pixelrahmen. */
@Composable
internal fun PixelPanel(content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.matchParentSize()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val border = 4.dp.toPx()
                drawRect(color = OutlineColor)
                drawRect(
                    color = PanelSand,
                    topLeft = Offset(border, border),
                    size = Size(size.width - 2 * border, size.height - 2 * border)
                )
            }
        }
        Box(modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp)) {
            content()
        }
    }
}

/** Medaille ab 10 Punkten: Bronze, Silber, Gold, Platin. */
@Composable
internal fun MedalBadge(score: Int) {
    val medalColor = when {
        score >= 40 -> Color(0xFFE5E4E2) // Platin
        score >= 30 -> Color(0xFFFFD700) // Gold
        score >= 20 -> Color(0xFFC0C0C0) // Silber
        score >= 10 -> Color(0xFFCD7F32) // Bronze
        else -> null
    }
    Box(
        modifier = Modifier.size(72.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val border = 3.dp.toPx()
            drawRect(color = OutlineColor)
            drawRect(
                color = GroundSandShade,
                topLeft = Offset(border, border),
                size = Size(size.width - 2 * border, size.height - 2 * border)
            )
            if (medalColor != null) {
                drawPixelCircle(
                    color = medalColor,
                    outline = OutlineColor,
                    centerX = size.width / 2f,
                    centerY = size.height / 2f,
                    radius = size.width * 0.3f
                )
            }
        }
        if (medalColor == null) {
            Text(
                text = "-",
                fontFamily = Bytesized,
                fontSize = 24.sp,
                color = TextDark
            )
        }
    }
}

// ===== Hilfe / Anleitung =====

/**
 * Vollflächige Spiel-Erklärung über dunklem Scrim. Ein Tap irgendwo
 * schließt sie — und wird dabei konsumiert, damit er nicht gleichzeitig
 * als Spiel-Tap (Sofort-Neustart!) durchschlägt.
 */
@Composable
internal fun HelpOverlay(onClose: () -> Unit) {
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

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.tap_to_close),
                fontFamily = Bytesized,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun StopHelpContent() {
    HelpHeading(stringResource(R.string.help_title))
    HelpLine(stringResource(R.string.help_line1))
    HelpLine(stringResource(R.string.help_line2))
    HelpLine(stringResource(R.string.help_line3), DotBody)
    HelpLine(stringResource(R.string.help_line4), DotBody)
    HelpLine(stringResource(R.string.help_line5))

    Spacer(modifier = Modifier.height(18.dp))
    Text(
        text = stringResource(R.string.help_twists),
        style = ScoreShadowStyle,
        fontSize = 24.sp,
        color = Color(0xFFFF8A3C)
    )
    Spacer(modifier = Modifier.height(8.dp))

    TwistHelpRow(
        GrassLight,
        stringResource(R.string.twist_pulse_title),
        stringResource(R.string.twist_pulse_text)
    )
    TwistHelpRow(
        Color(0xFF5B9BD5),
        stringResource(R.string.twist_drift_title),
        stringResource(R.string.twist_drift_text)
    )
    TwistHelpRow(
        CloudColor,
        stringResource(R.string.twist_ghost_title),
        stringResource(R.string.twist_ghost_text)
    )
    TwistHelpRow(
        Color(0xFFB44FD8),
        stringResource(R.string.twist_fake_title),
        stringResource(R.string.twist_fake_text)
    )
    TwistHelpRow(
        Color(0xFFFF8A3C),
        stringResource(R.string.twist_chain_title),
        stringResource(R.string.twist_chain_text)
    )

    Spacer(modifier = Modifier.height(10.dp))
    HelpLine(stringResource(R.string.help_max_twists))
}

/** "SERIE: n TAG/TAGE" bzw. "STREAK: n DAY/DAYS", sprachrichtig. */
@Composable
internal fun streakLabel(days: Int): String =
    if (days == 1) stringResource(R.string.streak_one)
    else stringResource(R.string.streak_many, days)

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

@Composable
private fun TwistHelpRow(color: Color, title: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color)
        )
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

// ===== Skin-Auswahl =====

/**
 * Vollflächiger Skin-Picker über dunklem Scrim, im Stil der Hilfe.
 * Freigeschaltete Skins lassen sich antippen, gesperrte zeigen ihre
 * Freischalt-Bedingung. Ein Tap außerhalb schließt (und wird konsumiert,
 * damit er nicht als Spiel-Tap durchschlägt).
 */
@Composable
internal fun SkinOverlay(
    stats: DotSkin.Stats,
    selected: DotSkin,
    onSelect: (DotSkin) -> Unit,
    onClose: () -> Unit
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
                text = stringResource(R.string.skins),
                style = ScoreShadowStyle,
                fontSize = 32.sp,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            DotSkin.entries.forEach { skin ->
                val unlocked = skin.isUnlocked(stats)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = unlocked) { onSelect(skin) }
                        .padding(horizontal = 48.dp, vertical = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(OutlineColor)
                            .padding(4.dp)
                            .background(
                                if (unlocked) Color(skin.body)
                                else Color(skin.body).copy(alpha = 0.25f)
                            )
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = stringResource(skin.titleRes),
                            fontFamily = Bytesized,
                            fontSize = 20.sp,
                            color = if (unlocked) Color.White else Color.White.copy(alpha = 0.45f)
                        )
                        Text(
                            text = when {
                                skin == selected -> stringResource(R.string.skin_selected)
                                unlocked -> stringResource(R.string.skin_tap_select)
                                else -> skin.unlockHintRes?.let { stringResource(it) } ?: ""
                            },
                            fontFamily = Bytesized,
                            fontSize = 14.sp,
                            color = when {
                                skin == selected -> DotBody
                                unlocked -> Color.White.copy(alpha = 0.7f)
                                else -> Color.White.copy(alpha = 0.45f)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.tap_to_close),
                fontFamily = Bytesized,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

// ===== Spott-Texte für den Rage-Faktor =====

internal fun pickTaunt(
    context: Context,
    score: Int,
    previousBest: Int,
    isNewRecord: Boolean
): String {
    if (isNewRecord) return context.getString(R.string.new_record)
    val gap = previousBest - score
    val pool = context.resources.getStringArray(
        when {
            score == 0 -> R.array.taunts_zero
            gap in 1..3 -> R.array.taunts_close
            score < previousBest / 2 -> R.array.taunts_low
            else -> R.array.taunts_default
        }
    )
    val line = pool[(score + previousBest) % pool.size]
    // Nur die "knapp daneben"-Zeilen tragen einen %1$d-Platzhalter.
    return if (line.contains("%1\$d")) line.format(gap) else line
}

// ===== Gemeinsame Zeichen-Helfer =====

internal const val GRID = 13f

/** Zeichnet einen blockigen "Pixel"-Kreis aus Rasterzellen. */
internal fun DrawScope.drawPixelCircle(
    color: Color,
    outline: Color,
    centerX: Float,
    centerY: Float,
    radius: Float,
    shade: Color = color
) {
    val n = GRID.toInt()
    val u = (radius * 2f) / GRID
    val mid = (GRID - 1f) / 2f
    val rr = GRID / 2f - 0.25f

    for (row in 0 until n) {
        for (col in 0 until n) {
            val dx = col - mid
            val dy = row - mid
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            if (dist <= rr) {
                val cellColor = when {
                    dist > rr - 1.1f -> outline
                    row + col > GRID * 1.15f -> shade
                    else -> color
                }
                drawRect(
                    color = cellColor,
                    topLeft = Offset(centerX - radius + col * u, centerY - radius + row * u),
                    size = Size(u + 0.5f, u + 0.5f)
                )
            }
        }
    }
}

/** Blockige Retro-Wolke aus drei gestapelten Rechtecken. */
internal fun DrawScope.drawCloud(x: Float, y: Float, cell: Float) {
    val u = cell * 2f
    drawRect(color = CloudColor, topLeft = Offset(x, y + u * 2), size = Size(u * 14, u * 3))
    drawRect(color = CloudColor, topLeft = Offset(x + u * 2, y), size = Size(u * 7, u * 2))
    drawRect(color = CloudColor, topLeft = Offset(x + u * 4, y - u * 1.5f), size = Size(u * 4, u * 1.5f))
}
