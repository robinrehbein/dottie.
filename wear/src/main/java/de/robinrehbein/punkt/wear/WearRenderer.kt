package de.robinrehbein.punkt.wear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SkinState
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.game.TrapPaint
import de.robinrehbein.punkt.game.Twist
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ===== Palette 1:1 aus app/.../ui/screens/GameOverlays.kt und
// TimingGameScreen.kt übernommen, damit der Wear-Prototyp optisch zur
// Telefon-Version passt, ohne eine Abhängigkeit auf :app zu brauchen. =====

// Die Himmelsfarben kommen aus ScenePaint (:core) — die Uhr zieht sie nur
// LESEND mit, gewählt wird eine Kulisse hier nicht. Ohne Boden und
// Szenerie ist der Himmel ohnehin alles, was von einer Kulisse auf das
// kleine Display passt; die Bahn bleibt in jeder Kulisse unverändert.

internal val WearOutlineColor = Color(0xFF543847)
internal val WearGrassLight = Color(0xFF9DE85A)
internal val WearGrassDark = Color(0xFF74BF2E)

/** Standard-Segmentfarbe außerhalb jeder Zone (Sand-Ton aus GameOverlays.kt). */
internal val WearTrackDefaultColor = Color(0xFFD3C87E)

/**
 * Gold-Akzent (DotBody am Phone) für Overlay-Texte und der Glanzton der
 * Medaillen-Münze. Der Vogel selbst zeichnet nicht mehr mit diesen
 * Konstanten, sondern mit dem gewählten Skin (WearDotSkin) — KLASSIK
 * trägt dieselben Werte.
 */
internal val WearDotBody = Color(0xFFFFD847)
internal val WearDotShine = Color(0xFFFFF3B8)

/**
 * Nebelband (Twist NEBEL, intern GHOST): Rand und Kern. Dieselben Töne wie
 * die Wolke am Telefon (Unterkante und Inneres), aber ohne Wolkenform —
 * auf der Uhr reicht ein einfaches Band (Plan 8.6 Punkt 15).
 */
internal val WearFogEdgeColor = Color(0xFFA0BEDA)
internal val WearFogMidColor = Color(0xFFD6E5F4)
internal val WearFogCoreColor = Color(0xFFF4F8FD)

/** Raster-Auflösung für den Pixel-Vogel, wie GRID in GameOverlays.kt. */
private const val WEAR_GRID = 13f

/**
 * Mario-Tod, Werte 1:1 aus TimingGameScreen.kt: Nach dem Todes-Freeze
 * hüpft der Vogel mit dieser Anfangsgeschwindigkeit nach oben und fällt
 * dann mit der Gravitation unten aus dem Bild — beides in Bildhöhen pro
 * Sekunde(²), skaliert also automatisch aufs kleine Display.
 */
private const val WEAR_DEATH_HOP_SPEED = 1.6f
private const val WEAR_DEATH_GRAVITY = 6f

/**
 * Während des Hüpfers dreht sich der Vogel um 180° auf den Rücken und
 * fällt kopfüber — die Drehung ist am Scheitelpunkt (~0,27s) fertig.
 */
private const val WEAR_DEATH_FLIP_SECONDS = 0.3f

/** Vogelradius im Verhältnis zur kleineren Displaykante. */
private const val WEAR_DOT_RADIUS_SHARE = 0.075f

/** Segmentzahl der Bahn — dieselbe wie am Phone (drawTrack). */
internal const val WEAR_TRACK_SEGMENTS = 60

/**
 * Blockbreiten als Vielfaches des Segment-Abstands. Die Werte stammen aus
 * der Phone-Bahn: Dort liegen bei 60 Segmenten rund 41 Pixel zwischen den
 * Mittelpunkten, die neutralen Blöcke sind 30 Pixel breit (0,74) und die
 * Zonen-Blöcke 50 (1,23). Genau daraus entsteht der gewollte Unterschied —
 * die Kette behält ihre Lücken, die Zone überlappt sich zu einem
 * durchgehenden Band.
 *
 * Warum am Abstand statt an einer festen Zellgröße (so war es vorher):
 * `floor(d / 220)` ergibt auf einem 480er Uhren-Display die Zellgröße 2,
 * damit waren die Blöcke nur 6 bzw. 14 Pixel breit — bei 29 Pixeln
 * Abstand. Die Kette zerfiel in Staubkörner und die Zone blieb eine Reihe
 * einzelner Quadrate statt eines Bandes. Am Abstand gerechnet stimmen die
 * Proportionen auf jeder Displaygröße von selbst.
 */
