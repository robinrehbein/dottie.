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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.R
import de.robinrehbein.punkt.game.DotScene
import de.robinrehbein.punkt.game.DotSkin
import de.robinrehbein.punkt.game.Goal
import de.robinrehbein.punkt.game.GoalAxis
import de.robinrehbein.punkt.game.Progress
import de.robinrehbein.punkt.ui.components.PixelButton
import de.robinrehbein.punkt.ui.theme.Bytesized
import kotlin.math.max

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
internal fun StatsOverlay(
    stats: DotSkin.Stats,
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
                text = stringResource(R.string.stats),
                style = ScoreShadowStyle,
                fontSize = 32.sp,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            StatRow(stringResource(R.string.record_label), stats.bestScore.toString())
            StatRow(stringResource(R.string.stats_runs), stats.runCount.toString())
            StatRow(stringResource(R.string.stats_total_score), stats.totalScore.toString())
            StatRow(stringResource(R.string.stats_days), stats.daysPlayed.toString())
            StatRow(stringResource(R.string.stats_months), stats.monthsPlayed.toString())
            StatRow(stringResource(R.string.stats_perfect), stats.bestPerfectStreak.toString())
            StatRow(stringResource(R.string.stats_daily_streak), stats.bestDailyStreak.toString())
            // Beide Sammlungen als Stand "12/35": Die Zahl allein sagt
            // nichts, erst das Verhältnis zeigt, wie weit es noch ist.
            StatRow(
                stringResource(R.string.skins),
                "${DotSkin.unlockedCount(stats)}/${DotSkin.collectableCount()}"
            )
            StatRow(
                stringResource(R.string.scenes),
                "${DotScene.unlockedCount(stats)}/${DotScene.entries.size}"
            )

            if (goals.isNotEmpty()) {
                SkinFamilyHeading(stringResource(R.string.stats_goals))
                goals.forEach { goal -> GoalRow(goal) }
            }

            if (leaderboardAvailable) {
                Spacer(modifier = Modifier.height(20.dp))
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
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.tap_to_close),
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
internal fun GoalRow(goal: Goal) {
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
internal fun goalLabel(goal: Goal): String =
    stringResource(R.string.goal_progress, goalName(goal), goal.current, goal.target)

/**
 * Die lange Fassung für den Startbildschirm:
 * "NAECHSTER SKIN: MEDAILLE — 199/200 LAEUFE".
 *
 * Dort steht das Ziel allein und ohne Überschrift, deshalb muss die Zeile
 * beides selbst sagen: was es freischaltet und worauf gezählt wird. Ohne
 * die Achse läse sich "MEDAILLE 199/200" als 199 von 200 Medaillen.
 */
@Composable
internal fun goalHeadline(goal: Goal): String = stringResource(
    if (goal.skin != null) R.string.goal_next_skin else R.string.goal_next_scene,
    goalName(goal),
    goal.current,
    goal.target,
    stringResource(goalAxisRes(goal.axis))
)

/** Name der Belohnung — Skin oder Kulisse, je nachdem was das Ziel öffnet. */
@Composable
private fun goalName(goal: Goal): String {
    val skin = goal.skin
    return if (skin != null) {
        stringResource(DotSkin.of(skin).titleRes)
    } else {
        stringResource(DotScene.of(goal.scene!!).titleRes)
    }
}

/** Die Einheit hinter den Zahlen: LAEUFE, PUNKTE, TAGE, … */
private fun goalAxisRes(axis: GoalAxis): Int = when (axis) {
    GoalAxis.BEST_SCORE -> R.string.goal_axis_points
    GoalAxis.TOTAL_SCORE -> R.string.goal_axis_total
    GoalAxis.PERFECT_STREAK -> R.string.goal_axis_perfect
    GoalAxis.DAILY_STREAK, GoalAxis.DAYS_PLAYED, GoalAxis.SEASON_DAYS -> R.string.goal_axis_days
    GoalAxis.MONTHS_PLAYED -> R.string.goal_axis_months
    GoalAxis.RUN_COUNT -> R.string.goal_axis_runs
    GoalAxis.SKIN_COLLECTION -> R.string.goal_axis_skins
    GoalAxis.SCENE_COLLECTION -> R.string.goal_axis_scenes
}

/**
 * Der Fortschrittsbalken im Pixel-Look: dunkler Rahmen, Sandbett, gold
 * gefüllte Blöcke. Der Füllstand rastet bewusst auf ganze Blöcke ein —
 * ein weicher Balken wäre der einzige stufenlose Verlauf im ganzen Spiel.
 */
@Composable
internal fun GoalBar(
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
