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
 *   Bauplan verhindern soll. Die Farben der Kugel stehen trotzdem hier
 *   ([dotCell]): Der Kreis ist Zeichencode, seine Farben sind Daten.
 * - **Der Boden.** Aus demselben Grund: Die Bodenmuster der Welten
 *   zeichnet `:ui` mit der Routine des Spiels, hier steht nur, welcher
 *   Boden und wo ([ground], [groundY]).
 *
 * Seit dem Bevel-Look (docs/bevel-look.md) trägt die Karte dieselben
 * Kanten wie das Spiel: Dottie als Kugel, Wolken mit Licht und Schatten,
 * der Boden der Welt und ein Rahmen mit Kanten auf seinen Flächen.
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
     * Himmel und Wolken der gewählten Kulisse — der Grund, auf dem alles
     * andere liegt.
     *
     * Dass die Kulisse überhaupt auf die Karte kommt, ist der Sinn der
     * Sache: Sonst sähe niemand außer der Besitzerin, welche sie trägt.
     * Eine Kulisse ohne Wolken (WELTRAUM) lässt die Wolken einfach weg.
     *
     * Den Boden gibt es hier nicht als Rechtecke, sondern als [ground]:
     * Seit dem Bevel-Look trägt jede Welt ihr eigenes Bodenmuster
     * ([GroundStyle]), und das zeichnet `:ui` mit derselben Routine wie
     * die Spielwelt. Eine zweite Beschreibung der fünf Böden hier liefe
     * mit der ersten garantiert auseinander — derselbe Grund wie bei den
     * Pixelkreisen.
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
        return out
    }

    /**
     * Der Boden der Kulisse — null im WELTRAUM, der keinen hat. `:ui`
     * zeichnet ihn wie im Spiel im Muster von [Ground.style], mit [CELL]
     * als Rasterzelle und der Oberkante bei [groundY].
     *
     * [CELL] ist dabei keine neue Größe: Das Spiel rechnet seine Zelle
     * als `floor(Höhe / 220)`, und bei 1350 Pixeln Kartenhöhe sind das
     * genau diese sechs Pixel. Der Boden der Karte ist also Pixel für
     * Pixel der Boden, den ein gleich hohes Spielbild zeigt.
     */
    fun ground(scene: SceneId): Ground? = ScenePaint.of(scene).ground

    /** Die Bodenkante in Kartenpixeln (siehe [GROUND_TOP]). */
    fun groundY(): Float = HEIGHT * GROUND_TOP

    /**
     * Blockige Retro-Wolke, wie im Spiel aus drei Rechtecken gestapelt —
     * und wie im Spiel (`drawCloud`) mit Bevel ohne Kontur: erst der
     * kühle Schatten ([BevelPaint.cloudShade]) unten und rechts am
     * Sockel, dann das weiße Licht auf den Oberkanten der drei Stufen und
     * links am Sockel. Die Kante ist ein Feld breit, nicht die Wolkenzelle
     * — breiter wirkte die Wolke wie ein Kasten mit Rahmen.
     */
    private fun cloud(x: Float, y: Float, color: Long): List<CardRect> {
        val u = CELL * 4f
        val e = CELL
        val schatten = BevelPaint.cloudShade(color)
        return listOf(
            kante(x, y + u * 2, x + u * 14, y + u * 5, color),
            kante(x + u * 2, y, x + u * 9, y + u * 2, color),
            kante(x + u * 4, y - u * 1.5f, x + u * 8, y, color),
            CardRect(x, y + u * 5 - e, u * 14, e, schatten),
            CardRect(x + u * 14 - e, y + u * 2, e, u * 3, schatten),
            CardRect(x, y + u * 2, u * 2, e, WHITE),
            CardRect(x + u * 2, y, u * 2, e, WHITE),
            CardRect(x + u * 4, y - u * 1.5f, u * 4, e, WHITE),
            CardRect(x, y + u * 2, e, u * 3 - e, WHITE)
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
     * Die Farbe einer Zelle des Punkt-Körpers: Dottie als Kugel, genau
     * wie im Spiel (`drawTimingDot`) und in der Sammlung — die Skin-Farbe
     * aus [SkinPaint.cell] mit ihrer Stufe auf der Lichtachse
     * ([BevelPaint.kugel]), zum Glanz des Skins hin statt zu Weiß. Dunkle
     * Musterzellen bleiben unberührt; das entscheidet [BevelPaint.kugel]
     * selbst.
     */
    fun dotCell(skin: SkinId, state: SkinState, col: Int, row: Int): Long =
        BevelPaint.kugel(col, row, SkinPaint.cell(skin, col, row, state), SkinPaint.shine(skin, state))

    /**
     * Glanz, Auge und (wo nötig) dessen Kontur des Spiel-Punkts — alles
     * am Vogel außer seinem Körper, den der Pixelkreis malt.
     *
     * Wie im Spiel mit Blick nach rechts: im Glanzpunkt ein weißer Kern,
     * unter dem Augenweiß eine halbe Zeile [BevelPaint.EYE_EDGE] — der
     * Glanz liegt auf der Kugel, das Auge in ihr.
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
        out += feld(2.5f, 2.5f, 1f, 1f, WHITE)
        // Kontur nur, wo das Auge auf hellem Körper sonst verschwände
        // (wie im Spiel, siehe drawTimingDot).
        if (SkinPaint.needsEyeOutline(skin)) {
            out += feld(7f, 3f, 0.5f, 4f, OUTLINE)
            out += feld(7.5f, 2.5f, 3.5f, 0.5f, OUTLINE)
            out += feld(7.5f, 7f, 3.5f, 0.5f, OUTLINE)
        }
        out += feld(7.5f, 3f, 3.5f, 4f, WHITE)
        out += feld(7.5f, 6.5f, 3.5f, 0.5f, BevelPaint.EYE_EDGE)
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
     * Feldgröße, jedes Stück mit seiner Bevel-Kante ([partBevel]). Er
     * kommt zuletzt aufs Blatt: Er liegt über Kulisse UND Schrift, damit
     * an der Kante nichts durchscheint.
     *
     * Gebaut wird Stück für Stück ([CardStyle.parts]): erst die Fläche,
     * dann ihre Kante, dann das nächste Stück darüber. So liegen Zähne,
     * Perlen und Eckformen auf der Kante des Bandes darunter, wie sie auf
     * seiner Fläche liegen — die Kante läuft unter ihnen durch, statt um
     * jeden Zahn herum zu zacken.
     *
     * Die Kanten stehen nur hier und nicht in [CardStyle.frameRects]: Die
     * Feldtabelle gehört zu den Golden Vectors und bedient auch das
     * Game-Over-Panel und die Sammlung, deren Rahmen so klein sind, dass
     * ein Feld keine Kante tragen kann. Ohne die Kanten ist die Liste hier
     * genau die Feldtabelle mal [CELL], in derselben Reihenfolge.
     */
    fun frame(frame: CardFrame): List<CardRect> {
        val out = mutableListOf<CardRect>()
        CardStyle.parts(frame).forEach { part ->
            val felder = CardStyle.partRects(part, COLS, ROWS)
            felder.forEach {
                out += CardRect(it.col * CELL, it.row * CELL, it.cols * CELL, it.rows * CELL, it.tone.argb)
            }
            out += partBevel(part.tone.argb, felder)
        }
        return out
    }

    /**
     * Die Bevel-Kante eines Rahmenstücks, ein Feld breit (docs/bevel-look.md,
     * Abschnitt 0): helle Kante oben und links ([BevelPaint.light]),
     * dunkle unten und rechts ([BevelPaint.dark]).
     *
     * Gerechnet wird auf der Form des ganzen Stücks, nicht je Rechteck:
     * Ein Band besteht aus vier Rechtecken, die sich an den Ecken
     * überlappen, eine Perle oder Raute aus Zeilen. Je Rechteck gebevelt
     * zögen sich Kanten quer durch die Ecken und über jede Zeile. So
     * bekommt die Form ihre Kante genau an ihrem Umriss:
     *
     * - Fehlt oben oder links der Nachbar, ist das Feld Lichtkante; sonst,
     *   wenn er rechts oder unten fehlt, Schattenkante. Das Licht gewinnt,
     *   wo beides zutrifft — dieselbe Reihenfolge wie `bevelRect` (erst
     *   dunkel, dann hell darüber). Ein Band wird so ein erhabener Ring:
     *   außen oben und links hell, innen oben und links dunkel.
     * - Flächen unter drei Feldern bleiben flach: Ein Feld bekommt nur
     *   dann eine Kante, wenn die Form waagrecht UND senkrecht mindestens
     *   drei Felder am Stück durch es hindurch läuft. Die zwei Felder
     *   starken Bänder, Zähne und kleinen Nieten bleiben, wie sie sind.
     * - Die Kontur bekommt keine Kante, sie IST die Grenze zwischen den
     *   Flächen. Dasselbe gilt für jede andere dunkle Farbe
     *   ([BevelPaint.isDarkCell]).
     *
     * Die Kanten stehen als waagrechte Läufe gleicher Farbe, nicht als
     * einzelne Felder — sonst würde ein Rahmen zu ein paar tausend
     * Rechtecken.
     */
    private fun partBevel(tone: Long, felder: List<FrameRect>): List<CardRect> {
        if (felder.isEmpty() || BevelPaint.isDarkCell(tone)) return emptyList()
        val form = BooleanArray(COLS * ROWS)
        felder.forEach { f ->
            for (r in f.row until f.row + f.rows) {
                for (c in f.col until f.col + f.cols) form[r * COLS + c] = true
            }
        }
        fun drin(c: Int, r: Int): Boolean = c in 0 until COLS && r in 0 until ROWS && form[r * COLS + c]

        // Wie weit die Form durch jedes Feld läuft, waagrecht und senkrecht.
        val waag = IntArray(COLS * ROWS)
        val senk = IntArray(COLS * ROWS)
        for (r in 0 until ROWS) {
            var c = 0
            while (c < COLS) {
                if (!form[r * COLS + c]) { c++; continue }
                var e = c
                while (e < COLS && form[r * COLS + e]) e++
                for (k in c until e) waag[r * COLS + k] = e - c
                c = e
            }
        }
        for (c in 0 until COLS) {
            var r = 0
            while (r < ROWS) {
                if (!form[r * COLS + c]) { r++; continue }
                var e = r
                while (e < ROWS && form[e * COLS + c]) e++
                for (k in r until e) senk[k * COLS + c] = e - r
                r = e
            }
        }

        val hell = BevelPaint.light(tone)
        val dunkel = BevelPaint.dark(tone)
        // 0 heißt: keine Kante. Die Kantentöne sind deckend, also nie 0.
        fun kante(c: Int, r: Int): Long {
            if (!form[r * COLS + c]) return 0L
            if (waag[r * COLS + c] < 3 || senk[r * COLS + c] < 3) return 0L
            return when {
                !drin(c, r - 1) || !drin(c - 1, r) -> hell
                !drin(c + 1, r) || !drin(c, r + 1) -> dunkel
                else -> 0L
            }
        }

        val out = mutableListOf<CardRect>()
        val zeile = LongArray(COLS)
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) zeile[c] = kante(c, r)
            var c = 0
            while (c < COLS) {
                val k = zeile[c]
                var e = c + 1
                while (e < COLS && zeile[e] == k) e++
                if (k != 0L) out += CardRect(c * CELL, r * CELL, (e - c) * CELL, CELL, k)
                c = e
            }
        }
        return out
    }
}
