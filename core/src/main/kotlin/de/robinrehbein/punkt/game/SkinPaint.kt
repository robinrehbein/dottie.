package de.robinrehbein.punkt.game

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Farbwerk aller Punkt-Skins — die einzige Quelle für Skin-Farben in
 * Kotlin. :app und :wear halten nur noch die Namen und Beschriftungen und
 * fragen hier nach, was ein Pixel des Vogels für eine Farbe hat.
 *
 * Ein Skin ist nicht mehr "drei Farben", sondern eine Funktion
 * [cell]: Für jedes Feld des 13x13-Rasters kommt eine Farbe zurück. Damit
 * sind gemusterte (Biene, Melone), animierte (Regenbogen, Magma) und auf
 * den Lauf reagierende Skins (Chamäleon, Kombo) möglich, ohne dass jeder
 * Renderer Sonderfälle kennen muss — er füllt weiter stumpf Rechtecke.
 *
 * [body], [shade] und [shine] bleiben als Stellvertreter-Farben erhalten:
 * Münzen, Medaillen-Prägung und die Score-Karte brauchen einen einzelnen
 * Farbwert, wo kein ganzer Vogel gezeichnet wird.
 *
 * Alle Farben sind ARGB-Longs (0xAARRGGBB), damit das Modul frei von
 * Compose- und Android-Typen bleibt und in Unit-Tests prüfbar ist.
 */
enum class SkinId {
    // Einfarbig
    KLASSIK, MINZE, LAVA, GOLD, FROST, SCHATTEN, PRISMA,

    // Gemustert
    BIENE, MELONE, PILZ, KOI, GALAXIE, KARO,
    EI, TIGER, PINGUIN, FUSSBALL, DONUT,

    // Bewegt (Zeit)
    REGENBOGEN, AURORA, MAGMA, NEON, CHROM,
    WELLE, GEWITTER, KONFETTI, DISCO, HOLO,

    // Reagierend (Spielstand, Uhr, Kalender)
    CHAMAELEON, KOMBO, TINTE,
    THERMO, MEDAILLE, TAGESZEIT, JAHRESZEIT,

    // Saison — nur im eigenen Monat verdienbar, dann für immer
    KUERBIS, ZUCKERSTANGE, HERZ, OSTEREI,

    // Gönner — gekauft, nicht verdient
    DIAMANT, PHOENIX, ONYX
}

/**
 * Der Lauf-Zustand, aus dem sich bewegte und reagierende Skins speisen.
 * Für Standbilder (Auswahl, Score-Karte) reicht der Standardwert.
 *
 * [hour] (0-23) und [month] (1-12) kommen von der Uhr des Geräts, nicht
 * aus dem Lauf: TAGESZEIT und JAHRESZEIT ziehen daraus ihr Kleid. Die
 * Standardwerte zeigen den Mittag im Juni — so sieht jede Vorschau, die
 * keinen Kalender kennt, dasselbe Bild.
 */
data class SkinState(
    val elapsed: Float = 0f,
    val score: Int = 0,
    val perfectStreak: Int = 0,
    val hour: Int = 12,
    val month: Int = 6
)

/**
 * Alles, woraus sich Freischaltungen speisen. Die ersten drei Werte sind
 * Bestleistungen (Können), die nächsten vier Ausdauer (Menge) — die
 * Trennung ist Absicht: Wer nie Rekord 60 sieht, sammelt trotzdem weiter.
 *
 * [seasonEarned] ist eine Bitmaske über [Season.bit]: Saison-Skins werden
 * nur in ihrem Monat verdient, bleiben danach aber für immer. Die Maske
 * ist deshalb der einzige Weg, sie zu prüfen — der Kalender allein würde
 * sie im November wieder wegnehmen.
 *
 * [patronOwned] ist kein Verdienst, sondern ein Kauf. Er schaltet nur die
 * Gönner-Familie frei und zählt nirgends mit, wo Leistung gezählt wird.
 */
data class SkinStats(
    val bestScore: Int,
    val bestPerfectStreak: Int,
    val bestDailyStreak: Int,
    val runCount: Int = 0,
    val totalScore: Int = 0,
    val daysPlayed: Int = 0,
    val monthsPlayed: Int = 0,
    val seasonEarned: Int = 0,
    val patronOwned: Boolean = false
)

/**
 * Die vier Saison-Skins und ihr Fenster. [requiredDays] zählt Tage mit
 * mindestens einem Lauf innerhalb des Monats — bewusst kein Rekord und
 * keine Serie: Ein Saison-Skin soll an Anwesenheit hängen, nicht an
 * Können, sonst ist er für die Hälfte der Spieler:innen Deko.
 *
 * Verpasst ist nicht verloren: Das Fenster kommt jedes Jahr wieder.
 */
enum class Season(
    val skin: SkinId,
    /** Kalendermonat 1-12, in dem dieser Skin verdient werden kann. */
    val month: Int,
    val requiredDays: Int
) {
    KUERBIS(SkinId.KUERBIS, 10, 5),
    ZUCKERSTANGE(SkinId.ZUCKERSTANGE, 12, 5),
    HERZ(SkinId.HERZ, 2, 3),
    OSTEREI(SkinId.OSTEREI, 4, 5);

    /** Bit dieses Skins in [SkinStats.seasonEarned]. */
    val bit: Int get() = 1 shl ordinal

    companion object {
        /** Der Skin, der in diesem Monat verdient werden kann — sonst null. */
        fun forMonth(month: Int): Season? = entries.firstOrNull { it.month == month }

        fun forSkin(id: SkinId): Season? = entries.firstOrNull { it.skin == id }
    }
}

object SkinPaint {

    /** Kantenlänge des Vogel-Rasters (wie GRID in den Renderern). */
    const val GRID = 13

    private const val MID = (GRID - 1) / 2f

    /** Radius der Kreismaske — identisch zu drawPixelCircle. */
    private const val RR = GRID / 2f - 0.25f

    /**
     * Himmelsstufen. Spiegelt SkyStages aus der UI (TimingGameScreen.kt);
     * der Gleichstand ist per Test abgesichert.
     */
    val SKY_STAGES = longArrayOf(
        0xFF4EC0CA, // 0+  Tag
        0xFF5B9BD5, // 5+  Blau
        0xFF7B6FD0, // 10+ Lila
        0xFFC0616F, // 15+ Altrosa
        0xFFD98A3D, // 20+ Sonnenuntergang
        0xFF3D4A8C, // 25+ Dämmerung
        0xFF2A2640  // 30+ Nacht
    )

