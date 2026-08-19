package de.robinrehbein.punkt.ui.data

import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Der Abgleich mit der Uhr, von der Speicher-Seite aus gesehen.
 *
 * Zwei Eigenschaften hängen hier dran, die man am Gerät erst merkt, wenn
 * sie fehlen: dass die Oberfläche vom Merge überhaupt erfährt
 * ([GameStore.syncRevision]), und dass eine abgelehnte Wahl der Uhr beim
 * nächsten Abgleich wieder anklopft (der Zeitstempel bleibt stehen).
 */
class GameStoreSyncTest {

    private fun store() = GameStore(FakeKeyValueStore())

    @Test
    fun `ein uebernommener Abgleich meldet sich bei der Oberflaeche`() {
        val store = store()
        assertEquals(0, store.syncRevision)

        assertTrue(store.applySync(SyncState(bestScore = 42)))

        assertEquals(42, store.bestScore)
        assertEquals(1, store.syncRevision, "Die Oberfläche muss nachziehen können")
    }

    @Test
    fun `ein Abgleich ohne Neuigkeit meldet sich nicht`() {
        val store = store()
        store.applySync(SyncState(bestScore = 42))
        val vorher = store.syncRevision

        // Derselbe Stand ein zweites Mal: Der Merge ist idempotent, also
        // gibt es auch nichts nachzuziehen.
        store.applySync(store.syncState())

        assertEquals(vorher, store.syncRevision)
    }

    @Test
    fun `jede uebernommene Entscheidung zaehlt einzeln`() {
        val store = store()
        store.applySync(SyncState(bestScore = 10))
        store.applySync(SyncState(bestScore = 20))

        assertEquals(2, store.syncRevision)
        assertEquals(20, store.bestScore)
    }

    @Test
    fun `der gewaehlte Skin kommt aus dem Abgleich in den Speicher`() {
        val store = store()

        store.applySync(
            SyncState(bestScore = 15, skin = SkinId.MINZE.name, skinChangedAt = 5_000L)
        )

        assertEquals(SkinId.MINZE, store.selectedSkin)
        assertEquals(
            5_000L,
            store.syncState().skinChangedAt,
            "Eine uebernommene Wahl schreibt ihren Zeitstempel fort"
        )
    }

    @Test
    fun `ein abgelehnter Goenner-Skin klopft nach dem Kauf erneut an`() {
        val store = store()
        val vonDerUhr = SyncState(skin = SkinId.DIAMANT.name, skinChangedAt = 9_000L)

        store.applySync(vonDerUhr)

        // Der Kauf ist hier noch nicht bekannt — also bleibt die Wahl aus.
        assertEquals(SkinId.KLASSIK, store.selectedSkin)
        assertEquals(
            0L,
            store.syncState().skinChangedAt,
            "Ohne Uebernahme darf der Zeitstempel nicht mitwandern"
        )

        // Play meldet den Gönner-Kauf nach: Jetzt muss dieselbe Wahl
        // durchgehen. Mit fortgeschriebenem Zeitstempel waere sie fuer
        // immer verschluckt gewesen.
        store.patronOwned = true
        store.applySync(vonDerUhr)

        assertEquals(SkinId.DIAMANT, store.selectedSkin)
        assertEquals(9_000L, store.syncState().skinChangedAt)
    }

    @Test
    fun `eine noch ungedeckte Kulisse kommt spaeter erneut an`() {
        val store = store()
        // STADT haengt an Rekord 85 — den kennt hier noch niemand.
        val vonDerUhr = SyncState(
            bestScore = 50,
            scene = SceneId.STADT.name,
            sceneChangedAt = 7_000L
        )

        store.applySync(vonDerUhr)

        assertEquals(SceneId.WIESE, store.selectedScene)
        assertEquals(0L, store.syncState().sceneChangedAt)

        // Der Rekord kommt nach — beim naechsten Abgleich ist die Kulisse
        // gedeckt und wird uebernommen.
        store.submitRun(score = 85, epochDay = 20_000L, month = 6, year = 2026)
        store.applySync(vonDerUhr)

        assertEquals(SceneId.STADT, store.selectedScene)
        assertEquals(7_000L, store.syncState().sceneChangedAt)
    }
}
