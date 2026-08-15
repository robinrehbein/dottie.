package de.robinrehbein.punkt.game

import androidx.annotation.StringRes
import de.robinrehbein.punkt.R

/**
 * Wählbare Ton-Sets — die dritte Sammlung neben den Skins und den
 * Kulissen. Name und Freischalt-Hinweis sind String-Ressourcen (DE/EN),
 * die UI löst sie per stringResource auf.
 *
 * Frequenzen, Hüllkurven und Schwellen liegen wie bei [DotScene] nicht
 * hier, sondern in [SoundBank] im :core-Modul; dieselbe Quelle nutzen
 * PWA, iOS und die Uhr. Diese Aufzählung trägt nur die Beschriftung —
 * und die Reihenfolge, in der die Auswahl sie zeigt.
 *
 * Ein Ton-Set entscheidet nie über einen Treffer: Jedes Set meldet jedes
 * Ereignis, nur eben anders. Genau deshalb darf es verdient werden und
 * die Zonenbreite nicht.
 */
enum class DotSound(
    val id: SoundSetId,
    @StringRes val titleRes: Int,
    @StringRes val unlockHintRes: Int?
) {
    KLASSIK(SoundSetId.KLASSIK, R.string.sound_klassik, null),
    GLOCKE(SoundSetId.GLOCKE, R.string.sound_glocke, R.string.sound_hint_glocke),
    AMBOSS(SoundSetId.AMBOSS, R.string.sound_amboss, R.string.sound_hint_amboss);

    /** Drei Balkenhöhen (0..1) für die Vorschau-Kachel in der Auswahl. */
    val chips: List<Float> get() = SoundBank.chips(id)

    fun isUnlocked(stats: DotSkin.Stats): Boolean =
        SoundBank.isUnlocked(id, stats.toSkinStats())

    companion object {
        /** Ton-Set zu einem gespeicherten Namen, KLASSIK als Fallback. */
        fun fromName(name: String?): DotSound =
            entries.firstOrNull { it.name == name } ?: KLASSIK

        /**
         * Der Eintrag zu einer [SoundSetId] aus :core — der Weg von einem
         * Ziel ([Goal.sound]) zu seiner Beschriftung.
         */
        fun of(id: SoundSetId): DotSound = entries.first { it.id == id }

        /** Wie viele Ton-Sets offen sind — reine Leistungsanzeige. */
        fun unlockedCount(stats: DotSkin.Stats): Int =
            SoundBank.unlockedCount(stats.toSkinStats())
    }
}
