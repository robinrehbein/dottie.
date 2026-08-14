package de.robinrehbein.punkt.wear

import de.robinrehbein.punkt.game.Season
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Die Ausdauer-Buchführung der Uhr (WearProgress). Sie trägt alle Skins,
 * die nicht am Rekord hängen — geht hier etwas verloren, merkt es niemand
 * sofort, aber der Skin kommt nie.
 */
class WearProgressTest {

    private val oct = { day: Int -> LocalDate.of(2025, 10, day) }

    @Test
    fun `Jeder Lauf zaehlt Laeufe und Gesamtpunkte`() {
        val after = WearProgress()
            .afterRun(7, oct(1))
            .afterRun(5, oct(1))
        assertEquals(2, after.runCount)
        assertEquals(12, after.totalScore)
    }

    @Test
    fun `Ein Nuller-Lauf zaehlt trotzdem als Lauf`() {
        val after = WearProgress().afterRun(0, oct(1))
        assertEquals(1, after.runCount)
        assertEquals(0, after.totalScore)
    }

    @Test
    fun `Mehrere Laeufe an einem Tag zaehlen nur einen Tag`() {
        val after = WearProgress()
            .afterRun(3, oct(1))
            .afterRun(9, oct(1))
            .afterRun(4, oct(1))
        assertEquals(1, after.daysPlayed)
        assertEquals(oct(1).toEpochDay(), after.lastPlayedDay)
    }

    @Test
    fun `Ein neuer Kalendertag zaehlt dazu`() {
        val after = WearProgress()
            .afterRun(3, oct(1))
            .afterRun(3, oct(2))
        assertEquals(2, after.daysPlayed)
    }

    @Test
    fun `Der Monat steht als Bit, derselbe Monat zaehlt nur einmal`() {
        val after = WearProgress()
            .afterRun(1, LocalDate.of(2025, 3, 4))
            .afterRun(1, LocalDate.of(2025, 3, 28))
            .afterRun(1, LocalDate.of(2026, 3, 2))
            .afterRun(1, LocalDate.of(2026, 5, 2))
        // Bit 0 = Januar, also Maerz = Bit 2 und Mai = Bit 4.
        assertEquals((1 shl 2) or (1 shl 4), after.monthsPlayed)
        assertEquals(2, Integer.bitCount(after.monthsPlayed))
    }

    /**
     * Der Kern der Tag-Fixierung: Der Lauf bringt seinen Tag mit (in
     * prepareRun beim Start festgelegt). Ein Lauf, der um 23:59 beginnt
     * und nach Mitternacht endet, gehoert deshalb ganz dem Starttag —
     * sonst zaehlte eine Nacht als zwei Tage.
     */
    @Test
    fun `Ein Lauf ueber Mitternacht faellt in genau einen Tagestopf`() {
        val start = LocalDate.of(2025, 3, 31)
        val ende = start.plusDays(1)
        val after = WearProgress()
            .afterRun(5, start)
            // Zweiter Lauf desselben Abends, ebenfalls ueber Mitternacht.
            .afterRun(8, start)
        assertEquals(1, after.daysPlayed)
        assertEquals(2, after.runCount)
        assertEquals(start.toEpochDay(), after.lastPlayedDay)
        // Nur Maerz, nicht April — der Endtag spielt keine Rolle.
        assertEquals(1 shl 2, after.monthsPlayed)
        assertNotEquals(ende.toEpochDay(), after.lastPlayedDay)
    }

    // ===== Saison =====

    @Test
    fun `Saison-Skin nach genug Tagen im Fenster`() {
        val kuerbis = Season.KUERBIS
        var p = WearProgress()
        for (day in 1 until kuerbis.requiredDays) {
            p = p.afterRun(1, oct(day))
            assertEquals("nach $day Tagen noch nicht verdient", 0, p.seasonEarned)
        }
        p = p.afterRun(1, oct(kuerbis.requiredDays))
        assertEquals(kuerbis.bit, p.seasonEarned and kuerbis.bit)
    }

    @Test
    fun `Mehrere Laeufe an einem Tag bringen den Saison-Zaehler nur einmal weiter`() {
        var p = WearProgress()
        repeat(10) { p = p.afterRun(1, oct(1)) }
        assertEquals(1, p.seasonDays)
        assertEquals(0, p.seasonEarned)
    }

    @Test
    fun `Ausserhalb des Saison-Monats gibt es keinen Fortschritt`() {
        val p = WearProgress().afterRun(1, LocalDate.of(2025, 11, 3))
        assertEquals(0, p.seasonWindow)
        assertEquals(0, p.seasonDays)
        assertEquals(0, p.seasonEarned)
    }

    @Test
    fun `Das Fenster faengt im naechsten Jahr von vorn an`() {
        // Vier Tage im Oktober 2025 — einer zu wenig fuer den Kuerbis.
        var p = WearProgress()
        for (day in 1..4) p = p.afterRun(1, oct(day))
        assertEquals(4, p.seasonDays)
        // Ein Tag im Oktober 2026 stueckelt den Rest NICHT zusammen.
        p = p.afterRun(1, LocalDate.of(2026, 10, 1))
        assertEquals(1, p.seasonDays)
        assertEquals(2026 * 100 + 10, p.seasonWindow)
        assertEquals(0, p.seasonEarned)
    }

    @Test
    fun `Eine verdiente Saison-Maske wird nie zurueckgenommen`() {
        var p = WearProgress()
        for (day in 1..Season.KUERBIS.requiredDays) p = p.afterRun(1, oct(day))
        val earned = p.seasonEarned
        assertEquals(Season.KUERBIS.bit, earned)
        // Weiterspielen im November, im naechsten Oktober, im Februar:
        // Der Kuerbis bleibt in jedem Fall.
        p = p.afterRun(1, LocalDate.of(2025, 11, 2))
        p = p.afterRun(1, LocalDate.of(2026, 10, 9))
        p = p.afterRun(1, LocalDate.of(2027, 2, 1))
        assertEquals(earned, p.seasonEarned and earned)
    }

    @Test
    fun `Ein zweiter Saison-Skin kommt neben den ersten`() {
        var p = WearProgress()
        for (day in 1..Season.KUERBIS.requiredDays) p = p.afterRun(1, oct(day))
        for (day in 1..Season.HERZ.requiredDays) {
            p = p.afterRun(1, LocalDate.of(2026, 2, day))
        }
        assertEquals(Season.KUERBIS.bit or Season.HERZ.bit, p.seasonEarned)
    }
}
