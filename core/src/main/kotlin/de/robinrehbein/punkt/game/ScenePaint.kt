package de.robinrehbein.punkt.game

/**
 * Farbwerk aller Kulissen — die zweite Sammlung neben den Skins und die
 * einzige Quelle für Kulissen-Farben in Kotlin, wie [SkinPaint] für den
 * Vogel.
 *
 * Eine Kulisse ist alles, was NICHT über Treffer entscheidet: Himmel (in
 * sieben Stufen), Wolken, Requisiten am Boden und der Bodenstreifen. Die
 * Bahn gehört ausdrücklich nicht dazu — Zielzone, Perfekt-Kern und Falle
 * behalten in jeder Kulisse dieselben Farben. Genau deshalb ist die
 * Kulisse die verkäufliche Fläche und die Bahn nicht: Wer eine Kulisse
 * kauft, kauft Aussicht, keinen Vorteil und keinen Nachteil.
 *
 * Die Requisiten sind bewusst Daten und kein Zeichencode: [Prop]
 * beschreibt Form, Größe, Windanteil und Farben, und alle vier Renderer
 * (Compose, Canvas, SpriteKit, Wear) werten dieselbe Liste gleich aus.
 * Ohne diese Trennung müsste jede neue Kulisse in vier Sprachen
 * nachgezeichnet werden — und liefe in vieren auseinander.
 *
 * Alle Farben sind ARGB-Longs (0xAARRGGBB), damit das Modul frei von
 * Compose- und Android-Typen bleibt und in Unit-Tests prüfbar ist.
 */
enum class SceneId {
    WIESE, WUESTE, MEER, BERG, STADT, WELTRAUM
}

/**
 * Die Formen, aus denen Kulissen ihre Requisiten bauen. Jede Form ist in
 * allen vier Renderern als Stapel von Rechtecken umgesetzt — der
 * Pixel-Look entsteht aus Blöcken, nicht aus Pfaden.
 *
 * Welche Farbrolle eine Form benutzt, steht bei [Prop].
 */
enum class PropShape {
    /** Laubbaum: Stamm plus dreistufige Krone (Bestand der WIESE). */
    BAUM,

    /** Blume: Stiel, zwei Blätter, vier Blütenblätter um eine Mitte. */
    BLUME,

    /** Strauch: runde Beeren-Silhouette, Bauch in der Mitte am breitesten. */
    STRAUCH,

    /** Kaktus: Säule mit zwei Armen, oben eine Blüte. */
    KAKTUS,

    /** Welle: flacher, breiter Stapel mit Schaumkrone. */
    WELLE,

    /** Nadelbaum: schmaler Stamm, drei spitze Lagen, Spitze obendrauf. */
    NADELBAUM,

    /** Hochhaus: hoher Block mit Schattenseite, Dachkante und Fenstern. */
    HOCHHAUS,

    /** Fels: pyramidenförmiger Stapel, unten am breitesten. */
    FELS
}

/**
 * Eine Requisite der Kulisse. Die Renderer laufen die Liste einer Kulisse
 * zyklisch ab (`props[k % props.size]`), genau wie der Bestand bisher
 * `k % 4` benutzt hat.
 *
 * [size] ist ein Anteil der Bildhöhe (0.075 = die großen Bäume der
 * WIESE), [sway] der Anteil am Windausschlag; negativ heißt gegenläufig,
 * 0 heißt unbeweglich (Hochhäuser wanken nicht).
 *
 * Die drei Farblagen [dark], [body] und [light] gehen von unten (dunkel)
 * nach oben (hell) — außer bei BLUME, wo [dark] der Stiel, [body] die
 * Blätter und [light] die Blütenmitte ist. [stem] und [stemShade] tragen
 * Stämme, [accents] wechselt je Wiederholung durch (Blütenblätter der
 * Blume, Schaum der Welle, Fensterfarbe des Hochhauses) — leer heißt
 * "diese Form braucht keinen Akzent".
 */
