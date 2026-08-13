package de.robinrehbein.punkt

import de.robinrehbein.punkt.game.DotSkin
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SkinPaint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DotSkinTest {

    private fun stats(
        best: Int = 0,
        perfect: Int = 0,
        daily: Int = 0,
        runs: Int = 0,
        total: Int = 0,
        days: Int = 0,
        months: Int = 0,
        season: Int = 0,
        patron: Boolean = false
    ) = DotSkin.Stats(
        bestScore = best,
        bestPerfectStreak = perfect,
        bestDailyStreak = daily,
        runCount = runs,
        totalScore = total,
        daysPlayed = days,
        monthsPlayed = months,
        seasonEarned = season,
        patronOwned = patron
    )

    /** Alles, was sich ohne Saison und ohne Kauf verdienen lässt. */
    private fun allEarned() = stats(
        best = 80, perfect = 15, daily = 21,
        runs = 300, total = 5_000, days = 7, months = 3
    )

    @Test
    fun `Klassik ist immer frei`() {
        assertTrue(DotSkin.KLASSIK.isUnlocked(stats()))
    }

    @Test
    fun `Medaillen-Skins schalten an den Medaillen-Schwellen frei`() {
        assertFalse(DotSkin.MINZE.isUnlocked(stats(best = 9)))
        assertTrue(DotSkin.MINZE.isUnlocked(stats(best = 10)))
        assertFalse(DotSkin.LAVA.isUnlocked(stats(best = 19)))
        assertTrue(DotSkin.LAVA.isUnlocked(stats(best = 20)))
        assertFalse(DotSkin.GOLD.isUnlocked(stats(best = 29)))
        assertTrue(DotSkin.GOLD.isUnlocked(stats(best = 30)))
        assertFalse(DotSkin.FROST.isUnlocked(stats(best = 39)))
        assertTrue(DotSkin.FROST.isUnlocked(stats(best = 40)))
    }

    @Test
    fun `Schatten braucht die Perfekt-Serie, Prisma die Daily-Serie`() {
        assertFalse(DotSkin.SCHATTEN.isUnlocked(stats(best = 99, perfect = 3)))
        assertTrue(DotSkin.SCHATTEN.isUnlocked(stats(perfect = 4)))
        assertFalse(DotSkin.PRISMA.isUnlocked(stats(best = 99, daily = 2)))
        assertTrue(DotSkin.PRISMA.isUnlocked(stats(daily = 3)))
    }

    @Test
    fun `unbekannter gespeicherter Name faellt auf Klassik zurueck`() {
        assertEquals(DotSkin.KLASSIK, DotSkin.fromName(null))
        assertEquals(DotSkin.KLASSIK, DotSkin.fromName("GIBTS_NICHT"))
        assertEquals(DotSkin.LAVA, DotSkin.fromName("LAVA"))
    }

    @Test
    fun `DotSkin fuehrt SkinId in derselben Reihenfolge`() {
        // Die Reihenfolge ist zugleich der gespeicherte Wert und die Folge
        // im Skin-Picker — und :wear, ios/ und web/ fuehren dieselbe Liste
        // (siehe skin.order in parity/golden-vectors.txt). Rutscht hier
        // etwas, zeigen die anderen Plattformen etwas anderes an.
        assertEquals(SkinId.entries.toList(), DotSkin.entries.map { it.id })
    }

    @Test
    fun `unlockedCount zaehlt nur sammelbare Skins`() {
        assertEquals(1, DotSkin.unlockedCount(stats()))
        // Saison und Goenner zaehlen nie mit — der Sammlungsstand ist eine
        // Leistungsanzeige, und der Regenbogen haengt an ihm.
        assertEquals(DotSkin.collectableCount(), DotSkin.unlockedCount(allEarned()))
        assertEquals(
            DotSkin.entries.count { it.countsForCollection },
            DotSkin.collectableCount()
        )
        assertTrue(DotSkin.collectableCount() < DotSkin.entries.size)
    }

    @Test
    fun `ein gekaufter Skin hebt weder Sammlung noch Feier`() {
        val ohne = stats()
        val mit = stats(patron = true)
        assertTrue(DotSkin.DIAMANT.isUnlocked(mit))
        assertFalse(DotSkin.DIAMANT.isUnlocked(ohne))
        assertEquals(DotSkin.unlockedCount(ohne), DotSkin.unlockedCount(mit))
        // earnedCount traegt die Feier: Ein Kauf darf sie nie ausloesen.
        assertEquals(DotSkin.earnedCount(ohne), DotSkin.earnedCount(mit))
    }

    @Test
    fun `ein verdienter Saison-Skin wird gefeiert, obwohl er nicht sammelt`() {
        val ohne = stats()
        val mit = stats(season = 1) // Bit 0 = KUERBIS
        assertTrue(DotSkin.KUERBIS.isUnlocked(mit))
        assertEquals(DotSkin.unlockedCount(ohne), DotSkin.unlockedCount(mit))
        assertEquals(DotSkin.earnedCount(ohne) + 1, DotSkin.earnedCount(mit))
    }

    @Test
    fun `die Saison-Maske schaltet genau ihr eigenes Bit frei`() {
        assertTrue(DotSkin.KUERBIS.isUnlocked(stats(season = 0b0001)))
        assertFalse(DotSkin.ZUCKERSTANGE.isUnlocked(stats(season = 0b0001)))
        assertTrue(DotSkin.ZUCKERSTANGE.isUnlocked(stats(season = 0b0010)))
        assertTrue(DotSkin.HERZ.isUnlocked(stats(season = 0b0100)))
        assertTrue(DotSkin.OSTEREI.isUnlocked(stats(season = 0b1000)))
        assertFalse(DotSkin.OSTEREI.isUnlocked(stats(season = 0b0111)))
    }

    @Test
    fun `die Ausdauer-Achsen haengen an den angekuendigten Schwellen`() {
        assertFalse(DotSkin.EI.isUnlocked(stats(runs = 24)))
        assertTrue(DotSkin.EI.isUnlocked(stats(runs = 25)))
        assertFalse(DotSkin.TIGER.isUnlocked(stats(runs = 99)))
        assertTrue(DotSkin.TIGER.isUnlocked(stats(runs = 100)))
        assertFalse(DotSkin.MEDAILLE.isUnlocked(stats(runs = 199)))
        assertTrue(DotSkin.MEDAILLE.isUnlocked(stats(runs = 200)))
        assertFalse(DotSkin.FUSSBALL.isUnlocked(stats(runs = 299)))
        assertTrue(DotSkin.FUSSBALL.isUnlocked(stats(runs = 300)))
        assertFalse(DotSkin.DONUT.isUnlocked(stats(total = 999)))
        assertTrue(DotSkin.DONUT.isUnlocked(stats(total = 1_000)))
        assertFalse(DotSkin.KONFETTI.isUnlocked(stats(total = 4_999)))
        assertTrue(DotSkin.KONFETTI.isUnlocked(stats(total = 5_000)))
        assertFalse(DotSkin.TAGESZEIT.isUnlocked(stats(days = 6)))
        assertTrue(DotSkin.TAGESZEIT.isUnlocked(stats(days = 7)))
        assertFalse(DotSkin.JAHRESZEIT.isUnlocked(stats(months = 2)))
        assertTrue(DotSkin.JAHRESZEIT.isUnlocked(stats(months = 3)))
    }

    @Test
    fun `die neuen Rekord-Skins haengen an den angekuendigten Schwellen`() {
        assertFalse(DotSkin.PINGUIN.isUnlocked(stats(best = 64)))
        assertTrue(DotSkin.PINGUIN.isUnlocked(stats(best = 65)))
        assertFalse(DotSkin.WELLE.isUnlocked(stats(best = 69)))
        assertTrue(DotSkin.WELLE.isUnlocked(stats(best = 70)))
        assertFalse(DotSkin.THERMO.isUnlocked(stats(best = 74)))
        assertTrue(DotSkin.THERMO.isUnlocked(stats(best = 75)))
        assertFalse(DotSkin.HOLO.isUnlocked(stats(best = 79)))
        assertTrue(DotSkin.HOLO.isUnlocked(stats(best = 80)))
        assertFalse(DotSkin.GEWITTER.isUnlocked(stats(perfect = 14)))
        assertTrue(DotSkin.GEWITTER.isUnlocked(stats(perfect = 15)))
        assertFalse(DotSkin.DISCO.isUnlocked(stats(daily = 20)))
        assertTrue(DotSkin.DISCO.isUnlocked(stats(daily = 21)))
    }

    @Test
    fun `jeder Skin traegt Namen und Freischalt-Hinweis`() {
        DotSkin.entries.forEach { skin ->
            assertTrue("${skin.name} braucht einen Namen", skin.titleRes != 0)
            if (skin != DotSkin.KLASSIK) {
                assertTrue("${skin.name} braucht einen Hinweis", skin.unlockHintRes != null)
            }
        }
        assertEquals("Klassik ist von Anfang an da und braucht keinen Hinweis", null, DotSkin.KLASSIK.unlockHintRes)
    }

    @Test
    fun `Enum-Reihenfolge und Kennungen decken sich mit dem Farbwerk`() {
        // Die gespeicherte Auswahl haengt am Enum-Namen: Wenn Reihenfolge
        // oder Schreibweise auseinanderlaufen, waehlt ein Update stillschweigend
        // einen anderen Skin aus.
        assertEquals(SkinId.entries.map { it.name }, DotSkin.entries.map { it.name })
        DotSkin.entries.forEach { assertEquals(it.name, it.id.name) }
    }

    @Test
    fun `neue Skins haengen an den angekuendigten Schwellen`() {
        assertFalse(DotSkin.BIENE.isUnlocked(stats(perfect = 5)))
        assertTrue(DotSkin.BIENE.isUnlocked(stats(perfect = 6)))
        assertFalse(DotSkin.MELONE.isUnlocked(stats(best = 24)))
        assertTrue(DotSkin.MELONE.isUnlocked(stats(best = 25)))
        assertFalse(DotSkin.KOI.isUnlocked(stats(daily = 6)))
        assertTrue(DotSkin.KOI.isUnlocked(stats(daily = 7)))
        assertFalse(DotSkin.MAGMA.isUnlocked(stats(best = 59)))
        assertTrue(DotSkin.MAGMA.isUnlocked(stats(best = 60)))
    }

    @Test
    fun `Regenbogen kommt zuletzt`() {
        assertFalse(DotSkin.REGENBOGEN.isUnlocked(stats(best = 999, perfect = 99, daily = 13)))
        assertTrue(DotSkin.REGENBOGEN.isUnlocked(allEarned()))
    }

    @Test
    fun `Regenbogen bleibt ohne Saison und ohne Kauf erreichbar`() {
        // Sonst waere der Abschluss der Sammlung entweder ein Jahr lang
        // gesperrt oder schlicht kaeuflich.
        val ohne = allEarned()
        assertEquals(0, ohne.seasonEarned)
        assertFalse(ohne.patronOwned)
        assertTrue(DotSkin.REGENBOGEN.isUnlocked(ohne))
        DotSkin.entries.filter { it.isSeasonal || it.isPatron }.forEach {
            assertFalse("${it.name} darf ohne Saison/Kauf nicht offen sein", it.isUnlocked(ohne))
        }
    }

    @Test
    fun `jede Familie liegt am Stueck`() {
        // Das Skin-Menue setzt eine Ueberschrift, sobald die Familie
        // wechselt — laege eine Familie in zwei Bloecken, stuende ihre
        // Ueberschrift zweimal da.
        val seen = mutableSetOf<DotSkin.Family>()
        var last: DotSkin.Family? = null
        DotSkin.entries.forEach { skin ->
            if (skin.family != last) {
                assertTrue("${skin.family} kommt ein zweites Mal", seen.add(skin.family))
                last = skin.family
            }
        }
        assertEquals(DotSkin.Family.entries.size, seen.size)
        DotSkin.entries.filter { it.isPatron }.forEach {
            assertEquals(DotSkin.Family.GOENNER, it.family)
        }
        DotSkin.entries.filter { it.isSeasonal }.forEach {
            assertEquals(DotSkin.Family.SAISON, it.family)
        }
    }

    @Test
    fun `Chamaeleon nutzt dieselben Himmelsstufen wie das Spiel`() {
        // SkyStages in TimingGameScreen.kt faerbt den Hintergrund, SkinPaint
        // faerbt den Vogel danach — laufen sie auseinander, passt der Skin
        // nicht mehr zur Stufe, die er spiegeln soll.
        assertEquals(skyStagesFromUi(), SkinPaint.SKY_STAGES.toList())
    }

    private fun skyStagesFromUi(): List<Long> {
        val source = java.io.File("src/main/java/de/robinrehbein/punkt/ui/screens/TimingGameScreen.kt")
            .readText()
        // Bis zur schliessenden Klammer am Zeilenanfang — die Kommentare in
        // der Liste tragen selbst Klammern ("(tuerkis)").
        val block = source.substringAfter("private val SkyStages = listOf(").substringBefore("\n)")
        return Regex("0x([0-9A-Fa-f]{8})").findAll(block)
            .map { it.groupValues[1].toLong(16) }
            .toList()
    }

    @Test
    fun `ohne Tagespass ist verfuegbar dasselbe wie freigeschaltet`() {
        val s = stats(best = 25)
        DotSkin.entries.forEach { skin ->
            assertEquals(skin.isUnlocked(s), skin.isAvailable(s, null))
        }
    }

    @Test
    fun `der Tagespass macht genau einen gesperrten Skin spielbar`() {
        val s = stats()
        assertTrue(DotSkin.LAVA.isAvailable(s, DotSkin.LAVA))
        assertFalse(DotSkin.GOLD.isAvailable(s, DotSkin.LAVA))
        assertTrue(DotSkin.KLASSIK.isAvailable(s, DotSkin.LAVA))
    }

    @Test
    fun `ein Tagespass schaltet nichts dauerhaft frei`() {
        // Die Feier im Game-Over haengt an unlockedCount — ein geliehener
        // Skin darf sie nicht ausloesen.
        val s = stats()
        assertFalse(DotSkin.LAVA.isUnlocked(s))
        assertEquals(1, DotSkin.unlockedCount(s))
        assertTrue(DotSkin.LAVA.isAvailable(s, DotSkin.LAVA))
    }
}
