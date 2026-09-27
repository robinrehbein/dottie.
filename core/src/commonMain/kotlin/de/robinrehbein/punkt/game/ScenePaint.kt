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

    /** Fels: unsymmetrischer Umriss mit Lichtseite (siehe ROCK_PARTS). */
    FELS,

    /** Laterne: schmaler Mast mit leuchtendem Glas (siehe LANTERN_PARTS). */
    LATERNE,

    /**
     * Insel: flacher Sandhügel im Wasser mit zwei Palmen. [Prop.dark] und
     * [Prop.body] sind die Palmwedel, [Prop.light] der Sand, [Prop.stem]
     * und [Prop.stemShade] die Stämme, der Akzent die Sandschattenseite.
     */
    INSEL
}

/**
 * Was hinter Wolken und Requisiten liegt — eine Ebene, die sich langsamer
 * bewegt als alles davor.
 */
enum class BackdropKind {
    /**
     * Zwei Gebirgsketten mit Schneegipfeln, die hintere heller (Luftdunst).
     * Farben: ferne Kette, ihre Schattenseite, nahe Kette, ihre
     * Schattenseite, Schnee, Schneeschatten.
     */
    GEBIRGE,

    /**
     * Sternenhimmel über das ganze Bild: funkelnde Sterne, zwei langsam
     * drehende Galaxien, ab und zu eine Sternschnuppe. Farben: Stern
     * weiß, Stern warm, Stern kühl, Galaxienkern, Arm eins, Arm zwei.
     */
    STERNENHIMMEL
}

/**
 * Die Hintergrund-Ebene einer Kulisse. [colors] folgt der Reihenfolge,
 * die bei [BackdropKind] steht.
 */
data class Backdrop(
    val kind: BackdropKind,
    val colors: List<Long>
)

/**
 * Ein Rechteck einer Requisitenform, die als Tabelle statt als
 * Zeichencode vorliegt — bisher der Fels ([ROCK_PARTS]) und die Laterne
 * ([LANTERN_PARTS]).
 *
 * [x] ist auf die Mitte bezogen, [y] zählt vom Boden nach oben, [tone]
 * wählt aus der Requisiten-Palette: 0 dunkel, 1 Körper, 2 hell,
 * 3 Akzent (die erste Farbe aus [Prop.accents]).
 */
data class BlockPart(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val tone: Int
)

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
 *
 * [style] sagt dem Renderer, welches Bodenmuster er aus diesen Farben
 * baut (siehe [GroundStyle]). Es steht am Ende und hat einen Standard,
 * damit bestehende Aufrufe unverändert bleiben.
 */
data class Ground(
    val sand: Long,
    val sandShade: Long,
    val turfDark: Long,
    val turfLight: Long,
    val style: GroundStyle = GroundStyle.NARBE
)

/**
 * Das Bodenmuster einer Welt (Bevel-Look, Schritt 2). Oberkante und
 * Füllung gehören fest zusammen — eine Dünenkante über Asphalt ergäbe
 * keinen Sinn —, deshalb ein Stil für beides statt zweier Achsen.
 *
 * Die Farben kommen aus [Ground], die zusätzlichen Musterfarben aus
 * [GroundPaint]. Die Bodenkante [ScenePaint.GROUND_TOP] ist für jeden
 * Stil dieselbe: Wellenkämme und Dünen dürfen darüber hinausragen, die
 * Linie selbst verschiebt kein Muster.
 */
enum class GroundStyle {
    /** WIESE: Grasnarbe als Bevel-Kacheln, Sand mit Kieseln. */
    NARBE,

    /** WÜSTE: Dünenkante, drei Sandsteinschichten mit Fugen, halb eingesunkene Kiesel. */
    DUENE,

    /** MEER: Wellenkämme mit Schaum, Wasserstufen, Luftblasen-Ringe. */
    WELLEN,

    /** BERG: Schneedecke mit Eiszapfen über einer bevelten Felsmauer. */
    SCHNEE,

    /** STADT: Bordsteine, Asphalt mit gelber Mittellinie und Gully. */
    BORDSTEIN
}

/**
 * Das Muster eines normalen Bahn-Blocks. Es liegt auf der Fläche mit
 * Bevel; die Farbe des Musters ist [TrackStyle.accent].
 */
enum class BlockPattern {
    /** Nur Fläche mit Kante (WIESE, wie bisher). */
    GLATT,

    /** Waagrechte Fuge durch die Mitte (Sandstein). */
    FUGE,

    /** Zwei Maserungspunkte übereinander (Treibholz-Planke). */
    PLANKE,

    /** Obere Kante als Schneekappe, links eine Stufe tiefer (Fels). */
    SCHNEEKAPPE,

    /** Zwei Nieten in gegenüberliegenden Ecken (Betonplatte). */
    NIETEN,

    /** Ein Lämpchen in der Mitte (Metallpanel). */
    LAEMPCHEN
}

/**
 * Das Motiv, das eine Welt auf ihre Zonenblöcke setzt. Der Blockkörper
 * bleibt in jeder Welt grün (die Zone ist das Signal, nicht das
 * Material); nur ein paar Pixel darauf wechseln.
 */
enum class ZoneMotif {
    /** Blätter und Tupfer des Bestands — der Renderer zeichnet sie wie bisher. */
    WIESE,

    /** Weiße Kaktusstacheln. */
    KAKTUS,

    /** Seerosenblatt mit Stiel und ein Wassertropfen. */
    SEEROSE,

    /** Moos mit Steinchen. */
    EDELWEISS,

    /** LED-Kachel: vier hellgrüne Punkte. */
    LED,

    /** Hellgrüne Kristall-Facette als Diagonale. */
    KRISTALL
}

/**
 * Die Bahn einer Welt: Blockfläche [block] mit ihren Kanten [light] und
 * [dark] (von Hand gesetzt, nicht abgeleitet — so stehen sie in den
 * Zielbildern), das Muster [pattern] in [accent] und das Zonen-Motiv
 * [motif]. Bei [BlockPattern.GLATT] ist [accent] durchsichtig (0) und
 * wird nicht gezeichnet.
 */
