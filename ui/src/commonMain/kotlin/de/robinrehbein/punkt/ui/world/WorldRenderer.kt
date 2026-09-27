package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.Ground
import de.robinrehbein.punkt.game.Prop
import de.robinrehbein.punkt.game.PropShape
import de.robinrehbein.punkt.game.PropSprites
import de.robinrehbein.punkt.game.BlockPart
import de.robinrehbein.punkt.game.BlockPattern
import de.robinrehbein.punkt.game.BevelPaint
import de.robinrehbein.punkt.game.TrackStyle
import de.robinrehbein.punkt.game.ZoneMotif
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SkinState
import de.robinrehbein.punkt.game.TimingGame
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Die Spielwelt: Himmel, Wolken, Kulisse, Boden, Perlenketten-Bahn und
 * der Pixel-Vogel — alles als Rechtecke auf einem Canvas.
 *
 * Diese Datei lag bis v2.24 in :app und war damit Android-only; der
 * iOS-Port zeichnete dieselben Rechtecke ein zweites Mal mit SpriteKit.
 * Hier steht sie im geteilten Modul: derselbe Code fuer beide
 * Oberflaechen.
 *
 * Sie kennt weder Texte noch Knoepfe noch Werbung — nur `:core` und
 * Compose-Zeichenbefehle. Genau deshalb laesst sie sich teilen.
 *
 * Himmel, Wolken, Requisiten und Boden kommen aus [ScenePaint]: Die
 * Kulisse ist Daten, kein Zeichencode. Welche Himmelsstufe zu einem Score
 * gehoert, rechnet [SkinPaint.skyStage] — der Zaehler laeuft im Umlauf,
 * nach der Nacht geht es zurueck Richtung Tag.
 */

/**
 * Lage der Kreisbahn im Bild: Mitte und Radius. Eine Quelle für alles,
 * was sich am Ring ausrichten muss (Todesursache, Hinweis, Hand) — damit
 * niemand die Zahlen aus [drawTimingWorld] abschreibt.
 */
data class RingGeometry(val cx: Float, val cy: Float, val radius: Float)

/** Die Kreisbahn für eine Bildgröße: Mitte bei 44 % der Höhe, Radius min(0,36 w; 0,28 h). */
fun ringGeometry(size: Size): RingGeometry = RingGeometry(
    cx = size.width / 2f,
    cy = size.height * 0.44f,
    radius = min(size.width * 0.36f, size.height * 0.28f)
)

fun DrawScope.drawTimingWorld(
    game: TimingGame,
    fx: FxState,
    skin: SkinId,
    scene: SceneId,
    hour: Int,
    month: Int
) {
    val h = size.height
    val w = size.width
    val cell = floor(h / 220f).coerceAtLeast(2f)
    val kulisse = ScenePaint.of(scene)

    // Screen-Shake beim Tod
    val shake = if (fx.shakeTime > 0f) {
        val strength = fx.shakeTime * 28f
        Offset(
            (sin(fx.shakeTime * 91f) * strength),
            (sin(fx.shakeTime * 77f) * strength)
        )
    } else {
        Offset.Zero
    }

    translate(shake.x, shake.y) {
        // Himmel färbt sich mit jeder 5er-Stufe weiter Richtung Nacht —
        // welche sieben Töne das sind, sagt die Kulisse.
        val sky = Color(kulisse.sky[SkinPaint.skyStage(game.score)])
        drawRect(color = sky, topLeft = Offset(-40f, -40f), size = Size(w + 80f, h + 80f))

        // Hinter allem: Gebirge (BERG) oder Sternenhimmel (WELTRAUM).
        // Die Galaxien verblassen gegen genau diese Himmelsstufe.
        drawBackdrop(kulisse.backdrop, game.elapsed, cell, sky)

        // Langsam driftende Wolken. Im Vakuum gibt es keine — dann bleibt
        // der Himmel leer, statt graue Attrappen zu zeigen.
        kulisse.cloud?.let { cloud ->
            val drift = game.elapsed * h * 0.01f
            drawCloud(w * 0.1f - drift % (w * 1.4f), h * 0.16f, cell, Color(cloud))
            drawCloud(w * 0.75f - drift % (w * 1.4f), h * 0.24f, cell, Color(cloud))
        }

        drawScenery(game, cell, kulisse.props)
        kulisse.ground?.let { drawGroundStrip(cell, it) }

        // Kreisbahn mit Zielzone, ggf. Fallen-Zone und Punkt. Worauf
        // getippt wird, sieht überall gleich aus — sonst wäre die Kulisse
        // ein Vorteil: Zone, Kern und Minen bleiben in jeder Welt dieselben.
        // Die Kulisse wechselt nur das Material der normalen Blöcke und ein
        // Motiv von ein paar Pixeln auf der Zone (ScenePaint.track).
        val (cx, cy, radius) = ringGeometry(size)
        drawTrack(game, cx, cy, radius, cell, scene)
        // == AP-22 start ==
        drawStartCoach(game, fx, cx, cy, radius, cell)
        // == /AP-22 ==
        if (game.isDotVisible) {
            drawTimingDot(game, fx, cx, cy, radius, skin, hour, month)
        }
        // == AP-23 nebel ==
        // Die Nebelbank liegt über dem Vogel: Er gleitet an ihrer Kontur
        // hinein und heraus. Grenzen allein aus der Engine.
        drawFogBank(game, cx, cy, radius, game.fogStart(), game.fogEnd(), kulisse.fog)
        drawFogPuffs(game, fx, cx, cy, radius, kulisse.fog)
        // == /AP-23 ==
        if (fx.celebrateTime > 0f) {
            drawUnlockBurst(fx.celebrateTime, cx, cy, radius, cell)
        }
        // == AP-12 bomben ==
        drawTrapBoom(game, fx.deathTime, cx, cy, radius, cell)
        // == /AP-12 ==
    }

    // Weißer Blitz beim Aufprall
    if (fx.flashAlpha > 0f) {
        drawRect(color = Color.White.copy(alpha = fx.flashAlpha.coerceAtMost(1f)))
    }
}

/**
 * Requisiten vor dem Boden. Die Szenerie driftet wie die Wolken nach
 * links — nur schneller, weil sie näher am Betrachter ist (Parallaxe) —
 * und wickelt rechts wieder ein. Dazu wiegt ein leichter Wind sie, pro
 * Requisite phasenversetzt.
 *
 * Welche Requisite an welchem Platz steht, sagt die Kulisse: Die Liste
 * wird zyklisch abgelaufen, genau wie der Bestand bisher k % 4 benutzt
 * hat. Der Akzent (Blütenfarbe, Fensterfarbe) wechselt eine Ebene
 * langsamer, also erst mit der nächsten Wiederholung.
 */
