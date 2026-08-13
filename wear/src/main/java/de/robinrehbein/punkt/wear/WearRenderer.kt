package de.robinrehbein.punkt.wear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SkinState
import de.robinrehbein.punkt.game.TimingGame
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

// ===== Palette 1:1 aus app/.../ui/screens/GameOverlays.kt und
// TimingGameScreen.kt übernommen, damit der Wear-Prototyp optisch zur
// Telefon-Version passt, ohne eine Abhängigkeit auf :app zu brauchen. =====

/** Himmelsfarbe pro 5er-Stufe (aus TimingGameScreen.kt, dort privat). */
internal val WearSkyStages = listOf(
    Color(0xFF4EC0CA), // 0+  Tag (türkis)
    Color(0xFF5B9BD5), // 5+  Blau
    Color(0xFF7B6FD0), // 10+ Lila
    Color(0xFFC0616F), // 15+ Altrosa
    Color(0xFFD98A3D), // 20+ Sonnenuntergang
    Color(0xFF3D4A8C), // 25+ Dämmerung
    Color(0xFF2A2640)  // 30+ Nacht
)

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

/** Fallen-Zone (aus TimingGameScreen.kt, dort privat). */
internal val WearFakeZoneColor = Color(0xFFB44FD8)
internal val WearFakeZoneCoreColor = Color(0xFF8A2FB0)

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

/** Segmentzahl der Bahn — dieselbe wie am Phone (drawTrack). */
private const val WEAR_TRACK_SEGMENTS = 60

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
 */
internal fun DrawScope.drawWearWorld(game: TimingGame, skin: WearDotSkin) {
    val d = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f

    val sky = WearSkyStages[SkinPaint.skyStage(game.score)]
    drawRect(color = sky, topLeft = Offset.Zero, size = size)

    // Radius proportional zu minDimension statt zur Bildhöhe wie am Phone —
    // auf der Uhr sind Breite und Höhe (fast) identisch, aber minDimension
    // ist robust gegenüber eckigen/ovalen Displays.
    val radius = d * 0.38f
    drawWearTrack(game, cx, cy, radius)
    // In OVER ist der Vogel bereits unten aus dem Bild gefallen
    // (Mario-Hüpfer in der DYING-Phase) — die Bahn bleibt leer, bis der
    // nächste Lauf startet. Am Phone regelt das fx.deathTime genauso.
    if (game.phase != TimingGame.Phase.OVER && game.isDotVisible) {
        drawWearDot(game, cx, cy, radius, d, skin)
    }
}

/**
 * Die Kreisbahn als Kette blockiger Zellen — dieselbe Zeichnung wie
 * drawTrack am Phone, nur dass die Blockgrößen hier aus dem
 * Segment-Abstand folgen (siehe WEAR_SEG_*), damit die Proportionen auf
 * dem kleinen Display erhalten bleiben.
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
    for (k in 0 until segments) {
        val a = k.toFloat() / segments * (2f * Math.PI.toFloat())
        val px = cx + cos(a) * radius
        val py = cy + sin(a) * radius

        val relativeZone = TimingGame.wrapToPi(a - game.zoneCenter)
        val inZone = abs(relativeZone) <= zoneHalf
        // Kern-Radius nie unter ein Segment schrumpfen lassen, sonst leuchtet
        // bei geschrumpfter/pulsierender Zone zeitweise gar kein Block hell.
        val coreHalf = (zoneHalf * TimingGame.PERFECT_SHARE)
            .coerceAtLeast(Math.PI.toFloat() / segments)
        val inPerfectCore = abs(relativeZone) <= coreHalf

        val inFake = game.hasFakeZone &&
            abs(TimingGame.wrapToPi(a - game.fakeZoneCenter)) <= game.zoneHalfWidth
        val inFakeCore = game.hasFakeZone &&
            abs(TimingGame.wrapToPi(a - game.fakeZoneCenter)) <=
            game.zoneHalfWidth * TimingGame.PERFECT_SHARE

        val highlighted = inZone || inFake
        val outer = if (highlighted) zoneOuter else neutralOuter
        val inner = if (highlighted) zoneInner else neutralInner
        val innerColor = when {
            inPerfectCore -> WearGrassLight
            inZone -> WearGrassDark
            inFakeCore -> WearFakeZoneCoreColor
            inFake -> WearFakeZoneColor
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
    skin: WearDotSkin
) {
    val px = cx + cos(game.angle) * radius
    var py = cy + sin(game.angle) * radius
    val r = minDimension * 0.075f

    // Mario-Tod wie am Phone (drawTimingDot in TimingGameScreen.kt):
    // Während des Todes-Freeze bleibt der Vogel stehen, dann hüpft er nach
    // oben, dreht sich dabei auf den Rücken und fällt kopfüber mit
    // Gravitation unten aus dem Bild. Ein eigener Zeitgeber ist unnötig —
    // game.elapsed zählt in DYING ab dem Todesmoment.
    var flip = 0f
    if (game.phase == TimingGame.Phase.DYING) {
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
        perfectStreak = game.perfectStreak
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
    if (skin.hasTrail && game.phase == TimingGame.Phase.RUNNING) {
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
 * Kleine Skin-Vorschau-Münze fürs READY-Overlay: Pixel-Kreis in den
 * Farben des gewählten Skins mit Glanzpunkt — dieselbe Zeichnung wie die
 * Medaillen-Münze, nur eben in Skin-Farben. Ein Tap darauf schaltet
 * zyklisch zum nächsten freigeschalteten Skin (siehe cycleSkin im
 * WearGameController).
 */
internal fun DrawScope.drawWearSkinCoin(skin: WearDotSkin) {
    val r = size.minDimension / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawWearPixelCircle(
        outline = WearOutlineColor,
        centerX = cx,
        centerY = cy,
        radius = r
    ) { col, row -> skin.cell(col, row) }
    val u = (r * 2f) / WEAR_GRID
    drawRect(
        color = skin.shine,
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