data class Prop(
    val shape: PropShape,
    val size: Float,
    val sway: Float,
    val dark: Long,
    val body: Long,
    val light: Long,
    val stem: Long = 0xFF543847,
    val stemShade: Long = 0xFF543847,
    val accents: List<Long> = emptyList()
)

/**
 * Der Bodenstreifen: Grundfläche mit einem dunkleren Band darin, darüber
 * eine Narbe aus zwei Farben (durchgehend dunkel, davor helle Zähne).
 * WELTRAUM hat keinen — dort ist [Scene.ground] null.
 */
data class Ground(
    val sand: Long,
    val sandShade: Long,
    val turfDark: Long,
    val turfLight: Long
)

/**
 * Eine komplette Kulisse. [cloud] und [ground] sind optional: Im Vakuum
 * gibt es weder Wolken noch Boden, und beides fehlt dort mit Absicht,
 * statt in Grau ausgeblendet zu werden.
 */
class Scene(
    val sky: LongArray,
    val cloud: Long?,
    val ground: Ground?,
    val props: List<Prop>
)

object ScenePaint {

    /**
     * Die Bodenkante als Anteil der Bildhöhe. Sie ist Layout-Anker, nicht
     * Dekor: Requisiten stehen darauf, der Bodenstreifen beginnt dort,
     * und die Tod-Animation misst ihren Sturz daran. Der Wert gilt
     * deshalb für JEDE Kulisse — auch für WELTRAUM, der gar keinen Boden
     * zeichnet. Eine Kulisse, die diese Linie verschöbe, würde das
     * Spielgefühl ändern, und genau das darf eine Kulisse nicht.
     */
    const val GROUND_TOP = 0.88f

    /**
     * Die Bodenkante in Pixeln. Alle vier Renderer fragen hier nach,
     * statt selbst mit 0.88 zu rechnen — nur so bleibt der Anker beim
     * Hinzufügen einer Kulisse garantiert an derselben Stelle, und die
     * Tod-Animation setzt in jeder Kulisse auf derselben Linie auf.
     */
    fun groundY(height: Float): Float = height * GROUND_TOP

    /**
     * Wie viele Requisiten-Plätze eine Kulisse mindestens beschreibt. Der
     * Bestand hat vier (Baum, Blume, kleiner Baum, Strauch); weniger
     * würde die Reihe sichtbar kurz wiederholen.
     */
    const val PROP_SLOTS = 4

    /**
     * Mindestabstand im RGB-Raum, den eine Kulissenfarbe zu Zielzone und
     * Falle halten muss (siehe ScenePaintTest). Der Wert liegt deutlich
     * über den 24 Schritten, ab denen SkinPaint eine Farbe schon als
     * "wie die Zone" wertet: Der Vogel ist ein Punkt, eine Kulisse ist
     * eine Fläche — und eine Fläche in Zonenfarbe zieht das Auge auch
     * dann, wenn sie nirgends im Ringband liegt.
     */
    const val MIN_ZONE_DISTANCE = 60f

    /**
     * Mindestabstand zweier aufeinanderfolgender Himmelsstufen. Der
     * Himmel ist Fortschrittsanzeige: Wer eine Stufe erreicht, soll das
     * sehen. Der engste Schritt im Bestand ist Tag → Blau mit 41 — die
     * Grenze liegt knapp darunter, damit sie den Bestand beschreibt und
     * nicht umfärbt.
     */
    const val MIN_SKY_STEP = 40f

