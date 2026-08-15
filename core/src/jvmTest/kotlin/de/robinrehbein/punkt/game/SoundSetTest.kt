package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Die Regeln, die ein Ton-Set von einer Nuance trennen. Was hier
 * durchfällt, fällt in allen vier Ports gleichzeitig auf — und zwar erst
 * im Spiel, wo es niemand mehr hört, weil es dort nach einem Fehler des
 * Lautsprechers klingt.
 */
class SoundSetTest {

    private val maxStats = SkinStats(
        bestScore = 999,
        bestPerfectStreak = 99,
        bestDailyStreak = 99,
        runCount = 9_999,
        totalScore = 999_999,
        daysPlayed = 365,
        monthsPlayed = 12
    )

    /** Alle Töne eines Sets, quer über alle Ereignisse. */
    private fun toene(id: SoundSetId): List<Tone> =
        SoundEvent.entries.flatMap { SoundBank.voice(id, it).tones }

    @Test
    fun `jedes Set beschreibt jedes Ereignis mit mindestens einem Ton`() {
        SoundSetId.entries.forEach { id ->
            val voices = SoundBank.of(id).voices
            assertEquals("$id kennt nicht alle Ereignisse", SoundEvent.entries.toSet(), voices.keys)
            SoundEvent.entries.forEach { event ->
                assertTrue(
                    "$id: $event hat keinen Ton",
                    SoundBank.voice(id, event).tones.isNotEmpty()
                )
            }
        }
    }

    @Test
    fun `alle Zahlen liegen in den Grenzen, die der Baukasten hergibt`() {
        SoundSetId.entries.forEach { id ->
            SoundEvent.entries.forEach { event ->
                val voice = SoundBank.voice(id, event)
                voice.tones.forEachIndexed { index, ton ->
                    val wo = "$id/$event Ton $index"
                    listOf(ton.fromHz, ton.toHz).forEach { hz ->
                        assertTrue(
                            "$wo liegt mit $hz Hz außerhalb des Tonumfangs",
                            hz in SoundBank.MIN_HZ..SoundBank.MAX_HZ
                        )
                    }
                    assertTrue(
                        "$wo dauert ${ton.seconds}s",
                        ton.seconds in SoundBank.MIN_SECONDS..SoundBank.MAX_SECONDS
                    )
                    assertTrue(
                        "$wo hat Lautstärke ${ton.volume}",
                        ton.volume in SoundBank.MIN_VOLUME..SoundBank.MAX_VOLUME
                    )
                    assertTrue("$wo klingt mit ${ton.decay} ab", ton.decay in 0f..SoundBank.MAX_DECAY)
                    assertTrue("$wo hat Pulsbreite ${ton.duty}", ton.duty in 0.05f..0.95f)
                }
                voice.noise?.let { rausch ->
                    val wo = "$id/$event Rauschen"
                    assertTrue(
                        "$wo dauert ${rausch.seconds}s",
                        rausch.seconds in SoundBank.MIN_SECONDS..SoundBank.MAX_SECONDS
                    )
                    assertTrue(
                        "$wo hat Lautstärke ${rausch.volume}",
                        rausch.volume in SoundBank.MIN_VOLUME..SoundBank.MAX_VOLUME
                    )
                    assertTrue(
                        "$wo klingt mit ${rausch.decay} ab",
                        rausch.decay in 0f..SoundBank.MAX_DECAY
                    )
                }
            }
        }
    }

    @Test
    fun `ein Gleitton traegt keine erfundene Pulsbreite`() {
        // ChipSynth.sweep kennt keine Pulsbreite und rendert immer mit
        // 0,5. Stünde in der Tabelle etwas anderes, läse es kein Port —
        // und die Zahl behauptete einen Klang, den es nicht gibt.
        SoundSetId.entries.forEach { id ->
            toene(id).filter { it.fromHz != it.toHz }.forEach {
                assertEquals("Gleitton in $id mit fremder Pulsbreite", 0.5f, it.duty, 1e-6f)
            }
        }
    }

