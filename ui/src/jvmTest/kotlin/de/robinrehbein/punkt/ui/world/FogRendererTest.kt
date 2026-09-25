package de.robinrehbein.punkt.ui.world

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.game.Twist
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap

/**
 * Die Nebelbank (Plan 3.3, AP-23): Der Vogel ist im Nebel nicht zu sehen,
 * gleitet an der Wolke hinein und heraus, und die Wolkenform hängt nicht
 * an der wandernden Zone.
 *
 * „Unsichtbar“ wird am Bild geprüft: Dieselbe Szene zweimal gezeichnet,
 * einmal mit dem gelben KLASSIK-Vogel und einmal mit dem grünen MINZE-
 * Vogel. Sind beide Bilder gleich, ist vom Vogel kein Pixel zu sehen.
 */
class FogRendererTest {

    private data class Format(val width: Int, val height: Int)

    private val formats = listOf(Format(1080, 2400), Format(720, 1280))

    @Test
    fun `punkt ist bei fogStart in der Mitte und bei fogEnd unsichtbar`() {
        listOf(0, 15, 40).forEach { hits ->
            formats.forEach { format ->
                listOf("fogStart" to 0f, "Mitte" to 0.5f, "fogEnd" to 1f).forEach { (name, share) ->
                    val game = fogGame(hits)
                    runToFogShare(game, share)
                    assertTrue(game.isInFog, "$name bei $hits Treffern liegt nicht im Nebel")
                    assertEquals(
                        0,
                        birdPixels(game, format),
                        "Vogel bei $name sichtbar ($hits Treffer, ${format.width}×${format.height})"
                    )
                }
            }
        }
    }

    @Test
    fun `vogel gleitet in die wolke hinein`() {
        listOf(0, 15, 40).forEach { hits ->
            formats.forEach { format ->
                val unitAngle = unitAngle(format)
                // Ganz: derselbe Lauf ohne Nebel. Halb drin: Die Mitte des
                // Vogels steht acht Wolkenpixel vor der ersten Bausche.
                val frei = fogGame(hits, emptySet())
                val halb = fogGame(hits).also { runToRel(it, it.fogStart() - 8f * unitAngle) }
                // Kurz vor dem Nebel ist der Vogel noch sichtbar geschaltet,
                // steckt aber schon ganz in der Wolke.
                val vorher = fogGame(hits).also { runToRel(it, it.fogStart() - 0.004f) }
                assertTrue(vorher.isDotVisible && !vorher.isInFog, "Vogel sollte noch sichtbar geschaltet sein")

                val ganz = birdPixels(frei, format)
                val teil = birdPixels(halb, format)
                assertTrue(ganz > 0, "Vogel frei vor der Wolke nicht zu sehen")
                assertTrue(teil in 1 until ganz, "Vogel gleitet nicht hinein: $teil von $ganz Pixeln")
                assertEquals(
                    0,
                    birdPixels(vorher, format),
                    "Vogel ragt am Nebelanfang aus der Wolke ($hits Treffer, ${format.width}×${format.height}, " +
                        "zoneAge=${vorher.zoneAge})"
                )
            }
        }
    }

    @Test
    fun `vogel gleitet aus der wolke heraus`() {
        listOf(0, 15, 40).forEach { hits ->
            formats.forEach { format ->
                val unitAngle = unitAngle(format)
                val frei = fogGame(hits, emptySet())
                val raus = fogGame(hits).also { runToRel(it, it.fogEnd() + 3f * unitAngle) }
                check(raus.phase == GamePhase.RUNNING)
                val ganz = birdPixels(frei, format)
                val teil = birdPixels(raus, format)
                assertTrue(teil in 1 until ganz, "Vogel gleitet nicht heraus: $teil von $ganz Pixeln")
            }
        }
    }

    @Test
    fun `seed ist unter DRIFT ueber 60 frames konstant`() {
        val game = fogGame(3, setOf(Twist.GHOST, Twist.DRIFT))
        val seed = fogSeed(game)
        val unitAngle = unitAngle(formats.first())
        val balls = fogBalls(seed, game.fogStart(), game.fogEnd(), unitAngle)
        val zoneBefore = game.zoneCenter
        repeat(60) { frame ->
            game.update(1f / 60f)
            check(game.phase == GamePhase.RUNNING) { "Lauf endet in Frame $frame" }
            assertEquals(seed, fogSeed(game), "Seed wechselt in Frame $frame")
            assertEquals(
                balls,
                fogBalls(fogSeed(game), game.fogStart(), game.fogEnd(), unitAngle),
                "Wolkenform wechselt in Frame $frame"
            )
        }
        assertTrue(game.zoneCenter != zoneBefore, "Zone ist unter DRIFT nicht gewandert")
    }

