package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rahmen und Beiname sind die beiden Aussagen der Score-Karte, die nicht
 * aus dem Lauf kommen, sondern aus allem davor. Beide werden hier an
 * ihren Kanten geprüft: Ein Rahmen, der eine Stufe zu früh springt,
 * verschenkt die Freude, und ein Beiname, der bei zwei erfüllten
 * Bedingungen mal so und mal so ausfällt, ist keine Auszeichnung mehr,
 * sondern Zufall.
 */
class CardStyleTest {

    private val leer = SkinStats(bestScore = 0, bestPerfectStreak = 0, bestDailyStreak = 0)

    /** Ein Spielstand, der jede Bedingung dieser Datei erfüllt. */
    private val alles = SkinStats(
        bestScore = 999,
        bestPerfectStreak = 99,
        bestDailyStreak = 99,
        runCount = 9_999,
        totalScore = 999_999,
        daysPlayed = 365,
        monthsPlayed = 12,
        seasonEarned = 0b1111,
        patronOwned = true
    )

    /**
     * Die Bedingung jedes Beinamens, gespiegelt aus [CardStyle.qualifies].
     * Dass sie hier ein zweites Mal steht, ist der Preis dafür, die Kante
     * überhaupt prüfen zu können — dieselbe Abmachung wie bei
     * `Progress.SKIN_THRESHOLDS`.
     */
    private val schwellen: Map<Epithet, Pair<(SkinStats, Int) -> SkinStats, Int>> = mapOf(
        Epithet.LEGENDE to (::mitBestScore to 80),
        Epithet.UHRWERK to (::mitPerfektserie to 15),
        Epithet.UNBEIRRBAR to (::mitTagesserie to 30),
        Epithet.STEHAUFMAENNCHEN to (::mitLaeufen to 500),
        Epithet.PUNKTESAMMLER to (::mitPunkten to 10_000),
        Epithet.SCHARFSCHUETZE to (::mitPerfektserie to 8),
        Epithet.STAMMGAST to (::mitTagen to 30),
        Epithet.EINGESPIELT to (::mitLaeufen to 25)
    )

    /**
     * Ein Spielstand, dessen Sammlung genau auf [ziel] steht.
     *
     * Gesucht statt gerechnet: Welcher Skin bei welchem Stand fällt, weiß
     * allein [SkinPaint]. Eine hier nachgebaute Formel wäre eine zweite
     * Wahrheit, die beim nächsten neuen Skin still falsch würde — der
     * Test soll die Rahmen prüfen, nicht die Skin-Schwellen kopieren.
     */
    private fun standFuer(ziel: CardFrame): SkinStats {
        var stufe = 0
        while (stufe <= 400) {
            val stand = leer.copy(
                bestScore = stufe,
                bestPerfectStreak = stufe / 8,
                bestDailyStreak = stufe / 6,
                runCount = stufe * 4,
                totalScore = stufe * 200,
                daysPlayed = stufe / 4,
                // Ein Bit je angefangenem Dutzend Stufen: Die Maske muss
                // wachsen, nicht bloß größer werden.
                monthsPlayed = (1 shl ((stufe / 12).coerceAtMost(12))) - 1
            )
            if (CardStyle.frame(stand) == ziel) return stand
            stufe++
        }
        throw AssertionError("Kein Spielstand gefunden, der auf $ziel steht")
    }

    private fun mitBestScore(s: SkinStats, v: Int) = s.copy(bestScore = v)
    private fun mitPerfektserie(s: SkinStats, v: Int) = s.copy(bestPerfectStreak = v)
    private fun mitTagesserie(s: SkinStats, v: Int) = s.copy(bestDailyStreak = v)
    private fun mitLaeufen(s: SkinStats, v: Int) = s.copy(runCount = v)
    private fun mitPunkten(s: SkinStats, v: Int) = s.copy(totalScore = v)
    private fun mitTagen(s: SkinStats, v: Int) = s.copy(daysPlayed = v)

    // ===== Rahmen =====

    @Test
    fun `jede Rahmenstufe faellt genau an ihrer Schwelle`() {
        assertEquals(3, CardStyle.FRAME_STEPS.size)
        assertEquals(CardFrame.entries.size, CardStyle.FRAME_STEPS.size + 1)
        CardStyle.FRAME_STEPS.forEachIndexed { i, schwelle ->
            assertEquals(
                "bei $schwelle gesammelten Skins steht Stufe ${i + 1}",
                CardFrame.entries[i + 1], CardStyle.frame(schwelle)
            )
            assertEquals(
                "bei ${schwelle - 1} steht noch Stufe $i",
                CardFrame.entries[i], CardStyle.frame(schwelle - 1)
            )
        }
    }

