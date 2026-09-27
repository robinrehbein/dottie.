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
import de.robinrehbein.punkt.game.Twist
import de.robinrehbein.punkt.ui.screens.ScoreHud
import de.robinrehbein.punkt.ui.world.FxState
import de.robinrehbein.punkt.ui.world.drawTimingWorld
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Bevel-Bilder ohne Bildschirm: jede Welt mit jedem Twist, als Matrix
 * neben die Zielbilder in docs/bevel-mockups/ziel zu legen.
 *
 * Seeds, Skin-Folge, Bot und Anlauf sind dieselben wie im Prototyp
 * (TmpTwistMatrix in docs/bevel-prototyp.patch), mit dem die Zielbilder
 * gerendert wurden. Nur so lassen sich die Bilder Pixel für Pixel
 * vergleichen: Ein anderer Seed legt die Zone woanders hin, ein anderer
 * Skin zeigt eine andere Kugel.
 *
 * Wie TwistShots ein Werkzeug, kein Prüfstein: Ohne SHOTS_DIR kehrt der
 * Test sofort zurück. Die Bilder landen im Unterordner matrix als
 * <welt>-<twist>-<skin>.png.
 */
class BevelShots {

    /** Zielverzeichnis der Bilder, oder null: dann tut der Test nichts. */
    private fun shotsDir(): File? {
        val dir = System.getProperty("shots.dir") ?: System.getenv("SHOTS_DIR") ?: return null
        return File(dir, "matrix").apply { mkdirs() }
    }

    /**
     * Sechs Welten mal sechs Varianten, 36 Bilder. Der Skin wechselt von
     * Bild zu Bild (Schritt 7 durch die Skin-Liste), damit jede Kugel
     * einmal in einer anderen Welt auftaucht, ohne die Matrix um eine
     * dritte Achse aufzublähen.
     */
    @Test
    fun matrix() {
        val dir = shotsDir() ?: return
        val skins = SkinId.entries
        var n = 0
        SceneId.entries.forEach { scene ->
            VARIANTEN.forEachIndexed { vi, (name, twists) ->
                val skin = skins[(n * 7 + 3) % skins.size]
                n++
                // Je Variante ein eigener Seed, wie im Prototyp: Die
                // Zielbilder hängen daran.
                val game = TimingGame(Random(SEED + vi)).apply { twistOverride = twists }
                check(playTo(game, 2)) { "$scene/$name: Bot gestorben" }
                // Die Falle liegt nicht auf jeder Zone: weiterspielen, bis sie da ist.
                var runden = 0
                while (Twist.FAKE in twists && !game.hasFakeZone) {
                    check(playTo(game, game.hits + 1)) { "$scene/$name: Bot gestorben" }
                    check(++runden < 30) { "$scene/$name: keine Falle" }
                }
                // Unter NEBEL näher heran, sonst steht der Vogel noch weit
                // vor der Wolke und das Bild zeigt nichts vom Twist.
                approachZone(game, if (name == "nebel") 0.35f else 0.7f)
                shoot(dir, "${scene.name.lowercase()}-$name-${skin.name.lowercase()}.png", game, skin, scene)
            }
        }
    }

    /**
     * Spielt, bis [hits] Treffer erreicht sind: Getippt wird genau dann,
     * wenn der Punkt die innere Hälfte des PERFEKT-Kerns erreicht — derselbe
     * Bot wie in TwistShots. Liefert false, falls das Spiel vorher endet.
     */
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

    /** Lässt den Punkt bis [gap] vor die Zone laufen, ohne zu tippen. */
    private fun approachZone(game: TimingGame, gap: Float) {
        var frames = 0
        while (game.relativeToZone() < -gap - game.effectiveZoneHalf()) {
            game.update(BOT_DT)
            check(++frames < MAX_BOT_FRAMES) { "Punkt erreicht die Zone nicht" }
        }
    }

    /** Welt und Score-Anzeige übereinander, wie im GameScreen, als PNG. */
    private fun shoot(dir: File, name: String, game: TimingGame, skin: SkinId, scene: SceneId) {
        ImageComposeScene(width = 1080, height = 2400, density = Density(2.625f)) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawTimingWorld(game, FxState(), skin, scene, hour = 12, month = 6)
                }
                ScoreHud(score = game.score)
            }
        }.use { s ->
            val data = s.render(0L).encodeToData(EncodedImageFormat.PNG)!!
            File(dir, name).writeBytes(data.bytes)
            println("-> matrix/$name")
        }
    }

    private companion object {
        const val SEED = 20260925L
        const val BOT_DT = 1f / 240f
        const val MAX_BOT_FRAMES = 2_000_000

        /** Die sechs Varianten der Matrix: ohne Twist und je ein Twist. */
        val VARIANTEN = listOf<Pair<String, Set<Twist>>>(
            "basis" to emptySet(),
            "puls" to setOf(Twist.PULSE),
            "drift" to setOf(Twist.DRIFT),
            "nebel" to setOf(Twist.GHOST),
            "bomben" to setOf(Twist.FAKE),
            "kette" to setOf(Twist.CHAIN)
        )
    }
}
