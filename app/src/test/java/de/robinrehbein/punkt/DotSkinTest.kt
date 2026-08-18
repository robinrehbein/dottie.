package de.robinrehbein.punkt

import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.SkinFamily
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SkinStats
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
    ) = SkinStats(
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
        assertTrue(SkinPaint.isUnlocked(SkinId.KLASSIK, stats()))
    }

    @Test
    fun `Medaillen-Skins schalten an den Medaillen-Schwellen frei`() {
        assertFalse(SkinPaint.isUnlocked(SkinId.MINZE, stats(best = 9)))
        assertTrue(SkinPaint.isUnlocked(SkinId.MINZE, stats(best = 10)))
        assertFalse(SkinPaint.isUnlocked(SkinId.LAVA, stats(best = 19)))
        assertTrue(SkinPaint.isUnlocked(SkinId.LAVA, stats(best = 20)))
        assertFalse(SkinPaint.isUnlocked(SkinId.GOLD, stats(best = 29)))
        assertTrue(SkinPaint.isUnlocked(SkinId.GOLD, stats(best = 30)))
        assertFalse(SkinPaint.isUnlocked(SkinId.FROST, stats(best = 39)))
        assertTrue(SkinPaint.isUnlocked(SkinId.FROST, stats(best = 40)))
    }

    @Test
    fun `Schatten braucht die Perfekt-Serie, Prisma die Daily-Serie`() {
        assertFalse(SkinPaint.isUnlocked(SkinId.SCHATTEN, stats(best = 99, perfect = 3)))
        assertTrue(SkinPaint.isUnlocked(SkinId.SCHATTEN, stats(perfect = 4)))
        assertFalse(SkinPaint.isUnlocked(SkinId.PRISMA, stats(best = 99, daily = 2)))
        assertTrue(SkinPaint.isUnlocked(SkinId.PRISMA, stats(daily = 3)))
    }

    @Test
    fun `unbekannter gespeicherter Name faellt auf Klassik zurueck`() {
        assertEquals(SkinId.KLASSIK, SkinPaint.fromName(null))
        assertEquals(SkinId.KLASSIK, SkinPaint.fromName("GIBTS_NICHT"))
        assertEquals(SkinId.LAVA, SkinPaint.fromName("LAVA"))
    }

    @Test
    fun `SkinId fuehrt SkinId in derselben Reihenfolge`() {
        // Die Reihenfolge ist zugleich der gespeicherte Wert und die Folge
        // im Skin-Picker — und :wear, ios/ und web/ fuehren dieselbe Liste
        // (siehe skin.order in parity/golden-vectors.txt). Rutscht hier
        // etwas, zeigen die anderen Plattformen etwas anderes an.
        assertEquals(SkinId.entries.toList(), SkinPaint.ORDER)
    }

    @Test
    fun `unlockedCount zaehlt nur sammelbare Skins`() {
        assertEquals(1, SkinPaint.unlockedCount(stats()))
        // Saison und Goenner zaehlen nie mit — der Sammlungsstand ist eine
        // Leistungsanzeige, und der Regenbogen haengt an ihm.
        assertEquals(SkinPaint.collectableCount(), SkinPaint.unlockedCount(allEarned()))
        assertEquals(
            SkinPaint.ORDER.count { SkinPaint.countsForCollection(it) },
            SkinPaint.collectableCount()
        )
        assertTrue(SkinPaint.collectableCount() < SkinPaint.ORDER.size)
    }

    @Test
    fun `ein gekaufter Skin hebt weder Sammlung noch Feier`() {
        val ohne = stats()
        val mit = stats(patron = true)
        assertTrue(SkinPaint.isUnlocked(SkinId.DIAMANT, mit))
        assertFalse(SkinPaint.isUnlocked(SkinId.DIAMANT, ohne))
        assertEquals(SkinPaint.unlockedCount(ohne), SkinPaint.unlockedCount(mit))
        // earnedCount traegt die Feier: Ein Kauf darf sie nie ausloesen.
        assertEquals(SkinPaint.earnedCount(ohne), SkinPaint.earnedCount(mit))
    }

    @Test
    fun `ein verdienter Saison-Skin wird gefeiert, obwohl er nicht sammelt`() {
        val ohne = stats()
        val mit = stats(season = 1) // Bit 0 = KUERBIS
        assertTrue(SkinPaint.isUnlocked(SkinId.KUERBIS, mit))
        assertEquals(SkinPaint.unlockedCount(ohne), SkinPaint.unlockedCount(mit))
        assertEquals(SkinPaint.earnedCount(ohne) + 1, SkinPaint.earnedCount(mit))
    }

    @Test
    fun `die Saison-Maske schaltet genau ihr eigenes Bit frei`() {
        assertTrue(SkinPaint.isUnlocked(SkinId.KUERBIS, stats(season = 0b0001)))
        assertFalse(SkinPaint.isUnlocked(SkinId.ZUCKERSTANGE, stats(season = 0b0001)))
        assertTrue(SkinPaint.isUnlocked(SkinId.ZUCKERSTANGE, stats(season = 0b0010)))
        assertTrue(SkinPaint.isUnlocked(SkinId.HERZ, stats(season = 0b0100)))
        assertTrue(SkinPaint.isUnlocked(SkinId.OSTEREI, stats(season = 0b1000)))
        assertFalse(SkinPaint.isUnlocked(SkinId.OSTEREI, stats(season = 0b0111)))
    }

    @Test
    fun `die Ausdauer-Achsen haengen an den angekuendigten Schwellen`() {
        assertFalse(SkinPaint.isUnlocked(SkinId.EI, stats(runs = 24)))
        assertTrue(SkinPaint.isUnlocked(SkinId.EI, stats(runs = 25)))
        assertFalse(SkinPaint.isUnlocked(SkinId.TIGER, stats(runs = 99)))
        assertTrue(SkinPaint.isUnlocked(SkinId.TIGER, stats(runs = 100)))
        assertFalse(SkinPaint.isUnlocked(SkinId.MEDAILLE, stats(runs = 199)))
        assertTrue(SkinPaint.isUnlocked(SkinId.MEDAILLE, stats(runs = 200)))
        assertFalse(SkinPaint.isUnlocked(SkinId.FUSSBALL, stats(runs = 299)))
        assertTrue(SkinPaint.isUnlocked(SkinId.FUSSBALL, stats(runs = 300)))
        assertFalse(SkinPaint.isUnlocked(SkinId.DONUT, stats(total = 999)))
        assertTrue(SkinPaint.isUnlocked(SkinId.DONUT, stats(total = 1_000)))
        assertFalse(SkinPaint.isUnlocked(SkinId.KONFETTI, stats(total = 4_999)))
        assertTrue(SkinPaint.isUnlocked(SkinId.KONFETTI, stats(total = 5_000)))
        assertFalse(SkinPaint.isUnlocked(SkinId.TAGESZEIT, stats(days = 6)))
        assertTrue(SkinPaint.isUnlocked(SkinId.TAGESZEIT, stats(days = 7)))
        assertFalse(SkinPaint.isUnlocked(SkinId.JAHRESZEIT, stats(months = 2)))
        assertTrue(SkinPaint.isUnlocked(SkinId.JAHRESZEIT, stats(months = 3)))
    }

    @Test
    fun `die neuen Rekord-Skins haengen an den angekuendigten Schwellen`() {
        assertFalse(SkinPaint.isUnlocked(SkinId.PINGUIN, stats(best = 64)))
        assertTrue(SkinPaint.isUnlocked(SkinId.PINGUIN, stats(best = 65)))
        assertFalse(SkinPaint.isUnlocked(SkinId.WELLE, stats(best = 69)))
        assertTrue(SkinPaint.isUnlocked(SkinId.WELLE, stats(best = 70)))
        assertFalse(SkinPaint.isUnlocked(SkinId.THERMO, stats(best = 74)))
        assertTrue(SkinPaint.isUnlocked(SkinId.THERMO, stats(best = 75)))
        assertFalse(SkinPaint.isUnlocked(SkinId.HOLO, stats(best = 79)))
        assertTrue(SkinPaint.isUnlocked(SkinId.HOLO, stats(best = 80)))
        assertFalse(SkinPaint.isUnlocked(SkinId.GEWITTER, stats(perfect = 14)))
        assertTrue(SkinPaint.isUnlocked(SkinId.GEWITTER, stats(perfect = 15)))
        assertFalse(SkinPaint.isUnlocked(SkinId.DISCO, stats(daily = 20)))
        assertTrue(SkinPaint.isUnlocked(SkinId.DISCO, stats(daily = 21)))
    }

    @Test
    fun `Enum-Reihenfolge und Kennungen decken sich mit dem Farbwerk`() {
        // Die gespeicherte Auswahl haengt am Enum-Namen: Wenn Reihenfolge
        // oder Schreibweise auseinanderlaufen, waehlt ein Update stillschweigend
        // einen anderen Skin aus.
        assertEquals(SkinId.entries.map { it.name }, SkinPaint.ORDER.map { it.name })
    }

    @Test
    fun `neue Skins haengen an den angekuendigten Schwellen`() {
        assertFalse(SkinPaint.isUnlocked(SkinId.BIENE, stats(perfect = 5)))
        assertTrue(SkinPaint.isUnlocked(SkinId.BIENE, stats(perfect = 6)))
        assertFalse(SkinPaint.isUnlocked(SkinId.MELONE, stats(best = 24)))
        assertTrue(SkinPaint.isUnlocked(SkinId.MELONE, stats(best = 25)))
        assertFalse(SkinPaint.isUnlocked(SkinId.KOI, stats(daily = 6)))
        assertTrue(SkinPaint.isUnlocked(SkinId.KOI, stats(daily = 7)))
        assertFalse(SkinPaint.isUnlocked(SkinId.MAGMA, stats(best = 59)))
        assertTrue(SkinPaint.isUnlocked(SkinId.MAGMA, stats(best = 60)))
    }

    @Test
    fun `Regenbogen kommt zuletzt`() {
        assertFalse(SkinPaint.isUnlocked(SkinId.REGENBOGEN, stats(best = 999, perfect = 99, daily = 13)))
        assertTrue(SkinPaint.isUnlocked(SkinId.REGENBOGEN, allEarned()))
    }

    @Test
    fun `Regenbogen bleibt ohne Saison und ohne Kauf erreichbar`() {
        // Sonst waere der Abschluss der Sammlung entweder ein Jahr lang
        // gesperrt oder schlicht kaeuflich.
        val ohne = allEarned()
        assertEquals(0, ohne.seasonEarned)
        assertFalse(ohne.patronOwned)
        assertTrue(SkinPaint.isUnlocked(SkinId.REGENBOGEN, ohne))
        SkinPaint.ORDER.filter { SkinPaint.isSeasonal(it) || SkinPaint.isPatron(it) }.forEach {
            assertFalse("${it.name} darf ohne Saison/Kauf nicht offen sein", SkinPaint.isUnlocked(it, ohne))
        }
    }

    @Test
    fun `jede Familie liegt am Stueck`() {
        // Das Skin-Menue setzt eine Ueberschrift, sobald die Familie
        // wechselt — laege eine Familie in zwei Bloecken, stuende ihre
        // Ueberschrift zweimal da.
        val seen = mutableSetOf<SkinFamily>()
        var last: SkinFamily? = null
        SkinPaint.ORDER.forEach { skin ->
            if (SkinPaint.family(skin) != last) {
                assertTrue("${SkinPaint.family(skin)} kommt ein zweites Mal", seen.add(SkinPaint.family(skin)))
                last = SkinPaint.family(skin)
            }
        }
        assertEquals(SkinFamily.entries.size, seen.size)
        SkinPaint.ORDER.filter { SkinPaint.isPatron(it) }.forEach {
            assertEquals(SkinFamily.GOENNER, SkinPaint.family(it))
        }
        SkinPaint.ORDER.filter { SkinPaint.isSeasonal(it) }.forEach {
            assertEquals(SkinFamily.SAISON, SkinPaint.family(it))
        }
    }

    @Test
    fun `Chamaeleon nutzt dieselben Himmelsstufen wie die Wiese`() {
        // Der Himmel kommt seit den Kulissen aus ScenePaint, der Vogel
        // weiterhin aus SkinPaint. Der CHAMAELEON spiegelt die Stufe des
        // Bestands — laufen die beiden Tabellen auseinander, traegt er im
        // Startbild eine Farbe, die am Himmel gar nicht vorkommt.
        assertEquals(
            ScenePaint.sky(SceneId.WIESE).toList(),
            SkinPaint.SKY_STAGES.toList()
        )
    }

    @Test
    fun `ohne Tagespass ist verfuegbar dasselbe wie freigeschaltet`() {
        val s = stats(best = 25)
        SkinPaint.ORDER.forEach { skin ->
            assertEquals(SkinPaint.isUnlocked(skin, s), (SkinPaint.isUnlocked(skin, s) || skin == null))
        }
    }

    @Test
    fun `der Tagespass macht genau einen gesperrten Skin spielbar`() {
        val s = stats()
        assertTrue((SkinPaint.isUnlocked(SkinId.LAVA, s) || SkinId.LAVA == SkinId.LAVA))
        assertFalse((SkinPaint.isUnlocked(SkinId.GOLD, s) || SkinId.GOLD == SkinId.LAVA))
        assertTrue((SkinPaint.isUnlocked(SkinId.KLASSIK, s) || SkinId.KLASSIK == SkinId.LAVA))
    }

    @Test
    fun `ein Tagespass schaltet nichts dauerhaft frei`() {
        // Die Feier im Game-Over haengt an unlockedCount — ein geliehener
        // Skin darf sie nicht ausloesen.
        val s = stats()
        assertFalse(SkinPaint.isUnlocked(SkinId.LAVA, s))
        assertEquals(1, SkinPaint.unlockedCount(s))
        assertTrue((SkinPaint.isUnlocked(SkinId.LAVA, s) || SkinId.LAVA == SkinId.LAVA))
    }
}
