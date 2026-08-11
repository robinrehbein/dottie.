package de.robinrehbein.punkt.wear

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color

/**
 * Medaillen-Stufen ab 10/20/30/40 Punkten — Schwellen, Namen und Farben
 * 1:1 aus dem Phone-Modul (MedalTier.kt und medalColors in
 * GameOverlays.kt) übernommen. Eigene Kopie statt Import, weil die
 * :app-Variante an den dortigen R.string-Ressourcen hängt und :wear
 * bewusst keine Abhängigkeit auf :app hat.
 */
internal enum class WearMedalTier(
    val threshold: Int,
    @StringRes val nameRes: Int,
    /** Körper- und Schattenfarbe der Münze (medalColors am Phone). */
    val body: Color,
    val shade: Color
) {
    BRONZE(10, R.string.medal_bronze, Color(0xFFCD7F32), Color(0xFF9C5A1E)),
    SILVER(20, R.string.medal_silver, Color(0xFFC0C0C0), Color(0xFF8F8F9C)),
    GOLD(30, R.string.medal_gold, Color(0xFFFFD700), Color(0xFFC9A400)),
    PLATINUM(40, R.string.medal_platinum, Color(0xFFE5E4E2), Color(0xFFADB5C4));

    companion object {
        /** Höchste erreichte Stufe, null unterhalb von Bronze. */
        fun forScore(score: Int): WearMedalTier? =
            entries.lastOrNull { score >= it.threshold }
    }
}
