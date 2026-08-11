package de.robinrehbein.punkt.wear

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import de.robinrehbein.punkt.game.ChipSynth
import java.io.File
import kotlin.concurrent.thread

/**
 * Chiptune-Soundeffekte für die Uhr — dieselbe Synthese wie am Phone
 * (ChipSynth in :core), damit beide Apps identisch klingen. Schlanke
 * Variante von GameAudio in :app: nur die für Wear relevanten Effekte,
 * moderate Lautstärke für den kleinen Uhren-Lautsprecher.
 *
 * Die WAVs landen einmalig im Cache und laufen über einen SoundPool
 * (niedrige Latenz). Synthese, Datei-I/O und das Laden passieren auf
 * einem Hintergrund-Thread, damit App-Start und Frame-Loop nie darauf
 * warten — bis dahin sind die Sounds einfach noch stumm.
 */
class WearAudio(context: Context) {

    private val appContext = context.applicationContext

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    /** Name → SoundPool-Id; der Lade-Thread befüllt die Map einmalig. */
    @Volatile
    private var soundIds: Map<String, Int> = emptyMap()

    /** Stumm geschaltet? Der Controller hält das mit den Prefs synchron. */
    @Volatile
    var muted: Boolean = false

    @Volatile
    private var released = false

    init {
        thread(name = "wear-sfx-init", isDaemon = true) {
            // Der Cache-Ordner ist versioniert wie am Phone: Ändert sich
            // die Synthese, Namen hochzählen — sonst spielen alte Dateien.
            val dir = File(appContext.cacheDir, "sfx-v1").apply { mkdirs() }
            val effects = ChipSynth.effects()
            val ids = HashMap<String, Int>()
            for (name in WEAR_EFFECTS) {
                val samples = effects[name] ?: continue
                val file = File(dir, "$name.wav")
                if (!file.exists()) file.writeBytes(ChipSynth.toWav(samples))
                if (released) return@thread
                ids[name] = soundPool.load(file.path, 1)
            }
            soundIds = ids
        }
    }

    /** Treffer-Blip; die Tonhöhe klettert pro 5er-Stufe eine Pentatonik hoch. */
    fun hit(score: Int) = play("hit", rate = ChipSynth.hitRate(score))

    /** Münz-Sound; jede Serien-Stufe klingt zwei Halbtöne höher. */
    fun perfect(streak: Int) = play("perfect", rate = ChipSynth.perfectRate(streak))

    /** Fanfare für neue Twists und jede 5er-Stufe. */
    fun unlock() = play("unlock")

    /** Fallender Sweep plus Rausch-Burst beim Tod. */
    fun death() = play("death")

    /** Rekord-Jingle. */
    fun newRecord() = play("record")

    fun release() {
        released = true
        soundPool.release()
    }

    private fun play(name: String, rate: Float = 1f) {
        if (muted || released) return
        val id = soundIds[name] ?: return
        soundPool.play(id, VOLUME, VOLUME, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    private companion object {
        /** Moderat statt voll aufgedreht — Uhren-Lautsprecher sitzen nah am Ohr. */
        const val VOLUME = 0.8f

        /**
         * Teilmenge der Phone-Effekte, die es auf der Uhr braucht — kein
         * "start"/"chain"/"thud", der Prototyp bleibt bewusst schlank.
         */
        val WEAR_EFFECTS = listOf("hit", "perfect", "unlock", "record", "death")
    }
}
