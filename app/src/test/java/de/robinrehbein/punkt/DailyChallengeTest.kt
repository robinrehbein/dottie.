package de.robinrehbein.punkt

import de.robinrehbein.punkt.game.DailyChallenge
import de.robinrehbein.punkt.game.TimingGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

class DailyChallengeTest {

    @Test
    fun `gleicher Tag ergibt gleichen Seed, andere Tage andere`() {
        val day = 20_675L
        assertEquals(DailyChallenge.seedFor(day), DailyChallenge.seedFor(day))
        assertNotEquals(DailyChallenge.seedFor(day), DailyChallenge.seedFor(day + 1))
        assertNotEquals(DailyChallenge.seedFor(day), DailyChallenge.seedFor(day - 1))
    }

    @Test
    fun `gleicher Seed ergibt dieselbe Zonen-Abfolge`() {
        val seed = DailyChallenge.seedFor(20_675L)
        val a = TimingGame(random = Random(seed)).apply { twistOverride = emptySet() }
        val b = TimingGame(random = Random(seed)).apply { twistOverride = emptySet() }
        a.tap()
        b.tap()
        assertEquals(a.zoneCenter, b.zoneCenter, 0f)
    }

    @Test
    fun `reseed macht einen Lauf deterministisch wiederholbar`() {
        val seed = DailyChallenge.seedFor(20_675L)
        val game = TimingGame(random = Random(seed)).apply { twistOverride = emptySet() }
        game.tap()
        val firstZone = game.zoneCenter

        game.reset()
        game.reseed(seed)
        game.tap()
        assertEquals(firstZone, game.zoneCenter, 0f)
    }

    @Test
    fun `erster Daily-Lauf ueberhaupt startet Serie bei 1`() {
        assertEquals(1, DailyChallenge.nextStreak(0L, 0, 20_675L))
    }

    @Test
    fun `Folgetag zaehlt die Serie hoch`() {
        assertEquals(4, DailyChallenge.nextStreak(20_674L, 3, 20_675L))
    }

    @Test
    fun `gleicher Tag laesst die Serie unveraendert`() {
        assertEquals(3, DailyChallenge.nextStreak(20_675L, 3, 20_675L))
    }

    @Test
    fun `Luecke setzt die Serie auf 1 zurueck`() {
        assertEquals(1, DailyChallenge.nextStreak(20_670L, 9, 20_675L))
    }
}