    /**
     * Länge eines Himmels-Umlaufs in Stufen: sechs hoch von Tag bis Nacht,
     * sechs zurück. Bei einer Stufe je fünf Punkte ist ein Umlauf also 60
     * Punkte lang.
     */
    const val SKY_CYCLE = 12

    /**
     * Himmelsstufe zu einem Score. Der Zähler bleibt nicht in der Nacht
     * stehen, sondern läuft weiter — hoch bis zur Nacht und wieder zurück
     * zum Tag. Damit bleibt der Himmel bis zum letzten Punkt ein
     * Fortschrittsanzeiger, statt ab Score 30 einzufrieren, und niemand
     * spielt einen langen Lauf komplett im Dunkeln.
     */
    fun skyStage(score: Int): Int {
        val step = ((score / 5) % SKY_CYCLE + SKY_CYCLE) % SKY_CYCLE
        return if (step <= SKY_CYCLE / 2) step else SKY_CYCLE - step
    }

    /** Wie viele Nachbilder ein Schweif-Skin auf der Bahn hinterlässt. */
    const val TRAIL_STEPS = 3

    /** Winkelabstand zweier Schweif-Nachbilder in Radiant. */
    const val TRAIL_SPACING = 0.10f

    // ===== Stellvertreter-Farben =====

    fun body(id: SkinId): Long = when (id) {
        SkinId.KLASSIK -> 0xFFFFD847
        SkinId.MINZE -> 0xFF4BE38C
        SkinId.LAVA -> 0xFFFF5A36
        SkinId.GOLD -> 0xFFFFC400
        SkinId.FROST -> 0xFF8FD8FF
        SkinId.SCHATTEN -> 0xFF6B4F8A
        SkinId.PRISMA -> 0xFFFF6FD8
        SkinId.BIENE -> 0xFFFFD847
        SkinId.MELONE -> 0xFFF0555C
        SkinId.PILZ -> 0xFFE8452F
        SkinId.KOI -> 0xFFF7F3EE
        SkinId.GALAXIE -> 0xFF4E3C86
        SkinId.KARO -> 0xFF4EC0CA
        SkinId.REGENBOGEN -> 0xFFFF6FD8
        SkinId.AURORA -> 0xFF3FE0A8
        SkinId.MAGMA -> 0xFF3A2431
        SkinId.NEON -> 0xFF241E33
        SkinId.CHROM -> 0xFFE6EAF2
        SkinId.CHAMAELEON -> 0xFF8FD8DE
        SkinId.KOMBO -> 0xFFFFD847
        SkinId.TINTE -> 0xFF2A46A8
        SkinId.EI -> 0xFFFFE58F
        SkinId.TIGER -> 0xFFFF8A2B
        SkinId.PINGUIN -> 0xFF2E3440
        SkinId.FUSSBALL -> 0xFFF7F3EE
        SkinId.DONUT -> 0xFFFF7FBF
        SkinId.WELLE -> 0xFF2E86D8
        SkinId.GEWITTER -> 0xFF4A5568
        SkinId.KONFETTI -> 0xFFF7F3EE
        SkinId.DISCO -> 0xFFC3CBD9
        SkinId.HOLO -> 0xFF7FD8E8
        SkinId.THERMO -> 0xFFFFD847
        SkinId.MEDAILLE -> 0xFFC0C0C0
        SkinId.TAGESZEIT -> 0xFF8FD8FF
        SkinId.JAHRESZEIT -> 0xFFFFC93C
        SkinId.KUERBIS -> 0xFFF5821F
        SkinId.ZUCKERSTANGE -> 0xFFE8452F
        SkinId.HERZ -> 0xFFFF6FA8
        SkinId.OSTEREI -> 0xFFFFB8D9
        SkinId.DIAMANT -> 0xFFA8C8EE
        SkinId.PHOENIX -> 0xFFFF8A2B
        SkinId.ONYX -> 0xFF221C29
    }

    fun shade(id: SkinId): Long = when (id) {
        SkinId.KLASSIK -> 0xFFF5A623
        SkinId.MINZE -> 0xFF2BA55E
        SkinId.LAVA -> 0xFFC22F12
        SkinId.GOLD -> 0xFFCC8F00
        SkinId.FROST -> 0xFF4FA3D8
        SkinId.SCHATTEN -> 0xFF43315C
        SkinId.PRISMA -> 0xFFC93BAA
        SkinId.BIENE -> 0xFF3A2C33
        SkinId.MELONE -> 0xFF74BF2E
        SkinId.PILZ -> 0xFFC2301F
        SkinId.KOI -> 0xFFE8452F
        SkinId.GALAXIE -> 0xFF231A3F
        SkinId.KARO -> 0xFF2E8E98
        SkinId.REGENBOGEN -> 0xFF7A3BC9
        SkinId.AURORA -> 0xFF2A7F8E
        SkinId.MAGMA -> 0xFFC22F12
        SkinId.NEON -> 0xFF181328
        SkinId.CHROM -> 0xFF5B6478
        SkinId.CHAMAELEON -> 0xFF3F9BA5
        SkinId.KOMBO -> 0xFFE0A400
        SkinId.TINTE -> 0xFF1F3A8A
        SkinId.EI -> 0xFFE8B92E
        SkinId.TIGER -> 0xFF2A1F1C
        SkinId.PINGUIN -> 0xFF1B1F28
        SkinId.FUSSBALL -> 0xFF2A2C33
        SkinId.DONUT -> 0xFFC08A47
        SkinId.WELLE -> 0xFF1F5FA8
        SkinId.GEWITTER -> 0xFF2F3644
        SkinId.KONFETTI -> 0xFFFF5A36
        SkinId.DISCO -> 0xFF8892A6
        SkinId.HOLO -> 0xFFC93BAA
        SkinId.THERMO -> 0xFFE0A400
        SkinId.MEDAILLE -> 0xFF8F8F9C
        SkinId.TAGESZEIT -> 0xFF3D4A8C
        SkinId.JAHRESZEIT -> 0xFFE09218
        SkinId.KUERBIS -> 0xFFC25E10
        SkinId.ZUCKERSTANGE -> 0xFFC2301F
        SkinId.HERZ -> 0xFFD6407E
        SkinId.OSTEREI -> 0xFFB096E8
        SkinId.DIAMANT -> 0xFF4E6A96
        SkinId.PHOENIX -> 0xFF8E2410
        SkinId.ONYX -> 0xFF141018
    }

