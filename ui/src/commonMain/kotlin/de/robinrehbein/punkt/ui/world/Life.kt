package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.robinrehbein.punkt.game.Life
import de.robinrehbein.punkt.game.LifeKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Was sich in den Welten bewegt (LifeKind in :core): Vögel, Boote, Autos,
// Schnee. Alles hier hängt an der Laufuhr und einem festen Hash, nichts an
// einer Zufallsquelle — dasselbe Bild zur selben Zeit.
//
// Die Lesbarkeitsregel: Nichts kreuzt das Ringband. Unterwegs ist es im
// Himmelsstreifen über dem Ring ([WorldFrame.skyBottom]), im Band unter
// dem Ring ([WorldFrame.bandTop]) oder auf dem Boden. Der Schnee fällt als
// einziger über das ganze Bild — hinter der Bahn, klein und langsam.
//
// Wie in Backdrop.kt gleitet alles stufenlos: Sprites liegen im
// Zellraster relativ zu ihrem Ursprung, der Ursprung auf ganzen Pixeln.

/**
 * Die Lage eines Bildes: Uhr, Zelle, Nacht und die freien Streifen um den
 * Ring. Einmal pro Bild angelegt, von allen Bewohnern gelesen.
 */
internal class WorldFrame(
    val time: Float,
    val cell: Float,
    val night: Float,
    /** Die Bahn: Mitte und Radius. */
    val ring: RingGeometry,
    /** Oberkante des freien Himmels — darüber steht die Punktzahl. */
    val skyTop: Float,
    /** Unterkante des freien Himmels über dem Ring. */
    val skyBottom: Float,
    /** Oberkante des freien Bands unter dem Ring. */
    val bandTop: Float,
    /** Bodenkante ([de.robinrehbein.punkt.game.ScenePaint.groundY]). */
    val groundY: Float,
    /** Himmelsfarbe dieser Stufe — Nachttönung für Dinge, die nicht verblassen. */
    val sky: Color
)

/** Freie Streifen um die Bahn [ring]. */
internal fun DrawScope.worldFrame(
    time: Float,
    cell: Float,
    night: Float,
    ring: RingGeometry,
    groundY: Float,
    sky: Color
): WorldFrame {
    // Die Punktzahl steht 40 dp unter der Oberkante und ist gut 40 dp
    // hoch; der Ring trägt Blöcke von gut drei Zellen Kante, dazu Luft.
    var top = size.height * 0.12f
    var bottom = ring.cy - ring.radius - cell * 8f
    // Auf breiten Bildschirmen (Tablets) wird der Ring so groß, dass
    // zwischen Punktzahl und Ring kein Himmel bleibt. Dann ziehen Wolken
    // und Vögel über der Punktzahl, statt hinter ihr.
    if (bottom - top < cell * 14f) {
        top = cell * 2f
        bottom = size.height * 0.055f
    }
    return WorldFrame(
        time = time,
        cell = cell,
        night = night,
        ring = ring,
        skyTop = top,
        skyBottom = bottom,
        bandTop = ring.cy + ring.radius + cell * 8f,
        groundY = groundY,
        sky = sky
    )
}

/**
 * Wann ein Bewohner gezeichnet wird. Die Reihenfolge in drawTimingWorld:
 * HORIZONT gleich nach der fernen Ebene, HIMMEL nach den Wolken, VOR_BODEN
 * vor dem Boden (was dahinter abtaucht), BODEN nach dem Boden, LICHT nach
 * dem Nachtschleier — was leuchtet, soll nicht mit abgedunkelt werden.
 */
internal enum class LifeLayer { HORIZONT, HIMMEL, VOR_BODEN, BODEN, LICHT }