internal fun DrawScope.drawScenery(game: TimingGame, cell: Float, props: List<Prop>) {
    val h = size.height
    val w = size.width
    // Basis knapp unter der Grasnarben-Oberkante — der Boden-Streifen
    // wird danach gezeichnet und verdeckt die Wurzeln sauber.
    val groundY = ScenePaint.groundY(h) + cell * 2f

    // Im WELTRAUM treibt vor den Sternen nichts.
    if (props.isEmpty()) return

    val drift = game.elapsed * h * 0.016f
    // Locker gestellt: zwei, höchstens drei Requisiten zugleich im Bild.
    // Die Abstände schwanken je Platz etwas, damit die Reihe nicht wie
    // gestempelt aussieht. Die Anzahl ist ein Vielfaches der Liste, sonst
    // stünden am Umbruch zwei gleiche Requisiten nebeneinander.
    val spacing = w * PROP_SPACING
    var count = props.size
    while (count * spacing < w + spacing * 2f) count += props.size
    val total = spacing * count
    for (k in 0 until count) {
        val jitter = PROP_JITTER[k % PROP_JITTER.size] * spacing
        val x = ((k * spacing + jitter - drift) % total + total) % total - spacing
        val wind = sin(game.elapsed * 1.4f + k * 1.7f) * cell * 0.6f
        val prop = props[k % props.size]
        val accent = if (prop.accents.isEmpty()) {
            Color.Transparent
        } else {
            Color(prop.accents[(k / props.size) % prop.accents.size])
        }
        drawProp(prop, x, groundY, h * prop.size, wind * prop.sway, cell, accent)
    }
}

/** Abstand der Requisiten als Anteil der Bildbreite (früher 0,26: zu voll). */
private const val PROP_SPACING = 0.42f

/** Feste Versätze je Platz, als Anteil des Abstands. */
private val PROP_JITTER = listOf(0f, 0.18f, -0.12f, 0.08f, -0.2f, 0.14f, -0.05f, 0.1f)

/** Verteilt eine Requisite auf die Zeichnung ihrer Form. */
internal fun DrawScope.drawProp(
    prop: Prop,
    cx: Float,
    groundY: Float,
    s: Float,
    sway: Float,
    cell: Float,
    accent: Color
) {
    val dark = Color(prop.dark)
    val body = Color(prop.body)
    val light = Color(prop.light)
    val stem = Color(prop.stem)
    val stemShade = Color(prop.stemShade)
    when (prop.shape) {
        PropShape.BAUM -> drawPixelTree(cx, groundY, s, sway, cell, dark, body, light, stem, stemShade)
        PropShape.BLUME -> drawPixelFlower(cx, groundY, s, sway, cell, dark, body, light, accent)
        PropShape.STRAUCH -> drawPixelBush(cx, groundY, s, sway, cell, dark, body, light)
        PropShape.KAKTUS -> drawPixelCactus(cx, groundY, s, sway, cell, dark, body, light, accent)
        PropShape.WELLE -> drawBreaker(prop, cx, groundY, cell)
        PropShape.NADELBAUM ->
            drawPixelFir(cx, groundY, s, sway, cell, dark, body, light, stem, stemShade, accent)
        PropShape.HOCHHAUS -> drawPixelTower(cx, groundY, s, cell, dark, body, light, accent)
        // Der Fels ist ein Findling aus PropSprites, keine Kastentabelle
        // mehr. ScenePaint.ROCK_PARTS bleibt für die Uhr und den
        // Paritäts-Vertrag bestehen, das Telefon zeichnet ihn nicht mehr.
        PropShape.FELS -> drawBoulder(prop, cx, groundY, cell)
        PropShape.LATERNE ->
            drawBlockParts(ScenePaint.LANTERN_PARTS, cx, groundY, s, sway, cell,
                dark, body, light, accent)
        PropShape.INSEL ->
            drawPixelIsland(cx, groundY, s, sway, cell, dark, body, light, stem, stemShade, accent)
    }
}

/**
 * Formen mit sich überlappenden Teilen (Kaktus, Hochhaus) brauchen zwei
 * Durchgänge: erst alle Konturen, dann alle Füllungen. Sonst legt die
 * Kontur des einen Blocks einen Balken über die Füllung des anderen.
 */
internal fun DrawScope.drawOutlinedBlocks(cell: Float, blocks: List<Pair<Rect, Color>>) {
    blocks.forEach { (r, _) ->
        drawRect(
            color = OutlineColor,
            topLeft = Offset(r.x - cell, r.y - cell),
            size = Size(r.w + cell * 2f, r.h + cell * 2f)
        )
    }
    // Jede Fläche mit Bevel (Kante eine Zelle): So erben Kaktus, Insel
    // und Palmen die Kante, ohne selbst davon zu wissen.
    blocks.forEach { (r, color) ->
        bevelRect(color, Offset(r.x, r.y), Size(r.w, r.h), cell)
    }
}

/** Rechteck in Weltkoordinaten — nur als Bündel für [drawOutlinedBlocks]. */
internal data class Rect(val x: Float, val y: Float, val w: Float, val h: Float)

/** Pixel-Baum: Stamm mit Schattenseite, dreistufige Krone im Wind. */
internal fun DrawScope.drawPixelTree(
    cx: Float,
    groundY: Float,
    s: Float,
    sway: Float,
    cell: Float,
    dark: Color,
    body: Color,
    light: Color,
    stem: Color,
    stemShade: Color
) {
    val trunkW = s * 0.30f
    val trunkH = s * 0.60f
    drawRect(
        color = OutlineColor,
        topLeft = Offset(cx - trunkW / 2f - cell, groundY - trunkH - cell),
        size = Size(trunkW + cell * 2f, trunkH + cell)
    )
    drawRect(
        color = stem,
        topLeft = Offset(cx - trunkW / 2f, groundY - trunkH),
        size = Size(trunkW, trunkH)
    )
    drawRect(
        color = stemShade,
        topLeft = Offset(cx, groundY - trunkH),
        size = Size(trunkW / 2f, trunkH)
    )

    // Krone: von unten (breit, dunkel) nach oben (schmal, hell); der Wind
    // greift oben stärker.
    val layers = listOf(
        Triple(s * 1.6f, s * 0.45f, dark),
        Triple(s * 1.2f, s * 0.40f, body),
        Triple(s * 0.7f, s * 0.35f, light)
    )
    var layerTop = groundY - trunkH
    layers.forEachIndexed { i, (lw, lh, color) ->
        layerTop -= lh
        val lx = cx + sway * (0.35f + 0.35f * i)
        drawRect(
            color = OutlineColor,
            topLeft = Offset(lx - lw / 2f - cell, layerTop - cell),
            size = Size(lw + cell * 2f, lh + cell * 2f)
        )
        bevelRect(color, Offset(lx - lw / 2f, layerTop), Size(lw, lh), cell)
    }
}

/**
 * Pixel-Strauch: runde Beeren-Silhouette statt Torten-Stufen — der Bauch
 * in der Mitte ist die breiteste Lage, oben sitzt eine helle Kuppe, und
 * zwei Licht-Tupfer geben der Fläche Textur.
 */
internal fun DrawScope.drawPixelBush(
    cx: Float,
    groundY: Float,
    s: Float,
    sway: Float,
    cell: Float,
    dark: Color,
    body: Color,
    light: Color
) {
    val layers = listOf(
        Triple(s * 2.1f, s * 0.55f, dark), // Sockel
        Triple(s * 2.7f, s * 0.70f, body), // Bauch — am breitesten
        Triple(s * 1.5f, s * 0.55f, light) // Kuppe
    )
    var layerTop = groundY
    layers.forEachIndexed { i, (lw, lh, color) ->
        layerTop -= lh
        val lx = cx + sway * (0.2f + 0.3f * i)
        drawRect(
            color = OutlineColor,
            topLeft = Offset(lx - lw / 2f - cell, layerTop - cell),
            size = Size(lw + cell * 2f, lh + cell * 2f)
        )
        bevelRect(color, Offset(lx - lw / 2f, layerTop), Size(lw, lh), cell)
    }

    // Licht-Tupfer auf dem Bauch
    val u = cell * 1.5f
    drawRect(
        color = light,
        topLeft = Offset(cx - s * 1.0f + sway * 0.4f, groundY - s * 1.05f),
        size = Size(u * 2f, u)
    )
    drawRect(
        color = light,
        topLeft = Offset(cx + s * 0.35f + sway * 0.4f, groundY - s * 0.8f),
        size = Size(u, u)
    )
}