    /** Glanzpunkt — bei NEON wandert er mit der Leuchtfarbe mit. */
    fun shine(id: SkinId, state: SkinState = SkinState()): Long = when (id) {
        SkinId.KLASSIK -> 0xFFFFF3B8
        SkinId.MINZE -> 0xFFC8FFE0
        SkinId.LAVA -> 0xFFFFC9A3
        SkinId.GOLD -> 0xFFFFF7CC
        SkinId.FROST -> 0xFFE8F9FF
        SkinId.SCHATTEN -> 0xFFCBB8E8
        SkinId.PRISMA -> 0xFFB8F3FF
        SkinId.BIENE -> 0xFFFFF3B8
        SkinId.MELONE -> 0xFFFFD3D6
        SkinId.PILZ -> 0xFFFFD9C9
        SkinId.KOI -> 0xFFFFFFFF
        SkinId.GALAXIE -> 0xFFFFF3B8
        SkinId.KARO -> 0xFFFFFFFF
        SkinId.REGENBOGEN -> 0xFFFFFFFF
        SkinId.AURORA -> 0xFFE8F9FF
        SkinId.MAGMA -> 0xFFFFD847
        SkinId.NEON -> neonGlow(state)
        SkinId.CHROM -> 0xFFFFFFFF
        SkinId.CHAMAELEON -> 0xFFFFFFFF
        SkinId.KOMBO -> 0xFFFFF3B8
        SkinId.TINTE -> 0xFFA8C0FF
        SkinId.EI -> 0xFFFFFFFF
        SkinId.TIGER -> 0xFFFFE0B8
        SkinId.PINGUIN -> 0xFFFFFFFF
        SkinId.FUSSBALL -> 0xFFFFFFFF
        SkinId.DONUT -> 0xFFFFFFFF
        SkinId.WELLE -> 0xFFFFFFFF
        SkinId.GEWITTER -> 0xFFFFF3B8
        SkinId.KONFETTI -> 0xFFFFFFFF
        SkinId.DISCO -> 0xFFFFFFFF
        SkinId.HOLO -> 0xFFFFFFFF
        SkinId.THERMO -> 0xFFFFFFFF
        SkinId.MEDAILLE -> 0xFFFFFFFF
        SkinId.TAGESZEIT -> 0xFFFFFFFF
        SkinId.JAHRESZEIT -> 0xFFFFFFFF
        SkinId.KUERBIS -> 0xFFFFE0B8
        SkinId.ZUCKERSTANGE -> 0xFFFFFFFF
        SkinId.HERZ -> 0xFFFFFFFF
        SkinId.OSTEREI -> 0xFFFFFFFF
        SkinId.DIAMANT -> 0xFFFFFFFF
        SkinId.PHOENIX -> 0xFFFFF3B8
        SkinId.ONYX -> 0xFFFFE07A
    }

    /**
     * Felder, an die das Auge grenzt — in beiden Blickrichtungen, damit
     * die Entscheidung nicht beim Richtungswechsel kippt. Das Auge selbst
     * liegt (nach rechts blickend) auf Spalte 7,5 bis 11 und Zeile 3 bis 7.
     */
    private val EYE_NEIGHBOURS = listOf(
        7 to 3, 7 to 4, 7 to 5, 7 to 6, 8 to 2, 9 to 2, 10 to 2, 8 to 7, 9 to 7, 10 to 7,
        5 to 3, 5 to 4, 5 to 5, 5 to 6, 4 to 2, 3 to 2, 2 to 2, 4 to 7, 3 to 7, 2 to 7
    )

    /**
     * Ab welchem Abstand zu Weiß (0 bis 441 im RGB-Raum) ein Körper als
     * "zu hell fürs Auge" gilt. Der Wert liegt bewusst niedrig: Gold und
     * Hellblau tragen den Kontrast zum weißen Auge noch selbst, Creme und
     * gebürstetes Metall nicht mehr.
     */
    private const val EYE_OUTLINE_DISTANCE = 60f

    /**
     * Braucht das Auge dieses Skins eine Kontur zum Körper hin? Auf sehr
     * hellen Körpern (Koi, Chrom) verschwände das weiße Auge sonst und nur
     * die Pupille bliebe stehen; auf allen anderen wirkt die Kontur wie
     * ein Kasten ums Auge und nimmt dem Vogel die weiche Silhouette.
     *
     * Gemessen wird im Ruhezustand, nicht pro Frame — sonst könnte die
     * Kontur bei bewegten Skins mitten im Lauf an- und ausgehen.
     */
    fun needsEyeOutline(id: SkinId): Boolean = EYE_NEIGHBOURS.any { (col, row) ->
        distanceToWhite(cell(id, col, row)) < EYE_OUTLINE_DISTANCE
    }

    /** Abstand einer ARGB-Farbe zu Weiß im RGB-Raum. */
    private fun distanceToWhite(color: Long): Float {
        val r = 255f - ((color shr 16) and 0xFF)
        val g = 255f - ((color shr 8) and 0xFF)
        val b = 255f - (color and 0xFF)
        return sqrt(r * r + g * g + b * b)
    }

    /** Drei Farben für Vorschau-Kacheln außerhalb des Spiels. */
    fun chips(id: SkinId): List<Long> = listOf(body(id), shade(id), shine(id))

    /** Hinterlässt der Skin Nachbilder auf der Bahn? */
    fun hasTrail(id: SkinId): Boolean = id == SkinId.TINTE || id == SkinId.PHOENIX

    /** Saison-Skin? Verdienbar nur im eigenen Monat (siehe [Season]). */
    fun isSeasonal(id: SkinId): Boolean = Season.forSkin(id) != null

    /** Gekaufter Gönner-Skin? */
    fun isPatron(id: SkinId): Boolean = when (id) {
        SkinId.DIAMANT, SkinId.PHOENIX, SkinId.ONYX -> true
        else -> false
    }