    /**
     * Mindestabstand eines Himmels zu den Signalfarben der Bahn (Zone,
     * Perfekt-Kern, Falle und Fallen-Kern).
     *
     * Der Wert ist bewusst der Bestand selbst: Das knappste ausgelieferte
     * Paar ist der Fallen-Kern vor dem Stadt-Himmel der zweiten Stufe mit
     * 64,4. Damit sagt die Zusicherung nicht "das ist gut", sondern "keine
     * neue Kulisse darf schlechter sein als das Schlechteste, was wir
     * heute zeigen" — und genau dafuer taugt sie.
     *
     * Der Bestand ist an dieser Stelle wirklich knapp: Die Falle vor dem
     * lila Himmel (Score 10-14 und wieder 50-54) hebt sich kaum ab. Das
     * ist bewusst so belassen — die Falle bleibt, wie sie ist. Die Folge
     * ist aber keine Unfairness, sondern das Gegenteil: Eine Falle, die
     * man kaum sieht, taeuscht auch niemanden. Der FALLE-Twist wirkt in
     * diesen zehn Punkten also schwaecher als sonst.
     */
    const val MIN_SKY_SIGNAL_DISTANCE = 64f

    /**
     * Die Greens, die die WIESE seit jeher trägt: Buschfarbe, ihr
     * Schatten und die beiden Töne der Grasnarbe. Sie sind praktisch die
     * Zielzonenfarbe — die Narbe ist sogar exakt sie.
     *
     * Das bleibt so. Diese Flächen liegen am unteren Bildrand, nie im
     * Ringband: Die Bahn endet bei 72 % Höhe, die Kronen beginnen bei
     * 74 %. Ein stiller Umbau wäre eine Änderung am ausgelieferten Bild
     * gewesen, keine Absicherung — deshalb steht der Bestand hier als
     * benannte Ausnahme, und nur die WIESE darf sie benutzen.
     */
    val LEGACY_ZONE_GREENS = longArrayOf(
        0xFF71C837, // BushColor
        0xFF5AA82C, // BushShadeColor
        0xFF9DE85A, // GrassLight
        0xFF74BF2E  // GrassDark
    )

    // ===== Die Kulissen =====

    /**
     * Der Bestand. Jeder Wert stammt aus GameOverlays.kt bzw.
     * TimingGameScreen.kt und ist absichtlich unverändert: Wer die
     * Umstellung auf ScenePaint sieht, hat sie falsch gemacht.
     */
    private val WIESE = Scene(
        sky = longArrayOf(
            0xFF4EC0CA, // 0+  Tag (türkis)
            0xFF5B9BD5, // 5+  Blau
            0xFF7B6FD0, // 10+ Lila
            0xFFC0616F, // 15+ Altrosa
            0xFFD98A3D, // 20+ Sonnenuntergang
            0xFF3D4A8C, // 25+ Dämmerung
            0xFF2A2640  // 30+ Nacht
        ),
        cloud = 0xFFE9FCFD,
        ground = Ground(
            sand = 0xFFDED895,
            sandShade = 0xFFD3C87E,
            turfDark = 0xFF74BF2E,
            turfLight = 0xFF9DE85A
        ),
        props = listOf(
            Prop(
                PropShape.BAUM, 0.075f, 1.0f,
                dark = 0xFF5AA82C, body = 0xFF71C837, light = 0xFF9DE85A,
                stem = 0xFF9C6B3C, stemShade = 0xFF7A4E2A
            ),
            // Die Mitte der Blüte ist Gold (DotBody), nicht Grün — sie
            // war es immer, und sie ist der einzige warme Punkt im Grün.
            Prop(
                PropShape.BLUME, 0.032f, 0.8f,
                dark = 0xFF5AA82C, body = 0xFF71C837, light = 0xFFFFD847,
                accents = listOf(0xFFE53935, 0xFFE9FCFD)
            ),
            Prop(
                PropShape.BAUM, 0.058f, -1.0f,
                dark = 0xFF5AA82C, body = 0xFF71C837, light = 0xFF9DE85A,
                stem = 0xFF9C6B3C, stemShade = 0xFF7A4E2A
            ),
            Prop(
                PropShape.STRAUCH, 0.026f, 0.4f,
                dark = 0xFF5AA82C, body = 0xFF71C837, light = 0xFF9DE85A
            )
        )
    )

