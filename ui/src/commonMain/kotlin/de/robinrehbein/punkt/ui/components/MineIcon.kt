package de.robinrehbein.punkt.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.robinrehbein.punkt.game.TrapPaint
import de.robinrehbein.punkt.ui.world.drawMine
import kotlin.math.floor
import kotlin.math.min

/**
 * Die Mine der Falle als kleines Symbol für Texte: in der Hilfe vor der
 * Zeile BOMBEN und im Game-Over vor der Erklärung. Dasselbe Sprite wie auf
 * der Bahn ([TrapPaint.MINE], gezeichnet von `drawMine`), damit man die
 * Falle im nächsten Lauf wiedererkennt.
 *
 * Die Pixel sind ganzzahlig: So viele ganze Pixel, wie das Sprite samt
 * hellem Rand in [size] Platz hat.
 *
 * @param red Kugel im Rot des Lauflichts statt schwarz.
 */
@Composable
fun MineIcon(size: Dp = 16.dp, red: Boolean = false, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        // Sprite 7 Pixel plus je 0,6 Pixel Rand links und rechts.
        val fit = TrapPaint.MINE_SIZE + 1.2f
        val px = floor(min(this.size.width, this.size.height) / fit).toInt().coerceAtLeast(1)
        drawMine(this.size.width / 2f, this.size.height / 2f, px, red)
    }
}
