package de.robinrehbein.punkt

import de.robinrehbein.punkt.notify.DailyReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Wann welche Erinnerung kommt (ab v2.30): 18 Uhr immer, solange die
 * Daily offen ist; 21 Uhr nur, wenn eine echte Serie auf dem Spiel steht.
 */
class DailyReminderDecisionTest {

    @Test
    fun `heute schon gespielt heisst nie erinnern`() {
        assertNull(DailyReminder.decide("daily", playedToday = true, streakAlive = 9))
        assertNull(DailyReminder.decide("risk", playedToday = true, streakAlive = 9))
    }

    @Test
    fun `um 18 Uhr immer, auch ohne Serie`() {
        assertEquals("daily", DailyReminder.decide("daily", playedToday = false, streakAlive = 0))
        assertEquals("daily", DailyReminder.decide("daily", playedToday = false, streakAlive = 5))
    }

    @Test
    fun `um 21 Uhr nur fuer eine Serie ab drei Tagen`() {
        assertNull(DailyReminder.decide("risk", playedToday = false, streakAlive = 0))
        assertNull(DailyReminder.decide("risk", playedToday = false, streakAlive = DailyReminder.RISK_MIN_STREAK - 1))
        assertEquals("risk", DailyReminder.decide("risk", playedToday = false, streakAlive = DailyReminder.RISK_MIN_STREAK))
    }
}
