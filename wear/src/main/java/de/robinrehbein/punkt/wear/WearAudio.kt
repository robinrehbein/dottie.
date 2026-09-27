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
 * Variante von GameAudio in :app: dieselben acht Ereignisse, moderate
 * Lautstärke für den kleinen Uhren-Lautsprecher.
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

    /** Lauf-Start ohne Treffer (Sofort-Neustart aus dem Game-Over). */
    fun start() = play("start")

    /** Treffer-Blip; die Tonhöhe klettert pro 5er-Stufe eine Pentatonik hoch. */
    fun hit(score: Int) = play("hit", rate = ChipSynth.hitRate(score))

    /** Münz-Sound; jede Serien-Stufe klingt zwei Halbtöne höher. */
    fun perfect(streak: Int) = play("perfect", rate = ChipSynth.perfectRate(streak))

    /** Folge-Zone der KETTE. */
    fun chain() = play("chain")

    /** Fanfare für neue Twists und jede 5er-Stufe. */
    fun unlock() = play("unlock")

    /** Fallender Sweep plus Rausch-Burst beim Tod. */
    fun death() = play("death")

    /** Rekord-Jingle. */
    fun newRecord() = play("record")

    /** Aufschlag, wenn das Ergebnis feststeht (ohne neuen Rekord). */
    fun thud() = play("thud")

    /**
     * Hörprobe im Ton-Wähler: die Fanfare des Sets [set], auch wenn es
     * noch nicht festgeschrieben ist — wie preview am Telefon.
     */
    fun preview(set: SoundSetId) = play("unlock", set = set)

    fun release() {
        released = true
        soundPool.release()
    }

    private fun play(name: String, rate: Float = 1f, set: SoundSetId = soundSet) {
        if (muted || released) return
        val id = soundIds[set]?.get(name) ?: return
        soundPool.play(id, VOLUME, VOLUME, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    private companion object {
        /** Moderat statt voll aufgedreht — Uhren-Lautsprecher sitzen nah am Ohr. */
        const val VOLUME = 0.8f

        /**
         * Alle acht Ereignisse eines Ton-Sets, wie am Telefon: Ein Set, das
         * auf der Uhr den Start, die Kette oder den Aufschlag verschluckt,
         * klänge dort anders als gewählt (README, „Ton-Sets“).
         */
        val WEAR_EFFECTS = listOf("start", "hit", "perfect", "chain", "unlock", "record", "death", "thud")
    }
}