    /**
     * Zählt dieser Skin für den Sammlungsstand — und damit für die
     * Bedingung des REGENBOGEN?
     *
     * Saison-Skins nicht, sonst wäre der Regenbogen frühestens nach einem
     * Jahr erreichbar. Gönner-Skins nicht, sonst wäre er käuflich. Beides
     * würde aus dem Abschluss der Sammlung etwas machen, das nicht mehr
     * am Spielen hängt.
     */
    fun countsForCollection(id: SkinId): Boolean = !isSeasonal(id) && !isPatron(id)

    /**
     * Bewegte Skins müssen nicht in jedem Frame neu gerastert werden — ein
     * Zwölftel einer Sekunde ist fein genug für den Pixel-Look. iOS
     * schlüsselt seinen Textur-Cache darüber, statt 60-mal pro Sekunde ein
     * Bild zu erzeugen.
     */
    fun frameKey(id: SkinId, state: SkinState): Int = when {
        isAnimated(id) -> (state.elapsed * 12f).toInt()
        id == SkinId.CHAMAELEON -> skyStage(state.score)
        id == SkinId.KOMBO -> min(state.perfectStreak, 5)
        id == SkinId.THERMO -> min(state.score, HEAT_SCORE)
        id == SkinId.MEDAILLE -> medalTier(state.score)
        id == SkinId.TAGESZEIT -> state.hour
        id == SkinId.JAHRESZEIT -> state.month
        else -> 0
    }

    /** Hängt die Farbe an der Uhr (im Gegensatz zu Muster und Spielstand)? */
    fun isAnimated(id: SkinId): Boolean = when (id) {
        SkinId.REGENBOGEN, SkinId.AURORA, SkinId.MAGMA, SkinId.NEON, SkinId.CHROM,
        SkinId.WELLE, SkinId.GEWITTER, SkinId.KONFETTI, SkinId.DISCO, SkinId.HOLO,
        SkinId.ZUCKERSTANGE, SkinId.DIAMANT, SkinId.PHOENIX, SkinId.ONYX -> true
        else -> false
    }

    // ===== Freischaltung =====

    fun isUnlocked(id: SkinId, stats: SkinStats): Boolean = when (id) {
        SkinId.KLASSIK -> true
        SkinId.MINZE -> stats.bestScore >= 10
        SkinId.LAVA -> stats.bestScore >= 20
        SkinId.GOLD -> stats.bestScore >= 30
        SkinId.FROST -> stats.bestScore >= 40
        SkinId.SCHATTEN -> stats.bestPerfectStreak >= 4
        SkinId.PRISMA -> stats.bestDailyStreak >= 3
        SkinId.BIENE -> stats.bestPerfectStreak >= 6
        SkinId.MELONE -> stats.bestScore >= 25
        SkinId.CHAMAELEON -> stats.bestScore >= 30
        SkinId.PILZ -> stats.bestScore >= 35
        SkinId.CHROM -> stats.bestScore >= 45
        SkinId.GALAXIE -> stats.bestScore >= 50
        SkinId.TINTE -> stats.bestScore >= 55
        SkinId.MAGMA -> stats.bestScore >= 60
        SkinId.KOI -> stats.bestDailyStreak >= 7
        SkinId.AURORA -> stats.bestDailyStreak >= 14
        SkinId.KOMBO -> stats.bestPerfectStreak >= 8
        SkinId.KARO -> stats.bestPerfectStreak >= 10
        SkinId.NEON -> stats.bestPerfectStreak >= 12

        // Ausdauer statt Können: Diese Achsen wachsen mit jedem Lauf, auch
        // mit den schlechten. Ohne sie hängen fast alle Skins am Rekord,
        // und wer bei 25 stehenbleibt, sammelt nie wieder etwas.
        SkinId.EI -> stats.runCount >= 25
        SkinId.TIGER -> stats.runCount >= 100
        SkinId.MEDAILLE -> stats.runCount >= 200
        SkinId.FUSSBALL -> stats.runCount >= 300
        SkinId.DONUT -> stats.totalScore >= 1_000
        SkinId.KONFETTI -> stats.totalScore >= 5_000
        SkinId.TAGESZEIT -> stats.daysPlayed >= 7
        SkinId.JAHRESZEIT -> stats.monthsPlayed >= 3

        SkinId.PINGUIN -> stats.bestScore >= 65
        SkinId.WELLE -> stats.bestScore >= 70
        SkinId.THERMO -> stats.bestScore >= 75
        SkinId.HOLO -> stats.bestScore >= 80
        SkinId.GEWITTER -> stats.bestPerfectStreak >= 15
        SkinId.DISCO -> stats.bestDailyStreak >= 21

        // Saison: im eigenen Monat verdient, danach für immer gehalten.
        // Geprüft wird deshalb die Maske, nie der Kalender — sonst wäre
        // der Kürbis im November wieder weg.
        SkinId.KUERBIS, SkinId.ZUCKERSTANGE, SkinId.HERZ, SkinId.OSTEREI ->
            Season.forSkin(id)?.let { stats.seasonEarned and it.bit != 0 } ?: false

        // Gönner: gekauft. Kein Verdienst, keine Feier, kein Zählwert.
        SkinId.DIAMANT, SkinId.PHOENIX, SkinId.ONYX -> stats.patronOwned

        // Der Regenbogen ist der Abschluss der Sammlung: Er kommt erst,
        // wenn alle Skins offen sind, die für die Sammlung zählen (er
        // selbst zählt nicht mit, sonst wäre die Bedingung zirkulär —
        // Saison und Gönner zählen nicht mit, siehe countsForCollection).
        SkinId.REGENBOGEN -> SkinId.entries.all {
            it == SkinId.REGENBOGEN || !countsForCollection(it) || isUnlocked(it, stats)
        }
    }

    /**
     * Wie viele Skins dauerhaft verdient sind. Gekaufte und Saison-Skins
     * bleiben außen vor: Der Zähler ist eine Leistungsanzeige.
     */
    fun unlockedCount(stats: SkinStats): Int =
        SkinId.entries.count { countsForCollection(it) && isUnlocked(it, stats) }

