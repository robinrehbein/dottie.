package de.robinrehbein.punkt.wear

import androidx.compose.ui.graphics.Color

/**
 * Freischaltbare Punkt-Skins — Farbwerte und Freischalt-Schwellen 1:1 aus
 * dem Phone-Modul (DotSkin.kt in :app) übernommen. Eigene Kopie statt
 * Import, weil die :app-Variante an den dortigen R.string-Ressourcen hängt
 * und :wear bewusst keine Abhängigkeit auf :app hat (wie WearMedalTier).
 *
 * Freischaltungen hängen wie am Phone an dauerhaften Leistungen (Rekord,
 * beste Perfekt-Serie, Daily-Serie) und werden bei jedem Durchschalten
 * frisch aus den gespeicherten Ständen abgeleitet — nichts wird separat
 * persistiert, ein neuer Rekord macht den Skin also automatisch wählbar.
 */
internal enum class WearDotSkin(
    val body: Color,
    val shade: Color,
    val shine: Color
) {
    KLASSIK(Color(0xFFFFD847), Color(0xFFF5A623), Color(0xFFFFF3B8)),
    MINZE(Color(0xFF4BE38C), Color(0xFF2BA55E), Color(0xFFC8FFE0)),
    LAVA(Color(0xFFFF5A36), Color(0xFFC22F12), Color(0xFFFFC9A3)),
    GOLD(Color(0xFFFFC400), Color(0xFFCC8F00), Color(0xFFFFF7CC)),
    FROST(Color(0xFF8FD8FF), Color(0xFF4FA3D8), Color(0xFFE8F9FF)),
    SCHATTEN(Color(0xFF6B4F8A), Color(0xFF43315C), Color(0xFFCBB8E8)),
    PRISMA(Color(0xFFFF6FD8), Color(0xFFC93BAA), Color(0xFFB8F3FF));

    /** Dauerhafte Bestleistungen, gegen die Freischaltungen geprüft werden. */
    data class Stats(
        val bestScore: Int,
        val bestPerfectStreak: Int,
        val bestDailyStreak: Int
    )

    /** Gleiche Schwellen wie DotSkin.isUnlocked am Phone. */
    fun isUnlocked(stats: Stats): Boolean = when (this) {
        KLASSIK -> true
        MINZE -> stats.bestScore >= 10
        LAVA -> stats.bestScore >= 20
        GOLD -> stats.bestScore >= 30
        FROST -> stats.bestScore >= 40
        SCHATTEN -> stats.bestPerfectStreak >= 4
        PRISMA -> stats.bestDailyStreak >= 3
    }

    companion object {
        /** Skin zu einem gespeicherten Namen, KLASSIK als Fallback. */
        fun fromName(name: String?): WearDotSkin =
            entries.firstOrNull { it.name == name } ?: KLASSIK
    }
}
