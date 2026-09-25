package de.robinrehbein.punkt.ui.screens

import de.robinrehbein.punkt.game.GamePhase

/**
 * Was die Zurück-Geste gerade tun soll (Plan 7.1).
 *
 * Eine reine Entscheidung ohne Compose, damit sie sich ohne Bildschirm
 * prüfen lässt. Ausgeführt wird sie in GameScreen über den
 * PlatformBackHandler.
 */
enum class BackAction {
    /** Hilfe schließen. */
    CLOSE_HELP,

    /** Einstellungen schließen. */
    CLOSE_SETTINGS,

    /** Statistik schließen. */
    CLOSE_STATS,

    /** Sammlung schließen. */
    CLOSE_COLLECTION,

    /** Die einmalige Daily-Karte schließen. */
    CLOSE_DAILY_INTRO,

    /** Aus dem Game-Over zurück in den Startbildschirm. */
    TO_MENU,

    /** Verbrauchen, ohne etwas zu tun (laufendes Spiel: keine Pause). */
    CONSUME,

    /** Nicht behandeln: Das System entscheidet (Startbildschirm schließt die App). */
    NOT_HANDLED
}

/**
 * Die Entscheidung für die Zurück-Geste aus dem sichtbaren Zustand.
 *
 * Das oberste Overlay schließt zuerst, in dieser Reihenfolge: Hilfe
 * (kann über den Einstellungen liegen) > Einstellungen > Statistik >
 * Sammlung. Ohne Overlay entscheidet die Phase:
 *
 * - OVER: zurück in den Startbildschirm, wie MENÜ. Die 0,8-s-Sperre der
 *   Game-Over-Leiste gilt hier nicht (Plan 8.6 #13): Wer bewusst zurück
 *   wischt, meint es auch so.
 * - RUNNING und DYING: verbrauchen, ohne etwas zu tun. Es gibt keine
 *   Pause, und ein Wisch vom Rand darf keinen Lauf beenden.
 * - READY: nicht behandeln, Android schließt dann die App wie bisher.
 *
 * [showDailyIntro] bleibt bis AP-22 ohne Wirkung (die Karte gibt es erst
 * dort, Plan 8.4).
 */
fun backAction(
    showHelp: Boolean,
    showSettings: Boolean,
    showStats: Boolean,
    showSkins: Boolean,
    showDailyIntro: Boolean,
    phase: GamePhase
): BackAction = when {
    showHelp -> BackAction.CLOSE_HELP
    showSettings -> BackAction.CLOSE_SETTINGS
    showStats -> BackAction.CLOSE_STATS
    showSkins -> BackAction.CLOSE_COLLECTION
    phase == GamePhase.OVER -> BackAction.TO_MENU
    phase == GamePhase.RUNNING || phase == GamePhase.DYING -> BackAction.CONSUME
    else -> BackAction.NOT_HANDLED
}