    /**
     * Wüste: heller Dunsthimmel, der über Sandschleier und Glut in eine
     * kalte Nacht fällt. Die Kakteen sind bewusst blaustichig grün —
     * ein Wiesengrün hätte hier den Mindestabstand zur Zielzone gerissen.
     */
    private val WUESTE = Scene(
        sky = longArrayOf(
            0xFFA8DCE8, 0xFFF2C46B, 0xFFE8934A, 0xFFC85F3C,
            0xFF8E3B47, 0xFF4A2C4E, 0xFF241C33
        ),
        cloud = 0xFFF7E9C8,
        ground = Ground(
            sand = 0xFFE8C88A,
            sandShade = 0xFFD4AE6E,
            turfDark = 0xFFC79A55,
            turfLight = 0xFFEFD7A0
        ),
        props = listOf(
            Prop(
                PropShape.KAKTUS, 0.075f, 1.0f,
                dark = 0xFF1F6B41, body = 0xFF2E8B57, light = 0xFF43A96B,
                accents = listOf(0xFFE8607A, 0xFFF2A83C)
            ),
            Prop(
                PropShape.FELS, 0.032f, 0f,
                dark = 0xFF8A6A4A, body = 0xFFA88860, light = 0xFFC4A87C
            ),
            Prop(
                PropShape.KAKTUS, 0.058f, -1.0f,
                dark = 0xFF1F6B41, body = 0xFF2E8B57, light = 0xFF43A96B,
                accents = listOf(0xFFF2A83C, 0xFFE8607A)
            ),
            Prop(
                PropShape.FELS, 0.026f, 0.4f,
                dark = 0xFF8A6A4A, body = 0xFFA88860, light = 0xFFC4A87C
            )
        )
    )

    /** Meer: der Boden ist Wasser, die Narbe darauf ist Schaum. */
    private val MEER = Scene(
        sky = longArrayOf(
            0xFF5AD2E8, 0xFF2F9AD4, 0xFF2E5FB8, 0xFFC4707C,
            0xFFE09A4A, 0xFF35447F, 0xFF1B2138
        ),
        cloud = 0xFFDFF4FF,
        ground = Ground(
            sand = 0xFF2F86C8,
            sandShade = 0xFF24699E,
            turfDark = 0xFF4FC3DE,
            turfLight = 0xFFBFE9FF
        ),
        props = listOf(
            Prop(
                PropShape.WELLE, 0.075f, 1.0f,
                dark = 0xFF1F5FA8, body = 0xFF2E86D8, light = 0xFF7FC8F0,
                accents = listOf(0xFFFFFFFF, 0xFFDFF4FF)
            ),
            Prop(
                PropShape.WELLE, 0.032f, 0.8f,
                dark = 0xFF1F5FA8, body = 0xFF2E86D8, light = 0xFF7FC8F0,
                accents = listOf(0xFFDFF4FF, 0xFFFFFFFF)
            ),
            Prop(
                PropShape.WELLE, 0.058f, -1.0f,
                dark = 0xFF1F5FA8, body = 0xFF2E86D8, light = 0xFF7FC8F0,
                accents = listOf(0xFFFFFFFF, 0xFFDFF4FF)
            ),
            Prop(
                PropShape.FELS, 0.026f, 0.4f,
                dark = 0xFF4A5A6A, body = 0xFF6B7C8C, light = 0xFF9AAAB8
            )
        )
    )

