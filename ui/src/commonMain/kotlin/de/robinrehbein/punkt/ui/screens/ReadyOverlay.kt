package de.robinrehbein.punkt.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.game.Goal
import de.robinrehbein.punkt.ui.components.PixelIcon
import de.robinrehbein.punkt.ui.components.PIXEL_SHADOW
import de.robinrehbein.punkt.ui.components.PixelIconButton
import de.robinrehbein.punkt.ui.components.pixelPressable
import de.robinrehbein.punkt.ui.resources.Res
import de.robinrehbein.punkt.ui.resources.best_score
import de.robinrehbein.punkt.ui.resources.collection
import de.robinrehbein.punkt.ui.resources.daily
import de.robinrehbein.punkt.ui.resources.settings
import de.robinrehbein.punkt.ui.resources.start_daily_armed
import de.robinrehbein.punkt.ui.resources.stats
import de.robinrehbein.punkt.ui.theme.Bytesized
import de.robinrehbein.punkt.ui.world.DotBody
import de.robinrehbein.punkt.ui.world.OutlineColor
import de.robinrehbein.punkt.ui.world.PanelSand
import de.robinrehbein.punkt.ui.world.RecordRed
import de.robinrehbein.punkt.ui.world.TextDark
import de.robinrehbein.punkt.ui.world.readyHintArea
import org.jetbrains.compose.resources.stringResource

// Der Startbildschirm mit Taster-Leiste. Bis v2.27 Teil von
// GameOverlays.kt (aufgeteilt nach Plan 8.4).

/**
 * Der Startbildschirm. Er zeigt bewusst nur noch acht Dinge: Titel,
 * Rekord, den Hinweis unter dem Ring, das Zahnrad, die drei Knöpfe und die
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
    onToggleDiagnostics: () -> Unit = {},
    // Gibt es in der Sammlung etwas Neues, das noch niemand angesehen
    // hat? Bis AP-15 ohne Wirkung (Plan 8.3).
    collectionHasNew: Boolean = false,
    // Platz für ein Banner unter Titel und Rekord ("NEUE WELTEN: …").
    // Leer, solange niemand etwas hineinreicht (Plan 8.3).
    banner: @Composable () -> Unit = {},
    // DAILY ist scharf: Der nächste Lauf ist der Tageslauf (Plan 8.6 #4).
    dailyArmed: Boolean = false,
    // Die Zielzeile fällt während der Stützräder weg (Plan 8.6 #7).
    goalHidden: Boolean = false
) {

    // Die Taster-Leiste zeichnet bis an den physischen Bildschirmrand,
    // deshalb liegt sie außerhalb des Inset-Paddings — alles andere bleibt
    // wie bisher innerhalb der Systemleisten.
    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            // Ein Zahnrad statt dreier Einzel-Icons: Ton, Erinnerung und Hilfe
            // sind Einstellungen, keine Spielzüge — sie gehören hinter eine Tür.
            // Schatten und Glanzkante geben ihm dieselbe Tiefe wie dem Titel;
            // beim Drücken sinkt es sichtbar in den Schatten.
            PixelIconButton(
                icon = PixelIcon.SLIDERS,
                contentDescription = stringResource(Res.string.settings),
                onClick = onSettings,
                backgroundColor = PanelSand,
                borderColor = TextDark,
                strikeColor = RecordRed,
                buttonSize = 48.dp,
                borderWidth = 3.dp,
                shadow = 4.dp,
                highlightColor = Color(0xFFEFE9C2),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )

            // Auf kurzen Bildschirmen (720×1280) reicht der Ring bis dicht
            // unter den Titel. Die Titelspalte rückt dann nach oben und der
            // Titel wird kleiner, damit Rekord und Welten-Banner über
            // „NOCH NICHT“ und der Hand enden (Plan 8.5 AP-22).
            val compact = maxHeight < COMPACT_HEIGHT
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (compact) 12.dp else 80.dp)
            ) {
                Text(
                    // "DOTTIE." ist mit 7 Zeichen schmal genug für die vollen
                    // 64.sp — auch auf 360-dp-Displays ohne Umbruch.
                    text = "DOTTIE.",
                    style = ScoreShadowStyle,
                    fontSize = if (compact) 48.sp else 64.sp,
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
                // == AP-15 sammlung ==
                banner()
                // == /AP-15 ==
            }

            // Eine Zeile statt einer Zahlenwand: der eine Grund, gleich noch
            // einmal zu spielen. Sie steht dort, wo früher die Knopfreihe
            // schwebte — die Achse bleibt dabei, "MEDAILLE 199/200" allein
            // läse sich als Medaillen.
            if (goal != null && !goalHidden) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 84.dp)
                ) {
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

        // Der Hinweis steht unter dem Ring, ohne Blinken (Plan 3.1): Ein
        // blinkender Text sagt „jetzt tippen“, und genau das soll man
        // hier nicht blind tun. Die Fläche rechnet sich aus derselben
        // Bildgröße wie die Kreisbahn (readyHintArea), deshalb liegt er
        // außerhalb des Inset-Paddings, wie der Ring selbst.
        ReadyHint(hint)

        // Drei Taster statt dreier schwebender Knöpfe: Die Statistik
        // gehört auf den Startscreen, nicht in ein Untermenü — sie ist
        // der Grund, den nächsten Lauf zu starten.
        TasterBar(
            dailyStreak = dailyStreak,
            dailyArmed = dailyArmed,
            collectionHasNew = collectionHasNew,
            onDaily = onDaily,
            onSkins = onSkins,
            onStats = onStats,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Die Taster-Leiste am unteren Bildschirmrand: drei gleich breite Felder,
 * randlos von ganz links bis ganz rechts, nur durch Outline-Linien
 * getrennt — wie Taster an einem Gerät. Sie zeichnet unter die
 * Systemleiste (Edge-to-Edge); deren Höhe landet als Innenabstand unter
 * der Beschriftung, damit die Fläche bis an den Rand reicht, der Text
 * aber über der Gesten-Zone steht.
 */
