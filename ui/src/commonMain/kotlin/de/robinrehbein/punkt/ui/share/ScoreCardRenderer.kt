package de.robinrehbein.punkt.ui.share

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.game.CardFrame
import de.robinrehbein.punkt.game.CardPlan
import de.robinrehbein.punkt.game.CardRect
import de.robinrehbein.punkt.game.CardStyle
import de.robinrehbein.punkt.game.FrameTone
import de.robinrehbein.punkt.game.MedalPaint
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SkinState
import de.robinrehbein.punkt.ui.world.OutlineColor
import de.robinrehbein.punkt.ui.world.drawPixelCircle

/**
 * Was auf der Karte steht. Alle Zeichenketten kommen fertig von außen:
 * Sie hängen an der Sprache der Oberfläche, und die kennt weder `:core`
 * noch diese Datei — nur der Bildschirm, der sie aus den Ressourcen
 * liest.
 *
 * [hour] und [month] sind das Kleid des Moments, in dem geteilt wird:
 * TAGESZEIT und JAHRESZEIT sollen auf der Karte nicht ewig Mittag im
 * Juni zeigen. Bewegte Skins stehen dagegen still — ein geteiltes Bild
 * ist ein Standbild.
 */
data class ScoreCardContent(
    val score: Int,
    val skin: SkinId,
    val scene: SceneId,
    val frame: CardFrame,
    val hour: Int,
    val month: Int,
    /** Der Daily-Hinweis über dem Beinamen — null außerhalb der Challenge. */
    val dailyLine: String?,
    /** Der Beiname, schon in der Sprache der Oberfläche — null in den ersten Läufen. */
    val epithet: String?,
    val pointsLine: String,
    val sceneLine: String,
    val recordLine: String,
    /** Ist die REKORD-Zeile eine Feier? Dann steht sie in Gold. */
    val recordHighlighted: Boolean,
    val challengeLine: String
)

/**
 * Die geteilte Score-Karte als Bild — eine Zeichnung für beide Apps.
 *
 * Bis v2.26 stand sie in `app/.../share/ScoreCard.kt` und malte mit
 * `android.graphics`. Auf dem iPhone gab es sie deshalb gar nicht, und
 * der TEILEN-Knopf im Game-Over fehlte dort. Der naheliegende Weg
 * dorthin wäre ein zweiter Port gewesen — dieselbe Karte ein zweites
 * Mal beschrieben, mit derselben Aussicht wie beim SpriteKit-Port des
 * Spiels: Sie laufen auseinander.
 *
 * Stattdessen steht die Geometrie als Datentabelle in `:core`
 * ([CardPlan], wie [CardStyle.frameRects] vorher), und hier wird sie
 * einmal ausgemalt — mit denselben Compose-Bausteinen, mit denen auch
 * die Spielwelt zeichnet ([drawPixelCircle]).
 *
 * Was dabei NICHT gleich bleiben kann, ist das Rastern der Schrift:
 * `android.graphics` und Compose setzen dieselbe Pixelschrift nicht
 * Pixel für Pixel identisch. Geometrie, Farben und Raster sind es —
 * und ein Test in `:core` hält sie fest.
 */
fun renderScoreCard(
    content: ScoreCardContent,
    measurer: TextMeasurer,
    font: FontFamily
): ImageBitmap {
    val blatt = ImageBitmap(CardPlan.WIDTH, CardPlan.HEIGHT)
    CanvasDrawScope().draw(
        // Dichte 1: Die Karte rechnet in ihren eigenen Pixeln, nicht in
        // denen des Geräts. Ein iPhone mit dreifacher Dichte soll
        // dieselben 1080 mal 1350 Pixel abliefern wie ein Telefon mit
        // zweifacher — sonst hinge die Größe der geteilten Datei am
        // Bildschirm dessen, der teilt.
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(blatt),
        size = Size(CardPlan.WIDTH.toFloat(), CardPlan.HEIGHT.toFloat())
    ) {
        drawScoreCard(content, measurer, font)
    }
    return blatt
}

/** Dichte 1 — siehe [renderScoreCard]. */
private val EINS = Density(1f)

private val GOLD = Color(FrameTone.GOLD.argb)
private val ACCENT = Color(FrameTone.ACCENT.argb)

