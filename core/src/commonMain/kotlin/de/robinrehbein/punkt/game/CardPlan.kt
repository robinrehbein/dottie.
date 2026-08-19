package de.robinrehbein.punkt.game

import kotlin.math.roundToInt

/**
 * Ein gefülltes Rechteck der Score-Karte, in Kartenpixeln.
 *
 * Der Unterschied zu [FrameRect] ist die Einheit: Der Rahmen rechnet in
 * Feldern, weil dieselbe Tabelle auf dem winzigen Game-Over-Panel und auf
 * dem 1080 Pixel breiten Blatt liegt. Die Karte dagegen hat genau eine
 * Größe ([CardPlan.WIDTH] × [CardPlan.HEIGHT]), also darf sie in Pixeln
 * rechnen — und muss es sogar, weil ihre Zeilen an Anteilen der Bildhöhe
 * hängen und nicht an Feldern.
 *
 * Die Farbe steht als ARGB-Long und nicht als Compose-Farbe: [CardPlan]
 * gehört wie [SkinPaint] und [ScenePaint] zu den Daten, nicht zum
 * Zeichencode.
 */
data class CardRect(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val color: Long
)

/**
 * Die Medaille auf der Karte, in zwei Portionen: [ribbon] liegt unter der
 * Münze, [face] darüber. Dazwischen zeichnet der Renderer den Pixelkreis —
 * die eine Form, die hier NICHT als Rechteckliste steht (siehe [CardPlan]).
 */
data class CardMedal(
    val tier: MedalId,
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val ribbon: List<CardRect>,
    val face: List<CardRect>
)

/**
 * Das Beiname-Schild: drei Rechtecke mit abgeschrägten Ecken und die
 * Baseline, auf der der Titel darin sitzt.
 */
data class CardPlaque(
    val rects: List<CardRect>,
    val baseline: Float
)

/**
 * Der Bauplan der geteilten Score-Karte: jedes Rechteck des Bildes als
 * Datensatz, in Zeichenreihenfolge.
 *
 * Der Grund ist derselbe wie bei [CardStyle.frameRects] und
 * [ScenePaint.ROCK_PARTS]. Bis v2.26 zeichnete die Karte allein
 * `android.graphics` in `:app` — auf dem iPhone gab es sie deshalb gar
 * nicht, und der naheliegende Weg dorthin (ein zweiter Port) hätte
 * dieselbe Karte ein zweites Mal beschrieben. Stattdessen steht die
 * Geometrie jetzt hier, einmal, und `:ui` malt sie auf beiden Plattformen
 * mit derselben Compose-Routine aus.
 *
 * Was hier NICHT steht, ist genauso wichtig:
 *
 * - **Texte.** Zeilen, Schriftgrade und Baselines gibt es hier als Maße
 *   ([SCORE], [SCORE_SIZE] …), die Zeichenketten selbst kommen von außen.
 *   Sie hängen an der Sprache der Oberfläche, und die kennt `:core` nicht.
 * - **Die beiden Pixelkreise** (Punkt und Münze). Die zeichnet `:ui` mit
 *   derselben Routine, mit der auch die Spielwelt ihren Vogel zeichnet —
 *   ein zweites Mal beschrieben wäre sie genau der Fehler, den dieser
 *   Bauplan verhindern soll.
 *
 * Alle Maße sind so übernommen, wie die Karte sie seit ihrer Einführung
 * hatte. Wer eine Zahl hier ändert, ändert die Karte von Leuten, die
 * nichts dafür getan haben — und ein Test in `:core` hält sie fest.
 */
object CardPlan {

    /** Die Kantenlänge des Blattes in Pixeln. */
    const val WIDTH = 1080
    const val HEIGHT = 1350

    /** Ein Feld des Pixelrasters. Alles auf der Karte sitzt auf ganzen Feldern. */
    const val CELL = 6f

