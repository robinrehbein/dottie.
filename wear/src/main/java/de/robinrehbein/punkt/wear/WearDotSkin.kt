package de.robinrehbein.punkt.wear

import androidx.compose.ui.graphics.Color
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SkinState
import de.robinrehbein.punkt.game.SkinStats

/**
 * Freischaltbare Punkt-Skins auf der Uhr. Farben und Freischalt-Schwellen
 * kommen aus SkinPaint in :core — derselben Quelle wie am Phone, die
 * beiden können also nicht mehr auseinanderlaufen. Eine eigene Aufzählung
 * bleibt trotzdem: Sie legt die Reihenfolge zum Durchschalten fest, und
 * :wear hat bewusst keine Abhängigkeit auf :app (wie WearMedalTier).
 *
 * Freischaltungen hängen wie am Phone an dauerhaften Leistungen (Rekord,
 * beste Perfekt-Serie, Daily-Serie) und werden bei jedem Durchschalten
 * frisch aus den gespeicherten Ständen abgeleitet — nichts wird separat
 * persistiert, ein neuer Rekord macht den Skin also automatisch wählbar.
 */
internal enum class WearDotSkin(val id: SkinId) {
    KLASSIK(SkinId.KLASSIK),
    MINZE(SkinId.MINZE),
    LAVA(SkinId.LAVA),
    GOLD(SkinId.GOLD),
    FROST(SkinId.FROST),
    SCHATTEN(SkinId.SCHATTEN),
    PRISMA(SkinId.PRISMA),

    // Gemustert
    BIENE(SkinId.BIENE),
    MELONE(SkinId.MELONE),
    PILZ(SkinId.PILZ),
    KOI(SkinId.KOI),
    GALAXIE(SkinId.GALAXIE),
    KARO(SkinId.KARO),

    // Bewegt
    REGENBOGEN(SkinId.REGENBOGEN),
    AURORA(SkinId.AURORA),
    MAGMA(SkinId.MAGMA),
    NEON(SkinId.NEON),
    CHROM(SkinId.CHROM),

    // Reagierend
    CHAMAELEON(SkinId.CHAMAELEON),
    KOMBO(SkinId.KOMBO),
    TINTE(SkinId.TINTE);

    /** Stellvertreter-Farben für Münze und Glanzpunkt. */
    val body: Color get() = Color(SkinPaint.body(id))
    val shade: Color get() = Color(SkinPaint.shade(id))
    val shine: Color get() = Color(SkinPaint.shine(id))

    /** Farbe eines Rasterfelds des Vogels — siehe SkinPaint.cell. */
    fun cell(col: Int, row: Int, state: SkinState = SkinState()): Color =
        Color(SkinPaint.cell(id, col, row, state))

    fun shineColor(state: SkinState = SkinState()): Color = Color(SkinPaint.shine(id, state))

    val hasTrail: Boolean get() = SkinPaint.hasTrail(id)

    /** Braucht das Auge eine Kontur zum Körper hin? Siehe SkinPaint. */
    val needsEyeOutline: Boolean get() = SkinPaint.needsEyeOutline(id)

    /** Dauerhafte Bestleistungen, gegen die Freischaltungen geprüft werden. */
    data class Stats(
        val bestScore: Int,
        val bestPerfectStreak: Int,
        val bestDailyStreak: Int
    )

    /** Gleiche Schwellen wie am Phone — beide fragen SkinPaint. */
    fun isUnlocked(stats: Stats): Boolean = SkinPaint.isUnlocked(
        id,
        SkinStats(stats.bestScore, stats.bestPerfectStreak, stats.bestDailyStreak)
    )

    companion object {
        /** Skin zu einem gespeicherten Namen, KLASSIK als Fallback. */
        fun fromName(name: String?): WearDotSkin =
            entries.firstOrNull { it.name == name } ?: KLASSIK
    }
}
