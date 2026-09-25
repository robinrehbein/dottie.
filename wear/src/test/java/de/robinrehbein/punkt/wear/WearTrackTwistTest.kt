package de.robinrehbein.punkt.wear

import androidx.compose.ui.geometry.Offset
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.game.TrapPaint
import de.robinrehbein.punkt.game.Twist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Minen und Nebelband auf der Uhr (Plan 8.5 AP-24). Einen Screenshot-Test
 * gibt es für `:wear` nicht; geprüft wird deshalb die Geometrie, aus der
 * WearRenderer zeichnet. Die Zeichnung selbst prüft der Mensch in AP-31.
 */
class WearTrackTwistTest {

    private val frame = 1f / 240f
    private val cell = 2f * PI.toFloat() / WEAR_TRACK_SEGMENTS

    /** Ein Lauf mit festem Twist-Set, gestartet ohne Treffer. */
    private fun running(seed: Long, vararg twists: Twist): TimingGame {
        val game = TimingGame()
        game.twistOverride = twists.toSet()
        game.reseed(seed)
        game.start()
        return game
    }

    /** Erster Seed, dessen erste Zone eine Falle trägt. */
    private fun withTrap(vararg extra: Twist): TimingGame {
        for (seed in 1L..200L) {
            val game = running(seed, Twist.FAKE, *extra)
            if (game.hasFakeZone) return game
        }
        throw AssertionError("Keine Falle gefunden")
    }

    // ===== Minen =====

    @Test
    fun `Minenzahl kommt aus TrapPaint und rundet ab`() {
        val game = withTrap()
        val mines = wearTrapMineAngles(game, WEAR_TRACK_SEGMENTS, game.effectiveZoneHalf())
        val expected = TrapPaint.count(game.zoneHalfWidth, cell)
        assertEquals(expected, (2f * game.zoneHalfWidth / cell).toInt().coerceAtLeast(1))
        assertEquals(expected, mines.size)
    }

    @Test
    fun `Minen liegen in der Fallenbreite aus fakeZoneHalf`() {
        val game = withTrap()
        val half = game.fakeZoneHalf()
        wearTrapMineAngles(game, WEAR_TRACK_SEGMENTS, game.effectiveZoneHalf()).forEach {
            assertTrue(abs(TimingGame.wrapToPi(it - game.fakeZoneCenter)) <= half + 1e-5f)
        }
    }

    @Test
    fun `Minen stehen still, ohne Lauflicht und ohne Zufall`() {
        val game = withTrap()
        val first = wearTrapMineAngles(game, WEAR_TRACK_SEGMENTS, game.effectiveZoneHalf())
        // Über mehrere Lauflicht-Schritte hinweg: gleiche Minen, gleiche Lage.
        repeat(60) { game.update(frame) }
        assertEquals(GamePhase.RUNNING, game.phase)
        assertEquals(first, wearTrapMineAngles(game, WEAR_TRACK_SEGMENTS, game.effectiveZoneHalf()))
    }

    @Test
    fun `Unter PULS bleibt die Minenzahl stehen`() {
        val game = withTrap(Twist.PULSE)
        val count = TrapPaint.count(game.zoneHalfWidth, cell)
        repeat(120) {
            val mines = wearTrapMineAngles(game, WEAR_TRACK_SEGMENTS, game.effectiveZoneHalf())
            assertEquals(count, mines.size)
            game.update(frame)
            if (game.phase != GamePhase.RUNNING) return
        }
    }

    @Test
    fun `Ohne Falle keine Minen`() {
        val game = running(5L)
        assertTrue(wearTrapMineAngles(game, WEAR_TRACK_SEGMENTS, game.effectiveZoneHalf()).isEmpty())
    }

    @Test
    fun `Benachbarte Kugeln beruehren sich nie`() {
        var d = 4f
        while (d < 80f) {
            for (zoneOuter in listOf(8f, 16f, 22f, 30f, 40f)) {
                val px = wearMinePixel(d, zoneOuter)
                assertTrue(px >= 1)
                assertTrue("px=$px d=$d", px == 1 || wearMinesFit(px, d))
                // Nie größer als ein Zonenblock.
                assertTrue(px * TrapPaint.MINE_SIZE <= zoneOuter + TrapPaint.MINE_SIZE / 2f)
            }
            d += 0.5f
        }
    }

    @Test
    fun `Auf ueblichen Uhren sind die Minen gross genug`() {
        // 384 px (Pixel Watch) und 454 px (Galaxy Watch), Bahnradius 0,38·d.
        // Ohne PULS: Unter PULS rückt die Kette im Wellental auf 62 %
        // zusammen, dann bleiben die Minen bei 1 px je Sprite-Pixel, damit
        // sich die Kugeln nie berühren (siehe oben).
        for (display in listOf(384f, 454f)) {
            val game = withTrap()
            val radius = display * 0.38f
            val spacing = 2f * PI.toFloat() * radius / WEAR_TRACK_SEGMENTS
            val zoneOuter = kotlin.math.round(spacing * 1.23f)
            val px = wearMinePixel(wearMineDistance(game, WEAR_TRACK_SEGMENTS, radius), zoneOuter)
            assertTrue("display=$display px=$px", px >= 2)
        }
    }

