package de.robinrehbein.punkt.ui.world

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.TimingGame
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * Die Bahn im gerenderten Bild (1080×2340, WIESE): keine rosa Blüte mehr
 * im Perfekt-Kern, und ein Sandblock hat links und rechts genau eine
 * Rasterstufe (5 px) Umriss, darin eine Stufe Licht- bzw. Schattenkante.
 */
class ZoneBlockRenderTest {

    private fun runningGame(): TimingGame {
        val game = TimingGame(Random(SEED)).apply { twistOverride = emptySet() }
        game.start()
        repeat(5) { game.update(1f / 60f) }
        check(game.phase == GamePhase.RUNNING)
        return game
    }

    private fun render(game: TimingGame): Bitmap =
        ImageComposeScene(width = W, height = H, density = Density(DENSITY)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTimingWorld(game, FxState().apply { trainingWheels = false }, SkinId.KLASSIK, SceneId.WIESE, hour = 12, month = 6)
            }
        }.use { scene -> Bitmap.makeFromImage(scene.render(0L)) }

    @Test
    fun `im Bild der Zone gibt es kein BlossomPink mehr`() {
        val bitmap = render(runningGame())
        val pink = 0xFFE8607A.toInt()
        var pinkCount = 0
        var halo = 0
        var leafCore = 0
        for (y in 0 until H) for (x in 0 until W) {
            val c = bitmap.getColor(x, y)
            if (c == pink) pinkCount++
            if (c == ZoneCoreHalo.toArgb()) halo++
            if (c == GrassLeafCore.toArgb()) leafCore++
        }
        assertTrue(halo > 0 && leafCore > 0, "Perfekt-Kern ist im Bild (Saum $halo, Blätter $leafCore)")
        assertEquals(0, pinkCount, "BlossomPink-Pixel im Bild")
    }

    @Test
    fun `ein Sandblock hat genau eine Rasterstufe Umriss`() {
        val game = runningGame()
        val bitmap = render(game)
        val (cx, cy, radius) = ringGeometry(Size(W.toFloat(), H.toFloat()))
        val cell = floor(H / 220f)
        val unit = trackUnit(cell)
        assertEquals(5, unit)
        // Ein Platz gegenüber der Zone, weit weg vom Vogel.
        val k = (0 until TRACK_SEGMENTS).maxBy { k ->
            val a = trackSlotAngle(k, TRACK_SEGMENTS)
            minOf(
                abs(TimingGame.wrapToPi(a - game.zoneCenter)),
                abs(TimingGame.wrapToPi(a - game.angle))
            )
        }
        val a = trackSlotAngle(k, TRACK_SEGMENTS)
        val b = sandBlock(cx + cos(a) * radius, cy + sin(a) * radius, cell)
        val outline = OutlineColor.toArgb()
        // Seit dem Bevel-Look: in der Fläche links eine Stufe Licht, rechts
        // eine Stufe Schatten (ScenePaint.track, WIESE), dazwischen Sand.
        val track = ScenePaint.track(SceneId.WIESE)
        assertEquals(GroundSandShade, Color(track.block), "WIESE behält GroundSandShade als Fläche")
        val sand = GroundSandShade.toArgb()
        val light = Color(track.light).toArgb()
        val dark = Color(track.dark).toArgb()
        val y = (b.top + b.outer / 2f).toInt()
        val row = (b.left.toInt() - 1..(b.left + b.outer).toInt()).map { bitmap.getColor(it, y) }
        val expected = listOf(null) + List(unit) { outline } + List(unit) { light } +
            List(b.inner.toInt() - 2 * unit) { sand } + List(unit) { dark } +
            List(unit) { outline } + listOf(null)
        assertEquals(expected.size, row.size)
        for ((i, e) in expected.withIndex()) {
            if (e == null) {
                assertFalse(row[i] == outline, "Umriss breiter als eine Stufe (Spalte $i)")
            } else {
                assertEquals(Color(e), Color(row[i]), "Spalte $i des Sandblocks $k")
            }
        }
        val col = (b.top.toInt() - 1..(b.top + b.outer).toInt()).map { bitmap.getColor((b.left + b.outer / 2f).toInt(), it) }
        assertEquals(List(unit) { outline }, col.subList(1, 1 + unit), "Umriss oben")
        assertEquals(List(unit) { outline }, col.subList(col.size - 1 - unit, col.size - 1), "Umriss unten")
        assertFalse(col.first() == outline || col.last() == outline, "Umriss oben/unten zu breit")
    }

    private companion object {
        const val W = 1080
        const val H = 2340
        const val DENSITY = 2.625f
        const val SEED = 7L
    }
}