private const val WEAR_SEG_NEUTRAL = 0.74f
private const val WEAR_SEG_ZONE = 1.23f

/** Farbkern im Verhältnis zum Block — am Phone 1.8/3 bzw. 3.4/5. */
private const val WEAR_CORE_NEUTRAL = 0.6f
private const val WEAR_CORE_ZONE = 0.68f

/**
 * Zeichnet die komplette Spielwelt für das runde Wear-Display: Himmel je
 * nach Score-Stufe, die Bahn als Perlenkette und den Vogel — kein
 * Szenerie-/Boden-Hintergrund wie am Phone, das Display ist dafür zu klein.
 *
 * [hour] und [month] kommen von der Geräte-Uhr (Controller): TAGESZEIT
 * und JAHRESZEIT ziehen daraus ihr Kleid. [scene] ist die Kulisse des
 * Telefons — die Uhr wählt keine, sie zeigt nur, was ankommt.
 * [dotWobble] schiebt den Vogel seitlich, in Vogel-Pixeln: das Wackeln
 * nach einem Tap daneben in READY (siehe WearNotYet.wobble).
 */
internal fun DrawScope.drawWearWorld(
    game: TimingGame,
    skin: WearDotSkin,
    hour: Int,
    month: Int,
    scene: SceneId = SceneId.WIESE,
    dotWobble: Float = 0f
) {
    val d = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f

    val sky = Color(ScenePaint.skyFor(scene, game.score))
    drawRect(color = sky, topLeft = Offset.Zero, size = size)

    // Radius proportional zu minDimension statt zur Bildhöhe wie am Phone —
    // auf der Uhr sind Breite und Höhe (fast) identisch, aber minDimension
    // ist robust gegenüber eckigen/ovalen Displays.
    val radius = d * 0.38f
    drawWearTrack(game, cx, cy, radius)
    // Die Nebelbank liegt über der Bahn und unter dem Vogel. Verdeckt wird
    // der Vogel nicht vom Band, sondern von der Engine (isDotVisible) —
    // das Band zeigt nur, wo das passiert.
    drawFogBand(game, cx, cy, radius, d * WEAR_DOT_RADIUS_SHARE)
    // In OVER ist der Vogel bereits unten aus dem Bild gefallen
    // (Mario-Hüpfer in der DYING-Phase) — die Bahn bleibt leer, bis der
    // nächste Lauf startet. Am Phone regelt das fx.deathTime genauso.
    if (game.phase != GamePhase.OVER && game.isDotVisible) {
        drawWearDot(game, cx, cy, radius, d, skin, hour, month, dotWobble)
    }
}

/**
 * Die Kreisbahn als Kette blockiger Zellen — dieselbe Zeichnung wie
 * drawTrack am Phone, nur dass die Blockgrößen hier aus dem
 * Segment-Abstand folgen (siehe WEAR_SEG_*), damit die Proportionen auf
 * dem kleinen Display erhalten bleiben.
 *
 * Die Falle (Twist FAKE) ist wie am Telefon eine Kette aus Minen aus
 * [TrapPaint], auf der Uhr aber ohne Lauflicht (Plan 8.6 Punkt 15): alle
 * Kugeln schwarz. Wo eine Mine liegt, bleibt die Bahn frei.
 */
