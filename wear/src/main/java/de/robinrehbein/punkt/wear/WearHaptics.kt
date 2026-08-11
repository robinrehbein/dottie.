package de.robinrehbein.punkt.wear

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptik zusätzlich zum Sound (WearAudio) — Vibration bleibt das
 * primäre Feedback am Handgelenk, gerade wenn der Ton aus ist
 * (siehe GameHaptics in :app für die Telefon-Entsprechung).
 *
 * minSdk 30 liegt unter API 31 (VibratorManager) — auf API 30 muss der
 * alte Weg über den klassischen Vibrator-Service greifen.
 */
class WearHaptics(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }

    /** Kurzer Klick bei einem normalen Treffer. */
    fun hit() {
        vibrate(VibrationEffect.createOneShot(20, 120))
    }

    /** Stärkerer Doppelklick bei einem perfekten Treffer. */
    fun perfectHit() {
        val timings = longArrayOf(0, 18, 35, 30)
        val amplitudes = intArrayOf(0, 140, 0, 220)
        vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    /** Langes Rumpeln beim Tod. */
    fun died() {
        vibrate(VibrationEffect.createOneShot(120, 255))
    }

    private fun vibrate(effect: VibrationEffect) {
        val v = vibrator ?: return
        if (v.hasVibrator()) v.vibrate(effect)
    }
}
