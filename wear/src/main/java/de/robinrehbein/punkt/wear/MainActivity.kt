package de.robinrehbein.punkt.wear

import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import de.robinrehbein.punkt.sync.StatsSync
import kotlin.math.abs
import kotlin.math.sign

/**
 * Einzige Activity des Wear-Prototyps. FLAG_KEEP_SCREEN_ON bleibt für die
 * gesamte Lebensdauer der Activity gesetzt — einfacher und robuster als ein
 * Modifier, der nur während RUNNING greifen müsste, und für einen
 * Ein-Screen-Prototyp ohne Zusatz-Views unkritisch für den Akku.
 *
 * `controller` lebt hier statt in der Composable via remember{}: onKeyDown
 * und dispatchGenericMotionEvent (Hardware-Tasten und Drehkrone/Bezel)
 * laufen außerhalb der Composition und brauchen denselben Zustand wie der
 * Touch-Handler in WearGameScreen.
 */
class MainActivity : ComponentActivity() {

    private lateinit var controller: WearGameController
    private lateinit var statsSync: StatsSync
    private lateinit var patron: WearPatron

    /** Aufsummierte Rotary-Einheiten seit dem letzten ausgelösten Tap. */
    private var rotaryAccumulated = 0f

    /** Zeitstempel (elapsedRealtime) des letzten Rotary-Taps, für die Entprellung. */
    private var lastRotaryTapMs = 0L

    /** Taste, deren Drücken verbraucht wurde — ihr Loslassen gehört dann auch uns. */
    private var consumedKey = KeyEvent.KEYCODE_UNKNOWN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        controller = WearGameController(applicationContext)
        // Abgleich mit dem Telefon. Der Controller kennt den Data Layer
        // nicht — er meldet nur, dass sich etwas geaendert hat.
        statsSync = StatsSync(
            context = applicationContext,
            read = { controller.syncState() },
            write = { controller.applySync(it) }
        )
        controller.onStateChanged = { statsSync.publish() }
        // „NOCH NICHT“ als kurzer Tick über das Berührungs-Feedback der
        // Ansicht: Er folgt damit der Systemeinstellung und lässt sich
        // dort abschalten (Plan 8.6 Punkt 17), anders als die Vibration
        // bei Treffer und Tod. Kommt für Touch, Taste und Drehring
        // gleichermaßen, weil alle drei über controller.tap() laufen.
        controller.onNotYetTick = {
            window.decorView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        // Gönner-Kauf: Die Uhr fragt Play selbst, statt sich ein Flag vom
        // Telefon schicken zu lassen — der Kauf hängt am Google-Konto.
        patron = WearPatron(applicationContext) {
            // Neu erfahren: Jetzt darf auch ein vom Telefon gewählter
            // Gönner-Skin durchgehen, deshalb gleich noch einmal
            // abgleichen (siehe WearGameController.applySync).
            if (controller.onPatronResolved(true)) statsSync.pullAndPublish()
        }
        setContent {
            WearGameScreen(controller)
        }
    }

    override fun onStart() {
        super.onStart()
        statsSync.start()
        // Bei jedem Sichtbarwerden neu fragen: Der Kauf kann eben erst am
        // Telefon passiert sein. Ohne Play bleibt der Aufruf folgenlos.
        patron.connect()
    }

    override fun onStop() {
        statsSync.stop()
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        controller.onAppPaused()
    }

    override fun onDestroy() {
        // SoundPool freigeben (WearAudio) — Ein-Activity-App, Destroy = Ende.
        if (::controller.isInitialized) controller.release()
        if (::patron.isInitialized) patron.release()
        super.onDestroy()
    }

