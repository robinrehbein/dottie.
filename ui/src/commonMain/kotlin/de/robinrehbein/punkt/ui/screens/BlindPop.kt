package de.robinrehbein.punkt.ui.screens

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.ui.resources.Res
import de.robinrehbein.punkt.ui.resources.fog_blind_plus
import de.robinrehbein.punkt.ui.world.ringGeometry
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.jetbrains.compose.resources.stringResource

/**
 * BLIND! +1 nach einem Treffer, solange der Punkt noch im Nebel steckte
 * (Plan 3.3, 8.6 #1). Der Pop nennt nur den Bonus, nicht den ganzen
 * Treffer: Der Treffer selbst zählt wie immer +1, der Nebel gibt +1 dazu.
 *
 * Er steht am Ring, dort, wo getroffen wurde, ein Stück zur Mitte hin
 * ([blindPopAnchor]): Der Blick ist in diesem Moment beim Vogel, nicht
 * unten bei den Bäumen, wo PERFEKT steht. Beides zugleich gibt es nicht,
 * der PERFEKT-Kern liegt immer hinter dem Nebel.
 *
 * Die Farbe ist das Weiß der Wolkenoberkante mit einem Hauch Blau, damit
 * der Pop zum Nebel gehört und nicht mit dem gelben PERFEKT verwechselt
 * wird. Wann er steht, sagt `isBlindPopShown`.
 *
 * @param bonus die Extrapunkte des Blindtreffers ([blindBonusPoints])
 * @param angle der Winkel des Vogels beim Treffer, wie `TimingGame.angle`
 */
@Composable
fun BlindPop(bonus: Int, angle: Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val anchor = with(LocalDensity.current) {
            blindPopAnchor(Size(maxWidth.toPx(), maxHeight.toPx()), angle)
        }
        Text(
            text = stringResource(Res.string.fog_blind_plus, bonus),
            style = ScoreShadowStyle,
            fontSize = 28.sp,
            color = BlindPopColor,
            // Mittig auf den Ankerpunkt setzen, ohne die Breite zu kennen.
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        (anchor.x - placeable.width / 2f).roundToInt(),
                        (anchor.y - placeable.height / 2f).roundToInt()
                    )
                }
            }
        )
    }
}

/**
 * Die Extrapunkte des letzten Treffers durch den Nebel. Ein Blindtreffer
 * ist nie PERFEKT (der Kern liegt hinter dem Nebel), er zählt also 1 plus
 * Bonus. Die Engine hält den Bonus privat, deshalb die Differenz.
 */
fun blindBonusPoints(game: TimingGame): Int = (game.lastHitPoints - 1).coerceAtLeast(0)

/**
 * Mitte des Pops: auf dem Strahl vom Ringmittelpunkt zum Trefferpunkt,
 * bei [BLIND_POP_RING_SHARE] des Radius. So liegt er im Ring, neben der
 * Stelle des Treffers, und nie auf der Landschaft unter dem Ring.
 */
fun blindPopAnchor(size: Size, angle: Float): Offset {
    val ring = ringGeometry(size)
    val d = ring.radius * BLIND_POP_RING_SHARE
    return Offset(ring.cx + cos(angle) * d, ring.cy + sin(angle) * d)
}

/** Abstand des Pops vom Ringmittelpunkt, relativ zum Radius. */
internal const val BLIND_POP_RING_SHARE = 0.5f

/** Wolkenweiß mit Blaustich, zwischen FogTop und FogMid. */
private val BlindPopColor = Color(0xFFE8F2FF)
