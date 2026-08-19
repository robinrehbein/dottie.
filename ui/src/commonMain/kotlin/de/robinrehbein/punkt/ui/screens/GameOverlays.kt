package de.robinrehbein.punkt.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.game.CardFrame
import de.robinrehbein.punkt.game.CardStyle
import de.robinrehbein.punkt.game.FrameTone
import de.robinrehbein.punkt.game.Goal
import de.robinrehbein.punkt.game.MedalId
import de.robinrehbein.punkt.game.MedalPaint
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.SkinFamily
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SkinState
import de.robinrehbein.punkt.game.SkinStats
import de.robinrehbein.punkt.game.SoundBank
import de.robinrehbein.punkt.game.SoundSetId
import de.robinrehbein.punkt.game.Twist
import de.robinrehbein.punkt.ui.components.PixelButton
import de.robinrehbein.punkt.ui.components.PixelIcon
import de.robinrehbein.punkt.ui.components.PixelIconButton
import de.robinrehbein.punkt.ui.data.deviceHourAndMonth
import de.robinrehbein.punkt.ui.resources.Res
import de.robinrehbein.punkt.ui.resources.ad_privacy
import de.robinrehbein.punkt.ui.resources.banner_twist_chain
import de.robinrehbein.punkt.ui.resources.banner_twist_drift
import de.robinrehbein.punkt.ui.resources.banner_twist_fake
import de.robinrehbein.punkt.ui.resources.banner_twist_ghost
import de.robinrehbein.punkt.ui.resources.banner_twist_pulse
import de.robinrehbein.punkt.ui.resources.best_score
import de.robinrehbein.punkt.ui.resources.daily
import de.robinrehbein.punkt.ui.resources.frame_on_card
import de.robinrehbein.punkt.ui.resources.frames
import de.robinrehbein.punkt.ui.resources.game_over
import de.robinrehbein.punkt.ui.resources.help
import de.robinrehbein.punkt.ui.resources.help_line1
import de.robinrehbein.punkt.ui.resources.help_line2
import de.robinrehbein.punkt.ui.resources.help_line3
import de.robinrehbein.punkt.ui.resources.help_line4
import de.robinrehbein.punkt.ui.resources.help_line5
import de.robinrehbein.punkt.ui.resources.help_max_twists
import de.robinrehbein.punkt.ui.resources.help_title
import de.robinrehbein.punkt.ui.resources.help_twists
import de.robinrehbein.punkt.ui.resources.leaderboard
import de.robinrehbein.punkt.ui.resources.medal
import de.robinrehbein.punkt.ui.resources.medal_next
import de.robinrehbein.punkt.ui.resources.menu
import de.robinrehbein.punkt.ui.resources.new_medal
import de.robinrehbein.punkt.ui.resources.new_record
import de.robinrehbein.punkt.ui.resources.new_skin_unlocked
import de.robinrehbein.punkt.ui.resources.patron_owned
import de.robinrehbein.punkt.ui.resources.patron_pack
import de.robinrehbein.punkt.ui.resources.patron_pack_skins_only
import de.robinrehbein.punkt.ui.resources.points_label
import de.robinrehbein.punkt.ui.resources.record_label
import de.robinrehbein.punkt.ui.resources.reminder_off
import de.robinrehbein.punkt.ui.resources.reminder_on
import de.robinrehbein.punkt.ui.resources.remove_ads
import de.robinrehbein.punkt.ui.resources.run_number
import de.robinrehbein.punkt.ui.resources.scenes
import de.robinrehbein.punkt.ui.resources.settings
import de.robinrehbein.punkt.ui.resources.share
import de.robinrehbein.punkt.ui.resources.skin_pass_offer
import de.robinrehbein.punkt.ui.resources.skin_pass_today
import de.robinrehbein.punkt.ui.resources.skin_selected
import de.robinrehbein.punkt.ui.resources.skin_tap_select
import de.robinrehbein.punkt.ui.resources.skins
import de.robinrehbein.punkt.ui.resources.sound_off
import de.robinrehbein.punkt.ui.resources.sound_on
import de.robinrehbein.punkt.ui.resources.sound_tap_hear
import de.robinrehbein.punkt.ui.resources.sounds
import de.robinrehbein.punkt.ui.resources.stats
import de.robinrehbein.punkt.ui.resources.streak_many
import de.robinrehbein.punkt.ui.resources.streak_one
import de.robinrehbein.punkt.ui.resources.tap_retry
import de.robinrehbein.punkt.ui.resources.tap_to_close
import de.robinrehbein.punkt.ui.resources.taunts_close
import de.robinrehbein.punkt.ui.resources.taunts_default
import de.robinrehbein.punkt.ui.resources.taunts_low
import de.robinrehbein.punkt.ui.resources.taunts_zero
import de.robinrehbein.punkt.ui.resources.today_score
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
import de.robinrehbein.punkt.ui.text.familyTitle
import de.robinrehbein.punkt.ui.text.frameHint
import de.robinrehbein.punkt.ui.text.frameTitle
import de.robinrehbein.punkt.ui.text.medalName
import de.robinrehbein.punkt.ui.text.sceneHint
import de.robinrehbein.punkt.ui.text.sceneTitle
import de.robinrehbein.punkt.ui.text.skinHint
import de.robinrehbein.punkt.ui.text.skinTitle
import de.robinrehbein.punkt.ui.text.soundHint
import de.robinrehbein.punkt.ui.text.soundTitle
import de.robinrehbein.punkt.ui.theme.Bytesized
import de.robinrehbein.punkt.ui.world.BlockBody
import de.robinrehbein.punkt.ui.world.BlockCap
import de.robinrehbein.punkt.ui.world.BlockDark
import de.robinrehbein.punkt.ui.world.BlockLight
import de.robinrehbein.punkt.ui.world.BushColor
import de.robinrehbein.punkt.ui.world.BushShadeColor
import de.robinrehbein.punkt.ui.world.CloudColor
import de.robinrehbein.punkt.ui.world.DotBody
import de.robinrehbein.punkt.ui.world.DotShade
import de.robinrehbein.punkt.ui.world.DotShine
import de.robinrehbein.punkt.ui.world.FxState
import de.robinrehbein.punkt.ui.world.GRID
import de.robinrehbein.punkt.ui.world.GrassDark
import de.robinrehbein.punkt.ui.world.GrassLight
import de.robinrehbein.punkt.ui.world.GroundSand
import de.robinrehbein.punkt.ui.world.GroundSandShade
import de.robinrehbein.punkt.ui.world.OutlineColor
import de.robinrehbein.punkt.ui.world.PanelSand
import de.robinrehbein.punkt.ui.world.RecordRed
import de.robinrehbein.punkt.ui.world.SkyColor
import de.robinrehbein.punkt.ui.world.TextDark
import de.robinrehbein.punkt.ui.world.TrunkColor
import de.robinrehbein.punkt.ui.world.TrunkShade
import de.robinrehbein.punkt.ui.world.drawCloud
import de.robinrehbein.punkt.ui.world.drawPixelCircle
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/**
 * Der Score-Stil mit hartem Pixel-Schatten. Als @Composable-Getter, weil
 * [Bytesized] die Schrift ueber Compose Resources laedt.
 */
