package de.robinrehbein.punkt.game

import androidx.annotation.StringRes
import de.robinrehbein.punkt.R

/**
 * Medaillen-Stufen ab 10/20/30/40 Punkten. Die Reihenfolge der Einträge
 * ist gleichzeitig die Rangfolge (ordinal) — daran hängt die
 * "NEUE MEDAILLE!"-Feier im Game-Over.
 */
enum class MedalTier(val threshold: Int, @StringRes val nameRes: Int) {
    BRONZE(10, R.string.medal_bronze),
    SILVER(20, R.string.medal_silver),
    GOLD(30, R.string.medal_gold),
    PLATINUM(40, R.string.medal_platinum);

    companion object {
        /** Höchste erreichte Stufe, null unterhalb von Bronze. */
        fun forScore(score: Int): MedalTier? = entries.lastOrNull { score >= it.threshold }

        /** Nächste noch nicht erreichte Stufe, null ab Platin. */
        fun next(score: Int): MedalTier? = entries.firstOrNull { score < it.threshold }

        /** Bringt dieser Score eine höhere Stufe als der bisherige Bestwert? */
        fun isUpgrade(score: Int, previousBest: Int): Boolean =
            (forScore(score)?.ordinal ?: -1) > (forScore(previousBest)?.ordinal ?: -1)
    }
}
