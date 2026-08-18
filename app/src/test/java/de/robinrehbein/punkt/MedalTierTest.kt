package de.robinrehbein.punkt

import de.robinrehbein.punkt.game.MedalId
import de.robinrehbein.punkt.game.MedalPaint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedalTierTest {

    @Test
    fun `forScore liefert die hoechste erreichte Stufe`() {
        assertNull(MedalPaint.forScore(0))
        assertNull(MedalPaint.forScore(9))
        assertEquals(MedalId.BRONZE, MedalPaint.forScore(10))
        assertEquals(MedalId.BRONZE, MedalPaint.forScore(19))
        assertEquals(MedalId.SILVER, MedalPaint.forScore(20))
        assertEquals(MedalId.GOLD, MedalPaint.forScore(30))
        assertEquals(MedalId.PLATINUM, MedalPaint.forScore(40))
        assertEquals(MedalId.PLATINUM, MedalPaint.forScore(999))
    }

    @Test
    fun `next liefert die naechste offene Stufe und null ab Platin`() {
        assertEquals(MedalId.BRONZE, MedalPaint.next(0))
        assertEquals(MedalId.SILVER, MedalPaint.next(10))
        assertEquals(MedalId.PLATINUM, MedalPaint.next(39))
        assertNull(MedalPaint.next(40))
    }

    @Test
    fun `isUpgrade feiert nur echte Stufen-Aufstiege`() {
        assertTrue(MedalPaint.isUpgrade(score = 10, previousBest = 9))
        assertTrue(MedalPaint.isUpgrade(score = 40, previousBest = 35))
        assertFalse(MedalPaint.isUpgrade(score = 15, previousBest = 12)) // gleiche Stufe
        assertFalse(MedalPaint.isUpgrade(score = 9, previousBest = 0))   // noch keine
        assertFalse(MedalPaint.isUpgrade(score = 12, previousBest = 25)) // schlechter
    }
}