    @Test
    fun `ohne Sammlung der schlichte Rand, mit voller Sammlung die Pracht`() {
        assertEquals(CardFrame.SCHLICHT, CardStyle.frame(0))
        assertEquals(CardFrame.SCHLICHT, CardStyle.frame(leer))
        assertEquals(CardFrame.PRACHT, CardStyle.frame(SkinPaint.collectableCount()))
        assertEquals(CardFrame.PRACHT, CardStyle.frame(alles))
    }

    @Test
    fun `die Stufe steigt nie wieder ab`() {
        var vorher = CardFrame.SCHLICHT
        for (n in 0..SkinPaint.collectableCount()) {
            val jetzt = CardStyle.frame(n)
            assertTrue("Stufe faellt bei $n zurueck", jetzt.ordinal >= vorher.ordinal)
            vorher = jetzt
        }
    }

    @Test
    fun `ohne Wahl traegt die Karte die hoechste verdiente Stufe`() {
        // Der wichtigste Fall der ganzen Wahl: Sie darf niemandem etwas
        // wegnehmen. Wer die Auswahl nie anfasst, bekommt genau das, was
        // er vorher auch bekam — sonst wäre die Einführung der Wahl für
        // jeden bestehenden Spielstand ein stiller Rückschritt.
        CardFrame.entries.forEach { erwartet ->
            val stand = standFuer(erwartet)
            assertEquals(erwartet, CardStyle.frame(null, stand))
            assertEquals(CardStyle.frame(stand), CardStyle.frame(null, stand))
        }
    }

    @Test
    fun `eine verdiente Wahl gewinnt gegen die Vorgabe`() {
        // Der Sinn der Sache: absteigen dürfen. Wer die Pracht hat, darf
        // trotzdem schlicht teilen.
        val voll = alles
        CardFrame.entries.forEach { gewaehlt ->
            assertEquals(
                "$gewaehlt ist verdient und muss gelten",
                gewaehlt, CardStyle.frame(gewaehlt, voll)
            )
        }
    }

    @Test
    fun `eine ungedeckte Wahl verliert gegen den Spielstand`() {
        // Kann beim Abgleich mit einem weiteren Geraet entstehen, dessen
        // Stand weiter war, oder beim Zuruecklesen eines Backups. Die
        // Karte darf nie einen Rahmen tragen, den ihr Stand nicht deckt.
        val nichts = leer
        assertEquals(CardFrame.SCHLICHT, CardStyle.frame(CardFrame.PRACHT, nichts))
        assertEquals(CardFrame.SCHLICHT, CardStyle.frame(CardFrame.ZINNEN, nichts))

        val mittig = standFuer(CardFrame.ZINNEN)
        assertEquals(CardFrame.ZINNEN, CardStyle.frame(CardFrame.PRACHT, mittig))
        // Was darunter liegt, bleibt aber erlaubt.
        assertEquals(CardFrame.SCHLICHT, CardStyle.frame(CardFrame.SCHLICHT, mittig))
    }

    @Test
    fun `die Stufen bauen aufeinander auf`() {
        // Anders als Skins und Ton-Sets haengen die Rahmen an einer
        // einzigen Achse. Wer Stufe drei hat, hat auch Stufe zwei — eine
        // eigene Schwellenliste je Stufe waere hier eine Luege.
        CardFrame.entries.forEach { stand ->
            val stats = standFuer(stand)
            CardFrame.entries.forEach { frage ->
                assertEquals(
                    "bei Stand $stand muss $frage ${if (frage.ordinal <= stand.ordinal) "offen" else "zu"} sein",
                    frage.ordinal <= stand.ordinal,
                    CardStyle.isUnlocked(frage, stats)
                )
            }
            assertEquals(stand.ordinal + 1, CardStyle.unlockedCount(stats))
        }
    }

    @Test
    fun `gespeicherte Namen finden zurueck, alles andere ist keine Wahl`() {
        CardFrame.entries.forEach {
            assertEquals(it, CardStyle.fromName(it.name))
        }
        // Kein Fallback auf die erste Stufe: "unbekannt" heisst "nie
        // gewaehlt", und das ist etwas anderes als SCHLICHT.
        assertNull(CardStyle.fromName(null))
        assertNull(CardStyle.fromName(""))
        assertNull(CardStyle.fromName("GOLDRAHMEN"))
    }

