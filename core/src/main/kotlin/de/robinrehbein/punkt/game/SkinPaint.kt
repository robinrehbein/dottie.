package de.robinrehbein.punkt.game

import kotlin.math.abs
import kotlin.math.floor
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
    // Bestand
    KLASSIK, MINZE, LAVA, GOLD, FROST, SCHATTEN, PRISMA,

    // Gemustert
    BIENE, MELONE, PILZ, KOI, GALAXIE, KARO,

    // Bewegt (Zeit)
    REGENBOGEN, AURORA, MAGMA, NEON, CHROM,

    // Reagierend (Spielstand)
    CHAMAELEON, KOMBO, TINTE
}

/**
 * Der Lauf-Zustand, aus dem sich bewegte und reagierende Skins speisen.
 * Für Standbilder (Auswahl, Score-Karte) reicht der Standardwert.
 */
data class SkinState(
    val elapsed: Float = 0f,
    val score: Int = 0,
    val perfectStreak: Int = 0
)

/** Dauerhafte Bestleistungen, gegen die Freischaltungen geprüft werden. */
data class SkinStats(
    val bestScore: Int,
    val bestPerfectStreak: Int,
    val bestDailyStreak: Int
)

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
    fun hasTrail(id: SkinId): Boolean = id == SkinId.TINTE

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
        else -> 0
    }

    /** Hängt die Farbe an der Uhr (im Gegensatz zu Muster und Spielstand)? */
    fun isAnimated(id: SkinId): Boolean = when (id) {
        SkinId.REGENBOGEN, SkinId.AURORA, SkinId.MAGMA, SkinId.NEON, SkinId.CHROM -> true
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
        // Der Regenbogen ist der Abschluss der Sammlung: Er kommt erst,
        // wenn alle anderen Skins offen sind (er selbst zählt nicht mit,
        // sonst wäre die Bedingung zirkulär).
        SkinId.REGENBOGEN -> SkinId.entries.all { it == SkinId.REGENBOGEN || isUnlocked(it, stats) }
    }

    fun unlockedCount(stats: SkinStats): Int = SkinId.entries.count { isUnlocked(it, stats) }

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
