package de.robinrehbein.punkt.ui.platform

import de.robinrehbein.punkt.game.SkinId

/**
 * Alles, was ein Store-Oekosystem kann und das Spiel nicht.
 *
 * Werbung, Kaeufe, Bestenlisten, der Abgleich mit der Uhr, die
 * Benachrichtigungs-Berechtigung und das Teilen — nichts davon ist eine
 * Spielregel, und nichts davon gibt es auf beiden Plattformen. Statt den
 * Bildschirm nach der Plattform fragen zu lassen, bekommt er sie als
 * Werte und Rueckrufe gereicht.
 *
 * Jeder Standard tut nichts. Eine Fassung ohne Store — und das ist die
 * iOS-App — nimmt `PlatformHooks()` und bekommt dieselbe Oberflaeche
 * ohne jede leere Schaltflaeche: Wo ein Preis `null` ist, faellt die
 * Zeile weg; wo `available` falsch ist, der Knopf.
 */
class PlatformHooks(

    // ===== Werbung =====

    /** Ist Werbung ueberhaupt aktiv? Ohne AdMob-IDs auch auf Android false. */
    val adsEnabled: Boolean = false,

    /** Nach jedem beendeten Lauf — der Zwischenspot entscheidet selbst. */
    val onRunEnded: () -> Unit = {},

    /** Steht ein belohnter Spot bereit (Skin-Tagespass)? */
    val rewardedReady: Boolean = false,

    /**
     * Spot fuer den Tagespass dieses Skins zeigen. Der Rueckruf kommt
     * NUR bei bestaetigtem Spot — was der Pass dann bedeutet, entscheidet
     * der Bildschirm, nicht die Plattform.
     */
    val onWatchAdFor: (SkinId, onEarned: () -> Unit) -> Unit = { _, _ -> },

    /** Muss der Einwilligungs-Dialog erreichbar sein (DSGVO)? */
    val privacyVisible: Boolean = false,
    val onPrivacy: () -> Unit = {},

    // ===== Kauf =====

    /** Preis fuer "Werbung entfernen" — null = kein Angebot. */
    val removeAdsPrice: String? = null,
    val onRemoveAds: () -> Unit = {},

    /** Preis des Goenner-Pakets — null = kein Angebot. */
    val patronPrice: String? = null,
    val onPatron: () -> Unit = {},

    // ===== Bestenlisten =====

    val leaderboardAvailable: Boolean = false,
    val onShowLeaderboard: () -> Unit = {},
    val onSubmitBest: (Int) -> Unit = {},
    val onSubmitDaily: (Int) -> Unit = {},

    // ===== Abgleich mit der Uhr =====

    /** Nach jeder Entscheidung, die die Gegenseite sehen soll. */
    val onPublishSync: () -> Unit = {},

    // ===== Tages-Erinnerung =====

    /** Kann die Plattform ueberhaupt erinnern? */
    val reminderSupported: Boolean = false,

    /**
     * Der Schalter wurde umgelegt. Die Plattform entscheidet selbst, ob
     * sie dafuer erst eine Berechtigung braucht, und meldet im Rueckruf,
     * was daraus geworden ist — ein aktivierter Schalter ohne Zustellung
     * waere eine Luege.
     */
    val setReminder: (wanted: Boolean, onResult: (Boolean) -> Unit) -> Unit = { _, _ -> },

    // ===== Teilen =====

    /** Score-Karte teilen — null = die Plattform kann es nicht. */
    val onShare: ((ShareRequest) -> Unit)? = null,

    // ===== Diagnose =====

    /** Die versteckte Diagnose-Zeile (langer Druck auf den Titel). */
    val diagnostics: String? = null
)

/** Was auf der Score-Karte steht. Die Karte selbst baut die Plattform. */
data class ShareRequest(
    val score: Int,
    val bestScore: Int,
    val isNewRecord: Boolean,
    val daily: Boolean,
    val dailyStreak: Int,
    val sceneName: String,
    val recordText: String
)
