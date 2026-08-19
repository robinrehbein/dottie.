package de.robinrehbein.punkt.game

import kotlin.math.min

/**
 * Rahmen und Beiname der Score-Karte — was die geteilte Karte über die
 * Spielerin verrät, das die Zahl darauf nicht sagt.
 *
 * Beides sind reine Ableitungen aus [SkinStats] und stehen deshalb hier
 * und nicht im Zeichencode: Android rendert ein PNG, die PWA und iOS
 * ziehen später nach, und alle drei müssen bei demselben Spielstand
 * denselben Rahmen und denselben Titel zeigen. Der Zeichencode fragt nur
 * ab, wie er es bei [SkinPaint.medalTier] auch tut.
 *
 * Die Trennung der beiden Achsen ist Absicht: Der Rahmen hängt an der
 * Sammlung (was man besitzt), der Beiname an dem, was man geleistet hat.
 * Zwei Karten mit demselben Rahmen können also sehr verschiedene Titel
 * tragen — und genau das macht die Karte lesbar.
 *
 * Seit v2.26 steht hier nicht nur, WELCHEN Rahmen jemand trägt, sondern
 * auch, wie er aussieht ([CardStyle.parts]). Der Grund ist derselbe wie
 * bei [ScenePaint.ROCK_PARTS]: Es gibt zwei Renderer — die geteilte
 * Karte (`android.graphics`) und das Game-Over-Panel in `:ui`, das auf
 * Android wie auf iOS läuft. Eine Form, die jeder für sich nachzeichnet,
 * läuft auseinander.
 */
enum class CardFrame {
    /** Eine einzelne dunkle Kante — der Stand, mit dem jeder anfängt. */
    SCHLICHT,

    /** Doppellinie mit farbigem Zwischenband und Ecknieten. */
    DOPPELLINIE,

    /** Breiter Rahmen mit Zinnenkranz und gestuften Eckklötzen. */
    ZINNEN,

    /** Vier Lagen, eingelegte Farben und Eckrosetten. */
    PRACHT
}

/**
 * Die Farbrollen eines Rahmens. Sie stehen als Rolle und nicht als
 * Farbwert im Muster, damit die Tabelle lesbar bleibt ("hier liegt Gold",
 * nicht "hier liegt 0xFFFFE95E") — der Wert hängt aber an der Rolle und
 * nicht am Renderer, sonst hätte jede Plattform ihre eigene Palette.
 *
 * Es sind genau die fünf Farben, mit denen die Karte schon vorher
 * gezeichnet hat; ein Rahmen erfindet keine sechste.
 */
enum class FrameTone(val argb: Long) {
    /** Die Kontur des ganzen Spiels — jeder Rahmen fängt damit an. */
    OUTLINE(0xFF543847),

    /** Das Orange der Aufforderung. */
    ACCENT(0xFFFF8A3C),

    /** Das Rekord-Gelb. */
    GOLD(0xFFFFE95E),

    /** Das Türkis des Tageshimmels — die eingelegte Farbe. */
    INLAY(0xFF4EC0CA),

    /** Perlweiß, die hellste Farbe der Karte. */
    PEARL(0xFFF7F3EE)
}

/**
 * Die vier Formen, aus denen jeder Rahmen besteht. Mehr braucht es nicht:
 * Was ein Rahmen zu sagen hat, sagt er über Breite, Farbe und Takt.
 */
enum class FrameShape {
    /** Ein umlaufendes Band. */
    BAND,

    /** Quadratische Zähne im Takt auf allen vier Kanten. */
    ZAEHNE,

    /** Dasselbe Quadrat in allen vier Ecken. */
    ECKBLOCK,

    /** Dieselbe Ecke als Rhombus. */
    ECKRAUTE
}

/**
 * Ein Stück Rahmen — die Muster-Spezifikation, aus der beide Renderer
 * (die geteilte Karte in `android.graphics`, das Game-Over-Panel in
 * Compose) dieselben Rechtecke ableiten.
 *
 * Alle Maße zählen in FELDERN, nie in Pixeln: Die Karte rechnet mit 6 px
 * je Feld, das Game-Over-Panel mit gut einem dp — dieselbe Tabelle, zwei
 * Zellgrößen. Ein Rahmen in absoluten Pixeln wäre auf dem Panel entweder
 * ein Balken oder unsichtbar.
 *
 * [inset] ist der Abstand vom Blattrand, [size] die Bandstärke bzw. die
 * Kantenlänge der Form, [step] der Takt der Zahnreihen und [phase] deren
 * Versatz — zwei Reihen mit gleichem Takt und verschiedenem Versatz
 * ergeben eine Treppe.
 */