    /** Berg: Schnee statt Sand, Nadelbäume mit weißer Spitze. */
    private val BERG = Scene(
        sky = longArrayOf(
            0xFFA8D8E8, 0xFF6FAFD8, 0xFF4A7FC0, 0xFF8A5A6E,
            0xFFD08A5A, 0xFF3E4A78, 0xFF1E2438
        ),
        cloud = 0xFFF2FAFF,
        ground = Ground(
            sand = 0xFFE4EDF4,
            sandShade = 0xFFCBD8E4,
            turfDark = 0xFFA8B8C8,
            turfLight = 0xFFFFFFFF
        ),
        props = listOf(
            Prop(
                PropShape.NADELBAUM, 0.075f, 1.0f,
                dark = 0xFF1E5140, body = 0xFF2A6B52, light = 0xFFD8E8F0,
                stem = 0xFF5C4130, stemShade = 0xFF46311F
            ),
            Prop(
                PropShape.FELS, 0.032f, 0f,
                dark = 0xFF6A6E78, body = 0xFF8A8F9C, light = 0xFFB8BEC9
            ),
            Prop(
                PropShape.NADELBAUM, 0.058f, -1.0f,
                dark = 0xFF1E5140, body = 0xFF2A6B52, light = 0xFFD8E8F0,
                stem = 0xFF5C4130, stemShade = 0xFF46311F
            ),
            Prop(
                PropShape.FELS, 0.026f, 0.4f,
                dark = 0xFF6A6E78, body = 0xFF8A8F9C, light = 0xFFB8BEC9
            )
        )
    )

    /**
     * Stadt: Asphalt statt Wiese, Bordstein statt Grasnarbe. Die
     * Hochhäuser haben Windanteil 0 — ein wankendes Haus wäre ein Witz,
     * den das Spiel an dieser Stelle nicht macht.
     */
    private val STADT = Scene(
        sky = longArrayOf(
            0xFF9ED4E4, 0xFF5F9BC8, 0xFF7B6B9E, 0xFFC4707E,
            0xFFE8963C, 0xFF3A3F6E, 0xFF1A1A2E
        ),
        cloud = 0xFFE4E8F0,
        ground = Ground(
            sand = 0xFF4A4550,
            sandShade = 0xFF383340,
            turfDark = 0xFF6E6878,
            turfLight = 0xFF9A93A4
        ),
        props = listOf(
            Prop(
                PropShape.HOCHHAUS, 0.075f, 0f,
                dark = 0xFF3E4A5E, body = 0xFF56647C, light = 0xFF8494AC,
                accents = listOf(0xFFFFD847, 0xFF7FD8E8)
            ),
            Prop(
                PropShape.HOCHHAUS, 0.052f, 0f,
                dark = 0xFF4E3E52, body = 0xFF6C5870, light = 0xFF9A86A0,
                accents = listOf(0xFF7FD8E8, 0xFFFFD847)
            ),
            Prop(
                PropShape.HOCHHAUS, 0.062f, 0f,
                dark = 0xFF3A4C50, body = 0xFF54686C, light = 0xFF869A9E,
                accents = listOf(0xFFFFD847, 0xFF7FD8E8)
            ),
            Prop(
                PropShape.FELS, 0.026f, 0.4f,
                dark = 0xFF4E4A56, body = 0xFF6A6672, light = 0xFF8C8894
            )
        )
    )

    /**
     * Weltraum: kein Boden, keine Wolken. Statt Pflanzen treiben
     * Felsbrocken in zwei Legierungen auf der Höhe, auf der sonst der
     * Boden läge — die Linie bleibt, nur der Boden fehlt.
     */
    private val WELTRAUM = Scene(
        sky = longArrayOf(
            // Der Weltraum bleibt dunkel: Der Verlauf laeuft ueber Nebel-
            // Toene bis zu dunklem Wein, nie bis ins Abendrot. Eine helle
            // Stufe sah aus wie ein Sonnenuntergang mit Sternen daneben.
            0xFF0E1430, 0xFF1A2A62, 0xFF3E1A78, 0xFF6A1E6E,
            0xFF8A2C4A, 0xFF3A1A3E, 0xFF0A0716
        ),
        cloud = null,
        ground = null,
        props = listOf(
            Prop(
                PropShape.FELS, 0.075f, 1.0f,
                dark = 0xFF342E42, body = 0xFF4E4860, light = 0xFF726C88
            ),
            Prop(
                PropShape.FELS, 0.032f, 0.8f,
                dark = 0xFF2E3A4A, body = 0xFF46566C, light = 0xFF6C8098
            ),
            Prop(
                PropShape.FELS, 0.058f, -1.0f,
                dark = 0xFF342E42, body = 0xFF4E4860, light = 0xFF726C88
            ),
            Prop(
                PropShape.FELS, 0.026f, 0.4f,
                dark = 0xFF2E3A4A, body = 0xFF46566C, light = 0xFF6C8098
            )
        )
    )

