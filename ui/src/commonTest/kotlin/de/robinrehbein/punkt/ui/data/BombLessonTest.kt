package de.robinrehbein.punkt.ui.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * „BOMBE = NIE TIPPEN“ kommt einmal pro Gerät (Plan 8.6 #9): beim ersten
 * Bomben-Tod, danach nie wieder, auch nicht nach einem Neustart der App.
 */
class BombLessonTest {

    @Test
    fun `die Lektion kommt beim ersten Mal`() {
        val store = GameStore(FakeKeyValueStore())

        assertFalse(store.bombLessonSeen)
        assertTrue(store.takeBombLesson(), "erster Bomben-Tod zeigt die Lektion")
        assertTrue(store.bombLessonSeen)
    }

    @Test
    fun `der Merker verhindert die Lektion beim zweiten Mal`() {
        val store = GameStore(FakeKeyValueStore())

        store.takeBombLesson()

        assertFalse(store.takeBombLesson(), "zweiter Bomben-Tod: keine Lektion mehr")
    }

    @Test
    fun `der Merker überlebt den Neustart`() {
        val prefs = FakeKeyValueStore()
        GameStore(prefs).takeBombLesson()

        assertFalse(GameStore(prefs).takeBombLesson(), "nach dem Neustart wieder gezeigt")
    }

    @Test
    fun `der Merker bleibt lokal und geht nicht in den Sync`() {
        val store = GameStore(FakeKeyValueStore())
        val vorher = store.syncState()

        store.takeBombLesson()

        assertEquals(vorher, store.syncState(), "der Merker darf den SyncState nicht ändern")
    }
}