/** Zeichnet alle Bewohner der Welt, die in [layer] liegen. */
internal fun DrawScope.drawLife(life: List<Life>, layer: LifeLayer, f: WorldFrame) {
    for (i in life.indices) {
        val l = life[i]
        val c = l.colors
        when (l.kind) {
            LifeKind.SCHWARM -> if (layer == LifeLayer.HIMMEL) drawFlock(c, f)
            LifeKind.GLUEHWUERMCHEN -> if (layer == LifeLayer.LICHT) drawFireflies(c, f)
            LifeKind.GEIER -> if (layer == LifeLayer.HIMMEL) drawVulture(c, f)
            LifeKind.STEPPENLAEUFER -> if (layer == LifeLayer.BODEN) drawTumbleweed(c, f)
            LifeKind.MOEWEN -> if (layer == LifeLayer.HIMMEL) drawGulls(c, f)
            LifeKind.SEGELBOOT -> if (layer == LifeLayer.HORIZONT) drawSailboat(c, f)
            LifeKind.DELFIN -> when (layer) {
                LifeLayer.VOR_BODEN -> drawDolphin(c, f)
                LifeLayer.BODEN -> drawDolphinSplash(c, f)
                else -> Unit
            }
            LifeKind.SCHNEEFALL -> if (layer == LifeLayer.LICHT) drawSnow(c, f)
            LifeKind.FLUGZEUG -> if (layer == LifeLayer.HIMMEL) drawPlane(c, f)
            LifeKind.AUTO -> when (layer) {
                LifeLayer.BODEN -> drawCars(c, f, lights = false)
                LifeLayer.LICHT -> drawCars(c, f, lights = true)
                else -> Unit
            }
            LifeKind.PLANET -> if (layer == LifeLayer.HIMMEL) drawPlanet(c, f)
            LifeKind.SATELLIT -> if (layer == LifeLayer.HIMMEL) drawSatellite(c, f)
        }
    }
}

// ===== Sprites =====

/**
 * Zeichnet eine Pixel-Maske: eine Zelle [u] pro Zeichen, '1'…'9' wählen
 * die Farbe aus [colors] (Index Ziffer − 1), 'o' ist die Kontur, '.'
 * bleibt frei. Gleiche Nachbarn einer Zeile werden ein Rechteck.
 *
 * [flip] spiegelt waagrecht (Blickrichtung), [shear] versetzt jede Spalte
 * um shear · (Spalte − Mitte) Zellen senkrecht — so neigt sich ein
 * springender Delfin, ohne dass es drei Masken braucht. [tint] legt eine
 * Farbe mit [tintAlpha] darüber (Nachttönung), ohne die Farben zu mischen.
 */
private fun DrawScope.mask(
    x: Float,
    y: Float,
    u: Float,
    rows: Array<String>,
    colors: List<Long>,
    alpha: Float = 1f,
    flip: Boolean = false,
    shear: Float = 0f,
    tint: Color? = null,
    tintAlpha: Float = 0f,
    outline: Boolean = false
) {
    // Mit Kontur zwei Durchgänge: erst jede Zelle eine Zelle breiter in
    // Konturfarbe, dann die Flächen darüber — wie bei den Requisiten.
    if (outline) maskPass(x, y, u, rows, colors, alpha, flip, shear, null, 0f, grow = true)
    maskPass(x, y, u, rows, colors, alpha, flip, shear, tint, tintAlpha, grow = false)
}

private fun DrawScope.maskPass(
    x: Float,
    y: Float,
    u: Float,
    rows: Array<String>,
    colors: List<Long>,
    alpha: Float,
    flip: Boolean,
    shear: Float,
    tint: Color?,
    tintAlpha: Float,
    grow: Boolean
) {
    val width = rows[0].length
    val mid = (width - 1) / 2f
    for (r in rows.indices) {
        val row = rows[r]
        var k = 0
        while (k < row.length) {
            val ch = row[k]
            if (ch == '.' || ch == ' ') {
                k++
                continue
            }
            // Mit Scherung jede Spalte einzeln, sonst gleiche Nachbarn zusammen.
            var end = k + 1
            if (shear == 0f) while (end < row.length && row[end] == ch) end++
            val color = if (grow || ch == 'o') OutlineColor else Color(colors[ch - '1'])
            val col = if (flip) width - end else k
            // Die Scherung hängt an der Spalte der Maske, nicht an der
            // gezeichneten: Gespiegelt bleibt die Nase vorn oben.
            val dy = if (shear == 0f) 0f else (shear * (k - mid)).roundToInt() * u
            val g = if (grow) u else 0f
            val left = x + col * u - g
            val top = y + r * u + dy - g
            val size = Size((end - k) * u + g * 2f, u + g * 2f)
            drawRect(color, Offset(left, top), size, alpha = alpha)
            if (tint != null && tintAlpha > 0f) drawRect(tint, Offset(left, top), size, alpha = alpha * tintAlpha)
            k = end
        }
    }
}

