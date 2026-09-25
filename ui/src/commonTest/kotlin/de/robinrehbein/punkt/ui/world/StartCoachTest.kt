package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import de.robinrehbein.punkt.game.GameEventNotYet
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.TimingGame
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Der Startbildschirm als Tutorial (Plan 3.1, 8.5 AP-22): Startregel aus
 * Sicht des Bildschirms, Lage von Hinweis und „NOCH NICHT“, Hand, Echos
 * und Stützräder.
 */
class StartCoachTest {

    /** Lässt den Punkt in READY laufen, bis [inZone] gilt. */
    private fun TimingGame.runReadyUntil(inZone: Boolean) {
        var frames = 0
        while (isInZone != inZone && frames++ < 1_000) update(1f / 120f)
        assertEquals(inZone, isInZone, "Punkt erreicht den gewünschten Stand nicht")
    }

    // ===== Startregel =====

    @Test
    fun `ein Tap daneben lässt Score, Phase und Zone unverändert`() {
        val game = TimingGame(Random(7))
        game.update(0.2f) // Punkt steht vor dem Grün
        assertFalse(game.isInZone)
        val zone = game.zoneCenter
        val half = game.zoneHalfWidth

        val event = game.tap()

        assertEquals(GameEventNotYet, event)
        assertEquals(0, game.score)
        assertEquals(GamePhase.READY, game.phase)
        assertEquals(zone, game.zoneCenter)
        assertEquals(half, game.zoneHalfWidth)
        // Und der Punkt kreist weiter.
        val angle = game.angle
        game.update(0.1f)
        assertTrue(game.angle != angle)
    }

    @Test
    fun `der erste Tap im Grün ergibt Score 1 oder 2`() {
        for (seed in 1L..40L) {
            val game = TimingGame(Random(seed))
            game.runReadyUntil(inZone = true)
            // Mal am Rand, mal mitten drin.
            game.update((seed % 5) * 0.05f)
            if (!game.isInZone) continue

            game.tap()

            assertEquals(GamePhase.RUNNING, game.phase)
            assertTrue(game.score == 1 || game.score == 2, "Score ${game.score} bei Seed $seed")
        }
    }

    // ===== Hand und Echos =====

    @Test
    fun `die Hand drückt genau im Grün und startet dabei ihr Echo`() {
        val game = TimingGame(Random(3))
        val fx = FxState()
        advanceStartCoach(fx, game, 0f)
        assertFalse(fx.handPressed)
        assertTrue(fx.handEchoTime < 0f)

        while (!game.isInZone) {
            game.update(1f / 60f)
            advanceStartCoach(fx, game, 1f / 60f)
        }
        assertTrue(fx.handPressed)
        assertEquals(0f, fx.handEchoTime)

        advanceStartCoach(fx, game, HAND_ECHO_SECONDS + 0.01f)
        assertTrue(fx.handEchoTime < 0f, "das Echo endet nach einer halben Sekunde")
    }

    @Test
    fun `die Hand drückt nur in READY`() {
        val game = TimingGame(Random(3))
        val fx = FxState()
        game.runReadyUntil(inZone = true)
        game.tap()
        game.update(0f)
        advanceStartCoach(fx, game, 1f / 60f)
        assertFalse(fx.handPressed)
    }

    @Test
    fun `Tipp-Echos wachsen 0,45 s lang und verschwinden dann`() {
        val game = TimingGame(Random(1))
        val fx = FxState()
        fx.addTapEcho(Offset(10f, 20f))
        advanceStartCoach(fx, game, 0.3f)
        assertEquals(1, fx.tapEchoes.size)
        advanceStartCoach(fx, game, 0.2f)
        assertTrue(fx.tapEchoes.isEmpty())
    }

    @Test
    fun `es stehen nie mehr als acht Echos gleichzeitig`() {
        val fx = FxState()
        repeat(20) { fx.addTapEcho(Offset(it.toFloat(), 0f)) }
        assertEquals(8, fx.tapEchoes.size)
        assertEquals(19f, fx.tapEchoes.last().x)
    }

    @Test
    fun `NOCH NICHT steht 0,7 s und wackelt dabei`() {
        val game = TimingGame(Random(1))
        val fx = FxState()
        fx.notYetTime = NOT_YET_SECONDS
        assertTrue(notYetWobble(fx.notYetTime, 1f) != 0f)
        advanceStartCoach(fx, game, 0.69f)
        assertTrue(fx.notYetTime > 0f)
        advanceStartCoach(fx, game, 0.02f)
        assertEquals(0f, fx.notYetTime)
        assertEquals(0f, notYetWobble(fx.notYetTime, 1f))
    }

    @Test
    fun `Stützräder für die ersten fünf Läufe`() {
        assertTrue(startCoachActive(0))
        assertTrue(startCoachActive(4))
        assertFalse(startCoachActive(5))
        assertFalse(startCoachActive(500))
    }

    @Test
    fun `die Hand hat 16 mal 17 Pixel`() {
        assertEquals(17, HAND.size)
        HAND.forEach { assertEquals(HAND_COLUMNS, it.length) }
    }

    // ===== Geometrie =====

    /** Die Formate aus dem Plan in dp, dazu dieselben in Pixeln typischer Dichten. */
    private val formats = listOf(
        Size(360f, 640f),
        Size(411f, 914f),
        Size(720f, 1280f),
        Size(1080f, 1920f),
        Size(1080f, 2400f),
        Size(411f * 2.625f, 914f * 2.625f)
    )

    @Test
    fun `der Hinweis überlappt Ring und Zone nicht`() {
        for (size in formats) {
            val hint = readyHintArea(size)
            val ring = ringExtent(size)
            assertFalse(hint.overlaps(ring), "Hinweis $hint überlappt Ring $ring bei $size")
            assertTrue(hint.top > ring.bottom, "Hinweis steht unter dem Ring bei $size")
            assertTrue(hint.left >= 0f && hint.right <= size.width, "Hinweis im Bild bei $size")
        }
    }

    @Test
    fun `der Hinweis endet über der Taster-Leiste`() {
        // Die Leiste ist 64 dp hoch; in dp gerechnet ist 1 px = 1 dp.
        for (size in listOf(Size(360f, 640f), Size(411f, 914f))) {
            val hint = readyHintArea(size)
            assertTrue(hint.bottom <= size.height - 64f, "Hinweis ragt in die Leiste bei $size")
        }
    }

    @Test
    fun `NOCH NICHT steht im Ring über der Hand`() {
        for (size in formats) {
            val ring = ringGeometry(size)
            val center = notYetCenter(size)
            val handTop = ring.cy - handUnit(ring.radius)
            // Die Schrift ist gut 22 dp hoch; ein Zehntel des Radius reicht
            // auf allen Formaten als halbe Zeilenhöhe.
            assertTrue(center.y + ring.radius * 0.12f < handTop, "NOCH NICHT auf der Hand bei $size")
            assertTrue(center.y - ring.radius * 0.12f > ring.cy - ring.radius, "NOCH NICHT über dem Ring bei $size")
            assertEquals(ring.cx, center.x)
        }
    }
}
