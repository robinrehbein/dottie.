@file:OptIn(ExperimentalForeignApi::class)

package de.robinrehbein.punkt.ui.platform

import de.robinrehbein.punkt.game.ChipSynth
import de.robinrehbein.punkt.game.SoundBank
import de.robinrehbein.punkt.game.SoundEvent
import de.robinrehbein.punkt.game.SoundSetId
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * [GameSounds] ueber AVAudioPlayer.
 *
 * Die Samples kommen aus `:core` — [SoundBank] beschreibt jeden Ton,
 * [ChipSynth] rechnet ihn aus und verpackt ihn als WAV. Hier wird nur
 * abgespielt.
 *
 * Alle Klaenge werden beim Start einmal vorgerechnet: Drei Ton-Sets sind
 * zusammen kein Speicherproblem, und die Hoerprobe in der Auswahl muss
 * sofort kommen. Die Tonhoehen-Varianten (Treffer-Pentatonik,
 * Perfekt-Serie) stehen als eigene Puffer da — Android pitcht beim
 * Abspielen (SoundPool-Rate), AVAudioPlayer kann das nicht.
 */
class IosSounds : GameSounds {

    override var muted: Boolean = false
    override var soundSet: SoundSetId = SoundSetId.KLASSIK

    /** Ton-Set -> Name -> Spieler. */
    private val players: Map<SoundSetId, Map<String, AVAudioPlayer>> = buildMap {
        SoundBank.ORDER.forEach { set ->
            put(set, buildMap {
                SoundBank.EVENTS.forEach { event ->
                    if (event != SoundEvent.HIT && event != SoundEvent.PERFECT) {
                        player(ChipSynth.render(SoundBank.voice(set, event)))
                            ?.let { put(event.name.lowercase(), it) }
                    }
                }
                // Treffer-Blip: fuenf Pentatonik-Stufen (score % 5).
                for (step in 0 until 5) {
                    val samples = ChipSynth.render(
                        SoundBank.voice(set, SoundEvent.HIT), ChipSynth.hitRate(step)
                    )
                    player(samples)?.let { put("hit$step", it) }
                }
                // Muenz-Sound: fuenf Serien-Stufen (Deckel bei 5).
                for (streak in 1..5) {
                    val samples = ChipSynth.render(
                        SoundBank.voice(set, SoundEvent.PERFECT), ChipSynth.perfectRate(streak)
                    )
                    player(samples)?.let { put("perfect$streak", it) }
                }
            })
        }
    }

    init {
        // Ambient: Das Spiel unterbricht keine laufende Musik.
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryAmbient, null)
        AVAudioSession.sharedInstance().setActive(true, null)
        players.values.forEach { set -> set.values.forEach { it.prepareToPlay() } }
    }

    override fun start() = play("start")

    override fun hit(score: Int) = play("hit${((score % 5) + 5) % 5}")

    override fun perfect(streak: Int) = play("perfect${streak.coerceIn(1, 5)}")

    override fun chain() = play("chain")
    override fun unlock() = play("unlock")
    override fun death() = play("death")
    override fun thud() = play("thud")
    override fun newRecord() = play("record")

    override fun preview(set: SoundSetId) = play("unlock", set)

    override fun release() {
        players.values.forEach { entries -> entries.values.forEach { it.stop() } }
    }

    private fun play(name: String, set: SoundSetId? = null) {
        if (muted) return
        val spieler = players[set ?: soundSet]?.get(name) ?: return
        // Zuruecksetzen statt neu bauen: Ein zweiter Treffer waehrend des
        // ersten soll den Klang neu ansetzen, nicht verschlucken.
        spieler.currentTime = 0.0
        spieler.play()
    }

    private fun MutableMap<String, AVAudioPlayer>.player(samples: FloatArray): AVAudioPlayer? {
        val wav = ChipSynth.toWav(samples)
        val daten = wav.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = wav.size.toULong())
        }
        return AVAudioPlayer(data = daten, error = null)
    }
}