    /** Dieselbe Kantenlänge in Feldern — damit rechnet der Rahmen. */
    const val COLS = 180
    const val ROWS = 225

    /**
     * Die Bodenkante als Anteil der Bildhöhe. Sie liegt bei 86 % und
     * nicht bei den 88 % der Spielwelt ([ScenePaint.GROUND_TOP]), weil
     * unten auf der Karte noch die Aufforderung steht.
     */
    const val GROUND_TOP = 0.86f

    /** Wo die feststehenden Zeilen sitzen — Anteile der Bildhöhe. */
    const val SCORE = 0.55f

    /**
     * Mittig zwischen Score-Zahl und REKORD-Zeile, damit PUNKTE weder an
     * der Zahl noch am REKORD klebt.
     */
    const val POINTS = 0.615f
    const val SCENE = 0.645f
    const val RECORD = 0.68f

    /**
     * Die Münz-Mitte: so, dass das Band oben nicht in die REKORD-Zeile
     * ragt und die Münze über der Grasnarbe endet.
     */
    const val MEDAL = 0.79f
    const val MEDAL_RADIUS = 62f

    /** Der Abstand, um den der Daily-Hinweis den Beinamen nach unten schiebt. */
    const val DAILY_GAP = 0.04f

    /** Die Schriftgrade der festen Zeilen (Titel und Beiname siehe [CardStyle.layout]). */
    const val EPITHET_SIZE = 52f
    const val SCORE_SIZE = 320f
    const val POINTS_SIZE = 60f
    const val SCENE_SIZE = 34f
    const val RECORD_SIZE = 68f
    const val CHALLENGE_SIZE = 72f

    /** Der Schatten sitzt um ein Zwanzigstel des Schriftgrads versetzt. */
    const val SHADOW = 0.05f

    /**
     * Die einzige Farbe, die die Karte selbst führt. Alles andere kommt
     * aus den Rollen, die es ohnehin gibt: die Kontur und die Bänder aus
     * [FrameTone], die Münze aus [MedalPaint], Himmel und Boden aus
     * [ScenePaint], der Punkt aus [SkinPaint].
     */
    const val WHITE = 0xFFFFFFFF

    /** Die Kontur des ganzen Spiels — dieselbe, mit der jeder Rahmen anfängt. */
    private val OUTLINE = FrameTone.OUTLINE.argb

    /** Die Mitte des Blattes — fast alles auf der Karte ist zentriert. */
    const val CENTER_X = WIDTH / 2f

    /** Nächste Rasterlinie. */
    fun raster(px: Float): Float = (px / CELL).roundToInt() * CELL

    /**
     * Ein Rechteck aus seinen vier Kanten. Die Karte hat ihre Maße immer
     * so gerechnet (`links, oben, rechts, unten`), und Breite als
     * Differenz der Kanten ist nicht dasselbe wie Breite aus der
     * Multiplikation: In Gleitkomma trennen die beiden Wege ein
     * Millionstel Pixel. Sichtbar ist das nie — aber ein Test, der den
     * Bestand nachrechnet, sieht es, und dann ist unklar, ob sich etwas
     * bewegt hat. Also wird hier gerechnet wie eh und je.
     */
    private fun kante(l: Float, t: Float, r: Float, b: Float, color: Long) =
        CardRect(l, t, r - l, b - t, color)

