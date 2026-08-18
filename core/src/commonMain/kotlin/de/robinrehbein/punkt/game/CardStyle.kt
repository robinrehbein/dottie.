package de.robinrehbein.punkt.game

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
