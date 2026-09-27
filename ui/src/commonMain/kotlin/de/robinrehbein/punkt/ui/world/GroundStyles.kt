package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.robinrehbein.punkt.game.BevelPaint
import de.robinrehbein.punkt.game.Ground
import de.robinrehbein.punkt.game.GroundPaint
import de.robinrehbein.punkt.game.GroundStyle
import de.robinrehbein.punkt.game.PropSprites
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

// Die Böden der Welten (Bevel-Look, docs/bevel-look.md Abschnitt 0
// Punkt 7). Welches Muster eine Welt trägt, sagt Ground.style in :core;
// die Farben kommen aus Ground und GroundPaint. Hier steht nur, wo die
// Rechtecke liegen — keine Kulisse braucht einen Sonderfall.
//
// Für alle Stile gilt: Die Bodenkante [top] (ScenePaint.groundY) bleibt,
// wo sie ist. Requisiten stehen darauf und die Tod-Animation landet dort;
// Dünen und Wellenkämme dürfen darüber hinausragen, die Linie selbst
// verschiebt kein Muster. Der Boden ist statisch, ohne Animation.

/** Zeichnet den Boden unterhalb von [top] im Muster von [Ground.style]. */
internal fun DrawScope.drawGroundStyle(cell: Float, top: Float, ground: Ground) {
    when (ground.style) {
        GroundStyle.NARBE -> drawTurfGround(cell, top, ground)
        GroundStyle.DUENE -> drawDuneGround(cell, top, ground)
        GroundStyle.WELLEN -> drawWaveGround(cell, top, ground)
        GroundStyle.SCHNEE -> drawSnowGround(cell, top, ground)
        GroundStyle.BORDSTEIN -> drawCurbGround(cell, top, ground)
    }
}

/** Auf das Raster der Zelle abrunden, damit Muster nie zwischen Pixeln liegen. */
private fun snap(v: Float, c: Float): Float = floor(v / c) * c

/**
 * WIESE: Grasnarbe als Bevel-Kacheln im Wechsel hell/dunkel, darunter
 * Sand mit Schattenband und ein paar Kieseln. Die Kacheln bekommen nur
 * eine halbe Zelle Kante und einen weichen Schatten (GroundPaint.turfShade):
 * Sie sind nur vier Zellen hoch, eine volle Kante machte ein Gitter daraus.
 */
private fun DrawScope.drawTurfGround(c: Float, top: Float, ground: Ground) {
    val w = size.width
    val h = size.height
    drawRect(Color(ground.sand), Offset(0f, top), Size(w, h - top))
    drawRect(Color(ground.sandShade), Offset(0f, top + c * 8), Size(w, c * 2))

    // Kiesel: ein Lichtpixel über einem flachen Zweierstein. Die Plätze
    // folgen dem goldenen Schnitt, damit sie nicht in Reihe stehen.
    val pebbleLight = Color(GroundPaint.PEBBLE_LIGHT)
    val pebbleDark = Color(GroundPaint.PEBBLE_DARK)
    for (i in 0 until 14) {
        val x = snap(((i * 0.618f) % 1f) * w, c)
        val y = snap(top + c * 11 + ((i * 0.37f) % 1f) * (h - top - c * 14), c)
        drawRect(pebbleLight, Offset(x, y - c), Size(c, c))
        drawRect(pebbleDark, Offset(x, y), Size(c * 2, c))
    }

    val toothW = c * 5f
    val turfLight = Color(ground.turfLight)
    val turfDark = Color(ground.turfDark)
    val lightEdges = Color(BevelPaint.light(ground.turfLight)) to Color(GroundPaint.turfShade(ground.turfLight))
    val darkEdges = Color(BevelPaint.light(ground.turfDark)) to Color(GroundPaint.turfShade(ground.turfDark))
    var x = 0f
    var k = 0
    while (x < w) {
        val even = k % 2 == 0
        val (edgeLight, edgeDark) = if (even) lightEdges else darkEdges
        bevelRect(
            if (even) turfLight else turfDark,
            Offset(x, top),
            Size(toothW, c * 4),
            c * 0.5f,
            edgeLight,
            edgeDark
        )
        x += toothW
        k++
    }
    // Unterkante der Narbe im Kantengrün der Zone: Die Wiese ist die
    // einzige Welt, deren Boden grün sein darf (LEGACY_ZONE_GREENS).
    drawRect(GrassEdge, Offset(0f, top + c * 4), Size(w, c))
    drawRect(OutlineColor, Offset(0f, top - c), Size(w, c))
}