private fun px(v: Float): Float = v.roundToInt().toFloat()

/** Eine Höhe im freien Himmel: 0 = gleich unter der Punktzahl, 1 = knapp über dem Ring. */
private fun WorldFrame.skyY(t: Float): Float = skyTop + (skyBottom - skyTop) * t

/** Wie weit etwas am Tag Sichtbares nachts schon verblasst ist (1 = voll da). */
private fun dayAlpha(night: Float, speed: Float): Float = (1f - night * speed).coerceIn(0f, 1f)

// ===== WIESE =====

private val BIRD_UP = arrayOf(
    "1.....1",
    ".1...1.",
    "..111.."
)
private val BIRD_DOWN = arrayOf(
    "..111..",
    ".1...1.",
    "1.....1"
)

/** Keilformation, von vorn nach hinten versetzt (Zellen). */
private val FLOCK_DX = floatArrayOf(0f, 6f, 6f, 12f, 12f)
private val FLOCK_DY = floatArrayOf(0f, -3.5f, 3.5f, -7f, 7f)

/** Ein kleiner Schwarm im Keil, alle 17 Sekunden einmal von rechts nach links. */
private fun DrawScope.drawFlock(c: List<Long>, f: WorldFrame) {
    val a = dayAlpha(f.night, 1.6f)
    if (a <= 0f) return
    val w = size.width
    val cell = f.cell
    val period = 17f
    val cycle = floor(f.time / period).toInt()
    val local = f.time - cycle * period
    val lead = w + cell * 4f - local * w * 0.085f
    if (lead < -cell * 24f) return
    val y0 = f.skyY(0.3f + 0.4f * worldHash(cycle * 3 + 1))
    for (i in FLOCK_DX.indices) {
        val flap = ((f.time * 5f + i * 0.37f).toInt() % 2) == 0
        val bob = sin(f.time * 2.2f + i * 1.3f) * cell * 0.6f
        mask(
            px(lead + FLOCK_DX[i] * cell), px(y0 + FLOCK_DY[i] * cell + bob), cell,
            if (flap) BIRD_UP else BIRD_DOWN, c, alpha = a
        )
    }
}

/**
 * Glühwürmchen über dem Gras, ab der Dämmerung: Jedes schwebt auf seiner
 * eigenen kleinen Bahn und glimmt in seinem eigenen Takt.
 */
private fun DrawScope.drawFireflies(c: List<Long>, f: WorldFrame) {
    val a = ((f.night - 0.25f) / 0.5f).coerceIn(0f, 1f)
    if (a <= 0f) return
    val w = size.width
    val cell = f.cell
    val core = Color(c[0])
    val glow = Color(c[1])
    val top = maxOf(f.bandTop, f.groundY - size.height * 0.15f)
    val band = f.groundY - cell * 2f - top
    for (i in 0 until 14) {
        val bx = worldHash(i * 4 + 900) * w
        val by = top + worldHash(i * 4 + 901) * band
        val x = bx + sin(f.time * 0.35f + i * 2.1f) * w * 0.05f
        val y = by + sin(f.time * 0.6f + i * 1.3f) * band * 0.18f
        val blink = sin(f.time * (1.2f + worldHash(i * 4 + 902) * 1.6f) + i * 2.7f)
        if (blink <= 0f) continue
        val b = blink * blink * a
        val gx = px(x)
        val gy = px(y)
        drawRect(glow, Offset(gx - cell, gy - cell), Size(cell * 3f, cell * 3f), alpha = b * 0.3f)
        drawRect(core, Offset(gx, gy), Size(cell, cell), alpha = b)
    }
}

