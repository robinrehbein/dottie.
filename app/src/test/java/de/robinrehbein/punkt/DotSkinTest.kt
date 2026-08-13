package de.robinrehbein.punkt

import de.robinrehbein.punkt.game.DotSkin
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SkinPaint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DotSkinTest {

    private fun stats(best: Int = 0, perfect: Int = 0, daily: Int = 0) =
        DotSkin.Stats(bestScore = best, bestPerfectStreak = perfect, bestDailyStreak = daily)

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
    fun `unlockedCount zaehlt ueber alle Bedingungen`() {
        assertEquals(1, DotSkin.unlockedCount(stats()))
        assertEquals(
            DotSkin.entries.size,
            DotSkin.unlockedCount(stats(best = 60, perfect = 12, daily = 14))
        )
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
        assertTrue(DotSkin.REGENBOGEN.isUnlocked(stats(best = 60, perfect = 12, daily = 14)))
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
}
