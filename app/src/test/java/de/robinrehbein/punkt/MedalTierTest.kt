package de.robinrehbein.punkt

import de.robinrehbein.punkt.game.MedalTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedalTierTest {

    @Test
    fun `forScore liefert die hoechste erreichte Stufe`() {
        assertNull(MedalTier.forScore(0))
        assertNull(MedalTier.forScore(9))
        assertEquals(MedalTier.BRONZE, MedalTier.forScore(10))
        assertEquals(MedalTier.BRONZE, MedalTier.forScore(19))
        assertEquals(MedalTier.SILVER, MedalTier.forScore(20))
        assertEquals(MedalTier.GOLD, MedalTier.forScore(30))
        assertEquals(MedalTier.PLATINUM, MedalTier.forScore(40))
        assertEquals(MedalTier.PLATINUM, MedalTier.forScore(999))
    }

    @Test
    fun `next liefert die naechste offene Stufe und null ab Platin`() {
        assertEquals(MedalTier.BRONZE, MedalTier.next(0))
        assertEquals(MedalTier.SILVER, MedalTier.next(10))
        assertEquals(MedalTier.PLATINUM, MedalTier.next(39))
        assertNull(MedalTier.next(40))
    }

    @Test
    fun `isUpgrade feiert nur echte Stufen-Aufstiege`() {
        assertTrue(MedalTier.isUpgrade(score = 10, previousBest = 9))
        assertTrue(MedalTier.isUpgrade(score = 40, previousBest = 35))
        assertFalse(MedalTier.isUpgrade(score = 15, previousBest = 12)) // gleiche Stufe
        assertFalse(MedalTier.isUpgrade(score = 9, previousBest = 0))   // noch keine
        assertFalse(MedalTier.isUpgrade(score = 12, previousBest = 25)) // schlechter
    }
}