    @Test
    fun `kein Set klingt bei einem Ereignis wie ein anderes`() {
        // Der eigentliche Sinn der Sammlung: Ein Set muss sich beim ersten
        // Treffer verraten, nicht erst im Vergleich zweier Aufnahmen.
        // Gemessen am Grundton des Ereignisses — mindestens eine Quarte.
        SoundEvent.entries.forEach { event ->
            SoundSetId.entries.forEach { a ->
                SoundSetId.entries.filter { it != a }.forEach { b ->
                    val hzA = SoundBank.voice(a, event).tones.first().fromHz
                    val hzB = SoundBank.voice(b, event).tones.first().fromHz
                    val verhaeltnis = max(hzA, hzB) / min(hzA, hzB)
                    assertTrue(
                        "$event klingt in $a ($hzA Hz) fast wie in $b ($hzB Hz)",
                        verhaeltnis >= SoundBank.MIN_PITCH_RATIO
                    )
                }
            }
        }
    }

    @Test
    fun `die drei Sets liegen in drei verschiedenen Lagen`() {
        // Nicht nur ereignisweise verschieden, sondern als Ganzes: Die
        // Glocke steht über dem Bestand, der Amboss deutlich darunter.
        fun lage(id: SoundSetId): Float =
            SoundEvent.entries.map { SoundBank.voice(id, it).tones.first().fromHz }.average()
                .toFloat()

        val klassik = lage(SoundSetId.KLASSIK)
        val glocke = lage(SoundSetId.GLOCKE)
        val amboss = lage(SoundSetId.AMBOSS)
        assertTrue(
            "Die GLOCKE ($glocke Hz) muss deutlich über dem Bestand ($klassik Hz) liegen",
            glocke >= klassik * 1.3f
        )
        assertTrue(
            "Der AMBOSS ($amboss Hz) muss deutlich unter dem Bestand ($klassik Hz) liegen",
            klassik >= amboss * 2f
        )
    }

    @Test
    fun `die Glocke klingt nach und zerbricht nie`() {
        // Der Charakter in Zahlen: volle Pulsbreite (rund), langsames
        // Abklingen (singend), kein Rauschen (weich) — auch beim Tod.
        toene(SoundSetId.GLOCKE).forEach {
            assertEquals("Die GLOCKE ist rund, nicht nasal", 0.5f, it.duty, 1e-6f)
            assertTrue("Die GLOCKE klingt nach: ${it.decay}", it.decay <= 5f)
        }
        SoundEvent.entries.forEach {
            assertNull(
                "Im Set GLOCKE zerbricht nichts, auch $it nicht",
                SoundBank.voice(SoundSetId.GLOCKE, it).noise
            )
        }
        // Und der Tod ist der längste Einzelton des Sets, nicht der
        // härteste: Er klingt aus, statt abzureißen.
        val tod = SoundBank.voice(SoundSetId.GLOCKE, SoundEvent.DEATH).tones.single()
        toene(SoundSetId.GLOCKE).filter { it != tod }.forEach {
            assertTrue("Ein Ton der GLOCKE dauert länger als ihr Tod", it.seconds < tod.seconds)
        }
    }

    @Test
    fun `der Amboss schlaegt tief, hart und sparsam`() {
        toene(SoundSetId.AMBOSS).forEach {
            assertTrue("Der AMBOSS bleibt tief: ${it.fromHz} Hz", it.fromHz <= 450f)
            assertTrue("Im AMBOSS hallt nichts nach: ${it.decay}", it.decay >= 6f)
            // Der Gleitton bleibt außen vor: Er hat gar keine Pulsbreite
            // (siehe oben), seine 0,5 ist eine Notiz und kein Klang.
            if (it.fromHz == it.toHz) {
                assertTrue("Der AMBOSS ist dünn, nicht rund: ${it.duty}", it.duty <= 0.25f)
            }
        }
        // Sparsam heißt wörtlich weniger Töne als der Bestand — und die
        // Härte kommt stattdessen aus dem Rauschen.
        assertTrue(
            "Der AMBOSS darf nicht mehr Töne haben als der Bestand",
            toene(SoundSetId.AMBOSS).size < toene(SoundSetId.KLASSIK).size
        )
        assertNotNull(
            "Der Treffer des AMBOSS lebt von seinem Anschlag",
            SoundBank.voice(SoundSetId.AMBOSS, SoundEvent.HIT).noise
        )
        assertNotNull(SoundBank.voice(SoundSetId.AMBOSS, SoundEvent.DEATH).noise)
    }

