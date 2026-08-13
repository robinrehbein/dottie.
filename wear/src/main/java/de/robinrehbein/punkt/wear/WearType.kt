package de.robinrehbein.punkt.wear

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Dieselbe Pixel-Schrift wie am Telefon (ui/theme/Type.kt). Die Datei liegt
 * als Kopie in wear/res/font, weil :wear bewusst nicht von :app abhängt —
 * geteilt wird nur die Spiellogik in :core.
 *
 * Bewusst ohne Bold-Schnitt: Bytesized bringt nur einen Strichstärken-Grad
 * mit. Ein FontWeight.Bold ließe die Schrift synthetisch fetten, und
 * Android verschmiert dabei genau die harten Pixelkanten, wegen derer die
 * Schrift überhaupt gewählt wurde. Wo es am Phone kräftiger wirken soll,
 * regelt das die Größe, nicht das Gewicht.
 */
internal val WearBytesized = FontFamily(
    Font(R.font.bytesized, FontWeight.Normal)
)