private fun DrawScope.drawWearTrack(
    game: TimingGame,
    cx: Float,
    cy: Float,
    radius: Float
) {
    val segments = WEAR_TRACK_SEGMENTS
    // Abstand zwischen zwei Segment-Mittelpunkten. Auf ganze Pixel
    // gerundet, damit die Blöcke ihre harten Kanten behalten.
    val spacing = 2f * Math.PI.toFloat() * radius / segments
    val neutralOuter = round(spacing * WEAR_SEG_NEUTRAL).coerceAtLeast(2f)
    val zoneOuter = round(spacing * WEAR_SEG_ZONE).coerceAtLeast(4f)
    val neutralInner = round(neutralOuter * WEAR_CORE_NEUTRAL).coerceAtLeast(1f)
    val zoneInner = round(zoneOuter * WEAR_CORE_ZONE).coerceAtLeast(2f)

    val zoneHalf = game.effectiveZoneHalf()
    // Kern und Fallenbreite kommen aus der Engine — siehe perfectHalf()
    // und fakeZoneHalf(). Die Minen verteilen sich über fakeZoneHalf().
    val coreHalf = game.perfectHalf()
    val fakeHalf = game.fakeZoneHalf()
    val mines = wearTrapMineAngles(game, segments, zoneHalf)
    val minePx = wearMinePixel(wearMineDistance(game, segments, radius), zoneOuter)
    val mineHalf = wearMineHalfExtent(minePx)
    val mineCenters = mines.map { Offset(cx + cos(it) * radius, cy + sin(it) * radius) }

    for (k in 0 until segments) {
        val a = k.toFloat() / segments * (2f * Math.PI.toFloat())
        val px = cx + cos(a) * radius
        val py = cy + sin(a) * radius

        val relativeZone = TimingGame.wrapToPi(a - game.zoneCenter)
        val inZone = abs(relativeZone) <= zoneHalf
        val inPerfectCore = abs(relativeZone) <= coreHalf

        val inFake = game.hasFakeZone &&
            abs(TimingGame.wrapToPi(a - game.fakeZoneCenter)) <= fakeHalf
        // Auf der Falle liegen die Minen statt der Blöcke. Liegt sie auf
        // der Zone, gewinnt die Zone: Grün bleibt Grün.
        if (inFake && !inZone) continue

        val outer = if (inZone) zoneOuter else neutralOuter
        // Die Minen liegen nicht auf dem Segment-Raster: Ein Sandblock,
        // den eine Mine berühren würde, bleibt ebenfalls frei.
        if (!inZone && wearBlockHitsMine(px, py, outer / 2f, mineCenters, mineHalf)) continue
        val inner = if (inZone) zoneInner else neutralInner
        val innerColor = when {
            inPerfectCore -> WearGrassLight
            inZone -> WearGrassDark
            else -> WearTrackDefaultColor
        }

        drawRect(
            color = WearOutlineColor,
            topLeft = Offset(px - outer / 2f, py - outer / 2f),
            size = Size(outer, outer)
        )
        drawRect(
            color = innerColor,
            topLeft = Offset(px - inner / 2f, py - inner / 2f),
            size = Size(inner, inner)
        )
    }

    // Erst alle Ränder, dann alle Kugeln: Benachbarte Minen teilen sich
    // ihren Rand, die Kugeln berühren sich nie (wearMinePixel).
    for (c in mineCenters) drawWearMine(c.x, c.y, minePx, rimOnly = true)
    for (c in mineCenters) drawWearMine(c.x, c.y, minePx, rimOnly = false)
}

// ===== Minen der Falle (Plan 3.4, auf der Uhr ohne Lauflicht) =====

/**
 * Winkel der Minen der aktuellen Falle auf der Bahn.
 *
 * Die Zahl kommt aus [TrapPaint.count] mit der Grundbreite
 * [TimingGame.zoneHalfWidth] und dem Winkel eines Bahn-Blocks als Zelle;
 * sie rundet ab und bleibt unter PULS stehen. Die Minen liegen gleichmäßig
 * über die Breite von [TimingGame.fakeZoneHalf] verteilt (je eine in der
 * Mitte von n gleich breiten Feldern), die Kette atmet also mit, die Zahl
 * nicht. Minen, die in der echten Zone ([zoneHalf]) lägen, entfallen.
 * Kein Lauflicht, keine Zufallszahl.
 */
