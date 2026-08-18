package de.robinrehbein.punkt.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.game.Goal
import de.robinrehbein.punkt.game.GoalAxis
import de.robinrehbein.punkt.game.MedalId
import de.robinrehbein.punkt.game.MedalPaint
import de.robinrehbein.punkt.game.Progress
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.SkinFamily
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SkinStats
import de.robinrehbein.punkt.game.SoundBank
import de.robinrehbein.punkt.game.SoundSetId
import de.robinrehbein.punkt.ui.components.PixelButton
import de.robinrehbein.punkt.ui.resources.Res
import de.robinrehbein.punkt.ui.resources.goal_axis_days
import de.robinrehbein.punkt.ui.resources.goal_axis_months
import de.robinrehbein.punkt.ui.resources.goal_axis_perfect
import de.robinrehbein.punkt.ui.resources.goal_axis_points
import de.robinrehbein.punkt.ui.resources.goal_axis_runs
import de.robinrehbein.punkt.ui.resources.goal_axis_scenes
import de.robinrehbein.punkt.ui.resources.goal_axis_skins
import de.robinrehbein.punkt.ui.resources.goal_axis_total
import de.robinrehbein.punkt.ui.resources.goal_next_scene
import de.robinrehbein.punkt.ui.resources.goal_next_skin
import de.robinrehbein.punkt.ui.resources.goal_next_sound
import de.robinrehbein.punkt.ui.resources.goal_progress
import de.robinrehbein.punkt.ui.resources.leaderboard
import de.robinrehbein.punkt.ui.resources.record_label
import de.robinrehbein.punkt.ui.resources.scenes
import de.robinrehbein.punkt.ui.resources.skins
import de.robinrehbein.punkt.ui.resources.sounds
import de.robinrehbein.punkt.ui.resources.stats
import de.robinrehbein.punkt.ui.resources.stats_daily_streak
import de.robinrehbein.punkt.ui.resources.stats_days
import de.robinrehbein.punkt.ui.resources.stats_goals
import de.robinrehbein.punkt.ui.resources.stats_months
import de.robinrehbein.punkt.ui.resources.stats_perfect
import de.robinrehbein.punkt.ui.resources.stats_runs
import de.robinrehbein.punkt.ui.resources.stats_total_score
import de.robinrehbein.punkt.ui.resources.tap_to_close
import de.robinrehbein.punkt.ui.text.familyTitle
import de.robinrehbein.punkt.ui.text.medalName
import de.robinrehbein.punkt.ui.text.sceneHint
import de.robinrehbein.punkt.ui.text.sceneTitle
import de.robinrehbein.punkt.ui.text.skinHint
import de.robinrehbein.punkt.ui.text.skinTitle
import de.robinrehbein.punkt.ui.text.soundHint
import de.robinrehbein.punkt.ui.text.soundTitle
import de.robinrehbein.punkt.ui.theme.Bytesized
import de.robinrehbein.punkt.ui.world.DotBody
import de.robinrehbein.punkt.ui.world.GroundSandShade
import de.robinrehbein.punkt.ui.world.OutlineColor
import de.robinrehbein.punkt.ui.world.PanelSand
import de.robinrehbein.punkt.ui.world.TextDark
import kotlin.math.max
import org.jetbrains.compose.resources.stringResource

/**
 * Die Statistik-Seite: alle Zähler auf einen Blick und darunter die
 * nächsten Freischaltungen mit Balken.
 *
 * Der Anlass: Seit v2.20 laufen vier Ausdauer-Achsen mit, und sichtbar
 * war davon nichts. Wer bei Rekord 25 hängenbleibt, sah nur eine Zahl —
 * dass der nächste Skin in 30 Läufen fällt, stand nirgends. Ein Balken
 * bei 72 % ist der stärkste Grund, noch einen Lauf zu starten, den
 * dieses Spiel hat; eine Zahlenwand ist es nicht, deshalb stehen die
 * Ziele unter den Zahlen und nicht umgekehrt.
 *
 * Aufbau wie Hilfe und Skin-Auswahl: dunkler Scrim, scrollbare Spalte,
 * ein Tap irgendwo schließt (und wird konsumiert, damit er nicht als
 * Spiel-Tap durchschlägt).
 */
