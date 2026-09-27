package de.robinrehbein.punkt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.ui.screens.ScoreHud
import de.robinrehbein.punkt.ui.world.FxState
import de.robinrehbein.punkt.ui.world.drawTimingWorld
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/**
 * Die Welten ohne Bildschirm: jede Kulisse über den Tageslauf ihres
 * Himmels (Score 0, 10, 20, 30), als Einzelbilder und als Kontaktbogen
 * `welten.png` (Zeilen: Welten, Spalten: Himmelsstufen).
 *
 * Wie BevelShots ein Werkzeug, kein Prüfstein: Ohne SHOTS_DIR kehrt der
 * Test sofort zurück. Die Bilder landen im Unterordner welten.
 */
class WorldShots {

    @Test
    fun welten() {
        val root = System.getProperty("shots.dir") ?: System.getenv("SHOTS_DIR") ?: return
        val dir = File(root, "welten").apply { mkdirs() }
        val thumbW = 270
        val thumbH = 270 * shotSize.second / shotSize.first
        val sheet = Surface.makeRasterN32Premul(thumbW * SCORES.size, thumbH * SceneId.entries.size)
        SceneId.entries.forEachIndexed { row, scene ->
            // Ein Spiel je Welt, das sich durch die Stufen spielt: Die
            // Zeit läuft mit, damit Wolken und Requisiten wandern.
            val game = TimingGame(Random(SEED)).apply { twistOverride = emptySet() }
            SCORES.forEachIndexed { col, score ->
                check(playToScore(game, score)) { "$scene: Bot gestorben vor $score" }
                val img = shoot(game, scene)
                File(dir, "${scene.name.lowercase()}-$score.png")
                    .writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                sheet.canvas.drawImageRect(
                    img,
                    Rect.makeXYWH((col * thumbW).toFloat(), (row * thumbH).toFloat(), thumbW.toFloat(), thumbH.toFloat())
                )
                img.close()
            }
        }
        val out = sheet.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!
        File(dir, "welten.png").writeBytes(out.bytes)
        println("-> welten/welten.png")
    }

    /**
     * Die Bewohner der Welten: jede Welt zu sechs Zeitpunkten, vier am Tag
     * (Score 0, der Vogel wartet) und zwei in der Nacht (Score 30, der
     * Bot spielt weiter). Die Zeitpunkte liegen so, dass Delfin,
     * Steppenläufer, Flugzeug und Satellit mindestens einmal im Bild sind.
     * Kontaktbogen `leben.png`, Zeilen Welten, Spalten Zeitpunkte.
     */
    @Test
    fun leben() {
        val root = System.getProperty("shots.dir") ?: System.getenv("SHOTS_DIR") ?: return
        val dir = File(root, "welten").apply { mkdirs() }
        val thumbW = 270
        val thumbH = 600
        val cols = DAY_TIMES.size + NIGHT_TIMES.size
        val sheet = Surface.makeRasterN32Premul(thumbW * cols, thumbH * SceneId.entries.size)
        SceneId.entries.forEachIndexed { row, scene ->
            val day = TimingGame(Random(SEED)).apply { twistOverride = emptySet() }
            DAY_TIMES.forEachIndexed { col, t ->
                while (day.elapsed < t) day.update(BOT_DT)
                val img = shoot(day, scene)
                File(dir, "leben-${scene.name.lowercase()}-tag-$col.png")
                    .writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                sheet.canvas.drawImageRect(img, Rect.makeXYWH((col * thumbW).toFloat(), (row * thumbH).toFloat(), thumbW.toFloat(), thumbH.toFloat()))
                img.close()
            }
            val night = TimingGame(Random(SEED)).apply { twistOverride = emptySet() }
            check(playToScore(night, 30))
            NIGHT_TIMES.forEachIndexed { k, dt ->
                check(botFor(night, dt)) { "$scene: Bot gestorben in der Nacht" }
                val img = shoot(night, scene)
                val col = DAY_TIMES.size + k
                File(dir, "leben-${scene.name.lowercase()}-nacht-$k.png")
                    .writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                sheet.canvas.drawImageRect(img, Rect.makeXYWH((col * thumbW).toFloat(), (row * thumbH).toFloat(), thumbW.toFloat(), thumbH.toFloat()))
                img.close()
            }
        }
        File(dir, "leben.png").writeBytes(sheet.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes)
        println("-> welten/leben.png")
    }

