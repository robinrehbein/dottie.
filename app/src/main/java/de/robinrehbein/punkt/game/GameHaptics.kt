package de.robinrehbein.punkt.game

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import de.robinrehbein.punkt.ui.platform.GameFeedback

/**
 * Haptisches Feedback für das Spiel. Jeder Effekt ist bewusst kurz gehalten,
 * damit er das Spielgefühl unterstützt statt zu nerven.
 */
class GameHaptics(private val context: Context) : GameFeedback {

    // VibratorManager gibt es erst ab API 31 — auf Android 9-11 (minSdk 28)
    // führt der alte Weg über VIBRATOR_SERVICE. Nullable + as?, damit ein
    // Gerät ganz ohne Vibrator niemals crasht, sondern nur still bleibt.
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** Kurzer, satter Blip bei einem Treffer in der Zone. */
    override fun score() {
        vibrate(VibrationEffect.createOneShot(28, 140))
    }

    /** Doppel-Tick für einen perfekten Treffer. */
    override fun perfect() {
        val timings = longArrayOf(0, 20, 40, 35)
        val amplitudes = intArrayOf(0, 120, 0, 220)
        vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    /** Harter Schlag beim Aufprall — der Rage-Moment. */
    override fun death() {
        val timings = longArrayOf(0, 70, 40, 130)
        val amplitudes = intArrayOf(0, 255, 0, 180)
        vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    /** Dumpfer Thud, wenn der Punkt nach dem Aus am Boden aufschlägt. */
    override fun thud() {
        vibrate(VibrationEffect.createOneShot(50, 90))
    }

    /** Aufsteigende Fanfare, wenn ein Twist oder eine neue Stufe freigeschaltet wird. */
    override fun unlock() {
        val timings = longArrayOf(0, 25, 45, 25, 45, 25, 45, 90)
        val amplitudes = intArrayOf(0, 90, 0, 140, 0, 200, 0, 255)
        vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    /** Feier-Muster für einen neuen Rekord. */
    override fun newRecord() {
        val timings = longArrayOf(0, 40, 60, 40, 60, 80)
        val amplitudes = intArrayOf(0, 160, 0, 200, 0, 255)
        vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    /**
     * Kurzer Tick beim Drücken eines Knopfs.
     *
     * Bewusst nicht über den Vibrator wie die Spiel-Muster oben, sondern
     * über `View.performHapticFeedback`: Das folgt der Systemeinstellung
     * für Berührungs-Feedback. Wer sie abschaltet, spürt keinen Tick.
     */
    override fun tap() {
        touchView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    // Der Tick braucht eine View. GameHaptics bekommt nur den Context
    // aus LocalContext, das ist die Activity (oder eine Hülle um sie).
    private val touchView: View? by lazy {
        var c: Context? = context
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        (c as? Activity)?.window?.decorView
    }

    private fun vibrate(effect: VibrationEffect) {
        val v = vibrator ?: return
        if (v.hasVibrator()) {
            v.vibrate(effect)
        }
    }
}
