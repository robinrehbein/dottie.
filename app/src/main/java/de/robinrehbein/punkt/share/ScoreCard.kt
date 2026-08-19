package de.robinrehbein.punkt.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import java.io.File

/**
 * Die fertige Score-Karte aus der App herausreichen — mehr ist hier
 * nicht mehr.
 *
 * Bis v2.26 stand in dieser Datei die ganze Karte: 400 Zeilen
 * `android.graphics`, die Himmel, Wolken, Boden, Schrift, Punkt,
 * Medaille und Rahmen aufs Blatt malten. Das war der Grund, warum es die
 * Karte auf dem iPhone nicht gab — und ein zweiter Port hätte dieselbe
 * Karte ein zweites Mal beschrieben. Gezeichnet wird sie jetzt einmal in
 * `:ui` (`renderScoreCard`), auf beiden Plattformen.
 *
 * Was bleibt, kann wirklich nur Android: ein PNG im Cache, ein
 * FileProvider, der es freigibt, und `ACTION_SEND`.
 */
object ScoreCard {

    /**
     * Schreibt die Karte in den Cache und öffnet den Share-Dialog.
     *
     * Der Dateiname ist bewusst konstant: Es soll immer nur eine geteilte
     * Karte im Cache liegen, nicht eine je Lauf.
     */
    fun share(context: Context, image: ImageBitmap, text: String, chooserTitle: String) {
        val bitmap = image.asAndroidBitmap()
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "punkt-score.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }
}