    @Test
    fun `Kugeln ueberdecken sich auch im Bild nicht`() {
        val game = withTrap(Twist.PULSE)
        val radius = 454f * 0.38f
        val spacing = 2f * PI.toFloat() * radius / WEAR_TRACK_SEGMENTS
        val zoneOuter = kotlin.math.round(spacing * 1.23f)
        val px = wearMinePixel(wearMineDistance(game, WEAR_TRACK_SEGMENTS, radius), zoneOuter)
        repeat(200) {
            val centers = wearTrapMineAngles(game, WEAR_TRACK_SEGMENTS, game.effectiveZoneHalf())
                .map { Offset(cos(it) * radius, sin(it) * radius) }
            for (i in 1 until centers.size) {
                val a = centers[i - 1]
                val b = centers[i]
                val dist = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
                assertTrue("dist=$dist px=$px", px == 1 || wearMinesFit(px, dist + 1e-3f))
            }
            game.update(frame)
            if (game.phase != GamePhase.RUNNING) return
        }
    }

    @Test
    fun `Sandbloecke unter einer Mine bleiben frei`() {
        val mine = listOf(Offset(100f, 100f))
        assertTrue(wearBlockHitsMine(105f, 100f, 5f, mine, wearMineHalfExtent(2)))
        assertFalse(wearBlockHitsMine(140f, 100f, 5f, mine, wearMineHalfExtent(2)))
    }

    // ===== Nebelband =====

    private fun maxGap(angles: List<Float>): Float =
        angles.zipWithNext { a, b -> abs(TimingGame.wrapToPi(b - a)) }.maxOrNull() ?: 0f

    /** Liegt [angle] höchstens [tolerance] neben einem Bandblock? */
    private fun covered(angles: List<Float>, angle: Float, tolerance: Float): Boolean =
        angles.any { abs(TimingGame.wrapToPi(it - angle)) <= tolerance + 1e-5f }

    @Test
    fun `Nebelband reicht von fogStart bis fogEnd`() {
        val game = running(11L, Twist.GHOST)
        val step = 0.05f
        val angles = wearFogAngles(game, step)
        assertTrue(angles.size >= 2)
        assertTrue("Band ohne Lücken", maxGap(angles) <= step + 1e-5f)
        val start = game.fogStart()
        val end = game.fogEnd()
        for (rel in listOf(start, (start + end) / 2f, end)) {
            val angle = TimingGame.wrapTwoPi(game.zoneCenter + game.direction * rel)
            assertTrue("rel=$rel nicht bedeckt", covered(angles, angle, step / 2f))
        }
        // Nicht über die Zone hinaus: Das Band endet im ersten Viertel.
        angles.forEach {
            val rel = TimingGame.wrapToPi(game.direction * (it - game.zoneCenter))
            assertTrue(rel >= start - 1e-4f && rel <= end + 1e-4f)
        }
    }

    @Test
    fun `Im Nebelband ist der Vogel unsichtbar, am Anfang, in der Mitte und am Ende`() {
        val game = running(11L, Twist.GHOST)
        val step = 0.02f
        var inFog = 0
        var nearStart = false
        var nearMid = false
        var nearEnd = false
        repeat(20_000) {
            if (game.phase != GamePhase.RUNNING) return@repeat
            val rel = game.relativeToZone()
            val start = game.fogStart()
            val end = game.fogEnd()
            if (rel >= start && rel <= end) {
                inFog++
                assertFalse("rel=$rel sichtbar", game.isDotVisible)
                val angles = wearFogAngles(game, step)
                assertTrue("rel=$rel ohne Band", covered(angles, game.angle, step / 2f))
                val len = end - start
                if (rel - start < len * 0.1f) nearStart = true
                if (abs(rel - (start + end) / 2f) < len * 0.1f) nearMid = true
                if (end - rel < len * 0.1f) nearEnd = true
            }
            game.update(frame)
        }
        assertTrue("Der Vogel ist nie durch den Nebel gelaufen", inFog > 5)
        assertTrue(nearStart && nearMid && nearEnd)
    }

    @Test
    fun `Kein Nebelband ohne NEBEL, in READY und nach dem Tod`() {
        assertTrue(wearFogAngles(running(11L), 0.05f).isEmpty())
        val ready = TimingGame().apply { twistOverride = setOf(Twist.GHOST) }
        assertTrue(wearFogAngles(ready, 0.05f).isEmpty())
        val game = running(11L, Twist.GHOST)
        repeat(20_000) {
            if (game.phase == GamePhase.RUNNING) game.update(frame)
        }
        assertTrue(game.phase == GamePhase.DYING || game.phase == GamePhase.OVER)
        assertTrue(wearFogAngles(game, 0.05f).isEmpty())
    }
}
