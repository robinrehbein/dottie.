package de.robinrehbein.punkt.wear

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import de.robinrehbein.punkt.game.ChipSynth
import de.robinrehbein.punkt.game.SoundSetId
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
 *
 * Seit den Ton-Sets liegen alle Sets im Pool. Gewählt wird auf dem
 * Telefon; die Uhr bekommt die Wahl über den Abgleich und muss sie
 * sofort spielen können — Nachladen hieße, dass der erste Treffer nach
 * dem Abgleich noch im alten Set klingt.
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

    /** Set → Name → SoundPool-Id; der Lade-Thread befüllt die Map einmalig. */
    @Volatile
    private var soundIds: Map<SoundSetId, Map<String, Int>> = emptyMap()

    /**
     * Das gewählte Ton-Set. Die Uhr wählt es nicht selbst — der
     * Controller setzt es aus den Prefs und aus dem, was der Abgleich
     * vom Telefon bringt.
     */
    @Volatile
    var soundSet: SoundSetId = SoundSetId.KLASSIK

    /** Stumm geschaltet? Der Controller hält das mit den Prefs synchron. */
    @Volatile
    var muted: Boolean = false

    @Volatile
    private var released = false

    init {
        thread(name = "wear-sfx-init", isDaemon = true) {
            // Der Cache-Ordner ist versioniert wie am Phone: Ändert sich
            // die Synthese, Namen hochzählen — sonst spielen alte
            // Dateien. v2 trennt die Sets in eigene Dateinamen.
            val dir = File(appContext.cacheDir, "sfx-v2").apply { mkdirs() }
            val ids = HashMap<SoundSetId, Map<String, Int>>()
            for (set in SoundSetId.entries) {
                val effects = ChipSynth.effects(set)
                val proSet = HashMap<String, Int>()
                for (name in WEAR_EFFECTS) {
                    val samples = effects[name] ?: continue
                    val file = File(dir, "${set.name}-$name.wav")
                    if (!file.exists()) file.writeBytes(ChipSynth.toWav(samples))
                    if (released) return@thread
                    proSet[name] = soundPool.load(file.path, 1)
                }
                ids[set] = proSet
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
        val id = soundIds[soundSet]?.get(name) ?: return
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
