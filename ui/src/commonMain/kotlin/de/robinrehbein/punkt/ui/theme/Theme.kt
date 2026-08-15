package de.robinrehbein.punkt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

/**
 * Das Material-Thema als Rahmen. Es traegt wenig: Das Spiel zeichnet
 * seine Farben selbst (siehe ui/world/Palette.kt), Material liefert nur
 * die Grundlage fuer Text und Wellen-Effekte.
 *
 * Die dynamischen Farben von Android 12+ sind mit dem Umzug in das
 * geteilte Modul entfallen. Sie waren ohnehin nie sichtbar — jede Flaeche
 * des Spiels hat ihre eigene Farbe — und sie sind Android-only.
 */
@Composable
fun PunktTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}