package de.robinrehbein.punkt.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import de.robinrehbein.punkt.R
import de.robinrehbein.punkt.game.CardFrame
import de.robinrehbein.punkt.game.CardStyle
import de.robinrehbein.punkt.game.DotScene
import de.robinrehbein.punkt.game.DotSkin
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SkinState
import de.robinrehbein.punkt.game.SkinStats
import java.io.File
import java.time.LocalDateTime
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Rendert nach einem Lauf eine teilbare Score-Card als PNG im Pixel-Look
 * des Spiels und öffnet den System-Share-Dialog. Das Bild entsteht komplett
 * im Code (android.graphics), wie alles andere in der App auch — die
 * Farbwerte spiegeln die Spiel-Palette aus GameOverlays/TimingGameScreen.
 */
object ScoreCard {

    private const val W = 1080
    private const val H = 1350
    private const val CELL = 6f

    /** Kantenlänge der Karte im Pixelraster — der Rahmen rechnet in Feldern. */
    private const val COLS = 180
    private const val ROWS = 225

    private const val OUTLINE = 0xFF543847.toInt()
    private const val ACCENT = 0xFFFF8A3C.toInt()
    private const val RECORD_YELLOW = 0xFFFFE95E.toInt()
    private const val INLAY = 0xFF4EC0CA.toInt()
    private const val PEARL = 0xFFF7F3EE.toInt()

