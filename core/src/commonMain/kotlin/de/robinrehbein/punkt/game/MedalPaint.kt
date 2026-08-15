package de.robinrehbein.punkt.game

/**
 * Medaillen-Stufen — die einzige Quelle für Schwellen, Rangfolge und
 * Münzfarben in Kotlin. Nach demselben Muster wie [SkinPaint]: :app und
 * :wear halten nur noch ihre eigenen Aufzählungen mit den lokalen
 * String-Ressourcen und fragen hier nach Zahlen und Farben.
 *
 * Vorher lagen Schwellen und Farben doppelt im Repo (MedalTier.kt und
 * medalColors() in :app, WearMedalTier.kt in :wear) — vier Zahlen und
 * acht Farbwerte, die niemand gleichzeitig pflegt.
 *
 * Die Reihenfolge der Einträge ist gleichzeitig die Rangfolge (ordinal);
 * daran hängt die "NEUE MEDAILLE!"-Feier im Game-Over.
 *
 * Farben sind ARGB-Longs (0xAARRGGBB), damit das Modul frei von Compose-
 * und Android-Typen bleibt.
 */
enum class MedalId { BRONZE, SILVER, GOLD, PLATINUM }

object MedalPaint {

    /** Ab welchem Score die Stufe erreicht ist. */
    /** Die Medaillen von der niedrigsten zur höchsten Schwelle. */
    val ORDER: List<MedalId> = MedalId.entries.toList()

    fun threshold(id: MedalId): Int = when (id) {
        MedalId.BRONZE -> 10
        MedalId.SILVER -> 20
        MedalId.GOLD -> 30
        MedalId.PLATINUM -> 40
    }

    /** Körperfarbe der Münze. */
    fun body(id: MedalId): Long = when (id) {
        MedalId.BRONZE -> 0xFFCD7F32
        MedalId.SILVER -> 0xFFC0C0C0
        MedalId.GOLD -> 0xFFFFD700
        MedalId.PLATINUM -> 0xFFE5E4E2
    }

    /** Schattenfarbe der Münze (auch die Farbe der Prägung). */
    fun shade(id: MedalId): Long = when (id) {
        MedalId.BRONZE -> 0xFF9C5A1E
        MedalId.SILVER -> 0xFF8F8F9C
        MedalId.GOLD -> 0xFFC9A400
        MedalId.PLATINUM -> 0xFFADB5C4
    }

    /** Höchste erreichte Stufe, null unterhalb von Bronze. */
    fun forScore(score: Int): MedalId? =
        MedalId.entries.lastOrNull { score >= threshold(it) }

    /** Nächste noch nicht erreichte Stufe, null ab Platin. */
    fun next(score: Int): MedalId? =
        MedalId.entries.firstOrNull { score < threshold(it) }

    /** Bringt dieser Score eine höhere Stufe als der bisherige Bestwert? */
    fun isUpgrade(score: Int, previousBest: Int): Boolean =
        (forScore(score)?.ordinal ?: -1) > (forScore(previousBest)?.ordinal ?: -1)
}
