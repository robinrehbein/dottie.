package de.robinrehbein.punkt.wear

import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.game.TrapPaint
import de.robinrehbein.punkt.game.Twist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.round

/**
 * Kulisse, Nebel-Partikel, Lauflicht und Effekte auf der Uhr: geprüft
 * wird die Geometrie und der Takt, aus denen WearRenderer zeichnet.
 */
class WearSceneryTest {

    /** Runde Uhren von 384 (Pixel Watch) bis 480 px (Galaxy Watch Ultra). */
    private val displays = listOf(384f, 396f, 416f, 450f, 454f, 466f, 480f)

    @Test
    fun `Der Boden beginnt unter den Bahnbloecken`() {
        // Die Grasnarbe der WIESE trägt exakt Zonengrün — sie darf nie
        // auf einem Bahnblock liegen.
        for (d in displays) {
            val radius = d * 0.38f
            val zoneOuter = round(2f * PI.toFloat() * radius / WEAR_TRACK_SEGMENTS * 1.23f)
            val trackOuter = radius + zoneOuter / 2f
            val cell = wearCell(d)
            val top = wearGroundTop(d / 2f, trackOuter, cell)
            assertTrue("d=$d top=$top", top - cell > d / 2f + trackOuter)
            assertTrue("d=$d: Boden liegt noch im Bild", top < d)
        }
    }

    @Test
    fun `Die Kulisse liegt im Pixelraster und ist nie Staub`() {
        for (d in displays) assertTrue(wearCell(d) >= 2f)
        assertEquals(3f, wearCell(480f))
    }

    @Test
    fun `Jede Welt bringt ihren eigenen Nebel mit`() {
        val fogs = SceneId.entries.map { ScenePaint.of(it).fog }
        assertEquals(fogs.size, fogs.toSet().size)
    }

    private fun running(seed: Long, vararg twists: Twist): TimingGame = TimingGame().apply {
        twistOverride = twists.toSet()
        reseed(seed)
        start()
    }

    @Test
    fun `Nebel-Partikel bleiben im Kern und flackern nicht`() {
        val game = running(3L, Twist.GHOST)
        val angles = List(30) { it * 0.02f }
        val a = wearFogSpecks(game, angles, reach = 5f)
        assertEquals(10, a.size)
        a.forEach { assertTrue(it.offset in -5f..5f) }
        assertEquals(a, wearFogSpecks(game, angles, reach = 5f))
        assertTrue(wearFogSpecks(game, emptyList(), 5f).isEmpty())
        assertTrue(wearFogSpecks(game, angles, 0f).isEmpty())
    }

    @Test
    fun `Das Lauflicht der Bomben folgt TrapPaint`() {
        var game: TimingGame? = null
        for (seed in 1L..200L) {
            val g = running(seed, Twist.FAKE)
            if (g.hasFakeZone) { game = g; break }
        }
        val g = game ?: throw AssertionError("Keine Falle gefunden")
        val cell = 2f * PI.toFloat() / WEAR_TRACK_SEGMENTS
        val count = TrapPaint.count(g.zoneHalfWidth, cell)
        var sawRed = false
        repeat(200) {
            g.update(1f / 240f)
            if (g.phase != GamePhase.RUNNING) return@repeat
            val mines = wearTrapMines(g, WEAR_TRACK_SEGMENTS, g.effectiveZoneHalf())
            assertEquals(wearTrapMineAngles(g, WEAR_TRACK_SEGMENTS, g.effectiveZoneHalf()), mines.map { it.angle })
            val mask = TrapPaint.redMask(count, floor(g.zoneAge / TrapPaint.LIGHT_STEP_SECONDS).toInt())
            assertTrue(mines.count { it.red } <= mask.count { it })
            if (mines.any { it.red }) sawRed = true
        }
        assertTrue("Lauflicht nie gesehen", sawRed)
    }

    @Test
    fun `Tod setzt Blitz und Wackeln, beides klingt ab`() {
        val fx = WearFx()
        fx.died()
        assertEquals(1f, fx.flashAlpha)
        assertEquals(0f, fx.deathTime)
        fx.step(0.1f)
        assertEquals(1f - 0.1f * WearFx.FLASH_FADE_PER_SECOND, fx.flashAlpha, 1e-5f)
        assertEquals(0.1f, fx.deathTime, 1e-5f)
        fx.step(1f)
        assertEquals(0f, fx.flashAlpha)
        assertEquals(0f, fx.shakeTime)
        fx.reset()
        assertEquals(-1f, fx.deathTime)
    }

    @Test
    fun `Die Feier laeuft so lang wie am Telefon und endet der Tod`() {
        val fx = WearFx()
        fx.celebrate()
        assertEquals(1.1f, fx.celebrateTime)
        fx.died()
        assertEquals(0f, fx.celebrateTime)
    }
}