@Composable
private fun TasterBar(
    dailyStreak: Int,
    dailyArmed: Boolean,
    collectionHasNew: Boolean,
    onDaily: () -> Unit,
    onSkins: () -> Unit,
    onStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bottomInset = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
    Row(modifier = modifier.fillMaxWidth().height(64.dp + bottomInset)) {
        // DAILY ist sandfarben wie die anderen Taster und kein zweites
        // „Spielen“ mehr (Plan 7.2). Scharf geschaltet trägt er das Gelb
        // des Vogels und sagt es: DAILY: AN.
        Taster(
            text = if (dailyArmed) stringResource(Res.string.start_daily_armed)
            else stringResource(Res.string.daily),
            onClick = onDaily,
            backgroundColor = if (dailyArmed) DailyArmedColor else PanelSand,
            bottomInset = bottomInset,
            modifier = Modifier.weight(1f)
        ) {
            // Die laufende Serie hängt als Abzeichen am Taster, zu dem sie
            // gehört. Sie rückt in die Ecke hinein: Am Bildschirmrand gibt
            // es kein Außen mehr, über das sie wie früher hinausragen
            // könnte, ohne abgeschnitten zu werden.
            if (dailyStreak >= 1) {
                StreakBadge(
                    days = dailyStreak,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 6.dp)
                )
            }
        }
        Taster(
            text = stringResource(Res.string.collection),
            onClick = onSkins,
            backgroundColor = PanelSand,
            bottomInset = bottomInset,
            divider = true,
            modifier = Modifier.weight(1f)
        ) {
            // == AP-15 sammlung ==
            // Roter Punkt, solange in der Sammlung etwas NEU ist — in
            // derselben Ecke, in der am DAILY-Taster die Serie hängt.
            if (collectionHasNew) {
                NewDot(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp)
                )
            }
            // == /AP-15 ==
        }
        Taster(
            text = stringResource(Res.string.stats),
            onClick = onStats,
            backgroundColor = PanelSand,
            bottomInset = bottomInset,
            divider = true,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Ein Feld der [TasterBar]: Fläche, Oberkante, links optional die
 * Trennlinie zum Nachbarn. Unter- und Seitenkanten gibt es nicht — die
 * Leiste endet am Bildschirmrand, nicht an einem Rahmen.
 *
 * Gedrückt wie die übrigen Pixel-Knöpfe (Plan 7.2): keine Material-Welle,
 * ein Haptik-Tick über [pixelPressable], und das Feld sinkt ein — die
 * Fläche dunkelt um eine Stufe ab und die Beschriftung rutscht um den
 * Pixelschatten nach unten. Auch die Game-Over-Leiste baut darauf auf.
 *
 * @param enabled Ist das Feld gesperrt, löst ein Tap nichts aus. Er wird
 *   dabei aber nicht verbraucht (siehe [pixelPressable]); wer ihn
 *   schlucken will, muss das außen tun.
 */
@Composable
internal fun Taster(
    text: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
    divider: Boolean = false,
    enabled: Boolean = true,
    badge: @Composable BoxScope.() -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .fillMaxHeight()
            .pixelPressable(
                enabled = enabled,
                interactionSource = interactionSource,
                onClick = onClick
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val border = 4.dp.toPx()
            drawRect(color = backgroundColor)
            if (pressed) drawRect(color = OutlineColor.copy(alpha = TASTER_PRESSED_SHADE))
            drawRect(color = OutlineColor, size = Size(size.width, border))
            if (divider) drawRect(color = OutlineColor, size = Size(border, size.height))
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomInset)
                .offset(y = if (pressed) PIXEL_SHADOW else 0.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontFamily = Bytesized,
                fontSize = 16.sp,
                color = TextDark
            )
        }
        badge()
    }
}

/** Unter dieser Höhe rückt die Titelspalte zusammen (720×1280 sind 640 dp). */
private val COMPACT_HEIGHT = 700.dp

/**
 * „TIPPE, WENN DER PUNKT IM GRÜNEN IST“ in der Fläche aus
 * [readyHintArea]: unter dem Ring, ohne Ring, Zone und Vogel zu berühren.
 */
@Composable
private fun ReadyHint(hint: String) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val area = with(density) { readyHintArea(Size(maxWidth.toPx(), maxHeight.toPx())) }
        with(density) {
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .offset(x = area.left.toDp(), y = area.top.toDp())
                    .size(width = area.width.toDp(), height = area.height.toDp())
            ) {
                Text(
                    text = hint,
                    style = ScoreShadowStyle,
                    fontSize = 18.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Wie stark ein gedrückter Taster abdunkelt: eine Stufe, kein Schatten-Loch. */
private const val TASTER_PRESSED_SHADE = 0.14f

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
