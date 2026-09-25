package de.robinrehbein.punkt.ui.data

import de.robinrehbein.punkt.game.CardFrame
import de.robinrehbein.punkt.game.CardStyle
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SkinStats
import de.robinrehbein.punkt.game.SoundBank
import de.robinrehbein.punkt.game.SoundSetId

/**
 * Was in der Sammlung schon angesehen wurde — die Grundlage der
 * NEU-Markierung (Plan 7.1).
 *
 * Gespeichert wird eine Menge von Schlüsseln der Form `SKIN:MINZE`,
 * `SCENE:WUESTE`, `SOUND:GLOCKE` und `FRAME:KASKADE`, über die NAMEN wie
 * bei [TwistLessons]: Eine Maske über Ordinale verschöbe bei jedem
 * Einschub in eine Aufzählung still ihre Bedeutung.
 *
 * NEU ist, was offen ist und noch nicht angesehen wurde. Saison- und
 * Gönner-Skins bekommen nie eine NEU-Markierung (Plan 8.6 #12): Der
 * Saison-Skin hat im Game-Over seine eigene Feier, und ein Kauf ist kein
 * Fund.
 *
 * Bewusst rein lokal und nicht im SyncState: Was die Uhr gezeigt hat,
 * hat am Telefon niemand gesehen.
 */
object CollectionSeen {

    const val SKIN = "SKIN:"
    const val SCENE = "SCENE:"
    const val SOUND = "SOUND:"
    const val FRAME = "FRAME:"

    fun key(id: SkinId): String = SKIN + id.name
    fun key(id: SceneId): String = SCENE + id.name
    fun key(id: SoundSetId): String = SOUND + id.name
    fun key(id: CardFrame): String = FRAME + id.name

    /** Bekommt dieser Skin je eine NEU-Markierung? */
    fun canBeNew(id: SkinId): Boolean = !SkinPaint.isSeasonal(id) && !SkinPaint.isPatron(id)

    /**
     * Alle Kacheln, die jetzt offen sind und NEU sein könnten, als
     * Schlüssel. Saison- und Gönner-Skins fehlen hier absichtlich.
     */
    fun openKeys(stats: SkinStats): Set<String> = buildSet {
        SkinPaint.ORDER.forEach { if (canBeNew(it) && SkinPaint.isUnlocked(it, stats)) add(key(it)) }
        ScenePaint.ORDER.forEach { if (ScenePaint.isUnlocked(it, stats)) add(key(it)) }
        SoundBank.ORDER.forEach { if (SoundBank.isUnlocked(it, stats)) add(key(it)) }
        CardFrame.entries.forEach { if (CardStyle.isUnlocked(it, stats)) add(key(it)) }
    }

    /** Was jetzt NEU ist: offen, aber noch nicht angesehen. */
    fun newKeys(stats: SkinStats, seen: Set<String>): Set<String> = openKeys(stats) - seen

    /** Die neuen Welten in Sammlungs-Reihenfolge — für das Banner im Startbildschirm. */
    fun newScenes(newKeys: Set<String>): List<SceneId> =
        ScenePaint.ORDER.filter { key(it) in newKeys }

    /**
     * Die einmalige Übernahme beim ersten Lesen (Plan 8.5, AP-15).
     *
     * Alles, was heute offen ist, gilt als gesehen — sonst stünde nach dem
     * Update die halbe Sammlung auf NEU. Ausgenommen sind die Welten, die
     * nach den alten Schwellen noch zu waren ([legacyScenes], aus
     * `ScenePaint.legacyUnlocked`): Die hat erst die Welten-Leiter
     * geöffnet, und genau die sollen gefeiert werden. War der WELTRAUM
     * nach den alten Schwellen zu, gilt dasselbe für die KASKADE und alles
     * darüber — sie hängen an allen Welten und sind mit der Leiter
     * gekommen.
     *
     * Bei einer Neuinstallation ist nur der Anfangsbestand offen, und der
     * war auch nach den alten Schwellen offen: nichts ist NEU.
     */
    fun migrated(stats: SkinStats, legacyScenes: Set<String>): Set<String> {
        val neuDurchLeiter = buildSet {
            ScenePaint.ORDER.forEach { if (it.name !in legacyScenes) add(key(it)) }
            if (SceneId.WELTRAUM.name !in legacyScenes) {
                CardFrame.entries
                    .filter { it.ordinal >= CardFrame.KASKADE.ordinal }
                    .forEach { add(key(it)) }
            }
        }
        return openKeys(stats) - neuDurchLeiter
    }

    /**
     * `"SKIN:MINZE,SCENE:WIESE"` → Menge. Tolerant gegen alles, was eine
     * andere Version geschrieben haben könnte: Leerraum wird getrimmt,
     * Leeres fällt weg, unbekannte Schlüssel (eine Welt aus einer neueren
     * Version, eine fremde Art) bleiben stehen, damit sie ein Downgrade
     * und das nächste Schreiben überleben. Nur Einträge ohne Art-Präfix
     * sind sicher Müll und fallen weg.
     */
    fun decode(stored: String?): Set<String> =
        stored?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && ':' in it && !it.startsWith(':') && !it.endsWith(':') }
            ?.toSet()
            ?: emptySet()

    /** Und zurück, sortiert, damit derselbe Stand immer dieselbe Zeichenkette ist. */
    fun encode(keys: Collection<String>): String =
        keys.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted().joinToString(SEPARATOR)

    private const val SEPARATOR = ","
}
