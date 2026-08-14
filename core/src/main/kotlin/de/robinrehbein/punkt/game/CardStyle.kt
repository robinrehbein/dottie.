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

object CardStyle {

    /**
     * Ab wie vielen gesammelten Skins die nächste Rahmenstufe greift.
     * Gezählt wird mit [SkinPaint.unlockedCount], also ohne Saison- und
     * Gönner-Skins: Ein gekaufter Rahmen wäre etwas anderes als ein
     * verdienter, und die Saison-Skins hängen am Kalender statt am Spiel.
     */
    val FRAME_STEPS = intArrayOf(10, 20, 30)

    /** Rahmenstufe zu einem Sammlungsstand. */
    fun frame(collected: Int): CardFrame {
        var stufe = 0
        FRAME_STEPS.forEach { if (collected >= it) stufe++ }
        return CardFrame.entries[stufe]
    }

    /** Rahmenstufe zu einem Spielstand — der Weg, den der Zeichencode nimmt. */
    fun frame(stats: SkinStats): CardFrame = frame(SkinPaint.unlockedCount(stats))

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
