package de.robinrehbein.punkt

import de.robinrehbein.punkt.game.ChipSynth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ChipSynthTest {

    @Test
    fun `square produces the requested number of samples within bounds`() {
        val samples = ChipSynth.square(440f, 0.1f)
        assertEquals((0.1f * ChipSynth.SAMPLE_RATE).toInt(), samples.size)
        assertTrue(samples.all { abs(it) <= 1f })
    }

    @Test
    fun `tones start silent and fade out to silence`() {
        for (samples in listOf(
            ChipSynth.square(660f, 0.08f),
            ChipSynth.sweep(700f, 90f, 0.2f),
            ChipSynth.noise(0.1f)
        )) {
            // Attack-Rampe: der allererste Sample ist stumm (kein Knacksen).
            assertEquals(0f, samples.first(), 0.0001f)
            // Hüllkurve plus End-Fade: am Ende praktisch Stille.
            assertTrue("Endet nicht leise: ${samples.last()}", abs(samples.last()) < 0.02f)
        }
    }

    @Test
    fun `concat joins parts seamlessly`() {
        val a = ChipSynth.square(440f, 0.05f)
        val b = ChipSynth.square(880f, 0.07f)
        val joined = ChipSynth.concat(a, b)
        assertEquals(a.size + b.size, joined.size)
        assertEquals(b[0], joined[a.size], 0.0001f)
    }

    @Test
    fun `mix overlays and stays within valid range`() {
        val loudA = ChipSynth.square(200f, 0.1f, volume = 0.9f, decay = 0.1f)
        val loudB = ChipSynth.square(207f, 0.15f, volume = 0.9f, decay = 0.1f)
        val mixed = ChipSynth.mix(loudA, loudB)
        assertEquals(loudB.size, mixed.size)
        assertTrue(mixed.all { abs(it) <= 1f })
    }

    @Test
    fun `wav output is a well-formed 16-bit mono file`() {
        val samples = ChipSynth.square(440f, 0.05f)
        val wav = ChipSynth.toWav(samples)

        assertEquals(44 + samples.size * 2, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        // Sample-Rate steht Little-Endian bei Offset 24.
        val rate = (wav[24].toInt() and 0xFF) or
            ((wav[25].toInt() and 0xFF) shl 8) or
            ((wav[26].toInt() and 0xFF) shl 16) or
            ((wav[27].toInt() and 0xFF) shl 24)
        assertEquals(ChipSynth.SAMPLE_RATE, rate)
    }

    @Test
    fun `hit rate climbs a pentatonic ladder and resets each stage`() {
        // Innerhalb einer 5er-Stufe steigt die Tonhöhe streng an ...
        for (step in 0 until 4) {
            assertTrue(ChipSynth.hitRate(step + 1) > ChipSynth.hitRate(step))
        }
        // ... und mit der nächsten Stufe geht es wieder von vorn los.
        assertEquals(ChipSynth.hitRate(0), ChipSynth.hitRate(5), 0.0001f)
        assertEquals(1f, ChipSynth.hitRate(0), 0.0001f)
        // Alles bleibt im SoundPool-Rahmen [0.5, 2.0].
        for (score in 0 until 30) {
            val rate = ChipSynth.hitRate(score)
            assertTrue(rate in 0.5f..2f)
        }
    }

    @Test
    fun `perfect rate rises with the streak and is capped`() {
        assertEquals(1f, ChipSynth.perfectRate(1), 0.0001f)
        assertTrue(ChipSynth.perfectRate(2) > ChipSynth.perfectRate(1))
        assertTrue(ChipSynth.perfectRate(3) > ChipSynth.perfectRate(2))
        // Ab Serie 5 ist Schluss mit dem Anstieg.
        assertEquals(ChipSynth.perfectRate(5), ChipSynth.perfectRate(9), 0.0001f)
        for (streak in 1 until 20) {
            assertTrue(ChipSynth.perfectRate(streak) in 0.5f..2f)
        }
    }

    @Test
    fun `all game effects exist and are non-empty`() {
        val effects = ChipSynth.effects()
        val expected = setOf(
            "start", "hit", "perfect", "chain", "unlock", "record", "death", "thud"
        )
        assertEquals(expected, effects.keys)
        for ((name, samples) in effects) {
            assertTrue("Effekt $name ist leer", samples.isNotEmpty())
            assertTrue("Effekt $name übersteuert", samples.all { abs(it) <= 1f })
        }
    }
}
