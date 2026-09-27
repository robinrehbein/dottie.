package de.robinrehbein.punkt.game

/**
 * Was sich in einer Welt bewegt, ohne Requisite zu sein: Vögel, Boote,
 * Autos, Schnee. Jede Welt hat zwei bis drei davon ([Scene.life]).
 *
 * Wie die Requisiten sind sie Daten und kein Zeichencode — [Life] sagt
 * nur, WAS unterwegs ist und in welchen Farben. Wo und wann, rechnet der
 * Renderer aus der Laufuhr ([TimingGame.elapsed]) und einem festen Hash,
 * ohne Zufallsquelle: Zwei Bilder zur selben Zeit sind dieselben Bilder.
 *
 * Für alle gilt dieselbe Lesbarkeitsregel wie für die Kulisse: Nichts
 * davon kreuzt das Ringband. Unterwegs ist es im Himmelsstreifen über
 * dem Ring, im Band zwischen Ring und Boden oder auf dem Boden selbst.
 */
enum class LifeKind {
    /** WIESE, am Tag: ein kleiner Vogelschwarm in Keilformation. Farbe: Silhouette. */
    SCHWARM,

    /** WIESE, in der Nacht: Glühwürmchen über dem Gras. Farben: Kern, Schein. */
    GLUEHWUERMCHEN,

    /** WÜSTE: ein Geier kreist hoch oben. Farben: Körper, Schwingen, Kopf. */
    GEIER,

    /** WÜSTE: ein Steppenläufer rollt über den Boden. Farben: Körper, dunkel, hell. */
    STEPPENLAEUFER,

    /** MEER: Möwen segeln über den Himmel. Farben: Gefieder, Flügelspitzen, Schnabel. */
    MOEWEN,

    /**
     * MEER: ein Segelboot kreuzt am Horizont. Farben: Segel, Segelschatten,
     * Rumpf, Rumpfschatten, Laterne (nachts).
     */
    SEGELBOOT,

    /** MEER: ab und zu springt ein Delfin. Farben: Rücken, Bauch, Kontur, Gischt. */
    DELFIN,

    /** BERG: leiser Schneefall. Farben: Flocke, kleine Flocke. */
    SCHNEEFALL,

    /**
     * STADT: ein Flugzeug zieht über den Himmel. Farben: Rumpf, Schatten,
     * Streifen, Positionslicht links, Positionslicht rechts.
     */
    FLUGZEUG,

    /**
     * STADT: Autos auf der Straße. Farben: Lack eins, sein Schatten, Lack
     * zwei, sein Schatten, Scheibe, Reifen, Scheinwerfer, Rücklicht.
     */
    AUTO,

    /**
     * WELTRAUM: ein Ringplanet mit Mond. Farben: Planet, Schattenseite,
     * Band, Ring, Ringschatten, Mond.
     */
    PLANET,

    /** WELTRAUM: ein Satellit zieht vorbei. Farben: Rumpf, Solarzelle, ihr Licht, Blinklicht. */
    SATELLIT
}

/** Ein Bewohner einer Welt; die Farben folgen der Reihenfolge bei [LifeKind]. */
data class Life(
    val kind: LifeKind,
    val colors: List<Long>
)

/**
 * Der Tageslauf einer Welt. Der Himmel läuft mit dem Score durch sieben
 * Stufen ([SkinPaint.skyStage]) vom Tag über den Abend in die Nacht —
 * alles andere in der Welt folgt ihm von hier aus: Dunst auf der fernen
 * Ebene, getönte Wolken, Sonne, Mond und Sterne, der Schleier über dem
 * Vordergrund und die Lichter, die nachts angehen.
 *
 * Stufe 0 ist für jede Welt genau der Bestand: kein Dunst, keine Tönung,
 * kein Schleier. Wer die Wiese am Tag ansieht, sieht sie wie vorher.
 *
 * Die abgeleiteten Farben sind je Welt und Stufe einmal gerechnet
 * ([backdrop], [cloud]); der Renderer fragt sie in jedem Bild ab, und
 * die Oklab-Mischung ([BevelPaint.mix]) wäre dafür zu teuer.
 */
object DayCycle {

    /** Wie viel Nacht in einer Himmelsstufe steckt: 0 = Tag, 1 = tiefe Nacht. */
    private val NIGHT = floatArrayOf(0f, 0f, 0f, 0.1f, 0.3f, 0.7f, 1f)

    /** Anteil des Himmels, der sich über die ferne Ebene legt (Luftdunst). */
    private val HAZE = floatArrayOf(0f, 0.12f, 0.18f, 0.28f, 0.32f, 0.5f, 0.62f)

    /** Anteil des Himmels in der Wolkenfarbe: abends warm, nachts dunkel. */
    private val CLOUD_TINT = floatArrayOf(0f, 0.05f, 0.1f, 0.25f, 0.3f, 0.5f, 0.62f)