internal fun wearTrapMineAngles(game: TimingGame, segments: Int, zoneHalf: Float): List<Float> {
    if (!game.hasFakeZone) return emptyList()
    val cell = 2f * PI.toFloat() / segments
    val count = TrapPaint.count(game.zoneHalfWidth, cell)
    val fakeHalf = game.fakeZoneHalf()
    val pitch = 2f * fakeHalf / count
    val angles = ArrayList<Float>(count)
    for (i in 0 until count) {
        val angle = TimingGame.wrapTwoPi(game.fakeZoneCenter - fakeHalf + (i + 0.5f) * pitch)
        if (abs(TimingGame.wrapToPi(angle - game.zoneCenter)) <= zoneHalf) continue
        angles.add(angle)
    }
    return angles
}

/**
 * Kleinster Abstand zweier benachbarter Minen auf dem Bild (Sehne, in
 * Bildpunkten), den die Falle in dieser Runde annehmen kann. Unter PULS
 * zählt das Wellental, damit die Minen beim Atmen nicht die Größe wechseln.
 */
internal fun wearMineDistance(game: TimingGame, segments: Int, radius: Float): Float {
    val count = TrapPaint.count(game.zoneHalfWidth, 2f * PI.toFloat() / segments)
    val narrowest = if (Twist.PULSE in game.activeTwists) {
        min(game.fakeZoneHalf(), game.zoneHalfWidth * TimingGame.PULSE_MIN_SHARE)
    } else {
        game.fakeZoneHalf()
    }
    val pitch = 2f * narrowest / count
    return 2f * radius * sin(pitch / 2f)
}

/** Breite des hellen Rands in Bildpunkten bei Sprite-Pixeln der Kante [px]. */
internal fun wearMineRim(px: Int): Int = (px * 0.6f).roundToInt().coerceAtLeast(1)

/** Halbe Kantenlänge einer Mine samt Rand, von der Mitte gemessen. */
internal fun wearMineHalfExtent(px: Int): Float = TrapPaint.MINE_SIZE * px / 2f + wearMineRim(px)

/**
 * Berühren sich zwei Kugeln mit Sprite-Pixeln [px] im Abstand [distance]
 * nicht? Zwischen ihnen bleibt mindestens ein Bildpunkt; die hellen Ränder
 * dürfen sich teilen, sie liegen unter den Kugeln. Das Sprite ist ein
 * 5×5-Kern mit vier Zacken: schräg begrenzt der Kern (`√2 · 5 px`), längs
 * der Achsen die Zacken (`7 px`).
 */
internal fun wearMinesFit(px: Int, distance: Float): Boolean {
    val diagonal = sqrt(2f) * 5f * px
    val axis = (TrapPaint.MINE_SIZE * px).toFloat()
    return distance >= max(diagonal, axis) + 1f
}

/**
 * Sprite-Pixelmaß der Minen: höchstens so groß, wie ein Zonenblock
 * ([zoneOuter]) es hergibt, und nie so groß, dass sich benachbarte Kugeln
 * im Abstand [distance] berühren. Mindestens 1.
 */
internal fun wearMinePixel(distance: Float, zoneOuter: Float): Int {
    var px = (zoneOuter / TrapPaint.MINE_SIZE).roundToInt().coerceAtLeast(1)
    while (px > 1 && !wearMinesFit(px, distance)) px--
    return px
}

/**
 * Überdeckt ein Bahn-Block mit Mitte ([bx], [by]) und halber Kante
 * [blockHalf] eine der Minen? Ein Bildpunkt Luft zählt mit.
 */
internal fun wearBlockHitsMine(
    bx: Float,
    by: Float,
    blockHalf: Float,
    mineCenters: List<Offset>,
    mineHalf: Float
): Boolean {
    val reach = blockHalf + mineHalf + 1f
    return mineCenters.any { abs(it.x - bx) < reach && abs(it.y - by) < reach }
}