    @Test
    fun `jede zone bekommt eine eigene form`() {
        val game = fogGame(0)
        val seeds = mutableSetOf(fogSeed(game))
        repeat(5) {
            check(playTo(game, game.hits + 1))
            seeds += fogSeed(game)
        }
        assertEquals(6, seeds.size, "Seeds wiederholen sich: $seeds")
    }

    @Test
    fun `bauschen sind acht bis zwoelf wolkenpixel gross und luekenlos`() {
        val unitAngle = unitAngle(formats.first())
        listOf(0, 15, 40).forEach { hits ->
            val game = fogGame(hits)
            val balls = fogBalls(fogSeed(game), game.fogStart(), game.fogEnd(), unitAngle)
            assertTrue(balls.size >= 2)
            balls.forEach { assertTrue(it.radius in 8f..12f, "Bauschradius ${it.radius}") }
            assertEquals(game.fogStart(), balls.first().rel, 1e-6f, "Erste Bausche nicht am Nebelanfang")
            // Auf der Bahn endet die Wolke mit dem Nebel (höchstens ein Wolkenpixel darüber).
            val end = balls.last().rel + balls.last().radius * unitAngle
            assertTrue(end <= game.fogEnd() + unitAngle, "Wolke quillt über das Nebelende")
            balls.zipWithNext().forEach { (a, b) ->
                assertTrue(b.rel - a.rel <= FOG_BALL_SPACING * unitAngle + 1e-5f, "Lücke in der Wolke")
            }
        }
    }

    @Test
    fun `wolke nur in RUNNING unter NEBEL`() {
        val format = formats.first()
        val ready = TimingGame(Random(SEED)).apply { twistOverride = setOf(Twist.GHOST) }
        ready.update(0.1f)
        assertEquals(0, fogPixels(ready, format), "Wolke in READY")

        val running = fogGame(5)
        repeat(48) { running.update(1f / 240f) }
        check(running.phase == GamePhase.RUNNING)
        assertTrue(fogPixels(running, format) > 0, "Keine Wolke in RUNNING")

        val ohne = fogGame(5, emptySet())
        assertEquals(0, fogPixels(ohne, format), "Wolke ohne NEBEL")

        // Tod: sofort daneben tippen. Die Wolke ist weg.
        running.tap()
        running.update(1f / 60f)
        assertEquals(GamePhase.DYING, running.phase)
        assertEquals(0, fogPixels(running, format), "Wolke beim Tod")
    }

    @Test
    fun `wolke rollt ein`() {
        val format = formats.first()
        val game = fogGame(5)
        // fogGame steht direkt nach dem Treffer: zoneAge ist fast 0.
        val frueh = fogPixels(game, format, anyFogColor = true)
        while (game.zoneAge < 0.05f) game.update(1f / 240f)
        val mitte = fogPixels(game, format, anyFogColor = true)
        while (game.zoneAge < FOG_ROLL_IN_SECONDS) game.update(1f / 240f)
        val voll = fogPixels(game, format, anyFogColor = true)
        assertTrue(frueh < mitte && mitte < voll, "Kein Einrollen: $frueh, $mitte, $voll")
    }

    @Test
    fun `woelkchen beim ein- und austritt`() {
        val game = fogGame(15)
        val fx = FxState()
        var inSeen = false
        var outSeen = false
        var frames = 0
        val zone = game.hits
        while (game.hits == zone && game.relativeToZone() < game.fogEnd() + 0.05f) {
            game.update(1f / 240f)
            trackFog(fx, game, 1f / 240f)
            if (fx.fogInTime == 0f) {
                inSeen = true
                assertTrue(game.isInFog, "Eintritts-Wölkchen außerhalb des Nebels")
                assertEquals(game.angle, fx.fogInAngle)
            }
            if (fx.fogOutTime == 0f) {
                outSeen = true
                assertTrue(!game.isInFog, "Austritts-Wölkchen im Nebel")
            }
            check(++frames < 100_000)
        }
        assertTrue(inSeen, "Kein Wölkchen beim Eintritt")
        assertTrue(outSeen, "Kein Wölkchen beim Austritt")

        // Nach 0,35 s sind beide weg.
        repeat((FOG_PUFF_SECONDS * 240).toInt() + 2) { trackFog(fx, game, 1f / 240f) }
        assertTrue(fx.fogInTime < 0f && fx.fogOutTime < 0f, "Wölkchen bleiben stehen")

        // Außerhalb von RUNNING ist nichts zu sehen, und nach dem Neustart
        // gibt es kein Wölkchen aus dem alten Lauf.
        fx.fogInTime = 0.1f
        game.tap() // trifft noch die Zone
        if (game.phase == GamePhase.RUNNING) game.tap() // die nächste ist weit weg: ZU FRÜH
        game.update(1f / 60f)
        check(game.phase == GamePhase.DYING)
        trackFog(fx, game, 1f / 60f)
        assertTrue(fx.fogInTime < 0f && fx.fogWasIn == -1)
    }