/**
 * Pixel-Blume: Stiel mit Blättern und großer Blüte (vier Blütenblätter
 * um eine helle Mitte). Die Blüte wiegt im Wind, der Stiel bleibt unten
 * verwurzelt.
 */
internal fun DrawScope.drawPixelFlower(
    cx: Float,
    groundY: Float,
    s: Float,
    sway: Float,
    cell: Float,
    dark: Color,
    body: Color,
    light: Color,
    petal: Color
) {
    val stemH = s * 1.15f
    val bx = cx + sway
    val by = groundY - stemH

    // Stiel (mit Outline), oben leicht zur Blüte versetzt gezeichnet
    drawRect(
        color = OutlineColor,
        topLeft = Offset(cx - cell * 1.5f, by),
        size = Size(cell * 3f, stemH)
    )
    drawRect(
        color = dark,
        topLeft = Offset(cx - cell * 0.75f, by),
        size = Size(cell * 1.5f, stemH)
    )

    // Zwei Blätter auf halber Höhe
    val leafY = groundY - stemH * 0.45f
    drawRect(
        color = OutlineColor,
        topLeft = Offset(cx - s * 0.6f - cell, leafY - cell),
        size = Size(s * 0.6f + cell * 2f, cell * 3f)
    )
    drawRect(
        color = body,
        topLeft = Offset(cx - s * 0.6f, leafY),
        size = Size(s * 0.6f, cell * 1.5f)
    )
    drawRect(
        color = OutlineColor,
        topLeft = Offset(cx - cell, leafY + cell * 3f),
        size = Size(s * 0.55f + cell * 2f, cell * 3f)
    )
    drawRect(
        color = body,
        topLeft = Offset(cx, leafY + cell * 4f),
        size = Size(s * 0.55f, cell * 1.5f)
    )

    // Blüte: Plus aus vier Blütenblättern um die helle Mitte
    val u = s * 0.38f
    fun block(x: Float, y: Float, color: Color) {
        drawRect(
            color = OutlineColor,
            topLeft = Offset(x - cell, y - cell),
            size = Size(u + cell * 2f, u + cell * 2f)
        )
        bevelRect(color, Offset(x, y), Size(u, u), cell)
    }
    block(bx - u / 2f, by - u * 1.5f, petal)          // oben
    block(bx - u * 1.5f, by - u / 2f, petal)          // links
    block(bx + u / 2f, by - u / 2f, petal)            // rechts
    block(bx - u / 2f, by + u / 2f, petal)            // unten
    block(bx - u / 2f, by - u / 2f, light)            // Mitte
}

/**
 * Kaktus: Säule mit zwei versetzten Armen und einer Blüte obendrauf. Die
 * Arme sitzen auf verschiedenen Höhen — zwei gleich hohe Arme sähen aus
 * wie ein Zeichen, nicht wie eine Pflanze.
 */
internal fun DrawScope.drawPixelCactus(
    cx: Float,
    groundY: Float,
    s: Float,
    sway: Float,
    cell: Float,
    dark: Color,
    body: Color,
    light: Color,
    bloom: Color
) {
    val stemW = s * 0.34f
    val stemH = s * 1.5f
    val armW = s * 0.20f
    val leftY = groundY - stemH * 0.55f
    val rightY = groundY - stemH * 0.78f
    val lean = sway * 0.4f

    drawOutlinedBlocks(
        cell,
        listOf(
            Rect(cx - stemW / 2f, groundY - stemH, stemW, stemH) to body,
            Rect(cx - s * 0.75f + lean, leftY, s * 0.75f, armW) to body,
            Rect(cx - s * 0.75f + lean, leftY - s * 0.45f, armW, s * 0.45f + armW) to body,
            Rect(cx + lean, rightY, s * 0.75f, armW) to body,
            Rect(cx + s * 0.75f - armW + lean, rightY - s * 0.38f, armW, s * 0.38f + armW) to body
        )
    )

    // Schattenseite rechts, Lichtkante links — wie beim Vogel.
    drawRect(
        color = dark,
        topLeft = Offset(cx + stemW * 0.12f, groundY - stemH),
        size = Size(stemW * 0.38f, stemH)
    )
    drawRect(
        color = light,
        topLeft = Offset(cx - stemW / 2f, groundY - stemH),
        size = Size(stemW * 0.26f, stemH * 0.92f)
    )

    val fw = s * 0.26f
    drawRect(
        color = OutlineColor,
        topLeft = Offset(cx - fw / 2f - cell, groundY - stemH - fw - cell),
        size = Size(fw + cell * 2f, fw + cell * 2f)
    )
    drawRect(
        color = bloom,
        topLeft = Offset(cx - fw / 2f, groundY - stemH - fw),
        size = Size(fw, fw)
    )
}

/**
 * Findling (FELS) mit Kiesel daneben, als Pixel-Maske aus
 * [PropSprites.BOULDER] und [PropSprites.PEBBLE]: eine Zelle pro
 * Maskenpixel. Gestapelte Rechtecke lasen sich als Kasten; die Maske
 * bringt die runde Kuppe, Licht oben links, dunklen Fuß und den Riss.
 * Er liegt auf der Bodenkante ([groundY] ist die Requisiten-Basis, zwei
 * Zellen darunter) und wiegt nicht im Wind — Steine tun das nicht.
 */
internal fun DrawScope.drawBoulder(prop: Prop, cx: Float, groundY: Float, cell: Float) {
    val pal = spritePalette(prop)
    val base = groundY - cell * 2f
    val x = floor((cx - 8 * cell) / cell) * cell
    pixelMap(x, base - PropSprites.BOULDER.size * cell, cell, PropSprites.BOULDER, pal)
    pixelMap(x + 14 * cell, base - PropSprites.PEBBLE.size * cell, cell, PropSprites.PEBBLE, pal)
}

/**
 * Brecher (WELLE) aus [PropSprites.BREAKER]: eingerollte Krone mit
 * Schaumkante und Gischt. Der Fuß sitzt zwei Zellen über der Bodenkante,
 * sonst verdeckten ihn die Wellenkämme des Bodens.
 */
internal fun DrawScope.drawBreaker(prop: Prop, cx: Float, groundY: Float, cell: Float) {
    val base = groundY - cell * 2f
    val x = floor((cx - 9 * cell) / cell) * cell
    pixelMap(x, base - (PropSprites.BREAKER.size + 2) * cell, cell, PropSprites.BREAKER, spritePalette(prop))
}

/**
 * Farben der Masken-Zeichen für eine Requisite (siehe [PropSprites]).
 * Die Kulissen sind feste Daten; die Palette wird deshalb je Requisite
 * einmal angelegt und nicht in jedem Frame neu.
 */