    @Test
    fun `das KLASSIK-Set ist Ton fuer Ton der Bestand`() {
        // Die Messlatte des ganzen Umbaus: Wer die Umstellung hört, hat
        // sie falsch gemacht. Die Werte stammen aus ChipSynth.effects()
        // vor der Einführung der Ton-Sets.
        val set = SoundBank.of(SoundSetId.KLASSIK)
        assertEquals(
            listOf(Tone(440f, 440f, 0.06f, 0.22f, 20f, 0.5f)),
            set.voices.getValue(SoundEvent.START).tones
        )
        assertEquals(
            listOf(Tone(660f, 660f, 0.07f, 0.38f, 18f, 0.5f)),
            set.voices.getValue(SoundEvent.HIT).tones
        )
        assertEquals(
            listOf(
                Tone(988f, 988f, 0.06f, 0.32f, 12f, 0.5f),
                Tone(1319f, 1319f, 0.16f, 0.38f, 9f, 0.5f)
            ),
            set.voices.getValue(SoundEvent.PERFECT).tones
        )
        assertEquals(
            listOf(
                Tone(880f, 880f, 0.05f, 0.3f, 20f, 0.5f),
                Tone(1175f, 1175f, 0.07f, 0.3f, 18f, 0.5f)
            ),
            set.voices.getValue(SoundEvent.CHAIN).tones
        )
        assertEquals(
            listOf(
                Tone(523f, 523f, 0.07f, 0.3f, 14f, 0.5f),
                Tone(659f, 659f, 0.07f, 0.3f, 14f, 0.5f),
                Tone(784f, 784f, 0.07f, 0.3f, 14f, 0.5f),
                Tone(1046f, 1046f, 0.2f, 0.34f, 8f, 0.5f)
            ),
            set.voices.getValue(SoundEvent.UNLOCK).tones
        )
        assertEquals(
            listOf(
                Tone(784f, 784f, 0.09f, 0.32f, 10f, 0.5f),
                Tone(1046f, 1046f, 0.09f, 0.32f, 10f, 0.5f),
                Tone(1319f, 1319f, 0.09f, 0.32f, 10f, 0.5f),
                Tone(1568f, 1568f, 0.3f, 0.36f, 6f, 0.5f)
            ),
            set.voices.getValue(SoundEvent.RECORD).tones
        )
        val tod = set.voices.getValue(SoundEvent.DEATH)
        assertEquals(listOf(Tone(700f, 90f, 0.35f, 0.42f, 4f, 0.5f)), tod.tones)
        assertEquals(Noise(0.12f, 0.32f, 22f), tod.noise)
        assertEquals(
            listOf(Tone(100f, 100f, 0.09f, 0.5f, 14f, 0.5f)),
            set.voices.getValue(SoundEvent.THUD).tones
        )
    }

    @Test
    fun `der Bestand klingt Sample fuer Sample wie vorher`() {
        // Dieselbe Zusicherung eine Ebene tiefer: Nicht nur die Tabelle,
        // auch der gerenderte Klang muss der alte sein. Die rechte Seite
        // ist der Aufruf, wie er vor den Ton-Sets in effects() stand.
        val effects = ChipSynth.effects(SoundSetId.KLASSIK)
        assertArrayEquals(
            ChipSynth.square(660f, 0.07f, volume = 0.38f, decay = 18f),
            effects.getValue("hit")
        )
        assertArrayEquals(
            ChipSynth.concat(
                ChipSynth.square(988f, 0.06f, volume = 0.32f, decay = 12f),
                ChipSynth.square(1319f, 0.16f, volume = 0.38f, decay = 9f)
            ),
            effects.getValue("perfect")
        )
        assertArrayEquals(
            ChipSynth.mix(
                ChipSynth.sweep(700f, 90f, 0.35f, volume = 0.42f, decay = 4f),
                ChipSynth.noise(0.12f, volume = 0.32f, decay = 22f)
            ),
            effects.getValue("death")
        )
        // Und ohne Angabe bleibt es das Standard-Set.
        assertArrayEquals(effects.getValue("hit"), ChipSynth.effects().getValue("hit"))
    }

    @Test
    fun `jedes Set laesst sich rendern und uebersteuert nicht`() {
        SoundSetId.entries.forEach { id ->
            val effects = ChipSynth.effects(id)
            assertEquals(
                "$id: ein Ereignis fehlt im gerenderten Set",
                SoundEvent.entries.map { it.name.lowercase() }.toSet(),
                effects.keys
            )
            effects.forEach { (name, samples) ->
                assertTrue("$id/$name ist leer", samples.isNotEmpty())
                assertTrue("$id/$name übersteuert", samples.all { abs(it) <= 1f })
                // Attack-Rampe und End-Fade: kein Knacksen an den Rändern.
                assertEquals("$id/$name knackst am Anfang", 0f, samples.first(), 1e-4f)
                assertTrue("$id/$name knackst am Ende", abs(samples.last()) < 0.02f)
            }
        }
    }

