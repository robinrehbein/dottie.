package de.robinrehbein.punkt.wear

import de.robinrehbein.punkt.game.Season
import de.robinrehbein.punkt.game.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Abgleich mit dem Telefon, so wie ihn applySync rechnet: Zahlen
 * wachsen nur, Masken werden verodert. Beides muss stimmen, sonst nimmt
 * ein Abgleich der Uhr etwas weg, das sie sich verdient hat.
 */
class WearSyncMergeTest {

    private val leer = SyncState()

    @Test
    fun `Zahlen nehmen das Maximum, egal von welcher Seite`() {
        val uhr = SyncState(
            bestScore = 42, bestPerfectStreak = 3, bestDailyStreak = 9,
            runCount = 12, totalScore = 800, daysPlayed = 4
        )
        val telefon = SyncState(
            bestScore = 17, bestPerfectStreak = 11, bestDailyStreak = 2,
            runCount = 300, totalScore = 120, daysPlayed = 9
        )
        val merged = WearSyncMerge.skinStats(uhr, telefon, patronOwned = false)
        assertEquals(42, merged.bestScore)
        assertEquals(11, merged.bestPerfectStreak)
        assertEquals(9, merged.bestDailyStreak)
        assertEquals(300, merged.runCount)
        assertEquals(800, merged.totalScore)
        assertEquals(9, merged.daysPlayed)
    }

    @Test
    fun `Das Zusammenfuehren ist von der Reihenfolge unabhaengig`() {
        val uhr = SyncState(bestScore = 42, monthsPlayed = 0b0101, runCount = 3)
        val telefon = SyncState(bestScore = 17, monthsPlayed = 0b1010, runCount = 99)
        assertEquals(
            WearSyncMerge.skinStats(uhr, telefon, patronOwned = false),
            WearSyncMerge.skinStats(telefon, uhr, patronOwned = false)
        )
    }

    /**
     * Der Kern der Sache: Für die Freischaltungen zählt der Bestwert der
     * Daily-Serie, nicht die laufende. Sonst nähme ein Abgleich, bei dem
     * eine Seite gerade nach einer Lücke neu angefangen hat, einen längst
     * verdienten Skin wieder weg.
     */
    @Test
    fun `Die Daily-Serie zaehlt mit ihrem Bestwert, nicht mit dem laufenden Stand`() {
        // Auf der Uhr sind einmal 14 Tage am Stück zusammengekommen —
        // AURORA ist verdient. Jetzt läuft dort eine frische Serie.
        val uhr = SyncState(dailyStreak = 1, bestDailyStreak = 14)
        val telefon = SyncState(dailyStreak = 1, bestDailyStreak = 1)
        val merged = WearSyncMerge.skinStats(uhr, telefon, patronOwned = false)
        assertEquals(14, merged.bestDailyStreak)
        assertTrue(WearDotSkin.AURORA.isUnlocked(merged))
        // Und in der anderen Richtung genauso.
        assertTrue(
            WearDotSkin.AURORA.isUnlocked(
                WearSyncMerge.skinStats(telefon, uhr, patronOwned = false)
            )
        )
    }

    @Test
    fun `Monats-Masken werden verodert und als Anzahl gezaehlt`() {
        // Uhr: Maerz und Mai. Telefon: Mai und August. Zusammen drei.
        val uhr = SyncState(monthsPlayed = (1 shl 2) or (1 shl 4))
        val telefon = SyncState(monthsPlayed = (1 shl 4) or (1 shl 7))
        val merged = WearSyncMerge.skinStats(uhr, telefon, patronOwned = false)
        assertEquals(3, merged.monthsPlayed)
        // Genau die Schwelle von JAHRESZEIT (drei verschiedene Monate).
        assertTrue(WearDotSkin.JAHRESZEIT.isUnlocked(merged))
    }

    @Test
    fun `Ein verdienter Saison-Skin geht beim Zusammenfuehren nicht verloren`() {
        // Nur die Uhr war im Oktober fleissig, das Telefon weiss nichts.
        val uhr = SyncState(seasonEarned = Season.KUERBIS.bit)
        val merged = WearSyncMerge.skinStats(uhr, leer, patronOwned = false)
        assertEquals(Season.KUERBIS.bit, merged.seasonEarned)
        assertTrue(WearDotSkin.KUERBIS.isUnlocked(merged))
        // Und andersherum genauso.
        val andersherum = WearSyncMerge.skinStats(leer, uhr, patronOwned = false)
        assertTrue(WearDotSkin.KUERBIS.isUnlocked(andersherum))
    }

    @Test
    fun `Saison-Masken beider Seiten stehen danach nebeneinander`() {
        val uhr = SyncState(seasonEarned = Season.KUERBIS.bit)
        val telefon = SyncState(seasonEarned = Season.OSTEREI.bit)
        val merged = WearSyncMerge.skinStats(uhr, telefon, patronOwned = false)
        assertTrue(WearDotSkin.KUERBIS.isUnlocked(merged))
        assertTrue(WearDotSkin.OSTEREI.isUnlocked(merged))
        assertFalse(WearDotSkin.HERZ.isUnlocked(merged))
    }

    /**
     * Der Gönner-Kauf steht bewusst NICHT im Austauschformat: Er käme über
     * den Data Layer fälschbar herein. Es zählt allein, was Play der Uhr
     * selbst gesagt hat.
     */
    @Test
    fun `Der Goenner-Kauf kommt vom Geraet, nicht aus dem Abgleich`() {
        val ohne = WearSyncMerge.skinStats(leer, leer, patronOwned = false)
        assertFalse(ohne.patronOwned)
        assertFalse(WearDotSkin.DIAMANT.isUnlocked(ohne))

        val mit = WearSyncMerge.skinStats(leer, leer, patronOwned = true)
        assertTrue(mit.patronOwned)
        // Genau der Fall aus applySync: Das Telefon schickt DIAMANT als
        // gewaehlten Skin — sobald der Kauf hier bekannt ist, geht er durch.
        assertTrue(WearDotSkin.fromName("DIAMANT").isUnlocked(mit))
    }
}
