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
    fun `Klassik ist immer frei`() {
        assertTrue(DotSkin.KLASSIK.isUnlocked(stats()))
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
    }

    @Test
    fun `Schatten braucht die Perfekt-Serie, Prisma die Daily-Serie`() {
        assertFalse(DotSkin.SCHATTEN.isUnlocked(stats(best = 99, perfect = 3)))
        assertTrue(DotSkin.SCHATTEN.isUnlocked(stats(perfect = 4)))
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
        assertEquals(1, DotSkin.unlockedCount(stats()))
        assertEquals(DotSkin.entries.size, DotSkin.unlockedCount(stats(best = 40, perfect = 4, daily = 3)))
    }

    @Test
    fun `ohne Tagespass ist verfuegbar dasselbe wie freigeschaltet`() {
        val s = stats(best = 25)
        DotSkin.entries.forEach { skin ->
            assertEquals(skin.isUnlocked(s), skin.isAvailable(s, null))
        }
    }

    @Test
    fun `der Tagespass macht genau einen gesperrten Skin spielbar`() {
        val s = stats()
        assertTrue(DotSkin.LAVA.isAvailable(s, DotSkin.LAVA))
        assertFalse(DotSkin.GOLD.isAvailable(s, DotSkin.LAVA))
        assertTrue(DotSkin.KLASSIK.isAvailable(s, DotSkin.LAVA))
    }

    @Test
    fun `ein Tagespass schaltet nichts dauerhaft frei`() {
        // Die Feier im Game-Over haengt an unlockedCount — ein geliehener
        // Skin darf sie nicht ausloesen.
        val s = stats()
        assertFalse(DotSkin.LAVA.isUnlocked(s))
        assertEquals(1, DotSkin.unlockedCount(s))
        assertTrue(DotSkin.LAVA.isAvailable(s, DotSkin.LAVA))
    }
}
