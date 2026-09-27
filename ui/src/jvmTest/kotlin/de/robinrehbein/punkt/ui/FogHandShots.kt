package de.robinrehbein.punkt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.game.Twist
import de.robinrehbein.punkt.ui.world.FxState
import de.robinrehbein.punkt.ui.world.addTapEcho
import de.robinrehbein.punkt.ui.world.drawTapEchoes
import de.robinrehbein.punkt.ui.world.drawTimingWorld
import de.robinrehbein.punkt.ui.world.trackFog
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Bilder für den Bevel auf Nebel und Start-Hand: die Nebelbank in jeder
 * Welt (Vogel mitten in der Wolke), die Wölkchen beim Eintritt und der
 * Startbildschirm mit der Hand, losgelassen und gedrückt, dazu ein
 * Tipp-Echo.
 *
 * Wie TwistShots ein Werkzeug, kein Prüfstein: Ohne SHOTS_DIR kehrt der
 * Test sofort zurück. Die Bilder landen im Unterordner fog.
 */
class FogHandShots {

    /** Zielverzeichnis der Bilder, oder null: dann tut der Test nichts. */
    private fun shotsDir(): File? {
        val dir = System.getProperty("shots.dir") ?: System.getenv("SHOTS_DIR") ?: return null
        return File(dir, "fog").apply { mkdirs() }
    }

    /** Die Nebelbank in allen Welten, der Vogel mitten drin (unsichtbar). */
    @Test
    fun nebelWelten() {
        val dir = shotsDir() ?: return
        SceneId.entries.forEach { scene ->
            val game = seededGame(setOf(Twist.GHOST))
            check(playTo(game, 15)) { "Bot ist vor 15 Treffern gestorben" }
            runToRel(game, (game.fogStart() + game.fogEnd()) / 2f)
            shoot(dir, "nebel-${scene.name.lowercase()}.png", game, FxState(), scene)
        }
    }

    /** Die Wölkchen kurz nach dem Eintritt in den Nebel. */
    @Test
    fun nebelWoelkchen() {
        val dir = shotsDir() ?: return
        val game = seededGame(setOf(Twist.GHOST))
        check(playTo(game, 15)) { "Bot ist vor 15 Treffern gestorben" }
        val fx = FxState()
        var frames = 0
        while (fx.fogInTime < 0.08f) {
            game.update(BOT_DT)
            trackFog(fx, game, BOT_DT)
            check(++frames < MAX_BOT_FRAMES) { "Punkt erreicht den Nebel nicht" }
        }
        shoot(dir, "nebel-woelkchen.png", game, fx, SceneId.WIESE)
    }

    /** Startbildschirm mit Hand: losgelassen und gedrückt (Punkt im Grün), mit Tipp-Echo. */
    @Test
    fun startHand() {
        val dir = shotsDir() ?: return
        listOf(false to "los", true to "gedrueckt").forEach { (pressed, name) ->
            val game = seededGame(emptySet())
            game.update(0.05f)
            check(game.phase == GamePhase.READY)
            val fx = FxState().apply {
                trainingWheels = true
                handPressed = pressed
                if (pressed) handEchoTime = 0.15f
                addTapEcho(Offset(300f, 700f))
                tapEchoes.last().age = 0.12f
            }
            listOf(SceneId.WIESE, SceneId.WELTRAUM).forEach { scene ->
                shoot(dir, "start-hand-$name-${scene.name.lowercase()}.png", game, fx, scene, echoes = true)
            }
        }
    }

    private fun seededGame(twists: Set<Twist>): TimingGame =
        TimingGame(Random(SEED)).apply { twistOverride = twists }

    /** Wie in TwistShots: tippt in der inneren Hälfte des PERFEKT-Kerns. */
    private fun playTo(game: TimingGame, hits: Int): Boolean {
        var frames = 0
        while (game.hits < hits) {
            game.update(BOT_DT)
            if (game.phase == GamePhase.DYING || game.phase == GamePhase.OVER) return false
            val rel = game.relativeToZone()
            val core = game.perfectHalf() * 0.5f
            if (rel >= -core && rel <= core) game.tap()
            check(++frames < MAX_BOT_FRAMES) { "Bot kommt nicht voran" }
        }
        return true
    }

    private fun runToRel(game: TimingGame, rel: Float) {
        var frames = 0
        while (game.relativeToZone() < rel) {
            game.update(1f / 2000f)
            check(game.phase == GamePhase.RUNNING) { "Lauf endet vor rel=$rel" }
            check(++frames < MAX_BOT_FRAMES) { "Punkt erreicht rel=$rel nicht" }
        }
    }

    private fun shoot(
        dir: File,
        name: String,
        game: TimingGame,
        fx: FxState,
        scene: SceneId,
        echoes: Boolean = false
    ) {
        ImageComposeScene(width = 1080, height = 2400, density = Density(2.625f)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTimingWorld(game, fx, SkinId.KLASSIK, scene, hour = 12, month = 6)
                if (echoes) drawTapEchoes(fx)
            }
        }.use { s ->
            val data = s.render(0L).encodeToData(EncodedImageFormat.PNG)!!
            File(dir, name).writeBytes(data.bytes)
            println("-> fog/$name")
        }
    }

    private companion object {
        const val SEED = 20260925L
        const val BOT_DT = 1f / 240f
        const val MAX_BOT_FRAMES = 2_000_000
    }
}
