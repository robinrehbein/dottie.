package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.Ground
import de.robinrehbein.punkt.game.Prop
import de.robinrehbein.punkt.game.PropShape
import de.robinrehbein.punkt.game.RockPart
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SkinState
import de.robinrehbein.punkt.game.TimingGame
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
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

        // Langsam driftende Wolken. Im Vakuum gibt es keine — dann bleibt
        // der Himmel leer, statt graue Attrappen zu zeigen.
        kulisse.cloud?.let { cloud ->
            val drift = game.elapsed * h * 0.01f
            drawCloud(w * 0.1f - drift % (w * 1.4f), h * 0.16f, cell, Color(cloud))
            drawCloud(w * 0.75f - drift % (w * 1.4f), h * 0.24f, cell, Color(cloud))
        }

        drawScenery(game, cell, kulisse.props)
        kulisse.ground?.let { drawGroundStrip(cell, it) }

        // Kreisbahn mit Zielzone, ggf. Fallen-Zone und Punkt. Sie zieht
        // ihre Farben bewusst NICHT aus der Kulisse: Worauf getippt wird,
        // sieht überall gleich aus — sonst wäre die Kulisse ein Vorteil.
        val cx = w / 2f
        val cy = h * 0.44f
        val radius = min(w * 0.36f, h * 0.28f)
        drawTrack(game, cx, cy, radius, cell)
        if (game.isDotVisible) {
            drawTimingDot(game, fx, cx, cy, radius, skin, hour, month)
        }
        if (fx.celebrateTime > 0f) {
            drawUnlockBurst(fx.celebrateTime, cx, cy, radius, cell)
        }
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

    val drift = game.elapsed * h * 0.016f
    val spacing = w * 0.26f
    val count = (w / spacing).toInt() + 3
    val total = spacing * count
    for (k in 0 until count) {
        val x = ((k * spacing - drift) % total + total) % total - spacing
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
        PropShape.WELLE -> drawPixelWave(cx, groundY, s, sway, cell, dark, body, light, accent)
        PropShape.NADELBAUM ->
            drawPixelFir(cx, groundY, s, sway, cell, dark, body, light, stem, stemShade)
        PropShape.HOCHHAUS -> drawPixelTower(cx, groundY, s, cell, dark, body, light, accent)
        PropShape.FELS -> drawPixelRock(cx, groundY, s, sway, cell, dark, body, light)
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
    blocks.forEach { (r, color) ->
        drawRect(color = color, topLeft = Offset(r.x, r.y), size = Size(r.w, r.h))
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
        drawRect(
            color = color,
            topLeft = Offset(lx - lw / 2f, layerTop),
            size = Size(lw, lh)
        )
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
        drawRect(
            color = color,
            topLeft = Offset(lx - lw / 2f, layerTop),
            size = Size(lw, lh)
        )
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
        drawRect(color = color, topLeft = Offset(x, y), size = Size(u, u))
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
 * Welle: flacher, breiter Stapel mit Schaumtupfern. Bewusst breiter als
 * hoch — eine Welle, die wie ein Busch stünde, läse sich als Pflanze.
 */
internal fun DrawScope.drawPixelWave(
    cx: Float,
    groundY: Float,
    s: Float,
    sway: Float,
    cell: Float,
    dark: Color,
    body: Color,
    light: Color,
    foam: Color
) {
    val layers = listOf(
        Triple(s * 3.0f, s * 0.30f, dark),
        Triple(s * 2.2f, s * 0.26f, body),
        Triple(s * 1.2f, s * 0.22f, light)
    )
    var layerTop = groundY
    var lx = cx
    layers.forEachIndexed { i, (lw, lh, color) ->
        layerTop -= lh
        lx = cx + sway * (0.3f + 0.4f * i)
        drawRect(
            color = OutlineColor,
            topLeft = Offset(lx - lw / 2f - cell, layerTop - cell),
            size = Size(lw + cell * 2f, lh + cell * 2f)
        )
        drawRect(color = color, topLeft = Offset(lx - lw / 2f, layerTop), size = Size(lw, lh))
    }

    val u = cell * 1.5f
    drawRect(color = foam, topLeft = Offset(lx - s * 0.5f, layerTop), size = Size(u * 2f, u))
    drawRect(color = foam, topLeft = Offset(lx + s * 0.2f, layerTop + u), size = Size(u, u))
}

/** Nadelbaum: schmaler Stamm, drei spitze Lagen, helle Spitze obendrauf. */
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
    stemShade: Color
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
        drawRect(color = color, topLeft = Offset(lx - lw / 2f, layerTop), size = Size(lw, lh))
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
    drawRect(color = body, topLeft = Offset(cx - w / 2f, groundY - hgt), size = Size(w, hgt))
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
 * Fels: Umriss aus [ScenePaint.ROCK_PARTS], unsymmetrisch und mit
 * Lichtseite. Erst alle Konturen, dann alle Flächen — sonst schnitte die
 * Kontur eines höheren Stücks in die Fläche des darunterliegenden, und
 * der Stein bekäme Fugen, die er nicht hat.
 */
internal fun DrawScope.drawPixelRock(
    cx: Float,
    groundY: Float,
    s: Float,
    sway: Float,
    cell: Float,
    dark: Color,
    body: Color,
    light: Color
) {
    val parts = ScenePaint.ROCK_PARTS
    fun left(p: RockPart) = cx + sway * (0.15f + 0.25f * p.y) + p.x * s
    fun top(p: RockPart) = groundY - (p.y + p.h) * s

    parts.forEach { p ->
        drawRect(
            color = OutlineColor,
            topLeft = Offset(left(p) - cell, top(p) - cell),
            size = Size(p.w * s + cell * 2f, p.h * s + cell * 2f)
        )
    }
    parts.forEach { p ->
        drawRect(
            color = when (p.tone) {
                0 -> dark
                1 -> body
                else -> light
            },
            topLeft = Offset(left(p), top(p)),
            size = Size(p.w * s, p.h * s)
        )
    }
}

/**
 * Bodenstreifen: Grundfläche mit dunklerem Band, darüber die Narbe aus
 * zwei Tönen. Der statische Boden unter allem — welche Farben, sagt die
 * Kulisse; wo er beginnt, sagt ScenePaint.groundY und sonst niemand.
 */
internal fun DrawScope.drawGroundStrip(cell: Float, ground: Ground) {
    val h = size.height
    val w = size.width
    val groundTop = ScenePaint.groundY(h)

    drawRect(
        color = Color(ground.sand),
        topLeft = Offset(0f, groundTop),
        size = Size(w, h - groundTop)
    )
    drawRect(
        color = Color(ground.sandShade),
        topLeft = Offset(0f, groundTop + cell * 8),
        size = Size(w, cell * 2)
    )
    val toothW = cell * 5f
    drawRect(
        color = Color(ground.turfDark),
        topLeft = Offset(0f, groundTop),
        size = Size(w, cell * 5)
    )
    var x = 0f
    while (x < w) {
        drawRect(
            color = Color(ground.turfLight),
            topLeft = Offset(x, groundTop),
            size = Size(toothW, cell * 4)
        )
        x += toothW * 2
    }
    drawRect(color = OutlineColor, topLeft = Offset(0f, groundTop - cell), size = Size(w, cell))
}

/**
 * Die Kreisbahn als Kette blockiger Zellen. Die Zielzone ist grün mit
 * hellem Perfekt-Kern, die Fallen-Zone violett — alles im Pixel-Raster.
 */
internal fun DrawScope.drawTrack(
    game: TimingGame,
    cx: Float,
    cy: Float,
    radius: Float,
    cell: Float
) {
    // 60 statt 72 Segmente: Die einzelnen Kettenglieder bekommen sichtbaren
    // Abstand (Perlenketten-Look), statt sich zu überlappen. Die Zonen
    // bleiben durch ihre größeren Blöcke bewusst ein durchgehendes Band.
    val segments = 60
    val zoneHalf = game.effectiveZoneHalf()
    for (k in 0 until segments) {
        val a = k.toFloat() / segments * (2f * Math.PI.toFloat())
        val px = cx + cos(a) * radius
        val py = cy + sin(a) * radius

        val relativeZone = TimingGame.wrapToPi(a - game.zoneCenter)
        val inZone = abs(relativeZone) <= zoneHalf
        // Kern und Fallenbreite kommen aus der Engine, nicht aus dem
        // Renderer: Was hier leuchtet, ist exakt das Fenster, das der Tap
        // auch wertet — und die Falle misst sich wie die echte Zone.
        val coreHalf = game.perfectHalf()
        val inPerfectCore = abs(relativeZone) <= coreHalf

        val fakeHalf = game.fakeZoneHalf()
        val inFake = game.hasFakeZone &&
            abs(TimingGame.wrapToPi(a - game.fakeZoneCenter)) <= fakeHalf
        val inFakeCore = game.hasFakeZone &&
            abs(TimingGame.wrapToPi(a - game.fakeZoneCenter)) <= coreHalf

        val highlighted = inZone || inFake
        val outer = if (highlighted) cell * 5f else cell * 3f
        val inner = if (highlighted) cell * 3.4f else cell * 1.8f
        val innerColor = when {
            inPerfectCore -> GrassLight
            inZone -> GrassDark
            inFakeCore -> FakeZoneCoreColor
            inFake -> FakeZoneColor
            else -> GroundSandShade
        }

        drawRect(
            color = OutlineColor,
            topLeft = Offset(px - outer / 2f, py - outer / 2f),
            size = Size(outer, outer)
        )
        drawRect(
            color = innerColor,
            topLeft = Offset(px - inner / 2f, py - inner / 2f),
            size = Size(inner, inner)
        )
    }
}

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
            val a = (k.toFloat() / sparks + ring * 0.025f) * (2f * Math.PI.toFloat())
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
    val px = cx + cos(game.angle) * radius
    var py = cy + sin(game.angle) * radius
    val r = h * 0.026f

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

    fun drawBird(centerX: Float, centerY: Float, alpha: Float = 1f) {
        drawPixelCircle(
            outline = OutlineColor,
            centerX = centerX,
            centerY = centerY,
            radius = r,
            alpha = alpha
        ) { col, row -> Color(SkinPaint.cell(skin, col, row, state)) }

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
        val shine = Color(SkinPaint.shine(skin, state))
        val eyeOutline = SkinPaint.needsEyeOutline(skin)
        if (facingLeft) {
            rect(GRID - 4.5f, 2.5f, 2f, 2f, shine)
            if (eyeOutline) {
                rect(5.5f, 3f, 0.5f, 4f, OutlineColor)
                rect(2f, 2.5f, 3.5f, 0.5f, OutlineColor)
                rect(2f, 7f, 3.5f, 0.5f, OutlineColor)
            }
            rect(2f, 3f, 3.5f, 4f, Color.White)
            rect(2f, 4f, 1.5f, 2f, OutlineColor)
        } else {
            rect(2.5f, 2.5f, 2f, 2f, shine)
            if (eyeOutline) {
                rect(7f, 3f, 0.5f, 4f, OutlineColor)
                rect(7.5f, 2.5f, 3.5f, 0.5f, OutlineColor)
                rect(7.5f, 7f, 3.5f, 0.5f, OutlineColor)
            }
            rect(7.5f, 3f, 3.5f, 4f, Color.White)
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
}