    /**
     * Himmel, Wolken und Boden der gewählten Kulisse — der Grund, auf dem
     * alles andere liegt.
     *
     * Dass die Kulisse überhaupt auf die Karte kommt, ist der Sinn der
     * Sache: Sonst sähe niemand außer der Besitzerin, welche sie trägt.
     * Eine Kulisse ohne Wolken oder ohne Boden (WELTRAUM) lässt die
     * jeweiligen Rechtecke einfach weg.
     */
    fun background(scene: SceneId, score: Int): List<CardRect> {
        val kulisse = ScenePaint.of(scene)
        val out = mutableListOf<CardRect>()
        out += CardRect(
            0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
            kulisse.sky[SkinPaint.skyStage(score)]
        )
        kulisse.cloud?.let { wolke ->
            out += cloud(WIDTH * 0.08f, HEIGHT * 0.10f, wolke)
            out += cloud(WIDTH * 0.62f, HEIGHT * 0.17f, wolke)
        }
        kulisse.ground?.let { boden ->
            val oben = HEIGHT * GROUND_TOP
            out += kante(0f, oben, WIDTH.toFloat(), HEIGHT.toFloat(), boden.sand)
            out += kante(0f, oben, WIDTH.toFloat(), oben + CELL * 5, boden.turfDark)
            var x = 0f
            while (x < WIDTH) {
                out += kante(x, oben, x + CELL * 5, oben + CELL * 4, boden.turfLight)
                x += CELL * 10
            }
            out += kante(0f, oben - CELL, WIDTH.toFloat(), oben, OUTLINE)
        }
        return out
    }

    /** Blockige Retro-Wolke, wie im Spiel aus drei Rechtecken gestapelt. */
    private fun cloud(x: Float, y: Float, color: Long): List<CardRect> {
        val u = CELL * 4f
        return listOf(
            kante(x, y + u * 2, x + u * 14, y + u * 5, color),
            kante(x + u * 2, y, x + u * 9, y + u * 2, color),
            kante(x + u * 4, y - u * 1.5f, x + u * 8, y, color)
        )
    }

    /**
     * Das dunkle Schild hinter dem Beinamen.
     *
     * Es ist kein Schmuck, sondern Lesbarkeit: In dieser Höhe stehen die
     * Wolken, und heller Text auf heller Wolke ist genau die Stelle, an
     * der eine geteilte Karte unleserlich wird.
     *
     * [baseline] ist die Zeile, auf der der Beiname stünde, [halfText] die
     * halbe gemessene Textbreite. Die Box rückt zwei Felder nach unten,
     * weil sie sonst vom Schatten des Titels gestreift wird; die Ecken
     * sind angeschrägt — die Treppen-Form der Overlays, dafür drei
     * Rechtecke statt eines nackten, ohne die Wolke dahinter zu
     * übermalen.
     */
    fun plaque(baseline: Float, halfText: Float): CardPlaque {
        val halb = raster(halfText + CELL * 5)
        val oben = raster(baseline - CELL * 6)
        val unten = oben + CELL * 10
        return CardPlaque(
            rects = listOf(
                kante(CENTER_X - halb + CELL, oben, CENTER_X + halb - CELL, unten, OUTLINE),
                kante(CENTER_X - halb, oben + CELL, CENTER_X - halb + CELL, unten - CELL, OUTLINE),
                kante(CENTER_X + halb - CELL, oben + CELL, CENTER_X + halb, unten - CELL, OUTLINE)
            ),
            // Bytesized läuft mit den Großbuchstaben bis 2/16 UNTER die
            // Baseline — der Text sitzt darum optisch zwei Zellen höher,
            // als die Baseline verrät.
            baseline = oben + CELL * 6
        )
    }

