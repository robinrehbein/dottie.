package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.floor

/**
 * Insel im MEER: flacher Sandhügel in drei Stufen, darauf zwei Palmen mit
 * gebogenem Stamm, sechs Wedeln und zwei Kokosnüssen. Die Wedel wiegen im
 * Wind ([sway]), der Hügel nicht.
 *
 * Farben wie bei [de.robinrehbein.punkt.game.PropShape.INSEL]: [frondDark]
 * und [frond] die Wedel, [sand] und [sandShade] der Hügel, [trunk] und
 * [trunkShade] die Stämme.
 */
internal fun DrawScope.drawPixelIsland(
    cx: Float,
    groundY: Float,
    s: Float,
    sway: Float,
    cell: Float,
    frondDark: Color,
    frond: Color,
    sand: Color,
    trunk: Color,
    trunkShade: Color,
    sandShade: Color
) {
    val u = snapUp(s * 0.16f, cell)
    // Der Hügel steht auf der Wasserlinie, nicht auf dem Grund: Der
    // Bodenstreifen (Wasser) wird danach gezeichnet und würde ihn sonst
    // fast ganz verdecken.
    val water = groundY - cell * 2f

    // Sandhügel: drei Stufen, rechts im Schatten.
    val mound = listOf(
        s * 2.8f to s * 0.22f,
        s * 2.0f to s * 0.18f,
        s * 1.2f to s * 0.14f
    )
    val blocks = mutableListOf<Pair<Rect, Color>>()
    var top = water
    mound.forEach { (mw, mh) ->
        val hh = snapUp(mh, cell)
        top -= hh
        val half = snapUp(mw / 2f, cell)
        blocks += Rect(snap(cx, cell) - half, top, half, hh) to sand
        blocks += Rect(snap(cx, cell), top, half, hh) to sandShade
    }

    // Palmen hinter dem obersten Sand, ihre Füße stecken im Hügel.
    val crest = top + snapUp(s * 0.08f, cell)
    drawPalm(cx - s * 0.3f, crest, s * 1.7f, -1f, sway, u, cell, frondDark, frond, trunk, trunkShade)
    drawPalm(cx + s * 0.35f, crest + s * 0.1f, s * 1.25f, 1f, sway * 0.8f, u, cell, frondDark, frond, trunk, trunkShade)
    drawOutlinedBlocks(cell, blocks)
}

/**
 * Palme: Stamm aus fünf Segmenten, die sich zur Seite [lean] neigen,
 * oben sechs Wedel aus Blöcken, die nach außen hängen.
 */
private fun DrawScope.drawPalm(
    footX: Float,
    footY: Float,
    height: Float,
    lean: Float,
    sway: Float,
    u: Float,
    cell: Float,
    frondDark: Color,
    frond: Color,
    trunk: Color,
    trunkShade: Color
) {
    val segments = 5
    val segH = snapUp(height / segments, cell)
    val trunkW = snapUp(u * 0.9f, cell)
    val blocks = mutableListOf<Pair<Rect, Color>>()
    var x = footX
    var y = footY
    for (i in 0 until segments) {
        // Der Stamm biegt sich: oben stärker als unten, und der Wind
        // nimmt die Spitze mit.
        x += lean * cell * (0.2f + i * 0.3f) + sway * 0.1f * i
        y -= segH
        val sx = snap(x, cell)
        blocks += Rect(sx - trunkW / 2f, y, trunkW / 2f, segH) to trunk
        blocks += Rect(sx, y, trunkW / 2f, segH) to trunkShade
    }
    val topX = snap(x + sway * 0.6f, cell)
    val topY = y

    // Wedel: je ein Pfad aus Blöcken (dx, dy in Einheiten u), außen
    // dunkler. Links und rechts hängen sie, oben stehen zwei ab.
    val fronds = listOf(
        // Lange Wedel links und rechts, die nach außen abknicken.
        listOf(-1f to -0.2f, -2f to -0.2f, -3f to 0.3f, -3.8f to 1.1f, -4.3f to 2f),
        listOf(1f to -0.2f, 2f to -0.2f, 3f to 0.3f, 3.8f to 1.1f, 4.3f to 2f),
        // Kürzere, die nach oben abstehen.
        listOf(-0.8f to -0.9f, -1.7f to -1.5f, -2.6f to -1.5f),
        listOf(0.8f to -0.9f, 1.7f to -1.5f, 2.6f to -1.5f),
        // Und zwei, die steil hängen.
        listOf(-0.8f to 0.7f, -1.4f to 1.6f, -1.8f to 2.5f),
        listOf(0.8f to 0.7f, 1.4f to 1.6f, 1.8f to 2.5f)
    )
    fronds.forEach { path ->
        path.forEachIndexed { k, (dx, dy) ->
            val color = if (k >= path.size - 2) frondDark else frond
            blocks += Rect(
                snap(topX + dx * u - u / 2f, cell),
                snap(topY + dy * u - u * 0.35f, cell),
                u,
                snapUp(u * 0.5f, cell)
            ) to color
        }
    }
    // Krone in der Mitte, zwei Kokosnüsse darunter.
    blocks += Rect(topX - u * 0.75f, topY - u * 0.5f, u * 1.5f, snapUp(u * 0.7f, cell)) to frond
    blocks += Rect(topX - u * 0.6f, topY + u * 0.5f, u * 0.6f, u * 0.6f) to trunkShade
    blocks += Rect(topX + u * 0.1f, topY + u * 0.5f, u * 0.6f, u * 0.6f) to trunkShade
    drawOutlinedBlocks(cell, blocks)
}

private fun snap(v: Float, cell: Float): Float = floor(v / cell) * cell

private fun snapUp(v: Float, cell: Float): Float = (floor(v / cell) * cell).coerceAtLeast(cell)
