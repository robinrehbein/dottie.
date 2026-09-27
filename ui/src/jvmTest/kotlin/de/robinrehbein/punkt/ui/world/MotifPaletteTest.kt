package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.graphics.Color
import de.robinrehbein.punkt.game.GroundPaint
import de.robinrehbein.punkt.game.BevelPaint
import de.robinrehbein.punkt.game.MotifPixel
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.ZoneMotif
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Zonenmotive in :core tragen einige Töne der Palette aus :ui als
 * Werte (DotBody, GrassEdge, GrassLight, GrassDeep), weil :core die
 * Palette nicht kennt. Ändert jemand die Palette, liefen Zone und Motiv
 * still auseinander — hier fällt es auf.
 */
class MotifPaletteTest {

    private fun at(pixels: List<MotifPixel>, dx: Int, dy: Int): Color =
        Color(pixels.single { it.dx == dx && it.dy == dy }.color)

    @Test
    fun `die Motive tragen die Toene der Palette`() {
        val seerose = ScenePaint.motif(ZoneMotif.SEEROSE, core = false)
        assertEquals(GrassEdge, at(seerose, 0, -1), "Seerosenstiel ist GrassEdge")
        assertEquals(GrassEdge, at(seerose, 0, -2), "Seerosenstiel ist GrassEdge")
        assertEquals(DotBody, at(ScenePaint.motif(ZoneMotif.SEEROSE, core = true), 0, 0), "Mitte ist DotBody")

        val edelweiss = ScenePaint.motif(ZoneMotif.EDELWEISS, core = false)
        assertEquals(GrassLight, at(edelweiss, 1, -1), "Moos hell ist GrassLight")
        assertEquals(GrassDeep, at(edelweiss, 0, 1), "Moos dunkel ist GrassDeep")
        assertEquals(DotBody, at(ScenePaint.motif(ZoneMotif.EDELWEISS, core = true), 0, 0), "Mitte ist DotBody")
    }

    @Test
    fun `die Kiesel der WIESE kommen aus ihrem Sand`() {
        val sand = ScenePaint.ground(SceneId.WIESE)!!.sand
        assertEquals(GroundSand, Color(sand), "Der Sand der WIESE ist GroundSand")
        assertEquals(BevelPaint.light(sand), GroundPaint.PEBBLE_LIGHT)
        assertEquals(BevelPaint.mix(sand, BevelPaint.OUTLINE, 0.2f), GroundPaint.PEBBLE_DARK)
    }
}