    /** Deckkraft des Schleiers über Requisiten und Boden in tiefer Nacht. */
    const val VEIL_MAX = 0.3f

    /** 0 = Tag, 1 = tiefe Nacht. */
    fun night(stage: Int): Float = NIGHT[stage.coerceIn(0, 6)]

    /** Himmelsstufen, in denen die Sonne tief am Horizont steht. */
    fun isDusk(stage: Int): Boolean = stage == 3 || stage == 4

    /** Himmelsstufen, in denen der Mond statt der Sonne am Himmel steht. */
    fun isMoon(stage: Int): Boolean = stage >= 5

    /** Deckkraft des Nachtschleiers über dem Vordergrund. */
    fun veil(stage: Int): Float = night(stage) * VEIL_MAX

    /** Farbe des Nachtschleiers: der Himmel, zur Kontur hin abgedunkelt. */
    fun veilColor(id: SceneId, stage: Int): Long = veils[id.ordinal][stage.coerceIn(0, 6)]

    /**
     * Die Farben der fernen Ebene ([Scene.backdrop]) in dieser Stufe, mit
     * dem Himmel als Dunst darüber. Der Sternenhimmel bleibt, wie er ist:
     * Sterne stehen vor dem Himmel, nicht in ihm.
     */
    fun backdrop(id: SceneId, stage: Int): List<Long> = backdrops[id.ordinal][stage.coerceIn(0, 6)]

    /** Die Wolkenfarbe in dieser Stufe, oder null, wo es keine Wolken gibt. */
    fun cloud(id: SceneId, stage: Int): Long? = clouds[id.ordinal][stage.coerceIn(0, 6)]

    /**
     * Die Lichtkante der Wolken in dieser Stufe: am Tag Weiß, abends und
     * nachts mit dem Himmel getönt — eine weiße Kante an einer grauen
     * Nachtwolke leuchtete wie ein Rahmen.
     */
    fun cloudLight(id: SceneId, stage: Int): Long = cloudLights[id.ordinal][stage.coerceIn(0, 6)]

    // ===== Sonne, Mond und Sterne (in jeder Welt dieselben) =====

    const val SUN: Long = 0xFFFFE27A
    const val SUN_LIGHT: Long = 0xFFFFF6C8
    const val SUN_SHADE: Long = 0xFFF5C04A
    const val SUN_LOW: Long = 0xFFFFA64D
    const val SUN_LOW_LIGHT: Long = 0xFFFFCB8A
    const val SUN_LOW_SHADE: Long = 0xFFF0843C
    const val MOON: Long = 0xFFF2F0E0
    const val MOON_SHADE: Long = 0xFFC8C8D4
    const val NIGHT_STAR: Long = 0xFFFFFFFF

    /** Alle Farben, die der Tageslauf selbst mitbringt — für die Prüfungen. */
    val OWN_COLORS: List<Long> = listOf(
        SUN, SUN_LIGHT, SUN_SHADE, SUN_LOW, SUN_LOW_LIGHT, SUN_LOW_SHADE, MOON, MOON_SHADE, NIGHT_STAR
    )

    private val backdrops: List<List<List<Long>>> by lazy {
        SceneId.entries.map { id ->
            val scene = ScenePaint.of(id)
            (0..6).map { stage ->
                val b = scene.backdrop ?: return@map emptyList()
                // Das Meer am Horizont nimmt nur halb so viel Dunst an:
                // Voll getönt wurde es vor dem Abendhimmel ein stumpfes Grau.
                val haze = HAZE[stage] * if (b.kind == BackdropKind.HORIZONT) 0.5f else 1f
                if (b.kind == BackdropKind.STERNENHIMMEL || haze == 0f) {
                    b.colors
                } else {
                    b.colors.map { BevelPaint.mix(it, scene.sky[stage], haze) }
                }
            }
        }
    }

    private val clouds: List<List<Long?>> by lazy {
        SceneId.entries.map { id ->
            val scene = ScenePaint.of(id)
            (0..6).map { stage ->
                scene.cloud?.let { c ->
                    if (CLOUD_TINT[stage] == 0f) c else BevelPaint.mix(c, scene.sky[stage], CLOUD_TINT[stage])
                }
            }
        }
    }

    private val cloudLights: List<List<Long>> by lazy {
        SceneId.entries.map { id ->
            val scene = ScenePaint.of(id)
            (0..6).map { stage ->
                if (CLOUD_TINT[stage] == 0f) WHITE else BevelPaint.mix(WHITE, scene.sky[stage], CLOUD_TINT[stage])
            }
        }
    }

    private const val WHITE: Long = 0xFFFFFFFF

    private val veils: List<List<Long>> by lazy {
        SceneId.entries.map { id ->
            val scene = ScenePaint.of(id)
            (0..6).map { stage -> BevelPaint.mix(scene.sky[stage], BevelPaint.OUTLINE, 0.5f) }
        }
    }
}