// ===== WÜSTE =====

private val VULTURE = arrayOf(
    "22...........22",
    ".222.......222.",
    "..32211111122..",
    "......111......"
)

/** Ein Geier, der oben links in einer flachen Ellipse kreist. */
private fun DrawScope.drawVulture(c: List<Long>, f: WorldFrame) {
    val a = dayAlpha(f.night, 1.5f)
    if (a <= 0f) return
    val w = size.width
    val cell = f.cell
    val theta = f.time * 0.3f
    val cx = w * 0.22f
    val cy = f.skyY(0.35f)
    val x = cx + cos(theta) * w * 0.11f - cell * 7.5f
    val y = cy + sin(theta) * cell * 4f
    // Kopf voran: Die Maske blickt nach links, nach rechts gespiegelt.
    val toRight = -sin(theta) > 0f
    mask(px(x), px(y), cell, VULTURE, c, alpha = a, flip = toRight)
}

private val TUMBLEWEED = arrayOf(
    "..21112..",
    ".2..1..2.",
    "2.113.1.2",
    "1.3..31.1",
    "113.1..31",
    "1.13..1.1",
    "2..31.3.2",
    ".2..1..2.",
    "..21112.."
)

/** Die Maske um eine Vierteldrehung gedreht, je Drehstufe einmal angelegt. */
private val TUMBLEWEED_TURNS: Array<Array<String>> = Array(4) { turn ->
    var m = TUMBLEWEED
    repeat(turn) {
        val n = m.size
        m = Array(n) { r -> CharArray(n) { k -> m[n - 1 - k][r] }.concatToString() }
    }
    m
}

/**
 * Ein Steppenläufer, der alle zwölf Sekunden von rechts nach links über
 * den Boden rollt und dabei hüpft. Er dreht sich in Vierteldrehungen wie
 * ein Sprite aus einem alten Spiel; darunter ein flacher Schatten, der
 * schmaler wird, je höher er springt.
 */
private fun DrawScope.drawTumbleweed(c: List<Long>, f: WorldFrame) {
    val w = size.width
    val cell = f.cell
    val period = 12f
    val cycle = floor(f.time / period).toInt()
    val local = f.time - cycle * period
    val speed = w * 0.2f
    val x = w + cell * 6f - local * speed
    if (x < -cell * 12f) return
    val hop = abs(sin(local * 1.8f * PI.toFloat())) * size.height * 0.012f * (0.6f + 0.4f * worldHash(cycle))
    val sw = cell * (7f - (hop / cell).coerceAtMost(4f))
    drawRect(OutlineColor, Offset(px(x + cell * 4.5f - sw / 2f), f.groundY - cell), Size(sw, cell), alpha = 0.22f)
    // Rollt nach links, dreht also gegen den Uhrzeigersinn.
    val turns = (local * speed / (cell * 4.5f) / (PI.toFloat() / 2f)).toInt()
    val m = TUMBLEWEED_TURNS[(4 - turns % 4) % 4]
    mask(px(x), px(f.groundY - cell * 10f - hop), cell, m, c)
}

// ===== MEER =====

private val GULL_GLIDE = arrayOf(
    "2.......2",
    ".11...11.",
    "...111..."
)
private val GULL_FLAP = arrayOf(
    "...111...",
    ".11...11.",
    "2.......2"
)

