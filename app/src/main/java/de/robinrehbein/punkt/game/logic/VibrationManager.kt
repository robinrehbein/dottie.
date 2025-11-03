import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import de.robinrehbein.punkt.game.models.HitType

class VibrationManager(private val context: Context) {

    private val vibrator: Vibrator by lazy {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    }

    fun vibrateForHit(hitType: HitType) {
        if (!vibrator.hasVibrator()) return

        when (hitType) {
            HitType.PERFECT -> {
                val pattern = longArrayOf(100, 50, 50, 100)
                val amplitudes = intArrayOf(128, 64, 64, 255)
                val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
                vibrator.vibrate(effect)
            }
            HitType.GOOD -> {
                val effect = VibrationEffect.createOneShot(80, 128)
                vibrator.vibrate(effect)
            }
            HitType.BAD -> {
                val effect = VibrationEffect.createOneShot(120, 64)
                vibrator.vibrate(effect)
            }
            HitType.MISS -> {
                val pattern = longArrayOf(0, 100, 50, 100)
                val amplitudes = intArrayOf(0, 255, 0, 128)
                val effect = VibrationEffect.createWaveform(pattern, amplitudes, -1)
                vibrator.vibrate(effect)
            }
        }
    }
}
