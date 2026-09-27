package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.robinrehbein.punkt.game.DayCycle
import de.robinrehbein.punkt.game.ScenePaint
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Der Himmel über der Welt: Sonne, Mond, Sterne und Wolken. Wann was am
// Himmel steht, sagt DayCycle in :core; hier steht nur, wo.
//
// Lesbarkeit geht vor: Sonne und Mond stehen in den oberen Ecken, neben
// der Punktzahl und über dem Ring; die Abendsonne sinkt links hinter die
// ferne Ebene, weit unter der Bahn. Die Sterne sind einzelne Zellen und
// liegen wie im WELTRAUM hinter allem.

/** Anzahl der Nachtsterne außerhalb des WELTRAUMs — weniger als dort (110). */
private const val NIGHT_STARS = 46

/**
 * Nachtsterne: erscheinen mit der Abendstufe, sind in tiefer Nacht voll
 * da und funkeln wie die Sterne im WELTRAUM. Sie stehen fest (ohne
 * Drift) im oberen zwei Dritteln des Himmels.
 */
internal fun DrawScope.drawNightStars(night: Float, time: Float, cell: Float) {
    if (night <= 0.2f) return
    val w = size.width
    val h = size.height
    val star = Color(DayCycle.NIGHT_STAR)
    for (i in 0 until NIGHT_STARS) {
        val x = snap(worldHash(i * 2 + 501) * w, cell)
        val y = snap(worldHash(i * 2 + 502) * h * 0.66f, cell)
        val kind = worldHash(i + 8111)
        val twinkle = 0.55f + 0.45f * sin(time * (1f + kind * 2f) + i * 2.3f)
        // Erst nach Sonnenuntergang: Vor dem Abendrot sahen Sterne aus
        // wie Staub auf dem Bildschirm.
        val a = (((night - 0.2f) / 0.8f) * twinkle).coerceIn(0f, 1f)
        if (kind > 0.88f) {
            drawRect(star, Offset(x - cell, y), Size(cell * 3f, cell), alpha = a)
            drawRect(star, Offset(x, y - cell), Size(cell, cell * 3f), alpha = a)
        } else {
            drawRect(star, Offset(x, y), Size(cell, cell), alpha = a * 0.8f)
        }
    }
}

/**
 * Sonne oder Mond je nach Himmelsstufe: tagsüber die Sonne oben rechts
 * mit Strahlen, die langsam atmen; am Abend groß und orange links über
 * dem Horizont, mit Streifen im Retro-Stil, halb hinter der fernen Ebene;
 * nachts der Vollmond oben links mit Kratern und einem Schein.
 *
 * Muss VOR der fernen Ebene gezeichnet werden, damit die Abendsonne
 * hinter Hügeln, Tafelbergen und Häusern versinkt.
 */
internal fun DrawScope.drawSkyBody(stage: Int, sky: Color, time: Float, cell: Float) {
    val w = size.width
    val h = size.height
    when {
        DayCycle.isMoon(stage) -> {
            val cx = snap(w * 0.15f, cell)
            val cy = snap(h * 0.075f, cell)
            val r = 4
            pixelDisc(cx, cy, r + 2, cell, Color(DayCycle.MOON), alpha = 0.12f)
            pixelDisc(cx, cy, r, cell, Color(DayCycle.MOON))
            val crater = Color(DayCycle.MOON_SHADE)
            drawRect(crater, Offset(cx - cell * 2f, cy - cell), Size(cell * 2f, cell * 2f))
            drawRect(crater, Offset(cx + cell, cy + cell), Size(cell * 2f, cell))
            drawRect(crater, Offset(cx + cell, cy - cell * 3f), Size(cell, cell))
            // Schattenrand rechts unten: der Mond ist eine Kugel, keine Scheibe.
            drawRect(crater, Offset(cx + cell * 3f, cy), Size(cell, cell * 2f))
            drawRect(crater, Offset(cx + cell, cy + cell * 3f), Size(cell * 2f, cell))
        }
        DayCycle.isDusk(stage) -> {
            val cx = snap(w * 0.2f, cell)
            val cy = snap(ScenePaint.groundY(h) - h * 0.075f, cell)
            val r = 8
            pixelDisc(cx, cy, r + 3, cell, Color(DayCycle.SUN_LOW_LIGHT), alpha = 0.18f)
            pixelDisc(cx, cy, r, cell, Color(DayCycle.SUN_LOW))
            // Licht oben links — und durch die untere Hälfte Streifen in
            // Himmelsfarbe, die nach unten breiter werden (Retro-Sonne).
            drawRect(Color(DayCycle.SUN_LOW_LIGHT), Offset(cx - cell * 4f, cy - cell * 6f), Size(cell * 5f, cell))
            drawRect(Color(DayCycle.SUN_LOW_LIGHT), Offset(cx - cell * 6f, cy - cell * 4f), Size(cell * 2f, cell * 2f))
            for (k in 0 until 3) {
                drawRect(
                    sky,
                    Offset(cx - cell * (r + 1), cy + cell * (1.5f + k * 2.5f)),
                    Size(cell * (2 * r + 3), cell * (1 + k / 2))
                )
            }
        }
        else -> {
            val cx = snap(w * 0.85f, cell)
            val cy = snap(h * 0.075f, cell)
            val r = 4
            val sun = Color(DayCycle.SUN)
            // Strahlen: acht kurze Balken, die langsam länger und kürzer werden.
            val ray = cell * (2f + (sin(time * 1.6f) > 0f).toFloat())
            val gap = cell * (r + 2)
            drawRect(sun, Offset(cx - cell / 2f - 0f, cy - gap - ray), Size(cell, ray))
            drawRect(sun, Offset(cx - cell / 2f, cy + gap), Size(cell, ray))
            drawRect(sun, Offset(cx - gap - ray, cy - cell / 2f), Size(ray, cell))
            drawRect(sun, Offset(cx + gap, cy - cell / 2f), Size(ray, cell))
            val d = cell * (r + 1)
            for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
                drawRect(sun, Offset(cx + sx * d - cell / 2f, cy + sy * d - cell / 2f), Size(cell, cell))
            }
            pixelDisc(cx, cy, r, cell, sun)
            drawRect(Color(DayCycle.SUN_SHADE), Offset(cx + cell, cy + cell * 2f), Size(cell * 3f, cell))
            drawRect(Color(DayCycle.SUN_SHADE), Offset(cx + cell * 3f, cy), Size(cell, cell * 2f))
            drawRect(Color(DayCycle.SUN_LIGHT), Offset(cx - cell * 2f, cy - cell * 3f), Size(cell * 2f, cell))
            drawRect(Color(DayCycle.SUN_LIGHT), Offset(cx - cell * 3f, cy - cell * 2f), Size(cell, cell * 2f))
        }
    }
}

