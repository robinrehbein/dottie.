package de.robinrehbein.punkt.wear

import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
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

    /** Aufsummierte Rotary-Einheiten seit dem letzten ausgelösten Tap. */
    private var rotaryAccumulated = 0f

    /** Zeitstempel (elapsedRealtime) des letzten Rotary-Taps, für die Entprellung. */
    private var lastRotaryTapMs = 0L

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
        setContent {
            WearGameScreen(controller)
        }
    }

    override fun onStart() {
        super.onStart()
        statsSync.start()
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
        super.onDestroy()
    }

    /**
     * Hardware-Zusatztasten lösen denselben Tap aus wie ein Touch aufs
     * Display — praktisch, weil der Finger beim Timing sonst genau die
     * Zielzone verdeckt.
     *
     * Was welche Uhr liefert (Stand der Recherche, Wear OS 4/5):
     *  - STEM_1..3 sind laut Google-Doku die einzigen Tasten-Keycodes, die
     *    Dritt-Apps im Vordergrund bekommen dürfen (Pixel Watch: Krone ist
     *    nur Rotary/Power; TicWatch u. a. liefern ihre freien
     *    Multifunktionstasten als STEM_1/2/3).
     *  - Samsung Galaxy Watch 4/5/6/7: Home- und Zurück-Taste sind
     *    System-Keys — es kommt für sie NIE ein STEM-Event bei Dritt-Apps
     *    an; das ist so dokumentiert und kein Bug dieser App.
     *  - Galaxy Watch Ultra: Der Quick-Button wurde von One UI zunächst
     *    teils als STEM_1 durchgereicht, seit dem Firmware-Update vom
     *    September 2025 (Wear OS 5) konsumiert das System das Event aber
     *    vollständig — Dritt-Apps sehen weder onKeyDown noch
     *    dispatchKeyEvent, es gibt keine Permission dagegen. Die
     *    STEM-Behandlung hier bleibt trotzdem: Sie ist auf allen anderen
     *    Uhren mit freier Multifunktionstaste der offizielle Weg und
     *    schadet auf Samsung-Geräten nicht.
     *  - STEM_PRIMARY ist der Power-/Home-Knopf und system-reserviert —
     *    bewusst NICHT abgefangen, genau wie BACK: alles außer STEM_1..3
     *    geht unangetastet an super, sonst ließe sich die App über die
     *    Krone/Zurück-Geste nicht mehr verlassen.
     *
     * repeatCount-Guard wie in der Google-Doku: Nur der erste Down zählt,
     * Halten der Taste darf im Timing-Spiel kein Dauerfeuer auslösen.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_STEM_1,
            KeyEvent.KEYCODE_STEM_2,
            KeyEvent.KEYCODE_STEM_3 -> {
                if (event == null || event.repeatCount == 0) controller.tap()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    /**
     * Drehkrone/Bezel als zusätzlicher Hardware-Tap: eine Raste = ein Tap.
     *
     * Hintergrund: Auf der Galaxy Watch Ultra ist der Quick-Button für
     * Dritt-Apps nicht abfangbar (siehe onKeyDown) — der drehbare Ring
     * bzw. die Touch-Lünette ist dort die einzige Hardware-Eingabe, die
     * bei Apps ankommt. Rotary kommt als generisches MotionEvent
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
                    rotaryAccumulated = 0f
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastRotaryTapMs >= ROTARY_TAP_DEBOUNCE_MS) {
                        lastRotaryTapMs = now
                        controller.tap()
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
