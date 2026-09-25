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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.game.CardFrame
import de.robinrehbein.punkt.game.Goal
import de.robinrehbein.punkt.game.MedalPaint
import de.robinrehbein.punkt.game.Twist
import de.robinrehbein.punkt.ui.components.PixelButton
import de.robinrehbein.punkt.ui.resources.Res
import de.robinrehbein.punkt.ui.resources.daily
import de.robinrehbein.punkt.ui.resources.game_over
import de.robinrehbein.punkt.ui.resources.medal
import de.robinrehbein.punkt.ui.resources.medal_next
import de.robinrehbein.punkt.ui.resources.menu
import de.robinrehbein.punkt.ui.resources.new_medal
import de.robinrehbein.punkt.ui.resources.new_record
import de.robinrehbein.punkt.ui.resources.new_skin_unlocked
import de.robinrehbein.punkt.ui.resources.points_label
import de.robinrehbein.punkt.ui.resources.record_label
import de.robinrehbein.punkt.ui.resources.share
import de.robinrehbein.punkt.ui.resources.tap_retry
import de.robinrehbein.punkt.ui.resources.taunts_close
import de.robinrehbein.punkt.ui.resources.taunts_default
import de.robinrehbein.punkt.ui.resources.taunts_low
import de.robinrehbein.punkt.ui.resources.taunts_zero
import de.robinrehbein.punkt.ui.resources.today_score
import de.robinrehbein.punkt.ui.text.medalName
import de.robinrehbein.punkt.ui.theme.Bytesized
import de.robinrehbein.punkt.ui.world.DotBody
import de.robinrehbein.punkt.ui.world.PanelSand
import de.robinrehbein.punkt.ui.world.RecordRed
import de.robinrehbein.punkt.ui.world.TextDark
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

// Das Game-Over mit Hilfe-Ecke und Spott-Texten. Bis v2.27 Teil von
// GameOverlays.kt (aufgeteilt nach Plan 8.4).

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
    /**
     * Der Twist, den dieser Lauf freigeschaltet hat und der noch nie
     * erklärt wurde — null, wenn es nichts zu erklären gibt (siehe
     * TwistLessons). Höchstens einer je Game-Over.
     */
    newTwist: Twist?,
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
    cardFrame: CardFrame = CardFrame.SCHLICHT,
    /**
     * Platz direkt unter "GAME OVER", etwa für die Todesursache in klein
     * (Plan 3.2, AP-11). Leer, solange niemand etwas hineinreicht.
     */
    cause: @Composable () -> Unit = {}
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
            cause()

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

            // Die Twist-Erklärung sitzt UNTER den Feier-Zeilen und ÜBER
            // dem Ziel-Balken, und das ist der Platz, der den bestehenden
            // Rhythmus am wenigsten stört: Darüber steht, wie der Lauf
            // war — Spott oder Rekord, Daily-Stand, Medaille, Skin. Das
            // ist ein zusammenhängender Block aus Ergebnis und Belohnung,
            // und eine Lehrzeile mittendrin (oder gar über dem Spott)
            // würde den Blick vom Ergebnis wegziehen, das man nach dem
            // Tod zuerst sucht. Darunter steht, was als Nächstes kommt —
            // dorthin gehört auch der neue Twist, denn er ist keine
            // Belohnung, sondern eine Ansage für den nächsten Versuch.
            if (newTwist != null) {
                Spacer(modifier = Modifier.height(10.dp))
                TwistLessonShield(newTwist)
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