    /** Wie viele Skins dieser Zähler insgesamt erreichen kann. */
    fun collectableCount(): Int = SkinId.entries.count { countsForCollection(it) }

    // ===== Das Farbwerk =====

    /**
     * Farbe eines Rasterfelds. [col] und [row] laufen von 0 bis GRID-1,
     * Feld (0,0) liegt oben links. Die Kreismaske und die Kontur bleiben
     * Sache des Renderers — hier kommt immer die Füllfarbe zurück.
     */
    fun cell(id: SkinId, col: Int, row: Int, state: SkinState = SkinState()): Long {
        val t = state.elapsed
        return when (id) {
            SkinId.KLASSIK, SkinId.MINZE, SkinId.LAVA, SkinId.GOLD,
            SkinId.FROST, SkinId.SCHATTEN, SkinId.PRISMA, SkinId.TINTE ->
                shaded(col, row, body(id), shade(id))

            SkinId.BIENE ->
                if (((col - row) % 6 + 6) % 6 < 2) 0xFF3A2C33
                else shaded(col, row, 0xFFFFD847, 0xFFE0A400)

            SkinId.MELONE -> when {
                row >= 10 -> if (col + row > GRID * 1.15f) 0xFF5AA020 else 0xFF74BF2E
                row == 9 -> 0xFFDFF2C6
                isSeed(col, row) -> 0xFF3A2C33
                else -> shaded(col, row, 0xFFF0555C, 0xFFC93B48)
            }

            SkinId.PILZ -> when {
                row >= 9 -> shaded(col, row, 0xFFF7F3EE, 0xFFD9CEC2)
                isDot(col, row) -> 0xFFF7F3EE
                else -> shaded(col, row, 0xFFE8452F, 0xFFC2301F)
            }

            SkinId.KOI -> when {
                isRedPatch(col, row) -> 0xFFE8452F
                isOrangePatch(col, row) -> 0xFFF59A2E
                else -> shaded(col, row, 0xFFF7F3EE, 0xFFD9CEC2)
            }

            SkinId.GALAXIE -> when {
                isStar(col, row) -> 0xFFFFF3B8
                isNebula(col, row) -> 0xFF7FDCE4
                else -> mix(0xFF4E3C86, 0xFF231A3F, (col + row) / (GRID * 2f))
            }

            SkinId.KARO ->
                if ((col / 2 + row / 2) % 2 == 0) {
                    if (col + row > GRID * 1.15f) 0xFF2E8E98 else 0xFF4EC0CA
                } else {
                    shaded(col, row, 0xFFF7F3EE, 0xFFD9CEC2)
                }

            SkinId.REGENBOGEN -> {
                // Der Grünbereich wird übersprungen: Ein grüner Vogel sähe
                // für einen Moment aus wie die Zielzone.
                var h = (t * 45f) % 300f
                if (h > 80f) h += 60f
                if (col + row > GRID * 1.15f) hsl(h, 0.70f, 0.44f) else hsl(h, 0.85f, 0.62f)
            }

            SkinId.AURORA -> {
                val wave = sin((col + row) * 0.42f - t * 1.6f)
                val h = 168f + wave * 90f
                if (col + row > GRID * 1.15f) hsl(h, 0.55f, 0.40f) else hsl(h, 0.72f, 0.60f)
            }

            SkinId.MAGMA -> {
                val vein = sin(col * 1.3f + row * 0.7f) > 0.35f
                if (!vein) {
                    if (col + row > GRID * 1.15f) 0xFF241722 else 0xFF3A2431
                } else {
                    val heat = 0.5f + 0.5f * sin(t * 3.4f + col * 0.8f + row * 0.5f)
                    mix(0xFF8E2410, 0xFFFFD847, heat)
                }
            }

            SkinId.NEON -> {
                val dx = col - MID
                val dy = row - MID
                if (sqrt(dx * dx + dy * dy) > RR - 2.2f) neonGlow(state)
                else if (col + row > GRID * 1.15f) 0xFF181328 else 0xFF241E33
            }

            SkinId.CHROM -> {
                val band = 0.5f + 0.5f * sin(col * 1.1f)
                var base = mix(0xFF5B6478, 0xFFE6EAF2, band)
                val sweep = (t * 6f) % 18f - 3f
                val d = abs(col + row * 0.4f - sweep)
                if (d < 1.6f) base = mix(base, 0xFFFFFFFF, 1f - d / 1.6f)
                if (col + row > GRID * 1.15f) mix(base, 0xFF3B4152, 0.35f) else base
            }

            SkinId.CHAMAELEON -> {
                val sky = SKY_STAGES[skyStage(state.score)]
                if (col + row > GRID * 1.15f) mix(sky, 0xFF000000, 0.18f)
                else mix(sky, 0xFFFFFFFF, 0.34f)
            }

            SkinId.KOMBO -> {
                val k = min(state.perfectStreak, 5) / 5f
                shaded(col, row, mix(0xFF8C8790, 0xFFFFD847, k), mix(0xFF5F5B63, 0xFFE0A400, k))
            }

            // ===== Gemustert =====

            SkinId.EI -> {
                // Gezackte Schalenkante: Die Kappe endet je Spalte etwas
                // anders, sonst läge ein gerader Deckel auf dem Küken.
                val jag = 3.5f + (if (col % 3 == 0) 1f else 0f) + (if (col % 2 == 0) 0.5f else 0f)
                if (row <= jag) shaded(col, row, 0xFFF7F3EE, 0xFFDCD2C4)
                else shaded(col, row, 0xFFFFE58F, 0xFFE8B92E)
            }

            SkinId.TIGER -> {
                val wave = col + sin(row * 0.55f) * 2.2f
                if (((wave % 6f) + 6f) % 6f < 1.7f) 0xFF2A1F1C
                else shaded(col, row, 0xFFFF8A2B, 0xFFD2601A)
            }

            SkinId.PINGUIN -> when {
                row >= 11 -> 0xFFF5A623
                isBelly(col, row) -> shaded(col, row, 0xFFF7F3EE, 0xFFDCD2C4)
                else -> shaded(col, row, 0xFF2E3440, 0xFF1B1F28)
            }

            SkinId.FUSSBALL ->
                if (isBallPatch(col, row)) 0xFF2A2C33
                else shaded(col, row, 0xFFF7F3EE, 0xFFD9CEC2)

            SkinId.DONUT -> {
                val edge = 5.5f + sin(col * 1.05f) * 1.3f
                when {
                    row > edge -> shaded(col, row, 0xFFE8B36A, 0xFFC08A47)
                    isSprinkle(col, row) -> SPRINKLE_COLORS[(col + row) % SPRINKLE_COLORS.size]
                    else -> shaded(col, row, 0xFFFF7FBF, 0xFFE04E9C)
                }
            }

            // ===== Bewegt =====

            SkinId.WELLE -> {
                // Eine Wasserlinie, die im Körper schwappt — darüber Luft,
                // an der Kante Schaum.
                val line = 5.6f + sin(t * 1.7f + col * 0.52f) * 1.5f
                when {
                    row > line + 0.9f -> shaded(col, row, 0xFF2E86D8, 0xFF1F5FA8)
                    row > line -> 0xFFBFE9FF
                    else -> shaded(col, row, 0xFFDCF3FF, 0xFFBBD9E8)
                }
            }

            SkinId.GEWITTER -> {
                // Der Blitz ist kurz und selten: Er trägt den Skin, aber
                // ein Dauerflackern würde den Punkt unlesbar machen.
                val phase = t % 2.6f
                val flash = when {
                    phase < 0.14f -> 1f
                    phase < 0.30f -> 0.35f
                    else -> 0f
                }
                val base = shaded(col, row, 0xFF4A5568, 0xFF2F3644)
                when {
                    flash > 0f && isBolt(col, row) -> 0xFFFFF3B8
                    flash > 0f -> mix(base, 0xFFFFE95E, 0.5f * flash)
                    else -> base
                }
            }

            SkinId.KONFETTI -> {
                val step = floor(t * 0.9f).toInt()
                val n = noise(col, row, step)
                if (n % 100 < 38) CONFETTI_COLORS[n % CONFETTI_COLORS.size]
                else shaded(col, row, 0xFFF7F3EE, 0xFFD9CEC2)
            }

            SkinId.DISCO -> {
                val facet = (col / 2 + row / 2) % 2
                val base = if (facet == 0) 0xFFC3CBD9 else 0xFF8892A6
                val k = floor(t * 7f).toInt()
                when {
                    (col * 2 + row * 3 + k) % 11 == 0 -> 0xFFFFFFFF
                    (col + row * 2 + k) % 13 == 0 -> DISCO_COLORS[(col + row + k) % DISCO_COLORS.size]
                    col + row > GRID * 1.15f -> mix(base, 0xFF3B4152, 0.3f)
                    else -> base
                }
            }

            SkinId.HOLO -> {
                // Sammelkarten-Folie. Der Grünbereich wird übersprungen wie
                // beim REGENBOGEN — ein grüner Vogel sähe für einen Moment
                // aus wie die Zielzone.
                var h = ((col - row) * 13f + t * 60f) % 360f
                if (h < 0f) h += 360f
                if (h > 80f && h < 150f) h += 70f
                var color = hsl(h, 0.75f, if (col + row > GRID * 1.15f) 0.46f else 0.66f)
                val sweep = (t * 5f) % 20f - 4f
                val d = abs(col + row * 0.6f - sweep)
                if (d < 1.4f) color = mix(color, 0xFFFFFFFF, 1f - d / 1.4f)
                color
            }

            // ===== Reagierend =====

            SkinId.THERMO -> {
                // Der Vogel heizt sich im Lauf auf: kalt bei 0, weißglühend
                // bei HEAT_SCORE. Fortschrittsanzeige an der Stelle, auf
                // die der Daumen ohnehin schaut.
                val k = min(state.score, HEAT_SCORE) / HEAT_SCORE.toFloat()
                val body = if (k < 0.5f) mix(0xFF8FD8FF, 0xFFFFD847, k * 2f)
                else mix(0xFFFFD847, 0xFFFFF6E0, (k - 0.5f) * 2f)
                val shade = if (k < 0.5f) mix(0xFF4FA3D8, 0xFFE0A400, k * 2f)
                else mix(0xFFE0A400, 0xFFFF7A3C, (k - 0.5f) * 2f)
                shaded(col, row, body, shade)
            }

            SkinId.MEDAILLE -> {
                val tier = MEDAL_COLORS[medalTier(state.score)]
                val dx = col - MID
                val dy = row - MID
                // Prägerand: außen dunkler, damit die Münze eine Kante hat.
                if (sqrt(dx * dx + dy * dy) > RR - 1.85f) mix(tier[1], 0xFF000000, 0.18f)
                else shaded(col, row, tier[0], tier[1])
            }

            SkinId.TAGESZEIT -> {
                val p = dayPalette(state.hour)
                if (p.size > 2 && isStar(col, row)) p[2]
                else shaded(col, row, p[0], p[1])
            }

            SkinId.JAHRESZEIT -> {
                val p = seasonPalette(state.month)
                if ((col * 3 + row * 5) % 11 == p[3].toInt()) p[2]
                else shaded(col, row, p[0], p[1])
            }

            // ===== Saison =====

            SkinId.KUERBIS -> when {
                row <= 1 && col in 5..7 -> 0xFF5AA020
                isGrin(col, row) -> 0xFF2A1F1C
                else -> {
                    val rib = abs((((col + 1) % 4) + 4) % 4 - 2) < 1
                    val body = if (rib) 0xFFD86A12 else 0xFFF5821F
                    if (col + row > GRID * 1.15f) mix(body, 0xFF000000, 0.22f) else body
                }
            }

            SkinId.ZUCKERSTANGE -> {
                val band = floor((col + row - t * 4f) / 2.2f).toInt()
                if (((band % 2) + 2) % 2 == 0) shaded(col, row, 0xFFE8452F, 0xFFC2301F)
                else shaded(col, row, 0xFFF7F3EE, 0xFFDCD2C4)
            }

            SkinId.HERZ ->
                // Das Herz sitzt tief: Weiter oben verdeckte es das Auge,
                // und zwei Zeichen im selben Gesicht kämpfen gegeneinander.
                if (isHeart(col, row)) shaded(col, row, 0xFFFFF0F5, 0xFFFFC8DC)
                else shaded(col, row, 0xFFFF6FA8, 0xFFD6407E)

            SkinId.OSTEREI -> {
                val band = (row + (if (col % 2 == 0) 1 else 0)) / 2 % 4
                if (band == 1 && col % 3 == 0) 0xFFFFFFFF
                else shaded(col, row, EASTER_COLORS[band][0], EASTER_COLORS[band][1])
            }

            // ===== Gönner =====

            SkinId.DIAMANT -> {
                val facet = ((floor(col * 0.9f + row * 0.4f).toInt() % 3) + 3) % 3
                var base = DIAMOND_COLORS[facet]
                val sweep = (t * 7f) % 20f - 4f
                val d = abs(col + row * 0.5f - sweep)
                if (d < 1.2f) base = mix(base, 0xFFFFFFFF, 1f - d / 1.2f)
                when {
                    noise(col, row, floor(t * 3f).toInt()) % 37 == 0 -> 0xFFFFFFFF
                    col + row > GRID * 1.15f -> mix(base, 0xFF4E6A96, 0.35f)
                    else -> base
                }
            }

            SkinId.PHOENIX -> {
                val flicker = 0.5f + 0.5f * sin(t * 4f + col * 0.7f - row * 1.1f)
                val heat = max(0f, 1f - row / 11f) * 0.6f + flicker * 0.5f
                val color = if (heat > 0.9f) 0xFFFFF3B8 else mix(0xFFE5341A, 0xFFFFB020, min(1f, heat))
                if (col + row > GRID * 1.15f) mix(color, 0xFF8E2410, 0.35f) else color
            }

            SkinId.ONYX -> {
                val vein = sin(col * 1.15f + row * 0.85f) > 0.55f
                if (!vein) {
                    if (col + row > GRID * 1.15f) 0xFF141018 else 0xFF221C29
                } else {
                    val glow = 0.5f + 0.5f * sin(t * 1.6f + col * 0.5f + row * 0.4f)
                    mix(0xFF8A6A1E, 0xFFFFE07A, glow)
                }
            }
        }
    }

