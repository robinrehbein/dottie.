package de.robinrehbein.punkt.ui.platform

import androidx.compose.runtime.Composable

/** iOS hat keine Zurück-Taste, und CMP 1.7.3 keinen gemeinsamen Handler. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
}