/** Palette der halb eingesunkenen Wüstenkiesel; fest, also einmal angelegt. */
private val DunePebblePalette: Map<Char, Color> = mapOf(
    'O' to OutlineColor,
    'L' to Color(GroundPaint.DUNE_PEBBLE_LIGHT),
    'B' to Color(GroundPaint.DUNE_PEBBLE_BODY),
    'D' to Color(GroundPaint.DUNE_PEBBLE_DARK)
)

/** Plätze der Wüstenkiesel: Anteil der Breite und Zellen unter der Bodenkante. */
private val DunePebbleSpots = listOf(0.16f to 6, 0.2f to 7, 0.53f to 6, 0.82f to 7)

/**
 * WÜSTE: drei Sandsteinschichten mit versetzten Fugen, darüber eine
 * Dünenkante als Pixel-Welle mit heller Oberkante. Die Kiesel zeigen nur
 * ihre gerundete obere Hälfte — ein ganzer Stein im Sand läse sich als
 * schwebendes Quadrat.
 */
private fun DrawScope.drawDuneGround(c: Float, top: Float, ground: Ground) {
    val w = size.width
    val h = size.height
    drawRect(Color(ground.sand), Offset(0f, top), Size(w, h - top))
    val joint = Color(GroundPaint.SANDSTONE_JOINT)
    for (i in GroundPaint.SANDSTONE_BANDS.indices) {
        val y = top + c * (8 + i * 8)
        drawRect(Color(GroundPaint.SANDSTONE_BANDS[i]), Offset(0f, y), Size(w, c * 4))
        drawRect(Color(GroundPaint.SANDSTONE_BAND_LIGHTS[i]), Offset(0f, y), Size(w, c))
        // Fugen je Schicht versetzt, sonst stünden sie als Säulen übereinander.
        var fx = (i * 7 % 11) * c
        while (fx < w) {
            drawRect(joint, Offset(fx, y + c), Size(c, c * 3))
            fx += c * 23
        }
    }

    // Dünenkante: Spalte für Spalte eine Sinuswelle, auf Zellen gerundet.
    val crest = Color(GroundPaint.DUNE_CREST)
    val dune = Color(ground.turfLight)
    var x = 0f
    while (x < w) {
        val hh = snap(c * (2.2f + 2f * sin(x / (c * 9f))), c)
        drawRect(OutlineColor, Offset(x, top - hh - c), Size(c, c))
        drawRect(crest, Offset(x, top - hh), Size(c, c))
        drawRect(dune, Offset(x, top - hh + c), Size(c, hh + c * 3))
        x += c
    }
    drawRect(Color(ground.sandShade), Offset(0f, top + c * 4), Size(w, c))

    for ((fx, dy) in DunePebbleSpots) {
        pixelMap(snap(fx * w, c), top + c * dy, c, PropSprites.DUNE_PEBBLE, DunePebblePalette)
    }
}

/** Luftblasen im MEER: Anteil der Breite und Zellen unter der Bodenkante. */
private val BubbleSpots = listOf(0.13f to 25, 0.33f to 13, 0.57f to 28, 0.78f to 16)

/**
 * MEER: Wellenkämme mit Schaum, die bis drei Zellen über die Bodenkante
 * reichen, darunter Wasser in drei Stufen und Luftblasen als runde Ringe
 * mit Glanzpixel und kleiner Begleitblase. Einzelne Pixel wirkten hier
 * wie Kratzer, deshalb nur Ringe und 2×2-Blasen.
 */
private fun DrawScope.drawWaveGround(c: Float, top: Float, ground: Ground) {
    val w = size.width
    val h = size.height
    val waterMid = Color(GroundPaint.WATER_MID)
    val crest = Color(ground.turfDark)
    val spray = Color(ground.turfLight)
    val foam = Color(GroundPaint.FOAM)
    drawRect(Color(ground.sand), Offset(0f, top), Size(w, h - top))
    drawRect(waterMid, Offset(0f, top + c * 12), Size(w, c * 9))
    drawRect(Color(ground.sandShade), Offset(0f, top + c * 21), Size(w, h - top - c * 21))
    drawRect(OutlineColor, Offset(0f, top - c), Size(w, c))
    var x = 0f
    while (x < w) {
        drawRect(crest, Offset(x, top), Size(c * 12, c * 4))
        drawRect(OutlineColor, Offset(x + c * 2, top - c * 3), Size(c * 6, c))
        drawRect(OutlineColor, Offset(x + c, top - c * 2), Size(c, c))
        drawRect(OutlineColor, Offset(x + c * 8, top - c * 2), Size(c, c))
        drawRect(crest, Offset(x + c * 2, top - c * 2), Size(c * 6, c * 2))
        drawRect(foam, Offset(x + c * 3, top - c * 2), Size(c * 4, c))
        drawRect(spray, Offset(x + c * 2, top), Size(c * 2, c))
        x += c * 12
    }
    drawRect(waterMid, Offset(0f, top + c * 4), Size(w, c))

    for ((fx, dy) in BubbleSpots) {
        val bx = snap(fx * w, c)
        val by = top + c * dy
        for (yy in -2..2) for (xx in -2..2) {
            val d = hypot(xx.toFloat(), yy.toFloat())
            if (d in 1.5f..2.4f) drawRect(spray, Offset(bx + xx * c, by + yy * c), Size(c, c))
        }
        drawRect(foam, Offset(bx - c, by - c), Size(c, c))
        drawRect(spray, Offset(bx + c * 4, by - c * 6), Size(c * 2, c * 2))
    }
}