    @Test
    fun `die Vorschau-Kachel zeigt die Lage des Sets`() {
        SoundSetId.entries.forEach { id ->
            val chips = SoundBank.chips(id)
            assertEquals("$id braucht drei Balken", 3, chips.size)
            chips.forEach { assertTrue("$id: Balken außerhalb 0..1", it in 0f..1f) }
        }
        // Und die Kachel muss die Sets ebenso auseinanderhalten wie das Ohr.
        val glocke = SoundBank.chips(SoundSetId.GLOCKE)
        val klassik = SoundBank.chips(SoundSetId.KLASSIK)
        val amboss = SoundBank.chips(SoundSetId.AMBOSS)
        glocke.indices.forEach { i ->
            assertTrue("Balken $i: GLOCKE nicht höher als der Bestand", glocke[i] > klassik[i])
            assertTrue("Balken $i: AMBOSS nicht tiefer als der Bestand", amboss[i] < klassik[i])
        }
    }

    @Test
    fun `KLASSIK ist offen, alles andere haengt an Leistung`() {
        val leer = SkinStats(0, 0, 0)
        assertTrue(SoundBank.isUnlocked(SoundSetId.KLASSIK, leer))
        SoundSetId.entries.filter { it != SoundSetId.KLASSIK }.forEach {
            assertFalse("$it darf ohne Leistung nicht offen sein", SoundBank.isUnlocked(it, leer))
        }
        assertEquals(1, SoundBank.unlockedCount(leer))
        assertEquals(SoundSetId.entries.size, SoundBank.unlockedCount(maxStats))
    }

    @Test
    fun `jedes Set haengt an seiner eigenen Achse`() {
        // Können und Ausdauer, wie bei den Kulissen: Wer nur Rekorde
        // jagt, bekommt trotzdem nicht beide Sets.
        assertTrue(SoundBank.isUnlocked(SoundSetId.GLOCKE, SkinStats(0, 20, 0)))
        assertFalse(SoundBank.isUnlocked(SoundSetId.GLOCKE, SkinStats(0, 19, 0)))
        assertTrue(
            SoundBank.isUnlocked(SoundSetId.AMBOSS, SkinStats(0, 0, 0, totalScore = 25_000))
        )
        assertFalse(
            SoundBank.isUnlocked(SoundSetId.AMBOSS, SkinStats(0, 0, 0, totalScore = 24_999))
        )
        // Und es hängt an keinem Kauf: maxStats hat patronOwned = false.
        assertFalse(maxStats.patronOwned)
    }

    @Test
    fun `keine Schwelle stapelt sich auf einer fremden`() {
        // Fiele ein Ton-Set zusammen mit einem Skin oder einer Kulisse,
        // hörte niemand das neue Set — er sähe den neuen Vogel.
        val fremde = Progress.goals(SkinStats(0, 0, 0))
            .filter { it.sound == null }
            .map { it.axis to it.target }
            .toSet()
        Progress.goals(SkinStats(0, 0, 0)).filter { it.sound != null }.forEach { ziel ->
            assertFalse(
                "${ziel.sound} liegt auf derselben Zahl wie ein anderes Ziel",
                (ziel.axis to ziel.target) in fremde
            )
        }
    }

    @Test
    fun `gespeicherte Namen finden zurueck, alles andere landet auf KLASSIK`() {
        SoundSetId.entries.forEach { assertEquals(it, SoundBank.fromName(it.name)) }
        assertEquals(SoundSetId.KLASSIK, SoundBank.fromName(null))
        assertEquals(SoundSetId.KLASSIK, SoundBank.fromName("BLASMUSIK"))
    }

    // ===== Werkzeug =====

    private fun assertArrayEquals(erwartet: FloatArray, ist: FloatArray) {
        assertEquals("andere Länge", erwartet.size, ist.size)
        erwartet.indices.forEach { i ->
            assertEquals("Sample $i weicht ab", erwartet[i], ist[i], 0f)
        }
    }
}