private fun Boolean.toFloat(): Float = if (this) 1f else 0f

/**
 * Pixel-Scheibe mit Radius [r] Zellen um ([cx], [cy]), Zeile für Zeile
 * als ein Rechteck — eine Scheibe sind so 2r+1 Rechtecke.
 */
internal fun DrawScope.pixelDisc(cx: Float, cy: Float, r: Int, cell: Float, color: Color, alpha: Float = 1f) {
    for (dy in -r..r) {
        val half = floor(sqrt((r + 0.5f) * (r + 0.5f) - dy * dy.toFloat())).toInt()
        drawRect(
            color,
            Offset(cx - half * cell, cy + dy * cell - cell / 2f),
            Size((half * 2 + 1) * cell, cell),
            alpha = alpha
        )
    }
}

// ===== Wolken =====

/**
 * Die Wolken einer Welt: drei statt zwei, in zwei Formen und zwei
 * Größen, jede mit eigener Geschwindigkeit — die kleine ist weiter weg
 * und zieht langsamer. Alle bleiben über dem Ring.
 *
 * Jede Wolke läuft auf ihrer eigenen Runde über die ganze Bildbreite
 * plus ihre eigene Breite: Sie verlässt das Bild links vollständig, bevor
 * sie rechts wieder hereinkommt. Vorher sprang sie mit `drift % (1,4 w)`
 * mitten im Bild aus dem Nichts an ihren Startplatz.
 *
 * [top] und [bottom] sind der Himmelsstreifen, in dem sie ziehen dürfen;
 * [speed] skaliert die Drift (das Schaufenster zieht schneller).
 */
internal fun DrawScope.drawClouds(
    color: Color,
    time: Float,
    cell: Float,
    top: Float,
    bottom: Float,
    speed: Float = 1f,
    light: Color = Color.White
) {
    val w = size.width
    val h = size.height
    val band = bottom - top
    for (i in CLOUD_START.indices) {
        val u = if (CLOUD_SMALL[i]) cell else cell * 2f
        val cw = u * 16f
        val lap = w + cw * 2f
        val drift = time * h * CLOUD_SPEED[i] * speed
        val x = ((CLOUD_START[i] * w - drift) % lap + lap) % lap - cw
        // Die Form reicht 1,5 Einheiten über y und 5 darunter.
        val y = snap(top + u * 1.5f + (band - u * 6.5f).coerceAtLeast(0f) * CLOUD_Y[i], cell)
        drawCloud(x.roundToInt().toFloat(), y, cell, color, CLOUD_SHAPE[i], u, light)
    }
}

private val CLOUD_START = floatArrayOf(0.1f, 0.78f, 0.45f)
private val CLOUD_Y = floatArrayOf(0.25f, 0.62f, 0.9f)
private val CLOUD_SPEED = floatArrayOf(0.01f, 0.012f, 0.006f)
private val CLOUD_SMALL = booleanArrayOf(false, false, true)
private val CLOUD_SHAPE = intArrayOf(0, 1, 0)

/** Auf ganze Zellen abrunden: Sonne, Mond und Sterne stehen im Raster. */
private fun snap(v: Float, cell: Float): Float = floor(v / cell) * cell
