package de.robinrehbein.punkt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.game.DeathCause
import de.robinrehbein.punkt.game.Mission
import de.robinrehbein.punkt.game.MissionDay
import de.robinrehbein.punkt.game.MissionType
import de.robinrehbein.punkt.ui.components.CORNER_BUTTON_PADDING
import de.robinrehbein.punkt.ui.components.OverlayCloseButton
import de.robinrehbein.punkt.ui.components.PIXEL_SHADOW
import de.robinrehbein.punkt.ui.components.PixelButton
import de.robinrehbein.punkt.ui.components.pixelPressable
import de.robinrehbein.punkt.ui.resources.Res
import de.robinrehbein.punkt.ui.resources.ctl_close
import de.robinrehbein.punkt.ui.resources.death_margin
import de.robinrehbein.punkt.ui.resources.death_near
import de.robinrehbein.punkt.ui.resources.joker_used_line
import de.robinrehbein.punkt.ui.resources.mission_done_line
import de.robinrehbein.punkt.ui.resources.missions_all_done
import de.robinrehbein.punkt.ui.resources.missions_all_done_joker
import de.robinrehbein.punkt.ui.resources.mission_daily_run
import de.robinrehbein.punkt.ui.resources.mission_daily_score
import de.robinrehbein.punkt.ui.resources.mission_blind_hits
import de.robinrehbein.punkt.ui.resources.mission_perfect_streak
import de.robinrehbein.punkt.ui.resources.mission_perfects_today
import de.robinrehbein.punkt.ui.resources.mission_points_today
import de.robinrehbein.punkt.ui.resources.mission_progress
import de.robinrehbein.punkt.ui.resources.mission_runs
import de.robinrehbein.punkt.ui.resources.mission_score_in_run
import de.robinrehbein.punkt.ui.theme.Bytesized
import de.robinrehbein.punkt.ui.world.DotBody
import de.robinrehbein.punkt.ui.world.OutlineColor
import de.robinrehbein.punkt.ui.world.PanelSand
import de.robinrehbein.punkt.ui.world.RecordRed
import de.robinrehbein.punkt.ui.world.TextDark
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

// Die Oberfläche der Wiederkehr-Mechaniken (ab v2.30): Tagesaufgaben,
// Serien-Joker samt Rettungs-Angebot, „knapp daneben" und die zwei
// einmaligen Fragen (Erinnerung, Nutzungsstatistik). Die Regeln dazu
// stehen in :core (DailyMissions, DailyChallenge) und im GameStore — hier
// wird nur gezeichnet.

// ===== Tagesaufgaben =====

/** „3X PERFEKT IN FOLGE" — was eine Aufgabe verlangt. */
@Composable
fun missionLabel(mission: Mission): String = when (mission.type) {
    MissionType.RUNS -> stringResource(Res.string.mission_runs, mission.target)
    MissionType.POINTS_TODAY -> stringResource(Res.string.mission_points_today, mission.target)
    MissionType.DAILY_RUN -> stringResource(Res.string.mission_daily_run)
    MissionType.PERFECTS_TODAY -> stringResource(Res.string.mission_perfects_today, mission.target)
    MissionType.PERFECT_STREAK -> stringResource(Res.string.mission_perfect_streak, mission.target)
    MissionType.BLIND_HITS -> stringResource(Res.string.mission_blind_hits, mission.target)
    MissionType.SCORE_IN_RUN -> stringResource(Res.string.mission_score_in_run, mission.target)
    MissionType.DAILY_SCORE -> stringResource(Res.string.mission_daily_score, mission.target)
}

/**
 * Die drei Aufgaben des Tages mit Balken — Aufbau wie [GoalRow], damit
 * die Statistik-Seite eine Sprache spricht. Erledigte Aufgaben tragen
 * das Gold des Vogels statt Weiß.
 */
