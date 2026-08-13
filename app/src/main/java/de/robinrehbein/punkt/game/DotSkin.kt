package de.robinrehbein.punkt.game

import androidx.annotation.StringRes
import de.robinrehbein.punkt.R

/**
 * Freischaltbare Punkt-Skins. Name und Freischalt-Hinweis sind
 * String-Ressourcen (DE/EN), die UI löst sie per stringResource auf.
 *
 * Farben und Freischalt-Schwellen liegen nicht mehr hier, sondern in
 * [SkinPaint] im :core-Modul — dieselbe Quelle nutzt auch die Uhr. Ein
 * Skin ist dort eine Funktion über das 13x13-Raster des Vogels: So sind
 * gemusterte, animierte und auf den Lauf reagierende Skins möglich, ohne
 * dass die Renderer Sonderfälle kennen.
 *
 * Freischaltungen hängen an dauerhaften Leistungen (Rekord, beste
 * Perfekt-Serie, Daily-Serie), nie an Käufen — Sammeln ist die Belohnung
 * fürs Spielen.
 */
enum class DotSkin(
    val id: SkinId,
    @StringRes val titleRes: Int,
    @StringRes val unlockHintRes: Int?
) {
    KLASSIK(SkinId.KLASSIK, R.string.skin_klassik, null),
    MINZE(SkinId.MINZE, R.string.skin_minze, R.string.skin_hint_minze),
    LAVA(SkinId.LAVA, R.string.skin_lava, R.string.skin_hint_lava),
    GOLD(SkinId.GOLD, R.string.skin_gold, R.string.skin_hint_gold),
    FROST(SkinId.FROST, R.string.skin_frost, R.string.skin_hint_frost),
    SCHATTEN(SkinId.SCHATTEN, R.string.skin_schatten, R.string.skin_hint_schatten),
    PRISMA(SkinId.PRISMA, R.string.skin_prisma, R.string.skin_hint_prisma),

    // Gemustert
    BIENE(SkinId.BIENE, R.string.skin_biene, R.string.skin_hint_biene),
    MELONE(SkinId.MELONE, R.string.skin_melone, R.string.skin_hint_melone),
    PILZ(SkinId.PILZ, R.string.skin_pilz, R.string.skin_hint_pilz),
    KOI(SkinId.KOI, R.string.skin_koi, R.string.skin_hint_koi),
    GALAXIE(SkinId.GALAXIE, R.string.skin_galaxie, R.string.skin_hint_galaxie),
    KARO(SkinId.KARO, R.string.skin_karo, R.string.skin_hint_karo),

    // Bewegt
    REGENBOGEN(SkinId.REGENBOGEN, R.string.skin_regenbogen, R.string.skin_hint_regenbogen),
    AURORA(SkinId.AURORA, R.string.skin_aurora, R.string.skin_hint_aurora),
    MAGMA(SkinId.MAGMA, R.string.skin_magma, R.string.skin_hint_magma),
    NEON(SkinId.NEON, R.string.skin_neon, R.string.skin_hint_neon),
    CHROM(SkinId.CHROM, R.string.skin_chrom, R.string.skin_hint_chrom),

    // Reagierend
    CHAMAELEON(SkinId.CHAMAELEON, R.string.skin_chamaeleon, R.string.skin_hint_chamaeleon),
    KOMBO(SkinId.KOMBO, R.string.skin_kombo, R.string.skin_hint_kombo),
    TINTE(SkinId.TINTE, R.string.skin_tinte, R.string.skin_hint_tinte);

    /** Stellvertreter-Farben für Münzen, Prägung und Score-Karte. */
    val body: Long get() = SkinPaint.body(id)
    val shade: Long get() = SkinPaint.shade(id)
    val shine: Long get() = SkinPaint.shine(id)

    /** Farbe eines Rasterfelds des Vogels — siehe [SkinPaint.cell]. */
    fun cell(col: Int, row: Int, state: SkinState = SkinState()): Long =
        SkinPaint.cell(id, col, row, state)

    fun shineColor(state: SkinState = SkinState()): Long = SkinPaint.shine(id, state)

    val hasTrail: Boolean get() = SkinPaint.hasTrail(id)

    /** Braucht das Auge eine Kontur zum Körper hin? Siehe SkinPaint. */
    val needsEyeOutline: Boolean get() = SkinPaint.needsEyeOutline(id)

    /** Dauerhafte Bestleistungen, gegen die Freischaltungen geprüft werden. */
    data class Stats(
        val bestScore: Int,
        val bestPerfectStreak: Int,
        val bestDailyStreak: Int
    )

    /**
     * Dauerhaft verdient? Diese Frage kennt bewusst KEINE Tagespässe:
     * An ihr hängen die Freischalt-Feier im Game-Over und [unlockedCount],
     * und ein geliehener Skin darf sich nicht als Leistung ausgeben.
     */
    fun isUnlocked(stats: Stats): Boolean = SkinPaint.isUnlocked(id, stats.toPaint())

    /**
     * Jetzt spielbar? Also dauerhaft verdient ODER der eine Skin, für den
     * heute ein Tagespass läuft ([pass], null = keiner). Nur diese Frage
     * darf über Auswahl und Darstellung entscheiden — verdient bleibt
     * verdient, siehe [isUnlocked].
     */
    fun isAvailable(stats: Stats, pass: DotSkin?): Boolean =
        isUnlocked(stats) || this == pass

    companion object {
        private fun Stats.toPaint() = SkinStats(bestScore, bestPerfectStreak, bestDailyStreak)

        /** Skin zu einem gespeicherten Namen, KLASSIK als Fallback. */
        fun fromName(name: String?): DotSkin =
            entries.firstOrNull { it.name == name } ?: KLASSIK

        fun unlockedCount(stats: Stats): Int = entries.count { it.isUnlocked(stats) }
    }
}
