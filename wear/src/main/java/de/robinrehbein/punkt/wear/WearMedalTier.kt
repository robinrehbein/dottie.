package de.robinrehbein.punkt.wear

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import de.robinrehbein.punkt.game.MedalId
import de.robinrehbein.punkt.game.MedalPaint

/**
 * Medaillen-Stufen auf der Uhr. Schwellen und Münzfarben kommen aus
 * MedalPaint in :core — derselben Quelle wie am Phone, die beiden können
 * also nicht mehr auseinanderlaufen. Eine eigene Aufzählung bleibt
 * trotzdem: Sie hängt an den R.string-Ressourcen dieses Moduls, und
 * :wear hat bewusst keine Abhängigkeit auf :app (wie WearDotSkin).
 */
internal enum class WearMedalTier(
    val id: MedalId,
    @StringRes val nameRes: Int
) {
    BRONZE(MedalId.BRONZE, R.string.medal_bronze),
    SILVER(MedalId.SILVER, R.string.medal_silver),
    GOLD(MedalId.GOLD, R.string.medal_gold),
    PLATINUM(MedalId.PLATINUM, R.string.medal_platinum);

    val threshold: Int get() = MedalPaint.threshold(id)

    /** Körperfarbe der Münze (medalColors am Phone). */
    val body: Color get() = Color(MedalPaint.body(id))

    /** Schatten- und Prägefarbe der Münze. */
    val shade: Color get() = Color(MedalPaint.shade(id))

    companion object {
        /** Höchste erreichte Stufe, null unterhalb von Bronze. */
        fun forScore(score: Int): WearMedalTier? =
            MedalPaint.forScore(score)?.let { id -> entries.first { it.id == id } }
    }
}
