package de.robinrehbein.punkt.wear

import de.robinrehbein.punkt.game.Season
import java.time.LocalDate

/**
 * Die Ausdauer-Buchführung der Uhr als reine Rechnung: Läufe,
 * Punktesumme, Tage, Monats-Maske und Saison-Fortschritt.
 *
 * Bewusst ohne Android — [WearGameController] lädt die Werte aus den
 * Prefs, lässt hier rechnen und schreibt das Ergebnis zurück. Damit ist
 * die Buchführung als Unit-Test prüfbar, genau wie SyncState in :core.
 * Die Regeln sind unverändert die von writeSeasonProgress/submitRun im
 * Phone-Store — nur eben ohne SharedPreferences dazwischen.
 */
internal data class WearProgress(
    val runCount: Int = 0,
    val totalScore: Int = 0,
    val daysPlayed: Int = 0,
    /** Zuletzt gezählter Kalendertag; MIN_VALUE = noch nie gespielt. */
    val lastPlayedDay: Long = Long.MIN_VALUE,
    /** 12-Bit-Maske (Bit 0 = Januar): Derselbe Monat zählt nur einmal. */
    val monthsPlayed: Int = 0,
    /** Dauerhafte Maske der verdienten Saison-Skins (Season.bit). */
    val seasonEarned: Int = 0,
    /** Laufendes Saison-Fenster als Jahr*100+Monat, 0 = keins. */
    val seasonWindow: Int = 0,
    /** Tage mit Lauf im laufenden Fenster. */
    val seasonDays: Int = 0,
    /** Letzter für die Saison gezählte Tag; MIN_VALUE = noch keiner. */
    val seasonLastDay: Long = Long.MIN_VALUE
) {

    /**
     * Ein beendeter Lauf. [date] ist der Tag, dem der Lauf zugerechnet
     * ist — der, an dem er GESTARTET wurde (der Controller fixiert ihn in
     * prepareRun), nicht "jetzt": Ein Lauf über Mitternacht darf nicht in
     * zwei Tagestöpfe fallen.
     */
    fun afterRun(score: Int, date: LocalDate): WearProgress {
        val epochDay = date.toEpochDay()
        // Der letzte Tag steht als Marke daneben, damit mehrere Läufe an
        // einem Tag nur einen Tag zählen.
        val newDay = lastPlayedDay != epochDay
        return copy(
            runCount = runCount + 1,
            totalScore = totalScore + score,
            daysPlayed = if (newDay) daysPlayed + 1 else daysPlayed,
            lastPlayedDay = if (newDay) epochDay else lastPlayedDay,
            monthsPlayed = monthsPlayed or (1 shl (date.monthValue - 1))
        ).withSeasonProgress(date, epochDay)
    }

    /**
     * Saison-Fortschritt: Tage mit mindestens einem Lauf im aktiven
     * Saison-Monat. Ist die Marke erreicht, wandert das Bit in die
     * dauerhafte Maske und bleibt dort — der Kalender allein würde den
     * Kürbis im November wieder wegnehmen.
     */
    private fun withSeasonProgress(date: LocalDate, epochDay: Long): WearProgress {
        val season = Season.forMonth(date.monthValue) ?: return this
        // Verdient ist verdient: Steht das Bit, ist am Fortschritt nichts
        // mehr zu rechnen.
        if (seasonEarned and season.bit != 0) return this
        // Fenster-Schlüssel: Derselbe Monat im nächsten Jahr fängt bei
        // null an, sonst ließe sich der Skin über Jahre zusammenstückeln.
        val window = date.year * 100 + date.monthValue
        val freshWindow = seasonWindow != window
        if (!freshWindow && seasonLastDay == epochDay) return this
        val days = if (freshWindow) 1 else seasonDays + 1
        return copy(
            seasonWindow = window,
            seasonDays = days,
            seasonLastDay = epochDay,
            seasonEarned = if (days >= season.requiredDays) {
                seasonEarned or season.bit
            } else {
                seasonEarned
            }
        )
    }
}