    // ===== Muster-Details =====

    private fun isSeed(col: Int, row: Int): Boolean =
        (col == 4 && row == 3) || (col == 7 && row == 5) || (col == 3 && row == 6) ||
            (col == 8 && row == 2) || (col == 6 && row == 7)

    private fun isDot(col: Int, row: Int): Boolean =
        (col == 3 && row == 2) || (col == 8 && row == 1) || (col == 5 && row == 4) ||
            (col == 9 && row == 5) || (col == 2 && row == 6) || (col == 6 && row == 6)

    private fun isRedPatch(col: Int, row: Int): Boolean =
        (col == 2 && row == 4) || (col == 3 && row == 4) || (col == 3 && row == 5) ||
            (col == 2 && row == 5) || (col == 4 && row == 5) || (col == 3 && row == 3)

    private fun isOrangePatch(col: Int, row: Int): Boolean =
        (col == 8 && row == 7) || (col == 9 && row == 7) || (col == 8 && row == 8) ||
            (col == 7 && row == 8) || (col == 9 && row == 6) || (col == 7 && row == 7)

    private fun isStar(col: Int, row: Int): Boolean =
        (col == 3 && row == 3) || (col == 9 && row == 4) || (col == 5 && row == 8) ||
            (col == 10 && row == 8) || (col == 2 && row == 7)