    @Test
    fun `ohne NEBEL keine woelkchen`() {
        val game = fogGame(15, emptySet())
        val fx = FxState()
        var frames = 0
        while (game.relativeToZone() < game.fogEnd() + 0.05f) {
            game.update(1f / 240f)
            trackFog(fx, game, 1f / 240f)
            assertTrue(fx.fogInTime < 0f && fx.fogOutTime < 0f, "Wölkchen ohne NEBEL")
            check(++frames < 100_000)
        }
    }

    @Test
    fun `blind pop nur nach gewertetem blindtreffer`() {
        val blind = fogGame(15)
        blindHit(blind)
        assertTrue(blind.lastHitBlind)
        blind.update(1f / 60f)
        assertTrue(isBlindPopShown(blind), "Kein Pop nach Blindtreffer")
        while (blind.timeSinceHit < BLIND_POP_SECONDS) blind.update(1f / 60f)
        assertTrue(!isBlindPopShown(blind), "Pop bleibt stehen")

        val aus = fogGame(15).apply { blindBonus = false }
        blindHit(aus)
        aus.update(1f / 60f)
        assertTrue(!isBlindPopShown(aus), "Pop ohne BLIND-Bonus")

        val normal = fogGame(15)
        check(playTo(normal, 16))
        normal.update(1f / 60f)
        assertTrue(!isBlindPopShown(normal), "Pop nach normalem Treffer")
    }

    // == AP-31 release ==
    @Test
    fun `blind pop sagt plus eins und steht am ring statt ueber der landschaft`() {
        val blind = fogGame(15)
        val vorher = blind.score
        blindHit(blind)
        // Der Pop nennt den Bonus (+1, Plan 8.6 #1), nicht den ganzen Treffer (+2).
        assertEquals(2, blind.score - vorher, "Blindtreffer zählt nicht 1 + 1")
        assertEquals(1, de.robinrehbein.punkt.ui.screens.blindBonusPoints(blind))

        formats.forEach { format ->
            val w = format.width.toFloat()
            val h = format.height.toFloat()
            val ring = ringGeometry(androidx.compose.ui.geometry.Size(w, h))
            listOf(0f, 0.5f, 1f, 1.5f).forEach { turns ->
                val angle = turns * Math.PI.toFloat()
                val box = popPixels(format, angle)
                    ?: error("Pop nicht gezeichnet (${format.width}×${format.height}, $turns π)")
                val where = "(${format.width}×${format.height}, Winkel $turns π, $box)"
                // Im Quadrat des Rings, also nicht unten auf Bäumen und Boden
                // (dort, bei 74 % der Höhe, stand er vorher).
                assertTrue(box.bottom <= ring.cy + ring.radius, "Pop unter dem Ring $where")
                assertTrue(box.top >= ring.cy - ring.radius, "Pop über dem Ring $where")
                assertTrue(box.left >= ring.cx - ring.radius && box.right <= ring.cx + ring.radius, "Pop neben dem Ring $where")
                assertTrue(box.bottom < h * 0.74f, "Pop auf der Höhe von PERFEKT $where")
            }
        }
    }

