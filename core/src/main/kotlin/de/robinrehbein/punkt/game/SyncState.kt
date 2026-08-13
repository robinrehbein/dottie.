package de.robinrehbein.punkt.game

/**
 * Der Spielstand, den sich Telefon und Uhr teilen — als reine Daten, ohne
 * Android, damit die Zusammenführung als Unit-Test prüfbar bleibt.
 *
 * Beide Geräte spielen unabhängig weiter, auch ohne Verbindung. Wenn sie
 * sich wiedersehen, gibt es keinen "Server", der recht hat: Jede Seite
 * bekommt den Stand der anderen und führt ihn mit ihrem eigenen zusammen.
 * Damit dabei nichts verloren geht und beide garantiert beim selben
 * Ergebnis landen, ist [mergedWith] bewusst
 *
 *  - **kommutativ** (a.mergedWith(b) == b.mergedWith(a)) und
 *  - **idempotent** (a.mergedWith(a) == a).
 *
 * Ohne diese beiden Eigenschaften könnten sich zwei Geräte gegenseitig
 * endlos neue Stände schicken, oder das Ergebnis hinge davon ab, wer
 * zuerst online war.
 */
data class SyncState(
    val bestScore: Int = 0,
    val runCount: Int = 0,
    val bestPerfectStreak: Int = 0,
    /** Tag des letzten Daily-Laufs als Epoch-Day, 0 = noch nie gespielt. */
    val dailyDay: Long = 0L,
    val dailyBest: Int = 0,
    val dailyStreak: Int = 0,
    /** Name des gewählten Skins; leer = nie bewusst gewählt. */
    val skin: String = "",
    /**
     * Wann der Skin zuletzt bewusst gewechselt wurde (Millisekunden seit
     * 1970). Der einzige Wert, bei dem "neuer gewinnt" gilt statt "größer
     * gewinnt" — eine Skin-Wahl ist eine Entscheidung, kein Rekord.
     */
    val skinChangedAt: Long = 0L
) {

    /**
     * Führt zwei Stände zu dem zusammen, den beide Geräte danach tragen.
     *
     * Bestleistungen nehmen jeweils den höheren Wert — ein Rekord, der
     * einmal existiert hat, darf durch das Zusammenführen nie
     * verschwinden. Das gilt auch für die Anzahl der Läufe: Sie ist keine
     * echte Summe (die ließe sich ohne Verlaufswissen nicht bilden), aber
     * das Maximum ist die einzige Wahl, die beim wiederholten
     * Zusammenführen stabil bleibt.
     */
    fun mergedWith(other: SyncState): SyncState {
        val daily = mergeDaily(other)
        // Gleichstand beim Zeitstempel: Der alphabetisch kleinere Name
        // gewinnt. Willkürlich, aber auf beiden Geräten dieselbe Regel —
        // sonst behielte jede Seite ihren eigenen Skin und die beiden
        // würden sich gegenseitig ewig überschreiben.
        val skinFromOther = when {
            other.skinChangedAt > skinChangedAt -> true
            other.skinChangedAt < skinChangedAt -> false
            else -> other.skin < skin
        }
        return SyncState(
            bestScore = maxOf(bestScore, other.bestScore),
            runCount = maxOf(runCount, other.runCount),
            bestPerfectStreak = maxOf(bestPerfectStreak, other.bestPerfectStreak),
            dailyDay = daily.dailyDay,
            dailyBest = daily.dailyBest,
            dailyStreak = daily.dailyStreak,
            skin = if (skinFromOther) other.skin else skin,
            skinChangedAt = maxOf(skinChangedAt, other.skinChangedAt)
        )
    }

    /**
     * Der Daily-Teil ist der einzige, der nicht einfach das Maximum
     * nehmen darf: Die Serie beschreibt eine Kette von Tagen, und die
     * kann über beide Geräte verteilt entstanden sein.
     *
     * Drei Fälle:
     *  - **Gleicher Tag**: beide zählen denselben Tag, also das jeweils
     *    Bessere nehmen.
     *  - **Aufeinanderfolgende Tage**: Wer gestern auf der Uhr und heute
     *    am Telefon gespielt hat, hat die Serie fortgesetzt — auch wenn
     *    das Telefon für sich genommen bei 1 stand, weil es von gestern
     *    nichts wusste. Die Serie des jüngeren Tages ist deshalb
     *    mindestens die von gestern plus eins.
     *  - **Lücke**: Ein Tag ohne Lauf reißt die Serie. Dann zählt allein
     *    der jüngere Tag mit dem, was dort steht.
     */
    private fun mergeDaily(other: SyncState): SyncState {
        if (dailyDay == 0L) return other
        if (other.dailyDay == 0L) return this
        if (dailyDay == other.dailyDay) {
            return copy(
                dailyBest = maxOf(dailyBest, other.dailyBest),
                dailyStreak = maxOf(dailyStreak, other.dailyStreak)
            )
        }
        val newer = if (dailyDay > other.dailyDay) this else other
        val older = if (dailyDay > other.dailyDay) other else this
        if (newer.dailyDay != older.dailyDay + 1) return newer
        return newer.copy(dailyStreak = maxOf(newer.dailyStreak, older.dailyStreak + 1))
    }
}
