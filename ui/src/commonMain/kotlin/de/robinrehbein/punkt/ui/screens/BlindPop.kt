package de.robinrehbein.punkt.ui.screens

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.ui.resources.Res
import de.robinrehbein.punkt.ui.resources.fog_blind_plus
import org.jetbrains.compose.resources.stringResource

/**
 * BLIND! +2 nach einem Treffer, solange der Punkt noch im Nebel steckte
 * (Plan 3.3, BLIND! +1). Steht dort, wo sonst PERFEKT steht — beides
 * zugleich gibt es nicht, der PERFEKT-Kern liegt immer hinter dem Nebel.
 * Die Farbe ist das Weiß der Wolkenoberkante mit einem Hauch Blau, damit
 * der Pop zum Nebel gehört und nicht mit dem gelben PERFEKT verwechselt
 * wird. Wann er steht, sagt `isBlindPopShown`.
 *
 * Füllt die Fläche und setzt den Text bei 74 % der Höhe, wie PERFEKT in
 * GameScreen.
 */
@Composable
fun BlindPop(points: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(Res.string.fog_blind_plus, points),
            style = ScoreShadowStyle,
            fontSize = 28.sp,
            color = BlindPopColor,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = maxHeight * 0.74f)
        )
    }
}

/** Wolkenweiß mit Blaustich, zwischen FogTop und FogMid. */
private val BlindPopColor = Color(0xFFE8F2FF)
