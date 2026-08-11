package de.robinrehbein.punkt.wear

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * Einzige Activity des Wear-Prototyps. FLAG_KEEP_SCREEN_ON bleibt für die
 * gesamte Lebensdauer der Activity gesetzt — einfacher und robuster als ein
 * Modifier, der nur während RUNNING greifen müsste, und für einen
 * Ein-Screen-Prototyp ohne Zusatz-Views unkritisch für den Akku.
 *
 * `controller` lebt hier statt in der Composable via remember{}: onKeyDown
 * (Hardware-Zusatztasten) läuft außerhalb der Composition und braucht
 * denselben Zustand wie der Touch-Handler in WearGameScreen.
 */
class MainActivity : ComponentActivity() {

    private lateinit var controller: WearGameController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        controller = WearGameController(applicationContext)
        setContent {
            WearGameScreen(controller)
        }
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
     * Hardware-Zusatztasten (z. B. der Quick-Button der Galaxy Watch Ultra)
     * lösen denselben Tap aus wie ein Touch auf das Display — praktisch,
     * weil der Finger beim Timing sonst genau die Zielzone verdeckt.
     * STEM_1..3 sind die für Drittanbieter-Apps nutzbaren Zusatztasten;
     * alles andere (insbesondere BACK) geht unangetastet an super, sonst
     * ließe sich die App über die Krone/Zurück-Taste nicht mehr verlassen.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_STEM_1,
            KeyEvent.KEYCODE_STEM_2,
            KeyEvent.KEYCODE_STEM_3 -> {
                controller.tap()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
