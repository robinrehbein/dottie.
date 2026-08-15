package de.robinrehbein.punkt.game

import androidx.annotation.StringRes
import de.robinrehbein.punkt.R

/**
 * Wählbare Rahmen der Score-Karte — die vierte Sammlung neben den Skins,
 * den Kulissen und den Ton-Sets. Name und Freischalt-Hinweis sind
 * String-Ressourcen (DE/EN), die UI löst sie per stringResource auf.
 *
 * Maße, Farben und Schwellen liegen wie bei [DotScene] und [DotSound]
 * nicht hier, sondern in [CardStyle] im :core-Modul; dieselbe Quelle
 * nutzen iOS und später jeder weitere Port. Diese Aufzählung trägt nur
 * die Beschriftung und die Reihenfolge, in der die Auswahl sie zeigt.
 *
 * Der Unterschied zu den anderen drei Sammlungen ist, wo der Rahmen
 * auftaucht: nicht im Spiel, sondern auf dem, was geteilt wird. Er
 * entscheidet deshalb erst recht nichts über einen Treffer — er ist die
 * einzige Sammlung, die andere Leute zu sehen bekommen.
 */
enum class DotCardFrame(
    val id: CardFrame,
    @StringRes val titleRes: Int,
    @StringRes val unlockHintRes: Int?
) {
    SCHLICHT(CardFrame.SCHLICHT, R.string.frame_schlicht, null),
    DOPPELLINIE(CardFrame.DOPPELLINIE, R.string.frame_doppellinie, R.string.frame_hint_doppellinie),
    ZINNEN(CardFrame.ZINNEN, R.string.frame_zinnen, R.string.frame_hint_zinnen),
    PRACHT(CardFrame.PRACHT, R.string.frame_pracht, R.string.frame_hint_pracht);

    fun isUnlocked(stats: DotSkin.Stats): Boolean =
        CardStyle.isUnlocked(id, stats.toSkinStats())

    companion object {
        /**
         * Rahmen zu einem gespeicherten Namen — null heißt "nie gewählt",
         * und das ist etwas anderes als SCHLICHT: Wer nie gewählt hat,
         * bekommt automatisch seine höchste verdiente Stufe, wer SCHLICHT
         * gewählt hat, bekommt SCHLICHT. Ein Fallback auf den ersten
         * Eintrag würde beide verwechseln und jeder Sammlung beim ersten
         * Start den Rahmen abnehmen.
         */
        fun fromName(name: String?): DotCardFrame? =
            entries.firstOrNull { it.name == name }

        /** Der Eintrag zu einer [CardFrame] aus :core. */
        fun of(id: CardFrame): DotCardFrame = entries.first { it.id == id }

        /**
         * Der Rahmen, den die Karte tatsächlich trägt: die Wahl, falls sie
         * verdient ist — sonst die höchste verdiente Stufe.
         */
        fun effective(gewaehlt: DotCardFrame?, stats: DotSkin.Stats): DotCardFrame =
            of(CardStyle.frame(gewaehlt?.id, stats.toSkinStats()))

        /** Wie viele Stufen offen sind — reine Leistungsanzeige. */
        fun unlockedCount(stats: DotSkin.Stats): Int =
            CardStyle.unlockedCount(stats.toSkinStats())
    }
}
