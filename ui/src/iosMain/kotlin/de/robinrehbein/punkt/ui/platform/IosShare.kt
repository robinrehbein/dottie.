@file:OptIn(ExperimentalForeignApi::class)

package de.robinrehbein.punkt.ui.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIImage
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Die fertige Score-Karte an das Teilen-Blatt von iOS reichen.
 *
 * Das Gegenstueck zu `ScoreCard.share` in der Android-Schale, und der
 * ganze Unterschied zwischen den Plattformen: Gezeichnet ist die Karte
 * da schon — einmal, in `:ui`, fuer beide Apps (siehe
 * `renderScoreCard`). Hier bleibt nur der Weg nach draussen.
 *
 * Wie bei [IosSounds] laeuft der ueber Bytes und nicht ueber Dateien:
 * Skia gibt das Bild als PNG-Puffer heraus, der wird zu [NSData] und
 * damit zu einem [UIImage]. Eine Zwischendatei braeuchte einen Ort, an
 * dem sie liegen bleibt, und ein Aufraeumen dafuer.
 */
object IosShare {

    /**
     * Zeigt das Teilen-Blatt ueber [presenter].
     *
     * Der Ausgangs-Controller kommt von aussen (siehe MainViewController)
     * statt aus `UIApplication.keyWindow`: Er ist genau der, den die App
     * selbst gebaut hat — nach ihm zu suchen, waere die Frage nach etwas,
     * das man schon hat.
     */
    fun present(presenter: UIViewController, image: ImageBitmap, text: String) {
        val png = pngBytes(image) ?: return
        val daten = png.usePinned { fest ->
            NSData.create(bytes = fest.addressOf(0), length = png.size.toULong())
        }
        val bild = UIImage(data = daten)
        // Das Blatt darf nur vom Haupt-Thread aus praesentiert werden.
        // Der Druck auf TEILEN kommt zwar von dort, aber das ist eine
        // Annahme ueber Compose und keine Zusage — also ausdruecklich.
        dispatch_async(dispatch_get_main_queue()) {
            val teilen = UIActivityViewController(
                activityItems = listOf(bild, text),
                applicationActivities = null
            )
            // Ohne Popover-Verankerung: Die App ist iPhone-only
            // (TARGETED_DEVICE_FAMILY 1 in ios/project.yml), und dort
            // faehrt das Blatt von unten herein. Auf einem iPad braeuchte
            // es eine sourceView — dann aber zusammen mit allem anderen,
            // was ein iPad-Layout verlangt.
            presenter.presentViewController(teilen, animated = true, completion = null)
        }
    }

    /**
     * Die Karte als PNG. Compose zeichnet auf iOS in eine Skia-Bitmap —
     * die kann sich selbst kodieren, ein Umweg ueber CoreGraphics waere
     * derselbe Weg zweimal.
     */
    private fun pngBytes(image: ImageBitmap): ByteArray? =
        Image.makeFromBitmap(image.asSkiaBitmap())
            .encodeToData(EncodedImageFormat.PNG)
            ?.bytes
}