val ScoreShadowStyle: TextStyle
    @Composable get() = TextStyle(
        fontFamily = Bytesized,
        shadow = Shadow(color = OutlineColor, offset = Offset(4f, 4f), blurRadius = 0f)
    )

// ===== Overlays =====

@Composable
fun ScoreHud(score: Int, daily: Boolean = false, banner: String = "") {
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
                    text = stringResource(Res.string.daily),
                    style = ScoreShadowStyle,
                    fontSize = 18.sp,
                    color = DotBody
                )
            }
            // Twist-Banner direkt unter der Punktzahl statt an einer festen
            // Bildschirmhöhe — so können sich beide nie überlappen.
            if (banner.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = banner,
                    style = ScoreShadowStyle,
                    fontSize = 30.sp,
                    color = Color(0xFFFF8A3C),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
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

/**
 * Der Startbildschirm. Er zeigt bewusst nur noch acht Dinge: Titel,
 * Rekord, den blinkenden Hinweis, das Zahnrad, die drei Knöpfe und die
 * eine Ziel-Zeile.
 *
 * Alles, was vorher hier stand und nur selten gebraucht wird, ist
 * umgezogen: Ton, Erinnerung, Hilfe, Werbe-Kauf und Datenschutz in das
 * Einstellungs-Overlay hinter dem Zahnrad, die Rangliste in die
 * Statistik. Die Daily-Serie hängt als Abzeichen am DAILY-Knopf, und
 * statt Versuchszähler und Tageswerten trägt eine einzige Zeile mit
 * Balken das nächste Ziel — dieselbe Rechnung wie im Game-Over.
 */
@Composable
fun ReadyOverlay(
    bestScore: Int,
    hint: String,
    dailyStreak: Int,
    // Das nächstliegende offene Ziel — null, wenn alles gesammelt ist.
    // Dann fällt die Zeile ersatzlos weg: Es gibt nichts mehr zu zeigen.
    goal: Goal?,
    onDaily: () -> Unit,
    onSkins: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    // Versteckte Diagnose: langer Druck auf den Titel blendet den
    // Klartext-Zustand von Werbung und Kauf ein. Nach aussen sieht
    // "keine Einwilligung" genauso aus wie "keine Anzeige verfuegbar" —
    // ohne Rechner ist das sonst nicht auseinanderzuhalten. Ein langer
    // Druck auf eine Ueberschrift passiert niemandem versehentlich.
    diagnostics: String? = null,
    onToggleDiagnostics: () -> Unit = {}
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
        // Ein Zahnrad statt dreier Einzel-Icons: Ton, Erinnerung und Hilfe
        // sind Einstellungen, keine Spielzüge — sie gehören hinter eine Tür.
        PixelIconButton(
            icon = PixelIcon.GEAR,
            contentDescription = stringResource(Res.string.settings),
            onClick = onSettings,
            backgroundColor = PanelSand,
            borderColor = TextDark,
            strikeColor = RecordRed,
            buttonSize = 48.dp,
            borderWidth = 3.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        ) {
            Text(
                // "DOTTIE." ist mit 7 Zeichen schmal genug für die vollen
                // 64.sp — auch auf 360-dp-Displays ohne Umbruch.
                text = "DOTTIE.",
                style = ScoreShadowStyle,
                fontSize = 64.sp,
                color = Color.White,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onToggleDiagnostics() })
                }
            )
            if (bestScore > 0) {
                Text(
                    text = stringResource(Res.string.best_score, bestScore),
                    style = ScoreShadowStyle,
                    fontSize = 22.sp,
                    color = Color.White
                )
            }
            if (diagnostics != null) {
                Text(
                    text = diagnostics,
                    fontFamily = Bytesized,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, start = 16.dp, end = 16.dp)
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
            // Drei Knöpfe statt zwei: Die Statistik gehört auf den
            // Startscreen, nicht in ein Untermenü — sie ist der Grund,
            // den nächsten Lauf zu starten. Dafür sind alle drei etwas
            // schmaler (108 statt 116 dp bei 10 dp Abstand), damit die
            // Reihe auch auf 360-dp-Displays mit Rand steht.
            Row {
                // Die laufende Serie hängt als Abzeichen am Knopf, zu dem
                // sie gehört: Sie ist eine Eigenschaft der Daily, keine
                // eigene Zeile — und in der Ecke sieht man sie trotzdem.
                Box {
                    PixelButton(
                        text = stringResource(Res.string.daily),
                        onClick = onDaily,
                        backgroundColor = DotBody,
                        borderColor = TextDark,
                        textColor = TextDark,
                        width = 108.dp,
                        height = 52.dp,
                        borderWidth = 3.dp
                    )
                    if (dailyStreak >= 1) {
                        StreakBadge(
                            days = dailyStreak,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 5.dp, y = (-5).dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                PixelButton(
                    text = stringResource(Res.string.skins),
                    onClick = onSkins,
                    backgroundColor = PanelSand,
                    borderColor = TextDark,
                    textColor = TextDark,
                    width = 108.dp,
                    height = 52.dp,
                    borderWidth = 3.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                PixelButton(
                    text = stringResource(Res.string.stats),
                    onClick = onStats,
                    backgroundColor = PanelSand,
                    borderColor = TextDark,
                    textColor = TextDark,
                    width = 108.dp,
                    height = 52.dp,
                    borderWidth = 3.dp
                )
            }
            // Eine Zeile statt einer Zahlenwand: Wo vorher Tageswert,
            // Serie und Versuchszähler standen, steht jetzt der eine
            // Grund, gleich noch einmal zu spielen. Die Achse steht mit
            // dabei — "MEDAILLE 199/200" allein läse sich als Medaillen.
            if (goal != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = goalHeadline(goal),
                    style = ScoreShadowStyle,
                    fontSize = 15.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(5.dp))
                GoalBar(fraction = goal.fraction, modifier = Modifier.width(244.dp))
            }
        }
    }
}

/**
 * Das rote Serien-Abzeichen an der Ecke des DAILY-Knopfs: dunkler
 * Pixelrahmen, roter Kern, die Zahl der Tage. Dreistellige Serien gibt es
 * praktisch nicht, deshalb reicht ein quadratisches Feld.
 */
@Composable
private fun StreakBadge(days: Int, modifier: Modifier = Modifier) {
    val cd = streakLabel(days)
    Box(
        modifier = modifier
            .size(24.dp)
            .semantics { contentDescription = cd },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val border = 3.dp.toPx()
            drawRect(color = OutlineColor)
            drawRect(
                color = RecordRed,
                topLeft = Offset(border, border),
                size = Size(size.width - 2 * border, size.height - 2 * border)
            )
        }
        Text(
            text = days.toString(),
            fontFamily = Bytesized,
            fontSize = 13.sp,
            color = Color.White
        )
    }
}

