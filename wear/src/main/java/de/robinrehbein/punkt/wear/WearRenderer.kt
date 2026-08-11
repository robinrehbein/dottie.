package de.robinrehbein.punkt.wear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.robinrehbein.punkt.game.TimingGame
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
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

internal val WearDotBody = Color(0xFFFFD847)
internal val WearDotShade = Color(0xFFF5A623)
internal val WearDotShine = Color(0xFFFFF3B8)

/** Fallen-Zone (aus TimingGameScreen.kt, dort privat). */
internal val WearFakeZoneColor = Color(0xFFB44FD8)
internal val WearFakeZoneCoreColor = Color(0xFF8A2FB0)

/** Raster-Auflösung für den Pixel-Vogel, wie GRID in GameOverlays.kt. */
private const val WEAR_GRID = 13f

/**
 * Zeichnet die komplette Spielwelt für das runde Wear-Display: Himmel je
 * nach Score-Stufe, die Bahn als Perlenkette und den Vogel — kein
 * Szenerie-/Boden-Hintergrund wie am Phone, das Display ist dafür zu klein.
 */
internal fun DrawScope.drawWearWorld(game: TimingGame) {
    val d = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f
    // Proportional zu minDimension statt zur Bildhöhe wie am Phone — auf
    // der Uhr sind Breite und Höhe (fast) identisch, aber minDimension ist
    // robust gegenüber eckigen/ovalen Displays.
    val cell = floor(d / 220f).coerceAtLeast(2f)

    val sky = WearSkyStages[(game.score / 5).coerceAtMost(WearSkyStages.size - 1)]
    drawRect(color = sky, topLeft = Offset.Zero, size = size)

    val radius = d * 0.38f
    drawWearTrack(game, cx, cy, radius, cell)
    if (game.isDotVisible) {
        drawWearDot(game, cx, cy, radius, d)
    }
}

/**
 * Die Kreisbahn als Kette blockiger Zellen (Perlenketten-Look wie
 * drawTrack am Phone) — 40 statt 60 Segmente, damit die einzelnen Glieder
 * auf dem kleinen Display noch als Kette statt als durchgehender Ring
 * lesbar bleiben.
 */
private fun DrawScope.drawWearTrack(
    game: TimingGame,
    cx: Float,
    cy: Float,
    radius: Float,
    cell: Float
) {
    val segments = 40
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
        val outer = if (highlighted) cell * 5f else cell * 3f
        val inner = if (highlighted) cell * 3.4f else cell * 1.8f
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

/** Vogel als Pixel-Kreis mit Auge/Glanzpunkt in Flugrichtung. */
private fun DrawScope.drawWearDot(
    game: TimingGame,
    cx: Float,
    cy: Float,
    radius: Float,
    minDimension: Float
) {
    val px = cx + cos(game.angle) * radius
    val py = cy + sin(game.angle) * radius
    val r = minDimension * 0.075f

    drawWearPixelCircle(
        color = WearDotBody,
        outline = WearOutlineColor,
        centerX = px,
        centerY = py,
        radius = r,
        shade = WearDotShade
    )

    val u = (r * 2f) / WEAR_GRID
    fun rect(col: Float, row: Float, cols: Float, rows: Float, color: Color) {
        drawRect(
            color = color,
            topLeft = Offset(px - r + col * u, py - r + row * u),
            size = Size(cols * u, rows * u)
        )
    }

    // Auge/Glanzpunkt folgen der sichtbaren Flugrichtung wie am Phone
    // (drawTimingDot in TimingGameScreen.kt): horizontale Geschwindigkeit
    // ist ~ -sin(angle) * direction — zeigt sie nach links, wird gespiegelt.
    val facingLeft = sin(game.angle) * game.direction > 0f
    if (facingLeft) {
        rect(WEAR_GRID - 4.5f, 2.5f, 2f, 2f, WearDotShine)
        rect(2f, 3f, 3.5f, 4f, Color.White)
        rect(2f, 4f, 1.5f, 2f, WearOutlineColor)
    } else {
        rect(2.5f, 2.5f, 2f, 2f, WearDotShine)
        rect(7.5f, 3f, 3.5f, 4f, Color.White)
        rect(9.5f, 4f, 1.5f, 2f, WearOutlineColor)
    }
}

/** Lokale Kopie von drawPixelCircle (GameOverlays.kt) — kein :app-Zugriff. */
private fun DrawScope.drawWearPixelCircle(
    color: Color,
    outline: Color,
    centerX: Float,
    centerY: Float,
    radius: Float,
    shade: Color = color
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
                val cellColor = when {
                    dist > rr - 1.1f -> outline
                    row + col > WEAR_GRID * 1.15f -> shade
                    else -> color
                }
                drawRect(
                    color = cellColor,
                    topLeft = Offset(centerX - radius + col * u, centerY - radius + row * u),
                    size = Size(u + 0.5f, u + 0.5f)
                )
            }
        }
    }
}
