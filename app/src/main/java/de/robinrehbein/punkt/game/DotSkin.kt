package de.robinrehbein.punkt.game

/**
 * Freischaltbare Punkt-Skins. Die Farben sind ARGB-Werte (keine
 * Compose-Typen), damit die Unlock-Logik pur und in Unit-Tests prüfbar
 * bleibt — die UI wickelt sie in Color().
 *
 * Freischaltungen hängen an dauerhaften Leistungen (Rekord, beste
 * Perfekt-Serie, Daily-Serie), nie an Käufen — Sammeln ist die Belohnung
 * fürs Spielen.
 */
enum class DotSkin(
    val title: String,
    val body: Long,
    val shade: Long,
    val shine: Long
) {
    KLASSIK("KLASSIK", 0xFFFFD847, 0xFFF5A623, 0xFFFFF3B8),
    MINZE("MINZE", 0xFF4BE38C, 0xFF2BA55E, 0xFFC8FFE0),
    LAVA("LAVA", 0xFFFF5A36, 0xFFC22F12, 0xFFFFC9A3),
    GOLD("GOLD", 0xFFFFC400, 0xFFCC8F00, 0xFFFFF7CC),
    FROST("FROST", 0xFF8FD8FF, 0xFF4FA3D8, 0xFFE8F9FF),
    SCHATTEN("SCHATTEN", 0xFF6B4F8A, 0xFF43315C, 0xFFCBB8E8),
    PRISMA("PRISMA", 0xFFFF6FD8, 0xFFC93BAA, 0xFFB8F3FF);

    /** Dauerhafte Bestleistungen, gegen die Freischaltungen geprüft werden. */
    data class Stats(
        val bestScore: Int,
        val bestPerfectStreak: Int,
        val bestDailyStreak: Int
    )

    fun isUnlocked(stats: Stats): Boolean = when (this) {
        KLASSIK -> true
        MINZE -> stats.bestScore >= 10
        LAVA -> stats.bestScore >= 20
        GOLD -> stats.bestScore >= 30
        FROST -> stats.bestScore >= 40
        SCHATTEN -> stats.bestPerfectStreak >= 4
        PRISMA -> stats.bestDailyStreak >= 3
    }

    /** Kurzer Hinweis, wie sich ein gesperrter Skin freischalten lässt. */
    val unlockHint: String
        get() = when (this) {
            KLASSIK -> ""
            MINZE -> "REKORD 10 (BRONZE)"
            LAVA -> "REKORD 20 (SILBER)"
            GOLD -> "REKORD 30 (GOLD)"
            FROST -> "REKORD 40 (PLATIN)"
            SCHATTEN -> "4 PERFEKTE IN SERIE"
            PRISMA -> "DAILY-SERIE: 3 TAGE"
        }

    companion object {
        /** Skin zu einem gespeicherten Namen, KLASSIK als Fallback. */
        fun fromName(name: String?): DotSkin =
            entries.firstOrNull { it.name == name } ?: KLASSIK

        fun unlockedCount(stats: Stats): Int = entries.count { it.isUnlocked(stats) }
    }
}
