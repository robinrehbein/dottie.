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
 * Freischaltungen hängen an dauerhaften Leistungen (Rekord, Serien,
 * Ausdauer) — Sammeln ist die Belohnung fürs Spielen. Die drei
 * Gönner-Skins sind die einzige Ausnahme, und sie bleiben deshalb
 * konsequent draußen: keine Feier, kein Sammlungsstand, keine Bedingung
 * für den REGENBOGEN.
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
    EI(SkinId.EI, R.string.skin_ei, R.string.skin_hint_ei),
    TIGER(SkinId.TIGER, R.string.skin_tiger, R.string.skin_hint_tiger),
    PINGUIN(SkinId.PINGUIN, R.string.skin_pinguin, R.string.skin_hint_pinguin),
    FUSSBALL(SkinId.FUSSBALL, R.string.skin_fussball, R.string.skin_hint_fussball),
    DONUT(SkinId.DONUT, R.string.skin_donut, R.string.skin_hint_donut),

    // Bewegt
    REGENBOGEN(SkinId.REGENBOGEN, R.string.skin_regenbogen, R.string.skin_hint_regenbogen),
    AURORA(SkinId.AURORA, R.string.skin_aurora, R.string.skin_hint_aurora),
    MAGMA(SkinId.MAGMA, R.string.skin_magma, R.string.skin_hint_magma),
    NEON(SkinId.NEON, R.string.skin_neon, R.string.skin_hint_neon),
    CHROM(SkinId.CHROM, R.string.skin_chrom, R.string.skin_hint_chrom),
    WELLE(SkinId.WELLE, R.string.skin_welle, R.string.skin_hint_welle),
    GEWITTER(SkinId.GEWITTER, R.string.skin_gewitter, R.string.skin_hint_gewitter),
    KONFETTI(SkinId.KONFETTI, R.string.skin_konfetti, R.string.skin_hint_konfetti),
    DISCO(SkinId.DISCO, R.string.skin_disco, R.string.skin_hint_disco),
    HOLO(SkinId.HOLO, R.string.skin_holo, R.string.skin_hint_holo),

    // Reagierend
    CHAMAELEON(SkinId.CHAMAELEON, R.string.skin_chamaeleon, R.string.skin_hint_chamaeleon),
    KOMBO(SkinId.KOMBO, R.string.skin_kombo, R.string.skin_hint_kombo),
    TINTE(SkinId.TINTE, R.string.skin_tinte, R.string.skin_hint_tinte),
    THERMO(SkinId.THERMO, R.string.skin_thermo, R.string.skin_hint_thermo),
    MEDAILLE(SkinId.MEDAILLE, R.string.skin_medaille, R.string.skin_hint_medaille),
    TAGESZEIT(SkinId.TAGESZEIT, R.string.skin_tageszeit, R.string.skin_hint_tageszeit),
    JAHRESZEIT(SkinId.JAHRESZEIT, R.string.skin_jahreszeit, R.string.skin_hint_jahreszeit),

    // Saison — nur im eigenen Monat verdienbar, dann für immer
    KUERBIS(SkinId.KUERBIS, R.string.skin_kuerbis, R.string.skin_hint_kuerbis),
    ZUCKERSTANGE(SkinId.ZUCKERSTANGE, R.string.skin_zuckerstange, R.string.skin_hint_zuckerstange),
    HERZ(SkinId.HERZ, R.string.skin_herz, R.string.skin_hint_herz),
    OSTEREI(SkinId.OSTEREI, R.string.skin_osterei, R.string.skin_hint_osterei),

    // Gönner — gekauft, nicht verdient: Der "Hinweis" ist deshalb kein
    // Ziel, sondern der Kauf selbst.
    DIAMANT(SkinId.DIAMANT, R.string.skin_diamant, R.string.skin_hint_goenner),
    PHOENIX(SkinId.PHOENIX, R.string.skin_phoenix, R.string.skin_hint_goenner),
    ONYX(SkinId.ONYX, R.string.skin_onyx, R.string.skin_hint_goenner);

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

    /** Saison-Skin — nur in seinem Monat verdienbar, danach für immer. */
    val isSeasonal: Boolean get() = SkinPaint.isSeasonal(id)

    /** Gekaufter Gönner-Skin (kein Verdienst, keine Feier). */
    val isPatron: Boolean get() = SkinPaint.isPatron(id)

    /** Zählt für den Sammlungsstand — und damit für den REGENBOGEN. */
    val countsForCollection: Boolean get() = SkinPaint.countsForCollection(id)

    /**
     * Familie für die Gliederung des Skin-Menüs. Bei 42 Skins ist eine
     * ungegliederte Liste nicht mehr lesbar; die Einteilung folgt genau
     * den Blöcken von [SkinId] in :core, damit beide Seiten dieselbe
     * Ordnung erzählen.
     */
    val family: Family get() = when (this) {
        KLASSIK, MINZE, LAVA, GOLD, FROST, SCHATTEN, PRISMA -> Family.EINFARBIG
        BIENE, MELONE, PILZ, KOI, GALAXIE, KARO,
        EI, TIGER, PINGUIN, FUSSBALL, DONUT -> Family.GEMUSTERT
        REGENBOGEN, AURORA, MAGMA, NEON, CHROM,
        WELLE, GEWITTER, KONFETTI, DISCO, HOLO -> Family.BEWEGT
        CHAMAELEON, KOMBO, TINTE,
        THERMO, MEDAILLE, TAGESZEIT, JAHRESZEIT -> Family.REAGIEREND
        KUERBIS, ZUCKERSTANGE, HERZ, OSTEREI -> Family.SAISON
        DIAMANT, PHOENIX, ONYX -> Family.GOENNER
    }

    /** Überschriften des Skin-Menüs. */
    enum class Family(@StringRes val titleRes: Int) {
        EINFARBIG(R.string.skin_family_einfarbig),
        GEMUSTERT(R.string.skin_family_gemustert),
        BEWEGT(R.string.skin_family_bewegt),
        REAGIEREND(R.string.skin_family_reagierend),
        SAISON(R.string.skin_family_saison),
        GOENNER(R.string.skin_family_goenner)
    }

    /**
     * Alles, woraus Freischaltungen entstehen. Die ersten drei Werte sind
     * Bestleistungen (Können), die folgenden Ausdauer (Menge) — wer nie
     * Rekord 60 sieht, sammelt über die Ausdauer-Achsen trotzdem weiter.
     *
     * Alle neuen Felder tragen einen Standardwert: Aufrufer, die nur
     * Bestleistungen kennen (Tests, Vorschauen), bleiben so gültig.
     */
    data class Stats(
        val bestScore: Int,
        val bestPerfectStreak: Int,
        val bestDailyStreak: Int,
        val runCount: Int = 0,
        val totalScore: Int = 0,
        /** Kalendertage mit mindestens einem Lauf. */
        val daysPlayed: Int = 0,
        /** ANZAHL der Monate mit Lauf, nicht die Maske (siehe ScoreStore). */
        val monthsPlayed: Int = 0,
        /** Bitmaske der verdienten Saison-Skins (siehe Season.bit in :core). */
        val seasonEarned: Int = 0,
        /** Gönner-Paket gekauft — schaltet nur die Gönner-Familie frei. */
        val patronOwned: Boolean = false
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
        private fun Stats.toPaint() = SkinStats(
            bestScore = bestScore,
            bestPerfectStreak = bestPerfectStreak,
            bestDailyStreak = bestDailyStreak,
            runCount = runCount,
            totalScore = totalScore,
            daysPlayed = daysPlayed,
            monthsPlayed = monthsPlayed,
            seasonEarned = seasonEarned,
            patronOwned = patronOwned
        )

        /** Skin zu einem gespeicherten Namen, KLASSIK als Fallback. */
        fun fromName(name: String?): DotSkin =
            entries.firstOrNull { it.name == name } ?: KLASSIK

        /**
         * Sammlungsstand: nur Skins, die für die Sammlung zählen — Saison
         * und Gönner bleiben außen vor (siehe SkinPaint.unlockedCount).
         * An dieser Zahl hängt auch die Bedingung des REGENBOGEN.
         */
        fun unlockedCount(stats: Stats): Int = SkinPaint.unlockedCount(stats.toPaint())

        /** Wie viele Skins der Sammlungsstand insgesamt erreichen kann. */
        fun collectableCount(): Int = SkinPaint.collectableCount()

        /**
         * Wie viele Skins VERDIENT sind — Saison zählt mit, Gönner nicht.
         * Genau daran hängt die "NEUER SKIN FREIGESCHALTET!"-Feier: Ein
         * Saison-Skin ist Anwesenheit und darf gefeiert werden, ein
         * gekaufter ist es nicht. [unlockedCount] taugt dafür nicht, weil
         * es Saison-Skins bewusst nicht mitzählt.
         */
        fun earnedCount(stats: Stats): Int =
            entries.count { !it.isPatron && it.isUnlocked(stats) }
    }
}
