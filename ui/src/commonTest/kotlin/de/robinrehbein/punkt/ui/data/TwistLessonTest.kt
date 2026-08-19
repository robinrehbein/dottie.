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
}
