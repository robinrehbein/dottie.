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
 * Bis AP-14 nur ein Platzhalter (Plan 8.3): Zurück bleibt unbehandelt,
 * wie bisher.
 */
fun backAction(
    showHelp: Boolean,
    showSettings: Boolean,
    showStats: Boolean,
    showSkins: Boolean,
    showDailyIntro: Boolean,
    phase: GamePhase
): BackAction = BackAction.NOT_HANDLED