/** Möwen: drei, jede mit eigener Runde, mal von links, mal von rechts. */
private fun DrawScope.drawGulls(c: List<Long>, f: WorldFrame) {
    val a = dayAlpha(f.night, 1.4f)
    if (a <= 0f) return
    val w = size.width
    val cell = f.cell
    for (i in 0 until 3) {
        val period = 13f + i * 4f
        val t = f.time + i * 5.3f
        val cycle = floor(t / period).toInt()
        val local = t - cycle * period
        val lap = w + cell * 20f
        val travel = local * w * 0.07f
        if (travel > lap) continue
        val fromLeft = (cycle + i) % 2 == 0
        val x = if (fromLeft) travel - cell * 10f else w + cell * 10f - travel
        val y = f.skyY(0.15f + 0.6f * worldHash(cycle * 7 + i)) + sin(t * 0.9f + i) * cell * 3f
        // Meist gleiten, zwischendurch ein paar Flügelschläge.
        val flapping = (t % 3f) < 0.8f && ((t * 6f).toInt() % 2 == 0)
        mask(px(x), px(y), cell, if (flapping) GULL_FLAP else GULL_GLIDE, c, alpha = a)
    }
}

private val SAILBOAT = arrayOf(
    "....4....",
    "....41...",
    "....411..",
    "...2411..",
    "...24111.",
    "..224111.",
    "..2241111",
    "....4....",
    "333333333",
    ".444444.."
)

/** Horizontlinie des MEERs — dieselbe Rechnung wie in drawHorizon. */
private fun horizonY(groundY: Float, h: Float, cell: Float): Float =
    floor((groundY - h * 0.04f) / cell) * cell

/**
 * Ein Segelboot, das langsam am Horizont entlang nach rechts kreuzt und
 * in der Dünung schaukelt. Nachts brennt oben am Mast eine Laterne.
 */
private fun DrawScope.drawSailboat(c: List<Long>, f: WorldFrame) {
    val w = size.width
    val cell = f.cell
    val lap = w * 1.6f
    val x = (f.time * w * 0.02f + w * 0.55f) % lap - w * 0.3f
    val horizon = horizonY(f.groundY, size.height, cell)
    val y = horizon - cell * 8f + sin(f.time * 1.4f) * cell * 0.4f
    val bx = px(x)
    val by = px(y)
    mask(bx, by, cell, SAILBOAT, c, tint = f.sky, tintAlpha = f.night * 0.45f)
    if (f.night > 0.25f) {
        val a = ((f.night - 0.25f) / 0.5f).coerceIn(0f, 1f)
        val lamp = Color(c[4])
        drawRect(lamp, Offset(bx + cell * 4f, by - cell), Size(cell, cell), alpha = a)
        drawRect(lamp, Offset(bx + cell * 3f, by - cell * 2f), Size(cell * 3f, cell * 3f), alpha = a * 0.3f)
    }
}

private val DOLPHIN = arrayOf(
    "......3.....",
    ".....33.....",
    "3...311113..",
    "33311111111.",
    "3...2222223.",
)

private const val DOLPHIN_PERIOD = 8f
private const val DOLPHIN_JUMP = 1.5f

/**
 * Ein Delfin, der alle acht Sekunden aus dem Wasser springt: ein Bogen
 * über dem Boden, der Körper neigt sich mit der Bahn. Er wird VOR dem
 * Boden gezeichnet — beim Ein- und Auftauchen verdecken ihn die Wellen.
 */
private fun DrawScope.drawDolphin(c: List<Long>, f: WorldFrame) {
    val jump = dolphinJump(f) ?: return
    val (x, y, shear, flip) = jump
    mask(px(x), px(y), f.cell, DOLPHIN, c, flip = flip, shear = shear, outline = true)
}

