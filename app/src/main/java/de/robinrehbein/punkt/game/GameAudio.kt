package de.robinrehbein.punkt.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File

/**
 * Chiptune-Soundeffekte für das Spiel — zur Laufzeit aus Rechteckwellen
 * synthetisiert (ChipSynth), keine Audio-Assets im Repo. Die fertigen
 * WAVs landen einmalig im Cache und laufen über einen SoundPool, damit
 * die Latenz für ein Timing-Spiel niedrig genug ist.
 */
class GameAudio(context: Context) {

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds: Map<String, Int>

    /** Stumm geschaltet? Der Screen hält das mit dem ScoreStore synchron. */
    var muted: Boolean = false

    init {
        // Der Cache-Ordner ist versioniert: Ändert sich die Synthese,
        // Namen hochzählen — sonst spielen alte Dateien weiter.
        val dir = File(context.cacheDir, "sfx-v1").apply { mkdirs() }
        soundIds = ChipSynth.effects().mapValues { (name, samples) ->
            val file = File(dir, "$name.wav")
            if (!file.exists()) file.writeBytes(ChipSynth.toWav(samples))
            soundPool.load(file.path, 1)
        }
    }

    fun start() = play("start")

    /** Treffer-Blip; die Tonhöhe klettert pro 5er-Stufe eine Pentatonik hoch. */
    fun hit(score: Int) = play("hit", rate = ChipSynth.hitRate(score))

    /** Münz-Sound; jede Serien-Stufe klingt zwei Halbtöne höher. */
    fun perfect(streak: Int) = play("perfect", rate = ChipSynth.perfectRate(streak))

    fun chain() = play("chain")
    fun unlock() = play("unlock")
    fun death() = play("death")
    fun thud() = play("thud")
    fun newRecord() = play("record")

    fun release() = soundPool.release()

    private fun play(name: String, rate: Float = 1f) {
        if (muted) return
        val id = soundIds[name] ?: return
        soundPool.play(id, 1f, 1f, 1, 0, rate.coerceIn(0.5f, 2f))
    }
}
