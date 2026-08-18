package de.robinrehbein.punkt.wear

import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.SyncState

/**
 * Die Rechnung hinter [WearGameController.applySync], herausgezogen aus
 * den Prefs: Aus dem eigenen und dem hereingereichten Stand entstehen die
 * Stände, gegen die ein per Abgleich gewählter Skin geprüft wird.
 *
 * Zahlen wachsen nur (Maximum), Masken werden verodert — dieselbe Regel
 * wie SyncState.mergedWith in :core. Ein Monat oder ein Saison-Skin, den
 * nur eine Seite gesehen hat, darf beim Zusammenführen nicht
 * verlorengehen.
 */
internal object WearSyncMerge {

    /**
     * [patronOwned] kommt NICHT aus dem Austauschformat: Der Gönner-Kauf
     * hängt am Google-Konto und wird auf der Uhr über Play selbst
     * ermittelt (siehe [WearPatron]). Ein mitgeschicktes Flag wäre
     * fälschbar — hier gilt deshalb allein der lokale Spiegel.
     */
    fun skinStats(
        before: SyncState,
        incoming: SyncState,
        patronOwned: Boolean
    ): WearDotSkin.Stats = WearDotSkin.Stats(
        bestScore = maxOf(before.bestScore, incoming.bestScore),
        bestPerfectStreak = maxOf(before.bestPerfectStreak, incoming.bestPerfectStreak),
        // Die Daily-Serie zählt hier mit ihrem BESTWERT, nicht mit dem
        // laufenden Stand: Der fällt nach einer Lücke auf 1 zurück und
        // würde einen längst verdienten AURORA wieder wegnehmen. Dass der
        // Bestwert auch bei einer Gegenseite ohne dieses Feld nie unter
        // deren laufende Serie rutscht, stellt StatsSync beim Lesen sicher.
        bestDailyStreak = maxOf(before.bestDailyStreak, incoming.bestDailyStreak),
        runCount = maxOf(before.runCount, incoming.runCount),
        totalScore = maxOf(before.totalScore, incoming.totalScore),
        daysPlayed = maxOf(before.daysPlayed, incoming.daysPlayed),
        // Stats will die ANZAHL verschiedener Monate, gespeichert ist die
        // Maske — also erst verodern, dann die Bits zählen.
        monthsPlayed = Integer.bitCount(before.monthsPlayed or incoming.monthsPlayed),
        seasonEarned = before.seasonEarned or incoming.seasonEarned,
        patronOwned = patronOwned
    )

    /**
     * Welche Kulisse applySync übernehmen soll — null heißt: keine, der
     * bestehende Stand (samt Zeitstempel) bleibt stehen. Gleiche Regel wie
     * beim Skin: Nur eine WIRKLICH neuere Wahl schlägt die bestehende, und
     * nur wenn die zusammengeführten Stände sie hergeben.
     *
     * Die Uhr wählt selbst nie eine Kulisse (sie spiegelt nur das
     * Telefon), trotzdem lohnt die Prüfung: Für WELTRAUM ("alle anderen
     * Kulissen gesammelt") zählen dieselben Achsen wie bei den Skins, und
     * die Uhr kennt die zusammengeführten Stände hier oft eher als die
     * eigenen Prefs sie schon zeigen.
     */
    fun sceneToAdopt(
        before: SyncState,
        incoming: SyncState,
        patronOwned: Boolean
    ): SceneId? {
        if (incoming.sceneChangedAt <= before.sceneChangedAt) return null
        val merged = skinStats(before, incoming, patronOwned).toSkinStats()
        val scene = ScenePaint.fromName(incoming.scene)
        return if (ScenePaint.isUnlocked(scene, merged)) scene else null
    }
}