    /** Die komplette Beschreibung einer Kulisse. */
    fun of(id: SceneId): Scene = when (id) {
        SceneId.WIESE -> WIESE
        SceneId.WUESTE -> WUESTE
        SceneId.MEER -> MEER
        SceneId.BERG -> BERG
        SceneId.STADT -> STADT
        SceneId.WELTRAUM -> WELTRAUM
    }

    /** Himmelsfarben einer Kulisse; die Stufe kommt aus [SkinPaint.skyStage]. */
    fun sky(id: SceneId): LongArray = of(id).sky

    /** Himmelsfarbe zu einem Score — der Weg, den alle Renderer gehen. */
    fun skyFor(id: SceneId, score: Int): Long = of(id).sky[SkinPaint.skyStage(score)]

    /** Wolkenfarbe, null = diese Kulisse hat keine Wolken. */
    fun cloud(id: SceneId): Long? = of(id).cloud

    /** Bodenstreifen, null = diese Kulisse hat keinen Boden (WELTRAUM). */
    fun ground(id: SceneId): Ground? = of(id).ground

    fun props(id: SceneId): List<Prop> = of(id).props

    /**
     * Drei Farben für Vorschau-Kacheln: Tageshimmel, Boden (im Weltraum
     * ersatzweise die Nachtstufe) und die Körperfarbe der größten
     * Requisite. Mehr braucht eine 36-dp-Kachel nicht, um erkennbar zu
     * sein — und weniger wäre nicht unterscheidbar.
     */
    fun chips(id: SceneId): List<Long> {
        val scene = of(id)
        return listOf(
            scene.sky[0],
            scene.ground?.sand ?: scene.sky[6],
            scene.props.first().body
        )
    }

    // ===== Freischaltung =====

    /**
     * Kulissen hängen an denselben Zahlen wie die Skins ([SkinStats]),
     * aber an anderen Achsen: Wo Skins in dichten Stufen fallen, ist eine
     * Kulisse ein seltener, großer Wechsel. Deshalb stehen hier hohe,
     * weit auseinanderliegende Schwellen — je eine pro Achse, damit
     * niemand alle sechs über denselben Weg bekommt.
     */
    fun isUnlocked(id: SceneId, stats: SkinStats): Boolean = when (id) {
        SceneId.WIESE -> true
        SceneId.WUESTE -> stats.runCount >= 500
        SceneId.MEER -> stats.totalScore >= 10_000
        SceneId.BERG -> stats.bestDailyStreak >= 30
        SceneId.STADT -> stats.bestScore >= 85

        // Der Weltraum ist der Abschluss der Kulissen-Sammlung, wie der
        // REGENBOGEN bei den Skins: Er kommt erst, wenn alle anderen
        // offen sind (er selbst zählt nicht mit, sonst wäre die Bedingung
        // zirkulär).
        SceneId.WELTRAUM -> SceneId.entries.all {
            it == SceneId.WELTRAUM || isUnlocked(it, stats)
        }
    }

    /** Wie viele Kulissen offen sind — reine Leistungsanzeige. */
    fun unlockedCount(stats: SkinStats): Int =
        SceneId.entries.count { isUnlocked(it, stats) }

    /** Kulisse zu einem gespeicherten Namen, WIESE als Fallback. */
    fun fromName(name: String?): SceneId =
        SceneId.entries.firstOrNull { it.name == name } ?: SceneId.WIESE
}