data class TrackStyle(
    val block: Long,
    val light: Long,
    val dark: Long,
    val pattern: BlockPattern,
    val accent: Long,
    val motif: ZoneMotif
)

/**
 * Ein Pixel eines Zonen-Motivs. [dx] und [dy] zählen in Rasterstufen
 * (`unit` des Blocks) um die Mitte der Grasfläche, positiv nach rechts
 * und nach unten.
 */
data class MotifPixel(val dx: Int, val dy: Int, val color: Long)

/**
 * Zusätzliche Farben der Bodenmuster ([GroundStyle]). Was sich aus
 * [Ground] und [BevelPaint] ergibt, steht hier nicht noch einmal; hier
 * stehen nur die Töne, die ein Muster zusätzlich braucht. Werte wie im
 * Prototyp (docs/bevel-prototyp.patch, drawProtoGround).
 */
object GroundPaint {

    // --- NARBE (WIESE) ---

    /** Sand der WIESE, aus dem die Kiesel abgeleitet sind. */
    private const val WIESE_SAND: Long = 0xFFDED895

    /** Lichtpixel eines Kiesels im Sand. */
    val PEBBLE_LIGHT: Long = BevelPaint.light(WIESE_SAND)

    /** Körper eines Kiesels: nur ein Fünftel zur Kontur, sonst wird er ein Loch. */
    val PEBBLE_DARK: Long = BevelPaint.mix(WIESE_SAND, BevelPaint.OUTLINE, 0.2f)

    /**
     * Schattenkante der Narben-Kacheln. Schwächer als [BevelPaint.dark]:
     * Die Kacheln sind nur vier Zellen hoch, eine volle Kante machte aus
     * der Narbe ein Gitter. Die Lichtkante ist [BevelPaint.light].
     */
    fun turfShade(turf: Long): Long = BevelPaint.mix(turf, BevelPaint.OUTLINE, 0.2f)

    // --- DUENE (WÜSTE) ---

    /** Helle Oberkante der Dünenwelle. */
    const val DUNE_CREST: Long = 0xFFFFF0C8

    /** Die drei Sandsteinschichten, von oben nach unten dunkler. */
    val SANDSTONE_BANDS: List<Long> = listOf(0xFFD9B377, 0xFFCFA466, 0xFFC4955A)

    /** Lichtkante auf jeder Schicht, in derselben Reihenfolge. */
    val SANDSTONE_BAND_LIGHTS: List<Long> =
        SANDSTONE_BANDS.map { BevelPaint.mix(it, 0xFFFFFFFF, 0.3f) }

    /** Senkrechte Fugen in den Schichten. */
    const val SANDSTONE_JOINT: Long = 0xFFB88A52

    /** Halb eingesunkene Kiesel ([PropSprites.DUNE_PEBBLE]): Licht, Körper, Schatten. */
    const val DUNE_PEBBLE_LIGHT: Long = 0xFFE3BE82
    const val DUNE_PEBBLE_BODY: Long = 0xFFC79A55
    const val DUNE_PEBBLE_DARK: Long = 0xFFA57C42

    // --- WELLEN (MEER) ---

    /** Die mittlere Wasserstufe zwischen Oberfläche und Tiefe. */
    const val WATER_MID: Long = 0xFF2A78B6

    /** Schaum auf den Wellenkämmen. */
    const val FOAM: Long = 0xFFFFFFFF

    // --- SCHNEE (BERG) ---

    /** Die Fugen der Felsmauer (die Fläche hinter den Blöcken). */
    const val ROCK_JOINT: Long = 0xFF6B707C

    /** Ein Felsblock der Mauer mit Licht- und Schattenkante. */
    const val ROCK: Long = 0xFF9AA0AA
    const val ROCK_LIGHT: Long = 0xFFC4C9D1
    const val ROCK_DARK: Long = 0xFF7A808A

    /** Die oberste Zeile der Schneedecke. */
    const val SNOW_TOP: Long = 0xFFFFFFFF

    // --- BORDSTEIN (STADT) ---

    /** Lichtkante der Bordsteine. */
    const val CURB_LIGHT: Long = 0xFFC4BECC

    /** Gestrichelte Mittellinie und ihre Lichtkante. */
    const val CENTER_LINE: Long = 0xFFF2C94C
    const val CENTER_LINE_LIGHT: Long = 0xFFFFE08A
}

/**
 * Requisiten als Pixel-Masken, gezeichnet wie [TrapPaint.MINE]: eine
 * Zelle pro Zeichen, `.` bleibt frei. Zwei Formen lasen sich als
 * gestapelte Kästen (Fels und Welle); als Maske bekommen sie eine runde
 * Kante, die aus Rechtecken nicht zu haben ist.
 *
 * Zeichen: `O` Kontur ([BevelPaint.OUTLINE]), `L` hell, `B` Körper,
 * `D` dunkel (die drei Farblagen der Requisite), `K` Riss ([crack]),
 * `W` Weiß, `F` Schaumschatten ([FOAM_SHADE]).
 */
object PropSprites {

    /** Der Findling (FELS): Kuppe links, Riss rechts der Mitte, dunkler Fuß. */
    val BOULDER: List<String> = listOf(
        "....OOOOOOO.....",
        "..OOLLLLLLBOO...",
        ".OLLLLBBBBBBDO..",
        ".OLLBBBBBBBBBDO.",
        "OLLBBBBBBKBBBDO.",
        "OLBBBBBBBBKBBDDO",
        "OLBBBBBBBBBBBDDO",
        "OBBBBBBBBBBBDDDO",
        "ODDBBBBBBBBDDDDO",
        "OOOOOOOOOOOOOOOO"
    )

    /** Der Kiesel neben dem Findling, 8×5. */
    val PEBBLE: List<String> = listOf(
        "..OOOO..",
        ".OLLBBO.",
        "OLLBBBDO",
        "OBBBBDDO",
        "OOOOOOOO"
    )