@Composable
fun StatsOverlay(
    stats: SkinStats,
    goals: List<Goal>,
    onClose: () -> Unit,
    // Die Rangliste stand früher auf dem Startbildschirm. Sie gehört
    // hierher: Wer den eigenen Stand ansieht, ist der Einzige, den der
    // Stand der anderen interessiert. Die Bedingung ist unverändert —
    // nur sichtbar, wenn Play Games konfiguriert und angemeldet ist.
    leaderboardAvailable: Boolean = false,
    onLeaderboard: () -> Unit = {}
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
                text = stringResource(Res.string.stats),
                style = ScoreShadowStyle,
                fontSize = 32.sp,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            StatRow(stringResource(Res.string.record_label), stats.bestScore.toString())
            StatRow(stringResource(Res.string.stats_runs), stats.runCount.toString())
            StatRow(stringResource(Res.string.stats_total_score), stats.totalScore.toString())
            StatRow(stringResource(Res.string.stats_days), stats.daysPlayed.toString())
            StatRow(stringResource(Res.string.stats_months), stats.monthsPlayed.toString())
            StatRow(stringResource(Res.string.stats_perfect), stats.bestPerfectStreak.toString())
            StatRow(stringResource(Res.string.stats_daily_streak), stats.bestDailyStreak.toString())
            // Alle drei Sammlungen als Stand "12/35": Die Zahl allein
            // sagt nichts, erst das Verhältnis zeigt, wie weit es noch ist.
            StatRow(
                stringResource(Res.string.skins),
                "${SkinPaint.unlockedCount(stats)}/${SkinPaint.collectableCount()}"
            )
            StatRow(
                stringResource(Res.string.scenes),
                "${ScenePaint.unlockedCount(stats)}/${ScenePaint.ORDER.size}"
            )
            StatRow(
                stringResource(Res.string.sounds),
                "${SoundBank.unlockedCount(stats)}/${SoundBank.ORDER.size}"
            )

            if (goals.isNotEmpty()) {
                SkinFamilyHeading(stringResource(Res.string.stats_goals))
                goals.forEach { goal -> GoalRow(goal) }
            }

            if (leaderboardAvailable) {
                Spacer(modifier = Modifier.height(20.dp))
                PixelButton(
                    text = stringResource(Res.string.leaderboard),
                    onClick = onLeaderboard,
                    backgroundColor = PanelSand,
                    borderColor = TextDark,
                    textColor = TextDark,
                    width = 244.dp,
                    height = 48.dp,
                    borderWidth = 3.dp
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

/** Eine Zeile "LAEUFE ........ 218" — Beschriftung links, Zahl rechts. */
@Composable
private fun StatRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontFamily = Bytesized,
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.8f)
        )
        Text(
            text = value,
            fontFamily = Bytesized,
            fontSize = 18.sp,
            color = DotBody
        )
    }
}

/** Ein Ziel mit Balken: "FUSSBALL 218/300" und darunter der Fortschritt. */
@Composable
fun GoalRow(goal: Goal) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 6.dp)
    ) {
        Text(
            text = goalLabel(goal),
            fontFamily = Bytesized,
            fontSize = 16.sp,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        GoalBar(fraction = goal.fraction, modifier = Modifier.fillMaxWidth())
    }
}

/** "FUSSBALL 218/300" — Name der Belohnung plus Stand auf ihrer Achse. */
@Composable
fun goalLabel(goal: Goal): String =
    stringResource(Res.string.goal_progress, goalName(goal), goal.current, goal.target)

/**
 * Die lange Fassung für den Startbildschirm:
 * "NAECHSTER SKIN: MEDAILLE — 199/200 LAEUFE".
 *
 * Dort steht das Ziel allein und ohne Überschrift, deshalb muss die Zeile
 * beides selbst sagen: was es freischaltet und worauf gezählt wird. Ohne
 * die Achse läse sich "MEDAILLE 199/200" als 199 von 200 Medaillen.
 */
@Composable
fun goalHeadline(goal: Goal): String = stringResource(
    when {
        goal.skin != null -> Res.string.goal_next_skin
        goal.scene != null -> Res.string.goal_next_scene
        else -> Res.string.goal_next_sound
    },
    goalName(goal),
    goal.current,
    goal.target,
    stringResource(goalAxisText(goal.axis))
)

/** Name der Belohnung — Skin, Kulisse oder Ton-Set, je nach Ziel. */
@Composable
private fun goalName(goal: Goal): String {
    val skin = goal.skin
    val scene = goal.scene
    return when {
        skin != null -> skinTitle(skin)
        scene != null -> sceneTitle(scene)
        else -> soundTitle(goal.sound!!)
    }
}

/**
 * Worauf ein Ziel zählt. Bewusst eine Zuordnung statt eines abgeleiteten
 * Namens: Drei Achsen zählen Tage und teilen sich deshalb dasselbe Wort.
 */
private fun goalAxisText(axis: GoalAxis) = when (axis) {
    GoalAxis.BEST_SCORE -> Res.string.goal_axis_points
    GoalAxis.TOTAL_SCORE -> Res.string.goal_axis_total
    GoalAxis.PERFECT_STREAK -> Res.string.goal_axis_perfect
    GoalAxis.DAILY_STREAK, GoalAxis.DAYS_PLAYED, GoalAxis.SEASON_DAYS -> Res.string.goal_axis_days
    GoalAxis.MONTHS_PLAYED -> Res.string.goal_axis_months
    GoalAxis.RUN_COUNT -> Res.string.goal_axis_runs
    GoalAxis.SKIN_COLLECTION -> Res.string.goal_axis_skins
    GoalAxis.SCENE_COLLECTION -> Res.string.goal_axis_scenes
}

/**
 * Der Fortschrittsbalken im Pixel-Look: dunkler Rahmen, Sandbett, gold
 * gefüllte Blöcke. Der Füllstand rastet bewusst auf ganze Blöcke ein —
 * ein weicher Balken wäre der einzige stufenlose Verlauf im ganzen Spiel.
 */
@Composable
fun GoalBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp
) {
    Canvas(modifier = modifier.height(height)) {
        val border = 2.dp.toPx()
        val innerW = max(0f, size.width - border * 2f)
        val innerH = max(0f, size.height - border * 2f)

        drawRect(color = OutlineColor)
        drawRect(
            color = GroundSandShade,
            topLeft = Offset(border, border),
            size = Size(innerW, innerH)
        )

        // Wie viele Blöcke leuchten, rechnet :core — so ist derselbe Stand
        // in App, PWA und iOS gleich weit gefüllt.
        val unit = innerW / Progress.BAR_BLOCKS
        val filled = Progress.filledBlocks(fraction)
        if (filled > 0) {
            drawRect(
                color = DotBody,
                topLeft = Offset(border, border),
                size = Size(unit * filled, innerH)
            )
        }
    }
}
