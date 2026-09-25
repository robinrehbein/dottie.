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

    /**
     * Stützräder an (Hand, Leuchten, Plan 8.6 #2)? Kein Effekt, sondern
     * Einstellung aus GameScreen: [reset] lässt ihn stehen.
     */
    var trainingWheels = false

    /** Restzeit von „NOCH NICHT“ nach einem Tap daneben in READY. */
    var notYetTime = 0f

    /** Drückt die Hand gerade (Punkt im Grün)? */
    var handPressed = false

    /** Sekunden seit dem Echo an der Fingerspitze, negativ = keins. */
    var handEchoTime = -1f

    /** Die laufenden Tipp-Echos, ältestes zuerst. */
    val tapEchoes = ArrayList<TapEcho>()
    // == /AP-22 ==

    /** Sekunden seit dem Tod (Mario-Huepfer), negativ = kein Tod aktiv. */
    var deathTime = -1f
    // == AP-23 nebel ==
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
        notYetTime = 0f
        handPressed = false
        handEchoTime = -1f
        tapEchoes.clear()
        // == /AP-22 ==
        deathTime = -1f
        // == AP-23 nebel ==
        // == /AP-23 ==
    }
}