/** Gischt, wo der Delfin auftaucht und wieder eintaucht — vor den Wellen. */
private fun DrawScope.drawDolphinSplash(c: List<Long>, f: WorldFrame) {
    val w = size.width
    val cell = f.cell
    val cycle = floor(f.time / DOLPHIN_PERIOD).toInt()
    val local = f.time - cycle * DOLPHIN_PERIOD
    if (local > DOLPHIN_JUMP + 0.4f) return
    val x0 = w * (0.12f + 0.76f * worldHash(cycle * 5 + 2))
    val dir = if (worldHash(cycle * 5 + 3) > 0.5f) 1f else -1f
    val span = w * 0.2f
    val foam = Color(c[3])
    for (end in 0..1) {
        val t0 = if (end == 0) 0.05f else DOLPHIN_JUMP * 0.95f
        val age = local - t0
        if (age < 0f || age > 0.45f) continue
        val sx = x0 + dir * span * (if (end == 0) -0.42f else 0.42f)
        val fade = 1f - age / 0.45f
        for (k in 0 until 5) {
            val ox = (k - 2) * cell * 1.5f * (1f + age * 2f)
            val oy = -age * cell * 14f * (1f - abs(k - 2) * 0.25f) + age * age * cell * 30f
            drawRect(foam, Offset(px(sx + ox), px(f.groundY - cell * 2f + oy)), Size(cell, cell), alpha = fade)
        }
    }
}

private data class Jump(val x: Float, val y: Float, val shear: Float, val flip: Boolean)

private fun DrawScope.dolphinJump(f: WorldFrame): Jump? {
    val w = size.width
    val cell = f.cell
    val cycle = floor(f.time / DOLPHIN_PERIOD).toInt()
    val local = f.time - cycle * DOLPHIN_PERIOD
    if (local > DOLPHIN_JUMP) return null
    val q = local / DOLPHIN_JUMP
    val x0 = w * (0.12f + 0.76f * worldHash(cycle * 5 + 2))
    val dir = if (worldHash(cycle * 5 + 3) > 0.5f) 1f else -1f
    val span = w * 0.2f
    val height = size.height * 0.055f
    val x = x0 + dir * span * (q - 0.5f) - cell * 6f
    val y = f.groundY + cell * 3f - 4f * q * (1f - q) * height
    // Steigung der Bahn: vorn hoch beim Auftauchen, vorn tief beim Eintauchen.
    val shear = -(1f - 2f * q) * 0.55f
    return Jump(x, y, shear, dir < 0f)
}

// ===== BERG =====

/**
 * Leiser Schneefall über das ganze Bild, hinter der Bahn, in zwei Tiefen:
 * viele kleine, blasse Flocken, die langsam fallen, und wenige große, die
 * schneller fallen und weiter pendeln. Alle treiben mit dem Wind nach
 * links; am Boden verschwinden sie. Innerhalb des Rings sind sie
 * halb so deutlich — dort wird getippt.
 */
private fun DrawScope.drawSnow(c: List<Long>, f: WorldFrame) {
    val w = size.width
    val cell = f.cell
    val fall = f.groundY + cell * 4f
    val inner = f.ring.radius + cell * 6f
    for (i in 0 until 70) {
        val near = i % 3 == 0
        val v = size.height * (if (near) 0.05f else 0.022f) * (0.8f + 0.4f * worldHash(i * 3 + 700))
        val y = (worldHash(i * 3 + 701) * fall + f.time * v) % fall - cell * 3f
        val swing = if (near) cell * 4f else cell * 2f
        val sway = sin(f.time * (0.5f + worldHash(i + 55) * 0.6f) + i * 1.9f) * swing
        val wind = f.time * size.height * (if (near) 0.01f else 0.005f)
        val x = ((worldHash(i * 3 + 702) * w + sway - wind) % w + w) % w
        val dx = x - f.ring.cx
        val dy = y - f.ring.cy
        val damp = if (dx * dx + dy * dy < inner * inner) 0.5f else 1f
        if (near) {
            drawRect(Color(c[0]), Offset(px(x), px(y)), Size(cell * 2f, cell * 2f), alpha = 0.9f * damp)
        } else {
            drawRect(Color(c[1]), Offset(px(x), px(y)), Size(cell, cell), alpha = 0.6f * damp)
        }
    }
}

// ===== STADT =====

private val PLANE = arrayOf(
    "............2...",
    "...........112..",
    ".111111111111112",
    "13.3.3.3.3.31111",
    ".1111222211111..",
    "......22........"
)

/**
 * Ein Flugzeug, alle 24 Sekunden über den Himmel, abwechselnd nach links
 * und nach rechts. Am Tag zieht es einen Kondensstreifen, nachts blinken
 * Positionslicht und Blitzer.
 */