/**
 * Eine Mine aus [TrapPaint.MINE], mittig auf ([cx], [cy]). [rimOnly]: nur
 * der helle Rand um jeden gesetzten Pixel, sonst Kugel und Glanz. Die
 * Kugel ist immer schwarz — die Uhr hat kein Lauflicht.
 */
private fun DrawScope.drawWearMine(cx: Float, cy: Float, px: Int, rimOnly: Boolean) {
    val u = px.toFloat()
    val n = TrapPaint.MINE_SIZE
    val ox = (cx - u * n / 2f).roundToInt().toFloat()
    val oy = (cy - u * n / 2f).roundToInt().toFloat()
    val rim = wearMineRim(px).toFloat()
    val rimColor = Color(TrapPaint.RIM)
    val ball = Color(TrapPaint.BALL)
    val gloss = Color(TrapPaint.GLOSS)
    for (r in 0 until n) {
        val row = TrapPaint.MINE[r]
        for (k in 0 until n) {
            val ch = row[k]
            if (ch == '.') continue
            if (rimOnly) {
                drawRect(
                    color = rimColor,
                    topLeft = Offset(ox + k * u - rim, oy + r * u - rim),
                    size = Size(u + 2f * rim, u + 2f * rim)
                )
            } else {
                drawRect(
                    color = if (ch == 'W') gloss else ball,
                    topLeft = Offset(ox + k * u, oy + r * u),
                    size = Size(u, u)
                )
            }
        }
    }
}

// ===== Nebelband (Twist NEBEL, Plan 3.3 und 8.6 Punkt 15) =====

/**
 * Winkel, an denen das Nebelband Blöcke setzt, vom Anfang der Nebelbank
 * bis zu ihrem Ende, im Abstand von höchstens [step] (Radiant). Leer, wenn
 * gerade kein Nebel liegt: nur in RUNNING (beim Tod verschwindet das
 * Band) und nur unter NEBEL.
 *
 * Die Grenzen kommen allein aus der Engine ([TimingGame.fogStart],
 * [TimingGame.fogEnd], relativ zur Zone in Laufrichtung) — dieselben, an
 * denen [TimingGame.isDotVisible] den Vogel verbirgt.
 */
internal fun wearFogAngles(game: TimingGame, step: Float): List<Float> {
    if (game.phase != GamePhase.RUNNING || Twist.GHOST !in game.activeTwists) return emptyList()
    if (!(step > 0f)) return emptyList()
    val from = game.fogStart()
    val to = game.fogEnd()
    if (to < from) return emptyList()
    val n = ceil((to - from) / step).toInt().coerceAtLeast(1)
    return List(n + 1) { i ->
        val rel = from + (to - from) * i / n
        TimingGame.wrapTwoPi(game.zoneCenter + game.direction * rel)
    }
}

/**
 * Das Nebelband: eine Reihe heller Pixel-Blöcke über der Bahn, etwas
 * dicker als der Vogel, mit bläulichem Rand. Keine Wolkenform, kein
 * Einrollen — auf dem kleinen Display zählt nur, dass man sieht, wo der
 * Vogel verschwindet. [dotRadius] ist der Vogelradius in Bildpunkten.
 */
private fun DrawScope.drawFogBand(
    game: TimingGame,
    cx: Float,
    cy: Float,
    radius: Float,
    dotRadius: Float
) {
    // Ein Band-Pixel = ein Vogel-Pixel, wie die Wolke am Telefon.
    val u = (dotRadius * 2f) / WEAR_GRID
    val edge = round(u).coerceAtLeast(1f)
    val outer = round(u * (WEAR_GRID + 2f))
    val mid = outer - 2f * edge
    val core = mid - 4f * edge
    // Blöcke im Abstand eines halben Blocks: Das Band hat keine Lücken.
    val angles = wearFogAngles(game, step = (outer / 2f) / radius)
    if (angles.isEmpty()) return
    val layers = listOf(outer to WearFogEdgeColor, mid to WearFogMidColor, core to WearFogCoreColor)
    for ((extent, color) in layers) {
        if (extent <= 0f) continue
        for (a in angles) {
            val px = round(cx + cos(a) * radius)
            val py = round(cy + sin(a) * radius)
            drawRect(
                color = color,
                topLeft = Offset(px - extent / 2f, py - extent / 2f),
                size = Size(extent, extent)
            )
        }
    }
}

