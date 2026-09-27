package de.robinrehbein.punkt.game

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Farbableitung für den Bevel-Look: Licht von oben links, eine helle
 * Kante oben und links, eine dunkle unten und rechts (docs/bevel-look.md,
 * Abschnitte 0 und 3).
 *
 * Nach demselben Muster wie [SkinPaint] und [TrapPaint]: Die Renderer
 * (Telefon, iOS, Uhr) rechnen keine Farben selbst, sie fragen hier nach.
 * Nur so sehen die Kanten auf allen Geräten gleich aus — und nur so sind
 * sie in Unit-Tests prüfbar. Die Kanten werden aus der Grundfarbe
 * abgeleitet statt einzeln festgelegt, damit sie automatisch für jede
 * Kulisse und jeden Skin passen.
 *
 * Alle Farben sind ARGB-Longs (0xAARRGGBB). Ergebnisse sind immer
 * deckend: Der Look kennt keine Transparenz-Effekte.
 */
object BevelPaint {

    /** Die Kontur aller Formen. Die dunkle Kante mischt zu ihr hin, nicht zu Schwarz. */
    const val OUTLINE: Long = 0xFF543847

    private const val WHITE: Long = 0xFFFFFFFF

    /**
     * Kanalweise Mischung von [a] nach [b] (t = 0 → a, t = 1 → b),
     * kaufmännisch gerundet. Alpha ist immer 0xFF — eine halb
     * durchsichtige Kante würde vor jedem Himmel anders aussehen.
     */
    fun mix(a: Long, b: Long, t: Float): Long {
        fun ch(shift: Int): Long {
            val x = ((a shr shift) and 0xFF).toFloat()
            val y = ((b shr shift) and 0xFF).toFloat()
            return floor(x + (y - x) * t + 0.5f).toLong().coerceIn(0L, 255L)
        }
        return (0xFFL shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Helle Kante oben und links: 35 % zu Weiß. */
    fun light(base: Long): Long = mix(base, WHITE, 0.35f)

    /**
     * Dunkle Kante unten und rechts: 30 % zur Kontur. Zur Kontur statt zu
     * Schwarz, damit der Schatten in derselben warmen Familie bleibt wie
     * die Umrisse — Schwarz wirkte auf Sand und Schnee wie ein Loch.
     */
    fun dark(base: Long): Long = mix(base, OUTLINE, 0.30f)

    /**
     * Ist das eine dunkle Musterzelle (Bienenstreifen, Kerne, Pupillen)?
     * Mittelwert der RGB-Kanäle unter 80. Solche Zellen bleiben ohne
     * Kante: Aufgehellt würden sie grau und das Muster zerfiele.
     */
    fun isDarkCell(color: Long): Boolean {
        val sum = ((color shr 16) and 0xFF) + ((color shr 8) and 0xFF) + (color and 0xFF)
        return sum / 3f < 80f
    }

    // ===== Dottie als Kugel (Abschnitt 4) =====

    /** Raster des Vogels, wie `GRID` im Renderer. */
    private const val GRID = 13

    /** Mitte des Rasters. */
    private const val MID = (GRID - 1) / 2f

    /** Radius der Kreismaske, wie `RR` in drawPixelCircle. */
    private const val RR = GRID / 2f - 0.25f

    /**
     * Farbe einer Zelle des Vogels mit Kugel-Stufen. [base] ist die Farbe
     * aus [SkinPaint.cell], [shine] die Glanzfarbe des Skins.
     *
     * Die Lage auf der Lichtachse `s = (dx + dy) / (RR · √2)` läuft von
     * −1 (ganz im Licht, oben links) bis +1 (ganz im Schatten). Zwei
     * Lichtstufen, eine unveränderte Mitte und eine Schattenstufe: mehr
     * Stufen würden bei 13 Zellen Durchmesser zum Verlauf, und Verläufe
     * gibt es im Pixel-Look nicht. Dunkle Musterzellen bleiben unberührt.
     */
    fun kugel(col: Int, row: Int, base: Long, shine: Long): Long {
        if (isDarkCell(base)) return base
        val s = ((col - MID) + (row - MID)) / (RR * SQRT2)
        return when {
            s < -0.42f -> mix(base, shine, 0.55f)
            s < -0.12f -> mix(base, shine, 0.22f)
            s > 0.55f -> mix(base, OUTLINE, 0.28f)
            else -> base
        }
    }

    private val SQRT2 = sqrt(2f)

    /**
     * Die halbe Zeile am unteren Rand des Augenweiß: Das Auge sitzt in
     * der Kugel, nicht auf ihr.
     */
    const val EYE_EDGE: Long = 0xFFD5DEE2

    // ===== Minen (Abschnitt 0, Punkt 3) =====

    /**
     * Farbe eines gesetzten Pixels von [TrapPaint.MINE] mit Kante. Fehlt
     * der Nachbar rechts oder unten, liegt der Pixel an der Schattenseite
     * (dunkel); fehlt er links oder oben, an der Lichtseite (hell). Der
     * Schatten gewinnt, wo beides zutrifft — sonst hätten die Zacken
     * unten rechts eine helle Spitze. Innen bleibt die Kugelfarbe.
     * [red] wählt die Töne des Lauflichts.
     *
     * Der Glanz (`W`) zählt als gesetzter Nachbar, bekommt selbst aber
     * weiter [TrapPaint.GLOSS] — das entscheidet der Renderer am Zeichen.
     */
    fun mineCell(row: Int, col: Int, red: Boolean): Long {
        val rows = TrapPaint.MINE
        fun set(r: Int, c: Int) = r in rows.indices && c in rows[r].indices && rows[r][c] != '.'
        val darkEdge = !set(row, col + 1) || !set(row + 1, col)
        val lightEdge = !set(row, col - 1) || !set(row - 1, col)
        return when {
            darkEdge -> if (red) TrapPaint.RED_DARK else TrapPaint.BALL_DARK
            lightEdge -> if (red) TrapPaint.RED_LIGHT else TrapPaint.BALL_LIGHT
            else -> if (red) TrapPaint.RED else TrapPaint.BALL
        }
    }

    // ===== Wolken und Galaxien =====

    /**
     * Unterkante einer Wolke: 30 % zu einem kühlen Blaugrau. Nicht zur
     * Kontur hin — Wolken haben keine, und ein warmer Schatten sähe
     * schmutzig aus.
     */
    fun cloudShade(cloud: Long): Long = mix(cloud, 0xFF7A9AB0, 0.3f)

    /**
     * Ton eines Galaxienarms an der Stelle [t] (0 = Kern, 1 = Spitze):
     * drei deckende Stufen statt stufenloser Transparenz. Rosé halb
     * durchsichtig über Dunkelblau wurde ein schmutziges Grau-Lila; so
     * bleibt jede Stufe eine klare Farbe. [sky] ist die aktuelle
     * Himmelsstufe, damit die Arme auf jeder Stufe in den Himmel laufen.
     */
    fun galaxyTone(arm: Long, sky: Long, t: Float): Long = when {
        t < 0.35f -> arm
        t < 0.7f -> mix(arm, sky, 0.2f)
        else -> mix(arm, sky, 0.45f)
    }

    /** Staub auf der inneren Armhälfte: deckend und hell statt Kernfarbe mit Alpha. */
    fun galaxyDust(arm: Long): Long = mix(arm, WHITE, 0.6f)

    /** Der Kern-Schimmer, deckend in der mittleren Armstufe. */
    fun galaxyGlow(arm: Long, sky: Long): Long = mix(arm, sky, 0.2f)
}