private fun DrawScope.drawPlane(c: List<Long>, f: WorldFrame) {
    val w = size.width
    val cell = f.cell
    val period = 24f
    val cycle = floor(f.time / period).toInt()
    val local = f.time - cycle * period
    val travel = local * w * 0.075f
    val lap = w + cell * 60f
    if (travel > lap) return
    val toLeft = cycle % 2 == 0
    val x = if (toLeft) w + cell * 2f - travel else travel - cell * 18f
    val y = f.skyY(0.1f + 0.6f * worldHash(cycle * 11 + 5))
    val bx = px(x)
    val by = px(y)
    // Kondensstreifen hinter dem Heck, am Tag.
    val trail = dayAlpha(f.night, 1.4f)
    if (trail > 0f) {
        for (k in 0 until 8) {
            val len = cell * 6f
            val tx = if (toLeft) bx + cell * 16f + k * len else bx - (k + 1) * len
            drawRect(Color(c[0]), Offset(tx, by + cell * 2f), Size(len, cell), alpha = trail * 0.6f * (1f - k / 8f))
        }
    }
    mask(bx, by, cell, PLANE, c, flip = !toLeft, tint = f.sky, tintAlpha = f.night * 0.5f)
    if (f.night > 0.25f) {
        val blink = (f.time * 1.5f) % 1f < 0.15f
        val wingX = if (toLeft) bx + cell * 6f else bx + cell * 9f
        drawRect(Color(c[3]), Offset(wingX, by + cell * 5f), Size(cell, cell))
        if (blink) {
            val tailX = if (toLeft) bx + cell * 12f else bx + cell * 3f
            drawRect(Color.White, Offset(tailX, by - cell), Size(cell, cell))
            drawRect(Color.White, Offset(tailX - cell, by - cell * 2f), Size(cell * 3f, cell * 3f), alpha = 0.3f)
        }
    }
}

private val CAR = arrayOf(
    "....111111......",
    "...15515551.....",
    ".11111111111111.",
    "7111111111111118",
    "2222222222222222",
    ".2662222222662..",
    "..66........66.."
)

/**
 * Zwei Autos auf der oberen Fahrspur, in Fahrtrichtung links, mit großem
 * Abstand. [lights] zeichnet nur die Lichter (nach dem Nachtschleier):
 * Scheinwerferkegel nach vorn und ein Glühen an den Rücklichtern.
 */
private fun DrawScope.drawCars(c: List<Long>, f: WorldFrame, lights: Boolean) {
    if (lights && f.night <= 0.25f) return
    val w = size.width
    val cell = f.cell
    val speed = w * 0.19f
    val lap = w * 3f
    val y = px(f.groundY + cell * 8f)
    for (i in 0 until 2) {
        val x = w + cell * 4f - ((f.time * speed + i * w * 1.5f) % lap)
        if (x < -cell * 40f || x > w + cell * 4f) continue
        val bx = px(x)
        if (!lights) {
            val paint = if (i == 0) listOf(c[0], c[1], 0L, 0L, c[4], c[5], c[6], c[7])
            else listOf(c[2], c[3], 0L, 0L, c[4], c[5], c[6], c[7])
            mask(bx, y, cell, CAR, paint, outline = true)
        } else {
            val a = ((f.night - 0.25f) / 0.5f).coerceIn(0f, 1f)
            val head = Color(c[6])
            for (k in 0 until 5) {
                val grow = k + 1f
                drawRect(
                    head,
                    Offset(bx - cell * 4f * (k + 1), y + cell * (3f - grow * 0.5f)),
                    Size(cell * 4f, cell * grow),
                    alpha = a * (0.35f - k * 0.06f)
                )
            }
            drawRect(head, Offset(bx, y + cell * 3f), Size(cell, cell), alpha = a)
            drawRect(Color(c[7]), Offset(bx + cell * 14f, y + cell * 2f), Size(cell * 3f, cell * 3f), alpha = a * 0.35f)
        }
    }
}