    @Test
    fun `der Rahmen haengt am gezaehlten Bestand, nicht an der Zahl daneben`() {
        val stand = SkinStats(bestScore = 45, bestPerfectStreak = 8, bestDailyStreak = 7)
        assertEquals(CardStyle.frame(SkinPaint.unlockedCount(stand)), CardStyle.frame(stand))
    }

    /**
     * Ein gekaufter Rahmen wäre etwas anderes als ein verdienter — deshalb
     * darf das Gönner-Paket die Stufe nicht bewegen. Dasselbe gilt für die
     * Saison-Skins, die am Kalender hängen und nicht am Können.
     */
    @Test
    fun `Goenner- und Saison-Skins bewegen den Rahmen nicht`() {
        for (score in 0..90 step 5) {
            val stand = SkinStats(
                bestScore = score,
                bestPerfectStreak = score / 6,
                bestDailyStreak = score / 4,
                runCount = score * 6,
                totalScore = score * 200,
                daysPlayed = score
            )
            assertEquals(
                "Score $score: Gönner-Paket verschiebt den Rahmen",
                CardStyle.frame(stand), CardStyle.frame(stand.copy(patronOwned = true))
            )
            assertEquals(
                "Score $score: Saison-Bits verschieben den Rahmen",
                CardStyle.frame(stand), CardStyle.frame(stand.copy(seasonEarned = 0b1111))
            )
        }
    }

    // ===== Beinamen =====

    @Test
    fun `zu jedem Beinamen steht eine Bedingung in der Tabelle`() {
        assertEquals(Epithet.entries.size, schwellen.size)
        assertTrue("sechs bis acht Beinamen", Epithet.entries.size in 6..8)
    }

    @Test
    fun `jede Bedingung traegt ihren Beinamen und faellt eine Stufe darunter nicht`() {
        schwellen.forEach { (beiname, regel) ->
            val (setzen, schwelle) = regel
            assertTrue(
                "$beiname greift an seiner Schwelle",
                CardStyle.qualifies(beiname, setzen(leer, schwelle))
            )
            assertTrue(
                "$beiname greift unter seiner Schwelle",
                !CardStyle.qualifies(beiname, setzen(leer, schwelle - 1))
            )
            // Auf der Kante gewinnt er auch die Auswahl: Höher gereihte
            // Beinamen hängen an höheren Zahlen derselben oder einer
            // anderen Achse, also kann ihn hier keiner überholen.
            assertEquals(
                "$beiname wird an seiner Schwelle auch getragen",
                beiname, CardStyle.epithet(setzen(leer, schwelle))
            )
            assertNotEquals(
                "$beiname wird eine Stufe darunter nicht getragen",
                beiname, CardStyle.epithet(setzen(leer, schwelle - 1))
            )
        }
    }

    @Test
    fun `in den ersten Laeufen traegt niemand einen Beinamen`() {
        assertNull(CardStyle.epithet(leer))
        assertNull(CardStyle.epithet(leer.copy(runCount = 24, bestScore = 9)))
    }

    /**
     * Der Kern der Sache: Wer lange spielt, erfüllt mehrere Bedingungen
     * gleichzeitig. Getragen wird immer der oberste Eintrag — nie der
     * zuletzt erreichte, nie der zufällig erste in einer Schleife.
     */
    @Test
    fun `bei mehreren erfuellten Bedingungen gewinnt immer der oberste Eintrag`() {
        assertSame(Epithet.entries.first(), CardStyle.epithet(alles))

        // Paarweise: Erfüllt ein Stand die Bedingungen zweier Beinamen,
        // trägt er den mit dem kleineren Rang.
        Epithet.entries.forEach { a ->
            Epithet.entries.forEach { b ->
                if (a == b) return@forEach
                val (setzenA, schwelleA) = schwellen.getValue(a)
                val (setzenB, schwelleB) = schwellen.getValue(b)
                val beide = setzenB(setzenA(leer, schwelleA), schwelleB)
                if (!CardStyle.qualifies(a, beide) || !CardStyle.qualifies(b, beide)) {
                    // Zwei Bedingungen auf derselben Achse schließen sich
                    // aus (die kleinere Zahl überschreibt die größere) —
                    // dann gibt es hier nichts zu entscheiden.
                    return@forEach
                }
                val erwartet = if (a.ordinal < b.ordinal) a else b
                assertEquals("$a gegen $b", erwartet, CardStyle.epithet(beide))
            }
        }
    }

