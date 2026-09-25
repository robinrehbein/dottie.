package de.robinrehbein.punkt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.game.Twist
import de.robinrehbein.punkt.ui.components.MineIcon
import de.robinrehbein.punkt.ui.text.twistLesson
import de.robinrehbein.punkt.ui.theme.Bytesized
import de.robinrehbein.punkt.ui.world.OutlineColor

// Die einmalige Twist-Erklärung im Game-Over. Bis v2.27 Teil von
// GameOverlays.kt (aufgeteilt nach Plan 8.4).

/**
 * Die einmalige Twist-Erklärung: dunkles Pflaumen-Schild, gelber Text.
 * Dieselben zwei Farben, aus denen im Game-Over ohnehin alles gebaut ist
 * — der Pixelrahmen der Punkte-Tafel und die Feier-Zeilen darunter.
 *
 * Die Mine ([MineIcon]) gibt es nur bei der FALLE, und sie steht VOR der
 * Zeile statt mitten im Satz: Genauso zeigt die Hilfe ihre Twists (siehe
 * [TwistHelpRow]), und die Stelle hängt an keiner Wortstellung, die sich
 * zwischen Deutsch und Englisch verschiebt. Die Warnung hängt damit am
 * Bild der Bombe, nicht an einer Farbe — und wer die Minen im nächsten
 * Lauf sieht, erkennt sie wieder.
 */
@Composable
internal fun TwistLessonShield(twist: Twist) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .background(OutlineColor)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        if (twist == Twist.FAKE) {
            MineIcon(size = 16.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = twistLesson(twist),
            fontFamily = Bytesized,
            fontSize = 14.sp,
            color = Color(0xFFFFE95E),
            textAlign = TextAlign.Center
        )
    }
}