    /**
     * Glanz, Auge und (wo nötig) dessen Kontur des Spiel-Punkts — alles
     * am Vogel außer seinem Körper, den der Pixelkreis malt.
     *
     * Bewegte Skins stehen auf dem Bild still: Ein geteilter Screenshot
     * ist ein Standbild, also bleibt [SkinState.elapsed] bei 0. Stunde und
     * Monat trägt der Aufrufer bei, damit TAGESZEIT und JAHRESZEIT nicht
     * ewig Mittag im Juni zeigen.
     */
    fun dotDetails(skin: SkinId, state: SkinState, centerY: Float, radius: Float): List<CardRect> {
        val u = radius * 2f / 13f
        fun feld(col: Float, row: Float, cols: Float, rows: Float, color: Long) = kante(
            CENTER_X - radius + col * u, centerY - radius + row * u,
            CENTER_X - radius + (col + cols) * u, centerY - radius + (row + rows) * u, color
        )
        val out = mutableListOf<CardRect>()
        out += feld(2.5f, 2.5f, 2f, 2f, SkinPaint.shine(skin, state))
        // Kontur nur, wo das Auge auf hellem Körper sonst verschwände
        // (wie im Spiel, siehe drawTimingDot).
        if (SkinPaint.needsEyeOutline(skin)) {
            out += feld(7f, 3f, 0.5f, 4f, OUTLINE)
            out += feld(7.5f, 2.5f, 3.5f, 0.5f, OUTLINE)
            out += feld(7.5f, 7f, 3.5f, 0.5f, OUTLINE)
        }
        out += feld(7.5f, 3f, 3.5f, 4f, WHITE)
        out += feld(9.5f, 4f, 1.5f, 2f, OUTLINE)
        return out
    }

    /**
     * Die Medaille zu einem Score — null unterhalb von Bronze. Schwellen
     * und Münzfarben kommen aus [MedalPaint]: Die Karte hatte sie bis
     * v2.26 ein zweites Mal aufgeschrieben.
     */
    fun medal(score: Int): CardMedal? {
        val stufe = MedalPaint.forScore(score) ?: return null
        val cy = HEIGHT * MEDAL
        val radius = MEDAL_RADIUS
        val u = radius * 2f / 10f
        fun block(c: Float, r: Float, w: Float, h: Float, color: Long) = kante(
            CENTER_X - 8f * u + c * u, cy - radius - 4.5f * u + r * u,
            CENTER_X - 8f * u + (c + w) * u, cy - radius - 4.5f * u + (r + h) * u, color
        )
        val links = listOf(3.5f to 0f, 4.5f to 1.5f, 5.5f to 3f)
        val rechts = listOf(9.5f to 0f, 8.5f to 1.5f, 7.5f to 3f)
        val band = mutableListOf<CardRect>()
        for ((c, r) in links + rechts) band += block(c - 0.5f, r - 0.5f, 3f, 2.5f, OUTLINE)
        for ((c, r) in links) band += block(c, r, 2f, 1.5f, MedalPaint.RIBBON)
        for ((c, r) in rechts) band += block(c, r, 2f, 1.5f, MedalPaint.RIBBON_SHADE)

        val schatten = MedalPaint.shade(stufe)
        val cu = radius * 2f / 13f
        fun praegung(c: Float, r: Float, w: Float, h: Float, color: Long) = kante(
            CENTER_X - radius + c * cu, cy - radius + r * cu,
            CENTER_X - radius + (c + w) * cu, cy - radius + (r + h) * cu, color
        )
        val muenze = listOf(
            praegung(5f, 5f, 3f, 3f, schatten),
            praegung(5.5f, 3.5f, 2f, 2f, schatten),
            praegung(5.5f, 7.5f, 2f, 2f, schatten),
            praegung(3.5f, 5.5f, 2f, 2f, schatten),
            praegung(7.5f, 5.5f, 2f, 2f, schatten),
            praegung(2.5f, 2.5f, 2f, 2f, MedalPaint.GLINT)
        )
        return CardMedal(stufe, CENTER_X, cy, radius, band, muenze)
    }

    /**
     * Der Rahmen als Pixel-Rechtecke — [CardStyle.frameRects] mal
     * Feldgröße, sonst nichts. Er kommt zuletzt aufs Blatt: Er liegt über
     * Kulisse UND Schrift, damit an der Kante nichts durchscheint.
     */
    fun frame(frame: CardFrame): List<CardRect> =
        CardStyle.frameRects(frame, COLS, ROWS).map {
            CardRect(it.col * CELL, it.row * CELL, it.cols * CELL, it.rows * CELL, it.tone.argb)
        }
}
