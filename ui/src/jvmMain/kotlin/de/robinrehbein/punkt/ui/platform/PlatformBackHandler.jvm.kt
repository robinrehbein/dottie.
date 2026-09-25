package de.robinrehbein.punkt.ui.platform

import androidx.compose.runtime.Composable

/** Die JVM zeichnet nur Screenshots und Tests, eine Zurück-Geste gibt es dort nicht. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
}
