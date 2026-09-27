package de.robinrehbein.punkt.ui.share

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import de.robinrehbein.punkt.game.CardFrame
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.ui.theme.Bytesized
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * Die geteilte Score-Karte als PNG, für jede Welt einmal, mit
 * wechselnden Skins und Rahmen — zum Vergleich vor und nach einer
 * Änderung am Kartenbild.
 *
 * Wie BevelShots ein Werkzeug, kein Prüfstein: Ohne SHOTS_DIR kehrt der
 * Test sofort zurück. Die Bilder landen im Unterordner share als
 * <welt>-<rahmen>-<skin>.png. Gradle kennt SHOTS_DIR nicht als Eingabe,
 * deshalb mit --rerun starten.
 */
class ScoreCardShots {

    @Test
    fun karten() {
        val dir = System.getProperty("shots.dir") ?: System.getenv("SHOTS_DIR") ?: return
        val out = File(dir, "share").apply { mkdirs() }
        FAELLE.forEach { fall ->
            val name = "${fall.scene.name.lowercase()}-${fall.frame.name.lowercase()}-${fall.skin.name.lowercase()}.png"
            val bild = rendern(fall)
            val data = Image.makeFromBitmap(bild.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!
            File(out, name).writeBytes(data.bytes)
            println("-> share/$name")
        }
    }

    /**
     * Schrift und Textmesser gibt es nur in einer Komposition: Die Karte
     * wird darum in einer leeren Szene gerendert, so wie der GameScreen
     * sie beim Tippen auf TEILEN baut.
     */
    private fun rendern(fall: Fall): ImageBitmap {
        var bild: ImageBitmap? = null
        ImageComposeScene(width = 10, height = 10, density = Density(1f)) {
            val mass = rememberTextMeasurer()
            val schrift = Bytesized
            SideEffect {
                bild = renderScoreCard(
                    content = ScoreCardContent(
                        score = fall.score,
                        skin = fall.skin,
                        scene = fall.scene,
                        frame = fall.frame,
                        hour = 12,
                        month = 6,
                        dailyLine = if (fall.daily) "DAILY 17.06." else null,
                        epithet = fall.epithet,
                        pointsLine = "PUNKTE",
                        sceneLine = fall.scene.name,
                        recordLine = "REKORD ${fall.score + 3}",
                        recordHighlighted = fall.daily,
                        challengeLine = "SCHAFFST DU DAS?"
                    ),
                    measurer = mass,
                    font = schrift
                )
            }
        }.use { s ->
            // Zweimal: Compose Resources lädt die Schrift beim ersten
            // Durchlauf, erst der zweite misst mit Bytesized.
            s.render(0L).close()
            s.render(16_000_000L).close()
        }
        return checkNotNull(bild) { "Karte nicht gerendert" }
    }

    private data class Fall(
        val scene: SceneId,
        val frame: CardFrame,
        val skin: SkinId,
        val score: Int,
        val epithet: String? = null,
        val daily: Boolean = false
    )

    private companion object {
        val FAELLE = listOf(
            Fall(SceneId.WIESE, CardFrame.SCHLICHT, SkinId.KLASSIK, 12),
            Fall(SceneId.WUESTE, CardFrame.ZINNEN, SkinId.BIENE, 24, epithet = "SCHARFSCHUETZE"),
            Fall(SceneId.MEER, CardFrame.PRACHT, SkinId.KOI, 33, daily = true),
            Fall(SceneId.BERG, CardFrame.KASKADE, SkinId.GALAXIE, 41, epithet = "STAMMGAST"),
            Fall(SceneId.STADT, CardFrame.PERLENKRANZ, SkinId.CHROM, 18),
            Fall(SceneId.WELTRAUM, CardFrame.KRONE, SkinId.GOLD, 57, epithet = "LEGENDE"),
            Fall(SceneId.WIESE, CardFrame.DOPPELLINIE, SkinId.SCHATTEN, 7)
        )
    }
}
