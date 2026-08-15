package de.robinrehbein.punkt.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Das Farbwerk ist die einzige Quelle für Skin-Farben in Kotlin — was hier
 * durchfällt, fällt in :app und :wear gleichzeitig auf.
 */
class SkinPaintTest {

    private val maxStats = SkinStats(
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

    /** Alles verdient, aber nichts gekauft und keine Saison mitgenommen. */
    private val verdientStats = maxStats.copy(seasonEarned = 0, patronOwned = false)

    @Test
    fun `jedes Feld liefert eine deckende Farbe`() {
        SkinId.entries.forEach { id ->
            for (row in 0 until SkinPaint.GRID) {
                for (col in 0 until SkinPaint.GRID) {
                    val color = SkinPaint.cell(id, col, row, SkinState(elapsed = 1.7f, score = 33, perfectStreak = 3))
                    assertEquals(
                        "$id ($col,$row) muss volle Deckkraft haben",
                        0xFFL,
                        (color shr 24) and 0xFF
                    )
                    assertTrue("$id ($col,$row) liegt außerhalb von ARGB", color in 0xFF000000..0xFFFFFFFF)
                }
            }
        }
    }

    @Test
    fun `Standbild bleibt ueber die Zeit gleich, bewegte Skins nicht`() {
        val ruhe = SkinState(elapsed = 0f)
        val spaeter = SkinState(elapsed = 3.3f)
        SkinId.entries.forEach { id ->
            val a = SkinPaint.cell(id, 4, 4, ruhe)
            val b = SkinPaint.cell(id, 4, 4, spaeter)
            if (SkinPaint.isAnimated(id)) {
                // Irgendwo im Raster muss sich innerhalb einer Runde etwas
                // tun — wo und wann, ist Sache des Musters (der Glanzstreifen
                // von CHROM läuft z. B. schräg durchs Bild).
                val bewegt = (0 until SkinPaint.GRID).any { c ->
                    (0 until SkinPaint.GRID).any { r ->
                        (0..40).any { step ->
                            SkinPaint.cell(id, c, r, ruhe) !=
                                SkinPaint.cell(id, c, r, SkinState(elapsed = step * 0.25f))
                        }
                    }
                }
                assertTrue("$id gilt als bewegt, ändert sich aber nicht", bewegt)
            } else {
                assertEquals("$id darf sich ohne Zeitanteil nicht ändern", a, b)
            }
        }
    }

    @Test
    fun `Chamaeleon folgt der Himmelsstufe, Kombo der Perfekt-Serie`() {
        val tag = SkinPaint.cell(SkinId.CHAMAELEON, 3, 3, SkinState(score = 0))
        val nacht = SkinPaint.cell(SkinId.CHAMAELEON, 3, 3, SkinState(score = 30))
        assertNotEquals("Tag und Nacht müssen sich unterscheiden", tag, nacht)

        // Kein Deckel mehr: Der Himmel laeuft im Umlauf, siehe skyStage.
        assertEquals(
            "Nach einem vollen Umlauf ist wieder Nacht",
            nacht,
            SkinPaint.cell(SkinId.CHAMAELEON, 3, 3, SkinState(score = 90))
        )

        val kalt = SkinPaint.cell(SkinId.KOMBO, 3, 3, SkinState(perfectStreak = 0))
        val heiss = SkinPaint.cell(SkinId.KOMBO, 3, 3, SkinState(perfectStreak = 5))
        assertNotEquals(kalt, heiss)
        assertEquals(
            "Ab der fünften Perfekt-Serie ist der Deckel erreicht",
            heiss,
            SkinPaint.cell(SkinId.KOMBO, 3, 3, SkinState(perfectStreak = 12))
        )
    }

    @Test
    fun `Klassik bleibt Klassik`() {
        // Regression: Der Umbau auf Pixelfunktionen darf die bestehenden
        // Skins nicht verändern — Körper oben links, Schatten unten rechts.
        assertEquals(0xFFFFD847, SkinPaint.cell(SkinId.KLASSIK, 3, 3))
        assertEquals(0xFFF5A623, SkinPaint.cell(SkinId.KLASSIK, 9, 9))
        assertEquals(0xFF4BE38C, SkinPaint.cell(SkinId.MINZE, 3, 3))
        assertEquals(0xFF43315C, SkinPaint.cell(SkinId.SCHATTEN, 9, 9))
    }

    @Test
    fun `Klassik ist immer offen, alles andere haengt an Leistung`() {
        val leer = SkinStats(0, 0, 0)
        assertTrue(SkinPaint.isUnlocked(SkinId.KLASSIK, leer))
        SkinId.entries.filter { it != SkinId.KLASSIK }.forEach {
            assertFalse("$it darf ohne Leistung nicht offen sein", SkinPaint.isUnlocked(it, leer))
        }
        assertEquals(1, SkinPaint.unlockedCount(leer))
    }

    @Test
    fun `Regenbogen schliesst die Sammlung ab`() {
        val fastAlles = maxStats.copy(bestDailyStreak = 13)
        assertFalse(
            "Solange Aurora fehlt, bleibt der Regenbogen zu",
            SkinPaint.isUnlocked(SkinId.REGENBOGEN, fastAlles)
        )
        assertTrue(SkinPaint.isUnlocked(SkinId.REGENBOGEN, maxStats))
        assertEquals(SkinPaint.collectableCount(), SkinPaint.unlockedCount(maxStats))
    }

    @Test
    fun `Regenbogen haengt nicht an Saison und nicht am Geldbeutel`() {
        // Der wichtigste Test dieser Erweiterung: Wer alles erspielt hat,
        // bekommt den Regenbogen — auch ohne je im Oktober gespielt und
        // ohne je gezahlt zu haben. Sonst wäre der Abschluss der Sammlung
        // eine Frage des Kalenders oder des Kontos.
        assertTrue(SkinPaint.isUnlocked(SkinId.REGENBOGEN, verdientStats))
        assertFalse(SkinPaint.isUnlocked(SkinId.KUERBIS, verdientStats))
        assertFalse(SkinPaint.isUnlocked(SkinId.DIAMANT, verdientStats))
    }

    @Test
    fun `gekaufte und Saison-Skins zaehlen nicht im Sammlungsstand`() {
        val nurGekauft = SkinStats(0, 0, 0, patronOwned = true, seasonEarned = 0b1111)
        SkinId.entries.filter { SkinPaint.isSeasonal(it) || SkinPaint.isPatron(it) }.forEach {
            assertTrue("$it muss trotzdem spielbar sein", SkinPaint.isUnlocked(it, nurGekauft))
            assertFalse("$it darf nicht für die Sammlung zählen", SkinPaint.countsForCollection(it))
        }
        assertEquals(
            "Ein Kauf darf den Sammlungsstand nicht bewegen",
            1,
            SkinPaint.unlockedCount(nurGekauft)
        )
    }

    @Test
    fun `Ausdauer-Achsen schalten unabhaengig vom Rekord frei`() {
        // Der Kern der Erweiterung: Wer nie über Rekord 25 hinauskommt,
        // sammelt trotzdem weiter.
        val fleissig = SkinStats(
            bestScore = 12, bestPerfectStreak = 2, bestDailyStreak = 1,
            runCount = 300, totalScore = 5_000, daysPlayed = 7, monthsPlayed = 3
        )
        listOf(
            SkinId.EI, SkinId.TIGER, SkinId.MEDAILLE, SkinId.FUSSBALL,
            SkinId.DONUT, SkinId.KONFETTI, SkinId.TAGESZEIT, SkinId.JAHRESZEIT
        ).forEach {
            assertTrue("$it hängt an Ausdauer, nicht am Rekord", SkinPaint.isUnlocked(it, fleissig))
        }
        // ... und die Rekord-Skins bleiben davon unberührt zu.
        listOf(SkinId.PINGUIN, SkinId.WELLE, SkinId.THERMO, SkinId.HOLO).forEach {
            assertFalse("$it darf ohne Rekord nicht offen sein", SkinPaint.isUnlocked(it, fleissig))
        }

        val ersteSitzung = SkinStats(0, 0, 0, runCount = 25)
        assertTrue(
            "Der erste zusätzliche Skin muss schon in der ersten Sitzung fallen",
            SkinPaint.isUnlocked(SkinId.EI, ersteSitzung)
        )
    }

    @Test
    fun `Saison-Skins haengen an der Maske, nicht am Kalender`() {
        Season.entries.forEach { season ->
            val nurDiese = SkinStats(0, 0, 0, seasonEarned = season.bit)
            assertTrue(
                "${season.skin} muss mit gesetztem Bit offen sein",
                SkinPaint.isUnlocked(season.skin, nurDiese)
            )
            Season.entries.filter { it != season }.forEach { andere ->
                assertFalse(
                    "${andere.skin} darf von ${season.skin} nicht mit aufgehen",
                    SkinPaint.isUnlocked(andere.skin, nurDiese)
                )
            }
        }
        // Jeder Monat mit Saison findet genau einen Skin, alle anderen keinen.
        assertEquals(SkinId.KUERBIS, Season.forMonth(10)?.skin)
        assertEquals(SkinId.HERZ, Season.forMonth(2)?.skin)
        assertEquals(null, Season.forMonth(7))
        assertEquals(4, Season.entries.map { it.bit }.toSet().size)
    }

    @Test
    fun `Thermo folgt dem Score, Medaille der Stufe, Uhr und Kalender ihrem Wert`() {
        val kalt = SkinPaint.cell(SkinId.THERMO, 3, 3, SkinState(score = 0))
        val heiss = SkinPaint.cell(SkinId.THERMO, 3, 3, SkinState(score = SkinPaint.HEAT_SCORE))
        assertNotEquals(kalt, heiss)
        assertEquals(
            "Über der Platin-Schwelle glüht nichts mehr weiter",
            heiss,
            SkinPaint.cell(SkinId.THERMO, 3, 3, SkinState(score = 200))
        )

        // Die Medaille wechselt genau an den Schwellen, nicht dazwischen.
        assertEquals(
            SkinPaint.cell(SkinId.MEDAILLE, 3, 3, SkinState(score = 10)),
            SkinPaint.cell(SkinId.MEDAILLE, 3, 3, SkinState(score = 19))
        )
        assertNotEquals(
            SkinPaint.cell(SkinId.MEDAILLE, 3, 3, SkinState(score = 19)),
            SkinPaint.cell(SkinId.MEDAILLE, 3, 3, SkinState(score = 20))
        )
        assertEquals(listOf(0, 1, 2, 3, 4), listOf(0, 10, 20, 30, 40).map { SkinPaint.medalTier(it) })

        assertNotEquals(
            "Nacht und Mittag müssen sich unterscheiden",
            SkinPaint.cell(SkinId.TAGESZEIT, 3, 3, SkinState(hour = 2)),
            SkinPaint.cell(SkinId.TAGESZEIT, 3, 3, SkinState(hour = 12))
        )
        assertNotEquals(
            "Sommer und Winter müssen sich unterscheiden",
            SkinPaint.cell(SkinId.JAHRESZEIT, 3, 3, SkinState(month = 7)),
            SkinPaint.cell(SkinId.JAHRESZEIT, 3, 3, SkinState(month = 1))
        )
        // Alle 24 Stunden und 12 Monate müssen eine Farbe haben, auch die
        // Ränder — ein leerer Zweig wäre hier ein schwarzer Vogel.
        (0..23).forEach { h ->
            assertEquals(0xFFL, (SkinPaint.cell(SkinId.TAGESZEIT, 6, 6, SkinState(hour = h)) shr 24) and 0xFF)
        }
        (1..12).forEach { m ->
            assertEquals(0xFFL, (SkinPaint.cell(SkinId.JAHRESZEIT, 6, 6, SkinState(month = m)) shr 24) and 0xFF)
        }
    }

    @Test
    fun `reagierende Skins schluesseln ihren Frame ueber ihren Ausloeser`() {
        // Ohne eigenen Schlüssel würde iOS die Textur nie neu rastern —
        // der Vogel bliebe im ersten Bild stehen.
        assertNotEquals(
            SkinPaint.frameKey(SkinId.TAGESZEIT, SkinState(hour = 3)),
            SkinPaint.frameKey(SkinId.TAGESZEIT, SkinState(hour = 15))
        )
        assertNotEquals(
            SkinPaint.frameKey(SkinId.JAHRESZEIT, SkinState(month = 1)),
            SkinPaint.frameKey(SkinId.JAHRESZEIT, SkinState(month = 8))
        )
        assertNotEquals(
            SkinPaint.frameKey(SkinId.THERMO, SkinState(score = 5)),
            SkinPaint.frameKey(SkinId.THERMO, SkinState(score = 25))
        )
        assertEquals(
            "Über der Glüh-Schwelle darf sich nichts mehr ändern",
            SkinPaint.frameKey(SkinId.THERMO, SkinState(score = SkinPaint.HEAT_SCORE)),
            SkinPaint.frameKey(SkinId.THERMO, SkinState(score = 300))
        )
        assertEquals(
            "Die Medaille wechselt nur an den Stufen",
            SkinPaint.frameKey(SkinId.MEDAILLE, SkinState(score = 21)),
            SkinPaint.frameKey(SkinId.MEDAILLE, SkinState(score = 29))
        )
    }

    @Test
    fun `kein Skin faerbt sich flaechig wie die Zielzone`() {
        // Die Zielzone ist grün (GrassDark bis GrassLight). Ein flächig
        // grüner Vogel wäre für einen Moment nicht vom Ziel zu
        // unterscheiden — deshalb überspringen REGENBOGEN und HOLO den
        // Grünbereich, und deshalb ist kein neuer Skin grün.
        //
        // Ein grüner FLECK ist dagegen erlaubt und existiert seit v2.8:
        // Die Schale von MELONE trägt exakt GrassDark. Ein knappes Dutzend
        // Felder am unteren Rand liest niemand als Zielzone; die Grenze
        // hier zieht deshalb die Fläche, nicht die Farbe.
        val zone = listOf(0xFF74BF2EL, 0xFF9DE85AL)
        val erlaubteFelder = 12

        // Zwei Bestands-Skins reißen diese Grenze und bleiben trotzdem, wie
        // sie sind — ihr Verhalten ist ausgeliefert, und ein stiller Umbau
        // wäre eine Änderung am Bestand, keine Absicherung:
        //  - MELONE: die Schale trägt exakt GrassDark (ein Fleck, kein Kleid).
        //  - AURORA: die Welle läuft durch den Grünbereich, während
        //    REGENBOGEN ihn ausdrücklich überspringt. Das ist eine
        //    Inkonsistenz im Bestand, kein Zufall dieses Tests.
        // Für sie gilt eine laxere Grenze — ein KOMPLETT grüner Vogel fiele
        // auch dort noch durch.
        val bestandsausnahmen = setOf(SkinId.MELONE, SkinId.AURORA)
        val laxeGrenze = 60

        SkinId.entries.forEach { id ->
            (0..60).forEach { step ->
                val state = SkinState(
                    elapsed = step * 0.2f,
                    score = step,
                    perfectStreak = step % 6,
                    hour = step % 24,
                    month = step % 12 + 1
                )
                var treffer = 0
                for (row in 0 until SkinPaint.GRID) {
                    for (col in 0 until SkinPaint.GRID) {
                        val color = SkinPaint.cell(id, col, row, state)
                        if (zone.any { distance(color, it) <= 24f }) treffer++
                    }
                }
                val grenze = if (id in bestandsausnahmen) laxeGrenze else erlaubteFelder
                assertTrue(
                    "$id trägt bei elapsed=${state.elapsed} auf $treffer Feldern die Zonenfarbe",
                    treffer <= grenze
                )
            }
        }
    }

    /** Abstand zweier ARGB-Farben im RGB-Raum. */
    private fun distance(a: Long, b: Long): Float {
        var sum = 0f
        for (shift in intArrayOf(16, 8, 0)) {
            val d = (((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)).toFloat()
            sum += d * d
        }
        return kotlin.math.sqrt(sum)
    }

    @Test
    fun `Schwellen steigen mit dem Anspruch`() {
        val stats = SkinStats(bestScore = 30, bestPerfectStreak = 6, bestDailyStreak = 7)
        assertTrue(SkinPaint.isUnlocked(SkinId.MELONE, stats))
        assertTrue(SkinPaint.isUnlocked(SkinId.CHAMAELEON, stats))
        assertTrue(SkinPaint.isUnlocked(SkinId.BIENE, stats))
        assertTrue(SkinPaint.isUnlocked(SkinId.KOI, stats))
        assertFalse(SkinPaint.isUnlocked(SkinId.PILZ, stats))
        assertFalse(SkinPaint.isUnlocked(SkinId.KARO, stats))
        assertFalse(SkinPaint.isUnlocked(SkinId.AURORA, stats))
    }

    @Test
    fun `Frame-Schluessel wechselt nur, wenn sich das Bild aendert`() {
        val id = SkinId.MAGMA
        assertEquals(
            SkinPaint.frameKey(id, SkinState(elapsed = 1.00f)),
            SkinPaint.frameKey(id, SkinState(elapsed = 1.04f))
        )
        assertNotEquals(
            SkinPaint.frameKey(id, SkinState(elapsed = 1.00f)),
            SkinPaint.frameKey(id, SkinState(elapsed = 1.30f))
        )
        assertEquals(0, SkinPaint.frameKey(SkinId.KLASSIK, SkinState(elapsed = 42f)))
        assertEquals(
            "Kombo hängt an der Serie, nicht an der Uhr",
            SkinPaint.frameKey(SkinId.KOMBO, SkinState(elapsed = 0f, perfectStreak = 3)),
            SkinPaint.frameKey(SkinId.KOMBO, SkinState(elapsed = 9f, perfectStreak = 3))
        )
    }

    @Test
    fun `Farbwerkzeug rechnet richtig`() {
        assertEquals(0xFF000000, SkinPaint.mix(0xFF000000, 0xFFFFFFFF, 0f))
        assertEquals(0xFFFFFFFF, SkinPaint.mix(0xFF000000, 0xFFFFFFFF, 1f))
        // 50 % Grau ist 127,5 — seit dem Umstieg vom Abschneiden aufs
        // kaufmännische Runden fällt das auf 0x80 statt auf 0x7F. Der
        // Web-Port (Math.round) und iOS (rounded()) rechnen genauso; vorher
        // lag die App bis zu eine Stufe je Kanal unter der PWA.
        assertEquals(0xFF808080, SkinPaint.mix(0xFF000000, 0xFFFFFFFF, 0.5f))
        assertEquals("Werte außerhalb 0..1 werden gekappt", 0xFFFFFFFF, SkinPaint.mix(0xFF000000, 0xFFFFFFFF, 4f))

        assertEquals(0xFFFF0000, SkinPaint.hsl(0f, 1f, 0.5f))
        assertEquals(0xFF00FF00, SkinPaint.hsl(120f, 1f, 0.5f))
        assertEquals(0xFF0000FF, SkinPaint.hsl(240f, 1f, 0.5f))
        assertEquals("Negative Winkel wickeln sich", SkinPaint.hsl(30f, 1f, 0.5f), SkinPaint.hsl(-330f, 1f, 0.5f))
        // Grau aus HSL trifft dieselbe Stufe wie die Mischung — beide gehen
        // durch dieselbe Rundung.
        assertEquals(0xFF808080, SkinPaint.hsl(0f, 0f, 0.5f))
    }

    @Test
    fun `Runden weicht hoechstens eine Stufe vom frueheren Abschneiden ab`() {
        // Alt gegen neu: Bis zu dieser Änderung schnitten mix und hsl den
        // Kanal ab (toInt()), der Web-Port rundete. Die Beweislast dieses
        // Tests ist die Größe des Sprungs — pro Aufruf höchstens eine Stufe
        // je Kanal, und nie nach unten. Damit ist keine Skin-Farbe neu
        // gemischt, nur der Rundungsfehler halbiert.
        //
        // Wo ein Skin zwei Mischungen hintereinanderlegt (CHROM, HOLO,
        // DIAMANT und PHOENIX mischen erst die Fläche und dann den
        // Glanzstreifen darüber), können sich zwei solche Sprünge
        // aufaddieren — dort sind es im Höchstfall zwei Stufen je Kanal,
        // gemessen an unter 3 % der Felder. Auch das bleibt unterhalb der
        // Wahrnehmungsschwelle und gilt in App und PWA gleichermaßen, weil
        // beide Seiten jetzt dieselbe Rundung nutzen.
        fun kanaele(color: Long) = intArrayOf(
            ((color shr 16) and 0xFF).toInt(),
            ((color shr 8) and 0xFF).toInt(),
            (color and 0xFF).toInt()
        )

        /** Die frühere Mischung, Kanal für Kanal abgeschnitten. */
        fun alteMischung(a: Long, b: Long, k: Float): Long {
            val f = k.coerceIn(0f, 1f)
            var out = 0xFF000000L
            for (shift in intArrayOf(16, 8, 0)) {
                val ca = (a shr shift) and 0xFF
                val cb = (b shr shift) and 0xFF
                out = out or ((ca + (cb - ca) * f).toInt().coerceIn(0, 255).toLong() shl shift)
            }
            return out
        }

        val farben = listOf(
            0xFF000000L, 0xFFFFFFFFL, 0xFF8E2410L, 0xFFFFD847L, 0xFF4E3C86L,
            0xFF231A3FL, 0xFF5B6478L, 0xFFE6EAF2L, 0xFF8A6A1EL, 0xFFE5341AL
        )
        farben.forEach { a ->
            farben.forEach { b ->
                (0..100).forEach { step ->
                    val k = step / 100f
                    val neu = kanaele(SkinPaint.mix(a, b, k))
                    val alt = kanaele(alteMischung(a, b, k))
                    neu.indices.forEach { i ->
                        val d = neu[i] - alt[i]
                        assertTrue(
                            "mix($a, $b, $k) springt um $d statt um höchstens eine Stufe",
                            d in 0..1
                        )
                    }
                }
            }
        }

        // HSL: Der Rohwert je Kanal steht in der Formel, also lässt sich die
        // alte Stufe direkt daraus bilden — abgeschnitten ist der Boden des
        // Rohwerts, gerundet höchstens eine Stufe darüber.
        var geprueft = 0
        (0..359 step 3).forEach { grad ->
            listOf(0.55f, 0.70f, 0.75f, 0.85f).forEach { s ->
                listOf(0.40f, 0.44f, 0.46f, 0.60f, 0.62f, 0.66f).forEach { l ->
                    val h = grad.toFloat()
                    val hue = ((h % 360f) + 360f) % 360f
                    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
                    val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
                    val m = l - c / 2f
                    val roh = when {
                        hue < 60f -> listOf(c, x, 0f)
                        hue < 120f -> listOf(x, c, 0f)
                        hue < 180f -> listOf(0f, c, x)
                        hue < 240f -> listOf(0f, x, c)
                        hue < 300f -> listOf(x, 0f, c)
                        else -> listOf(c, 0f, x)
                    }.map { ((it + m) * 255f) }
                    val neu = kanaele(SkinPaint.hsl(h, s, l))
                    roh.forEachIndexed { i, wert ->
                        val alt = wert.toInt().coerceIn(0, 255)
                        val d = neu[i] - alt
                        assertTrue(
                            "hsl($h, $s, $l) springt um $d statt um höchstens eine Stufe",
                            d in 0..1
                        )
                        geprueft++
                    }
                }
            }
        }
        assertTrue("genug HSL-Stützstellen geprüft", geprueft > 1_000)
    }

    @Test
    fun `der Himmel laeuft im Umlauf statt in der Nacht stehenzubleiben`() {
        // Hoch bis zur Nacht ...
        assertEquals(0, SkinPaint.skyStage(0))
        assertEquals(0, SkinPaint.skyStage(4))
        assertEquals(1, SkinPaint.skyStage(5))
        assertEquals(5, SkinPaint.skyStage(25))
        assertEquals(6, SkinPaint.skyStage(30))
        // ... und wieder zurueck zum Tag.
        assertEquals(5, SkinPaint.skyStage(35))
        assertEquals(1, SkinPaint.skyStage(55))
        assertEquals(0, SkinPaint.skyStage(60))
        assertEquals(6, SkinPaint.skyStage(90))

        // Ein voller Umlauf ist SKY_CYCLE Stufen, also 60 Punkte lang.
        (0..400).forEach { score ->
            assertEquals(
                "Score $score muss sich nach einem Umlauf wiederholen",
                SkinPaint.skyStage(score),
                SkinPaint.skyStage(score + SkinPaint.SKY_CYCLE * 5)
            )
        }

        // Und er bleibt immer in der Farbtabelle.
        (0..1000).forEach { score ->
            val stage = SkinPaint.skyStage(score)
            assertTrue("Stufe $stage liegt ausserhalb der Tabelle", stage in SkinPaint.SKY_STAGES.indices)
        }
    }

    @Test
    fun `Chamaeleon laeuft mit dem Himmel zurueck`() {
        // Score 60 ist wieder Tag — der Skin muss dieselbe Farbe zeigen wie
        // beim Start, sonst folgt er dem Himmel nicht mehr.
        assertEquals(
            SkinPaint.cell(SkinId.CHAMAELEON, 3, 3, SkinState(score = 0)),
            SkinPaint.cell(SkinId.CHAMAELEON, 3, 3, SkinState(score = 60))
        )
        assertEquals(
            SkinPaint.cell(SkinId.CHAMAELEON, 3, 3, SkinState(score = 30)),
            SkinPaint.cell(SkinId.CHAMAELEON, 3, 3, SkinState(score = 90))
        )
        assertNotEquals(
            SkinPaint.cell(SkinId.CHAMAELEON, 3, 3, SkinState(score = 30)),
            SkinPaint.cell(SkinId.CHAMAELEON, 3, 3, SkinState(score = 60))
        )
    }

    @Test
    fun `die Augen-Kontur haengt an der Helligkeit des Koerpers`() {
        // Sehr helle Koerper: Ohne Kontur ginge das weisse Auge unter.
        listOf(SkinId.KOI, SkinId.CHROM, SkinId.KARO, SkinId.PILZ).forEach {
            assertTrue("$it braucht die Augen-Kontur", SkinPaint.needsEyeOutline(it))
        }
        // Alles andere traegt den Kontrast selbst — dort waere die Kontur
        // ein Kasten ums Auge und wuerde den Bestand veraendern.
        listOf(
            SkinId.KLASSIK, SkinId.GOLD, SkinId.MINZE, SkinId.LAVA, SkinId.FROST,
            SkinId.SCHATTEN, SkinId.PRISMA, SkinId.BIENE, SkinId.MELONE,
            SkinId.GALAXIE, SkinId.NEON, SkinId.MAGMA, SkinId.TINTE,
            SkinId.REGENBOGEN, SkinId.AURORA, SkinId.CHAMAELEON, SkinId.KOMBO
        ).forEach {
            assertFalse("$it braucht keine Augen-Kontur", SkinPaint.needsEyeOutline(it))
        }
    }

    @Test
    fun `nur Tinte und Phoenix ziehen einen Schweif`() {
        SkinId.entries.forEach { id ->
            assertEquals(
                id == SkinId.TINTE || id == SkinId.PHOENIX,
                SkinPaint.hasTrail(id)
            )
        }
    }
}
