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
    var shakeTime = 0f

    /** Restzeit der Freischalt-Zelebration (goldener Ring + Schimmer). */
    var celebrateTime = 0f

    /** Sekunden seit dem Tod (Mario-Huepfer), negativ = kein Tod aktiv. */
    var deathTime = -1f

    /**
     * Alle Effekte auf den Ruhezustand — noetig ueberall dort, wo ein Lauf
     * endet, ohne dass gleich der naechste startet (Rueckkehr ins Menue).
     * Vor allem [deathTime]: Bliebe der Sturz aktiv, waere der Vogel im
     * READY-Bild laengst unten aus dem Kader gefallen und unsichtbar.
     */
    fun reset() {
        flashAlpha = 0f
        shakeTime = 0f
        celebrateTime = 0f
        deathTime = -1f
    }
}
