package de.robinrehbein.punkt.ui.platform

import androidx.compose.runtime.Composable

/**
 * Fängt die Zurück-Geste des Systems ab, solange [enabled] gilt.
 *
 * Ohne Handler beendet Android bei Zurück die Activity, egal ob gerade
 * ein Overlay offen ist oder ein Lauf endet. Was Zurück jeweils tut,
 * entscheidet der Aufrufer (siehe `backAction` in `screens/BackAction.kt`),
 * dieser Baustein reicht die Geste nur weiter.
 *
 * - Android: `androidx.activity.compose.BackHandler`.
 * - iOS: ohne Wirkung. Compose Multiplatform 1.7.3 hat noch keinen
 *   gemeinsamen BackHandler, und iOS kennt keine Zurück-Taste.
 * - JVM (nur Screenshots und Tests): ohne Wirkung.
 *
 * Ist [enabled] falsch, geht die Geste an das System (Android schließt
 * dann die App).
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
