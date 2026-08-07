package de.robinrehbein.punkt.game

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptisches Feedback für das Spiel. Jeder Effekt ist bewusst kurz gehalten,
 * damit er das Spielgefühl unterstützt statt zu nerven.
 */
class GameHaptics(context: Context) {

    private val vibrator: Vibrator by lazy {
        val vibratorManager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    }

    /** Ganz leichter Tick bei jedem Schwerkraft-Kippen. */
    fun flip() {
        vibrate(VibrationEffect.createOneShot(12, 35))
    }

    /** Kurzer, satter Blip beim Passieren eines Hindernisses. */
    fun score() {
        vibrate(VibrationEffect.createOneShot(28, 140))
    }

    /** Doppel-Tick für einen perfekten Treffer im Stopp-Modus. */
    fun perfect() {
        val timings = longArrayOf(0, 20, 40, 35)
        val amplitudes = intArrayOf(0, 120, 0, 220)
        vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    /** Harter Schlag beim Aufprall — der Rage-Moment. */
    fun death() {
        val timings = longArrayOf(0, 70, 40, 130)
        val amplitudes = intArrayOf(0, 255, 0, 180)
        vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    /** Dumpfer Thud, wenn der Punkt am Boden aufschlägt. */
    fun thud() {
        vibrate(VibrationEffect.createOneShot(50, 90))
    }

    /** Feier-Muster für einen neuen Rekord. */
    fun newRecord() {
        val timings = longArrayOf(0, 40, 60, 40, 60, 80)
        val amplitudes = intArrayOf(0, 160, 0, 200, 0, 255)
        vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    private fun vibrate(effect: VibrationEffect) {
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(effect)
        }
    }
}