private fun spritePalette(prop: Prop): Map<Char, Color> = spritePalettes.getOrPut(prop) {
    mapOf(
        'O' to OutlineColor,
        'L' to Color(prop.light),
        'B' to Color(prop.body),
        'D' to Color(prop.dark),
        'K' to Color(PropSprites.crack(prop.dark)),
        'W' to Color.White,
        'F' to Color(PropSprites.FOAM_SHADE)
    )
}

private val spritePalettes = HashMap<Prop, Map<Char, Color>>()

/**
 * Nadelbaum: schmaler Stamm, drei spitze Lagen, helle Spitze obendrauf.
 * Mit [snow] liegt auf jeder Lage ein Schneestreifen.
 */
internal fun DrawScope.drawPixelFir(
    cx: Float,
    groundY: Float,
    s: Float,
    sway: Float,
    cell: Float,
    dark: Color,
    body: Color,
    light: Color,
    stem: Color,
    stemShade: Color,
    snow: Color = Color.Transparent
) {
    val trunkW = s * 0.22f
    val trunkH = s * 0.30f
    drawRect(
        color = OutlineColor,
        topLeft = Offset(cx - trunkW / 2f - cell, groundY - trunkH - cell),
        size = Size(trunkW + cell * 2f, trunkH + cell)
    )
    drawRect(
        color = stem,
        topLeft = Offset(cx - trunkW / 2f, groundY - trunkH),
        size = Size(trunkW, trunkH)
    )
    drawRect(
        color = stemShade,
        topLeft = Offset(cx, groundY - trunkH),
        size = Size(trunkW / 2f, trunkH)
    )

    val layers = listOf(
        Triple(s * 1.50f, s * 0.42f, dark),
        Triple(s * 1.05f, s * 0.38f, body),
        Triple(s * 0.60f, s * 0.34f, body)
    )
    var layerTop = groundY - trunkH
    var lx = cx
    layers.forEachIndexed { i, (lw, lh, color) ->
        layerTop -= lh
        lx = cx + sway * (0.3f + 0.3f * i)
        drawRect(
            color = OutlineColor,
            topLeft = Offset(lx - lw / 2f - cell, layerTop - cell),
            size = Size(lw + cell * 2f, lh + cell * 2f)
        )
        bevelRect(color, Offset(lx - lw / 2f, layerTop), Size(lw, lh), cell)
        // Schnee auf der Lage (BERG): ein Streifen an der Oberkante,
        // links länger, als wäre er von rechts angeweht.
        if (snow != Color.Transparent) {
            drawRect(
                color = snow,
                topLeft = Offset(lx - lw / 2f, layerTop),
                size = Size(lw * 0.7f, maxOf(cell, lh * 0.22f))
            )
        }
    }

    val tw = s * 0.24f
    val th = s * 0.26f
    lx = cx + sway * 1.2f
    drawRect(
        color = OutlineColor,
        topLeft = Offset(lx - tw / 2f - cell, layerTop - th - cell),
        size = Size(tw + cell * 2f, th + cell * 2f)
    )
    drawRect(color = light, topLeft = Offset(lx - tw / 2f, layerTop - th), size = Size(tw, th))
}

/**
 * Hochhaus: ein Block mit Schattenseite, heller Dachkante und einem
 * Fensterraster. Ohne Wind — ein wankendes Haus wäre ein Witz, den das
 * Spiel an dieser Stelle nicht macht.
 */
internal fun DrawScope.drawPixelTower(
    cx: Float,
    groundY: Float,
    s: Float,
    cell: Float,
    dark: Color,
    body: Color,
    light: Color,
    window: Color
) {
    val w = s * 0.9f
    val hgt = s * 2.4f
    drawRect(
        color = OutlineColor,
        topLeft = Offset(cx - w / 2f - cell, groundY - hgt - cell),
        size = Size(w + cell * 2f, hgt + cell)
    )
    bevelRect(body, Offset(cx - w / 2f, groundY - hgt), Size(w, hgt), cell)
    drawRect(color = dark, topLeft = Offset(cx, groundY - hgt), size = Size(w / 2f, hgt))
    drawRect(color = light, topLeft = Offset(cx - w / 2f, groundY - hgt), size = Size(w, s * 0.16f))

    // Fensterraster: jedes dritte Fenster bleibt dunkel, sonst sähe die
    // Fassade aus wie ein Schachbrett aus Licht.
    val uw = w * 0.22f
    val uh = s * 0.16f
    for (r in 0 until 5) {
        val fy = groundY - hgt + s * 0.34f + r * s * 0.36f
        if (fy + uh > groundY - s * 0.1f) break
        for (c in 0 until 2) {
            val fx = cx - w * 0.30f + c * w * 0.34f
            drawRect(
                color = if ((r + c) % 3 == 0) dark else window,
                topLeft = Offset(fx, fy),
                size = Size(uw, uh)
            )
        }
    }
}

/**
 * Formen, die als Tabelle in :core stehen statt als Zeichencode hier —
 * Fels ([ScenePaint.ROCK_PARTS]) und Laterne ([ScenePaint.LANTERN_PARTS]).
 * Der Renderer füllt stumpf Rechtecke; welche, sagt die Tabelle. Genau
 * deshalb kann keine der beiden Formen zwischen den Ports auseinander
 * laufen, ohne dass der Paritäts-Vertrag es meldet.
 *
 * Erst alle Konturen, dann alle Flächen — sonst schnitte die Kontur
 * eines höheren Stücks in die Fläche des darunterliegenden, und die Form
 * bekäme Fugen, die sie nicht hat.
 */
internal fun DrawScope.drawBlockParts(
    parts: List<BlockPart>,
    cx: Float,
    groundY: Float,
    s: Float,
    sway: Float,
    cell: Float,
    dark: Color,
    body: Color,
    light: Color,
    accent: Color
) {
    fun left(p: BlockPart) = cx + sway * (0.15f + 0.25f * p.y) + p.x * s
    fun top(p: BlockPart) = groundY - (p.y + p.h) * s

    parts.forEach { p ->
        drawRect(
            color = OutlineColor,
            topLeft = Offset(left(p) - cell, top(p) - cell),
            size = Size(p.w * s + cell * 2f, p.h * s + cell * 2f)
        )
    }
    parts.forEach { p ->
        bevelRect(
            when (p.tone) {
                0 -> dark
                1 -> body
                2 -> light
                else -> accent
            },
            Offset(left(p), top(p)),
            Size(p.w * s, p.h * s),
            cell
        )
    }
}

/**
 * Bodenstreifen: der statische Boden unter allem. Welches Muster und
 * welche Farben, sagt die Kulisse (Ground.style, siehe GroundStyles.kt);
 * wo er beginnt, sagt ScenePaint.groundY und sonst niemand.
 */
internal fun DrawScope.drawGroundStrip(cell: Float, ground: Ground) {
    drawGroundStyle(cell, ScenePaint.groundY(size.height), ground)
}

/**
 * Die Kreisbahn als Kette blockiger Zellen. Die Zielzone ist grün mit
 * hellem Perfekt-Kern, die Falle eine Kette aus Minen mit rotem Lauflicht
 * (siehe MineField.kt) — alles im Pixel-Raster.
 *
 * Sand- und Zonenblöcke liegen im Raster der halben Zelle ([trackUnit],
 * auf 1080×2340 fünf Bildpunkte): Kanten, Umrisse, Licht-Kanten und
 * Blätter sind Vielfache davon. Siehe [TrackBlock].
 */
