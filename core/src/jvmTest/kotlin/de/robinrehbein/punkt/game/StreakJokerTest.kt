package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Serien-Joker (ab v2.30): Ein Joker stopft genau einen verpassten Tag.
 * Reicht der Vorrat nicht für die ganze Lücke, reißt die Serie wie
 * bisher — und die Joker bleiben liegen.
 */
class StreakJokerTest {

    @Test
    fun `ohne Joker bleibt alles beim Alten`() {
        for (last in listOf(0L, 10L)) for (today in 10L..14L) for (streak in 0..5) {
            assertEquals(
                DailyChallenge.nextStreak(last, streak, today),
                DailyChallenge.nextStreak(last, streak, today, jokers = 0).streak
            )
        }
    }

    @Test
    fun `gestern gespielt kostet keinen Joker`() {
        val step = DailyChallenge.nextStreak(10L, 4, 11L, jokers = 2)
        assertEquals(DailyChallenge.StreakStep(5, 0), step)
    }

    @Test
    fun `ein verpasster Tag kostet einen Joker und die Serie laeuft weiter`() {
        assertEquals(DailyChallenge.StreakStep(5, 1), DailyChallenge.nextStreak(10L, 4, 12L, jokers = 1))
    }

    @Test
    fun `zwei verpasste Tage brauchen zwei Joker`() {
        assertEquals(DailyChallenge.StreakStep(5, 2), DailyChallenge.nextStreak(10L, 4, 13L, jokers = 2))
        // Einer reicht nicht: Die Serie reißt, der Joker bleibt liegen.
        assertEquals(DailyChallenge.StreakStep(1, 0), DailyChallenge.nextStreak(10L, 4, 13L, jokers = 1))
    }

    @Test
    fun `derselbe Tag aendert nichts und kostet nichts`() {
        assertEquals(DailyChallenge.StreakStep(4, 0), DailyChallenge.nextStreak(10L, 4, 10L, jokers = 2))
    }

    @Test
    fun `lebt die Serie`() {
        assertTrue(DailyChallenge.isStreakAlive(10L, 10L, 0))
        assertTrue(DailyChallenge.isStreakAlive(10L, 11L, 0))
        assertFalse(DailyChallenge.isStreakAlive(10L, 12L, 0))
        assertTrue(DailyChallenge.isStreakAlive(10L, 12L, 1))
        assertFalse(DailyChallenge.isStreakAlive(10L, 13L, 1))
        assertFalse(DailyChallenge.isStreakAlive(0L, 13L, 2))
    }

    @Test
    fun `gerettet werden kann, wenn genau ein Joker fehlt`() {
        // Gestern verpasst, kein Joker: ein Spot rettet.
        assertTrue(DailyChallenge.isRescuable(10L, 5, 12L, jokers = 0))
        // Zwei verpasst, einer da: ein Spot rettet.
        assertTrue(DailyChallenge.isRescuable(10L, 5, 13L, jokers = 1))
        // Zwei verpasst, keiner da: ein Spot reicht nicht.
        assertFalse(DailyChallenge.isRescuable(10L, 5, 13L, jokers = 0))
        // Drei verpasst: mehr als der Vorrat je fassen kann.
        assertFalse(DailyChallenge.isRescuable(10L, 5, 14L, jokers = 2))
        // Nichts verpasst: nichts zu retten.
        assertFalse(DailyChallenge.isRescuable(10L, 5, 11L, jokers = 0))
        // Eine Serie von einem Tag ist keinen Spot wert.
        assertFalse(DailyChallenge.isRescuable(10L, 1, 12L, jokers = 0))
    }
}