    /** Baut die Card, schreibt sie in den Cache und öffnet den Share-Dialog. */
    fun share(
        context: Context,
        score: Int,
        bestScore: Int,
        isNewRecord: Boolean,
        skin: DotSkin,
        scene: DotScene,
        daily: Boolean,
        dailyStreak: Int,
        stats: SkinStats,
        cardFrame: CardFrame? = null
    ) {
        val bitmap = render(
            context, score, bestScore, isNewRecord, skin, scene, daily, dailyStreak,
            stats, cardFrame
        )
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "punkt-score.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TEXT,
                if (daily) context.getString(R.string.share_text_daily, score)
                else context.getString(R.string.share_text, score)
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.share_chooser))
        )
    }

    internal fun render(
        context: Context,
        score: Int,
        bestScore: Int,
        isNewRecord: Boolean,
        skin: DotSkin,
        scene: DotScene,
        daily: Boolean,
        dailyStreak: Int,
        stats: SkinStats,
        /**
         * Die gewaehlte Rahmenstufe, oder null fuer "die hoechste
         * verdiente". Der Rueckfall steht in [CardStyle.frame]: Eine
         * Wahl, die der Spielstand nicht deckt, verliert gegen ihn.
         */
        cardFrame: CardFrame? = null
    ): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint().apply { isAntiAlias = false }
        val text = Paint().apply {
            isAntiAlias = false
            typeface = context.resources.getFont(R.font.bytesized_regular)
            textAlign = Paint.Align.CENTER
        }
        fun drawShadowed(str: String, x: Float, y: Float, size: Float, color: Int) {
            text.textSize = size
            text.color = OUTLINE
            canvas.drawText(str, x + size * 0.05f, y + size * 0.05f, text)
            text.color = color
            canvas.drawText(str, x, y, text)
        }

        // Himmel, Wolken und Boden kommen aus der gewaehlten Kulisse —
        // sonst saehe niemand ausser der Besitzerin, welche sie traegt.
        val kulisse = scene.scene
        paint.color = kulisse.sky[SkinPaint.skyStage(score)].toInt()
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)

        kulisse.cloud?.let { cloud ->
            drawCloud(canvas, paint, W * 0.08f, H * 0.10f, cloud.toInt())
            drawCloud(canvas, paint, W * 0.62f, H * 0.17f, cloud.toInt())
        }

        // Boden mit Narbe. Auf der Karte sitzt die Kante bei 86 % statt
        // 88 %, weil unten die Aufforderung steht — die Kulisse ohne
        // Boden (WELTRAUM) laesst den Streifen einfach weg.
        val groundTop = H * 0.86f
        kulisse.ground?.let { ground ->
            paint.color = ground.sand.toInt()
            canvas.drawRect(0f, groundTop, W.toFloat(), H.toFloat(), paint)
            paint.color = ground.turfDark.toInt()
            canvas.drawRect(0f, groundTop, W.toFloat(), groundTop + CELL * 5, paint)
            paint.color = ground.turfLight.toInt()
            var x = 0f
            while (x < W) {
                canvas.drawRect(x, groundTop, x + CELL * 5, groundTop + CELL * 4, paint)
                x += CELL * 10
            }
            paint.color = OUTLINE
            canvas.drawRect(0f, groundTop - CELL, W.toFloat(), groundTop, paint)
        }

        val cx = W / 2f
        // Wo die Zeilen sitzen, hängt am Rahmen: Die breiten Stufen
        // schieben den Inhalt nach innen, SCHLICHT nicht. Siehe
        // CardStyle.layout — dort steht auch, warum.
        val frame = CardStyle.frame(cardFrame, stats)
        val layout = CardStyle.layout(frame)
        drawShadowed("DOTTIE.", cx, H * layout.title, layout.titleSize, Color.WHITE)

        // Unter dem Titel wird gestapelt, nicht gesetzt: Daily-Hinweis und
        // Beiname können einzeln oder zusammen auftreten, und zwei feste
        // Zeilen ständen im Leerfall schief.
        var subline = H * layout.subline
        if (daily) {
            drawShadowed(
                context.getString(R.string.card_daily), cx, subline,
                layout.sublineSize, RECORD_YELLOW
            )
            subline += H * 0.04f
        }
        CardStyle.epithet(stats)?.let { titel ->
            // Der Beiname bekommt ein dunkles Schild statt nur einen
            // Schatten: In dieser Höhe stehen die Wolken, und heller Text
            // auf heller Wolke ist genau die Stelle, an der eine geteilte
            // Karte unleserlich wird.
            val label = CardStyle.label(titel, isGerman(context))
            text.textSize = 52f
            val halb = raster(text.measureText(label) / 2f + CELL * 4)
            val oben = raster(subline - CELL * 8)
            paint.color = OUTLINE
            canvas.drawRect(cx - halb, oben, cx + halb, oben + CELL * 10, paint)
            text.color = RECORD_YELLOW
            canvas.drawText(label, cx, oben + CELL * 8, text)
        }

        // Punkt im gewählten Skin, mittig über dem Score. Uhr und Kalender
        // einmal je Bild ablesen: TAGESZEIT und JAHRESZEIT tragen auf der
        // Karte das Kleid des Moments, in dem geteilt wird.
        val now = LocalDateTime.now()
        drawPixelDot(
            canvas, paint, cx, H * layout.dot, layout.dotRadius, skin,
            SkinState(hour = now.hour, month = now.monthValue)
        )

        drawShadowed(score.toString(), cx, H * 0.55f, 320f, Color.WHITE)
        // Mittig zwischen Score-Zahl (Baseline 0.55) und REKORD-Zeile (0.68),
        // damit PUNKTE weder an der Zahl noch am REKORD klebt.
        drawShadowed(context.getString(R.string.card_points), cx, H * 0.615f, 60f, Color.WHITE)
        // Die Kulisse steht klein neben PUNKTE: Sie ist Teil der
        // Sammlung, aber sie ist nicht die Nachricht der Karte.
        drawShadowed(
            context.getString(R.string.card_scene, context.getString(scene.titleRes)),
            cx, H * 0.645f, 34f, Color.WHITE
        )

        val recordLine = when {
            isNewRecord -> context.getString(R.string.new_record)
            daily && dailyStreak > 1 -> context.getString(R.string.card_daily_streak, dailyStreak)
            else -> context.getString(R.string.card_record, bestScore)
        }
        drawShadowed(recordLine, cx, H * 0.68f, 68f, if (isNewRecord) RECORD_YELLOW else Color.WHITE)

        val medal: Pair<Int, Int>? = when {
            score >= 40 -> 0xFFE5E4E2.toInt() to 0xFFADB5C4.toInt()
            score >= 30 -> 0xFFFFD700.toInt() to 0xFFC9A400.toInt()
            score >= 20 -> 0xFFC0C0C0.toInt() to 0xFF8F8F9C.toInt()
            score >= 10 -> 0xFFCD7F32.toInt() to 0xFF9C5A1E.toInt()
            else -> null
        }
        if (medal != null) {
            // Münz-Mitte so, dass das Band oben nicht in die REKORD-Zeile
            // ragt und die Münze über der Grasnarbe endet.
            drawMedal(canvas, paint, cx, H * 0.79f, 62f, medal.first, medal.second)
        }

        // Die Aufforderung sitzt bei den breiten Rahmen weiter innen —
        // bei 94,5 % läge sie unter der Prachtstufe.
        drawShadowed(context.getString(R.string.card_challenge), cx, H * layout.challenge, 72f, ACCENT)

        // Der Rahmen kommt zuletzt: Er liegt über Kulisse UND Schrift,
        // damit an der Kante nichts durchscheint.
        drawFrame(canvas, paint, frame)
        return bmp
    }

    /** Nächste Rasterlinie — alles auf der Karte sitzt auf ganzen Feldern. */
    private fun raster(px: Float): Float = (px / CELL).roundToInt() * CELL

    /** Zeigt die App gerade Deutsch? Nur dafür braucht der Beiname die Sprache. */
    private fun isGerman(context: Context): Boolean =
        context.resources.configuration.locales[0].language == "de"

    // ===== Rahmen =====

    /**
     * Der verdiente Rahmen. Vier Stufen, gestaffelt nach der Größe der
     * Sammlung (siehe [CardStyle.frame]) — und jede muss sich schon im
     * Vorschaubild eines Messengers von der vorigen unterscheiden. Deshalb
     * wächst nicht nur die Breite, sondern es kommen Farbe, Zinnen und
     * Eckornamente dazu: Zwei Pixel mehr Rand sieht auf 150 Pixel Breite
     * niemand.
     *
     * Gezeichnet wird ausschließlich im Feldraster ([CELL]) — ein Rahmen
     * mit krummen Kanten wäre der einzige Nicht-Pixel im ganzen Spiel.
     */
    private fun drawFrame(canvas: Canvas, paint: Paint, frame: CardFrame) {
        when (frame) {
            // Die Kante, mit der jeder anfängt: eine Linie, sonst nichts.
            // SCHLICHT zeichnet nichts. Der Bestand hatte keinen Rahmen,
            // und die erste Stufe soll etwas sein, das man verdient hat —
            // nicht etwas, das allen still dazukommt.
            CardFrame.SCHLICHT -> Unit

            CardFrame.DOPPELLINIE -> {
                band(canvas, paint, 0, 2, OUTLINE)
                band(canvas, paint, 2, 2, ACCENT)
                band(canvas, paint, 4, 2, OUTLINE)
                // Ecknieten: drei ineinandergesetzte Quadrate. Sie sind
                // der Teil, den man im Daumenbild zuerst sieht.
                cornerBlocks(canvas, paint, 0, 10, OUTLINE)
                cornerBlocks(canvas, paint, 2, 6, ACCENT)
                cornerBlocks(canvas, paint, 4, 2, RECORD_YELLOW)
            }

            CardFrame.ZINNEN -> {
                band(canvas, paint, 0, 2, OUTLINE)
                band(canvas, paint, 2, 4, ACCENT)
                // Zinnenkranz: gelbe Zähne im Farbband, alle sechs Felder.
                // Sie tragen die Stufe — das breitere Band allein wäre im
                // Vorschaubild nur ein etwas dickerer Strich.
                teeth(canvas, paint, 3, 2, 6, RECORD_YELLOW)
                band(canvas, paint, 6, 2, OUTLINE)
                band(canvas, paint, 8, 2, RECORD_YELLOW)
                band(canvas, paint, 10, 2, OUTLINE)
                cornerBlocks(canvas, paint, 0, 16, OUTLINE)
                cornerBlocks(canvas, paint, 2, 12, RECORD_YELLOW)
                cornerBlocks(canvas, paint, 5, 6, OUTLINE)
                cornerBlocks(canvas, paint, 7, 2, ACCENT)
            }

            CardFrame.PRACHT -> {
                band(canvas, paint, 0, 2, OUTLINE)
                band(canvas, paint, 2, 3, RECORD_YELLOW)
                band(canvas, paint, 5, 2, OUTLINE)
                band(canvas, paint, 7, 4, ACCENT)
                // Dieselben Zinnen, aber perlweiß und eine Lage weiter
                // innen — nebeneinander gelegt sind die beiden Stufen
                // dadurch auch dann auseinanderzuhalten, wenn die Breite
                // im Vorschaubild verlorengeht.
                teeth(canvas, paint, 8, 2, 6, PEARL)
                band(canvas, paint, 11, 2, INLAY)
                band(canvas, paint, 13, 2, OUTLINE)
                // Eckrosetten: ein eingelegter Rhombus statt der
                // gestapelten Quadrate der Stufe darunter.
                cornerBlocks(canvas, paint, 0, 22, OUTLINE)
                cornerDiamonds(canvas, paint, 1, 20, RECORD_YELLOW)
                cornerDiamonds(canvas, paint, 6, 10, ACCENT)
                cornerBlocks(canvas, paint, 9, 4, PEARL)
            }
        }
    }

    /** Ein Rechteck im Feldraster. */
    private fun block(
        canvas: Canvas,
        paint: Paint,
        col: Int,
        row: Int,
        cols: Int,
        rows: Int,
        color: Int
    ) {
        paint.color = color
        canvas.drawRect(
            col * CELL, row * CELL, (col + cols) * CELL, (row + rows) * CELL, paint
        )
    }

    /** Ein umlaufendes Band, [inset] Felder vom Blattrand, [thickness] dick. */
    private fun band(canvas: Canvas, paint: Paint, inset: Int, thickness: Int, color: Int) {
        val breite = COLS - 2 * inset
        val hoehe = ROWS - 2 * inset
        block(canvas, paint, inset, inset, breite, thickness, color)
        block(canvas, paint, inset, ROWS - inset - thickness, breite, thickness, color)
        block(canvas, paint, inset, inset, thickness, hoehe, color)
        block(canvas, paint, COLS - inset - thickness, inset, thickness, hoehe, color)
    }

    /**
     * Zähne auf allen vier Kanten, im Takt [step]. Gezählt wird von beiden
     * Enden zur Mitte, damit die Reihe an jeder Ecke gleich anfängt — von
     * links nach rechts durchgezählt bliebe an einer Kante ein Rest übrig.
     */
    private fun teeth(
        canvas: Canvas,
        paint: Paint,
        inset: Int,
        size: Int,
        step: Int,
        color: Int
    ) {
        var k = 0
        while (inset + k * step + size <= COLS - inset) {
            val col = inset + k * step
            block(canvas, paint, col, inset, size, size, color)
            block(canvas, paint, COLS - col - size, inset, size, size, color)
            block(canvas, paint, col, ROWS - inset - size, size, size, color)
            block(canvas, paint, COLS - col - size, ROWS - inset - size, size, size, color)
            k++
        }
        k = 0
        while (inset + k * step + size <= ROWS - inset) {
            val row = inset + k * step
            block(canvas, paint, inset, row, size, size, color)
            block(canvas, paint, inset, ROWS - row - size, size, size, color)
            block(canvas, paint, COLS - inset - size, row, size, size, color)
            block(canvas, paint, COLS - inset - size, ROWS - row - size, size, size, color)
            k++
        }
    }

    /** Dasselbe Quadrat in allen vier Ecken. */
    private fun cornerBlocks(
        canvas: Canvas,
        paint: Paint,
        inset: Int,
        size: Int,
        color: Int
    ) {
        for (col in intArrayOf(inset, COLS - inset - size)) {
            for (row in intArrayOf(inset, ROWS - inset - size)) {
                block(canvas, paint, col, row, size, size, color)
            }
        }
    }

    /** Dasselbe für den Rhombus der Prachtstufe. */
    private fun cornerDiamonds(
        canvas: Canvas,
        paint: Paint,
        inset: Int,
        size: Int,
        color: Int
    ) {
        for (col in intArrayOf(inset, COLS - inset - size)) {
            for (row in intArrayOf(inset, ROWS - inset - size)) {
                diamond(canvas, paint, col, row, size, color)
            }
        }
    }

    /** Ein Rhombus aus Zeilen — die einzige Form, die der Rahmen nicht rechteckig baut. */
    private fun diamond(
        canvas: Canvas,
        paint: Paint,
        col: Int,
        row: Int,
        size: Int,
        color: Int
    ) {
        val mitte = size / 2
        for (r in 0 until size) {
            // Von der nächstgelegenen Kante nach innen gezählt: Die Zeile
            // wächst bis zur Mitte und schrumpft wieder.
            val halb = min(min(r, size - 1 - r) + 1, mitte)
            block(canvas, paint, col + mitte - halb, row + r, 2 * halb, 1, color)
        }
    }

    /** Blockige Retro-Wolke, wie im Spiel aus drei Rechtecken gestapelt. */
    private fun drawCloud(canvas: Canvas, paint: Paint, x: Float, y: Float, color: Int) {
        val u = CELL * 4f
        paint.color = color
        canvas.drawRect(x, y + u * 2, x + u * 14, y + u * 5, paint)
        canvas.drawRect(x + u * 2, y, x + u * 9, y + u * 2, paint)
        canvas.drawRect(x + u * 4, y - u * 1.5f, x + u * 8, y, paint)
    }

    /**
     * Medaille wie im Game-Over: rotes Band im V, Münze mit geprägtem
     * Stern und Glanzpunkt. cy ist die Münz-Mitte, das Band sitzt darüber.
     */
    private fun drawMedal(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
        shade: Int
    ) {
        val u = radius * 2f / 10f
        fun block(c: Float, r: Float, w: Float, h: Float, col: Int) {
            paint.color = col
            canvas.drawRect(
                cx - 8f * u + c * u, cy - radius - 4.5f * u + r * u,
                cx - 8f * u + (c + w) * u, cy - radius - 4.5f * u + (r + h) * u,
                paint
            )
        }
        val leftBand = listOf(3.5f to 0f, 4.5f to 1.5f, 5.5f to 3f)
        val rightBand = listOf(9.5f to 0f, 8.5f to 1.5f, 7.5f to 3f)
        for ((c, r) in leftBand + rightBand) block(c - 0.5f, r - 0.5f, 3f, 2.5f, OUTLINE)
        for ((c, r) in leftBand) block(c, r, 2f, 1.5f, 0xFFE53935.toInt())
        for ((c, r) in rightBand) block(c, r, 2f, 1.5f, 0xFFB02A28.toInt())

        drawPixelCircle(canvas, paint, cx, cy, radius) { col, row ->
            if (col + row > 13 * 1.15f) shade else color
        }

        val cu = radius * 2f / 13f
        fun emboss(c: Float, r: Float, w: Float, h: Float) {
            paint.color = shade
            canvas.drawRect(
                cx - radius + c * cu, cy - radius + r * cu,
                cx - radius + (c + w) * cu, cy - radius + (r + h) * cu, paint
            )
        }
        emboss(5f, 5f, 3f, 3f)
        emboss(5.5f, 3.5f, 2f, 2f)
        emboss(5.5f, 7.5f, 2f, 2f)
        emboss(3.5f, 5.5f, 2f, 2f)
        emboss(7.5f, 5.5f, 2f, 2f)
        paint.color = 0xFFFFF3B8.toInt()
        canvas.drawRect(
            cx - radius + 2.5f * cu, cy - radius + 2.5f * cu,
            cx - radius + 4.5f * cu, cy - radius + 4.5f * cu, paint
        )
    }

    /** Pixel-Kreis mit Outline und Schattenseite, wie drawPixelCircle im Spiel. */
    private fun drawPixelCircle(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        radius: Float,
        cell: (col: Int, row: Int) -> Int
    ) {
        val grid = 13
        val u = radius * 2f / grid
        val mid = (grid - 1) / 2f
        val rr = grid / 2f - 0.25f
        for (row in 0 until grid) {
            for (col in 0 until grid) {
                val dx = col - mid
                val dy = row - mid
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > rr) continue
                paint.color = if (dist > rr - 1.1f) OUTLINE else cell(col, row)
                canvas.drawRect(
                    cx - radius + col * u,
                    cy - radius + row * u,
                    cx - radius + (col + 1) * u + 0.5f,
                    cy - radius + (row + 1) * u + 0.5f,
                    paint
                )
            }
        }
    }

    /**
     * Der Spiel-Punkt samt Glanz und Auge im gewählten Skin. Bewegte Skins
     * stehen auf dem Bild still: Ein geteilter Screenshot ist ein Standbild,
     * also bleibt [SkinState.elapsed] bei 0 — Stunde und Monat trägt der
     * Aufrufer bei, damit TAGESZEIT und JAHRESZEIT nicht ewig Mittag im
     * Juni zeigen.
     */
    private fun drawPixelDot(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        r: Float,
        skin: DotSkin,
        state: SkinState
    ) {
        drawPixelCircle(canvas, paint, cx, cy, r) { col, row -> skin.cell(col, row, state).toInt() }
        val u = r * 2f / 13f
        fun cellRect(col: Float, row: Float, cols: Float, rows: Float, color: Int) {
            paint.color = color
            canvas.drawRect(
                cx - r + col * u, cy - r + row * u,
                cx - r + (col + cols) * u, cy - r + (row + rows) * u, paint
            )
        }
        cellRect(2.5f, 2.5f, 2f, 2f, skin.shine.toInt())
        // Kontur nur, wo das Auge auf hellem Körper sonst verschwände
        // (wie im Spiel, siehe drawTimingDot).
        if (skin.needsEyeOutline) {
            cellRect(7f, 3f, 0.5f, 4f, OUTLINE)
            cellRect(7.5f, 2.5f, 3.5f, 0.5f, OUTLINE)
            cellRect(7.5f, 7f, 3.5f, 0.5f, OUTLINE)
        }
        cellRect(7.5f, 3f, 3.5f, 4f, Color.WHITE)
        cellRect(9.5f, 4f, 1.5f, 2f, OUTLINE)
    }
}