internal fun DrawScope.drawTrack(
    game: TimingGame,
    cx: Float,
    cy: Float,
    radius: Float,
    cell: Float,
    scene: SceneId
) {
    val style = ScenePaint.track(scene)
    // 60 statt 72 Segmente: Die einzelnen Kettenglieder bekommen sichtbaren
    // Abstand (Perlenketten-Look), statt sich zu überlappen. Die Zonen
    // bleiben durch ihre größeren Blöcke bewusst ein durchgehendes Band.
    val segments = TRACK_SEGMENTS
    val zoneHalf = game.effectiveZoneHalf()
    // Minen so groß wie möglich, aber nie so groß, dass sie sich oder den
    // Sand daneben berühren (unter PULS rückt die Kette zusammen). Sie
    // liegen im Takt der Blöcke auf den Plätzen, die die Falle frei lässt.
    val trap = trapLayout(game, segments, zoneHalf, radius, cell)
    val minePx = trap.px
    val mines = trap.mines
    val mineCenters = mines.map { Offset(cx + cos(it.angle) * radius, cy + sin(it.angle) * radius) }
    // Die Zone wird zur Mitte hin größer (zoneBlockScale). Kleine Blöcke
    // zuerst gezeichnet, damit die größeren oben liegen.
    val zoneBlocks = zoneSlots(game, cx, cy, radius, cell)
    // Erst der goldene Saum des Perfekt-Kerns, dann alle Blöcke: Die
    // Nachbarn überdecken ihn, sichtbar bleibt er nur außen um den Kern.
    // Er ist eine Rasterstufe breit und folgt der gerundeten Außenkante.
    for (z in zoneBlocks) {
        if (!z.core) continue
        val b = z.block
        drawRect(
            color = ZoneCoreHalo,
            topLeft = Offset(b.left - b.unit, b.top - b.unit),
            size = Size(b.outer + 2f * b.unit, b.outer + 2f * b.unit)
        )
    }
    for (k in 0 until segments) {
        val a = trackSlotAngle(k, segments)
        val px = cx + cos(a) * radius
        val py = cy + sin(a) * radius

        // Die Zonenblöcke kommen nach der Schleife (zoneBlocks) und liegen
        // damit über dem Sand daneben.
        val inZone = abs(TimingGame.wrapToPi(a - game.zoneCenter)) <= zoneHalf
        if (inZone) continue

        // Die Fallenbreite kommt aus der Engine, nicht aus dem Renderer:
        // Die Falle misst sich wie die echte Zone.
        val fakeHalf = game.fakeZoneHalf()
        val relativeFake = TimingGame.wrapToPi(a - game.fakeZoneCenter)
        val inFake = game.hasFakeZone && abs(relativeFake) <= fakeHalf

        // Die Falle ist eine Kette aus Minen statt aus Blöcken: Hier
        // bleibt die Bahn frei, die Minen kommen nach der Schleife — auf
        // genau diese Plätze (trapChain), der Sand daneben bleibt liegen.
        // Liegt die Falle auf der Zone, gewinnt die Zone: Grün bleibt Grün.
        if (inFake) continue

        val b = sandBlock(px, py, cell)
        // Nur im Notfall (winzige Bahn unter PULS, siehe trapChain) reicht
        // eine Mine an den Sand heran; dann bleibt dieser Block frei.
        // Gemessen an der gerundeten Lage, die wirklich gezeichnet wird.
        val underMine = mineCenters.any {
            mineTouchesBlock(it.x - b.centerX, it.y - b.centerY, minePx, b.outer / 2f)
        }
        if (underMine) continue
        drawTrackBlock(b, style)
    }

    for (z in zoneBlocks) drawZoneBlock(z.block, z.core, z.mirrored, scene)

    // Die Minen der Falle: so viele, wie TrapPaint.count aus der
    // Grundbreite ergibt, im Takt der Blöcke über die Breite
    // fakeZoneHalf() (8.7). Erst alle Ränder, dann alle Kugeln:
    // Benachbarte Minen teilen sich ihren Rand, die Kugeln berühren sich
    // nie (trapMinePixel).
    for (c in mineCenters) {
        drawMineRim(c.x, c.y, minePx)
    }
    for ((i, mine) in mines.withIndex()) {
        drawMineBody(mineCenters[i].x, mineCenters[i].y, minePx, mine.red)
    }
}

/**
 * Ein normaler Block der Bahn ([b], siehe [sandBlock]) im Material der
 * Welt ([style], aus [ScenePaint.track]): Umriss eine Rasterstufe breit
 * wie bisher, darin die Fläche mit Bevel und darauf das Muster der Welt.
 *
 * Kante und Muster liegen im Raster der Bahn (Kante = [TrackBlock.unit]),
 * genau wie Umriss und Blätter der Zone — sonst säßen sie neben den
 * Minen in krummen Pixeln. Die Mitte `m` ist auf eine ganze Stufe
 * abgerundet: Bei einer geraden Zahl von Stufen gibt es keine echte
 * Mitte, und ein halbes Rasterpixel läse sich unscharf.
 */
internal fun DrawScope.drawTrackBlock(b: TrackBlock, style: TrackStyle) {
    drawRect(color = OutlineColor, topLeft = Offset(b.left, b.top), size = Size(b.outer, b.outer))
    val l = b.faceLeft
    val t = b.faceTop
    val s = b.inner
    val e = b.unit
    bevelRect(Color(style.block), Offset(l, t), Size(s, s), e, Color(style.light), Color(style.dark))
    // GLATT hat keinen Akzent (durchsichtig) — dort bleibt die Fläche leer.
    if (style.pattern == BlockPattern.GLATT) return
    val accent = Color(style.accent)
    val n = (s / e).roundToInt()
    val m = ((n - 1) / 2) * e
    when (style.pattern) {
        BlockPattern.GLATT -> Unit
        // Waagrechte Fuge durch die Mitte, links und rechts eine Stufe frei.
        BlockPattern.FUGE -> drawRect(accent, Offset(l + e, t + m), Size(s - 2 * e, e))
        // Zwei Maserungspunkte übereinander.
        BlockPattern.PLANKE -> {
            drawRect(accent, Offset(l + m, t + e), Size(e, e))
            drawRect(accent, Offset(l + m, t + s - 2 * e), Size(e, e))
        }
        // Schneekappe über die ganze Oberkante, links eine Stufe tiefer
        // (angeweht wie auf den Nadelbäumen).
        BlockPattern.SCHNEEKAPPE -> {
            drawRect(accent, Offset(l, t), Size(s, e))
            drawRect(accent, Offset(l, t), Size(e, e * 2))
        }
        // Zwei Nieten in gegenüberliegenden Ecken.
        BlockPattern.NIETEN -> {
            drawRect(accent, Offset(l + e, t + e), Size(e, e))
            drawRect(accent, Offset(l + s - 2 * e, t + s - 2 * e), Size(e, e))
        }
        // Ein Lämpchen in der Mitte.
        BlockPattern.LAEMPCHEN -> drawRect(accent, Offset(l + m, t + m), Size(e, e))
    }
}

/** Zahl der Blöcke auf der Bahn. */
internal const val TRACK_SEGMENTS = 60

