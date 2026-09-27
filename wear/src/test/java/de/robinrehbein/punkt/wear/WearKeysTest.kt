package de.robinrehbein.punkt.wear

import android.view.KeyEvent
import de.robinrehbein.punkt.game.GamePhase
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Die Tastenbelegung der Uhr (WearKeys): Multifunktionstasten tippen
 * immer, ZURÜCK nur im laufenden Lauf, die Home-Taste nie. Die Keycodes
 * sind Konstanten und stehen auch im Unit-Test ohne Android bereit.
 */
class WearKeysTest {

    private fun down(
        key: Int,
        phase: GamePhase,
        repeat: Int = 0,
        elapsed: Float = 5f,
        picker: Boolean = false,
        physical: Boolean = true,
        backTaps: Boolean = true
    ) = WearKeys.onDown(key, repeat, phase, elapsed, picker, physical, backTaps)

    private val stems = listOf(KeyEvent.KEYCODE_STEM_1, KeyEvent.KEYCODE_STEM_2, KeyEvent.KEYCODE_STEM_3)

    @Test
    fun `Multifunktionstasten tippen in jeder Phase`() {
        for (key in stems) for (phase in GamePhase.entries) {
            assertEquals("$key in $phase", WearKeyAction.TAP, down(key, phase))
        }
    }

    @Test
    fun `Eine gehaltene Taste tippt nie ein zweites Mal`() {
        for (key in stems) assertEquals(WearKeyAction.SWALLOW, down(key, GamePhase.RUNNING, repeat = 1))
        assertEquals(WearKeyAction.SWALLOW, down(KeyEvent.KEYCODE_BACK, GamePhase.RUNNING, repeat = 3))
    }

    @Test
    fun `Im offenen Waehler bestaetigen Taste und Zurueck`() {
        for (key in stems) assertEquals(WearKeyAction.CONFIRM_PICKER, down(key, GamePhase.READY, picker = true))
        assertEquals(
            WearKeyAction.CONFIRM_PICKER,
            down(KeyEvent.KEYCODE_BACK, GamePhase.READY, picker = true)
        )
    }

    @Test
    fun `Zurueck tippt nur im laufenden Lauf`() {
        assertEquals(WearKeyAction.TAP, down(KeyEvent.KEYCODE_BACK, GamePhase.RUNNING))
        assertEquals(WearKeyAction.SYSTEM, down(KeyEvent.KEYCODE_BACK, GamePhase.READY))
    }

    @Test
    fun `Zurueck direkt nach dem Tod schliesst die App nicht`() {
        // Wer mit Zurück getippt hat und gestorben ist, drückt gerade noch
        // einmal: Sturz und Neustart-Sperre schlucken die Taste.
        assertEquals(WearKeyAction.SWALLOW, down(KeyEvent.KEYCODE_BACK, GamePhase.DYING))
        assertEquals(WearKeyAction.SWALLOW, down(KeyEvent.KEYCODE_BACK, GamePhase.OVER, elapsed = 0.2f))
        assertEquals(
            WearKeyAction.SYSTEM,
            down(KeyEvent.KEYCODE_BACK, GamePhase.OVER, elapsed = WearKeys.BACK_GRACE_SECONDS)
        )
    }

    @Test
    fun `Ein vom System erzeugtes Zurueck bleibt Zurueck`() {
        assertEquals(WearKeyAction.SYSTEM, down(KeyEvent.KEYCODE_BACK, GamePhase.RUNNING, physical = false))
    }

    @Test
    fun `Ohne Zurueck-Tipp bleibt Zurueck immer Zurueck`() {
        for (phase in GamePhase.entries) {
            assertEquals(WearKeyAction.SYSTEM, down(KeyEvent.KEYCODE_BACK, phase, backTaps = false))
        }
    }

    @Test
    fun `Home und alle anderen Tasten gehen unveraendert ans System`() {
        for (key in listOf(KeyEvent.KEYCODE_STEM_PRIMARY, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_ENTER)) {
            for (phase in GamePhase.entries) {
                assertEquals("$key in $phase", WearKeyAction.SYSTEM, down(key, phase))
                assertEquals("$key im Waehler", WearKeyAction.SYSTEM, down(key, phase, picker = true))
            }
        }
    }
}