data class FramePart(
    val shape: FrameShape,
    val inset: Int,
    val size: Int,
    val tone: FrameTone,
    val step: Int = 0,
    val phase: Int = 0
)

/**
 * Ein fertiges Rechteck im Feldraster, wie es aus [CardStyle.frameRects]
 * fällt. Die Renderer multiplizieren nur noch mit ihrer Zellgröße und
 * füllen — dieselbe Arbeitsteilung wie bei [BlockPart] und den
 * Requisiten: Die Form steht einmal in Kotlin, nicht je Renderer neu.
 */
data class FrameRect(
    val col: Int,
    val row: Int,
    val cols: Int,
    val rows: Int,
    val tone: FrameTone
)

/**
 * Die Beinamen, in ihrer Rangfolge. Die Reihenfolge der Einträge IST die
 * Entscheidung: [CardStyle.epithet] nimmt den ersten Eintrag, dessen
 * Bedingung erfüllt ist. Oben steht deshalb, was am seltensten ist —
 * sonst überdeckte ein leicht erreichter Titel dauerhaft den schwer
 * erkämpften, und die Karte hörte auf, etwas zu erzählen.
 *
 * Die Texte stehen hier und nicht in den String-Ressourcen der App: Ein
 * Beiname ist ohne seine Bedingung sinnlos, und drei Plattformen, die
 * ihre Titel getrennt pflegen, laufen garantiert auseinander. Deutsch
 * ohne Umlaute — die Pixelschrift der Karte hat keine.
 */
enum class Epithet(val de: String, val en: String) {
    /** Der höchste Rekord, den die Sammlung überhaupt fordert (HOLO). */
    LEGENDE("LEGENDE", "LEGEND"),

    /** Fünfzehn perfekte Treffer hintereinander — kein Zufall mehr. */
    UHRWERK("UHRWERK", "CLOCKWORK"),

    /** Einen Monat lang jeden Tag da gewesen. */
    UNBEIRRBAR("UNBEIRRBAR", "UNSHAKEN"),

    /** Fünfhundert Läufe heißt vor allem: fünfhundert Mal wieder aufgestanden. */
    STEHAUFMAENNCHEN("STEHAUFMAENNCHEN", "COMEBACK KID"),

    /** Zehntausend Punkte, zusammengetragen aus allen Läufen. */
    PUNKTESAMMLER("PUNKTESAMMLER", "POINT COLLECTOR"),

    /** Acht perfekte Treffer am Stück — die Hand sitzt. */
    SCHARFSCHUETZE("SCHARFSCHUETZE", "SHARPSHOOTER"),

    /** An dreißig verschiedenen Tagen gespielt, ohne Serienzwang. */
    STAMMGAST("STAMMGAST", "REGULAR"),

    /** Fünfundzwanzig Läufe: Das Spiel ist keine Neugier mehr. */
    EINGESPIELT("EINGESPIELT", "SEASONED")
}

/**
 * Wo die Zeilen der Karte sitzen, als Anteil der Kartenhöhe, plus die
 * Schriftgrade von Titel und Punkt.
 *
 * Dass das vom Rahmen abhängt, hat einen Grund: Die Prachtstufe ist
 * 90 Pixel breit. Ein Titel, der darin klemmt, sieht nach Fehler aus,
 * also rückt der Inhalt bei den breiten Rahmen nach innen. Bei
 * [CardFrame.SCHLICHT] darf er das aber gerade NICHT — siehe
 * [CardStyle.layout].
 */
data class CardLayout(
    val title: Float,
    val titleSize: Float,
    val subline: Float,
    val sublineSize: Float,
    val dot: Float,
    val dotRadius: Float,
    val challenge: Float
)

object CardStyle {

