package de.robinrehbein.punkt.game

/**
 * Pure Logik der Daily Challenge: Ein Kalendertag bestimmt einen festen
 * Zufalls-Seed — alle Spieler:innen (und alle Versuche des Tages) bekommen
 * dieselbe Zonen- und Twist-Abfolge. Das macht den Tages-Score vergleichbar
 * ("Schlag meine 23 von heute!") und die Challenge lernbar.
 *
 * Tage werden als Epoch-Day (java.time.LocalDate.toEpochDay) gehandhabt,
 * damit die Logik ohne Android-Abhängigkeiten testbar bleibt.
 */
object DailyChallenge {

    /**
     * Seed für einen Kalendertag. Der Epoch-Day wird mit einer großen
     * Primzahl gespreizt, damit aufeinanderfolgende Tage nicht fast
     * identische Seeds bekommen (kotlin.random streut kleine Seeds schwach).
     */
    fun seedFor(epochDay: Long): Long = epochDay * 0x9E3779B97F4A7C15UL.toLong()

    /**
     * Fortschreibung der Tages-Serie beim ersten Daily-Lauf eines Tages:
     * direkt aufeinanderfolgende Tage zählen hoch, derselbe Tag ändert
     * nichts, eine Lücke setzt auf 1 zurück. `lastPlayedEpochDay <= 0`
     * heißt: noch nie gespielt.
     *
     * Die Regel ohne Joker — die Uhr kennt keine und rechnet weiter so.
     */
    fun nextStreak(lastPlayedEpochDay: Long, currentStreak: Int, todayEpochDay: Long): Int =
        nextStreak(lastPlayedEpochDay, currentStreak, todayEpochDay, jokers = 0).streak

    // ===== Serien-Joker (ab v2.36) =====

    /**
     * Höchstens so viele Joker liegen auf Vorrat. Zwei decken ein
     * verpasstes Wochenende; mehr würde die Serie zu einer Zahl machen,
     * die man ansparen kann, statt zu einer, die man jeden Tag verdient.
     */
    const val MAX_JOKERS = 2

    /**
     * Ergebnis der Fortschreibung: die neue Serie und wie viele Joker
     * dafür verbraucht wurden.
     */
    data class StreakStep(val streak: Int, val jokersUsed: Int)

    /** Verpasste Tage zwischen dem letzten Daily-Tag und heute (0 = gestern gespielt). */
    fun missedDays(lastPlayedEpochDay: Long, todayEpochDay: Long): Int =
        if (lastPlayedEpochDay <= 0L || todayEpochDay <= lastPlayedEpochDay) 0
        else (todayEpochDay - lastPlayedEpochDay - 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /**
     * Die Serie mit Jokern: Jeder verpasste Tag kostet einen Joker. Reichen
     * sie für die ganze Lücke, läuft die Serie weiter, als wäre nichts
     * gewesen (+1 für heute). Reichen sie nicht, reißt sie wie bisher auf
     * 1 — und die Joker bleiben liegen. Ein Joker, der eine Lücke nur zur
     * Hälfte stopft, wäre verbrannt, ohne etwas gerettet zu haben.
     */
    fun nextStreak(
        lastPlayedEpochDay: Long,
        currentStreak: Int,
        todayEpochDay: Long,
        jokers: Int
    ): StreakStep {
        if (lastPlayedEpochDay <= 0L) return StreakStep(1, 0)
        if (todayEpochDay == lastPlayedEpochDay) return StreakStep(currentStreak.coerceAtLeast(1), 0)
        if (todayEpochDay < lastPlayedEpochDay) return StreakStep(1, 0)
        val missed = missedDays(lastPlayedEpochDay, todayEpochDay)
        return when {
            missed == 0 -> StreakStep(currentStreak.coerceAtLeast(0) + 1, 0)
            missed <= jokers.coerceAtLeast(0) ->
                StreakStep(currentStreak.coerceAtLeast(0) + 1, missed)
            else -> StreakStep(1, 0)
        }
    }

    /**
     * Lebt die Serie heute noch — also würde ein Daily-Lauf sie
     * fortschreiben statt sie auf 1 zu setzen? Gestern gespielt, heute
     * schon gespielt oder eine Lücke, die die Joker decken.
     */
    fun isStreakAlive(lastPlayedEpochDay: Long, todayEpochDay: Long, jokers: Int): Boolean =
        lastPlayedEpochDay > 0L && todayEpochDay >= lastPlayedEpochDay &&
            missedDays(lastPlayedEpochDay, todayEpochDay) <= jokers.coerceAtLeast(0)

    /**
     * Kann ein einziger zusätzlicher Joker (per Spot) die Serie heute noch
     * retten? Genau dann, wenn genau ein Joker fehlt und der Vorrat ihn
     * aufnehmen kann. Serien unter [RESCUE_MIN_STREAK] Tagen bekommen das
     * Angebot nicht: Einen Tag zu retten ist keinen Spot wert.
     */
    fun isRescuable(
        lastPlayedEpochDay: Long,
        currentStreak: Int,
        todayEpochDay: Long,
        jokers: Int
    ): Boolean {
        if (lastPlayedEpochDay <= 0L || currentStreak < RESCUE_MIN_STREAK) return false
        val missed = missedDays(lastPlayedEpochDay, todayEpochDay)
        return missed >= 1 && missed == jokers + 1 && missed <= MAX_JOKERS
    }

    /** Ab dieser Serie wird ein Rettungs-Spot angeboten. */
    const val RESCUE_MIN_STREAK = 2
}