/**
 * Die Rasterstufe der Bahn: eine halbe Zelle, ganzzahlig gerundet und
 * mindestens 1 px — auf 1080×2340 fünf Bildpunkte.
 *
 * Warum die halbe Zelle: Alles, womit auf der Bahn gespielt wird — Sand,
 * Zone und Minen — teilt sich so eine Pixelstufe. Die Minen zeichnen ihr
 * Sprite in genau diesem Maß (5 px auf 1080×2340, siehe [minePixel]);
 * Umrisse, Blätter und Licht-Kanten der Blöcke waren dagegen in krummen
 * Größen zwischen 2 und 8 px gesetzt und wirkten neben den Minen unscharf.
 * Die Welt dahinter (Wolken, Büsche, Boden) bleibt bei ganzen und
 * doppelten Zellen: Sie ist Kulisse und darf gröber sein.
 */
internal fun trackUnit(cell: Float): Int = (cell * 0.5f).roundToInt().coerceAtLeast(1)

/**
 * Ein Block der Bahn im Bild, fertig gerundet: linke obere Ecke
 * ([left], [top]) und Kante [outer] sind Vielfache der Rasterstufe
 * [unit] ([trackUnit]), gezählt ab dem Bildursprung. Der dunkle Umriss
 * ist überall genau eine Stufe breit, die Fläche darin ([inner]) also
 * `outer − 2 · unit`.
 */
internal class TrackBlock(val left: Float, val top: Float, val outer: Float, val unit: Float) {
    val inner: Float get() = outer - 2f * unit
    val faceLeft: Float get() = left + unit
    val faceTop: Float get() = top + unit
    val centerX: Float get() = left + outer / 2f
    val centerY: Float get() = top + outer / 2f
}

/**
 * Legt einen Block aus [units] Rasterstufen der Größe [unit] so ins
 * Raster, dass seine Mitte der Bahnposition ([x], [y]) so nah wie möglich
 * bleibt (höchstens eine halbe Stufe daneben).
 */
private fun snapBlock(x: Float, y: Float, units: Int, unit: Int): TrackBlock {
    val outer = units * unit
    val left = ((x - outer / 2f) / unit).roundToInt() * unit
    val top = ((y - outer / 2f) / unit).roundToInt() * unit
    return TrackBlock(left.toFloat(), top.toFloat(), outer.toFloat(), unit.toFloat())
}

/**
 * Ein Sandblock an der Bahnposition ([x], [y]): außen 3 Zellen (auf
 * 1080×2340 30 px, sechs Stufen), Umriss eine Stufe, Sandfläche
 * `3 Zellen − 2 Stufen` (20 px). Passt die Kante nicht auf das Raster
 * (etwa Zelle 7 px, Stufe 4 px), wird sie abgerundet — nie größer als
 * 3 Zellen, damit die Abstände der Minen ([mineBlockDistance]) halten.
 */
internal fun sandBlock(x: Float, y: Float, cell: Float): TrackBlock {
    val unit = trackUnit(cell)
    val units = floor(cell * 3f / unit + 1e-4f).toInt().coerceAtLeast(3)
    return snapBlock(x, y, units, unit)
}

/**
 * Ein Zonenblock an der Bahnposition ([x], [y]) mit dem Anteil [scale]
 * ([zoneBlockScale]) der vollen Größe von 5 Zellen: Kante auf ganze
 * Rasterstufen gerundet, mindestens vier (Umriss, zwei Stufen Gras,
 * Umriss). Auf 1080×2340 sind das 30 bis 50 px.
 */
internal fun zoneBlock(x: Float, y: Float, cell: Float, scale: Float): TrackBlock {
    val unit = trackUnit(cell)
    val units = (cell * scale * 5f / unit).roundToInt().coerceAtLeast(4)
    return snapBlock(x, y, units, unit)
}

/** Ein Block der grünen Zone vor dem Zeichnen: Lage und Maß, Anteil, Kern, gespiegelt. */
internal class ZoneSlot(val block: TrackBlock, val scale: Float, val core: Boolean, val mirrored: Boolean)

/**
 * Die Blöcke der grünen Zone, wie [drawTrack] sie zeichnet: in der
 * Reihenfolge des Zeichnens, kleine zuerst, damit die größeren oben
 * liegen. Kern und Breite kommen aus der Engine: Was hier leuchtet, ist
 * exakt das Fenster, das der Tap auch wertet.
 */
internal fun zoneSlots(game: TimingGame, cx: Float, cy: Float, radius: Float, cell: Float): List<ZoneSlot> {
    val segments = TRACK_SEGMENTS
    val zoneHalf = game.effectiveZoneHalf()
    // Gemessen bis einen halben Block über den Rand hinaus: Sonst schrumpft
    // ein Block, der zufällig genau auf der Grenze liegt, auf Sandgröße,
    // und eine schmale Zone (drei Blöcke) wirkt schief und kürzer.
    val taper = zoneHalf + PI.toFloat() / segments
    val slots = ArrayList<ZoneSlot>()
    for (k in 0 until segments) {
        val a = trackSlotAngle(k, segments)
        val rel = abs(TimingGame.wrapToPi(a - game.zoneCenter))
        if (rel > zoneHalf) continue
        val scale = zoneBlockScale(rel / taper)
        slots += ZoneSlot(
            block = zoneBlock(cx + cos(a) * radius, cy + sin(a) * radius, cell, scale),
            scale = scale,
            core = rel <= game.perfectHalf(),
            mirrored = k % 2 == 1
        )
    }
    slots.sortBy { it.scale }
    return slots
}

/**
 * Wie groß ein Zonenblock im Abstand [d] von der Zonenmitte ist (0 =
 * Mitte, 1 = einen halben Block hinter dem Rand), als Anteil der vollen
 * Größe: in der Mitte 1, bei 1 genau 0,6 — so groß wie ein Sandblock (3
 * von 5 Zellen). Dazwischen ein Viertel Kosinus: Die Blöcke bleiben lange
 * fast voll groß und werden erst zu den Enden hin schmaler.
 *
 * So zeigt die Zone ohne Worte zur Mitte, wo Perfekt liegt, und wirkt
 * trotzdem nie schmaler, als sie trifft: Der äußerste Block einer breiten
 * Zone bekommt etwa 0,67, der einer schmalen (drei Blöcke) etwa 0,77.
 * Das Trefferfenster ändert sich nicht, nur die Zeichnung. Gezeichnet
 * wird der Anteil auf ganze Rasterstufen gerundet ([zoneBlock]).
 */
internal fun zoneBlockScale(d: Float): Float =
    0.6f + 0.4f * cos(d.coerceIn(0f, 1f) * PI.toFloat() / 2f)