    /**
     * Ab wie vielen gesammelten Skins die nächste Rahmenstufe greift.
     * Gezählt wird mit [SkinPaint.unlockedCount], also ohne Saison- und
     * Gönner-Skins: Ein gekaufter Rahmen wäre etwas anderes als ein
     * verdienter, und die Saison-Skins hängen am Kalender statt am Spiel.
     */
    val FRAME_STEPS = intArrayOf(10, 20, 30)

    /** Die höchste Rahmenstufe zu einem Sammlungsstand. */
    fun frame(collected: Int): CardFrame {
        var stufe = 0
        FRAME_STEPS.forEach { if (collected >= it) stufe++ }
        return CardFrame.entries[stufe]
    }

    /**
     * Die höchste verdiente Rahmenstufe zu einem Spielstand — die
     * Vorgabe, solange niemand etwas anderes gewählt hat.
     */
    fun frame(stats: SkinStats): CardFrame = frame(SkinPaint.unlockedCount(stats))

    /**
     * Ist diese Stufe verdient? [CardFrame.SCHLICHT] immer — er ist der
     * Bestand und damit kein Fund, sondern der Ausgangspunkt.
     *
     * Dass die Stufen aufeinander aufbauen, macht die Frage einfacher als
     * bei Skins oder Ton-Sets: Wer die dritte Stufe hat, hat auch die
     * zweite. Eine eigene Schwellenliste je Stufe wäre hier eine Lüge
     * über die Sammlung.
     */
    fun isUnlocked(frame: CardFrame, stats: SkinStats): Boolean =
        frame.ordinal <= frame(stats).ordinal

    /** Wie viele Rahmenstufen offen sind — reine Leistungsanzeige. */
    fun unlockedCount(stats: SkinStats): Int = frame(stats).ordinal + 1

    /**
     * Der Rahmen, den die Karte trägt: die Wahl, falls sie verdient ist —
     * sonst die höchste verdiente Stufe.
     *
     * Der Rückfall ist kein Randfall, sondern der Normalfall beim ersten
     * Mal: Wer nie gewählt hat ([gewaehlt] ist null), bekommt weiterhin
     * automatisch seine höchste Stufe. Die Karte ändert sich also durch
     * die Einführung der Wahl für niemanden — sie wird nur wählbar.
     *
     * Der zweite Fall ist seltener und unangenehmer: Ein gespeicherter
     * Rahmen, der nicht mehr verdient ist. Das kann beim Abgleich mit
     * einem anderen Gerät passieren, dessen Spielstand weiter war. Dann
     * gewinnt der Spielstand und nicht die gespeicherte Wahl — sonst
     * trüge eine Karte einen Rahmen, den ihr Stand nicht deckt.
     */
    fun frame(gewaehlt: CardFrame?, stats: SkinStats): CardFrame {
        val hoechste = frame(stats)
        if (gewaehlt == null || gewaehlt.ordinal > hoechste.ordinal) return hoechste
        return gewaehlt
    }

    /** Rahmenstufe zu einem gespeicherten Namen, null bei Unbekanntem. */
    fun fromName(name: String?): CardFrame? =
        CardFrame.entries.firstOrNull { it.name == name }

    /**
     * Die Maße des Bestands: die Karte, wie sie vor den Rahmen aussah.
     * Sie stehen einzeln hier, damit ein Test sie festnageln kann — wer
     * sie ändert, ändert die Karte von Leuten, die nichts dafür getan
     * haben.
     */
    const val PLAIN_TITLE = 0.14f
    const val PLAIN_TITLE_SIZE = 130f
    const val PLAIN_SUBLINE = 0.20f
    const val PLAIN_SUBLINE_SIZE = 56f
    const val PLAIN_DOT = 0.32f
    const val PLAIN_DOT_RADIUS = 110f
    const val PLAIN_CHALLENGE = 0.945f