    /** Lässt den Bot [seconds] Sekunden weiterspielen; false, wenn er stirbt. */
    private fun botFor(game: TimingGame, seconds: Float): Boolean {
        val until = game.elapsed + seconds
        while (game.elapsed < until) {
            game.update(BOT_DT)
            if (game.phase == GamePhase.DYING || game.phase == GamePhase.OVER) return false
            val rel = game.relativeToZone()
            val core = game.perfectHalf() * 0.5f
            if (rel >= -core && rel <= core) game.tap()
        }
        return true
    }

    /**
     * Bewegungsstreifen: dieselbe Welt in [FRAMES] aufeinanderfolgenden
     * Bildern im 60-Hz-Takt, nur der untere Bildteil. Nebeneinander
     * gelegt zeigt der Streifen, ob etwas gleitet oder springt.
     */
    @Test
    fun bewegung() {
        val root = System.getProperty("shots.dir") ?: System.getenv("SHOTS_DIR") ?: return
        val scenes = (System.getProperty("shots.scenes") ?: System.getenv("SHOTS_SCENES"))
            ?.split(',')?.map { SceneId.valueOf(it) } ?: return
        val dir = File(root, "bewegung").apply { mkdirs() }
        scenes.forEach { scene ->
            val game = TimingGame(Random(SEED)).apply { twistOverride = emptySet() }
            check(playToScore(game, 1))
            repeat(FRAMES) { f ->
                game.update(1f / 60f)
                val img = shoot(game, scene)
                File(dir, "${scene.name.lowercase()}-$f.png")
                    .writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                img.close()
            }
        }
    }

    /** Bildgröße, umstellbar mit SHOTS_SIZE=BreitexHöhe (Standard 1080x2400). */
    private val shotSize: Pair<Int, Int> =
        (System.getProperty("shots.size") ?: System.getenv("SHOTS_SIZE"))
            ?.split('x')?.let { it[0].toInt() to it[1].toInt() } ?: (1080 to 2400)

    private fun shoot(game: TimingGame, scene: SceneId): Image {
        return ImageComposeScene(width = shotSize.first, height = shotSize.second, density = Density(2.625f * shotSize.first / 1080f)) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawTimingWorld(game, FxState().apply { trainingWheels = false }, SkinId.KLASSIK, scene, hour = 12, month = 6)
                }
                ScoreHud(score = game.score)
            }
        }.use { it.render(0L) }
    }

    /** Spielt mit dem Bot aus BevelShots, bis [score] erreicht ist. */
    private fun playToScore(game: TimingGame, score: Int): Boolean {
        var frames = 0
        while (game.score < score) {
            game.update(BOT_DT)
            if (game.phase == GamePhase.DYING || game.phase == GamePhase.OVER) return false
            val rel = game.relativeToZone()
            val core = game.perfectHalf() * 0.5f
            if (rel >= -core && rel <= core) game.tap()
            check(++frames < 20_000_000) { "Bot kommt nicht voran" }
        }
        // Ein Stück weiterlaufen, damit der Vogel nicht auf der Zone klebt.
        repeat(90) { game.update(BOT_DT) }
        return true
    }

    private companion object {
        const val SEED = 20260927L
        const val BOT_DT = 1f / 240f
        const val FRAMES = 6
        val SCORES = listOf(0, 10, 20, 30)

        /** Tag: Delfin (8 s, Sprung 1,5 s), Steppenläufer (12 s), Flugzeug (24 s), Satellit. */
        val DAY_TIMES = listOf(8.6f, 14.5f, 26.5f, 40.8f)

        /** Nacht: Sekunden, die der Bot nach Score 30 weiterspielt. */
        val NIGHT_TIMES = listOf(0.5f, 3f)
    }
}