/**
 * Ein Block der grünen Zone ([block], siehe [zoneBlock]): dunkler Umriss
 * eine Rasterstufe breit, darin die Grasfläche, darauf Blätterbüschel wie
 * bei den Büschen und eine Licht-Kante wie bei den Knöpfen — hell oben
 * und links, dunkel unten und rechts. Im Perfekt-Kern ([core]) ist alles
 * heller; den goldenen Saum zeichnet [drawTrack] vorher. Der Umriss ist
 * auch im Kern nur eine Stufe breit: Hervorgehoben wird der Kern durch
 * Farbe und Saum, nicht durch einen dickeren Rand.
 *
 * Alles liegt im Raster der Bahn ([trackUnit]): Die Grasfläche ist in
 * `n = inner / unit` Rasterpixel geteilt, Büschel und Tupfer sind ganze
 * Rasterpixel, ihre Lage wächst mit `n / 8` mit (auf 1080×2340 ist der
 * volle Block 10 Stufen, die Grasfläche 8).
 *
 * Früher saß im Kern eine rosa Blüte mit gelber Mitte. Sie ist weg: Das
 * Rot direkt neben der Nebelwolke irritierte die Tester — es las sich wie
 * ein Warnsignal oder eine Mine, nicht wie das Ziel.
 *
 * Die Zone war das einzige flache Element neben Wolken, Kaktus und Minen.
 * Die Form bleibt bewusst ein Quadrat: Die runden Minen unterscheiden sich
 * so auch ohne Farbe von der Zone (Rot-Grün-Schwäche), und die
 * überlappenden Quadrate lesen sich weiter als durchgehendes Band.
 *
 * [mirrored] spiegelt die Spalten der Büschel, damit nicht jeder Block
 * gleich aussieht.
 *
 * [scene] wählt das Motiv ([ScenePaint.track]): Der Körper bleibt in jeder
 * Welt grün, denn die Zone ist das Signal. Blätter und Tupfer trägt nur
 * die WIESE, die anderen Welten ein Motiv von ein paar Pixeln
 * ([ScenePaint.motif]); der Kern-Akzent ist nie rot oder rosa.
 */
internal fun DrawScope.drawZoneBlock(block: TrackBlock, core: Boolean, mirrored: Boolean, scene: SceneId) {
    val motif = ScenePaint.track(scene).motif
    val unit = block.unit
    val inner = block.inner
    val left = block.faceLeft
    val top = block.faceTop
    val n = (inner / unit).roundToInt()
    drawRect(color = OutlineColor, topLeft = Offset(block.left, block.top), size = Size(block.outer, block.outer))
    drawRect(color = if (core) GrassLight else GrassDark, topLeft = Offset(left, top), size = Size(inner, inner))
    /** Ein Rasterpixel der Grasfläche in Spalte [col], Zeile [row]; außerhalb fällt es weg. */
    fun pixel(col: Int, row: Int, color: Color) {
        if (col !in 0 until n || row !in 0 until n) return
        val c = if (mirrored) n - 1 - col else col
        drawRect(color = color, topLeft = Offset(left + c * unit, top + row * unit), size = Size(unit, unit))
    }
    // Blätter und Tupfer gehören zur Wiese. Die anderen Welten setzen ihr
    // eigenes Motiv (unten, nach der Kante); zwei Muster übereinander
    // machten die Fläche unruhig.
    if (motif == ZoneMotif.WIESE) {
        val f = n / 8f
        // Blätterbüschel: ein Pixel, sein rechter Nachbar und der darüber.
        val leaf = if (core) GrassLeafCore else GrassLight
        for ((a, b) in ZONE_LEAVES) {
            val col = (a * f).roundToInt()
            val row = (b * f).roundToInt()
            pixel(col, row, leaf)
            pixel(col + 1, row, leaf)
            pixel(col, row - 1, leaf)
        }
        // Dunkle Tupfer, je ein Pixel.
        val deep = if (core) GrassDark else GrassDeep
        for ((a, b) in ZONE_SPECKS) pixel((a * f).roundToInt(), (b * f).roundToInt(), deep)
    }
    // Licht-Kante wie bei den Eckknöpfen (drawSandBevel), eine Stufe breit:
    // erst der Schatten unten und rechts, dann das Licht oben und links darüber.
    val shade = if (core) GrassEdgeCore else GrassEdge
    val shine = if (core) GrassShineCore else GrassShine
    drawRect(shade, Offset(left, top + inner - unit), Size(inner, unit))
    drawRect(shade, Offset(left + inner - unit, top), Size(unit, inner))
    drawRect(shine, Offset(left, top), Size(inner - unit, unit))
    drawRect(shine, Offset(left, top), Size(unit, inner - unit))
    // Das Motiv der Welt liegt über der Kante, im Raster um die Mitte der
    // Grasfläche. Nicht gespiegelt: Es ist klein und mittig, ein
    // gespiegeltes Motiv sähe nur wie ein anderes aus. Die WIESE liefert
    // keine Pixel, dort bleiben die Blätter von oben.
    val mx = (left + inner / 2f - unit / 2f).roundToInt().toFloat()
    val my = (top + inner / 2f - unit / 2f).roundToInt().toFloat()
    for (p in ScenePaint.motif(motif, core)) {
        drawRect(Color(p.color), Offset(mx + p.dx * unit, my + p.dy * unit), Size(unit, unit))
    }
}

/** Lage der Blätterbüschel auf der Grasfläche, in Achteln (Spalte, Zeile). */
private val ZONE_LEAVES = listOf(1 to 2, 4 to 1, 2 to 4)

/** Lage der dunklen Tupfer, in Achteln (Spalte, Zeile). */
private val ZONE_SPECKS = listOf(4 to 4, 2 to 6, 5 to 2)

/**
 * Freischalt-Zelebration: ein goldener Ring aus Pixel-Blöcken, der von der
 * Bahn nach außen aufsteigt und dabei verblasst — plus kurzer Goldschimmer
 * über dem ganzen Bild direkt am Anfang.
 */
internal fun DrawScope.drawUnlockBurst(
    timeLeft: Float,
    cx: Float,
    cy: Float,
    radius: Float,
    cell: Float
) {
    val progress = 1f - (timeLeft / CELEBRATE_SECONDS).coerceIn(0f, 1f)
    val fade = 1f - progress

    // Goldschimmer, nur im ersten Drittel spürbar
    val glow = (fade - 0.66f).coerceAtLeast(0f) * 0.9f
    if (glow > 0f) {
        drawRect(color = DotBody.copy(alpha = glow))
    }

    // Zwei versetzte Pixel-Ringe wandern nach außen
    val sparks = 20
    for (ring in 0 until 2) {
        val ringProgress = (progress - ring * 0.15f).coerceIn(0f, 1f)
        if (ringProgress <= 0f) continue
        val burstRadius = radius * (0.55f + ringProgress * 0.9f)
        val blockSize = cell * (3.5f - ring) * fade
        if (blockSize <= 0f) continue
        val color = (if (ring == 0) DotBody else DotShine).copy(alpha = fade)
        for (k in 0 until sparks) {
            val a = (k.toFloat() / sparks + ring * 0.025f) * (2f * PI.toFloat())
            val px = cx + cos(a) * burstRadius
            val py = cy + sin(a) * burstRadius
            drawRect(
                color = color,
                topLeft = Offset(px - blockSize / 2f, py - blockSize / 2f),
                size = Size(blockSize, blockSize)
            )
        }
    }
}

/** Radius des Vogels relativ zur Bildhöhe — auch für alles, was ihm ausweichen muss. */
internal const val DOT_RADIUS_SHARE = 0.026f