    /**
     * Hardware-Tasten lösen denselben Tap aus wie ein Finger aufs Display —
     * über denselben Aufruf (controller.tap()) und im selben Moment: beim
     * DRÜCKEN, wie der Touch-Tap (onPress in WearGameScreen). Praktisch,
     * weil der Finger beim Timing sonst genau die Zielzone verdeckt. Was
     * welche Taste tut, entscheidet [WearKeys]; hier wird nur ausgeführt.
     *
     * Neben onKeyDown steht onKeyUp: Das Loslassen einer Taste, deren
     * Drücken hier verbraucht wurde, gehört ebenfalls uns — sonst schlösse
     * das System die App beim Loslassen von ZURÜCK, obwohl die Taste eben
     * getippt hat. (dispatchKeyEvent wäre der frühere Haken, ist in
     * ComponentActivity aber als eingeschränkte API markiert.)
     *
     * Was welche Uhr liefert (Stand der Recherche, September 2026):
     *  - STEM_1..3 sind laut Wear-OS-Doku („Physical buttons“) die einzigen
     *    Tasten-Keycodes, die Dritt-Apps im Vordergrund bekommen. Uhren mit
     *    freien Multifunktionstasten (Mobvoi TicWatch u. a.) liefern sie so.
     *  - STEM_PRIMARY (Krone/Home oben) ist system-reserviert und wird hier
     *    nie angefasst — sie führt immer zum Zifferblatt.
     *  - Samsung Galaxy Watch 4 bis 8 und Ultra: Home ist System, und laut
     *    Samsung-Entwicklerforum lassen sich die Tasten von Dritt-Apps
     *    nicht umbelegen. Die orange Quick-Taste der Ultra wird nach
     *    Entwicklerberichten seit dem Firmware-Update vom September 2025
     *    vollständig vom System verbraucht (keine KeyEvents an Apps, keine
     *    Berechtigung dafür); belegbar ist sie nur mit Samsungs eigenen
     *    Aktionen. Kommt auf einer Firmware doch ein STEM-Event an, tippt
     *    es hier ohne weiteres Zutun.
     *  - Die ZURÜCK-Taste (Samsung: unten rechts) kommt als KEYCODE_BACK
     *    an, solange sie in den Uhr-Einstellungen auf „Zurück“ steht. Sie
     *    tippt nur im laufenden Lauf, sonst bleibt sie Zurück (siehe
     *    [WearKeys]).
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val action = WearKeys.onDown(
            keyCode = keyCode,
            repeatCount = event.repeatCount,
            phase = controller.game.phase,
            phaseElapsed = controller.game.elapsed,
            pickerOpen = controller.pickerOpen,
            physical = isPhysicalKey(event)
        )
        when (action) {
            WearKeyAction.SYSTEM -> return super.onKeyDown(keyCode, event)
            WearKeyAction.TAP -> controller.tap()
            WearKeyAction.CONFIRM_PICKER -> controller.closePicker()
            WearKeyAction.SWALLOW -> Unit
        }
        consumedKey = keyCode
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == consumedKey) {
            consumedKey = KeyEvent.KEYCODE_UNKNOWN
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Eine echte Taste am Gehäuse — kein vom System erzeugtes Zurück
     * (Geste, Barrierefreiheit), das kommt vom virtuellen Gerät.
     */
    private fun isPhysicalKey(event: KeyEvent): Boolean =
        event.deviceId != KeyCharacterMap.VIRTUAL_KEYBOARD &&
            (event.flags and KeyEvent.FLAG_VIRTUAL_HARD_KEY) == 0

    /**
     * Drehkrone/Bezel als zusätzlicher Hardware-Tap: eine Raste = ein Tap.
     *
     * Hintergrund: Auf der Galaxy Watch Ultra ist der Quick-Button für
     * Dritt-Apps nicht abfangbar (siehe onKeyDown) — neben ZURÜCK
     * ist die Touch-Lünette (bzw. bei anderen Uhren Krone oder
     * Drehring) die Hardware-Eingabe, die bei Apps ankommt. Rotary kommt als generisches MotionEvent
     * (SOURCE_ROTARY_ENCODER, ACTION_SCROLL, AXIS_SCROLL); Abfang auf
     * dispatch-Ebene statt via Compose-onRotaryScrollEvent, weil so kein
     * fokussierbarer Knoten samt FocusRequester nötig ist — der
     * Spiel-Screen hat sonst keinerlei Fokus-Handling.
     *
     * AXIS_SCROLL liefert geräteunabhängige Einheiten (~1.0 pro Raste bei
     * gerasterten Kronen/Lünetten; kontinuierliche Kronen wie die der
     * Pixel Watch summieren sich in kleinen Schritten dorthin). Deshalb:
     * aufsummieren, bei |Summe| >= 1 einen Tap feuern, bei
     * Richtungswechsel neu ansetzen. Die Entprellung (ROTARY_TAP_DEBOUNCE_MS)
     * verhindert, dass ein schneller Dreh über mehrere Rasten als
     * Tap-Salve durchschlägt — im Timing-Spiel wäre das ein sofortiger
     * Fehl-Tap nach dem gewollten.
     */
    override fun dispatchGenericMotionEvent(ev: MotionEvent?): Boolean {
        if (ev != null &&
            ev.action == MotionEvent.ACTION_SCROLL &&
            ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
        ) {
            // Vorzeichen egal — beide Drehrichtungen sollen tappen, gezählt
            // wird nur der Betrag seit dem letzten Richtungswechsel.
            val delta = ev.getAxisValue(MotionEvent.AXIS_SCROLL)
            if (delta != 0f) {
                // Richtungswechsel verwirft den alten Rest — sonst könnten
                // sich gegenläufige Wackler zu einem Phantom-Tap addieren.
                if (sign(delta) != sign(rotaryAccumulated)) rotaryAccumulated = 0f
                rotaryAccumulated += delta
                if (abs(rotaryAccumulated) >= ROTARY_UNITS_PER_TAP) {
                    val steps = sign(rotaryAccumulated).toInt()
                    rotaryAccumulated = 0f
                    if (controller.pickerOpen) {
                        // Im Wähler ist die Krone kein Tap, sondern der
                        // Cursor: eine Raste = ein Eintrag weiter. Ohne
                        // Entprellung — hier ist ein Schritt zu viel
                        // folgenlos, im Lauf wäre er ein Fehl-Tap.
                        controller.movePickerCursor(steps)
                    } else {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastRotaryTapMs >= ROTARY_TAP_DEBOUNCE_MS) {
                            lastRotaryTapMs = now
                            controller.tap()
                        }
                    }
                }
            }
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    private companion object {
        /** Eine volle Raste (in AXIS_SCROLL-Einheiten) löst genau einen Tap aus. */
        const val ROTARY_UNITS_PER_TAP = 1f

        /** Mindestabstand zwischen zwei Rotary-Taps — Schwungdreher entprellen. */
        const val ROTARY_TAP_DEBOUNCE_MS = 250L
    }
}