/**
 * BERG: Felsmauer aus bevelten Blöcken, jede Reihe versetzt und mit
 * wechselnden Breiten, darüber eine Schneedecke mit Eiszapfen. Die Fugen
 * sind die dunkle Fläche hinter den Blöcken.
 */
private fun DrawScope.drawSnowGround(c: Float, top: Float, ground: Ground) {
    val w = size.width
    val h = size.height
    val rock = Color(GroundPaint.ROCK)
    val rockLight = Color(GroundPaint.ROCK_LIGHT)
    val rockDark = Color(GroundPaint.ROCK_DARK)
    drawRect(Color(GroundPaint.ROCK_JOINT), Offset(0f, top), Size(w, h - top))
    var y = top + c * 6
    var row = 0
    while (y < h) {
        var x = -((row * 5) % 8) * c
        var n = 0
        while (x < w) {
            val bw = c * (9 + (n * 7 + row * 3) % 7)
            bevelRect(rock, Offset(x + c, y + c), Size(bw - c, c * 6), c, rockLight, rockDark)
            x += bw
            n++
        }
        y += c * 8
        row++
    }
    val snowShade = Color(ground.sandShade)
    drawRect(OutlineColor, Offset(0f, top - c), Size(w, c))
    drawRect(Color(ground.sand), Offset(0f, top), Size(w, c * 5))
    drawRect(Color(GroundPaint.SNOW_TOP), Offset(0f, top), Size(w, c))
    drawRect(snowShade, Offset(0f, top + c * 4), Size(w, c))
    // Eiszapfen: zwei Zellen breit, zwei bis vier lang, mit dunkler Spitze.
    val tip = Color(ground.turfDark)
    var x = c * 3
    while (x < w) {
        val ln = 2 + ((x / c).toInt() * 7) % 3
        drawRect(snowShade, Offset(x, top + c * 5), Size(c * 2, c * ln))
        drawRect(tip, Offset(x, top + c * (5 + ln)), Size(c, c))
        x += c * 9
    }
}

/**
 * STADT: Bordsteine als bevelte Platten mit Fugen, darunter Asphalt mit
 * gestrichelter gelber Mittellinie und einem Gully.
 */
private fun DrawScope.drawCurbGround(c: Float, top: Float, ground: Ground) {
    val w = size.width
    val h = size.height
    val shade = Color(ground.sandShade)
    val curb = Color(ground.turfLight)
    val curbLight = Color(GroundPaint.CURB_LIGHT)
    val curbDark = Color(ground.turfDark)
    drawRect(Color(ground.sand), Offset(0f, top), Size(w, h - top))
    drawRect(OutlineColor, Offset(0f, top - c), Size(w, c))
    var x = 0f
    while (x < w) {
        bevelRect(curb, Offset(x, top), Size(c * 14, c * 5), c, curbLight, curbDark)
        drawRect(OutlineColor, Offset(x + c * 14, top), Size(c, c * 5))
        x += c * 15
    }
    drawRect(shade, Offset(0f, top + c * 5), Size(w, c))
    val line = Color(GroundPaint.CENTER_LINE)
    val lineLight = Color(GroundPaint.CENTER_LINE_LIGHT)
    x = c * 4
    while (x < w) {
        drawRect(line, Offset(x, top + c * 16), Size(c * 10, c * 2))
        drawRect(lineLight, Offset(x, top + c * 16), Size(c * 10, c))
        x += c * 20
    }
    drawRect(shade, Offset(0f, top + c * 26), Size(w, c * 2))
    // Gully: Kontur mit vier Schlitzen, bei zwei Dritteln der Breite.
    val gx = snap(w * 0.66f, c)
    drawRect(OutlineColor, Offset(gx, top + c * 7), Size(c * 10, c * 4))
    var k = 1
    while (k < 9) {
        drawRect(curbDark, Offset(gx + k * c, top + c * 8), Size(c, c * 2))
        k += 2
    }
}
