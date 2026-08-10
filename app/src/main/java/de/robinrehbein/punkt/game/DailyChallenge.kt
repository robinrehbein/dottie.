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
     */
    fun nextStreak(lastPlayedEpochDay: Long, currentStreak: Int, todayEpochDay: Long): Int =
        when {
            lastPlayedEpochDay <= 0L -> 1
            todayEpochDay == lastPlayedEpochDay -> currentStreak.coerceAtLeast(1)
            todayEpochDay == lastPlayedEpochDay + 1 -> currentStreak.coerceAtLeast(0) + 1
            else -> 1
        }
}