    /**
     * Wo der Inhalt sitzt — abhängig vom Rahmen, aber mit einer harten
     * Ausnahme: [CardFrame.SCHLICHT] ist Pixel für Pixel der Bestand.
     *
     * Der Grund ist derselbe wie bei der WIESE unter den Kulissen. Eine
     * Sammlung fängt bei dem an, was schon da war; sonst ändert sich für
     * alle etwas, die nichts gesammelt haben, und niemand kann sagen, ob
     * das Absicht war. Erst die zweite Stufe darf anders aussehen — dann
     * hat sie jemand verdient.
     *
     * Die übrigen drei rücken den Inhalt nach innen, weil ihre Rahmen
     * bis zu 90 Pixel breit sind.
     */
    fun layout(frame: CardFrame): CardLayout = when (frame) {
        CardFrame.SCHLICHT -> CardLayout(
            title = PLAIN_TITLE,
            titleSize = PLAIN_TITLE_SIZE,
            subline = PLAIN_SUBLINE,
            sublineSize = PLAIN_SUBLINE_SIZE,
            dot = PLAIN_DOT,
            dotRadius = PLAIN_DOT_RADIUS,
            challenge = PLAIN_CHALLENGE
        )
        else -> CardLayout(
            title = 0.155f,
            titleSize = 120f,
            subline = 0.205f,
            sublineSize = 52f,
            dot = 0.345f,
            dotRadius = 105f,
            challenge = 0.92f
        )
    }

    // ===== Das Muster der Rahmen =====

    /**
     * Wie die Rahmen aussehen — als Tabelle, nicht als Zeichencode.
     *
     * Der Grund ist derselbe wie bei [ScenePaint.ROCK_PARTS]: Sobald zwei
     * Renderer dieselbe Form zeichnen (die geteilte Karte in
     * `android.graphics`, das Game-Over-Panel in Compose — und damit
     * Android wie iOS), müsste jede neue Stufe in mehreren Sprachen
     * nachgezeichnet werden und liefe garantiert auseinander. Hier steht
     * sie einmal, die Renderer füllen nur noch Rechtecke.
     *
     * Gelesen wird von außen nach innen, und die Reihenfolge ist Teil der
     * Aussage: Was später kommt, liegt oben. Deshalb decken die
     * Eck-Einträge am Schluss die Bänder an den Ecken zu.
     *
     * [CardFrame.SCHLICHT] hat keine Einträge, und das ist Absicht: Der
     * Bestand hatte keinen Rahmen, und die erste Stufe soll etwas sein,
     * das man verdient hat — nicht etwas, das allen still dazukommt.
     */
    private val PARTS: Map<CardFrame, List<FramePart>> = mapOf(
        CardFrame.SCHLICHT to emptyList(),

        CardFrame.DOPPELLINIE to listOf(
            FramePart(FrameShape.BAND, 0, 2, FrameTone.OUTLINE),
            FramePart(FrameShape.BAND, 2, 2, FrameTone.ACCENT),
            FramePart(FrameShape.BAND, 4, 2, FrameTone.OUTLINE),
            // Ecknieten: drei ineinandergesetzte Quadrate. Sie sind der
            // Teil, den man im Daumenbild zuerst sieht.
            FramePart(FrameShape.ECKBLOCK, 0, 10, FrameTone.OUTLINE),
            FramePart(FrameShape.ECKBLOCK, 2, 6, FrameTone.ACCENT),
            FramePart(FrameShape.ECKBLOCK, 4, 2, FrameTone.GOLD)
        ),

        CardFrame.ZINNEN to listOf(
            FramePart(FrameShape.BAND, 0, 2, FrameTone.OUTLINE),
            FramePart(FrameShape.BAND, 2, 4, FrameTone.ACCENT),
            // Zinnenkranz: gelbe Zähne im Farbband, alle sechs Felder.
            // Sie tragen die Stufe — das breitere Band allein wäre im
            // Vorschaubild nur ein etwas dickerer Strich.
            FramePart(FrameShape.ZAEHNE, 3, 2, FrameTone.GOLD, step = 6),
            FramePart(FrameShape.BAND, 6, 2, FrameTone.OUTLINE),
            FramePart(FrameShape.BAND, 8, 2, FrameTone.GOLD),
            FramePart(FrameShape.BAND, 10, 2, FrameTone.OUTLINE),
            FramePart(FrameShape.ECKBLOCK, 0, 16, FrameTone.OUTLINE),
            FramePart(FrameShape.ECKBLOCK, 2, 12, FrameTone.GOLD),
            FramePart(FrameShape.ECKBLOCK, 5, 6, FrameTone.OUTLINE),
            FramePart(FrameShape.ECKBLOCK, 7, 2, FrameTone.ACCENT)
        ),

        CardFrame.PRACHT to listOf(
            FramePart(FrameShape.BAND, 0, 2, FrameTone.OUTLINE),
            FramePart(FrameShape.BAND, 2, 3, FrameTone.GOLD),
            FramePart(FrameShape.BAND, 5, 2, FrameTone.OUTLINE),
            FramePart(FrameShape.BAND, 7, 4, FrameTone.ACCENT),
            // Dieselben Zinnen wie eine Stufe darunter, aber perlweiß und
            // eine Lage weiter innen — nebeneinander gelegt sind die
            // beiden Stufen dadurch auch dann auseinanderzuhalten, wenn
            // die Breite im Vorschaubild verlorengeht.
            FramePart(FrameShape.ZAEHNE, 8, 2, FrameTone.PEARL, step = 6),
            FramePart(FrameShape.BAND, 11, 2, FrameTone.INLAY),
            FramePart(FrameShape.BAND, 13, 2, FrameTone.OUTLINE),
            // Eckrosetten: ein eingelegter Rhombus statt der gestapelten
            // Quadrate der Stufe darunter.
            FramePart(FrameShape.ECKBLOCK, 0, 22, FrameTone.OUTLINE),
            FramePart(FrameShape.ECKRAUTE, 1, 20, FrameTone.GOLD),
            FramePart(FrameShape.ECKRAUTE, 6, 10, FrameTone.ACCENT),
            FramePart(FrameShape.ECKBLOCK, 9, 4, FrameTone.PEARL)
        )
    )