    private fun isNebula(col: Int, row: Int): Boolean =
        (col == 7 && row == 2) || (col == 4 && row == 6) || (col == 8 && row == 9)

    /** Heller Bauch des PINGUIN — als Ellipse, damit er zur Kugel passt. */
    private fun isBelly(col: Int, row: Int): Boolean {
        val dx = (col - 6) * 0.9f
        val dy = row - 8.2f
        return sqrt(dx * dx + dy * dy) < 3.4f
    }

    /** Fünfeck in der Mitte plus angeschnittene Flecken am Rand. */
    private fun isBallPatch(col: Int, row: Int): Boolean =
        (col == 6 && row == 5) || (col == 5 && row == 6) || (col == 6 && row == 6) ||
            (col == 7 && row == 6) || (col == 5 && row == 7) || (col == 6 && row == 7) ||
            (col == 7 && row == 7) || (col == 6 && row == 8) ||
            (col == 1 && row == 4) || (col == 2 && row == 4) || (col == 2 && row == 3) ||
            (col == 10 && row == 9) || (col == 9 && row == 10) || (col == 3 && row == 11)

    private fun isSprinkle(col: Int, row: Int): Boolean =
        (col == 3 && row == 2) || (col == 5 && row == 1) || (col == 8 && row == 2) ||
            (col == 4 && row == 4) || (col == 9 && row == 4) || (col == 6 && row == 3) ||
            (col == 10 && row == 5) || (col == 2 && row == 4)

    /** Zickzack des Blitzes — läuft von oben rechts nach unten links. */
    private fun isBolt(col: Int, row: Int): Boolean =
        (col == 7 && row == 2) || (col == 6 && row == 3) || (col == 6 && row == 4) ||
            (col == 7 && row == 4) || (col == 5 && row == 5) || (col == 5 && row == 6) ||
            (col == 6 && row == 6) || (col == 4 && row == 7) || (col == 4 && row == 8) ||
            (col == 5 && row == 8) || (col == 3 && row == 9)

    /** Geschnitztes Grinsen des KUERBIS, bewusst unterhalb des Auges. */
    private fun isGrin(col: Int, row: Int): Boolean =
        (row == 10 && col in 3..9) || (row == 9 && (col == 3 || col == 6 || col == 9))

    /** Pixelherz, tief gesetzt — oben hat das Auge Vorrang. */
    private fun isHeart(col: Int, row: Int): Boolean = when (row) {
        6 -> col == 4 || col == 5 || col == 7 || col == 8
        7, 8 -> col in 3..9
        9 -> col in 4..8
        10 -> col in 5..7
        11 -> col == 6
        else -> false
    }

    private val SPRINKLE_COLORS = longArrayOf(0xFF4EC0CA, 0xFFFFF3B8, 0xFFFFFFFF, 0xFFFF5A36)

    private val CONFETTI_COLORS =
        longArrayOf(0xFFFF5A36, 0xFF4EC0CA, 0xFFFFD847, 0xFFFF6FD8, 0xFF7B6FD0)

