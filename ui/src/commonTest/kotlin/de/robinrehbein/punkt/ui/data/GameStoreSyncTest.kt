package de.robinrehbein.punkt.ui.data

import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ein Speicher im Arbeitsspeicher — genau das, was [GameStore] von einer
 * Plattform erwartet. Damit sind die Spielstands-Regeln ohne Android und
 * ohne iOS prüfbar; das war der Sinn der [KeyValueStore]-Schnittstelle.
 */
private class FakeKeyValueStore : KeyValueStore {

    private val werte = mutableMapOf<String, Any>()

    override fun int(key: String, fallback: Int): Int = werte[key] as? Int ?: fallback

    override fun long(key: String, fallback: Long): Long = werte[key] as? Long ?: fallback

    override fun boolean(key: String, fallback: Boolean): Boolean =
        werte[key] as? Boolean ?: fallback

    override fun string(key: String): String? = werte[key] as? String

    override fun edit(block: KeyValueEditor.() -> Unit) {
        object : KeyValueEditor {
            override fun putInt(key: String, value: Int) { werte[key] = value }
            override fun putLong(key: String, value: Long) { werte[key] = value }
            override fun putBoolean(key: String, value: Boolean) { werte[key] = value }
            override fun putString(key: String, value: String) { werte[key] = value }
            override fun remove(key: String) { werte.remove(key) }
        }.block()
    }
}

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
    }
}
