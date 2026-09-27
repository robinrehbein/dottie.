package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.geometry.Size
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.game.Twist
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Bahn liegt im Raster der halben Zelle ([trackUnit]): Kanten aller
 * Sand- und Zonenblöcke auf Vielfachen der Stufe, jeder Umriss genau eine
 * Stufe breit. Geprüft an [sandBlock] und [zoneSlots], die [drawTrack]
 * und [drawZoneBlock] selbst benutzen.
 */
class TrackGridTest {

    private class Screen(val name: String, val cx: Float, val cy: Float, val radius: Float, val cell: Float)

    private fun screen(w: Int, h: Int): Screen {
        val (cx, cy, radius) = ringGeometry(Size(w.toFloat(), h.toFloat()))
        return Screen("${w}x$h", cx, cy, radius, floor(h / 220f).coerceAtLeast(2f))
    }

    private val screens = listOf(screen(1080, 2340), screen(720, 1280))

    private fun onGrid(v: Float, unit: Int): Boolean {
        val r = v / unit
        return abs(r - kotlin.math.round(r)) < 1e-4f
    }

    private fun assertBlock(b: TrackBlock, unit: Int, x: Float, y: Float, what: String) {
        assertEquals(unit.toFloat(), b.unit, "Stufe ($what)")
        assertTrue(onGrid(b.left, unit) && onGrid(b.top, unit), "Ecke ${b.left}/${b.top} nicht im Raster $unit ($what)")
        assertTrue(onGrid(b.outer, unit), "Kante ${b.outer} nicht im Raster $unit ($what)")
        assertTrue(onGrid(b.left + b.outer, unit) && onGrid(b.top + b.outer, unit), "Außenkante ($what)")
        // Umriss genau eine Stufe: Fläche beginnt eine Stufe innen und endet eine Stufe vor der Kante.
        assertEquals(b.left + unit, b.faceLeft, "Umriss links ($what)")
        assertEquals(b.top + unit, b.faceTop, "Umriss oben ($what)")
        assertEquals(b.left + b.outer - unit, b.faceLeft + b.inner, "Umriss rechts ($what)")
        assertEquals(b.top + b.outer - unit, b.faceTop + b.inner, "Umriss unten ($what)")
        assertTrue(b.inner >= 2f * unit, "Fläche mindestens zwei Stufen ($what)")
        // Die Mitte bleibt höchstens eine halbe Stufe neben der Bahn.
        assertTrue(abs(b.centerX - x) <= unit / 2f + 1e-3f, "Mitte x ${b.centerX} statt $x ($what)")
        assertTrue(abs(b.centerY - y) <= unit / 2f + 1e-3f, "Mitte y ${b.centerY} statt $y ($what)")
    }

    @Test
    fun `die Rasterstufe ist eine halbe Zelle`() {
        assertEquals(5, trackUnit(screens[0].cell), "1080x2340")
        assertEquals(3, trackUnit(screens[1].cell), "720x1280")
        assertEquals(1, trackUnit(2f))
    }

    @Test
    fun `Sandbloecke sind drei Zellen gross und liegen im Raster`() {
        for (s in screens) {
            val unit = trackUnit(s.cell)
            for (k in 0 until TRACK_SEGMENTS) {
                val a = trackSlotAngle(k, TRACK_SEGMENTS)
                val x = s.cx + cos(a) * s.radius
                val y = s.cy + sin(a) * s.radius
                val b = sandBlock(x, y, s.cell)
                assertEquals(3f * s.cell, b.outer, "Außenmaß 3 Zellen (${s.name})")
                assertEquals(3f * s.cell - 2f * unit, b.inner, "Sandfläche (${s.name})")
                assertBlock(b, unit, x, y, "Sand $k, ${s.name}")
            }
        }
        val phone = sandBlock(0f, 0f, 10f)
        assertEquals(30f, phone.outer)
        assertEquals(20f, phone.inner)
    }

    @Test
    fun `Zonenbloecke liegen im Raster, auch unter PULS`() {
        for (s in screens) {
            val unit = trackUnit(s.cell)
            val sizes = HashSet<Float>()
            var checked = 0
            for (twists in listOf(emptySet(), setOf(Twist.PULSE), setOf(Twist.FAKE, Twist.PULSE))) {
                for (seed in 1L..8L) {
                    val game = TimingGame(Random(seed)).apply { twistOverride = twists }
                    game.start()
                    var frame = 0
                    while (game.phase == GamePhase.RUNNING && frame < 3000) {
                        if (frame % 7 == 0) {
                            val slots = zoneSlots(game, s.cx, s.cy, s.radius, s.cell)
                            assertTrue(slots.isNotEmpty(), "Zone ohne Blöcke (${s.name})")
                            for (z in slots) {
                                // Die Bahnposition: Mitte des gerundeten Blocks liegt höchstens eine halbe Stufe daneben.
                                val a = nearestSlotAngle(z.block.centerX - s.cx, z.block.centerY - s.cy)
                                val x = s.cx + cos(a) * s.radius
                                val y = s.cy + sin(a) * s.radius
                                assertBlock(z.block, unit, x, y, "Zone, ${s.name}, Seed $seed")
                                assertTrue(z.block.outer >= 4f * unit, "mindestens vier Stufen")
                                assertTrue(z.block.outer <= 5f * s.cell + unit / 2f, "höchstens 5 Zellen")
                                sizes += z.block.outer
                                checked++
                            }
                            // Kleine Blöcke zuerst: die größeren liegen oben.
                            assertEquals(slots.sortedBy { it.scale }.map { it.scale }, slots.map { it.scale })
                        }
                        game.update(1f / 60f)
                        if (game.isInZone && abs(game.relativeToZone()) < game.perfectHalf() * 0.5f) game.tap()
                        frame++
                    }
                }
            }
            assertTrue(checked > 1000, "genug Blöcke geprüft: $checked (${s.name})")
            // Der Verlauf zur Mitte bleibt sichtbar: mehrere Stufen kommen vor.
            assertTrue(sizes.size >= 3, "Blockgrößen $sizes (${s.name})")
        }
    }

    /** Der Winkel des Bahnplatzes, der der Richtung ([dx], [dy]) am nächsten liegt. */
    private fun nearestSlotAngle(dx: Float, dy: Float): Float {
        val a = kotlin.math.atan2(dy, dx)
        val slot = 2f * kotlin.math.PI.toFloat() / TRACK_SEGMENTS
        return kotlin.math.round(a / slot) * slot
    }
}