    /** Das Muster einer Stufe. */
    fun parts(frame: CardFrame): List<FramePart> = PARTS.getValue(frame)

    /**
     * Wie weit der Rahmen an einer Kante nach innen reicht, in Feldern.
     *
     * Gezählt werden nur die umlaufenden Formen: Die Ecken greifen weiter,
     * aber eben nur in den Ecken — wer den Inhalt nach ihnen einrückte,
     * ließe die Karte in der Mitte leer aussehen.
     */
    fun thickness(frame: CardFrame): Int = parts(frame)
        .filter { it.shape != FrameShape.ECKBLOCK && it.shape != FrameShape.ECKRAUTE }
        .maxOfOrNull { it.inset + it.size } ?: 0

    /**
     * Das Muster einer Stufe, ausgerollt auf ein Blatt von [cols] mal
     * [rows] Feldern — die einzige Stelle im Repo, an der aus einem
     * Rahmen Rechtecke werden.
     *
     * Die Liste ist in Zeichenreihenfolge: Wer sie stumpf von vorn nach
     * hinten füllt, bekommt den Rahmen. Ein Renderer, der sortiert oder
     * Doppelungen wegwirft, bekommt ihn nicht — die Bänder überlappen
     * einander an den Ecken absichtlich.
     */
    fun frameRects(frame: CardFrame, cols: Int, rows: Int): List<FrameRect> {
        val out = mutableListOf<FrameRect>()
        parts(frame).forEach { part ->
            when (part.shape) {
                FrameShape.BAND -> band(out, cols, rows, part)
                FrameShape.ZAEHNE -> takt(out, cols, rows, part, rund = false)
                FrameShape.ECKBLOCK -> ecken(out, cols, rows, part, rund = false)
                FrameShape.ECKRAUTE -> ecken(out, cols, rows, part, rund = true)
            }
        }
        return out
    }

    /** Ein umlaufendes Band: oben, unten, links, rechts. */
    private fun band(out: MutableList<FrameRect>, cols: Int, rows: Int, part: FramePart) {
        val i = part.inset
        val d = part.size
        val breite = cols - 2 * i
        val hoehe = rows - 2 * i
        out += FrameRect(i, i, breite, d, part.tone)
        out += FrameRect(i, rows - i - d, breite, d, part.tone)
        out += FrameRect(i, i, d, hoehe, part.tone)
        out += FrameRect(cols - i - d, i, d, hoehe, part.tone)
    }

