package de.robinrehbein.punkt.game

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.random.Random

/**
 * Die Wellenformen, die der Baukasten kennt.
 *
 * Mehr als zwei sind es bewusst nicht. Eine Sägezahnwelle klänge im
 * NES-Rahmen wie ein Fremdkörper — der Chip, dem dieses Spiel seinen
 * Klang schuldet, hatte zwei Pulskanäle, einen Dreieckkanal und
 * Rauschen, aber keine Säge. Wer eine dritte Form hinzufügt, sollte
 * also erst sagen können, welches Ton-Set sie braucht.
 */
enum class Wave {
    /**
     * Rechteck mit einstellbarer Pulsbreite — hell, schneidend, der
     * Klang des Bestands. Die Pulsbreite ist hier der Charakterregler.
     */
    PULS,

    /**
     * Dreieck: nur ungerade Oberwellen, und die fallen quadratisch statt
     * linear ab. Klingt deshalb weich und flötenartig statt schneidend,
     * und trägt in hoher Lage, wo ein Rechteck sticht. Die Pulsbreite
     * bleibt hier ungelesen — ein Dreieck hat keine.
     */
    DREIECK
}

/**
 * Purer Kotlin-Synthesizer für Chiptune-Soundeffekte im NES-Stil:
 * Rechteckwellen und Rauschen, zur Laufzeit erzeugt — keine Audio-Assets.
 * Lebt in :core (kein Android nötig), damit Phone (:app, GameAudio) und
 * Uhr (:wear, WearAudio) exakt dieselben Klänge erzeugen.
 *
 * Alle Funktionen liefern Mono-Samples im Bereich [-1, 1] bei
 * [SAMPLE_RATE] Hz. Jeder Ton bekommt eine kurze Attack-Rampe und ein
 * End-Fade gegen Knackser sowie eine exponentiell abfallende Hüllkurve
 * für den typischen perkussiven "Blip"-Charakter.
 */
object ChipSynth {

    const val SAMPLE_RATE = 22050

    /** Attack-Rampe (~1,5 ms) gegen Knackser am Ton-Anfang. */
    private const val ATTACK_SAMPLES = 32

    /** Lineares Fade-Out (~3 ms) gegen Knackser am Ton-Ende. */
    private const val FADE_OUT_SAMPLES = 64

    /** Rechteckwelle mit fester Frequenz. decay = Abklingrate pro Sekunde. */
    fun square(
        freqHz: Float,
        seconds: Float,
        volume: Float = 0.4f,
        decay: Float = 14f,
        duty: Float = 0.5f
    ): FloatArray = render(seconds, volume, decay, Wave.PULS) { _ -> freqHz to duty }

    /**
     * Dreieckwelle mit fester Frequenz — dieselbe Hüllkurve wie
     * [square], nur eine weichere Form. Keine Pulsbreite: Ein Dreieck
     * hat keine, und ein Parameter, den niemand liest, wäre eine Lüge
     * in der Signatur.
     */
    fun triangle(
        freqHz: Float,
        seconds: Float,
        volume: Float = 0.4f,
        decay: Float = 14f
    ): FloatArray = render(seconds, volume, decay, Wave.DREIECK) { _ -> freqHz to 0.5f }

    /** Welle, deren Frequenz linear von [fromHz] nach [toHz] gleitet. */
    fun sweep(
        fromHz: Float,
        toHz: Float,
        seconds: Float,
        volume: Float = 0.4f,
        decay: Float = 5f,
        wave: Wave = Wave.PULS
    ): FloatArray = render(seconds, volume, decay, wave) { progress ->
        (fromHz + (toHz - fromHz) * progress) to 0.5f
    }