@Composable
fun MissionRows(day: MissionDay) {
    val missions = day.missions
    missions.forEachIndexed { i, mission ->
        val done = day.isDone(i)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = missionLabel(mission),
                    fontFamily = Bytesized,
                    fontSize = 15.sp,
                    color = if (done) DotBody else Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(Res.string.mission_progress, day.progress[i], mission.target),
                    fontFamily = Bytesized,
                    fontSize = 15.sp,
                    color = if (done) DotBody else Color.White.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            GoalBar(
                fraction = day.progress[i].toFloat() / mission.target.coerceAtLeast(1),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ===== Knapp daneben =====

/**
 * Bis zu welchem Abstand die Zahl überhaupt gezeigt wird. Eine halbe
 * Sekunde daneben ist kein „fast", sondern ein anderer Takt — die Zahl
 * hülfe dort niemandem und stünde nur im Weg.
 */
const val MARGIN_SHOWN_SECONDS = 0.5f

/**
 * Die Hundertstel für „0,03 S" als zweistelliger Text, oder null, wenn
 * es nichts Sinnvolles zu zeigen gibt: kein Fehltap (VERPASST, BOOM!)
 * oder zu weit daneben. Mindestens „01" — ein Tap, der als Fehler zählte,
 * lag nie genau null daneben.
 */
fun marginHundredths(cause: DeathCause, missSeconds: Float): String? {
    if (cause != DeathCause.EARLY && cause != DeathCause.LATE) return null
    if (missSeconds <= 0f || missSeconds > MARGIN_SHOWN_SECONDS) return null
    val hundredths = (missSeconds * 100f).roundToInt().coerceIn(1, 99)
    return hundredths.toString().padStart(2, '0')
}

/** „KNAPP! 0,03 S" oder „0,21 S" — null, wenn es nichts zu zeigen gibt. */
@Composable
fun deathMarginText(cause: DeathCause, missSeconds: Float, near: Boolean): String? {
    val digits = marginHundredths(cause, missSeconds) ?: return null
    val margin = stringResource(Res.string.death_margin, digits)
    return if (near) stringResource(Res.string.death_near) + " " + margin else margin
}

// ===== Banner und Frage-Karten =====

/**
 * Ein rotes Banner unter Titel und Rekord, im Stil von „NEUE WELTEN" —
 * für das Rettungs-Angebot der Daily-Serie.
 */
@Composable
fun AttentionBanner(text: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .padding(top = 6.dp, start = 24.dp, end = 24.dp)
            .pixelPressable(interactionSource = interactionSource, onClick = onClick)
            .offset(y = if (pressed) PIXEL_SHADOW else 0.dp)
            .drawBehind {
                val s = PIXEL_SHADOW.toPx()
                val b = 3.dp.toPx()
                if (!pressed) drawRect(BannerEdge, topLeft = Offset(s, s), size = size)
                drawRect(OutlineColor)
                drawRect(RecordRed, topLeft = Offset(b, b), size = Size(size.width - 2 * b, size.height - 2 * b))
            }
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            fontFamily = Bytesized,
            fontSize = 14.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

private val BannerEdge = Color(0xFF1A1016)

/**
 * Eine Frage-Karte im Stil der DAILY-Karte: Titel, ein Satz, Kleingedrucktes,
 * JA als großer Knopf, NEIN als Textzeile darunter. X, Zurück und ein Tap
 * daneben schließen, ohne zu antworten ([onClose]).
 *
 * NEIN ist bewusst gleich leicht erreichbar wie JA und kein versteckter
 * Link: Die Karten fragen nach einer Einwilligung, und die muss man
 * ebenso bequem verweigern können.
 */
@Composable
fun PromptCard(
    title: String,
    text: String,
    fine: String?,
    yes: String,
    onYes: () -> Unit,
    no: String,
    onNo: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OutlineColor.copy(alpha = 0.92f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) }
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.padding(horizontal = 24.dp)) {
            PixelPanel {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = title,
                        fontFamily = Bytesized,
                        fontSize = 26.sp,
                        color = TextDark,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = text,
                        fontFamily = Bytesized,
                        fontSize = 16.sp,
                        color = TextDark,
                        textAlign = TextAlign.Center
                    )
                    if (fine != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = fine,
                            fontFamily = Bytesized,
                            fontSize = 12.sp,
                            color = TextDark.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    PixelButton(
                        text = yes,
                        onClick = onYes,
                        backgroundColor = DotBody,
                        borderColor = TextDark,
                        textColor = TextDark,
                        width = 220.dp,
                        height = 52.dp,
                        borderWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    PixelButton(
                        text = no,
                        onClick = onNo,
                        backgroundColor = PanelSand,
                        borderColor = TextDark,
                        textColor = TextDark,
                        width = 220.dp,
                        height = 44.dp,
                        borderWidth = 3.dp
                    )
                }
            }
        }

        OverlayCloseButton(
            onClose = onClose,
            contentDescription = stringResource(Res.string.ctl_close),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(CORNER_BUTTON_PADDING)
        )
    }
}

/** Welche einmalige Frage gerade offen ist. */
enum class Prompt {
    /** Nach dem ersten Tageslauf: Soll die Erinnerung an? */
    REMINDER,

    /** Nach ein paar Läufen: Darf anonyme Statistik gesendet werden? */
    ANALYTICS,

    /** Die Daily-Serie reißt heute — per Spot einen Joker holen? */
    RESCUE
}

/**
 * Welche Frage beim Betreten des Startbildschirms kommt — höchstens eine,
 * und nie in den ersten Läufen: Wer das Spiel gerade kennenlernt, soll
 * spielen, nicht Fragen beantworten.
 *
 * Die Erinnerung geht vor, weil sie an einem Ereignis hängt (die erste
 * Daily ist gerade gespielt) und die Statistik-Frage auch nächstes Mal
 * noch passt. Eine in dieser Sitzung weggeklickte Frage ([dismissed])
 * kommt erst beim nächsten Start wieder.
 */
fun nextPrompt(
    reminderSupported: Boolean,
    reminderEnabled: Boolean,
    reminderAsked: Boolean,
    dailyPlayedEver: Boolean,
    analyticsSupported: Boolean,
    analyticsAnswered: Boolean,
    runCount: Int,
    dismissed: Set<Prompt>
): Prompt? = when {
    runCount < PROMPT_MIN_RUNS -> null
    reminderSupported && !reminderEnabled && !reminderAsked && dailyPlayedEver &&
        Prompt.REMINDER !in dismissed -> Prompt.REMINDER
    analyticsSupported && !analyticsAnswered && Prompt.ANALYTICS !in dismissed -> Prompt.ANALYTICS
    else -> null
}

/**
 * Vor so vielen Läufen fragt das Spiel nichts. Die Stützräder laufen fünf
 * Läufe (START_COACH_RUNS); die Fragen kommen erst, wenn sie weg sind.
 */
const val PROMPT_MIN_RUNS = 5

// ===== Rekord in Reichweite =====

/** Ab diesem Rekord zählt der Lauf die letzten Punkte davor herunter. */
const val RECORD_HINT_MIN_BEST = 10

/** So viele Punkte vor dem Rekord erscheint „NOCH 3 BIS REKORD". */
const val RECORD_HINT_RANGE = 3

/**
 * Wie viele Punkte bis zum Rekord fehlen — null, wenn es nichts
 * anzuzeigen gibt: Rekord zu klein, noch zu weit weg oder schon geknackt
 * (dann feiert ohnehin „REKORD GEKNACKT!").
 */
fun recordGap(bestScore: Int, score: Int): Int? {
    if (bestScore < RECORD_HINT_MIN_BEST) return null
    val gap = bestScore - score
    return if (gap in 1..RECORD_HINT_RANGE) gap else null
}

// ===== Game-Over =====

/**
 * Die Belohnungs-Zeilen im Game-Over: erledigte Tagesaufgaben, „ALLE 3
 * GESCHAFFT!" (samt Joker) und ein eingesetzter Joker. Im selben Gold wie
 * „NEUE MEDAILLE!" — es sind Feiern, keine Hinweise.
 */
@Composable
fun GameOverRewardLines(
    missionsDone: List<Mission>,
    allDone: Boolean,
    jokerEarned: Boolean,
    jokerUsed: Boolean
) {
    if (jokerUsed) RewardLine(stringResource(Res.string.joker_used_line))
    missionsDone.forEach { mission ->
        RewardLine(stringResource(Res.string.mission_done_line, missionLabel(mission)))
    }
    if (allDone) {
        RewardLine(
            stringResource(
                if (jokerEarned) Res.string.missions_all_done_joker else Res.string.missions_all_done
            )
        )
    }
}

@Composable
private fun RewardLine(text: String) {
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = text,
        style = ScoreShadowStyle,
        fontSize = 16.sp,
        color = Color(0xFFFFE95E),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}
