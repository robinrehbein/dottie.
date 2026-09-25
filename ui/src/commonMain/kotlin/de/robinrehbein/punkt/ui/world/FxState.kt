package de.robinrehbein.punkt.ui.world

/**
 * Nicht-Compose-State fuer Effekte, wird pro Frame im Canvas gelesen.
 *
 * Bewusst eine schlichte Klasse mit `var`s statt Compose-State: Diese
 * Werte aendern sich in jedem Frame, und jede Aenderung wuerde sonst eine
 * Neuzusammensetzung ausloesen — fuer etwas, das ohnehin nur der Canvas
 * liest.
 */
class FxState {
    var flashAlpha = 0f
    // == AP-11 todesursache ==
    // == /AP-11 ==
    var shakeTime = 0f
    // == AP-12 bomben ==
    // == /AP-12 ==

    /** Restzeit der Freischalt-Zelebration (goldener Ring + Schimmer). */
    var celebrateTime = 0f
    // == AP-22 start ==
    // == /AP-22 ==

    /** Sekunden seit dem Tod (Mario-Huepfer), negativ = kein Tod aktiv. */
    var deathTime = -1f
    // == AP-23 nebel ==

    /**
     * Wölkchen beim Ein- und Austritt in den Nebel (siehe trackFog):
     * Sekunden seit dem Wechsel (negativ = keins) und Bahnwinkel des Punkts.
     */
    var fogInTime = -1f
    var fogInAngle = 0f
    var fogOutTime = -1f
    var fogOutAngle = 0f

    /** War der Punkt im letzten Frame im Nebel? 1 = ja, 0 = nein, -1 = unbekannt. */
    var fogWasIn = -1
    // == /AP-23 ==

    /**
     * Alle Effekte auf den Ruhezustand — noetig ueberall dort, wo ein Lauf
     * endet, ohne dass gleich der naechste startet (Rueckkehr ins Menue).
     * Vor allem [deathTime]: Bliebe der Sturz aktiv, waere der Vogel im
     * READY-Bild laengst unten aus dem Kader gefallen und unsichtbar.
     */
    fun reset() {
        flashAlpha = 0f
        // == AP-11 todesursache ==
        // == /AP-11 ==
        shakeTime = 0f
        // == AP-12 bomben ==
        // == /AP-12 ==
        celebrateTime = 0f
        // == AP-22 start ==
        // == /AP-22 ==
        deathTime = -1f
        // == AP-23 nebel ==
        fogInTime = -1f
        fogOutTime = -1f
        fogWasIn = -1
        // == /AP-23 ==
    }
}
