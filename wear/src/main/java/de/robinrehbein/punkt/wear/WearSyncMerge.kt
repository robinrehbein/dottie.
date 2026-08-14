package de.robinrehbein.punkt.wear

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
        bestDailyStreak = maxOf(before.dailyStreak, incoming.dailyStreak),
        runCount = maxOf(before.runCount, incoming.runCount),
        totalScore = maxOf(before.totalScore, incoming.totalScore),
        daysPlayed = maxOf(before.daysPlayed, incoming.daysPlayed),
        // Stats will die ANZAHL verschiedener Monate, gespeichert ist die
        // Maske — also erst verodern, dann die Bits zählen.
        monthsPlayed = Integer.bitCount(before.monthsPlayed or incoming.monthsPlayed),
        seasonEarned = before.seasonEarned or incoming.seasonEarned,
        patronOwned = patronOwned
    )
}