    /** Die WELLE als Brecher: eingerollte Krone, Schaumkante, zwei Gischt-Pixel. */
    val BREAKER: List<String> = listOf(
        ".......OOOOO.......",
        ".....OOFWWWWOO.....",
        "....OFWWLLLLWWO....",
        "...OLLLBBBBBLWWO...",
        "..OLBBBBOOOOBBWO.W.",
        ".OLBBBBO....OBWO..W",
        ".OLBBBO......OO....",
        "OLBBBBO............",
        "OBBBBBDO...........",
        "OBBBBBDDOOOOOOOOOOO"
    )

    /**
     * Kiesel im Wüstenboden, 7×3: nur die gerundete obere Hälfte schaut
     * aus dem Sand. Farben aus [GroundPaint] (DUNE_PEBBLE_*).
     */
    val DUNE_PEBBLE: List<String> = listOf(
        "..OOO..",
        ".OLLBO.",
        "OLBBBDO"
    )

    /** Schaumschatten in der Krone des Brechers (`F`). */
    const val FOAM_SHADE: Long = 0xFFDFF4FF

    /** Rissfarbe (`K`): die dunkle Lage noch ein Stück zur Kontur. */
    fun crack(dark: Long): Long = BevelPaint.mix(dark, BevelPaint.OUTLINE, 0.35f)
}

/** Form der Partikel im Nebel (siehe [FogPaint.speck]). */
enum class FogSpeck {
    /** Ein einzelnes Korn: Pollen, Staub, Gischt, Ruß, Stern. */
    DOT,

    /** Ein kleines Kreuz: Schneeflocke. Kreuze halten Abstand zueinander. */
    CROSS
}

/**
 * Der Nebel der Kulisse (Twist NEBEL): dieselbe Wolkenform in jeder
 * Welt, aber Farbe und Partikel der Welt — Sandsturm in der Wüste,
 * Sternennebel im Weltraum. Die Form muss überall gleich bleiben, weil
 * sie den Vogel verdecken muss; nur das Aussehen darf wechseln.
 *
 * Die fünf Töne gehen von unten nach oben: [bottom] die unterste
 * Pixelreihe, [low] die zweite, [top] die Oberkante (oben und links),
 * [inner] die Fläche, [mid] die eingestreuten Tupfer darin. [speck] ist
 * die Partikelfarbe, [speckShape] ihre Form. [crown] setzt die obersten
 * zwei Reihen weiß (Schaumkrone am MEER).
 */
data class FogPaint(
    val bottom: Long,
    val low: Long,
    val mid: Long,
    val top: Long,
    val inner: Long,
    val speck: Long,
    val speckShape: FogSpeck = FogSpeck.DOT,
    val crown: Boolean = false
)

/**
 * Eine komplette Kulisse. [cloud] und [ground] sind optional: Im Vakuum
 * gibt es weder Wolken noch Boden, und beides fehlt dort mit Absicht,
 * statt in Grau ausgeblendet zu werden. [backdrop] ist die Ebene hinter
 * allem (Gebirge, Sternenhimmel), null heißt: nur Himmel. [props] darf
 * leer sein — im WELTRAUM treibt nichts vor den Sternen.
 */