    /** Umriss der Pixel, die der Pop allein auf durchsichtigem Grund setzt. */
    private fun popPixels(format: Format, angle: Float): androidx.compose.ui.geometry.Rect? {
        val bitmap = ImageComposeScene(format.width, format.height, Density(format.width / 411f)) {
            de.robinrehbein.punkt.ui.screens.BlindPop(bonus = 1, angle = angle)
        }.use { scene -> Bitmap.makeFromImage(scene.render(0L)) }
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in 0 until format.height) for (x in 0 until format.width) {
            if ((bitmap.getColor(x, y) ushr 24) != 0) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < 0) return null
        return androidx.compose.ui.geometry.Rect(left.toFloat(), top.toFloat(), right + 1f, bottom + 1f)
    }
    // == /AP-31 ==

    // ---------- Helfer ----------

    /** Ein Lauf unter [twists], [hits] Treffer weit, direkt nach dem letzten Treffer. */
    private fun fogGame(hits: Int, twists: Set<Twist> = setOf(Twist.GHOST)): TimingGame {
        val game = TimingGame(Random(SEED)).apply { twistOverride = twists }
        check(playTo(game, maxOf(hits, 1))) { "Bot ist vor $hits Treffern gestorben" }
        return game
    }

    /** Tippt im PERFEKT-Kern, bis [hits] Treffer erreicht sind (wie TwistShots). */
    private fun playTo(game: TimingGame, hits: Int): Boolean {
        var frames = 0
        while (game.hits < hits) {
            game.update(1f / 240f)
            if (game.phase == GamePhase.DYING || game.phase == GamePhase.OVER) return false
            val rel = game.relativeToZone()
            val core = game.perfectHalf() * 0.5f
            if (rel >= -core && rel <= core) game.tap()
            check(++frames < 2_000_000) { "Bot kommt nicht voran" }
        }
        return true
    }

    /** Tippt, sobald der Punkt in der Zone, aber noch im Nebel ist. */
    private fun blindHit(game: TimingGame) {
        val zone = game.hits
        var frames = 0
        while (game.hits == zone) {
            game.update(1f / 1000f)
            check(game.phase == GamePhase.RUNNING)
            if (game.isInZone && game.isInFog) game.tap()
            check(++frames < 1_000_000)
        }
    }

    /** Lässt den Punkt in feinen Schritten bis [rel] (relativ zur Zone) laufen. */
    private fun runToRel(game: TimingGame, rel: Float) {
        var frames = 0
        while (game.relativeToZone() < rel) {
            game.update(1f / 4000f)
            check(game.phase == GamePhase.RUNNING) { "Lauf endet vor rel=$rel" }
            check(++frames < 1_000_000)
        }
    }

    /** Bis zum Anteil [share] der Nebelbank: 0 = erster Frame im Nebel, 1 = letzter. */
    private fun runToFogShare(game: TimingGame, share: Float) {
        val dt = 1f / 4000f
        if (share >= 1f) {
            // Letzter Frame im Nebel: Der nächste Schritt würde ihn verlassen.
            while (game.relativeToZone() + game.currentSpeed() * dt <= game.fogEnd()) {
                game.update(dt)
                check(game.phase == GamePhase.RUNNING)
            }
            return
        }
        val target = game.fogStart() + (game.fogEnd() - game.fogStart()) * share
        runToRel(game, target)
    }

    private fun unitAngle(format: Format): Float {
        val ring = ringGeometry(androidx.compose.ui.geometry.Size(format.width.toFloat(), format.height.toFloat()))
        return fogUnit(format.height.toFloat()) / ring.radius
    }

    private fun render(game: TimingGame, skin: SkinId, format: Format): Bitmap =
        ImageComposeScene(width = format.width, height = format.height, density = Density(2f)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTimingWorld(game, FxState(), skin, SceneId.WIESE, hour = 12, month = 6)
            }
        }.use { scene -> Bitmap.makeFromImage(scene.render(0L)) }

    /** Wie viele Pixel sich ändern, wenn nur die Farbe des Vogels wechselt. */
    private fun birdPixels(game: TimingGame, format: Format): Int {
        val a = render(game, SkinId.KLASSIK, format).readPixels()!!
        val b = render(game, SkinId.MINZE, format).readPixels()!!
        var diff = 0
        for (i in a.indices step 4) {
            if (a[i] != b[i] || a[i + 1] != b[i + 1] || a[i + 2] != b[i + 2]) diff++
        }
        return diff
    }

    /** Pixel in der Farbe der Wolkenunterkante (oder in irgendeiner Wolkenfarbe). */
    private fun fogPixels(game: TimingGame, format: Format, anyFogColor: Boolean = false): Int {
        val colors = if (anyFogColor) {
            setOf(FogBottom, FogLow, FogMid, FogInner).map { it.toArgb() }.toSet()
        } else {
            setOf(FogBottom.toArgb())
        }
        val bitmap = render(game, SkinId.KLASSIK, format)
        var count = 0
        for (y in 0 until format.height) for (x in 0 until format.width) {
            if (bitmap.getColor(x, y) in colors) count++
        }
        return count
    }

    private companion object {
        const val SEED = 20260925L
    }
}