/**
 * Vogel als Pixel-Kreis mit Auge/Glanzpunkt in Flugrichtung — Körper-,
 * Schatten- und Glanzfarbe kommen aus dem gewählten Skin, wie
 * drawTimingDot am Phone.
 */
private fun DrawScope.drawWearDot(
    game: TimingGame,
    cx: Float,
    cy: Float,
    radius: Float,
    minDimension: Float,
    skin: WearDotSkin,
    hour: Int,
    month: Int,
    wobble: Float
) {
    val r = minDimension * WEAR_DOT_RADIUS_SHARE
    val px = cx + cos(game.angle) * radius + wobble * (r * 2f) / WEAR_GRID
    var py = cy + sin(game.angle) * radius

    // Mario-Tod wie am Phone (drawTimingDot in TimingGameScreen.kt):
    // Während des Todes-Freeze bleibt der Vogel stehen, dann hüpft er nach
    // oben, dreht sich dabei auf den Rücken und fällt kopfüber mit
    // Gravitation unten aus dem Bild. Ein eigener Zeitgeber ist unnötig —
    // game.elapsed zählt in DYING ab dem Todesmoment.
    var flip = 0f
    if (game.phase == GamePhase.DYING) {
        val t = game.elapsed - TimingGame.DEATH_FREEZE_SECONDS
        if (t > 0f) {
            val h = size.height
            py += (-WEAR_DEATH_HOP_SPEED * t + 0.5f * WEAR_DEATH_GRAVITY * t * t) * h
            if (py - r * 2f > h) return
            flip = 180f * (t / WEAR_DEATH_FLIP_SECONDS).coerceAtMost(1f)
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
        drawWearPixelCircle(
            outline = WearOutlineColor,
            centerX = centerX,
            centerY = centerY,
            radius = r,
            alpha = alpha
        ) { col, row -> skin.cell(col, row, state) }

        val u = (r * 2f) / WEAR_GRID
        fun rect(col: Float, row: Float, cols: Float, rows: Float, color: Color) {
            drawRect(
                color = color,
                topLeft = Offset(centerX - r + col * u, centerY - r + row * u),
                size = Size(cols * u, rows * u),
                alpha = alpha
            )
        }

        // Auge/Glanzpunkt folgen der sichtbaren Flugrichtung wie am Phone
        // (drawTimingDot in TimingGameScreen.kt): horizontale Geschwindigkeit
        // ist ~ -sin(angle) * direction — zeigt sie nach links, wird gespiegelt.
        // Das Auge bekommt zum Körper hin eine Kontur, sonst geht es auf
        // hellen Skins (Koi, Chrom) im Körper unter.
        val facingLeft = sin(game.angle) * game.direction > 0f
        val shine = skin.shineColor(state)
        val eyeOutline = skin.needsEyeOutline
        if (facingLeft) {
            rect(WEAR_GRID - 4.5f, 2.5f, 2f, 2f, shine)
            if (eyeOutline) {
                rect(5.5f, 3f, 0.5f, 4f, WearOutlineColor)
                rect(2f, 2.5f, 3.5f, 0.5f, WearOutlineColor)
                rect(2f, 7f, 3.5f, 0.5f, WearOutlineColor)
            }
            rect(2f, 3f, 3.5f, 4f, Color.White)
            rect(2f, 4f, 1.5f, 2f, WearOutlineColor)
        } else {
            rect(2.5f, 2.5f, 2f, 2f, shine)
            if (eyeOutline) {
                rect(7f, 3f, 0.5f, 4f, WearOutlineColor)
                rect(7.5f, 2.5f, 3.5f, 0.5f, WearOutlineColor)
                rect(7.5f, 7f, 3.5f, 0.5f, WearOutlineColor)
            }
            rect(7.5f, 3f, 3.5f, 4f, Color.White)
            rect(9.5f, 4f, 1.5f, 2f, WearOutlineColor)
        }
    }

    // Schweif-Skins (Tinte) lassen Nachbilder auf der Bahn zurück; die
    // Positionen werden wie am Phone aus dem Winkel zurückgerechnet.
    if (skin.hasTrail && game.phase == GamePhase.RUNNING) {
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

/**
 * Kleine Pixel-Münze in den Farben der Medaillen-Stufe. Die 72dp-Medaille
 * des Phones (MedalBadge in GameOverlays.kt) samt Band wäre auf der Uhr
 * zu groß — hier reicht die Münze mit Glanzpunkt als kompaktes Symbol.
 */
internal fun DrawScope.drawWearMedalCoin(tier: WearMedalTier) {
    val r = size.minDimension / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawWearPixelCircle(
        outline = WearOutlineColor,
        centerX = cx,
        centerY = cy,
        radius = r
    ) { col, row -> if (col + row > WEAR_GRID * 1.15f) tier.shade else tier.body }
    // Glanzpunkt oben links, wie auf der Phone-Münze.
    val u = (r * 2f) / WEAR_GRID
    drawRect(
        color = WearDotShine,
        topLeft = Offset(cx - r + 2.5f * u, cy - r + 2.5f * u),
        size = Size(2f * u, 2f * u)
    )
}

/**
 * Kleine Skin-Vorschau-Münze: im READY-Overlay der Knopf zum Skin-Wähler,
 * in dessen Liste das Symbol jeder Zeile. Pixel-Kreis in den Farben des
 * Skins mit Glanzpunkt — dieselbe Zeichnung wie die Medaillen-Münze.
 *
 * Bewusst als Standbild (elapsed = 0): Eine Liste mit lauter animierten
 * Münzen würde die Uhr pro Frame durch dutzende Raster jagen. Stunde und
 * Monat kommen trotzdem mit, sonst zeigten TAGESZEIT und JAHRESZEIT in
 * der Vorschau ein anderes Kleid als im Lauf.
 */
internal fun DrawScope.drawWearSkinCoin(skin: WearDotSkin, hour: Int, month: Int) {
    val r = size.minDimension / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val state = SkinState(hour = hour, month = month)
    drawWearPixelCircle(
        outline = WearOutlineColor,
        centerX = cx,
        centerY = cy,
        radius = r
    ) { col, row -> skin.cell(col, row, state) }
    val u = (r * 2f) / WEAR_GRID
    drawRect(
        color = skin.shineColor(state),
        topLeft = Offset(cx - r + 2.5f * u, cy - r + 2.5f * u),
        size = Size(2f * u, 2f * u)
    )
}

/**
 * Lokale Kopie von drawPixelCircle (GameOverlays.kt) — kein :app-Zugriff.
 * Die Füllfarbe kommt pro Feld aus [cell], damit gemusterte und bewegte
 * Skins auf der Uhr genauso aussehen wie am Phone.
 */
private fun DrawScope.drawWearPixelCircle(
    outline: Color,
    centerX: Float,
    centerY: Float,
    radius: Float,
    alpha: Float = 1f,
    cell: (col: Int, row: Int) -> Color
) {
    val n = WEAR_GRID.toInt()
    val u = (radius * 2f) / WEAR_GRID
    val mid = (WEAR_GRID - 1f) / 2f
    val rr = WEAR_GRID / 2f - 0.25f

    for (row in 0 until n) {
        for (col in 0 until n) {
            val dx = col - mid
            val dy = row - mid
            val dist = sqrt(dx * dx + dy * dy)
            if (dist <= rr) {
                val cellColor = if (dist > rr - 1.1f) outline else cell(col, row)
                drawRect(
                    color = cellColor,
                    topLeft = Offset(centerX - radius + col * u, centerY - radius + row * u),
                    size = Size(u + 0.5f, u + 0.5f),
                    alpha = alpha
                )
            }
        }
    }
}
