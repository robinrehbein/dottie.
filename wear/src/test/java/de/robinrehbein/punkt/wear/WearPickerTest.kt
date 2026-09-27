package de.robinrehbein.punkt.wear

import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.SoundSetId
import de.robinrehbein.punkt.game.Twist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Cursor der Wähler (Krone) und die Twist-Erklärung im Game-Over. */
class WearPickerTest {

    private val worlds = listOf(SceneId.WIESE, SceneId.WUESTE, SceneId.BERG)

    @Test
    fun `Die Krone schiebt den Cursor zyklisch in beide Richtungen`() {
        assertEquals(SceneId.WUESTE, WearPickerCursor.step(worlds, SceneId.WIESE, 1))
        assertEquals(SceneId.WIESE, WearPickerCursor.step(worlds, SceneId.BERG, 1))
        assertEquals(SceneId.BERG, WearPickerCursor.step(worlds, SceneId.WIESE, -1))
        assertEquals(SceneId.WUESTE, WearPickerCursor.step(worlds, SceneId.WIESE, 7))
    }

    @Test
    fun `Ein Eintrag ausserhalb der Liste startet beim ersten`() {
        // Eine Welt, die nur über den Abgleich kam und hier nicht als offen gilt.
        assertEquals(SceneId.WUESTE, WearPickerCursor.step(worlds, SceneId.STADT, 1))
    }

    @Test
    fun `Eine leere Liste laesst die Wahl stehen`() {
        assertEquals(SoundSetId.ORGEL, WearPickerCursor.step(emptyList(), SoundSetId.ORGEL, 1))
    }

    @Test
    fun `Erklaert wird der erste neue Twist des Laufs`() {
        val run = listOf(Twist.PULSE, Twist.DRIFT)
        assertEquals(Twist.PULSE, WearLessons.next(run, emptySet(), diedInTrap = false))
        assertEquals(Twist.DRIFT, WearLessons.next(run, setOf("PULSE"), diedInTrap = false))
        assertNull(WearLessons.next(run, setOf("PULSE", "DRIFT"), diedInTrap = false))
    }

    @Test
    fun `Nach einem Tod in den Bomben draengelt deren Erklaerung vor`() {
        assertEquals(Twist.FAKE, WearLessons.next(listOf(Twist.PULSE), emptySet(), diedInTrap = true))
        assertEquals(Twist.PULSE, WearLessons.next(listOf(Twist.PULSE), setOf("FAKE"), diedInTrap = true))
    }

    @Test
    fun `Erklaerte Twists ueberstehen Speichern und Laden`() {
        val stored = WearLessons.encode(listOf("FAKE", "CHAIN"))
        assertEquals(setOf("FAKE", "CHAIN"), WearLessons.decode(stored))
        assertEquals(emptySet<String>(), WearLessons.decode(null))
        assertEquals(emptySet<String>(), WearLessons.decode(""))
    }
}