    /** Rausch-Burst (deterministischer Seed, damit die WAVs stabil bleiben). */
    fun noise(seconds: Float, volume: Float = 0.3f, decay: Float = 18f): FloatArray {
        val random = Random(42)
        val n = (seconds * SAMPLE_RATE).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            out[i] = (random.nextFloat() * 2f - 1f) * volume * envelope(i, n, t, decay)
        }
        return out
    }

    /** Hängt mehrere Klänge nahtlos aneinander. */
    fun concat(vararg parts: FloatArray): FloatArray {
        val out = FloatArray(parts.sumOf { it.size })
        var offset = 0
        for (part in parts) {
            part.copyInto(out, offset)
            offset += part.size
        }
        return out
    }

    /** Mischt zwei Klänge übereinander (Summe, hart auf [-1, 1] begrenzt). */
    fun mix(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(maxOf(a.size, b.size))
        for (i in out.indices) {
            val sum = (if (i < a.size) a[i] else 0f) + (if (i < b.size) b[i] else 0f)
            out[i] = sum.coerceIn(-1f, 1f)
        }
        return out
    }

    /**
     * Abspielrate für den Treffer-Blip: klettert innerhalb jeder 5er-Stufe
     * eine Pentatonik hinauf (0, 2, 4, 7, 9 Halbtöne) — jeder Lauf spielt
     * so seine eigene kleine Melodie, mit jeder Himmelsstufe geht es
     * wieder von vorn los. Bleibt im SoundPool-Rahmen [0.5, 2.0].
     */
    fun hitRate(score: Int): Float {
        val pentatonic = intArrayOf(0, 2, 4, 7, 9)
        val semitones = pentatonic[((score % 5) + 5) % 5]
        return 2f.pow(semitones / 12f)
    }

    /**
     * Abspielrate für den Perfekt-Sound: Jede Serien-Stufe hebt den
     * Münz-Sound um zwei Halbtöne — die Serie ist hörbar, ohne dass es
     * einen eigenen Zähler auf dem Bildschirm braucht.
     */
    fun perfectRate(streak: Int): Float =
        2f.pow(((streak - 1).coerceIn(0, 4) * 2) / 12f)

    /**
     * Ein Ereignis-Klang aus der Tabelle: Töne hintereinander, Rauschen
     * darüber. Die einzige Stelle, an der aus [Voice] Samples werden —
     * die drei Handports tun exakt dasselbe, damit ein neues Ton-Set
     * nirgends nachgebaut werden muss.
     */
    fun render(voice: Voice): FloatArray {
        val tones = concat(
            *voice.tones.map { t ->
                when {
                    t.fromHz != t.toHz ->
                        sweep(t.fromHz, t.toHz, t.seconds, t.volume, t.decay, t.wave)
                    t.wave == Wave.DREIECK ->
                        triangle(t.fromHz, t.seconds, volume = t.volume, decay = t.decay)
                    else ->
                        square(t.fromHz, t.seconds, volume = t.volume, decay = t.decay, duty = t.duty)
                }
            }.toTypedArray()
        )
        val rausch = voice.noise ?: return tones
        return mix(tones, noise(rausch.seconds, volume = rausch.volume, decay = rausch.decay))
    }

    /**
     * Alle Spiel-Sounds eines Ton-Sets, benannt — die Quelle für
     * GameAudio. Die Schlüssel sind die Ereignisnamen in Kleinschrift
     * ("hit", "perfect", …): Sie stehen so in den Cache-Dateien und in
     * den Abspiel-Schichten aller Ports.
     */
    fun effects(set: SoundSetId): Map<String, FloatArray> =
        SoundEvent.entries.associate { event ->
            event.name.lowercase() to render(SoundBank.voice(set, event))
        }

    /** Das Standard-Set — der Bestand, für Aufrufer ohne eigene Wahl. */
    fun effects(): Map<String, FloatArray> = effects(SoundSetId.KLASSIK)

    /** Verpackt Samples als 16-Bit-Mono-WAV (Little Endian, 44-Byte-Header). */
    fun toWav(samples: FloatArray): ByteArray {
        val dataSize = samples.size * 2
        val out = ByteArray(44 + dataSize)
        var p = 0
        fun bytes(value: String) { for (c in value) out[p++] = c.code.toByte() }
        fun le32(value: Int) {
            out[p++] = (value and 0xFF).toByte()
            out[p++] = ((value shr 8) and 0xFF).toByte()
            out[p++] = ((value shr 16) and 0xFF).toByte()
            out[p++] = ((value shr 24) and 0xFF).toByte()
        }
        fun le16(value: Int) {
            out[p++] = (value and 0xFF).toByte()
            out[p++] = ((value shr 8) and 0xFF).toByte()
        }

        bytes("RIFF"); le32(36 + dataSize); bytes("WAVE")
        bytes("fmt "); le32(16)
        le16(1)                    // PCM
        le16(1)                    // Mono
        le32(SAMPLE_RATE)
        le32(SAMPLE_RATE * 2)      // Byte-Rate
        le16(2)                    // Block Align
        le16(16)                   // Bits pro Sample
        bytes("data"); le32(dataSize)
        for (sample in samples) {
            val s = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
            le16(s and 0xFFFF)
        }
        return out
    }

    /**
     * Rendert eine Welle; [voice] liefert pro Fortschritt Frequenz und
     * Pulsbreite, [shape] die Form.
     *
     * Beide Formen teilen sich Phasenlauf und Hüllkurve — nur die eine
     * Zeile, die aus der Phase einen Wert macht, unterscheidet sie. Das
     * ist der Grund, warum ein zweites Ton-Set kein zweiter Synthesizer
     * ist.
     */
    private inline fun render(
        seconds: Float,
        volume: Float,
        decay: Float,
        shape: Wave,
        voice: (progress: Float) -> Pair<Float, Float>
    ): FloatArray {
        val n = (seconds * SAMPLE_RATE).toInt()
        val out = FloatArray(n)
        var phase = 0f
        for (i in 0 until n) {
            val t = i.toFloat() / SAMPLE_RATE
            val (freq, duty) = voice(if (n > 1) i.toFloat() / (n - 1) else 0f)
            val wave = when (shape) {
                // Unverändert gegenüber der Fassung vor der Dreieckwelle:
                // Der Bestand darf sich nicht um ein Sample verschieben.
                Wave.PULS -> if (phase < duty) 1f else -1f
                // Um eine Viertelperiode verschoben, damit der Ton im
                // Nulldurchgang beginnt und steigt. Ohne die Verschiebung
                // stünde bei Phase 0 der Scheitel, und die Attack-Rampe
                // arbeitete gegen einen Sprung von 0 auf 1.
                Wave.DREIECK -> {
                    val q = (phase + 0.25f) % 1f
                    1f - 4f * abs(q - 0.5f)
                }
            }
            out[i] = wave * volume * envelope(i, n, t, decay)
            phase += freq / SAMPLE_RATE
            if (phase >= 1f) phase -= 1f
        }
        return out
    }

    /** Attack-Rampe, exponentielles Abklingen und End-Fade in einem. */
    private fun envelope(index: Int, total: Int, t: Float, decay: Float): Float {
        val attack = if (index < ATTACK_SAMPLES) index.toFloat() / ATTACK_SAMPLES else 1f
        val remaining = total - index
        val fadeOut = if (remaining < FADE_OUT_SAMPLES) remaining.toFloat() / FADE_OUT_SAMPLES else 1f
        return attack * fadeOut * exp(-decay * t)
    }
}