private fun DrawScope.drawScoreCard(
    content: ScoreCardContent,
    measurer: TextMeasurer,
    font: FontFamily
) {
    // Wo die Zeilen sitzen, hängt am Rahmen: Die breiten Stufen schieben
    // den Inhalt nach innen, SCHLICHT nicht. Siehe CardStyle.layout.
    val layout = CardStyle.layout(content.frame)
    val hoehe = CardPlan.HEIGHT.toFloat()

    // Himmel, Wolken und Boden aus der gewählten Kulisse — sonst sähe
    // niemand außer der Besitzerin, welche sie trägt.
    fuellen(CardPlan.background(content.scene, content.score))

    zeile(measurer, font, "DOTTIE.", hoehe * layout.title, layout.titleSize, Color.White)

    // Unter dem Titel wird gestapelt, nicht gesetzt: Daily-Hinweis und
    // Beiname können einzeln oder zusammen auftreten, und zwei feste
    // Zeilen ständen im Leerfall schief.
    var subline = hoehe * layout.subline
    content.dailyLine?.let {
        zeile(measurer, font, it, subline, layout.sublineSize, GOLD)
        subline += hoehe * CardPlan.DAILY_GAP
    }
    content.epithet?.let { titel ->
        // Der Beiname bekommt ein dunkles Schild statt nur einen
        // Schatten: In dieser Höhe stehen die Wolken, und heller Text auf
        // heller Wolke ist genau die Stelle, an der eine geteilte Karte
        // unleserlich wird.
        val mass = messen(measurer, font, titel, CardPlan.EPITHET_SIZE)
        val schild = CardPlan.plaque(subline, mass.size.width / 2f)
        fuellen(schild.rects)
        setzen(mass, schild.baseline, GOLD)
    }

    // Punkt im gewählten Skin, mittig über dem Score.
    val kleid = SkinState(hour = content.hour, month = content.month)
    val dotY = hoehe * layout.dot
    drawPixelCircle(
        outline = OutlineColor,
        centerX = CardPlan.CENTER_X,
        centerY = dotY,
        radius = layout.dotRadius
    ) { col, row -> Color(SkinPaint.cell(content.skin, col, row, kleid)) }
    fuellen(CardPlan.dotDetails(content.skin, kleid, dotY, layout.dotRadius))

    zeile(
        measurer, font, content.score.toString(),
        hoehe * CardPlan.SCORE, CardPlan.SCORE_SIZE, Color.White
    )
    zeile(
        measurer, font, content.pointsLine,
        hoehe * CardPlan.POINTS, CardPlan.POINTS_SIZE, Color.White
    )
    // Die Kulisse steht klein neben PUNKTE: Sie ist Teil der Sammlung,
    // aber sie ist nicht die Nachricht der Karte.
    zeile(
        measurer, font, content.sceneLine,
        hoehe * CardPlan.SCENE, CardPlan.SCENE_SIZE, Color.White
    )
    zeile(
        measurer, font, content.recordLine,
        hoehe * CardPlan.RECORD, CardPlan.RECORD_SIZE,
        if (content.recordHighlighted) GOLD else Color.White
    )

    CardPlan.medal(content.score)?.let { medaille ->
        fuellen(medaille.ribbon)
        drawPixelCircle(
            color = Color(MedalPaint.body(medaille.tier)),
            outline = OutlineColor,
            centerX = medaille.centerX,
            centerY = medaille.centerY,
            radius = medaille.radius,
            shade = Color(MedalPaint.shade(medaille.tier))
        )
        fuellen(medaille.face)
    }

    // Die Aufforderung sitzt bei den breiten Rahmen weiter innen — bei
    // 94,5 % läge sie unter der Prachtstufe.
    zeile(
        measurer, font, content.challengeLine,
        hoehe * layout.challenge, CardPlan.CHALLENGE_SIZE, ACCENT
    )

    // Der Rahmen kommt zuletzt: Er liegt über Kulisse UND Schrift, damit
    // an der Kante nichts durchscheint.
    fuellen(CardPlan.frame(content.frame))
}

/** Eine Rechteckliste aus dem Bauplan, in ihrer Reihenfolge. */
private fun DrawScope.fuellen(rects: List<CardRect>) {
    rects.forEach {
        drawRect(Color(it.color), Offset(it.x, it.y), Size(it.w, it.h))
    }
}

private fun messen(
    measurer: TextMeasurer,
    font: FontFamily,
    text: String,
    size: Float
): TextLayoutResult = measurer.measure(
    text = AnnotatedString(text),
    style = TextStyle(fontFamily = font, fontSize = size.sp),
    density = EINS
)

/**
 * Ein gemessener Text, mittig auf [baseline].
 *
 * `android.graphics` setzte Text auf eine Grundlinie, Compose auf die
 * obere Kante seines Kastens — die Differenz ist genau
 * [TextLayoutResult.firstBaseline]. Ohne diese Umrechnung säße jede
 * Zeile der Karte um eine Zeilenhöhe zu tief.
 */
private fun DrawScope.setzen(mass: TextLayoutResult, baseline: Float, color: Color, versatz: Float = 0f) {
    drawText(
        textLayoutResult = mass,
        color = color,
        topLeft = Offset(
            CardPlan.CENTER_X - mass.size.width / 2f + versatz,
            baseline - mass.firstBaseline + versatz
        )
    )
}

/** Eine Zeile mit dem dunklen Schatten dahinter, den die Karte immer hatte. */
private fun DrawScope.zeile(
    measurer: TextMeasurer,
    font: FontFamily,
    text: String,
    baseline: Float,
    size: Float,
    color: Color
) {
    val mass = messen(measurer, font, text, size)
    setzen(mass, baseline, OutlineColor, versatz = size * CardPlan.SHADOW)
    setzen(mass, baseline, color)
}
