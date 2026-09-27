package de.robinrehbein.punkt.ui.screens

import de.robinrehbein.punkt.game.DeathCause
import de.robinrehbein.punkt.game.GamePhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Die reinen Entscheidungen hinter der Wiederkehr-Oberfläche (ab v2.36). */
class RetentionTest {

    @Test
    fun `der Abstand steht nur bei ZU FRUEH und ZU SPAET`() {
        assertEquals("03", marginHundredths(DeathCause.EARLY, 0.031f))
        assertEquals("21", marginHundredths(DeathCause.LATE, 0.21f))
        assertNull(marginHundredths(DeathCause.MISSED, 0.03f))
        assertNull(marginHundredths(DeathCause.TRAP, 0.03f))
        assertNull(marginHundredths(DeathCause.NONE, 0.03f))
    }

    @Test
    fun `nie null Hundertstel und nichts ueber einer halben Sekunde`() {
        assertEquals("01", marginHundredths(DeathCause.EARLY, 0.001f))
        assertEquals("50", marginHundredths(DeathCause.EARLY, 0.5f))
        assertNull(marginHundredths(DeathCause.EARLY, 0.51f))
        assertNull(marginHundredths(DeathCause.EARLY, 0f))
    }

    @Test
    fun `der Rekord-Zaehler kommt nur kurz davor`() {
        assertNull(recordGap(bestScore = 8, score = 7), "unter Rekord 10 nie")
        assertNull(recordGap(bestScore = 30, score = 20))
        assertEquals(3, recordGap(bestScore = 30, score = 27))
        assertEquals(1, recordGap(bestScore = 30, score = 29))
        assertNull(recordGap(bestScore = 30, score = 30), "Gleichstand ist noch kein Rekord — aber auch kein Zähler mehr")
        assertNull(recordGap(bestScore = 30, score = 31))
    }

    private fun frage(
        reminderEnabled: Boolean = false,
        reminderAsked: Boolean = false,
        dailyPlayedEver: Boolean = true,
        analyticsAnswered: Boolean = false,
        runCount: Int = 20,
        dismissed: Set<Prompt> = emptySet(),
        reminderSupported: Boolean = true,
        analyticsSupported: Boolean = true
    ) = nextPrompt(
        reminderSupported = reminderSupported,
        reminderEnabled = reminderEnabled,
        reminderAsked = reminderAsked,
        dailyPlayedEver = dailyPlayedEver,
        analyticsSupported = analyticsSupported,
        analyticsAnswered = analyticsAnswered,
        runCount = runCount,
        dismissed = dismissed
    )

    @Test
    fun `in den ersten Laeufen wird nichts gefragt`() {
        assertNull(frage(runCount = PROMPT_MIN_RUNS - 1))
    }

    @Test
    fun `die Erinnerung kommt nach der ersten Daily und nur einmal`() {
        assertEquals(Prompt.REMINDER, frage())
        assertEquals(Prompt.ANALYTICS, frage(dailyPlayedEver = false))
        assertEquals(Prompt.ANALYTICS, frage(reminderAsked = true))
        assertEquals(Prompt.ANALYTICS, frage(reminderEnabled = true))
        assertEquals(Prompt.ANALYTICS, frage(reminderSupported = false))
    }

    @Test
    fun `beantwortet oder weggeklickt heisst nicht noch einmal`() {
        assertNull(frage(reminderAsked = true, analyticsAnswered = true))
        assertNull(frage(dismissed = setOf(Prompt.REMINDER, Prompt.ANALYTICS)))
        assertEquals(Prompt.ANALYTICS, frage(dismissed = setOf(Prompt.REMINDER)))
        assertNull(frage(reminderAsked = true, analyticsSupported = false))
    }

    @Test
    fun `die Frage-Karte schliesst mit der Zurueck-Geste`() {
        assertEquals(
            BackAction.CLOSE_PROMPT,
            backAction(
                showHelp = false,
                showSettings = false,
                showStats = false,
                showSkins = false,
                showDailyIntro = false,
                phase = GamePhase.READY,
                showPrompt = true
            )
        )
    }
}
