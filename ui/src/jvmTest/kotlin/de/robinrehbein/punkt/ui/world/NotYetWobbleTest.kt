package de.robinrehbein.punkt.ui.world

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.TimingGame
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * Nach NOCH NICHT wackelt auch der Vogel (Plan 8.7, Mockup :605):
 * dx = sin(t·40)·4·t dp, 0,7 s lang, nur in READY (AP-31).
 *
 * Wo der Vogel steht, zeigt das Bild: Dieselbe Szene einmal mit dem
 * gelben KLASSIK- und einmal mit dem grünen MINZE-Vogel. Die Pixel, die
 * sich unterscheiden, sind der Vogel; ihr Schwerpunkt ist seine Lage.
 */
class NotYetWobbleTest {

    @Test
    fun `der Vogel wackelt nach NOCH NICHT seitlich`() {
        val game = TimingGame(Random(SEED))
        repeat(20) { game.update(1f / 60f) }
        check(game.phase == GamePhase.READY)

        val ruhig = birdCenterX(game, notYetTime = 0f)
        val t = 0.66f
        val bewegt = birdCenterX(game, notYetTime = t)
        val erwartet = sin(t * 40f) * 4f * t * DENSITY
        assertTrue(abs(erwartet) > 3f, "Prüfzeitpunkt ohne Ausschlag gewählt")
        assertEquals(erwartet, bewegt - ruhig, 1.5f, "Vogel wackelt nicht wie im Mockup")
    }

    @Test
    fun `im Lauf wackelt nichts`() {
        val game = TimingGame(Random(SEED))
        game.start()
        repeat(5) { game.update(1f / 60f) }
        check(game.phase == GamePhase.RUNNING)
        assertEquals(birdCenterX(game, 0f), birdCenterX(game, 0.66f), 0.01f)
    }

    private fun birdCenterX(game: TimingGame, notYetTime: Float): Float {
        val fx = FxState().apply {
            trainingWheels = false
            this.notYetTime = notYetTime
        }
        val a = render(game, fx, SkinId.KLASSIK).readPixels()!!
        val b = render(game, fx, SkinId.MINZE).readPixels()!!
        var sum = 0.0
        var n = 0
        for (i in a.indices step 4) {
            if (a[i] != b[i] || a[i + 1] != b[i + 1] || a[i + 2] != b[i + 2]) {
                sum += (i / 4) % W
                n++
            }
        }
        check(n > 0) { "kein Vogel im Bild" }
        return (sum / n).toFloat()
    }

    private fun render(game: TimingGame, fx: FxState, skin: SkinId): Bitmap =
        ImageComposeScene(width = W, height = H, density = Density(DENSITY)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTimingWorld(game, fx, skin, SceneId.WIESE, hour = 12, month = 6)
            }
        }.use { scene -> Bitmap.makeFromImage(scene.render(0L)) }

    private companion object {
        const val SEED = 20260925L
        const val W = 720
        const val H = 1280
        const val DENSITY = 2f
    }
}
