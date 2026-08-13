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
 * beste Perfekt-Serie, Daily-Serie) und an Ausdauer (Läufe, Punktesumme,
 * Tage, Monate, Saison) und werden bei jedem Öffnen des Skin-Wählers
 * frisch aus den gespeicherten Ständen abgeleitet — nichts wird separat
 * persistiert, ein neuer Rekord macht den Skin also automatisch wählbar.
 *
 * Die Reihenfolge ist die von [SkinId], damit die Liste auf der Uhr in
 * derselben Ordnung steht wie die Sammlung am Phone.
 *
 * Der Name der Aufzählung ist zugleich die Bezeichnung auf dem Display:
 * Die Uhr ist einsprachig, eine eigene Namens-Ressource je Skin wäre für
 * 42 Einträge viel Ballast für einen bewusst abgespeckten Prototyp.
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
    EI(SkinId.EI),
    TIGER(SkinId.TIGER),
    PINGUIN(SkinId.PINGUIN),
    FUSSBALL(SkinId.FUSSBALL),
    DONUT(SkinId.DONUT),

    // Bewegt
    REGENBOGEN(SkinId.REGENBOGEN),
    AURORA(SkinId.AURORA),
    MAGMA(SkinId.MAGMA),
    NEON(SkinId.NEON),
    CHROM(SkinId.CHROM),
    WELLE(SkinId.WELLE),
    GEWITTER(SkinId.GEWITTER),
    KONFETTI(SkinId.KONFETTI),
    DISCO(SkinId.DISCO),
    HOLO(SkinId.HOLO),

    // Reagierend
    CHAMAELEON(SkinId.CHAMAELEON),
    KOMBO(SkinId.KOMBO),
    TINTE(SkinId.TINTE),
    THERMO(SkinId.THERMO),
    MEDAILLE(SkinId.MEDAILLE),
    TAGESZEIT(SkinId.TAGESZEIT),
    JAHRESZEIT(SkinId.JAHRESZEIT),

    // Saison — nur im eigenen Monat verdienbar, dann für immer
    KUERBIS(SkinId.KUERBIS),
    ZUCKERSTANGE(SkinId.ZUCKERSTANGE),
    HERZ(SkinId.HERZ),
    OSTEREI(SkinId.OSTEREI),

    // Gönner — gekauft; auf der Uhr gibt es kein Billing, siehe Stats
    DIAMANT(SkinId.DIAMANT),
    PHOENIX(SkinId.PHOENIX),
    ONYX(SkinId.ONYX);

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

    /**
     * Alle Stände, gegen die Freischaltungen geprüft werden: erst die
     * Bestleistungen (Können), dann die Ausdauer-Zähler (Menge). Die
     * Trennung ist dieselbe wie in SkinStats — die Uhr spiegelt sie nur.
     *
     * [monthsPlayed] ist bereits die ANZAHL gesetzter Bits, nicht die
     * Maske: SkinStats erwartet an dieser Stelle den Zähler.
     *
     * [patronOwned] bleibt auf der Uhr immer false. Der Prototyp hat kein
     * Billing, die Gönner-Skins sind hier also nicht erreichbar — sie
     * kommen höchstens über den Abgleich mit dem Telefon in die Liste.
     */
    data class Stats(
        val bestScore: Int,
        val bestPerfectStreak: Int,
        val bestDailyStreak: Int,
        val runCount: Int = 0,
        val totalScore: Int = 0,
        val daysPlayed: Int = 0,
        val monthsPlayed: Int = 0,
        val seasonEarned: Int = 0,
        val patronOwned: Boolean = false
    ) {
        /** Übersetzung ins Format von :core — dort liegen die Schwellen. */
        fun toSkinStats(): SkinStats = SkinStats(
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
    }

    /** Gleiche Schwellen wie am Phone — beide fragen SkinPaint. */
    fun isUnlocked(stats: Stats): Boolean = SkinPaint.isUnlocked(id, stats.toSkinStats())

    companion object {
        /** Skin zu einem gespeicherten Namen, KLASSIK als Fallback. */
        fun fromName(name: String?): WearDotSkin =
            entries.firstOrNull { it.name == name } ?: KLASSIK
    }
}