    @Test
    fun `die Auswahl ist immer der erste passende Eintrag der Aufzaehlung`() {
        // Ein grobes Gitter über alle sechs Achsen: Was die Auswahl
        // liefert, muss Eintrag für Eintrag nachrechenbar sein.
        for (a in 0..100 step 20) {
            for (b in 0..20 step 5) {
                for (c in 0..600 step 150) {
                    val stand = SkinStats(
                        bestScore = a,
                        bestPerfectStreak = b,
                        bestDailyStreak = b * 2,
                        runCount = c,
                        totalScore = c * 25,
                        daysPlayed = c / 10
                    )
                    assertEquals(
                        Epithet.entries.firstOrNull { CardStyle.qualifies(it, stand) },
                        CardStyle.epithet(stand)
                    )
                }
            }
        }
    }

    // ===== Texte =====

    @Test
    fun `jeder Beiname hat einen deutschen und einen englischen Namen`() {
        Epithet.entries.forEach { beiname ->
            assertTrue("$beiname ohne deutschen Namen", beiname.de.isNotBlank())
            assertTrue("$beiname ohne englischen Namen", beiname.en.isNotBlank())
            assertEquals(beiname.de, CardStyle.label(beiname, german = true))
            assertEquals(beiname.en, CardStyle.label(beiname, german = false))
        }
        assertEquals(
            "zwei Beinamen teilen sich einen deutschen Namen",
            Epithet.entries.size, Epithet.entries.map { it.de }.toSet().size
        )
        assertEquals(
            "zwei Beinamen teilen sich einen englischen Namen",
            Epithet.entries.size, Epithet.entries.map { it.en }.toSet().size
        )
    }

    /**
     * Die Pixelschrift der Karte kennt keine Umlaute und kein ß — und sie
     * kennt keine Kleinbuchstaben. Ein Titel, der das verletzt, fällt auf
     * der Karte als Lücke auf, nicht im Code.
     */
    @Test
    fun `deutsche Beinamen kommen ohne Umlaute aus`() {
        Epithet.entries.forEach { beiname ->
            assertTrue(
                "$beiname schreibt Umlaute aus",
                !beiname.de.any { it in "ÄÖÜäöüß" }
            )
            listOf(beiname.de, beiname.en).forEach { text ->
                assertEquals("$beiname steht nicht in Versalien", text.uppercase(), text)
                assertTrue("$beiname ist zu lang fuer die Karte", text.length <= 16)
            }
        }
    }

    @Test
    fun `SCHLICHT ist Pixel fuer Pixel der Bestand`() {
        // Dieselbe Regel wie bei der WIESE unter den Kulissen: Eine
        // Sammlung faengt bei dem an, was schon da war. Sonst aendert sich
        // die geteilte Karte fuer alle, die nichts gesammelt haben — und
        // niemand kann sagen, ob das Absicht war oder ein Versehen.
        //
        // Die Zahlen stammen aus der Karte vor den Rahmen (ScoreCard.kt
        // in f1afb57). Wer sie hier aendert, aendert fremde Karten.
        val schlicht = CardStyle.layout(CardFrame.SCHLICHT)
        assertEquals(0.14f, schlicht.title, 0f)
        assertEquals(130f, schlicht.titleSize, 0f)
        assertEquals(0.20f, schlicht.subline, 0f)
        assertEquals(56f, schlicht.sublineSize, 0f)
        assertEquals(0.32f, schlicht.dot, 0f)
        assertEquals(110f, schlicht.dotRadius, 0f)
        assertEquals(0.945f, schlicht.challenge, 0f)
    }

    @Test
    fun `nur SCHLICHT traegt die Masse des Bestands`() {
        // Die Gegenprobe: Wuerden alle Stufen dasselbe Layout benutzen,
        // ginge der Test oben durch, ohne irgendetwas zu sichern.
        CardFrame.entries.filter { it != CardFrame.SCHLICHT }.forEach {
            val andere = CardStyle.layout(it)
            assertNotEquals(
                "$it darf nicht die Masse des Bestands tragen — sein Rahmen ist breiter",
                CardStyle.layout(CardFrame.SCHLICHT),
                andere
            )
            assertTrue("$it muss den Inhalt nach innen ruecken", andere.title > CardStyle.PLAIN_TITLE)
            assertTrue("$it muss die Aufforderung hochziehen", andere.challenge < CardStyle.PLAIN_CHALLENGE)
        }
    }
}
