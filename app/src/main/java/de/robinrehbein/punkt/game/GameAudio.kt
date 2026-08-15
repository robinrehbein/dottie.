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
 *
 * Seit den Ton-Sets liegen ALLE Sets im Pool, nicht nur das gewählte:
 * Drei Sets sind zusammen keine 200 kB, und ein Wechsel muss sofort
 * hörbar sein — mit Nachladen käme die Hörprobe in der Auswahl erst,
 * wenn der Finger längst weg ist.
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

    /** Ton-Set → Ereignisname → SoundPool-Id. */
    private val soundIds: Map<SoundSetId, Map<String, Int>>

    /** Stumm geschaltet? Der Screen hält das mit dem GameStore synchron. */
    var muted: Boolean = false

    /**
     * Das gewählte Ton-Set. Der Screen hält es mit dem GameStore
     * synchron; ein noch nicht verdientes Set kommt hier gar nicht an
     * (die Auswahl lässt es nicht antippen).
     */
    var soundSet: SoundSetId = SoundSetId.KLASSIK

    init {
        // Der Cache-Ordner ist versioniert: Ändert sich die Synthese,
        // Namen hochzählen — sonst spielen alte Dateien weiter. v2 trennt
        // die Sets in eigene Dateinamen.
        val dir = File(context.cacheDir, "sfx-v2").apply { mkdirs() }
        soundIds = SoundSetId.entries.associateWith { set ->
            ChipSynth.effects(set).mapValues { (name, samples) ->
                val file = File(dir, "${set.name}-$name.wav")
                if (!file.exists()) file.writeBytes(ChipSynth.toWav(samples))
                soundPool.load(file.path, 1)
            }
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

    /**
     * Hörprobe für die Auswahl: die Fanfare des angetippten Sets, auch
     * wenn es gerade nicht das gewählte ist. Ein Ton-Set ohne Probe wäre
     * eine Kachel, die man kaufen soll, ohne sie gesehen zu haben — und
     * die Fanfare zeigt vom Set am meisten: Lage, Länge und Anschlag.
     */
    fun preview(set: SoundSetId) = play("unlock", set = set)

    fun release() = soundPool.release()

    private fun play(name: String, rate: Float = 1f, set: SoundSetId = soundSet) {
        if (muted) return
        val id = soundIds[set]?.get(name) ?: return
        soundPool.play(id, 1f, 1f, 1, 0, rate.coerceIn(0.5f, 2f))
    }
}