// ===== WELTRAUM =====

/**
 * Ein Ringplanet oben rechts: Scheibe mit Bändern und Schattenseite, der
 * Ring als flache Ellipse — hinten verdeckt vom Planeten, vorn davor. Ein
 * kleiner Mond umrundet ihn.
 */
private fun DrawScope.drawPlanet(c: List<Long>, f: WorldFrame) {
    val w = size.width
    val cell = f.cell
    val cx = snapTo(w * 0.8f, cell)
    val cy = snapTo(f.skyY(0.45f), cell)
    val r = 6
    val rx = 12
    val ry = 3
    val theta = f.time * 0.45f
    val moonX = px(cx + cos(theta) * cell * (rx + 2))
    val moonY = px(cy + sin(theta) * cell * (ry + 3))
    val moon = Color(c[5])
    if (sin(theta) < 0f) drawRect(moon, Offset(moonX, moonY), Size(cell * 2f, cell * 2f))

    ringArc(cx, cy, rx, ry, cell, Color(c[3]), Color(c[4]), front = false)
    val body = Color(c[0])
    val shade = Color(c[1])
    val band = Color(c[2])
    for (dy in -r..r) {
        val half = floor(sqrt((r + 0.5f) * (r + 0.5f) - dy * dy.toFloat())).toInt()
        val left = cx - half * cell
        val full = (half * 2 + 1) * cell
        val lit = snapTo(full * 0.68f, cell)
        val y = cy + dy * cell - cell / 2f
        drawRect(if (dy == -2 || dy == 1 || dy == 2) band else body, Offset(left, y), Size(lit, cell))
        drawRect(shade, Offset(left + lit, y), Size(full - lit, cell))
    }
    ringArc(cx, cy, rx, ry, cell, Color(c[3]), Color(c[4]), front = true)
    if (sin(theta) >= 0f) drawRect(moon, Offset(moonX, moonY), Size(cell * 2f, cell * 2f))
}

/** Eine Hälfte des Planetenrings: hinten die obere, vorn die untere. */
private fun DrawScope.ringArc(
    cx: Float,
    cy: Float,
    rx: Int,
    ry: Int,
    cell: Float,
    ring: Color,
    ringShade: Color,
    front: Boolean
) {
    for (dx in -rx..rx) {
        val t = dx / rx.toFloat()
        val yy = (ry * sqrt((1f - t * t).coerceAtLeast(0f))).roundToInt()
        val x = cx + dx * cell - cell / 2f
        if (front) {
            drawRect(ring, Offset(x, cy + yy * cell - cell / 2f), Size(cell, cell))
            drawRect(ringShade, Offset(x, cy + yy * cell + cell / 2f), Size(cell, cell))
        } else {
            drawRect(ring, Offset(x, cy - yy * cell - cell / 2f), Size(cell, cell))
        }
    }
}

private fun snapTo(v: Float, cell: Float): Float = floor(v / cell) * cell

private val SATELLITE = arrayOf(
    "......111......",
    "23232.111.23232",
    "323231111132323",
    "23232.111.23232"
)

/** Ein Satellit, der alle 34 Sekunden langsam unter dem Ring vorbeizieht. */
private fun DrawScope.drawSatellite(c: List<Long>, f: WorldFrame) {
    val w = size.width
    val cell = f.cell
    val period = 34f
    val cycle = floor(f.time / period).toInt()
    val local = f.time - cycle * period
    val x = local * w * 0.045f - cell * 16f
    if (x > w + cell) return
    val y = f.bandTop + (f.groundY - f.bandTop) * (0.2f + 0.45f * worldHash(cycle * 13 + 7))
    val bx = px(x)
    val by = px(y)
    mask(bx, by, cell, SATELLITE, c)
    if ((f.time * 1.2f) % 1f < 0.25f) {
        drawRect(Color(c[3]), Offset(bx + cell * 7f, by - cell), Size(cell, cell))
    }
}