@Composable
fun GameOverOverlay(
    score: Int,
    bestScore: Int,
    isNewRecord: Boolean,
    taunt: String,
    daily: Boolean,
    dailyBest: Int,
    dailyStreak: Int,
    skinUnlocked: Boolean,
    newMedal: Boolean,
    // Das nächstliegende offene Ziel — null, wenn alles gesammelt ist.
    goal: Goal?,
    /** null = diese Plattform kann nicht teilen; dann faellt der Knopf weg. */
    onShare: (() -> Unit)?,
    onMenu: () -> Unit,
    onHelp: () -> Unit,
    /**
     * Der Rahmen um die Punkte-Box — der, den auch die geteilte Karte
     * traegt. Die Vorgabe ist der Bestand: Wer diese Zeile nicht setzt,
     * bekommt das Panel von vorher.
     */
    cardFrame: CardFrame = CardFrame.SCHLICHT
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
                text = stringResource(Res.string.game_over),
                style = ScoreShadowStyle,
                fontSize = 48.sp,
                color = Color(0xFFFF8A3C)
            )

            Spacer(modifier = Modifier.height(16.dp))

            PixelPanel(frame = cardFrame) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Medaille ploppt mit kleinem Überschwinger ein.
                        val pop = remember { Animatable(0f) }
                        LaunchedEffect(Unit) {
                            pop.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        }
                        MedalBadge(score = score, modifier = Modifier.scale(pop.value))
                        Spacer(modifier = Modifier.height(4.dp))
                        val tier = MedalPaint.forScore(score)
                        Text(
                            text = tier?.let { medalName(it) }
                                ?: stringResource(Res.string.medal),
                            fontFamily = Bytesized,
                            fontSize = 12.sp,
                            color = TextDark
                        )
                        // Nahziel: "NOCH 4 BIS GOLD" — gibt jedem Run ein Ziel.
                        MedalPaint.next(score)?.let { next ->
                            Text(
                                text = stringResource(
                                    Res.string.medal_next,
                                    MedalPaint.threshold(next) - score,
                                    medalName(next)
                                ),
                                fontFamily = Bytesized,
                                fontSize = 10.sp,
                                color = Color(0xFF8A7F5A)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(Res.string.points_label),
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
                            text = stringResource(Res.string.record_label),
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
                text = if (isNewRecord) stringResource(Res.string.new_record) else taunt,
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
                        stringResource(Res.string.daily),
                        stringResource(Res.string.today_score, dailyBest),
                        if (dailyStreak > 0) streakLabel(dailyStreak) else null
                    ).joinToString("  ·  "),
                    style = ScoreShadowStyle,
                    fontSize = 16.sp,
                    color = DotBody
                )
            }

            if (newMedal) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.new_medal),
                    style = ScoreShadowStyle,
                    fontSize = 18.sp,
                    color = Color(0xFFFFE95E)
                )
            }

            if (skinUnlocked) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.new_skin_unlocked),
                    style = ScoreShadowStyle,
                    fontSize = 18.sp,
                    color = Color(0xFFFFE95E)
                )
            }

            // Das nächste Ziel: eine Zeile, ein Balken, mehr nicht. Hier
            // stirbt gerade jemand und will neu starten — der Fortschritt
            // soll ihn dabei anschieben, nicht aufhalten.
            if (goal != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = goalLabel(goal),
                    style = ScoreShadowStyle,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(5.dp))
                GoalBar(fraction = goal.fraction, modifier = Modifier.width(220.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Kein NOCHMAL-Button: Tap irgendwo startet sofort neu (nach
            // kurzer Wut-Tap-Sperre) — der blinkende Hinweis ist die
            // einzige Restart-Affordanz und darf deshalb auffallen.
            Text(
                text = stringResource(Res.string.tap_retry),
                style = ScoreShadowStyle,
                fontSize = 26.sp,
                color = Color.White.copy(alpha = blink)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row {
                if (onShare != null) PixelButton(
                    text = stringResource(Res.string.share),
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
                    text = stringResource(Res.string.menu),
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

/**
 * Wie groß ein Rahmenfeld auf dem Panel ist.
 *
 * Die Karte rechnet mit 6 Pixeln je Feld auf 180 Feldern Breite; das
 * Panel ist gut ein Sechstel so breit, also ist auch sein Feld gut ein
 * Sechstel so groß. Genau deshalb steht das Muster in Feldern und nicht
 * in Pixeln (siehe [FramePart]): Dieselbe Tabelle trägt beide Größen,
 * und der Rahmen um das Panel hat dieselben Verhältnisse wie der auf der
 * geteilten Karte.
 */
private val PANEL_FRAME_CELL = 1.5.dp

/**
 * Beiger Panel-Hintergrund mit dunklem Pixelrahmen — und, ab der zweiten
 * Rahmenstufe, mit dem Rahmen der Spielerin darum.
 *
 * Dass der gewählte Rahmen hier auftaucht und nicht nur auf der geteilten
 * Karte, ist der Sinn der Sache: Sonst hätte man von einer ganzen
 * Sammlung nur beim Teilen etwas. [CardFrame.SCHLICHT] bleibt dabei
 * unangetastet der Treppenrahmen des Bestands — dieselbe Regel wie bei
 * der WIESE und bei [CardStyle.layout]: Wer nichts gesammelt hat, sieht
 * genau das, was er vorher sah.
 */
@Composable
fun PixelPanel(frame: CardFrame = CardFrame.SCHLICHT, content: @Composable () -> Unit) {
    // Der Bestand hat 4 dp Rand; die verzierten Stufen so viele Felder,
    // wie ihr Muster tief ist.
    val rand = if (frame == CardFrame.SCHLICHT) 4.dp
    else PANEL_FRAME_CELL * CardStyle.thickness(frame)
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.matchParentSize()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val border = rand.toPx()
                drawRect(color = OutlineColor)
                drawRect(
                    color = PanelSand,
                    topLeft = Offset(border, border),
                    size = Size(size.width - 2 * border, size.height - 2 * border)
                )
                if (frame == CardFrame.SCHLICHT) return@Canvas
                // Wie viele Felder auf das Panel passen — und dann die
                // Feldgröße noch einmal darauf gerechnet, damit die
                // letzte Spalte bündig an der Kante endet statt einen
                // halben Rest offen zu lassen.
                val zelle = PANEL_FRAME_CELL.toPx()
                val spalten = (size.width / zelle).roundToInt().coerceAtLeast(1)
                val zeilen = (size.height / zelle).roundToInt().coerceAtLeast(1)
                val breite = size.width / spalten
                val hoehe = size.height / zeilen
                CardStyle.frameRects(frame, spalten, zeilen).forEach { r ->
                    drawRect(
                        color = Color(r.tone.argb),
                        topLeft = Offset(r.col * breite, r.row * hoehe),
                        size = Size(r.cols * breite, r.rows * hoehe)
                    )
                }
            }
        }
        Box(
            modifier = Modifier.padding(
                horizontal = (rand + 6.dp).coerceAtLeast(32.dp),
                vertical = (rand + 6.dp).coerceAtLeast(24.dp)
            )
        ) {
            content()
        }
    }
}

/**
 * Körper- und Schattenfarbe pro Medaillen-Stufe. Die Werte kommen aus
 * MedalPaint (:core) — derselben Quelle, aus der sich auch die Uhr
 * bedient.
 */
fun medalColors(tier: MedalId): Pair<Color, Color> =
    Color(MedalPaint.body(tier)) to Color(MedalPaint.shade(tier))

/**
 * Medaille ab 10 Punkten: rotes Band im V, Münze mit geprägtem Stern und
 * Glanzpunkt; Platin funkelt. Unterhalb von Bronze erscheint dieselbe
 * Form als Sand-Silhouette — man sieht, dass es hier etwas zu holen gibt.
 */
@Composable
fun MedalBadge(score: Int, modifier: Modifier = Modifier) {
    val tier = MedalPaint.forScore(score)
    val (body, shade) = tier?.let { medalColors(it) }
        ?: (Color(0xFFBDB48A) to Color(0xFFA89E74))
    val ribbon = if (tier != null) RecordRed else Color(0xFFBDB48A)
    val ribbonDark = if (tier != null) Color(0xFFB02A28) else Color(0xFFA89E74)

    Canvas(modifier = modifier.size(72.dp)) {
        val u = size.minDimension / 16f
        fun block(c: Float, r: Float, w: Float, h: Float, color: Color) {
            drawRect(color, Offset(c * u, r * u), Size(w * u, h * u))
        }

        // Band im V: erst Outline-Pass, dann Farbe (links hell, rechts dunkel)
        val leftBand = listOf(3.5f to 0f, 4.5f to 1.5f, 5.5f to 3f)
        val rightBand = listOf(9.5f to 0f, 8.5f to 1.5f, 7.5f to 3f)
        for ((c, r) in leftBand + rightBand) block(c - 0.5f, r - 0.5f, 3f, 2.5f, OutlineColor)
        for ((c, r) in leftBand) block(c, r, 2f, 1.5f, ribbon)
        for ((c, r) in rightBand) block(c, r, 2f, 1.5f, ribbonDark)

        // Münze
        val coinR = size.minDimension * 0.33f
        val coinCx = size.minDimension * 0.5f
        val coinCy = size.minDimension * 0.6f
        drawPixelCircle(
            color = body,
            outline = OutlineColor,
            centerX = coinCx,
            centerY = coinCy,
            radius = coinR,
            shade = shade
        )

        // Geprägter Stern (Plus-Form in Schattenfarbe) und Glanzpunkt
        val cu = coinR * 2f / GRID
        fun emboss(c: Float, r: Float, w: Float, h: Float) {
            drawRect(shade, Offset(coinCx - coinR + c * cu, coinCy - coinR + r * cu), Size(w * cu, h * cu))
        }
        emboss(5f, 5f, 3f, 3f)
        emboss(5.5f, 3.5f, 2f, 2f)
        emboss(5.5f, 7.5f, 2f, 2f)
        emboss(3.5f, 5.5f, 2f, 2f)
        emboss(7.5f, 5.5f, 2f, 2f)
        drawRect(
            if (tier != null) DotShine else Color(0xFFEFE7C0),
            Offset(coinCx - coinR + 2.5f * cu, coinCy - coinR + 2.5f * cu),
            Size(2f * cu, 2f * cu)
        )

        if (tier == MedalId.PLATINUM) {
            for ((sc, sr) in listOf(0.2f to 4f, 12.6f to 7f, 10.5f to 0.2f)) {
                drawRect(
                    DotShine,
                    Offset(coinCx - coinR + sc * cu, coinCy - coinR + sr * cu),
                    Size(cu, cu)
                )
            }
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

            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(Res.string.tap_to_close),
                fontFamily = Bytesized,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
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
        Color(0xFFB44FD8),
        stringResource(Res.string.twist_fake_title),
        stringResource(Res.string.twist_fake_text)
    )
    TwistHelpRow(
        Color(0xFFFF8A3C),
        stringResource(Res.string.twist_chain_title),
        stringResource(Res.string.twist_chain_text)
    )

    Spacer(modifier = Modifier.height(10.dp))
    HelpLine(stringResource(Res.string.help_max_twists))
}

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

            // Die Knöpfe tragen ihren Zustand als Text — dieselbe Angabe,
            // die vorher als contentDescription am Icon hing.
            PixelButton(
                text = stringResource(if (soundOn) Res.string.sound_on else Res.string.sound_off),
                onClick = onToggleSound,
                backgroundColor = PanelSand,
                borderColor = TextDark,
                textColor = TextDark,
                width = 244.dp,
                height = 48.dp,
                borderWidth = 3.dp
            )
            if (reminderSupported) {
                Spacer(modifier = Modifier.height(12.dp))
                // Tägliche Daily-Challenge-Erinnerung (Opt-in, lokal).
                PixelButton(
                    text = stringResource(
                        if (reminderOn) Res.string.reminder_on else Res.string.reminder_off
                    ),
                    onClick = onToggleReminder,
                    backgroundColor = PanelSand,
                    borderColor = TextDark,
                    textColor = TextDark,
                    width = 244.dp,
                    height = 48.dp,
                    borderWidth = 3.dp
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
            // mit den Schaltern konkurrieren.
            if (removeAdsPrice != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(Res.string.remove_ads, removeAdsPrice),
                    style = ScoreShadowStyle,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier
                        .clickable { onRemoveAds() }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            // Noch eine Spur zurückhaltender als die Kauf-Zeile: Der
            // Widerruf muss dauerhaft erreichbar sein, aber niemand sucht
            // ihn — deshalb klein und blass.
            if (privacyVisible) {
                Text(
                    text = stringResource(Res.string.ad_privacy),
                    style = ScoreShadowStyle,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier
                        .clickable { onPrivacy() }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(Res.string.tap_to_close),
                fontFamily = Bytesized,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

/** "SERIE: n TAG/TAGE" bzw. "STREAK: n DAY/DAYS", sprachrichtig. */
@Composable
fun streakLabel(days: Int): String =
    if (days == 1) stringResource(Res.string.streak_one)
    else stringResource(Res.string.streak_many, days)

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
 *
 * [skinPass] ist der heute per Spot freigeschaltete Probier-Skin (siehe
 * ScoreStore.skinPassFor); [adOfferReady] sagt, ob dafür gerade ein Spot
 * bereitliegt. Beides ist ohne AdMob-IDs immer null bzw. false — dann
 * gibt es weder Zusatzzeilen noch anklickbare gesperrte Skins, das
 * Overlay ist Pixel für Pixel das alte.
 *
 * [patronPrice] ist der von Google gelieferte Preis des Gönner-Pakets
 * (null = nicht kaufbar, dann bleibt das Angebot unsichtbar).
 *
 * [adsAlreadyRemoved] ändert nur die Beschriftung dieses Angebots. Das
 * Paket enthält "Werbung entfernen"; wer das schon gekauft hat, würde es
 * ein zweites Mal bezahlen. Play kennt für Einmalprodukte keinen
 * Upgrade-Pfad, im Store ist das also nicht zu lösen — in der App schon:
 * Diese Gruppe liest, was für sie wirklich neu ist, nämlich die drei
 * Skins. Am Preis und am Produkt ändert sich nichts.
 */
@Composable
fun SkinOverlay(
    stats: SkinStats,
    selected: SkinId,
    onSelect: (SkinId) -> Unit,
    selectedScene: SceneId,
    onSelectScene: (SceneId) -> Unit,
    selectedSound: SoundSetId,
    onSelectSound: (SoundSetId) -> Unit,
    selectedCardFrame: CardFrame?,
    onSelectCardFrame: (CardFrame) -> Unit,
    onClose: () -> Unit,
    skinPass: SkinId? = null,
    adOfferReady: Boolean = false,
    onWatchAdFor: (SkinId) -> Unit = {},
    patronPrice: String? = null,
    adsAlreadyRemoved: Boolean = false,
    onPatron: () -> Unit = {}
) {
    // Uhr und Kalender einmal pro Öffnen ablesen, nicht pro Vorschau:
    // TAGESZEIT und JAHRESZEIT sollen in der Liste ihr heutiges Kleid
    // tragen, aber 42 Zeilen dürfen nicht 42-mal die Systemzeit fragen.
    val preview = remember {
        val (hour, month) = deviceHourAndMonth()
        SkinState(hour = hour, month = month)
    }
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
                text = stringResource(Res.string.skins),
                style = ScoreShadowStyle,
                fontSize = 32.sp,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Die Kulissen stehen ganz oben und vor allen Skin-Familien:
            // Es sind nur sechs, sie wirken auf das ganze Bild, und wer
            // das Menü öffnet, soll sie nicht erst suchen müssen.
            SkinFamilyHeading(stringResource(Res.string.scenes))
            ScenePaint.ORDER.forEach { scene ->
                val open = ScenePaint.isUnlocked(scene, stats)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = open) { onSelectScene(scene) }
                        .padding(horizontal = 48.dp, vertical = 10.dp)
                ) {
                    Canvas(modifier = Modifier.size(36.dp)) {
                        drawScenePreview(scene, alpha = if (open) 1f else 0.3f)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = sceneTitle(scene),
                            fontFamily = Bytesized,
                            fontSize = 20.sp,
                            color = if (open) Color.White else Color.White.copy(alpha = 0.45f)
                        )
                        Text(
                            text = when {
                                scene == selectedScene -> stringResource(Res.string.skin_selected)
                                open -> stringResource(Res.string.skin_tap_select)
                                else -> sceneHint(scene) ?: ""
                            },
                            fontFamily = Bytesized,
                            fontSize = 14.sp,
                            color = when {
                                scene == selectedScene -> DotBody
                                open -> Color.White.copy(alpha = 0.7f)
                                else -> Color.White.copy(alpha = 0.45f)
                            }
                        )
                    }
                }
            }

            // Die Ton-Sets stehen direkt hinter den Kulissen und vor den
            // Skins: Es sind drei, sie wirken wie die Kulisse auf den
            // ganzen Lauf, und die Hörprobe beim Antippen soll nicht
            // hinter 42 Vogel-Zeilen liegen — wer sie hört, will sofort
            // die nächste hören.
            SkinFamilyHeading(stringResource(Res.string.sounds))
            SoundBank.ORDER.forEach { sound ->
                val open = SoundBank.isUnlocked(sound, stats)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = open) { onSelectSound(sound) }
                        .padding(horizontal = 48.dp, vertical = 10.dp)
                ) {
                    Canvas(modifier = Modifier.size(36.dp)) {
                        drawSoundPreview(sound, alpha = if (open) 1f else 0.3f)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = soundTitle(sound),
                            fontFamily = Bytesized,
                            fontSize = 20.sp,
                            color = if (open) Color.White else Color.White.copy(alpha = 0.45f)
                        )
                        Text(
                            text = when {
                                sound == selectedSound -> stringResource(Res.string.skin_selected)
                                open -> stringResource(Res.string.sound_tap_hear)
                                else -> soundHint(sound) ?: ""
                            },
                            fontFamily = Bytesized,
                            fontSize = 14.sp,
                            color = when {
                                sound == selectedSound -> DotBody
                                open -> Color.White.copy(alpha = 0.7f)
                                else -> Color.White.copy(alpha = 0.45f)
                            }
                        )
                    }
                }
            }

            // Die Rahmen zuletzt unter den drei kleinen Sammlungen: Sie
            // sind die einzige, die im Spiel selbst nicht vorkommt —
            // sichtbar wird sie erst auf der geteilten Karte.
            //
            // Die angezeigte Wahl ist nie null: Wer nie gewählt hat,
            // steht auf seiner höchsten verdienten Stufe, und genau die
            // trägt seine Karte auch.
            SkinFamilyHeading(stringResource(Res.string.frames))
            val wirksamerRahmen = CardStyle.frame(selectedCardFrame, stats)
            CardFrame.entries.forEach { frame ->
                val open = CardStyle.isUnlocked(frame, stats)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = open) { onSelectCardFrame(frame) }
                        .padding(horizontal = 48.dp, vertical = 10.dp)
                ) {
                    Canvas(modifier = Modifier.size(36.dp)) {
                        drawCardFramePreview(frame, alpha = if (open) 1f else 0.3f)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = frameTitle(frame),
                            fontFamily = Bytesized,
                            fontSize = 20.sp,
                            color = if (open) Color.White else Color.White.copy(alpha = 0.45f)
                        )
                        Text(
                            text = when {
                                frame == wirksamerRahmen -> stringResource(Res.string.skin_selected)
                                open -> stringResource(Res.string.frame_on_card)
                                else -> frameHint(frame) ?: ""
                            },
                            fontFamily = Bytesized,
                            fontSize = 14.sp,
                            color = when {
                                frame == wirksamerRahmen -> DotBody
                                open -> Color.White.copy(alpha = 0.7f)
                                else -> Color.White.copy(alpha = 0.45f)
                            }
                        )
                    }
                }
            }

            // Bei 42 Skins ist die reine Liste nicht mehr lesbar: Die
            // Familien-Überschrift sagt, wonach die nächsten Zeilen
            // funktionieren — Muster, Zeit, Spielstand, Kauf.
            var lastFamily: SkinFamily? = null

            SkinPaint.ORDER.forEach { skin ->
                if (SkinPaint.family(skin) != lastFamily) {
                    lastFamily = SkinPaint.family(skin)
                    SkinFamilyHeading(familyTitle(SkinPaint.family(skin)))
                    // Das Gönner-Angebot steht unter seiner Überschrift und
                    // nirgends sonst: Wer die Skins ansieht, ist der einzige,
                    // den es interessiert. Ohne Preis von Google (oder wenn
                    // das Paket schon gehört) bleibt die Zeile weg.
                    if (SkinPaint.family(skin) == SkinFamily.GOENNER &&
                        (stats.patronOwned || patronPrice != null)
                    ) {
                        Text(
                            text = when {
                                stats.patronOwned -> stringResource(Res.string.patron_owned)
                                adsAlreadyRemoved -> stringResource(
                                    Res.string.patron_pack_skins_only, patronPrice.orEmpty()
                                )
                                else -> stringResource(Res.string.patron_pack, patronPrice.orEmpty())
                            },
                            fontFamily = Bytesized,
                            fontSize = 15.sp,
                            color = DotBody,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clickable(enabled = !stats.patronOwned) { onPatron() }
                                .padding(horizontal = 48.dp, vertical = 4.dp)
                        )
                    }
                }

                // "Verdient" und "heute spielbar" bleiben getrennt: Der
                // Tagespass macht den Skin nutzbar, nicht freigeschaltet.
                val available = SkinPaint.isUnlocked(skin, stats) || skin == skinPass
                val onPass = skin == skinPass && !SkinPaint.isUnlocked(skin, stats)
                // Gönner-Skins bleiben vom Tagespass ausgenommen: Ein Spot
                // darf keinen Kauf ersetzen, auch nicht für einen Tag.
                val adOffer = !available && adOfferReady && !SkinPaint.isPatron(skin)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = available || adOffer) {
                            if (available) onSelect(skin) else onWatchAdFor(skin)
                        }
                        .padding(horizontal = 48.dp, vertical = 10.dp)
                ) {
                    // Vorschau als echter Vogel statt als Farbfläche: Bei
                    // gemusterten Skins sagt ein einzelner Farbwert nichts
                    // mehr aus. Bewegte Skins stehen dabei still (Zeitpunkt 0),
                    // Uhr- und Kalender-Skins zeigen das Kleid von jetzt.
                    Canvas(modifier = Modifier.size(36.dp)) {
                        val d = size.minDimension
                        drawPixelCircle(
                            outline = OutlineColor,
                            centerX = d / 2f,
                            centerY = d / 2f,
                            radius = d / 2f,
                            alpha = if (available) 1f else 0.3f
                        ) { col, row -> Color(SkinPaint.cell(skin, col, row, preview)) }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = skinTitle(skin),
                            fontFamily = Bytesized,
                            fontSize = 20.sp,
                            color = if (available) Color.White else Color.White.copy(alpha = 0.45f)
                        )
                        Text(
                            text = when {
                                skin == selected -> stringResource(Res.string.skin_selected)
                                available -> stringResource(Res.string.skin_tap_select)
                                else -> skinHint(skin) ?: ""
                            },
                            fontFamily = Bytesized,
                            fontSize = 14.sp,
                            color = when {
                                skin == selected -> DotBody
                                available -> Color.White.copy(alpha = 0.7f)
                                else -> Color.White.copy(alpha = 0.45f)
                            }
                        )
                        // Der Spot-Hinweis bekommt eine eigene, kleinere
                        // Zeile: Die Freischalt-Bedingung ist die wichtigere
                        // Information und bleibt deshalb ungekürzt oben.
                        if (onPass || adOffer) {
                            Text(
                                text = stringResource(
                                    if (onPass) Res.string.skin_pass_today
                                    else Res.string.skin_pass_offer
                                ),
                                fontFamily = Bytesized,
                                fontSize = 12.sp,
                                color = GrassLight
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(Res.string.tap_to_close),
                fontFamily = Bytesized,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Vorschau einer Kulisse auf 36 dp: Tageshimmel, Bodenkante mit Narbe
 * und eine Requisite als Silhouette. Mehr passt nicht hinein — und
 * weniger wäre nicht auseinanderzuhalten.
 */
private fun DrawScope.drawScenePreview(scene: SceneId, alpha: Float) {
    val d = size.minDimension
    val paint = ScenePaint.of(scene)
    val border = d / 12f
    val horizon = d * 0.62f

    drawRect(color = OutlineColor, size = Size(d, d), alpha = alpha)
    drawRect(
        color = Color(paint.sky[0]),
        topLeft = Offset(border, border),
        size = Size(d - border * 2f, horizon - border),
        alpha = alpha
    )
    // Ohne Boden (WELTRAUM) läuft der Himmel bis unten durch und zeigt
    // dort seine Nachtstufe — die Kachel bleibt so trotzdem lesbar.
    val ground = paint.ground
    drawRect(
        color = Color(ground?.sand ?: paint.sky[6]),
        topLeft = Offset(border, horizon),
        size = Size(d - border * 2f, d - horizon - border),
        alpha = alpha
    )
    if (ground != null) {
        drawRect(
            color = Color(ground.turfLight),
            topLeft = Offset(border, horizon),
            size = Size(d - border * 2f, d * 0.07f),
            alpha = alpha
        )
    }
    // Requisite: die größte Form der Kulisse als zwei Blöcke.
    val prop = paint.props.first()
    drawRect(
        color = Color(prop.dark),
        topLeft = Offset(d * 0.22f, horizon - d * 0.22f),
        size = Size(d * 0.26f, d * 0.22f),
        alpha = alpha
    )
    drawRect(
        color = Color(prop.body),
        topLeft = Offset(d * 0.28f, horizon - d * 0.34f),
        size = Size(d * 0.16f, d * 0.14f),
        alpha = alpha
    )
    drawRect(
        color = Color(prop.light),
        topLeft = Offset(d * 0.58f, horizon - d * 0.16f),
        size = Size(d * 0.18f, d * 0.16f),
        alpha = alpha
    )
}

/**
 * Vorschau eines Ton-Sets auf 36 dp: drei Balken für Treffer, Perfekt
 * und Rekord, deren Höhe aus [SoundBank.chips] kommt. Ein Ton-Set hat
 * kein Bild — die Kachel zeigt deshalb die Lage des Sets: Die Glocke
 * steht hoch, der Amboss bleibt am Boden. Die Zahlen stammen aus :core,
 * damit die PWA dieselbe Kachel zeichnet.
 */
private fun DrawScope.drawSoundPreview(sound: SoundSetId, alpha: Float) {
    val d = size.minDimension
    val border = d / 12f
    val innen = d - border * 2f

    drawRect(color = OutlineColor, size = Size(d, d), alpha = alpha)
    drawRect(
        color = PanelSand,
        topLeft = Offset(border, border),
        size = Size(innen, innen),
        alpha = alpha
    )
    // Dieselbe Sprache wie der Fortschrittsbalken: Sandbett, goldene
    // Blöcke — nur senkrecht, weil hier keine Strecke gemeint ist.
    val chips = SoundBank.chips(sound)
    val breite = innen / (chips.size * 2f - 1f)
    chips.forEachIndexed { index, anteil ->
        // Auch das tiefste Set bleibt sichtbar: ein Fünftel Mindesthöhe.
        val hoehe = innen * (0.2f + 0.75f * anteil)
        drawRect(
            color = DotBody,
            topLeft = Offset(border + index * breite * 2f, border + innen - hoehe),
            size = Size(breite, hoehe),
            alpha = alpha
        )
    }
}

/**
 * Vorschau einer Rahmenstufe: eine leere Karte im Seitenverhältnis der
 * echten, mit genau dem Rahmen, den sie bekäme.
 *
 * Bewusst ohne Inhalt — kein Punkt, kein Titel, keine Zahl. Die Kachel
 * beantwortet eine einzige Frage („wie dick und wie verziert ist der
 * Rand"), und alles andere darin wäre bei 36 dp ohnehin nur Grieß.
 */
private fun DrawScope.drawCardFramePreview(frame: CardFrame, alpha: Float) {
    val d = size.minDimension
    // Die Karte ist breiter als hoch; die Kachel ist quadratisch. Also
    // ein liegendes Rechteck mittig einsetzen, statt zu verzerren.
    val h = d * 0.72f
    val top = (d - h) / 2f
    // Bewusst kräftiger als auf der echten Karte: Auf 36 dp wäre der
    // wahre Anteil (15 von 180 Feldern) ein Haar. Die Reihenfolge
    // stimmt trotzdem — jede Stufe ist breiter als die darunter.
    val staerke = when (frame) {
        CardFrame.SCHLICHT -> d / 18f
        CardFrame.DOPPELLINIE -> d / 12f
        CardFrame.ZINNEN -> d / 8f
        CardFrame.PRACHT -> d / 6f
        CardFrame.KASKADE -> d / 5.5f
        CardFrame.PERLENKRANZ -> d / 5f
        CardFrame.KRONE -> d / 4.5f
    }

    drawRect(color = OutlineColor, topLeft = Offset(0f, top), size = Size(d, h), alpha = alpha)
    drawRect(
        color = PanelSand,
        topLeft = Offset(staerke, top + staerke),
        size = Size(d - staerke * 2f, h - staerke * 2f),
        alpha = alpha
    )
    // Ab der zweiten Stufe liegt ein farbiges Band im Rahmen — dasselbe
    // Erkennungszeichen wie auf der Karte selbst.
    if (frame != CardFrame.SCHLICHT) {
        val band = staerke / 3f
        drawRect(
            color = DotBody,
            topLeft = Offset(band, top + band),
            size = Size(d - band * 2f, h - band * 2f),
            alpha = alpha,
            style = Stroke(width = band)
        )
    }
    // Ab der Prachtstufe kommen Eckornamente dazu, sonst sähen die
    // oberen vier Stufen alle aus wie eine bloß dickere Zinnenstufe.
    // Die Farbe unterscheidet sie: Gold für die Pracht, danach das
    // Kennzeichen der jeweiligen Sammlung.
    val eckFarbe = when (frame) {
        CardFrame.PRACHT -> DotBody
        CardFrame.KASKADE -> Color(FrameTone.INLAY.argb)
        CardFrame.PERLENKRANZ -> Color(FrameTone.PEARL.argb)
        CardFrame.KRONE -> Color(FrameTone.GOLD.argb)
        else -> null
    }
    if (eckFarbe != null) {
        val eck = staerke * 0.8f
        listOf(
            Offset(0f, top),
            Offset(d - eck, top),
            Offset(0f, top + h - eck),
            Offset(d - eck, top + h - eck)
        ).forEach {
            drawRect(color = eckFarbe, topLeft = it, size = Size(eck, eck), alpha = alpha)
        }
    }
}

/**
 * Überschrift einer Skin-Familie — schmal, damit die Liste ruhig bleibt.
 * Die Statistik-Seite benutzt dieselbe Überschrift für ihre Ziel-Liste:
 * Beide Seiten sind gegliederte Listen im selben Scrim.
 */
@Composable
fun SkinFamilyHeading(text: String) {
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = text,
        style = ScoreShadowStyle,
        fontSize = 18.sp,
        color = Color(0xFFFF8A3C),
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

/**
 * Die Ankuendigungen der Twists, einmal gelesen. Wie [rememberTaunter]:
 * gebraucht werden sie im Ereignis-Handler, lesbar sind sie nur waehrend
 * der Zusammensetzung.
 */
@Composable
fun rememberTwistBanners(): (Twist) -> String {
    val pulse = stringResource(Res.string.banner_twist_pulse)
    val drift = stringResource(Res.string.banner_twist_drift)
    val ghost = stringResource(Res.string.banner_twist_ghost)
    val fake = stringResource(Res.string.banner_twist_fake)
    val chain = stringResource(Res.string.banner_twist_chain)
    return remember(pulse, drift, ghost, fake, chain) {
        { twist ->
            when (twist) {
                Twist.PULSE -> pulse
                Twist.DRIFT -> drift
                Twist.GHOST -> ghost
                Twist.FAKE -> fake
                Twist.CHAIN -> chain
            }
        }
    }
}

// ===== Spott-Texte für den Rage-Faktor =====

/**
 * Liefert eine Funktion, die zu einem Lauf den passenden Spott-Text
 * waehlt.
 *
 * Der Umweg ueber eine zurueckgegebene Funktion hat einen Grund: Der
 * Text wird im Moment des Todes gebraucht — in einem Ereignis-Handler,
 * nicht beim Zeichnen. Texte lassen sich aber nur waehrend der
 * Zusammensetzung lesen. Also werden die vier Listen einmal gelesen und
 * die Auswahl bleibt eine reine Rechnung.
 */
@Composable
fun rememberTaunter(): (score: Int, previousBest: Int, isNewRecord: Boolean) -> String {
    val record = stringResource(Res.string.new_record)
    val zero = stringArrayResource(Res.array.taunts_zero)
    val close = stringArrayResource(Res.array.taunts_close)
    val low = stringArrayResource(Res.array.taunts_low)
    val default = stringArrayResource(Res.array.taunts_default)
    return remember(record, zero, close, low, default) {
        { score, previousBest, isNewRecord ->
            if (isNewRecord) {
                record
            } else {
                val gap = previousBest - score
                val pool = when {
                    score == 0 -> zero
                    gap in 1..3 -> close
                    score < previousBest / 2 -> low
                    else -> default
                }
                val line = pool[(score + previousBest) % pool.size]
                // Nur die "knapp daneben"-Zeilen tragen einen
                // %1$d-Platzhalter. `String.format` gibt es nur auf der
                // JVM — hier reicht Ersetzen.
                if (line.contains("%1\$d")) line.replace("%1\$d", gap.toString()) else line
            }
        }
    }
}
