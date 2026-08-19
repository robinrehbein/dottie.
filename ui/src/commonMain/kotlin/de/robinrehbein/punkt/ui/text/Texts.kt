@file:OptIn(ExperimentalResourceApi::class)

package de.robinrehbein.punkt.ui.text

import androidx.compose.runtime.Composable
import de.robinrehbein.punkt.game.CardFrame
import de.robinrehbein.punkt.game.MedalId
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.SkinFamily
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SoundSetId
import de.robinrehbein.punkt.game.Twist
import de.robinrehbein.punkt.ui.resources.Res
import de.robinrehbein.punkt.ui.resources.allStringArrayResources
import de.robinrehbein.punkt.ui.resources.allStringResources
import de.robinrehbein.punkt.ui.resources.skin_lava
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.StringArrayResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Der Weg von einem Namen aus `:core` zu seinem Text.
 *
 * Bis v2.24 trugen dafuer eigene Aufzaehlungen in `:app` die
 * Ressourcen-IDs: `DotSkin(SkinId.LAVA, Res.string.skin_lava, …)` — 42
 * Zeilen fuer die Skins, sechs fuer die Kulissen, drei fuer die
 * Ton-Sets, vier fuer die Medaillen. Der iOS-Port bildete dieselbe
 * Zuordnung noch einmal, dort aber aus dem Namen gerechnet
 * (`"skin_" + name.lowercase()`).
 *
 * Beide Wege sagen dasselbe, nur schreibt der eine es 55-mal auf. Hier
 * bleibt der gerechnete: Ein neuer Skin in `:core` braucht keinen
 * Eintrag mehr, nur seinen Text — und dass der da ist, prueft ein Test.
 */
object Texts {

    /**
     * Alle Texte nach Schluessel. Compose Resources erzeugt daraus sonst
     * nur getippte Konstanten (`Res.string.skin_lava`); fuer eine aus dem
     * Namen gerechnete Suche braucht es die Tabelle.
     */
    private val byKey: Map<String, StringResource> get() = Res.allStringResources

    /** Der Text zu einem Schluessel — null, wenn es ihn nicht gibt. */
    fun resource(key: String): StringResource? = byKey[key]

    /** Dasselbe fuer die Spott-Listen. */
    fun arrayResource(key: String): StringArrayResource? = Res.allStringArrayResources[key]

    fun skinTitleKey(id: SkinId): String = "skin_" + id.name.lowercase()

    fun frameTitleKey(id: CardFrame): String = "frame_" + id.name.lowercase()

    /**
     * Der Freischalt-Hinweis eines Kartenrahmens. SCHLICHT ist der Stand,
     * mit dem jeder anfaengt — er hat keinen.
     */
    fun frameHintKey(id: CardFrame): String? =
        if (id == CardFrame.SCHLICHT) null else "frame_hint_" + id.name.lowercase()

    /**
     * Der Freischalt-Hinweis eines Skins.
     *
     * Zwei Ausnahmen von der Namensregel, und beide sind inhaltlich:
     * KLASSIK ist von Anfang an offen und braucht keinen Hinweis. Die drei
     * Goenner-Skins teilen sich einen — sie werden gekauft, nicht
     * verdient, und drei Zeilen mit demselben Satz waeren drei Zeilen zu
     * viel.
     */
    fun skinHintKey(id: SkinId): String? = when {
        id == SkinId.KLASSIK -> null
        SkinPaint.isPatron(id) -> "skin_hint_goenner"
        else -> "skin_hint_" + id.name.lowercase()
    }

    fun sceneTitleKey(id: SceneId): String = "scene_" + id.name.lowercase()

    fun sceneHintKey(id: SceneId): String? =
        if (id == SceneId.WIESE) null else "scene_hint_" + id.name.lowercase()

    fun soundTitleKey(id: SoundSetId): String = "sound_" + id.name.lowercase()

    fun soundHintKey(id: SoundSetId): String? =
        if (id == SoundSetId.KLASSIK) null else "sound_hint_" + id.name.lowercase()

    fun familyTitleKey(family: SkinFamily): String = "skin_family_" + family.name.lowercase()

    fun medalNameKey(id: MedalId): String = "medal_" + id.name.lowercase()

    /**
     * Die kurze Erklaerung eines Twists fuer das Game-Over. Nicht zu
     * verwechseln mit `twist_*_text`, dem ausfuehrlichen Satz in der
     * Hilfe: Diese Zeile steht neben einer Punktzahl, die gerade jemanden
     * geaergert hat, und muss in einem Blick gelesen sein.
     */
    fun twistLessonKey(twist: Twist): String = "twist_learned_" + twist.name.lowercase()
}

/**
 * Text zu einem gerechneten Schluessel. Fehlt er, steht der Schluessel
 * selbst da — sichtbar falsch ist besser als leer, und der Test unten
 * sorgt dafuer, dass es nie so weit kommt.
 */
@Composable
fun textFor(key: String): String =
    Texts.resource(key)?.let { stringResource(it) } ?: key

@Composable
fun textForOrNull(key: String?): String? = key?.let { textFor(it) }

@Composable
fun skinTitle(id: SkinId): String = textFor(Texts.skinTitleKey(id))

@Composable
fun skinHint(id: SkinId): String? = textForOrNull(Texts.skinHintKey(id))

@Composable
fun sceneTitle(id: SceneId): String = textFor(Texts.sceneTitleKey(id))

@Composable
fun sceneHint(id: SceneId): String? = textForOrNull(Texts.sceneHintKey(id))

@Composable
fun soundTitle(id: SoundSetId): String = textFor(Texts.soundTitleKey(id))

@Composable
fun soundHint(id: SoundSetId): String? = textForOrNull(Texts.soundHintKey(id))

@Composable
fun familyTitle(family: SkinFamily): String = textFor(Texts.familyTitleKey(family))

@Composable
fun medalName(id: MedalId): String = textFor(Texts.medalNameKey(id))

@Composable
fun frameTitle(id: CardFrame): String = textFor(Texts.frameTitleKey(id))

@Composable
fun frameHint(id: CardFrame): String? = textForOrNull(Texts.frameHintKey(id))

@Composable
fun twistLesson(twist: Twist): String = textFor(Texts.twistLessonKey(twist))
