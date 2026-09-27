package de.robinrehbein.punkt.ui.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.robinrehbein.punkt.game.TrackMark
import de.robinrehbein.punkt.game.TrackMarks
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Die Markierungen der späten Twists auf der Bahn (ab v2.36).
 *
 * Beide Twists ändern etwas, das man sonst im Gefühl hat — das Tempo und
 * die Wende nach dem Treffer. Damit das fair bleibt, steht es VOR dem
 * Treffer auf der Bahn:
 *
 * - **TEMPO**: Die Blöcke im Tempo-Band vor der Zone ([TimingGame.tempoStart]
 *   bis [TimingGame.tempoEnd]) leuchten. Orange mit Pfeil in Laufrichtung
 *   heißt schneller, Eisblau mit Pause-Strichen heißt langsamer.
 * - **SPIEGEL**: Hinter der Zone zeigen [TrackMarks.MIRROR_BLOCKS] helle Blöcke
 *   mit Pfeil weiter in Laufrichtung — der Punkt kehrt nach dem Treffer
 *   nicht um.
 *
 * Welcher Platz welche Markierung trägt, entscheidet [TrackMarks] in
 * `:core` — die Uhr zeichnet aus derselben Quelle. Gezeichnet wird auf den Sandblöcken, im Raster der Bahn: Die Fläche
 * bekommt die Farbe (mit Bevel wie jeder Block), das Zeichen darauf liegt
 * in ganzen Rasterstufen. Kein Block wird gedreht — die Bahn bleibt
 * achsparallel (siehe docs/TODO.md, „Bahn: Blöcke drehen").
 */
/**
 * Die Laufrichtung am Bahnplatz [angle], auf eine von acht Richtungen
 * gerundet: 0 = rechts, 1 = rechts unten, 2 = unten … 7 = rechts oben
 * (Bildschirm, y nach unten).
 */
internal fun travelOctant(angle: Float, direction: Int): Int {
    // Tangente an den Kreis: Ableitung von (cos a, sin a), mal Richtung.
    val tx = -sin(angle) * direction
    val ty = cos(angle) * direction
    val a = atan2(ty, tx)
    val oct = (a / (PI.toFloat() / 4f)).roundToInt()
    return ((oct % 8) + 8) % 8
}

/**
 * Pfeilspitze in 3×3 Rasterstufen für eine der acht Richtungen. Die
 * geraden Richtungen sind ein Winkel „>“, die schrägen eine Ecke „┘“.
 */
internal fun arrowCells(octant: Int): List<Pair<Int, Int>> {
    val straight = listOf(0 to 0, 1 to 1, 0 to 2) // nach rechts
    val diagonal = listOf(0 to 2, 1 to 2, 2 to 2, 2 to 1, 2 to 0) // nach rechts unten
    val base = if (octant % 2 == 0) straight else diagonal
    // Je eine Vierteldrehung im Uhrzeigersinn: (x, y) -> (2 - y, x).
    var cells = base
    repeat(octant / 2) { cells = cells.map { (x, y) -> (2 - y) to x } }
    return cells
}

/** Pause-Striche „||“ für langsamer, in 3×3 Rasterstufen. */
private val PauseCells = listOf(0 to 0, 0 to 1, 0 to 2, 2 to 0, 2 to 1, 2 to 2)

/**
 * Zeichnet die Markierung auf einen Sandblock [b] (siehe [sandBlock]):
 * Fläche in der Farbe der Markierung, darauf das Zeichen in Umrissfarbe.
 */
internal fun DrawScope.drawTrackMark(b: TrackBlock, mark: TrackMark, octant: Int) {
    val face = Color(TrackMarks.face(mark))
    val e = b.unit
    bevelRect(face, Offset(b.faceLeft, b.faceTop), Size(b.inner, b.inner), e)
    val cells = if (mark == TrackMark.SLOW) PauseCells else arrowCells(octant)
    // Das Zeichen ist drei Stufen breit, die Fläche meist vier: Es rückt
    // eine Stufe in die Richtung, in die es zeigt, damit die Spitze vorn
    // liegt und nichts zwischen zwei Rasterstufen fällt.
    val n = (b.inner / e).roundToInt()
    val spare = (n - 3).coerceAtLeast(0)
    val dx = if (spare > 0 && octant in listOf(7, 0, 1)) spare else 0
    val dy = if (spare > 0 && octant in listOf(1, 2, 3)) spare else 0
    for ((x, y) in cells) {
        drawRect(
            color = OutlineColor,
            topLeft = Offset(b.faceLeft + (x + dx) * e, b.faceTop + (y + dy) * e),
            size = Size(e, e)
        )
    }
}
