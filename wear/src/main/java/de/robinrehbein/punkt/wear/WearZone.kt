package de.robinrehbein.punkt.wear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.game.ZoneMotif
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Die grüne Zone der Uhr, gezeichnet wie am Telefon (drawZoneBlock und
// zoneSlots in ui/.../world/WorldRenderer.kt): Blöcke im Raster der Bahn,
// zur Mitte hin größer, goldener Saum um den Perfekt-Kern, Licht-Kante
// in den Grastönen, Blätter und Tupfer in der WIESE, das Motiv der Welt
// in den anderen. :wear hängt nicht von :ui ab, deshalb steht hier eine
// eigene Fassung mit denselben Regeln und Farben.

// Grastöne der Zone, wie in ui/.../world/Palette.kt.
internal val WearGrassShine = Color(0xFFA8E070)
internal val WearGrassShineCore = Color(0xFFD8FFB0)
internal val WearGrassEdge = Color(0xFF579A1F)
internal val WearGrassEdgeCore = Color(0xFF6FC23A)
internal val WearGrassLeafCore = Color(0xFFC8F58F)
internal val WearGrassDeep = Color(0xFF5AA82C)

/** Goldener Saum um den Perfekt-Kern (ZoneCoreHalo am Telefon). */
internal val WearZoneCoreHalo = Color(0xFFFFF6B0)

/**
 * Rasterstufe der Zone bei einem Segmentabstand von [spacing] Pixeln:
 * ein Neuntel, ganzzahlig und mindestens 1 px. Am Telefon ist die volle
 * Zone zehn Stufen breit bei rund neun Stufen Segmentabstand — dasselbe
 * Verhältnis hält die Uhr (454 px: 18 px Abstand, 2 px Stufe).
 */
internal fun wearZoneUnit(spacing: Float): Int = (spacing / 9f).roundToInt().coerceAtLeast(1)

/**
 * Wie groß ein Zonenblock im Abstand [d] von der Zonenmitte ist (0 =
 * Mitte, 1 = einen halben Block hinter dem Rand), als Anteil der vollen
 * Größe — dieselbe Kurve wie zoneBlockScale am Telefon: in der Mitte 1,
 * am Rand 0,6, dazwischen ein Viertel Kosinus.
 */
internal fun wearZoneBlockScale(d: Float): Float =
    0.6f + 0.4f * cos(d.coerceIn(0f, 1f) * PI.toFloat() / 2f)

/**
 * Ein Block der Zone im Bild, fertig gerundet wie TrackBlock am Telefon:
 * linke obere Ecke und Kante sind Vielfache der Rasterstufe [unit], der
 * Umriss ist eine Stufe breit.
 */
internal class WearZoneBlock(val left: Float, val top: Float, val outer: Float, val unit: Float) {
    val inner: Float get() = outer - 2f * unit
    val faceLeft: Float get() = left + unit
    val faceTop: Float get() = top + unit
}

/** Ein Block vor dem Zeichnen: Lage und Maß, Anteil, Kern, gespiegelt. */
internal class WearZoneSlot(val block: WearZoneBlock, val scale: Float, val core: Boolean, val mirrored: Boolean)

/**
 * Zonenblock an der Bahnposition ([x], [y]): [scale] der vollen Größe
 * [fullUnits], auf ganze Stufen gerundet und mindestens vier (Umriss,
 * zwei Stufen Gras, Umriss), die Mitte höchstens eine halbe Stufe neben
 * der Bahnposition.
 */
internal fun wearZoneBlock(x: Float, y: Float, unit: Int, fullUnits: Int, scale: Float): WearZoneBlock {
    val units = (fullUnits * scale).roundToInt().coerceAtLeast(4)
    val outer = units * unit
    val left = ((x - outer / 2f) / unit).roundToInt() * unit
    val top = ((y - outer / 2f) / unit).roundToInt() * unit
    return WearZoneBlock(left.toFloat(), top.toFloat(), outer.toFloat(), unit.toFloat())
}

/**
 * Die Blöcke der Zone in Zeichenreihenfolge, kleine zuerst, damit die
 * größeren oben liegen — wie zoneSlots am Telefon. Kern und Breite kommen
 * aus der Engine: Was leuchtet, ist das Fenster, das der Tap wertet.
 * [coreHalf] reicht der Renderer aus [TimingGame.perfectHalf] durch —
 * dort prüft RendererSourceTest, dass niemand den Kern selbst rechnet.
 * [spacing] ist der Segmentabstand ([wearTrackSpacing]), die volle
 * Blockgröße [zoneOuter] dieselbe wie bisher, nur ins Raster gelegt.
 */
internal fun wearZoneSlots(
    game: TimingGame,
    cx: Float,
    cy: Float,
    radius: Float,
    coreHalf: Float,
    spacing: Float,
    zoneOuter: Float
): List<WearZoneSlot> {
    val segments = WEAR_TRACK_SEGMENTS
    val unit = wearZoneUnit(spacing)
    val fullUnits = (zoneOuter / unit).roundToInt().coerceAtLeast(4)
    val zoneHalf = game.effectiveZoneHalf()
    // Bis einen halben Block über den Rand gemessen, wie am Telefon: Sonst
    // schrumpft ein Block genau auf der Grenze auf Sandgröße.
    val taper = zoneHalf + PI.toFloat() / segments
    val slots = ArrayList<WearZoneSlot>()
    for (k in 0 until segments) {
        val a = k.toFloat() / segments * (2f * PI.toFloat())
        val rel = abs(TimingGame.wrapToPi(a - game.zoneCenter))
        if (rel > zoneHalf) continue
        val scale = wearZoneBlockScale(rel / taper)
        slots += WearZoneSlot(
            block = wearZoneBlock(cx + cos(a) * radius, cy + sin(a) * radius, unit, fullUnits, scale),
            scale = scale,
            core = rel <= coreHalf,
            mirrored = k % 2 == 1
        )
    }
    slots.sortBy { it.scale }
    return slots
}

/**
 * Die ganze Zone: erst der goldene Saum um die Kernblöcke (die Nachbarn
 * überdecken ihn, sichtbar bleibt er außen um den Kern), dann alle
 * Blöcke.
 */
internal fun DrawScope.drawWearZone(slots: List<WearZoneSlot>, motif: ZoneMotif) {
    for (z in slots) {
        if (!z.core) continue
        val b = z.block
        drawRect(
            WearZoneCoreHalo,
            Offset(b.left - b.unit, b.top - b.unit),
            Size(b.outer + 2f * b.unit, b.outer + 2f * b.unit)
        )
    }
    for (z in slots) drawWearZoneBlock(z.block, z.core, z.mirrored, motif)
}

/**
 * Ein Block der Zone wie drawZoneBlock am Telefon: Umriss eine Stufe,
 * Grasfläche (im Kern heller), in der WIESE Blätterbüschel und dunkle
 * Tupfer, die Licht-Kante eine Stufe breit, darüber das Motiv der Welt
 * um die Mitte der Grasfläche.
 */
internal fun DrawScope.drawWearZoneBlock(block: WearZoneBlock, core: Boolean, mirrored: Boolean, motif: ZoneMotif) {
    val unit = block.unit
    val inner = block.inner
    val left = block.faceLeft
    val top = block.faceTop
    val n = (inner / unit).roundToInt()
    drawRect(WearOutlineColor, Offset(block.left, block.top), Size(block.outer, block.outer))
    drawRect(if (core) WearGrassLight else WearGrassDark, Offset(left, top), Size(inner, inner))

    fun pixel(col: Int, row: Int, color: Color) {
        if (col !in 0 until n || row !in 0 until n) return
        val c = if (mirrored) n - 1 - col else col
        drawRect(color, Offset(left + c * unit, top + row * unit), Size(unit, unit))
    }
    if (motif == ZoneMotif.WIESE) {
        val f = n / 8f
        val leaf = if (core) WearGrassLeafCore else WearGrassLight
        for ((a, b) in WEAR_ZONE_LEAVES) {
            val col = (a * f).roundToInt()
            val row = (b * f).roundToInt()
            pixel(col, row, leaf)
            pixel(col + 1, row, leaf)
            pixel(col, row - 1, leaf)
        }
        val deep = if (core) WearGrassDark else WearGrassDeep
        for ((a, b) in WEAR_ZONE_SPECKS) pixel((a * f).roundToInt(), (b * f).roundToInt(), deep)
    }
    val shade = if (core) WearGrassEdgeCore else WearGrassEdge
    val shine = if (core) WearGrassShineCore else WearGrassShine
    drawRect(shade, Offset(left, top + inner - unit), Size(inner, unit))
    drawRect(shade, Offset(left + inner - unit, top), Size(unit, inner))
    drawRect(shine, Offset(left, top), Size(inner - unit, unit))
    drawRect(shine, Offset(left, top), Size(unit, inner - unit))
    val mx = (left + inner / 2f - unit / 2f).roundToInt().toFloat()
    val my = (top + inner / 2f - unit / 2f).roundToInt().toFloat()
    val px = ScenePaint.motif(motif, core)
    for (i in px.indices) {
        val p = px[i]
        drawRect(Color(p.color), Offset(mx + p.dx * unit, my + p.dy * unit), Size(unit, unit))
    }
}

/** Lage der Blätterbüschel auf der Grasfläche, in Achteln — wie ZONE_LEAVES am Telefon. */
private val WEAR_ZONE_LEAVES = listOf(1 to 2, 4 to 1, 2 to 4)

/** Lage der dunklen Tupfer, in Achteln — wie ZONE_SPECKS am Telefon. */
private val WEAR_ZONE_SPECKS = listOf(4 to 4, 2 to 6, 5 to 2)
