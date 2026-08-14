package de.robinrehbein.punkt.game

import androidx.annotation.StringRes
import de.robinrehbein.punkt.R

/**
 * Wählbare Kulissen — die zweite Sammlung neben den Skins. Name und
 * Freischalt-Hinweis sind String-Ressourcen (DE/EN), die UI löst sie per
 * stringResource auf.
 *
 * Farben, Requisiten und Schwellen liegen wie bei [DotSkin] nicht hier,
 * sondern in [ScenePaint] im :core-Modul; dieselbe Quelle nutzen PWA, iOS
 * und die Uhr. Diese Aufzählung trägt nur die Beschriftung — und die
 * Reihenfolge, in der die Auswahl sie zeigt.
 *
 * Eine Kulisse entscheidet nie über einen Treffer: Bahn, Zielzone und
 * Falle sehen in allen Kulissen gleich aus. Genau deshalb darf sie
 * verkauft werden und die Bahn nicht.
 */
enum class DotScene(
    val id: SceneId,
    @StringRes val titleRes: Int,
    @StringRes val unlockHintRes: Int?
) {
    WIESE(SceneId.WIESE, R.string.scene_wiese, null),
    WUESTE(SceneId.WUESTE, R.string.scene_wueste, R.string.scene_hint_wueste),
    MEER(SceneId.MEER, R.string.scene_meer, R.string.scene_hint_meer),
    BERG(SceneId.BERG, R.string.scene_berg, R.string.scene_hint_berg),
    STADT(SceneId.STADT, R.string.scene_stadt, R.string.scene_hint_stadt),
    WELTRAUM(SceneId.WELTRAUM, R.string.scene_weltraum, R.string.scene_hint_weltraum);

    /** Die komplette Beschreibung — Himmel, Wolke, Boden, Requisiten. */
    val scene: Scene get() = ScenePaint.of(id)

    /** Drei Farben für die Vorschau-Kachel in der Auswahl. */
    val chips: List<Long> get() = ScenePaint.chips(id)

    fun isUnlocked(stats: DotSkin.Stats): Boolean =
        ScenePaint.isUnlocked(id, stats.toSkinStats())

    companion object {
        /** Kulisse zu einem gespeicherten Namen, WIESE als Fallback. */
        fun fromName(name: String?): DotScene =
            entries.firstOrNull { it.name == name } ?: WIESE

        /**
         * Der Eintrag zu einer [SceneId] aus :core — der Weg von einem
         * Ziel ([Goal.scene]) zu seiner Beschriftung.
         */
        fun of(id: SceneId): DotScene = entries.first { it.id == id }

        /** Wie viele Kulissen offen sind — reine Leistungsanzeige. */
        fun unlockedCount(stats: DotSkin.Stats): Int =
            ScenePaint.unlockedCount(stats.toSkinStats())
    }
}
