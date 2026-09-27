package de.robinrehbein.punkt.game

import kotlin.math.pow
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
     * Mischung von [a] nach [b] (t = 0 → a, t = 1 → b) im Farbraum Oklab,
     * Schritt für Schritt so, wie Compose `lerp(Color, Color, Float)`
     * rechnet: Mit dieser Funktion sind die Zielbilder
     * (docs/bevel-mockups/ziel/) gerendert. Kanalweise in sRGB gemischt
     * lägen die abgeleiteten Töne um bis zu 39 pro Kanal daneben, meist
     * etwas zu grau; in Oklab bleibt die Sättigung auf dem Weg zu Weiß
     * oder zur Kontur erhalten.
     *
     * Nachgebaut statt aus Compose geholt, damit :core ohne Compose
     * auskommt und Uhr und iOS dieselben Werte bekommen. Nachgebaut bis
     * in die Float-Rundung: dieselben Matrizen, dieselbe Reihenfolge der
     * Rechenschritte, dieselbe schnelle Kubikwurzel und die Half-Float-
     * Speicherung, in der Compose eine Oklab-Farbe hält. Mit einer
     * „sauberen“ Oklab-Formel kippten rund 6 % der Töne an
     * Rundungsgrenzen um eine Stufe. `BevelMixTest` in :ui hält das
     * gegen Compose selbst. Alpha ist immer 0xFF — eine halb
     * durchsichtige Kante würde vor jedem Himmel anders aussehen.
     */
    fun mix(a: Long, b: Long, t: Float): Long {
        val from = toOklab(a)
        val to = toOklab(b)
        // Wie androidx.compose.ui.util.lerp: (1 − t) · a + t · b.
        fun lerp(shift: Int): Float {
            val x = halfToFloat(((from shr shift) and 0xFFFF).toInt())
            val y = halfToFloat(((to shr shift) and 0xFFFF).toInt())
            return half((1f - t) * x + t * y)
        }
        return fromOklab(lerp(32), lerp(16), lerp(0))
    }

    /**
     * sRGB → Oklab wie Compose: Kanal linearisieren, über XYZ (D50, der
     * Verbindungsraum von Compose) in den LMS-Raum, Kubikwurzel, dann
     * nach Lab. Jede Komponente wird als Half-Float gespeichert, wie in
     * Compose — und genau deshalb passen alle drei in einen Long (L in
     * den Bits 32–47, a in 16–31, b in 0–15). Das hält [mix] frei von
     * Allokationen: Es läuft in jedem Frame für Kugel, Kanten und
     * Galaxien.
     */
    private fun toOklab(c: Long): Long {
        val r = EOTF[((c shr 16) and 0xFF).toInt()]
        val g = EOTF[((c shr 8) and 0xFF).toInt()]
        val b = EOTF[(c and 0xFF).toInt()]
        val x = mul(SRGB_TO_XYZ, 0, r, g, b)
        val y = mul(SRGB_TO_XYZ, 1, r, g, b)
        val z = mul(SRGB_TO_XYZ, 2, r, g, b)
        val l = fastCbrt(mul(XYZ_TO_LMS, 0, x, y, z))
        val m = fastCbrt(mul(XYZ_TO_LMS, 1, x, y, z))
        val s = fastCbrt(mul(XYZ_TO_LMS, 2, x, y, z))
        val hl = halfBits(mul(LMS_TO_LAB, 0, l, m, s).coerceIn(0f, 1f)).toLong()
        val ha = halfBits(mul(LMS_TO_LAB, 1, l, m, s).coerceIn(-0.5f, 0.5f)).toLong()
        val hb = halfBits(mul(LMS_TO_LAB, 2, l, m, s).coerceIn(-0.5f, 0.5f)).toLong()
        return (hl shl 32) or (ha shl 16) or hb
    }

    /** Oklab → sRGB wie Compose: geklemmt, zurück über LMS und XYZ, auf 8 Bit gerundet. */
    private fun fromOklab(lab: Float, labA: Float, labB: Float): Long {
        val cl = lab.coerceIn(0f, 1f)
        val ca = labA.coerceIn(-0.5f, 0.5f)
        val cb = labB.coerceIn(-0.5f, 0.5f)
        val l0 = mul(LAB_TO_LMS, 0, cl, ca, cb)
        val m0 = mul(LAB_TO_LMS, 1, cl, ca, cb)
        val s0 = mul(LAB_TO_LMS, 2, cl, ca, cb)
        val l = l0 * l0 * l0
        val m = m0 * m0 * m0
        val s = s0 * s0 * s0
        val x = mul(LMS_TO_XYZ, 0, l, m, s)
        val y = mul(LMS_TO_XYZ, 1, l, m, s)
        val z = mul(LMS_TO_XYZ, 2, l, m, s)
        fun ch(row: Int): Long {
            val v = oetf(mul(XYZ_TO_SRGB, row, x, y, z)).coerceIn(0f, 1f)
            return (v * 255f + 0.5f).toInt().toLong()
        }
        return (0xFFL shl 24) or (ch(0) shl 16) or (ch(1) shl 8) or ch(2)
    }

    /**
     * Zeile [row] einer 3×3-Matrix mal (v0, v1, v2). Die Matrizen liegen
     * spaltenweise wie in Compose, und die Summe läuft in derselben
     * Reihenfolge — sonst weicht die Float-Rundung ab.
     */
    private fun mul(m: FloatArray, row: Int, v0: Float, v1: Float, v2: Float): Float =
        m[row] * v0 + m[row + 3] * v1 + m[row + 6] * v2

    /**
     * [eotf] für alle 256 Kanalwerte, einmal vorberechnet: Ein 8-Bit-Kanal
     * kann nur diese Werte haben, und so spart jede Mischung sechs pow.
     */
    private val EOTF = FloatArray(256) { eotf(it.toFloat() / 255f) }

    /** sRGB-Kurve rückwärts (Kanal → linear), in Double wie in Compose. */
    private fun eotf(v: Float): Float {
        val x = v.toDouble().coerceIn(0.0, 1.0)
        val r = if (x >= 0.04045) ((1 / 1.055) * x + 0.055 / 1.055).pow(2.4) else (1 / 12.92) * x
        return r.toFloat()
    }

    /** sRGB-Kurve vorwärts (linear → Kanal), in Double wie in Compose. */
    private fun oetf(v: Float): Float {
        val x = v.toDouble()
        val r = if (x >= 0.04045 * (1 / 12.92)) (x.pow(1 / 2.4) - 0.055 / 1.055) / (1 / 1.055) else x / (1 / 12.92)
        return r.coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Die schnelle Kubikwurzel aus Compose (`fastCbrt`): Startwert per
     * Bit-Trick, dann zwei Newton-Schritte. Nicht bitgleich mit einer
     * exakten Wurzel, deshalb nachgebaut.
     */
    private fun fastCbrt(x: Float): Float {
        val bits = x.toRawBits().toLong() and 0x1FFFFFFFFL
        var y = Float.fromBits(709952852 + (bits / 3).toInt())
        y -= (y - x / (y * y)) * (1f / 3f)
        y -= (y - x / (y * y)) * (1f / 3f)
        return y
    }

    /** Rundet auf ein Half-Float und zurück (siehe [halfBits]). */
    private fun half(v: Float): Float = halfToFloat(halfBits(v))

    /**
     * Die Bits des Half-Floats zu [v], genau wie der Farb-Konstruktor von
     * Compose sie bildet: 10 Bit Mantisse, halbe Stufen runden nach oben.
     */
    private fun halfBits(v: Float): Int {
        val bits = v.toRawBits()
        val sign = bits ushr 31
        var e = (bits ushr 23) and 0xFF
        var mant = bits and 0x7FFFFF
        var outE = 0
        var outM = 0
        var h: Int
        if (e == 0xFF) {
            outE = 31
            outM = if (mant != 0) 0x200 else 0
            h = (sign shl 15) or (outE shl 10) or outM
        } else {
            e = e - 127 + 15
            if (e >= 31) {
                outE = 0x31
                h = (sign shl 15) or (outE shl 10) or outM
            } else if (e <= 0) {
                if (e >= -10) {
                    mant = (mant or 0x800000) shr (1 - e)
                    if (mant and 0x1000 != 0) mant += 0x2000
                    outM = mant shr 13
                }
                h = (sign shl 15) or (outE shl 10) or outM
            } else {
                outE = e
                outM = mant shr 13
                h = (sign shl 15) or (outE shl 10) or outM
                if (mant and 0x1000 != 0) h = ((outE shl 10) or outM) + 1 or (sign shl 15)
            }
        }
        return h and 0xFFFF
    }

    private fun halfToFloat(h: Int): Float {
        val sign = if (h and 0x8000 != 0) -1f else 1f
        val e = (h shr 10) and 0x1F
        val m = h and 0x3FF
        return when (e) {
            0 -> sign * m * HALF_SUBNORMAL_STEP
            31 -> if (m == 0) sign * Float.POSITIVE_INFINITY else Float.NaN
            else -> sign * Float.fromBits(((e - 15 + 127) shl 23) or (m shl 13))
        }
    }

    private const val HALF_SUBNORMAL_STEP = 5.9604645e-8f

    // Die Matrizen aus Compose 1.7 (spaltenweise), ausgelesen aus
    // ColorSpaces.Srgb.adapt(D50) und ColorSpaces.Oklab. Compose rechnet
    // sie selbst in Float aus; hier stehen die fertigen Werte, damit
    // jedes Bit stimmt.
    private val SRGB_TO_XYZ = floatArrayOf(
        0.43602175f, 0.22247513f, 0.013928129f,
        0.38510883f, 0.71690667f, 0.09710153f,
        0.14308129f, 0.060618237f, 0.7141588f,
    )
    private val XYZ_TO_SRGB = floatArrayOf(
        3.1343124f, -0.97874373f, 0.07194816f,
        -1.6172329f, 1.916114f, -0.2289863f,
        -0.490686f, 0.033449814f, 1.4052706f,
    )
    private val XYZ_TO_LMS = floatArrayOf(
        0.7706913f, 0.0056468793f, 0.04636898f,
        0.34922713f, 0.93706733f, 0.252901f,
        -0.11203287f, 0.069691114f, 0.8516457f,
    )
    private val LMS_TO_LAB = floatArrayOf(
        0.21045426f, 1.9779985f, 0.025904037f,
        0.7936178f, -2.4285922f, 0.78277177f,
        -0.004072047f, 0.4505937f, -0.80867577f,
    )
    private val LMS_TO_XYZ = floatArrayOf(
        1.2886301f, -0.002604977f, -0.06938761f,
        -0.53787726f, 1.0923469f, -0.29509315f,
        0.21353269f, -0.08973064f, 1.1892171f,
    )
    private val LAB_TO_LMS = floatArrayOf(
        1.0000001f, 1.0f, 1.0000001f,
        0.3963378f, -0.105561346f, -0.08948418f,
        0.21580376f, -0.06385418f, -1.2914855f,
    )

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

    /**
     * [mineCell] für jedes Pixel der Mine, einmal vorberechnet: Das
     * Ergebnis hängt nur an Zeile, Spalte und [red], und die Renderer
     * fragen es in jedem Frame für jede Mine der Kette ab — auf der Uhr
     * genauso. Die Nachbarsuche im String-Sprite läuft so nur einmal.
     * Für `.` steht 0 (nicht gezeichnet); `W` bekommt den Wert eines
     * Kugelpixels, der Renderer nimmt dort [TrapPaint.GLOSS].
     */
    fun mineEdge(row: Int, col: Int, red: Boolean): Long =
        (if (red) MINE_EDGE_RED else MINE_EDGE)[row][col]

    private fun mineTable(red: Boolean): Array<LongArray> = Array(TrapPaint.MINE.size) { r ->
        val row = TrapPaint.MINE[r]
        LongArray(row.length) { c -> if (row[c] == '.') 0L else mineCell(r, c, red) }
    }

    private val MINE_EDGE: Array<LongArray> = mineTable(red = false)
    private val MINE_EDGE_RED: Array<LongArray> = mineTable(red = true)

    // ===== Wolken und Galaxien =====

    /**
     * Unterkante einer Wolke: 30 % zu einem kühlen Blaugrau. Nicht zur
     * Kontur hin — Wolken haben keine, und ein warmer Schatten sähe
     * schmutzig aus.
     */
    fun cloudShade(cloud: Long): Long = mix(cloud, 0xFF7A9AB0, 0.3f)

    /**
     * Der Himmel, gegen den die Galaxienarme verblassen: die erste
     * Weltraum-Stufe, fest. So ist es im Prototyp, mit dem die Zielbilder
     * gerendert sind. Gegen die jeweils aktuelle Stufe gemischt wurden die
     * äußeren Arme auf den helleren Stufen blass und milchig; mit dem
     * festen Nachtblau bleiben sie auf jeder Stufe gleich satt.
     */
    const val GALAXY_SKY: Long = 0xFF0E1430

    /**
     * Ton eines Galaxienarms an der Stelle [t] (0 = Kern, 1 = Spitze):
     * drei deckende Stufen statt stufenloser Transparenz. Rosé halb
     * durchsichtig über Dunkelblau wurde ein schmutziges Grau-Lila; so
     * bleibt jede Stufe eine klare Farbe.
     */
    fun galaxyTone(arm: Long, t: Float): Long = when {
        t < 0.35f -> arm
        t < 0.7f -> mix(arm, GALAXY_SKY, 0.2f)
        else -> mix(arm, GALAXY_SKY, 0.45f)
    }

    /** Staub auf der inneren Armhälfte: deckend und hell statt Kernfarbe mit Alpha. */
    fun galaxyDust(arm: Long): Long = mix(arm, WHITE, 0.6f)

    /** Der Kern-Schimmer, deckend in der mittleren Armstufe. */
    fun galaxyGlow(arm: Long): Long = mix(arm, GALAXY_SKY, 0.2f)
}
