package de.robinrehbein.punkt.game

import androidx.annotation.StringRes
import de.robinrehbein.punkt.R

/**
 * Freischaltbare Punkt-Skins. Die Farben sind ARGB-Werte (keine
 * Compose-Typen), damit die Unlock-Logik pur und in Unit-Tests prüfbar
 * bleibt — die UI wickelt sie in Color(). Name und Freischalt-Hinweis
 * sind String-Ressourcen (DE/EN), die UI löst sie per stringResource auf.
 *
 * Freischaltungen hängen an dauerhaften Leistungen (Rekord, beste
 * Perfekt-Serie, Daily-Serie), nie an Käufen — Sammeln ist die Belohnung
 * fürs Spielen.
 */
enum class DotSkin(
    @StringRes val titleRes: Int,
    @StringRes val unlockHintRes: Int?,
    val body: Long,
    val shade: Long,
    val shine: Long
) {
    KLASSIK(R.string.skin_klassik, null, 0xFFFFD847, 0xFFF5A623, 0xFFFFF3B8),
    MATCHA(R.string.skin_matcha, null, 0xFF9DBB61, 0xFF6E8B3D, 0xFFE2F0BF),
    TOFFIFEE(R.string.skin_toffifee, null, 0xFFB5793E, 0xFF7E4F23, 0xFFEBD2AB),
    MINZE(R.string.skin_minze, R.string.skin_hint_minze, 0xFF4BE38C, 0xFF2BA55E, 0xFFC8FFE0),
    LAVA(R.string.skin_lava, R.string.skin_hint_lava, 0xFFFF5A36, 0xFFC22F12, 0xFFFFC9A3),
    GOLD(R.string.skin_gold, R.string.skin_hint_gold, 0xFFFFC400, 0xFFCC8F00, 0xFFFFF7CC),
    FROST(R.string.skin_frost, R.string.skin_hint_frost, 0xFF8FD8FF, 0xFF4FA3D8, 0xFFE8F9FF),
    BASKETBALL(R.string.skin_basketball, R.string.skin_hint_basketball, 0xFFE8722C, 0xFFAD4C1B, 0xFFFFC291),
    SCHATTEN(R.string.skin_schatten, R.string.skin_hint_schatten, 0xFF6B4F8A, 0xFF43315C, 0xFFCBB8E8),
    TENNISBALL(R.string.skin_tennisball, R.string.skin_hint_tennisball, 0xFFCCE62E, 0xFF93AC1F, 0xFFF2FFAD),
    PRISMA(R.string.skin_prisma, R.string.skin_hint_prisma, 0xFFFF6FD8, 0xFFC93BAA, 0xFFB8F3FF);

    /** Dauerhafte Bestleistungen, gegen die Freischaltungen geprüft werden. */
    data class Stats(
        val bestScore: Int,
        val bestPerfectStreak: Int,
        val bestDailyStreak: Int
    )

    fun isUnlocked(stats: Stats): Boolean = when (this) {
        KLASSIK, MATCHA, TOFFIFEE -> true
        MINZE -> stats.bestScore >= 10
        LAVA -> stats.bestScore >= 20
        GOLD -> stats.bestScore >= 30
        FROST -> stats.bestScore >= 40
        BASKETBALL -> stats.bestScore >= 50
        SCHATTEN -> stats.bestPerfectStreak >= 4
        TENNISBALL -> stats.bestPerfectStreak >= 6
        PRISMA -> stats.bestDailyStreak >= 3
    }

    companion object {
        /** Skin zu einem gespeicherten Namen, KLASSIK als Fallback. */
        fun fromName(name: String?): DotSkin =
            entries.firstOrNull { it.name == name } ?: KLASSIK

        fun unlockedCount(stats: Stats): Int = entries.count { it.isUnlocked(stats) }
    }
}
