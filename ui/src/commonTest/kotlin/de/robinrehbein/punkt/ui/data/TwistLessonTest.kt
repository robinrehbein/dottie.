package de.robinrehbein.punkt.ui.data

import de.robinrehbein.punkt.game.Twist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Welcher Twist im Game-Over erklaert wird — die eine Regel, an der die
 * ganze Umstellung haengt: einmal je Twist, einer je Tod, und das
 * Gemerkte ueberlebt den Neustart.
 *
 * Am Geraet faellt ein Fehler hier erst nach Wochen auf ("wieso erklaert
 * mir das Spiel schon wieder die Falle?"), deshalb steht die Auswahl als
 * reine Funktion ([TwistLessons.next]) da und nicht mitten in der
 * Oberflaeche.
 */
class TwistLessonTest {

    private fun store() = GameStore(FakeKeyValueStore())

    @Test
    fun `der erste noch unerklaerte Twist des Laufs gewinnt`() {
        val store = store()

        // Ein langer Lauf: PULS und DRIFT sind schon freigeschaltet
        // gewesen, erklaert wurde noch keiner — also der erste.
        assertEquals(
            Twist.PULSE,
            store.twistToExplain(listOf(Twist.PULSE, Twist.DRIFT))
        )
    }

    @Test
    fun `der zweite folgt beim naechsten Tod`() {
        val store = store()
        val lauf = listOf(Twist.PULSE, Twist.DRIFT)

        val ersterTod = store.twistToExplain(lauf)
        store.markTwistExplained(ersterTod!!)

        assertEquals(Twist.PULSE, ersterTod)
        assertEquals(Twist.DRIFT, store.twistToExplain(lauf), "der naechste ist dran")

        store.markTwistExplained(Twist.DRIFT)
        assertNull(store.twistToExplain(lauf), "danach ist Ruhe")
    }

    @Test
    fun `ein Lauf ohne neuen Twist erklaert nichts`() {
        val store = store()

        // Wer bei 4 Punkten stirbt, hat keinen Twist gesehen.
        assertNull(store.twistToExplain(emptyList()))

        // Und ein bereits erklaerter zaehlt nicht noch einmal.
        store.markTwistExplained(Twist.FAKE)
        assertNull(store.twistToExplain(listOf(Twist.FAKE)))
    }

    @Test
    fun `das Gemerkte ueberlebt den Neustart`() {
        val prefs = FakeKeyValueStore()
        GameStore(prefs).markTwistExplained(Twist.FAKE)

        // Neuer Speicher auf denselben Preferences = App neu gestartet.
        val nachNeustart = GameStore(prefs)

        assertEquals(setOf("FAKE"), nachNeustart.explainedTwists)
        assertNull(nachNeustart.twistToExplain(listOf(Twist.FAKE)))
        assertEquals(Twist.CHAIN, nachNeustart.twistToExplain(listOf(Twist.FAKE, Twist.CHAIN)))
    }

    @Test
    fun `ein unbekannter Name ueberlebt ein Downgrade`() {
        val prefs = FakeKeyValueStore()
        prefs.edit { putString("twists_explained", "FAKE,WIRBEL") }
        val store = GameStore(prefs)

        store.markTwistExplained(Twist.CHAIN)

        // WIRBEL kennt diese Version nicht — weggeschrieben werden darf er
        // trotzdem nicht, sonst erklaert die neuere ihn ein zweites Mal.
        assertEquals(setOf("FAKE", "WIRBEL", "CHAIN"), store.explainedTwists)
    }

    // Fallen-Tod: Die Falle drängelt vor (Plan 3.4, AP-12).

    @Test
    fun `nach einem Fallen-Tod draengelt die Falle vor den unerklaerten PULS`() {
        val store = store()
        val lauf = listOf(Twist.PULSE, Twist.DRIFT, Twist.GHOST, Twist.FAKE)

        assertEquals(Twist.FAKE, store.twistToExplain(lauf, diedInTrap = true))
    }

    @Test
    fun `ohne Fallen-Tod bleibt die Reihenfolge`() {
        val store = store()
        val lauf = listOf(Twist.PULSE, Twist.DRIFT, Twist.GHOST, Twist.FAKE)

        assertEquals(Twist.PULSE, store.twistToExplain(lauf, diedInTrap = false))
        assertEquals(Twist.PULSE, store.twistToExplain(lauf))
    }

    @Test
    fun `ist die Falle schon erklaert, kommt nach dem Fallen-Tod der naechste Twist`() {
        val store = store()
        val lauf = listOf(Twist.PULSE, Twist.DRIFT, Twist.GHOST, Twist.FAKE)
        store.markTwistExplained(Twist.FAKE)

        assertEquals(Twist.PULSE, store.twistToExplain(lauf, diedInTrap = true))

        store.markTwistExplained(Twist.PULSE)
        store.markTwistExplained(Twist.DRIFT)
        store.markTwistExplained(Twist.GHOST)
        assertNull(store.twistToExplain(lauf, diedInTrap = true), "alles erklaert")
    }

    @Test
    fun `nach dem Vordraengeln folgen die uebrigen in alter Reihenfolge`() {
        val store = store()
        val lauf = listOf(Twist.PULSE, Twist.DRIFT, Twist.FAKE)

        store.markTwistExplained(store.twistToExplain(lauf, diedInTrap = true)!!)

        assertEquals(Twist.PULSE, store.twistToExplain(lauf, diedInTrap = true))
        store.markTwistExplained(Twist.PULSE)
        assertEquals(Twist.DRIFT, store.twistToExplain(lauf))
    }

    @Test
    fun `die vorgedraengelte Falle bleibt nach dem Neustart erklaert`() {
        val prefs = FakeKeyValueStore()
        val vorher = GameStore(prefs)
        val lauf = listOf(Twist.PULSE, Twist.FAKE)
        val erklaert = vorher.twistToExplain(lauf, diedInTrap = true)
        vorher.markTwistExplained(erklaert!!)

        // Neuer Speicher auf denselben Preferences = App neu gestartet.
        val nachNeustart = GameStore(prefs)

        assertEquals(Twist.FAKE, erklaert)
        assertEquals(setOf("FAKE"), nachNeustart.explainedTwists)
        assertEquals(Twist.PULSE, nachNeustart.twistToExplain(lauf, diedInTrap = true))
    }

    @Test
    fun `der Fallen-Tod zaehlt auch, wenn die Falle in der Liste fehlt`() {
        // Die Liste der Freischaltungen ist nur ein Merker der Oberfläche;
        // der Tod in der Falle beweist, dass sie im Lauf war.
        assertEquals(Twist.FAKE, TwistLessons.next(listOf(Twist.PULSE), emptySet(), diedInTrap = true))
        assertEquals(Twist.PULSE, TwistLessons.next(listOf(Twist.PULSE), setOf("FAKE"), diedInTrap = true))
    }
}
