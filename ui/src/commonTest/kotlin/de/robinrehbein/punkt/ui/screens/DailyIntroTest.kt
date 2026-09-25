package de.robinrehbein.punkt.ui.screens

import de.robinrehbein.punkt.ui.data.FakeKeyValueStore
import de.robinrehbein.punkt.ui.data.GameStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * DAILY als Umschalter mit einmaliger Karte (Plan 7.2, 8.6 #4): Die Karte
 * kommt genau einmal pro Gerät, danach schaltet DAILY aus ↔ scharf. Der
 * Merker ist lokal und reist nicht mit dem Abgleich.
 */
class DailyIntroTest {

    /** Spielt einen Tap auf DAILY so aus, wie GameScreen ihn ausführt. */
    private class Startbildschirm(val store: GameStore) {
        var armed = false
        var cardShown = 0

        fun tapDaily() {
            when (dailyTap(armed, store.dailyIntroSeen)) {
                DailyTap.SHOW_INTRO -> {
                    store.markDailyIntroSeen()
                    cardShown++
                }
                DailyTap.ARM -> armed = true
                DailyTap.DISARM -> armed = false
            }
        }

        /** START auf der Karte. */
        fun start() {
            armed = true
        }

        /** MENÜ im Game-Over (backToMenu). */
        fun menu() {
            armed = false
        }
    }

    @Test
    fun `die Karte kommt genau einmal`() {
        val prefs = FakeKeyValueStore()
        val screen = Startbildschirm(GameStore(prefs))

        screen.tapDaily()
        assertEquals(1, screen.cardShown, "erster Tap zeigt die Karte")
        assertFalse(screen.armed, "die Karte allein schaltet nicht scharf")

        screen.tapDaily()
        screen.tapDaily()
        screen.tapDaily()
        assertEquals(1, screen.cardShown, "danach nie wieder")

        // Auch nicht nach einem Neustart der App.
        val neu = Startbildschirm(GameStore(prefs))
        neu.tapDaily()
        assertEquals(0, neu.cardShown)
        assertTrue(neu.armed)
    }

    @Test
    fun `die Karte zählt als gesehen, auch wenn sie ohne START geschlossen wird`() {
        val screen = Startbildschirm(GameStore(FakeKeyValueStore()))
        screen.tapDaily() // Karte, dann Zurück: nicht scharf
        assertFalse(screen.armed)
        screen.tapDaily()
        assertTrue(screen.armed, "der zweite Tap schaltet scharf, ohne Karte")
    }

    @Test
    fun `START schaltet scharf, danach ist DAILY ein Umschalter`() {
        val screen = Startbildschirm(GameStore(FakeKeyValueStore()))
        screen.tapDaily()
        screen.start()
        assertTrue(screen.armed)

        screen.tapDaily()
        assertFalse(screen.armed, "scharf -> aus")
        screen.tapDaily()
        assertTrue(screen.armed, "aus -> scharf")
    }

    @Test
    fun `MENÜ schaltet ab`() {
        val screen = Startbildschirm(GameStore(FakeKeyValueStore()))
        screen.tapDaily()
        screen.start()
        screen.menu()
        assertFalse(screen.armed)
    }

    @Test
    fun `die Entscheidung als Tabelle`() {
        assertEquals(DailyTap.SHOW_INTRO, dailyTap(armed = false, introSeen = false))
        assertEquals(DailyTap.ARM, dailyTap(armed = false, introSeen = true))
        assertEquals(DailyTap.DISARM, dailyTap(armed = true, introSeen = true))
        assertEquals(DailyTap.DISARM, dailyTap(armed = true, introSeen = false))
    }

    @Test
    fun `der Merker steht nicht im SyncState`() {
        val store = GameStore(FakeKeyValueStore())
        val vorher = store.syncState()

        store.markDailyIntroSeen()

        assertTrue(store.dailyIntroSeen)
        assertEquals(vorher, store.syncState(), "die Karte ist Erklärung, kein Fortschritt")
    }

    @Test
    fun `ein Abgleich setzt den Merker nicht`() {
        val uhr = GameStore(FakeKeyValueStore())
        uhr.markDailyIntroSeen()
        val telefon = GameStore(FakeKeyValueStore())

        telefon.applySync(uhr.syncState())

        assertFalse(telefon.dailyIntroSeen)
    }
}
