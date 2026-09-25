package de.robinrehbein.punkt.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.game.CardFrame
import de.robinrehbein.punkt.game.CardStyle
import de.robinrehbein.punkt.game.MedalId
import de.robinrehbein.punkt.game.MedalPaint
import de.robinrehbein.punkt.ui.resources.Res
import de.robinrehbein.punkt.ui.resources.daily
import de.robinrehbein.punkt.ui.theme.Bytesized
import de.robinrehbein.punkt.ui.world.DotBody
import de.robinrehbein.punkt.ui.world.DotShine
import de.robinrehbein.punkt.ui.world.GRID
import de.robinrehbein.punkt.ui.world.OutlineColor
import de.robinrehbein.punkt.ui.world.PanelSand
import de.robinrehbein.punkt.ui.world.drawPixelCircle
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

// Bausteine, die mehrere Overlays teilen: Score-Stil, Punkte-HUD,
// Pixel-Panel, Medaille und die Familien-Überschrift. Bis v2.27 Teil von
// GameOverlays.kt (aufgeteilt nach Plan 8.4).

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
    // Bandfarben aus MedalPaint — dieselbe Quelle, aus der sich seit dem
    // Umzug der Karte auch die geteilte Medaille bedient.
    val ribbon = if (tier != null) Color(MedalPaint.RIBBON) else Color(0xFFBDB48A)
    val ribbonDark = if (tier != null) Color(MedalPaint.RIBBON_SHADE) else Color(0xFFA89E74)

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
            if (tier != null) Color(MedalPaint.GLINT) else Color(0xFFEFE7C0),
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
