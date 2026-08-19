package de.robinrehbein.punkt

import de.robinrehbein.punkt.game.DotSkin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DotSkinTest {

    private fun stats(best: Int = 0, perfect: Int = 0, daily: Int = 0) =
        DotSkin.Stats(bestScore = best, bestPerfectStreak = perfect, bestDailyStreak = daily)

    @Test
    fun `Klassik, Matcha und Toffifee sind immer frei`() {
        assertTrue(DotSkin.KLASSIK.isUnlocked(stats()))
        assertTrue(DotSkin.MATCHA.isUnlocked(stats()))
        assertTrue(DotSkin.TOFFIFEE.isUnlocked(stats()))
    }

    @Test
    fun `Medaillen-Skins schalten an den Medaillen-Schwellen frei`() {
        assertFalse(DotSkin.MINZE.isUnlocked(stats(best = 9)))
        assertTrue(DotSkin.MINZE.isUnlocked(stats(best = 10)))
        assertFalse(DotSkin.LAVA.isUnlocked(stats(best = 19)))
        assertTrue(DotSkin.LAVA.isUnlocked(stats(best = 20)))
        assertFalse(DotSkin.GOLD.isUnlocked(stats(best = 29)))
        assertTrue(DotSkin.GOLD.isUnlocked(stats(best = 30)))
        assertFalse(DotSkin.FROST.isUnlocked(stats(best = 39)))
        assertTrue(DotSkin.FROST.isUnlocked(stats(best = 40)))
        assertFalse(DotSkin.BASKETBALL.isUnlocked(stats(best = 49)))
        assertTrue(DotSkin.BASKETBALL.isUnlocked(stats(best = 50)))
    }

    @Test
    fun `Schatten und Tennisball brauchen die Perfekt-Serie, Prisma die Daily-Serie`() {
        assertFalse(DotSkin.SCHATTEN.isUnlocked(stats(best = 99, perfect = 3)))
        assertTrue(DotSkin.SCHATTEN.isUnlocked(stats(perfect = 4)))
        assertFalse(DotSkin.TENNISBALL.isUnlocked(stats(best = 99, perfect = 5)))
        assertTrue(DotSkin.TENNISBALL.isUnlocked(stats(perfect = 6)))
        assertFalse(DotSkin.PRISMA.isUnlocked(stats(best = 99, daily = 2)))
        assertTrue(DotSkin.PRISMA.isUnlocked(stats(daily = 3)))
    }

    @Test
    fun `unbekannter gespeicherter Name faellt auf Klassik zurueck`() {
        assertEquals(DotSkin.KLASSIK, DotSkin.fromName(null))
        assertEquals(DotSkin.KLASSIK, DotSkin.fromName("GIBTS_NICHT"))
        assertEquals(DotSkin.LAVA, DotSkin.fromName("LAVA"))
    }

    @Test
    fun `unlockedCount zaehlt ueber alle Bedingungen`() {
        // Drei Skins sind von Anfang an frei: Klassik, Matcha, Toffifee.
        assertEquals(3, DotSkin.unlockedCount(stats()))
        assertEquals(DotSkin.entries.size, DotSkin.unlockedCount(stats(best = 50, perfect = 6, daily = 3)))
    }
}
