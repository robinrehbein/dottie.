package de.robinrehbein.punkt.ui.screens

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.game.DeathCause
import de.robinrehbein.punkt.ui.resources.Res
import de.robinrehbein.punkt.ui.resources.death_bomb_lesson
import de.robinrehbein.punkt.ui.resources.death_early
import de.robinrehbein.punkt.ui.resources.death_late
import de.robinrehbein.punkt.ui.resources.death_missed
import de.robinrehbein.punkt.ui.resources.death_trap
import de.robinrehbein.punkt.ui.world.DOT_RADIUS_SHARE
import de.robinrehbein.punkt.ui.world.ringGeometry
import kotlin.math.min
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Todesursache nach jedem Aus (Plan 3.2): ZU FRÜH, ZU SPÄT, VERPASST oder
 * BOOM!. Wer sieht, WARUM es vorbei ist, ärgert sich über sich selbst
 * statt über das Spiel — und will es gleich noch einmal wissen.
 *
 * Die Quelle ist allein [de.robinrehbein.punkt.game.TimingGame.lastDeathCause]:
 * Die Engine hält die Ursache im Moment des Taps fest. Aus dem Zustand
 * beim späteren Died-Event ließe sie sich nicht mehr sicher ablesen
 * (Plan 8.7).
 */

/** Der Text zu einer Ursache, oder null für [DeathCause.NONE]. */
internal fun deathCauseText(cause: DeathCause): StringResource? = when (cause) {
    DeathCause.NONE -> null
    DeathCause.EARLY -> Res.string.death_early
    DeathCause.LATE -> Res.string.death_late
    DeathCause.MISSED -> Res.string.death_missed
    DeathCause.TRAP -> Res.string.death_trap
}

/**
 * Die Fläche für die Ursache am Ring: direkt unter der Kreisbahn, mit
 * Abstand für den Vogel (der über die Bahn hinausragt) und die Bahn
 * selbst. Nicht im Ring: Dort stehen Zone und Fallen, und der Vogel
 * hüpft beim Sturz hindurch. Nicht darüber: Dort steht die Punktzahl,
 * und auf kurzen Displays reicht der Platz zwischen ihr und dem Ring
 * nicht für zwei Zeilen.
 *
 * Eine reine Funktion der Bildgröße, damit ein Test sie auf jedem
 * Format gegen [ringGeometry] prüfen kann.
 */
fun deathCauseLabelArea(size: Size): Rect {
    val ring = ringGeometry(size)
    val top = ring.cy + ring.radius + size.height * (DOT_RADIUS_SHARE + LABEL_GAP_SHARE)
    val bottom = min(top + size.height * LABEL_HEIGHT_SHARE, size.height * LABEL_MAX_BOTTOM_SHARE)
    return Rect(
        left = size.width * LABEL_SIDE_SHARE,
        top = top,
        right = size.width * (1f - LABEL_SIDE_SHARE),
        bottom = bottom
    )
}

/** Luft zwischen Vogel-Unterkante und Text, relativ zur Bildhöhe. */
private const val LABEL_GAP_SHARE = 0.012f

/** Höhe der Fläche, relativ zur Bildhöhe. */
private const val LABEL_HEIGHT_SHARE = 0.16f

/** Tiefer als hier beginnt die Gestenleiste. */
private const val LABEL_MAX_BOTTOM_SHARE = 0.95f

/** Seitlicher Rand der Fläche, relativ zur Bildbreite. */
private const val LABEL_SIDE_SHARE = 0.04f

/** Höhe, die Ursache und Lektion zusammen brauchen (siehe [DeathCauseLabel]). */
internal val DEATH_LABEL_CONTENT_HEIGHT = 76.dp

/**
 * Alle Ursachen in Weiß mit dunklem Schatten: Farbig (BOOM! in Orange
 * oder Rot) versank der Text vor dem Abendhimmel der Stufe 1.
 */
private val CauseColor = Color.White

/**
 * Die Ursache am Ring, während DYING (Freeze und Sturz). Beim ersten
 * Bomben-Tod steht darunter „BOMBE = NIE TIPPEN“ ([bombLesson]).
 * Füllt den ganzen Bildschirm, damit sich die Lage aus derselben
 * Bildgröße rechnet wie die Kreisbahn.
 */
@Composable
fun DeathCauseLabel(
    cause: DeathCause,
    bombLesson: Boolean,
    modifier: Modifier = Modifier
) {
    val text = deathCauseText(cause) ?: return
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val area = with(density) {
            deathCauseLabelArea(Size(maxWidth.toPx(), maxHeight.toPx()))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(
                    x = with(density) { area.left.toDp() },
                    y = with(density) { area.top.toDp() }
                )
                .width(with(density) { area.width.toDp() })
        ) {
            Text(
                text = stringResource(text),
                style = ScoreShadowStyle,
                fontSize = 36.sp,
                color = CauseColor,
                textAlign = TextAlign.Center
            )
            if (bombLesson && cause == DeathCause.TRAP) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.death_bomb_lesson),
                    style = ScoreShadowStyle,
                    fontSize = 16.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Die Ursache in klein, für den Platz unter „GAME OVER“. Im Game-Over
 * steht sie noch einmal, weil der Blick dort landet, wenn der Sturz
 * vorbei ist.
 */
@Composable
fun DeathCauseSmall(cause: DeathCause, modifier: Modifier = Modifier) {
    val text = deathCauseText(cause) ?: return
    Text(
        text = stringResource(text),
        style = ScoreShadowStyle,
        fontSize = 20.sp,
        color = CauseColor,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}