internal fun DrawScope.drawTimingDot(
    game: TimingGame,
    fx: FxState,
    cx: Float,
    cy: Float,
    radius: Float,
    skin: SkinId,
    hour: Int,
    month: Int
) {
    val h = size.height
    // == AP-31 release ==
    // Nach NOCH NICHT wackelt der Vogel 0,7 s seitlich (Plan 8.7, Mockup
    // feedback-check.html:605), wie Hand und Schriftzug (AP-22). Nur in
    // READY: Ein Start-Treffer beendet das Wackeln sofort.
    val wobble = if (game.phase == GamePhase.READY) notYetWobble(fx.notYetTime, 1.dp.toPx()) else 0f
    val px = cx + cos(game.angle) * radius + wobble
    // == /AP-31 ==
    var py = cy + sin(game.angle) * radius
    val r = h * DOT_RADIUS_SHARE

    // Mario-Tod: Während des Todes-Freeze bleibt der Vogel stehen, dann
    // hüpft er nach oben, dreht sich dabei auf den Rücken und fällt
    // kopfüber mit Gravitation unten aus dem Bild.
    var flip = 0f
    if (fx.deathTime >= 0f) {
        val t = fx.deathTime - TimingGame.DEATH_FREEZE_SECONDS
        if (t > 0f) {
            py += (-DEATH_HOP_SPEED * t + 0.5f * DEATH_GRAVITY * t * t) * h
            if (py - r * 2f > h) return
            flip = 180f * (t / DEATH_FLIP_SECONDS).coerceAtMost(1f)
        }
    }

    val state = SkinState(
        elapsed = game.elapsed,
        score = game.score,
        perfectStreak = game.perfectStreak,
        hour = hour,
        month = month
    )

    // Dottie als Kugel (docs/bevel-look.md, Abschnitt 4): Jede Zelle
    // bekommt ihre Stufe auf der Lichtachse, zum Glanz des Skins hin statt
    // zu Weiß — so bleibt ein goldener Vogel golden. Die Glanzfarbe steht
    // einmal pro Frame fest, nicht pro Zelle.
    val shineArgb = SkinPaint.shine(skin, state)

    fun drawBird(centerX: Float, centerY: Float, alpha: Float = 1f) {
        drawPixelCircle(
            outline = OutlineColor,
            centerX = centerX,
            centerY = centerY,
            radius = r,
            alpha = alpha
        ) { col, row -> Color(BevelPaint.kugel(col, row, SkinPaint.cell(skin, col, row, state), shineArgb)) }

        val u = (r * 2f) / GRID
        fun rect(col: Float, row: Float, cols: Float, rows: Float, color: Color) {
            drawRect(
                color = color,
                topLeft = Offset(centerX - r + col * u, centerY - r + row * u),
                size = Size(cols * u, rows * u),
                alpha = alpha
            )
        }

        // Glanzpunkt und Auge folgen der sichtbaren Flugrichtung: Die
        // horizontale Geschwindigkeit ist ~ -sin(angle) * direction — zeigt
        // sie nach links, wird das Gesicht gespiegelt. Der Wechsel passiert
        // genau dort, wo der Vogel senkrecht fliegt, und fällt kaum auf.
        //
        // Auf sehr hellen Skins (Koi, Chrom) ginge das weiße Auge im
        // Körper unter — dort bekommt es zum Körper hin eine Kontur.
        // Wo der Körper von selbst genug Kontrast hat, bleibt sie weg:
        // Sie wirkte dort wie ein Kasten ums Auge. Zur Silhouette hin
        // fehlt sie immer, dort grenzt ohnehin die Kontur des Kreises an.
        val facingLeft = sin(game.angle) * game.direction > 0f
        val shine = Color(shineArgb)
        // Weißer Kern im Glanzpunkt und eine halbe Zeile Schatten unter dem
        // Augenweiß: Der Glanz liegt auf der Kugel, das Auge in ihr.
        val eyeEdge = Color(BevelPaint.EYE_EDGE)
        val eyeOutline = SkinPaint.needsEyeOutline(skin)
        if (facingLeft) {
            rect(GRID - 4.5f, 2.5f, 2f, 2f, shine)
            rect(GRID - 3.5f, 2.5f, 1f, 1f, Color.White)
            if (eyeOutline) {
                rect(5.5f, 3f, 0.5f, 4f, OutlineColor)
                rect(2f, 2.5f, 3.5f, 0.5f, OutlineColor)
                rect(2f, 7f, 3.5f, 0.5f, OutlineColor)
            }
            rect(2f, 3f, 3.5f, 4f, Color.White)
            rect(2f, 6.5f, 3.5f, 0.5f, eyeEdge)
            rect(2f, 4f, 1.5f, 2f, OutlineColor)
        } else {
            rect(2.5f, 2.5f, 2f, 2f, shine)
            rect(2.5f, 2.5f, 1f, 1f, Color.White)
            if (eyeOutline) {
                rect(7f, 3f, 0.5f, 4f, OutlineColor)
                rect(7.5f, 2.5f, 3.5f, 0.5f, OutlineColor)
                rect(7.5f, 7f, 3.5f, 0.5f, OutlineColor)
            }
            rect(7.5f, 3f, 3.5f, 4f, Color.White)
            rect(7.5f, 6.5f, 3.5f, 0.5f, eyeEdge)
            rect(9.5f, 4f, 1.5f, 2f, OutlineColor)
        }
    }

    // Schweif-Skins (Tinte) lassen Nachbilder auf der Bahn zurück. Die
    // Positionen werden aus dem Winkel zurückgerechnet statt gespeichert —
    // damit sehen alle Ports identisch aus, ohne eigenen Zustand.
    if (SkinPaint.hasTrail(skin) && game.phase == GamePhase.RUNNING) {
        for (step in SkinPaint.TRAIL_STEPS downTo 1) {
            val a = game.angle - game.direction * step * SkinPaint.TRAIL_SPACING
            drawBird(
                centerX = cx + cos(a) * radius,
                centerY = cy + sin(a) * radius,
                alpha = 0.34f / step
            )
        }
    }

    if (flip > 0f) {
        rotate(degrees = flip, pivot = Offset(px, py)) { drawBird(px, py) }
    } else {
        drawBird(px, py)
    }

    // Todesmarker (Plan 3.2): Im Freeze steht ein weißer Pixelrahmen um
    // den Vogel — genau dort ist es passiert. Mit dem Hüpfer ist er weg,
    // dann steht die Ursache als Text unter dem Ring.
    if (fx.deathTime >= 0f && fx.deathTime < TimingGame.DEATH_FREEZE_SECONDS) {
        drawDeathFrame(px, py, r, floor(h / 220f).coerceAtLeast(2f))
    }
}

/**
 * Weißer Rahmen um den Vogel, gezeichnet als vier Pixelbalken im
 * Raster der Bahn ([cell]), mit dunkler Kontur außen herum, damit er auch
 * vor hellem Himmel steht.
 */
private fun DrawScope.drawDeathFrame(px: Float, py: Float, r: Float, cell: Float) {
    val half = r + cell * 2f
    val stroke = cell
    fun frame(inset: Float, width: Float, color: Color) {
        val left = px - half - inset
        val top = py - half - inset
        val side = (half + inset) * 2f
        drawRect(color, Offset(left, top), Size(side, width))
        drawRect(color, Offset(left, top + side - width), Size(side, width))
        drawRect(color, Offset(left, top), Size(width, side))
        drawRect(color, Offset(left + side - width, top), Size(width, side))
    }
    frame(inset = stroke * 0.5f, width = stroke * 2f, color = OutlineColor)
    frame(inset = 0f, width = stroke, color = Color.White)
}
