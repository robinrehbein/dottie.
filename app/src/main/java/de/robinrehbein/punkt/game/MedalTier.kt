package de.robinrehbein.punkt.game

import androidx.annotation.StringRes
import de.robinrehbein.punkt.R

/**
 * Medaillen-Stufen ab 10/20/30/40 Punkten. Die Reihenfolge der Einträge
 * ist gleichzeitig die Rangfolge (ordinal) — daran hängt die
 * "NEUE MEDAILLE!"-Feier im Game-Over.
 *
 * Schwellen und Farben stehen nicht mehr hier, sondern in [MedalPaint]
 * im :core-Modul — dieselbe Quelle nutzt auch die Uhr. Diese Aufzählung
 * verbindet sie nur noch mit den String-Ressourcen der App.
 */
enum class MedalTier(val id: MedalId, @StringRes val nameRes: Int) {
    BRONZE(MedalId.BRONZE, R.string.medal_bronze),
    SILVER(MedalId.SILVER, R.string.medal_silver),
    GOLD(MedalId.GOLD, R.string.medal_gold),
    PLATINUM(MedalId.PLATINUM, R.string.medal_platinum);

    val threshold: Int get() = MedalPaint.threshold(id)

    /** Körperfarbe der Münze als ARGB-Long (siehe medalColors). */
    val body: Long get() = MedalPaint.body(id)

    /** Schatten- und Prägefarbe der Münze als ARGB-Long. */
    val shade: Long get() = MedalPaint.shade(id)

    companion object {
        private fun of(id: MedalId?): MedalTier? =
            id?.let { wanted -> entries.first { it.id == wanted } }

        /** Höchste erreichte Stufe, null unterhalb von Bronze. */
        fun forScore(score: Int): MedalTier? = of(MedalPaint.forScore(score))

        /** Nächste noch nicht erreichte Stufe, null ab Platin. */
        fun next(score: Int): MedalTier? = of(MedalPaint.next(score))

        /** Bringt dieser Score eine höhere Stufe als der bisherige Bestwert? */
        fun isUpgrade(score: Int, previousBest: Int): Boolean =
            MedalPaint.isUpgrade(score, previousBest)
    }
}
