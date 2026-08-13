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

    private val maxStats = SkinStats(bestScore = 999, bestPerfectStreak = 99, bestDailyStreak = 99)

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
        val fastAlles = SkinStats(bestScore = 999, bestPerfectStreak = 99, bestDailyStreak = 13)
        assertFalse(
            "Solange Aurora fehlt, bleibt der Regenbogen zu",
            SkinPaint.isUnlocked(SkinId.REGENBOGEN, fastAlles)
        )
        assertTrue(SkinPaint.isUnlocked(SkinId.REGENBOGEN, maxStats))
        assertEquals(SkinId.entries.size, SkinPaint.unlockedCount(maxStats))
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
        assertEquals(0xFF7F7F7F, SkinPaint.mix(0xFF000000, 0xFFFFFFFF, 0.5f))
        assertEquals("Werte außerhalb 0..1 werden gekappt", 0xFFFFFFFF, SkinPaint.mix(0xFF000000, 0xFFFFFFFF, 4f))

        assertEquals(0xFFFF0000, SkinPaint.hsl(0f, 1f, 0.5f))
        assertEquals(0xFF00FF00, SkinPaint.hsl(120f, 1f, 0.5f))
        assertEquals(0xFF0000FF, SkinPaint.hsl(240f, 1f, 0.5f))
        assertEquals("Negative Winkel wickeln sich", SkinPaint.hsl(30f, 1f, 0.5f), SkinPaint.hsl(-330f, 1f, 0.5f))
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
    fun `nur Tinte zieht einen Schweif`() {
        SkinId.entries.forEach { id ->
            assertEquals(id == SkinId.TINTE, SkinPaint.hasTrail(id))
        }
    }
}