    /**
     * Zähne auf allen vier Kanten, im Takt [FramePart.step].
     * Gezählt wird von beiden Enden zur Mitte, damit die Reihe an jeder
     * Ecke gleich anfängt — von links nach rechts durchgezählt bliebe an
     * einer Kante ein Rest übrig.
     */
    private fun takt(
        out: MutableList<FrameRect>,
        cols: Int,
        rows: Int,
        part: FramePart,
        rund: Boolean
    ) {
        val i = part.inset
        val d = part.size
        val start = i + part.phase
        var k = 0
        while (start + k * part.step + d <= cols - i) {
            val col = start + k * part.step
            form(out, col, i, d, part.tone, rund)
            form(out, cols - col - d, i, d, part.tone, rund)
            form(out, col, rows - i - d, d, part.tone, rund)
            form(out, cols - col - d, rows - i - d, d, part.tone, rund)
            k++
        }
        k = 0
        while (start + k * part.step + d <= rows - i) {
            val row = start + k * part.step
            form(out, i, row, d, part.tone, rund)
            form(out, i, rows - row - d, d, part.tone, rund)
            form(out, cols - i - d, row, d, part.tone, rund)
            form(out, cols - i - d, rows - row - d, d, part.tone, rund)
            k++
        }
    }

    /** Dieselbe Form in allen vier Ecken. */
    private fun ecken(
        out: MutableList<FrameRect>,
        cols: Int,
        rows: Int,
        part: FramePart,
        rund: Boolean
    ) {
        val i = part.inset
        val d = part.size
        for (col in intArrayOf(i, cols - i - d)) {
            for (row in intArrayOf(i, rows - i - d)) {
                form(out, col, row, d, part.tone, rund)
            }
        }
    }

    /**
     * Ein Quadrat oder eine Perle. Die Perle ist aus Zeilen gebaut: Die
     * Zeile wächst von der nächstgelegenen Kante zur Mitte hin und
     * schrumpft wieder — bei geraden Größen mit zwei gleich breiten
     * Zeilen in der Mitte, sonst mit einer.
     */
    private fun form(
        out: MutableList<FrameRect>,
        col: Int,
        row: Int,
        size: Int,
        tone: FrameTone,
        rund: Boolean
    ) {
        if (!rund) {
            out += FrameRect(col, row, size, size, tone)
            return
        }
        val mitte = size / 2
        for (r in 0 until size) {
            val halb = min(min(r, size - 1 - r) + 1, mitte)
            out += FrameRect(col + mitte - halb, row + r, 2 * halb, 1, tone)
        }
    }

    /**
     * Trägt dieser Beiname bei diesem Spielstand? Die Bedingungen sind
     * bewusst je eine einzige Schwelle auf einer einzigen Achse: Ein
     * Titel, für den man zwei Zahlen im Kopf haben muss, erklärt sich auf
     * einer geteilten Karte nicht mehr.
     */
    fun qualifies(epithet: Epithet, stats: SkinStats): Boolean = when (epithet) {
        Epithet.LEGENDE -> stats.bestScore >= 80
        Epithet.UHRWERK -> stats.bestPerfectStreak >= 15
        Epithet.UNBEIRRBAR -> stats.bestDailyStreak >= 30
        Epithet.STEHAUFMAENNCHEN -> stats.runCount >= 500
        Epithet.PUNKTESAMMLER -> stats.totalScore >= 10_000
        Epithet.SCHARFSCHUETZE -> stats.bestPerfectStreak >= 8
        Epithet.STAMMGAST -> stats.daysPlayed >= 30
        Epithet.EINGESPIELT -> stats.runCount >= 25
    }

    /**
     * Der Beiname dieses Spielstands — null in den ersten Läufen. Dass
     * ganz am Anfang keiner steht, ist gewollt: Der erste Titel soll ein
     * Ereignis sein und kein Begrüßungsgeschenk.
     *
     * Treffen mehrere zu (und das ist der Normalfall, sobald jemand länger
     * spielt), gewinnt der oberste — siehe [Epithet].
     */
    fun epithet(stats: SkinStats): Epithet? =
        Epithet.entries.firstOrNull { qualifies(it, stats) }

    /** Der Titel in der Sprache der Oberfläche. */
    fun label(epithet: Epithet, german: Boolean): String =
        if (german) epithet.de else epithet.en
}
