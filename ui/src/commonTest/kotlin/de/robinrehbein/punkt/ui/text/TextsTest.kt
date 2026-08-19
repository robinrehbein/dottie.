@file:OptIn(ExperimentalResourceApi::class)

package de.robinrehbein.punkt.ui.text

import de.robinrehbein.punkt.game.MedalPaint
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.SkinFamily
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SoundBank
import de.robinrehbein.punkt.game.Twist
import de.robinrehbein.punkt.ui.resources.Res
import de.robinrehbein.punkt.ui.resources.allStringResources
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Der Preis dafuer, dass die Textschluessel aus dem Namen gerechnet
 * werden statt in einer Tabelle zu stehen: Ein neuer Skin in `:core`
 * bringt seinen Text nicht mit, und ohne diesen Test faellt das erst als
 * leeres Label im Menue auf.
 *
 * Vorher hing dieselbe Absicherung daran, dass jeder Eintrag in `DotSkin`
 * eine `@StringRes`-ID trug — 55 Zeilen Zuordnung, die es nur dafuer
 * gab. Der Test ist billiger als die Tabelle.
 */
class TextsTest {

    private val keys: Set<String> get() = Res.allStringResources.keys

    @Test
    fun `die Texttabelle ist da`() {
        assertTrue(keys.isNotEmpty(), "keine Texte gefunden")
    }

    @Test
    fun `jeder Skin hat Namen und Hinweis`() {
        SkinPaint.ORDER.forEach { skin ->
            assertNotNull(Texts.resource(Texts.skinTitleKey(skin)), "kein Name fuer ${skin.name}")
            Texts.skinHintKey(skin)?.let {
                assertNotNull(Texts.resource(it), "kein Hinweis fuer ${skin.name}")
            }
        }
        // KLASSIK ist von Anfang an offen und braucht als einziger keinen.
        assertNull(Texts.skinHintKey(SkinId.KLASSIK))
    }

    @Test
    fun `jede Kulisse und jedes Ton-Set hat Namen und Hinweis`() {
        ScenePaint.ORDER.forEach { scene ->
            assertNotNull(Texts.resource(Texts.sceneTitleKey(scene)), "kein Name fuer ${scene.name}")
            Texts.sceneHintKey(scene)?.let {
                assertNotNull(Texts.resource(it), "kein Hinweis fuer ${scene.name}")
            }
        }
        SoundBank.ORDER.forEach { sound ->
            assertNotNull(Texts.resource(Texts.soundTitleKey(sound)), "kein Name fuer ${sound.name}")
            Texts.soundHintKey(sound)?.let {
                assertNotNull(Texts.resource(it), "kein Hinweis fuer ${sound.name}")
            }
        }
    }

    @Test
    fun `jede Familie, jede Medaille und jeder Twist hat einen Text`() {
        SkinFamily.entries.forEach {
            assertNotNull(Texts.resource(Texts.familyTitleKey(it)), "keine Ueberschrift fuer ${it.name}")
        }
        MedalPaint.ORDER.forEach {
            assertNotNull(Texts.resource(Texts.medalNameKey(it)), "kein Name fuer ${it.name}")
        }
        // Ein Twist ohne Erklaerung faellt sonst erst dem auf, der ihn
        // zum ersten Mal freischaltet — und dort steht dann der
        // Schluessel selbst im Game-Over.
        Twist.entries.forEach {
            assertNotNull(
                Texts.resource(Texts.twistLessonKey(it)),
                "keine Erklaerung fuer ${it.name}"
            )
        }
    }

    @Test
    fun `jeder Skin liegt in genau einer Familie, in Menue-Reihenfolge`() {
        val gesehen = SkinFamily.entries.flatMap { familie ->
            SkinPaint.ORDER.filter { SkinPaint.family(it) == familie }
        }
        assertTrue(gesehen == SkinPaint.ORDER, "Familien decken die Sammlung nicht in ihrer Reihenfolge ab")
    }
}
