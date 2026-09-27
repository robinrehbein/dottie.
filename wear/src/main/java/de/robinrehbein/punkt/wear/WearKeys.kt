package de.robinrehbein.punkt.wear

import android.view.KeyEvent
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.TimingGame

/**
 * Was eine Hardware-Taste im Spiel bewirkt. Die Entscheidung steht hier,
 * ohne Activity und ohne Android-Dienste — damit sie prüfbar ist und
 * MainActivity nur noch ausführt.
 */
internal enum class WearKeyAction {
    /** Derselbe Tap wie ein Finger aufs Display (controller.tap()). */
    TAP,

    /** Im offenen Wähler: Auswahl übernehmen und schließen. */
    CONFIRM_PICKER,

    /** Schlucken, ohne etwas zu tun (gehaltene Taste, gesperrte Phase). */
    SWALLOW,

    /** Nicht unsere Taste: unverändert ans System weiterreichen. */
    SYSTEM
}

/**
 * Die Tastenbelegung der Uhr.
 *
 * **Multifunktionstasten (STEM_1..3)** sind laut Wear-OS-Doku die einzigen
 * Tasten, die Dritt-Apps bekommen dürfen. Sie tippen in jeder Phase,
 * genau wie ein Finger — im offenen Wähler bestätigen sie stattdessen.
 * Welche Uhr sie wirklich liefert, steht in MainActivity.
 *
 * **Zurück (KEYCODE_BACK)** ist die Taste, die Wear OS zum Verlassen
 * erwartet. Sie tippt deshalb NUR, solange ein Lauf wirklich läuft
 * (RUNNING) — dort ist ein Verlassen ohnehin ein verlorener Lauf, und die
 * Taste unten rechts liegt dem Daumen am nächsten. Danach gilt:
 *  - DYING und die Neustart-Sperre im Game-Over schlucken sie: Wer eben
 *    mit ihr getippt hat und gestorben ist, drückt gerade noch einmal —
 *    ein Nachdrücken darf die App nicht schließen.
 *  - Im Startbildschirm und im Game-Over nach der Sperre ist sie wieder
 *    das normale Zurück und schließt die App.
 * Wischen zum Schließen bleibt davon unberührt (das erledigt das
 * Fenster, nicht die Taste), und die Home-Taste (STEM_PRIMARY) fasst die
 * App nie an.
 *
 * Nur eine ECHTE Taste zählt ([physical]): Ein vom System erzeugtes
 * Zurück (Geste, Barrierefreiheit) bleibt ein Zurück.
 *
 * Gehaltene Tasten ([repeatCount] > 0) tippen nie: Im Timing-Spiel wäre
 * Dauerfeuer ein sofortiger Fehl-Tap.
 */
internal object WearKeys {

    /** Ist das eine der freien Multifunktionstasten? */
    fun isStem(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_STEM_1 ||
            keyCode == KeyEvent.KEYCODE_STEM_2 ||
            keyCode == KeyEvent.KEYCODE_STEM_3

    /**
     * Die Wirkung eines Tasten-DRUCKS (ACTION_DOWN). [phaseElapsed] sind
     * die Sekunden seit dem letzten Phasenwechsel (TimingGame.elapsed).
     */
    fun onDown(
        keyCode: Int,
        repeatCount: Int,
        phase: GamePhase,
        phaseElapsed: Float,
        pickerOpen: Boolean,
        physical: Boolean,
        backTaps: Boolean = true
    ): WearKeyAction {
        if (isStem(keyCode)) {
            if (repeatCount > 0) return WearKeyAction.SWALLOW
            return if (pickerOpen) WearKeyAction.CONFIRM_PICKER else WearKeyAction.TAP
        }
        if (keyCode != KeyEvent.KEYCODE_BACK) return WearKeyAction.SYSTEM
        // Im Wähler ist Zurück genau das: Wähler zu. Der Wähler schreibt
        // die Wahl beim Schließen fest, also ist das auch ein Bestätigen.
        if (pickerOpen) {
            return if (repeatCount > 0) WearKeyAction.SWALLOW else WearKeyAction.CONFIRM_PICKER
        }
        if (!backTaps || !physical) return WearKeyAction.SYSTEM
        return when (phase) {
            GamePhase.RUNNING ->
                if (repeatCount > 0) WearKeyAction.SWALLOW else WearKeyAction.TAP
            GamePhase.DYING -> WearKeyAction.SWALLOW
            GamePhase.OVER ->
                if (phaseElapsed < BACK_GRACE_SECONDS) WearKeyAction.SWALLOW else WearKeyAction.SYSTEM
            GamePhase.READY -> WearKeyAction.SYSTEM
        }
    }

    /**
     * So lange nach dem Tod schluckt das Game-Over ein Zurück — dieselbe
     * Sperre, in der auch ein Finger-Tap noch nichts neu startet.
     */
    const val BACK_GRACE_SECONDS = TimingGame.RESTART_LOCK_SECONDS
}