    private val DISCO_COLORS = longArrayOf(0xFFFF6FD8, 0xFF4EC0CA, 0xFFFFD847)

    private val DIAMOND_COLORS = longArrayOf(0xFFDCEBFF, 0xFFA8C8EE, 0xFF7FA8D8)

    /** Bänder des OSTEREI: Körper- und Schattenfarbe je Band. */
    private val EASTER_COLORS = arrayOf(
        longArrayOf(0xFFFFB8D9, 0xFFE086B4),
        longArrayOf(0xFFBFE9FF, 0xFF8FC8E8),
        longArrayOf(0xFFFFF0A8, 0xFFE0CE6A),
        longArrayOf(0xFFD9C2FF, 0xFFB096E8)
    )

    /**
     * Bei welchem Score THERMO fertig durchgeglüht ist. Bewusst die
     * Platin-Schwelle: Der Vogel ist genau dann weißglühend, wenn der Lauf
     * die höchste Medaille erreicht hat.
     */
    const val HEAT_SCORE = 40

    /** Legierungen von MEDAILLE: Zinn, Bronze, Silber, Gold, Platin. */
    private val MEDAL_COLORS = arrayOf(
        longArrayOf(0xFFB8BEC9, 0xFF8A909C),
        longArrayOf(0xFFCD7F32, 0xFF9C5A1E),
        longArrayOf(0xFFC0C0C0, 0xFF8F8F9C),
        longArrayOf(0xFFFFD700, 0xFFC9A400),
        longArrayOf(0xFFE5E4E2, 0xFFADB5C4)
    )

    /**
     * Medaillenstufe eines Scores (0 = noch keine). Spiegelt MedalTier aus
     * der App — der Gleichstand ist per Test abgesichert.
     */
    fun medalTier(score: Int): Int = when {
        score >= 40 -> 4
        score >= 30 -> 3
        score >= 20 -> 2
        score >= 10 -> 1
        else -> 0
    }

    /**
     * Kleid von TAGESZEIT nach Stunde: Morgenrot, Mittagsblau, Abendglut,
     * Nachtblau mit Sternen. Nur die Nacht hat einen dritten Wert.
     */
    private fun dayPalette(hour: Int): LongArray = when (hour) {
        in 5..8 -> longArrayOf(0xFFFFC58F, 0xFFE8935A)
        in 9..16 -> longArrayOf(0xFF8FD8FF, 0xFF4FA3D8)
        in 17..20 -> longArrayOf(0xFFFF8A3C, 0xFFC0616F)
        else -> longArrayOf(0xFF3D4A8C, 0xFF232B55, 0xFFFFF3B8)
    }

    /**
     * Kleid von JAHRESZEIT nach Kalendermonat (1-12): Körper, Schatten,
     * Streufarbe und der Rest, bei dem die Streufarbe erscheint.
     */
    private fun seasonPalette(month: Int): LongArray = when (month) {
        3, 4, 5 -> longArrayOf(0xFFFFB8D9, 0xFFE086B4, 0xFFFFFFFF, 5)
        // Sommer ist Sonne, nicht Himmel: Das naheliegende Türkis wäre
        // exakt die Himmelsfarbe der ersten Stufe gewesen — der Vogel
        // hätte drei Monate im Jahr nur aus Kontur und Auge bestanden.
        6, 7, 8 -> longArrayOf(0xFFFFC93C, 0xFFE09218, 0xFFFFF6C0, 7)
        // Aus demselben Grund trägt der Herbst Rost statt Sonnenuntergang:
        // 0xFFE08A3C lag sieben Farbschritte neben der Himmelsstufe bei
        // Score 20 — nah genug, um im Lauf zu verschwinden.
        9, 10, 11 -> longArrayOf(0xFFC2551E, 0xFF8E3A14, 0xFFFFB84E, 4)
        else -> longArrayOf(0xFFDCF3FF, 0xFFA8C8DE, 0xFFFFFFFF, 6)
    }

    /**
     * Deterministisches Rauschen über Feld und Zeitschritt. Bewusst kein
     * Zufall: Zwei Geräte, zwei Renderer und der Textur-Cache auf iOS
     * müssen beim selben Zeitschritt dasselbe Bild ergeben.
     */
    private fun noise(col: Int, row: Int, seed: Int): Int {
        var n = (col * 73856093) xor (row * 19349663) xor (seed * 83492791)
        n = (n xor (n ushr 13)) * 1274126177
        return abs(n xor (n ushr 16))
    }

    /** Leuchtfarbe von NEON: springt im Vierteltakt weiter. */
    private fun neonGlow(state: SkinState): Long {
        val cols = longArrayOf(0xFFFF3DCB, 0xFF3DF5E0, 0xFFC3FF3D)
        val step = floor(state.elapsed * 2.5f).toInt()
        return cols[((step % cols.size) + cols.size) % cols.size]
    }

    // ===== Farb-Werkzeug =====

    /** Die Standard-Schattierung des Spiels: untere rechte Hälfte dunkler. */
    private fun shaded(col: Int, row: Int, body: Long, shade: Long): Long =
        if (col + row > GRID * 1.15f) shade else body

    /** Lineare Mischung zweier ARGB-Farben; k = 0 ergibt [a], k = 1 ergibt [b]. */
    fun mix(a: Long, b: Long, k: Float): Long {
        val f = k.coerceIn(0f, 1f)
        var out = 0xFF000000L
        for (shift in intArrayOf(16, 8, 0)) {
            val ca = (a shr shift) and 0xFF
            val cb = (b shr shift) and 0xFF
            val v = (ca + (cb - ca) * f).toInt().coerceIn(0, 255).toLong()
            out = out or (v shl shift)
        }
        return out
    }

    /** HSL nach ARGB. [h] in Grad, [s] und [l] von 0 bis 1. */
    fun hsl(h: Float, s: Float, l: Float): Long {
        val hue = ((h % 360f) + 360f) % 360f
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((hue / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r1, g1, b1) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun byte(v: Float): Long = ((v + m) * 255f).toInt().coerceIn(0, 255).toLong()
        return 0xFF000000L or (byte(r1) shl 16) or (byte(g1) shl 8) or byte(b1)
    }
}
