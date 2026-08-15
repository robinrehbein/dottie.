package de.robinrehbein.punkt.wear

import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Die Kulissen-Wahl im Abgleich (WearSyncMerge.sceneToAdopt), gleiche
 * Neuer-gewinnt-Regel wie beim Skin: Nur eine wirklich neuere Wahl schlägt
 * die bestehende, und nur wenn die zusammengeführten Stände sie hergeben.
 * Die Uhr wählt selbst nie eine Kulisse — sie prüft hier nur, was sie vom
 * Telefon übernehmen darf.
 */
class WearSceneSyncTest {

    /** Alles, was für STADT (Rekord 85) und WÜSTE/MEER/BERG längst reicht. */
    private fun state(scene: String, changedAt: Long, bestScore: Int = 90) = SyncState(
        bestScore = bestScore,
        runCount = 600,
        totalScore = 20_000,
        dailyStreak = 40,
        scene = scene,
        sceneChangedAt = changedAt
    )

    @Test
    fun `Eine neuere Kulisse wird uebernommen`() {
        val before = state(SceneId.WIESE.name, changedAt = 1_000L)
        val incoming = state(SceneId.STADT.name, changedAt = 2_000L)
        assertEquals(SceneId.STADT, WearSyncMerge.sceneToAdopt(before, incoming, patronOwned = false))
    }

    @Test
    fun `Eine aeltere Kulisse wird nicht uebernommen`() {
        val before = state(SceneId.STADT.name, changedAt = 2_000L)
        val incoming = state(SceneId.WIESE.name, changedAt = 1_000L)
        assertNull(WearSyncMerge.sceneToAdopt(before, incoming, patronOwned = false))
    }

    @Test
    fun `Gleicher Zeitstempel wird nicht uebernommen`() {
        // ">" ist die Regel, nicht ">=" — ein Echo mit demselben Stempel
        // darf keine erneute Uebernahme (und keinen neuen Abgleich) ausloesen.
        val before = state(SceneId.WIESE.name, changedAt = 1_000L)
        val incoming = state(SceneId.STADT.name, changedAt = 1_000L)
        assertNull(WearSyncMerge.sceneToAdopt(before, incoming, patronOwned = false))
    }

    @Test
    fun `Eine neuere aber noch nicht freigeschaltete Kulisse wird nicht uebernommen`() {
        // WELTRAUM braucht alle anderen Kulissen gesammelt — hier fehlt
        // WUESTE (500 Laeufe) auf beiden Seiten.
        val before = state(SceneId.WIESE.name, changedAt = 1_000L, bestScore = 0).copy(
            runCount = 0, totalScore = 0, dailyStreak = 0
        )
        val incoming = state(SceneId.WELTRAUM.name, changedAt = 2_000L, bestScore = 0).copy(
            runCount = 0, totalScore = 0, dailyStreak = 0
        )
        assertNull(WearSyncMerge.sceneToAdopt(before, incoming, patronOwned = false))
    }

    @Test
    fun `Ein unbekannter Kulissen-Name faellt auf Wiese zurueck, zaehlt aber als Uebernahme`() {
        val before = state(SceneId.WIESE.name, changedAt = 1_000L)
        val incoming = state("GIBTSNICHT", changedAt = 2_000L)
        assertEquals(SceneId.WIESE, WearSyncMerge.sceneToAdopt(before, incoming, patronOwned = false))
    }
}
