package de.robinrehbein.punkt.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import de.robinrehbein.punkt.R
import de.robinrehbein.punkt.game.DotSkin
import java.io.File
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

    /** Himmelsfarbe pro 5er-Stufe — Kopie der SkyStages aus dem Spiel. */
    private val SKY = intArrayOf(
        0xFF4EC0CA.toInt(), 0xFF5B9BD5.toInt(), 0xFF7B6FD0.toInt(),
        0xFFC0616F.toInt(), 0xFFD98A3D.toInt(), 0xFF3D4A8C.toInt(),
        0xFF2A2640.toInt()
    )
    private const val OUTLINE = 0xFF543847.toInt()
    private const val CLOUD = 0xFFE9FCFD.toInt()
    private const val SAND = 0xFFDED895.toInt()
    private const val GRASS_LIGHT = 0xFF9DE85A.toInt()
    private const val GRASS_DARK = 0xFF74BF2E.toInt()
    private const val ACCENT = 0xFFFF8A3C.toInt()
    private const val RECORD_YELLOW = 0xFFFFE95E.toInt()

    /** Baut die Card, schreibt sie in den Cache und öffnet den Share-Dialog. */
    fun share(
        context: Context,
        score: Int,
        bestScore: Int,
        isNewRecord: Boolean,
        skin: DotSkin,
        daily: Boolean,
        dailyStreak: Int
    ) {
        val bitmap = render(context, score, bestScore, isNewRecord, skin, daily, dailyStreak)
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
                if (daily) "Daily Challenge in PUNKT.: $score Punkte — schaffst du mehr?"
                else "$score Punkte in PUNKT. — schaffst du mehr?"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Score teilen"))
    }

    private fun render(
        context: Context,
        score: Int,
        bestScore: Int,
        isNewRecord: Boolean,
        skin: DotSkin,
        daily: Boolean,
        dailyStreak: Int
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

        // Himmel nach erreichter Stufe, wie im Spiel
        paint.color = SKY[(score / 5).coerceAtMost(SKY.size - 1)]
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)

        drawCloud(canvas, paint, W * 0.08f, H * 0.10f)
        drawCloud(canvas, paint, W * 0.62f, H * 0.17f)

        // Boden mit Grasnarbe
        val groundTop = H * 0.86f
        paint.color = SAND
        canvas.drawRect(0f, groundTop, W.toFloat(), H.toFloat(), paint)
        paint.color = GRASS_DARK
        canvas.drawRect(0f, groundTop, W.toFloat(), groundTop + CELL * 5, paint)
        paint.color = GRASS_LIGHT
        var x = 0f
        while (x < W) {
            canvas.drawRect(x, groundTop, x + CELL * 5, groundTop + CELL * 4, paint)
            x += CELL * 10
        }
        paint.color = OUTLINE
        canvas.drawRect(0f, groundTop - CELL, W.toFloat(), groundTop, paint)

        val cx = W / 2f
        drawShadowed("PUNKT.", cx, H * 0.14f, 130f, Color.WHITE)
        if (daily) {
            drawShadowed("DAILY-CHALLENGE", cx, H * 0.20f, 56f, RECORD_YELLOW)
        }

        // Punkt im gewählten Skin, mittig über dem Score
        drawPixelDot(canvas, paint, cx, H * 0.32f, 110f, skin)

        drawShadowed(score.toString(), cx, H * 0.55f, 320f, Color.WHITE)
        drawShadowed("PUNKTE", cx, H * 0.60f, 60f, Color.WHITE)

        val recordLine = when {
            isNewRecord -> "NEUER REKORD!"
            daily && dailyStreak > 1 -> "DAILY-SERIE: $dailyStreak TAGE"
            else -> "REKORD: $bestScore"
        }
        drawShadowed(recordLine, cx, H * 0.68f, 68f, if (isNewRecord) RECORD_YELLOW else Color.WHITE)

        val medal = when {
            score >= 40 -> 0xFFE5E4E2.toInt()
            score >= 30 -> 0xFFFFD700.toInt()
            score >= 20 -> 0xFFC0C0C0.toInt()
            score >= 10 -> 0xFFCD7F32.toInt()
            else -> null
        }
        if (medal != null) {
            drawPixelCircle(canvas, paint, cx, H * 0.755f, 62f, medal)
        }

        drawShadowed("SCHAFFST DU MEHR?", cx, H * 0.945f, 72f, ACCENT)
        return bmp
    }

    /** Blockige Retro-Wolke, wie im Spiel aus drei Rechtecken gestapelt. */
    private fun drawCloud(canvas: Canvas, paint: Paint, x: Float, y: Float) {
        val u = CELL * 4f
        paint.color = CLOUD
        canvas.drawRect(x, y + u * 2, x + u * 14, y + u * 5, paint)
        canvas.drawRect(x + u * 2, y, x + u * 9, y + u * 2, paint)
        canvas.drawRect(x + u * 4, y - u * 1.5f, x + u * 8, y, paint)
    }

    /** Pixel-Kreis mit Outline und Schattenseite, wie drawPixelCircle im Spiel. */
    private fun drawPixelCircle(
        canvas: Canvas,
        paint: Paint,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
        shade: Int = color
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
                paint.color = when {
                    dist > rr - 1.1f -> OUTLINE
                    row + col > grid * 1.15f -> shade
                    else -> color
                }
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

    /** Der Spiel-Punkt samt Glanz und Auge im gewählten Skin. */
    private fun drawPixelDot(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, skin: DotSkin) {
        drawPixelCircle(canvas, paint, cx, cy, r, skin.body.toInt(), skin.shade.toInt())
        val u = r * 2f / 13f
        fun cellRect(col: Float, row: Float, cols: Float, rows: Float, color: Int) {
            paint.color = color
            canvas.drawRect(
                cx - r + col * u, cy - r + row * u,
                cx - r + (col + cols) * u, cy - r + (row + rows) * u, paint
            )
        }
        cellRect(2.5f, 2.5f, 2f, 2f, skin.shine.toInt())
        cellRect(7.5f, 3f, 3.5f, 4f, Color.WHITE)
        cellRect(9.5f, 4f, 1.5f, 2f, OUTLINE)
    }
}