class Scene(
    val sky: List<Long>,
    val cloud: Long?,
    val ground: Ground?,
    val props: List<Prop>,
    val backdrop: Backdrop?,
    val fog: FogPaint
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
     * Der Fels als Umriss statt als Stapel.
     *
     * Vorher waren es drei mittig gestapelte Rechtecke, jedes schmaler
     * als das darunter. Bei der kleinsten Requisiten-Größe (0.026 der
     * Bildhöhe) sind das drei Streifen von wenigen Pixeln Höhe — das
     * liest sich als Treppe, nicht als Stein. Genau danach hat auch
     * niemand gefragt: Man sieht einen Klotz und rät.
     *
     * Was einem Klotz zum Fels fehlt, ist zweierlei. Erstens eine
     * unsymmetrische Kante — ein mittig gestapelter Umriss wirkt gebaut,
     * kein Stein liegt so. Die Kuppe sitzt deshalb links der Mitte und
     * die rechte Flanke fällt steiler ab. Zweitens eine Lichtseite: Die
     * obere linke Fläche ist die einzige helle, die rechte Flanke und
     * der Fuß liegen im Schatten. Licht von links oben, wie bei jeder
     * anderen Requisite auch.
     *
     * Die Tabelle steht hier und nicht in den Renderern, weil sie sonst
     * drei Ports von Hand gleich treffen müssten. So füllen sie stumpf
     * Rechtecke — dieselbe Arbeitsteilung wie bei den Requisitenfarben.
     */
    val ROCK_PARTS: List<BlockPart> = listOf(
        BlockPart(-1.20f, 0.00f, 2.40f, 0.42f, 0), // Fuß, im Bodenschatten
        BlockPart(-1.10f, 0.42f, 1.45f, 0.40f, 1), // Mittelbau
        BlockPart(0.35f, 0.42f, 0.75f, 0.40f, 0),  // rechte Flanke, Schatten
        BlockPart(-0.85f, 0.82f, 0.70f, 0.36f, 2), // obere linke Fläche, Licht
        BlockPart(-0.15f, 0.82f, 0.55f, 0.36f, 1), // Übergang zur Schattenseite
        BlockPart(-0.60f, 1.18f, 0.50f, 0.32f, 2)  // Kuppe
    )

    /** Breite des Fels-Umrisses in Vielfachen der Requisiten-Größe. */
    const val ROCK_WIDTH = 2.40f

    /** Höhe des Fels-Umrisses in Vielfachen der Requisiten-Größe. */
    const val ROCK_HEIGHT = 1.50f

    /**
     * Die Laterne — die vierte Requisite der STADT.
     *
     * Ein Findling auf Asphalt bleibt ein Findling, auch mit gutem
     * Umriss. Die Laterne löst ihn an genau einer Stelle ab; die neun
     * übrigen Vorkommen von [PropShape.FELS] bleiben unangetastet. Der
     * WELTRAUM braucht ihn am nötigsten: Dort sind alle vier Requisiten
     * Felsen, und dort dürfen sie als einzige treiben.
     *
     * Drei Regeln haben die Form bestimmt.
     *
     * Erstens: **Sie ist spiegelsymmetrisch, und zwar exakt.** Jedes
     * Stück steht mittig (x = -w/2), die beiden Sprossen sind Spiegel
     * voneinander. Ein Fels darf schief sein — er ist gewachsen. Eine
     * Laterne ist gefertigt, und eine Sprosse, die einen Hauch neben der
     * Mitte sitzt, liest sich sofort als Fehler. Aus demselben Grund
     * sitzen die beiden Lichtkanten hier mittig statt links: Die
     * Hausregel "Licht von links oben" gilt für Baum, Strauch und Fels,
     * aber eine einseitige Lichtkante macht ein symmetrisches Bauwerk
     * wieder schief.
     *
     * Zweitens: **Sie erfindet keine Farbe.** Das Glas trägt den Akzent
     * der Requisite, und der ist in der STADT dasselbe Fenstergelb, mit
     * dem die drei Hochhäuser schon leuchten. Der Kulissen-Test, der
     * jede Farbe gegen die Zielzone prüft, hat nichts Neues zu
     * prüfen. Das Eisen ist dunkles Violettgrau und nicht Schwarz, weil
     * die Kontur 0xFF543847 immer der dunkelste Wert einer Requisite
     * ist — ein Körper, der sie unterbietet, kehrte das Verhältnis um
     * und ließe die Kontur wie einen Lichtsaum aussehen.
     *
     * Drittens: **Sie erfindet kein Zeichenmittel.** Kein Lichtschein,
     * kein Halo, kein weicher Verlauf. Das Spiel kennt genau ein
     * Grundelement — den gefüllten Block mit Kontur. Die Laterne
     * leuchtet, indem ein Block hell ist.
     */
    val LANTERN_PARTS: List<BlockPart> = listOf(
        BlockPart(-0.55f, 0.00f, 1.10f, 0.16f, 0), // Fußplatte, breitester Teil
        BlockPart(-0.42f, 0.16f, 0.84f, 0.14f, 0), // zweite Stufe
        BlockPart(-0.30f, 0.30f, 0.60f, 0.30f, 1), // Sockelkragen
        BlockPart(-0.09f, 0.30f, 0.18f, 0.30f, 2), // dessen Lichtkante, mittig
        BlockPart(-0.16f, 0.60f, 0.32f, 1.70f, 1), // Mast
        BlockPart(-0.05f, 0.60f, 0.10f, 1.70f, 2), // Lichtkante des Masts, mittig
        BlockPart(-0.40f, 2.30f, 0.80f, 0.16f, 0), // Kragen unter der Leuchte
        BlockPart(-0.13f, 2.46f, 0.26f, 0.30f, 1), // Hals
        BlockPart(-0.42f, 2.76f, 0.84f, 0.12f, 0), // Sockel der Leuchte
        BlockPart(-0.36f, 2.88f, 0.72f, 0.62f, 3), // Glas
        BlockPart(-0.19f, 2.88f, 0.09f, 0.62f, 0), // Sprosse links
        BlockPart(0.10f, 2.88f, 0.09f, 0.62f, 0),  // Sprosse rechts, Spiegel
        BlockPart(-0.55f, 3.50f, 1.10f, 0.14f, 0), // Haube, unterer Kranz
        BlockPart(-0.38f, 3.64f, 0.76f, 0.16f, 0), // Haube, obere Stufe
        BlockPart(-0.10f, 3.80f, 0.20f, 0.14f, 0)  // Knauf
    )

    /**
     * Breite der Laterne in Vielfachen der Requisiten-Größe — halb so
     * breit wie der Fels (2.40) und zweieinhalbmal so hoch (1.50).
     * Schmal und hoch, wo der Stein breit und flach ist.
     */
    const val LANTERN_WIDTH = 1.10f

    /** Höhe der Laterne in Vielfachen der Requisiten-Größe. */
    const val LANTERN_HEIGHT = 3.94f

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
     * Mindestabstand eines Himmels zu den Zonensignalen der Bahn (Zone
     * hell und Zone dunkel).
     *
     * Der Wert ist bewusst der Bestand selbst: Das knappste Paar ist die
     * helle Zone vor dem Sandschleier der WÜSTE (Stufe 1) mit 93,9. Damit
     * sagt die Zusicherung nicht "das ist gut", sondern "keine neue
     * Kulisse darf schlechter sein als das Schlechteste, was wir heute
     * zeigen".
     *
     * Die Falle steht hier nicht mehr: Sie ist eine Kette von Minen
     * ([TrapPaint]) mit schwarzer Kugel und hellem Rand, keine Farbfläche.
     * Für sie gilt eine eigene Regel (ScenePaintTest): Vor jedem Himmel
     * muss mindestens eine der beiden Farben klar abstechen.
     */
    const val MIN_SKY_SIGNAL_DISTANCE = 93f

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
    val LEGACY_ZONE_GREENS: List<Long> = listOf(
        0xFF71C837, // BushColor
        0xFF5AA82C, // BushShadeColor
        0xFF9DE85A, // GrassLight
        0xFF74BF2E  // GrassDark
    )

    // ===== Die Kulissen =====

    /**
     * Der Bestand. Jeder Wert stammt aus dem alten Overlay-Code bzw.
     * TimingGameScreen.kt und ist absichtlich unverändert: Wer die
     * Umstellung auf ScenePaint sieht, hat sie falsch gemacht.
     *
     * Mit einer Ausnahme: Die Stufe ab Score 10 war Lila (#7B6FD0) und
     * wurde von Testern für die Falle gehalten. Sie ist jetzt ein tiefes
     * Blau, das zwischen Blau (5+) und Altrosa (15+) noch klar als eigene
     * Stufe zu sehen ist.
     */
    private val WIESE = Scene(
        sky = listOf(
            0xFF4EC0CA, // 0+  Tag (türkis)
            0xFF5B9BD5, // 5+  Blau
            0xFF3F6FC4, // 10+ tiefes Blau (früher Lila, siehe oben)
            0xFFC0616F, // 15+ Altrosa
            0xFFD98A3D, // 20+ Sonnenuntergang
            0xFF3D4A8C, // 25+ Dämmerung
            0xFF2A2640  // 30+ Nacht
        ),
        cloud = 0xFFE9FCFD,
        // Nebel: weiße Wolke mit Blütenpollen.
        fog = FogPaint(0xFFA0BEDA, 0xFFBED4EA, 0xFFD6E5F4, 0xFFFFFFFF, 0xFFF4F8FD, speck = 0xFFFFE89A),
        backdrop = null,
        ground = Ground(
            sand = 0xFFDED895,
            sandShade = 0xFFD3C87E,
            turfDark = 0xFF74BF2E,
            turfLight = 0xFF9DE85A,
            style = GroundStyle.NARBE
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
        sky = listOf(
            0xFFA8DCE8, 0xFFF2C46B, 0xFFE8934A, 0xFFC85F3C,
            0xFF8E3B47, 0xFF4A2C4E, 0xFF241C33
        ),
        cloud = 0xFFF7E9C8,
        // Nebel: Sandsturm mit Staubkörnern.
        fog = FogPaint(0xFFB8894E, 0xFFC9A064, 0xFFD4AE6E, 0xFFF0DDB0, 0xFFE8C88A, speck = 0xFF9C7A4A),
        backdrop = null,
        ground = Ground(
            sand = 0xFFE8C88A,
            sandShade = 0xFFD4AE6E,
            turfDark = 0xFFC79A55,
            turfLight = 0xFFEFD7A0,
            style = GroundStyle.DUENE
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
                PropShape.FELS, 0.026f, 0f,
                dark = 0xFF8A6A4A, body = 0xFFA88860, light = 0xFFC4A87C
            )
        )
    )

    /**
     * Meer: der Boden ist Wasser, die Narbe darauf ist Schaum. Zwischen
     * den Wellen treiben Inseln mit Palmen vorbei. Die Palmwedel sind
     * blaustichig grün wie die Kakteen der WÜSTE — ein Wiesengrün käme
     * der Zielzone zu nah.
     */
    private val MEER = Scene(
        sky = listOf(
            0xFF5AD2E8, 0xFF2F9AD4, 0xFF2E5FB8, 0xFFC4707C,
            0xFFE09A4A, 0xFF35447F, 0xFF1B2138
        ),
        cloud = 0xFFDFF4FF,
        // Nebel: Seenebel mit Schaumkrone und Gischt.
        fog = FogPaint(0xFF1F7A96, 0xFF3FA0B8, 0xFF6CC4D2, 0xFFE8FAFC, 0xFF9ED8E0, speck = 0xFFFFFFFF, crown = true),
        backdrop = null,
        ground = Ground(
            sand = 0xFF2F86C8,
            sandShade = 0xFF24699E,
            turfDark = 0xFF4FC3DE,
            turfLight = 0xFFBFE9FF,
            style = GroundStyle.WELLEN
        ),
        props = listOf(
            Prop(
                PropShape.INSEL, 0.062f, 1.0f,
                dark = 0xFF1F6B41, body = 0xFF2E8B57, light = 0xFFEBD49A,
                stem = 0xFF9C6B3C, stemShade = 0xFF7A4E2A,
                accents = listOf(0xFFCFB277)
            ),
            Prop(
                PropShape.WELLE, 0.022f, 0.8f,
                dark = 0xFF1F5FA8, body = 0xFF2E86D8, light = 0xFF7FC8F0,
                accents = listOf(0xFFDFF4FF, 0xFFFFFFFF)
            ),
            Prop(
                PropShape.INSEL, 0.046f, -1.0f,
                dark = 0xFF1F6B41, body = 0xFF2E8B57, light = 0xFFEBD49A,
                stem = 0xFF9C6B3C, stemShade = 0xFF7A4E2A,
                accents = listOf(0xFFCFB277)
            ),
            Prop(
                PropShape.WELLE, 0.028f, -0.8f,
                dark = 0xFF1F5FA8, body = 0xFF2E86D8, light = 0xFF7FC8F0,
                accents = listOf(0xFFFFFFFF, 0xFFDFF4FF)
            )
        )
    )

    /**
     * Berg: Schnee statt Sand, dahinter zwei Gebirgsketten mit
     * Schneegipfeln, davor verschneite Tannen. Der Akzent der Tannen ist
     * der Schnee auf ihren Lagen.
     */
    private val BERG = Scene(
        sky = listOf(
            0xFFA8D8E8, 0xFF6FAFD8, 0xFF4A7FC0, 0xFF8A5A6E,
            0xFFD08A5A, 0xFF3E4A78, 0xFF1E2438
        ),
        cloud = 0xFFF2FAFF,
        // Nebel: graue Nebelwand mit Schneeflocken.
        fog = FogPaint(0xFF5F6B7A, 0xFF7C8898, 0xFF98A3B1, 0xFFD7DEE6, 0xFFB4BDC8, speck = 0xFFFFFFFF, speckShape = FogSpeck.CROSS),
        backdrop = Backdrop(
            BackdropKind.GEBIRGE,
            listOf(
                0xFF9DB0CC, 0xFF8397B8, // ferne Kette, im Dunst
                0xFF66789A, 0xFF52627F, // nahe Kette
                0xFFF4F8FC, 0xFFD2DEEA  // Schnee, Schneeschatten
            )
        ),
        ground = Ground(
            sand = 0xFFE4EDF4,
            sandShade = 0xFFCBD8E4,
            turfDark = 0xFFA8B8C8,
            turfLight = 0xFFFFFFFF,
            style = GroundStyle.SCHNEE
        ),
        props = listOf(
            Prop(
                PropShape.NADELBAUM, 0.075f, 1.0f,
                dark = 0xFF1E5140, body = 0xFF2A6B52, light = 0xFFF4F8FC,
                stem = 0xFF5C4130, stemShade = 0xFF46311F,
                accents = listOf(0xFFF4F8FC)
            ),
            Prop(
                PropShape.NADELBAUM, 0.040f, 0.6f,
                dark = 0xFF1E5140, body = 0xFF2A6B52, light = 0xFFF4F8FC,
                stem = 0xFF5C4130, stemShade = 0xFF46311F,
                accents = listOf(0xFFF4F8FC)
            ),
            Prop(
                PropShape.NADELBAUM, 0.058f, -1.0f,
                dark = 0xFF1E5140, body = 0xFF2A6B52, light = 0xFFF4F8FC,
                stem = 0xFF5C4130, stemShade = 0xFF46311F,
                accents = listOf(0xFFF4F8FC)
            ),
            Prop(
                PropShape.NADELBAUM, 0.032f, 0.4f,
                dark = 0xFF1E5140, body = 0xFF2A6B52, light = 0xFFF4F8FC,
                stem = 0xFF5C4130, stemShade = 0xFF46311F,
                accents = listOf(0xFFF4F8FC)
            )
        )
    )

    /**
     * Stadt: Asphalt statt Wiese, Bordstein statt Grasnarbe. Die
     * Hochhäuser haben Windanteil 0 — ein wankendes Haus wäre ein Witz,
     * den das Spiel an dieser Stelle nicht macht.
     */
    private val STADT = Scene(
        sky = listOf(
            0xFF9ED4E4, 0xFF5F9BC8, 0xFF4A6AA8, 0xFFC4707E,
            0xFFE8963C, 0xFF3A3F6E, 0xFF1A1A2E
        ),
        cloud = 0xFFE4E8F0,
        // Nebel: Smog mit Ruß.
        fog = FogPaint(0xFF7E828C, 0xFF969AA3, 0xFFADB0B8, 0xFFD6D8DC, 0xFFC4C6CC, speck = 0xFF5E616A),
        backdrop = null,
        ground = Ground(
            sand = 0xFF4A4550,
            sandShade = 0xFF383340,
            turfDark = 0xFF6E6878,
            turfLight = 0xFF9A93A4,
            style = GroundStyle.BORDSTEIN
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
            // Der vierte Platz: eine Laterne statt eines Findlings.
            // Sie steht so still wie die Häuser und trägt als Akzent
            // dasselbe Fenstergelb, mit dem sie schon leuchten. Nur ein
            // Akzent, nicht zwei: Auf einer Straße brennen alle Laternen
            // in derselben Farbe — hier zu wechseln hieße, Abwechslung
            // zu behaupten, wo Gleichförmigkeit die Wahrheit ist.
            Prop(
                PropShape.LATERNE, 0.026f, 0f,
                dark = 0xFF3A3446, body = 0xFF4C4560, light = 0xFF766E8C,
                accents = listOf(0xFFFFD847)
            )
        )
    )

    /**
     * Weltraum: kein Boden, keine Wolken, keine Requisiten — nur der
     * Sternenhimmel mit Galaxien. Früher trieben hier Felsbrocken auf
     * Bodenhöhe; im Spiel lasen sie sich als graue Wolken.
     */
    private val WELTRAUM = Scene(
        sky = listOf(
            // Der Weltraum bleibt dunkel: Der Verlauf läuft über Blau-
            // Töne bis zu dunklem Wein, nie bis ins Abendrot. Eine helle
            // Stufe sah aus wie ein Sonnenuntergang mit Sternen daneben.
            // Die Stufen 2 und 3 waren Violett und Magenta (#3E1A78,
            // #6A1E6E) und damit mit der Falle verwechselbar; sie sind
            // jetzt Blau und Nachtblau.
            0xFF0E1430, 0xFF1A2A62, 0xFF243A8C, 0xFF1C2458,
            0xFF8A2C4A, 0xFF3A1A3E, 0xFF0A0716
        ),
        cloud = null,
        // Nebel: Sternennebel.
        fog = FogPaint(0xFF5B3A7A, 0xFF7A4E9A, 0xFFB06FB8, 0xFFF2C4E8, 0xFFD69AD6, speck = 0xFFFFFFFF),
        backdrop = Backdrop(
            BackdropKind.STERNENHIMMEL,
            listOf(
                0xFFFFFFFF, 0xFFFFE8A8, 0xFFA8D8FF, // Sterne: weiß, warm, kühl
                0xFFFFF4D8,                         // Galaxienkern
                0xFF7FA8E8, 0xFFE89AB8              // Arme: Blau, Rosé
            )
        ),
        ground = null,
        props = emptyList()
    )

    /**
     * Alle Kulissen in Sammlungs-Reihenfolge — der Gegenpart zu
     * [SkinPaint.ORDER], und aus demselben Grund eine `List`.
     */
    val ORDER: List<SceneId> = SceneId.entries.toList()

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
    fun sky(id: SceneId): List<Long> = of(id).sky

    /** Himmelsfarbe zu einem Score — der Weg, den alle Renderer gehen. */
    fun skyFor(id: SceneId, score: Int): Long = of(id).sky[SkinPaint.skyStage(score)]

    /** Wolkenfarbe, null = diese Kulisse hat keine Wolken. */
    fun cloud(id: SceneId): Long? = of(id).cloud

    /** Bodenstreifen, null = diese Kulisse hat keinen Boden (WELTRAUM). */
    fun ground(id: SceneId): Ground? = of(id).ground

    fun props(id: SceneId): List<Prop> = of(id).props

    // ===== Bahn je Welt (Bevel-Look, Schritt 2) =====

    // Die Bahn gehört weiter nicht zur Kulisse im Sinne des Verkaufs: Zone,
    // Perfekt-Kern und Minen sehen in jeder Welt gleich aus. Was wechselt,
    // ist nur das Material der normalen Blöcke und ein Motiv von ein paar
    // Pixeln auf der grünen Zone. Werte wie im Prototyp
    // (docs/bevel-prototyp.patch, trackLook), der die Zielbilder gerendert hat.

    /**
     * Die WIESE behält ihre Bahn: Fläche wie bisher (GroundSandShade), nur
     * mit Kante. Kein Muster, deshalb kein Akzent.
     */
    private val TRACK_WIESE = TrackStyle(
        block = 0xFFD3C87E, light = 0xFFF1EBB5, dark = 0xFFB0A55E,
        pattern = BlockPattern.GLATT, accent = 0x00000000, motif = ZoneMotif.WIESE
    )

    /** Sandstein mit waagrechter Fuge in der Schattenfarbe. */
    private val TRACK_WUESTE = TrackStyle(
        block = 0xFFE3B26A, light = 0xFFF6D59A, dark = 0xFFB07A3A,
        pattern = BlockPattern.FUGE, accent = 0xFFB07A3A, motif = ZoneMotif.KAKTUS
    )

    /** Treibholz-Planke mit zwei Maserungspunkten. */
    private val TRACK_MEER = TrackStyle(
        block = 0xFFB9844F, light = 0xFFD9A873, dark = 0xFF7E5530,
        pattern = BlockPattern.PLANKE, accent = 0xFF8E6038, motif = ZoneMotif.SEEROSE
    )

    /** Fels mit Schneekappe. */
    private val TRACK_BERG = TrackStyle(
        block = 0xFF9AA0AA, light = 0xFFC4C9D1, dark = 0xFF6B707C,
        pattern = BlockPattern.SCHNEEKAPPE, accent = 0xFFFFFFFF, motif = ZoneMotif.EDELWEISS
    )

    /** Betonplatte mit zwei Nieten. */
    private val TRACK_STADT = TrackStyle(
        block = 0xFFB9BCC4, light = 0xFFDDE0E6, dark = 0xFF7E828C,
        pattern = BlockPattern.NIETEN, accent = 0xFF7E828C, motif = ZoneMotif.LED
    )

    /** Metallpanel mit blauem Lämpchen. */
    private val TRACK_WELTRAUM = TrackStyle(
        block = 0xFFC9D2E2, light = 0xFFF0F4FA, dark = 0xFF8490A8,
        pattern = BlockPattern.LAEMPCHEN, accent = 0xFF7FD4FF, motif = ZoneMotif.KRISTALL
    )

    /** Die Bahn einer Welt. */
    fun track(id: SceneId): TrackStyle = when (id) {
        SceneId.WIESE -> TRACK_WIESE
        SceneId.WUESTE -> TRACK_WUESTE
        SceneId.MEER -> TRACK_MEER
        SceneId.BERG -> TRACK_BERG
        SceneId.STADT -> TRACK_STADT
        SceneId.WELTRAUM -> TRACK_WELTRAUM
    }

    // Motivfarben. Die Zonentöne stehen hier als Werte, weil :core die
    // Palette aus :ui (Palette.kt) nicht kennt; die Kommentare nennen den
    // Namen dort.
    private const val WHITE: Long = 0xFFFFFFFF
    private const val GOLD: Long = 0xFFFFD847          // DotBody
    private const val CACTUS_BLOSSOM: Long = 0xFFFFE08A
    private const val LEAF_STEM: Long = 0xFF579A1F     // GrassEdge
    private const val DROP: Long = 0xFF7FD8F0
    private const val PEBBLE: Long = 0xFFA8ACB4
    private const val MOSS_LIGHT: Long = 0xFF9DE85A    // GrassLight
    private const val MOSS_DEEP: Long = 0xFF5AA82C     // GrassDeep
    private const val LED_GREEN: Long = 0xFFC8FF9A

    private fun px(color: Long, vararg at: Pair<Int, Int>): List<MotifPixel> =
        at.map { (dx, dy) -> MotifPixel(dx, dy, color) }

    private val PLUS = arrayOf(0 to -1, -1 to 0, 1 to 0, 0 to 1)
    private val DIAGONALS = arrayOf(-1 to -1, 1 to 1, 1 to -1, -1 to 1)

    // Die Kern-Akzente tragen kein Rot und kein Rosa: main hat die rosa
    // Blüte aus dem Kern entfernt, weil Tester sie für eine Warnung
    // hielten. Weiß und Gold lesen sich als "genau hier", nicht als
    // "Vorsicht" (docs/bevel-look.md, Abschnitt 0, Punkt 5).
    private val MOTIF_KAKTUS = px(WHITE, -1 to -1, 1 to 0, -1 to 1)
    private val MOTIF_KAKTUS_CORE = px(CACTUS_BLOSSOM, *PLUS) + px(WHITE, 0 to 0)
    private val MOTIF_SEEROSE = px(LEAF_STEM, 0 to -1, 0 to -2) + px(DROP, -1 to 1)
    private val MOTIF_SEEROSE_CORE = px(WHITE, *PLUS) + px(GOLD, 0 to 0)
    private val MOTIF_EDELWEISS = px(PEBBLE, -1 to 0) + px(MOSS_LIGHT, 1 to -1) + px(MOSS_DEEP, 0 to 1)
    private val MOTIF_EDELWEISS_CORE = px(WHITE, *PLUS, *DIAGONALS) + px(GOLD, 0 to 0)
    private val MOTIF_LED = px(LED_GREEN, -1 to -1, 1 to -1, -1 to 1, 1 to 1)
    private val MOTIF_LED_CORE = px(WHITE, *Array(9) { (it % 3 - 1) to (it / 3 - 1) })
    private val MOTIF_KRISTALL = px(LED_GREEN, *Array(4) { i -> (i - 1) to (-(i - 1) + 1) })
    private val MOTIF_KRISTALL_CORE = px(WHITE, *Array(5) { i -> (i - 2) to -(i - 2) }) + px(WHITE, -1 to -1)

    /**
     * Die Pixel eines Zonen-Motivs, um die Mitte der Grasfläche; [core]
     * wählt den Akzent des Perfekt-Kerns. Die WIESE liefert eine leere
     * Liste — dort zeichnet der Renderer Blätter und Tupfer wie bisher.
     * Die Listen stehen fest, damit pro Frame nichts angelegt wird.
     */
    fun motif(m: ZoneMotif, core: Boolean): List<MotifPixel> = when (m) {
        ZoneMotif.WIESE -> emptyList()
        ZoneMotif.KAKTUS -> if (core) MOTIF_KAKTUS_CORE else MOTIF_KAKTUS
        ZoneMotif.SEEROSE -> if (core) MOTIF_SEEROSE_CORE else MOTIF_SEEROSE
        ZoneMotif.EDELWEISS -> if (core) MOTIF_EDELWEISS_CORE else MOTIF_EDELWEISS
        ZoneMotif.LED -> if (core) MOTIF_LED_CORE else MOTIF_LED
        ZoneMotif.KRISTALL -> if (core) MOTIF_KRISTALL_CORE else MOTIF_KRISTALL
    }

    /**
     * Drei Farben für Vorschau-Kacheln: Tageshimmel, Boden (im Weltraum
     * ersatzweise die Nachtstufe) und die Körperfarbe der größten
     * Requisite (im Weltraum, der keine hat, die Sternfarbe). Mehr braucht eine 36-dp-Kachel nicht, um erkennbar zu
     * sein — und weniger wäre nicht unterscheidbar.
     */
    fun chips(id: SceneId): List<Long> {
        val scene = of(id)
        return listOf(
            scene.sky[0],
            scene.ground?.sand ?: scene.sky[6],
            scene.props.firstOrNull()?.body ?: scene.backdrop!!.colors[0]
        )
    }

    // ===== Freischaltung =====

    /**
     * Ist diese Welt offen? Offen ist eine Welt, wenn sie in der
     * Besitz-Menge steht ([SkinStats.ownedScenes]) **oder** ihre Regel
     * erfüllt ist.
     *
     * Die Besitz-Menge ist der Bestandsschutz: Bis zur Welten-Leiter
     * wurden Freischaltungen nicht gespeichert, sondern bei jedem Aufruf
     * aus den Zahlen berechnet. Seit die STADT Rekord 100 statt 85
     * verlangt, würde ein Spieler mit Rekord 90 sie mit dem Update
     * verlieren. Die Menge hält fest, was einmal offen war, und die
     * Regel ([ruleMet]) gilt nur noch für das, was neu hinzukommt.
     */
    fun isUnlocked(id: SceneId, stats: SkinStats): Boolean =
        id.name in stats.ownedScenes || ruleMet(id, stats)

    /**
     * Die Welten-Leiter: je Welt eine Achse, und alle früh genug, dass
     * man die zweite Welt in den ersten Tagen sieht. WÜSTE fällt mit dem
     * Skin TIGER (100 Läufe), MEER mit BASKETBALL (2.500 Punkte)
     * zusammen — das ist gewollt, beides wird gemeinsam gefeiert.
     *
     * Der WELTRAUM ist der Abschluss der Sammlung, wie der REGENBOGEN bei
     * den Skins: Er kommt erst, wenn alle anderen offen sind — gefragt
     * über [isUnlocked], also samt Besitz-Menge. Wer die STADT aus dem
     * Bestand behält, bekommt damit auch den WELTRAUM nicht
     * weggenommen. Er selbst zählt nicht mit, sonst wäre die Bedingung
     * zirkulär.
     */
    fun ruleMet(id: SceneId, stats: SkinStats): Boolean = when (id) {
        SceneId.WIESE -> true
        SceneId.WUESTE -> stats.runCount >= 100
        SceneId.MEER -> stats.totalScore >= 2_500
        SceneId.BERG -> stats.bestDailyStreak >= 1
        SceneId.STADT -> stats.bestScore >= 100
        SceneId.WELTRAUM -> SceneId.entries.all {
            it == SceneId.WELTRAUM || isUnlocked(it, stats)
        }
    }

    /**
     * Die Schwellen vor der Welten-Leiter (500 Läufe, 10.000 Punkte,
     * Daily-Serie 30, Rekord 85, WELTRAUM alle anderen) — eingefroren und
     * nur für einen einzigen Schritt da: die einmalige Übernahme in die
     * Besitz-Menge beim ersten Start nach dem Update (GameStore). Die
     * Besitz-Menge spielt hier absichtlich keine Rolle, gefragt ist allein,
     * was der alte Stand hergab.
     *
     * Nicht ändern: Diese Funktion beschreibt die Vergangenheit.
     */
    fun legacyUnlocked(id: SceneId, stats: SkinStats): Boolean = when (id) {
        SceneId.WIESE -> true
        SceneId.WUESTE -> stats.runCount >= 500
        SceneId.MEER -> stats.totalScore >= 10_000
        SceneId.BERG -> stats.bestDailyStreak >= 30
        SceneId.STADT -> stats.bestScore >= 85
        SceneId.WELTRAUM -> SceneId.entries.all {
            it == SceneId.WELTRAUM || legacyUnlocked(it, stats)
        }
    }

    /**
     * Die Namen aller offenen Welten — das, was ein Speicher in die
     * Besitz-Menge schreibt. Reihenfolge wie [ORDER].
     */
    fun unlockedNames(stats: SkinStats): List<String> =
        ORDER.filter { isUnlocked(it, stats) }.map { it.name }

    /**
     * Die Namen aller Welten, die nach den alten Schwellen offen waren
     * ([legacyUnlocked]) — die einmalige Übernahme.
     */
    fun legacyUnlockedNames(stats: SkinStats): List<String> =
        ORDER.filter { legacyUnlocked(it, stats) }.map { it.name }

    /** Wie viele Kulissen offen sind — reine Leistungsanzeige. */
    fun unlockedCount(stats: SkinStats): Int =
        SceneId.entries.count { isUnlocked(it, stats) }

    /** Kulisse zu einem gespeicherten Namen, WIESE als Fallback. */
    fun fromName(name: String?): SceneId =
        SceneId.entries.firstOrNull { it.name == name } ?: SceneId.WIESE
}
