package de.robinrehbein.punkt.ui.platform

import de.robinrehbein.punkt.game.DeathCause
import de.robinrehbein.punkt.game.Mission
import de.robinrehbein.punkt.game.Twist
import kotlin.math.roundToLong

/**
 * Ein Ereignis für die Nutzungsstatistik (ab v2.36).
 *
 * Bis v2.35 hatte das Spiel keine Messung: Ob Leute nach dem ersten Tag
 * wiederkommen, an welchem Twist sie aufgeben oder ob die Daily überhaupt
 * gespielt wird, war Raten. Hier stehen die Ereignisse, die diese Fragen
 * beantworten — und nur die.
 *
 * Name und Parameter folgen den Grenzen von Firebase Analytics (Namen bis
 * 40 Zeichen aus Kleinbuchstaben und Unterstrich, höchstens 25 Parameter,
 * Werte als Text oder Zahl), sind aber an nichts davon gebunden: Die
 * Plattform übersetzt sie (siehe [PlatformHooks.onAnalytics]).
 *
 * Was hier NIE hineingehört: irgendetwas, das eine Person identifiziert.
 * Keine Namen, keine Geräte-IDs, keine Werbe-ID, kein Freitext.
 */
data class AnalyticsEvent(
    val name: String,
    /** Werte sind [String] oder [Long] — nichts anderes. */
    val params: Map<String, Any> = emptyMap()
)

/**
 * Die Ereignisse des Spiels an einer Stelle. Reine Funktionen, damit ein
 * Test prüfen kann, dass Namen und Werte in den Grenzen bleiben.
 */
object Telemetry {

    /** Ein beendeter Lauf — das wichtigste Ereignis: Wo und woran sterben die Leute? */
    fun runEnd(
        score: Int,
        hits: Int,
        cause: DeathCause,
        missSeconds: Float,
        daily: Boolean,
        runNumber: Int,
        bestBefore: Int,
        newRecord: Boolean,
        maxPerfectStreak: Int,
        activeTwists: Set<Twist>,
        skin: String
    ) = AnalyticsEvent(
        "run_end",
        buildMap {
            put("score", score.toLong())
            put("hits", hits.toLong())
            put("cause", cause.name.lowercase())
            // In Millisekunden: Analytics rechnet mit ganzen Zahlen besser.
            if (cause == DeathCause.EARLY || cause == DeathCause.LATE) {
                put("miss_ms", (missSeconds * 1000f).roundToLong())
            }
            put("daily", flag(daily))
            put("run_no", runNumber.toLong())
            put("best_before", bestBefore.toLong())
            put("record", flag(newRecord))
            put("perfect_max", maxPerfectStreak.toLong())
            // Die Twists der Zone, in der der Lauf endete — „starben die
            // Leute an BOMBEN oder an DRIFT?" ist die Frage dahinter.
            put("twists", twistList(activeTwists))
            put("skin", skin.lowercase())
        }
    )

    /** Der erste Tageslauf eines Tages hat die Serie fortgeschrieben. */
    fun dailyEnd(score: Int, streak: Int, jokersUsed: Int) = AnalyticsEvent(
        "daily_end",
        mapOf(
            "score" to score.toLong(),
            "streak" to streak.toLong(),
            "jokers_used" to jokersUsed.toLong()
        )
    )

    /** Eine Tagesaufgabe erledigt. */
    fun missionDone(mission: Mission, tier: Int, allDone: Boolean) = AnalyticsEvent(
        "mission_done",
        mapOf(
            "type" to mission.type.name.lowercase(),
            "target" to mission.target.toLong(),
            "tier" to tier.toLong(),
            "all_done" to flag(allDone)
        )
    )

    /** Ein Serien-Joker kam hinzu. [source] ist "missions" oder "ad". */
    fun jokerEarned(source: String, total: Int) = AnalyticsEvent(
        "joker_earned",
        mapOf("source" to source, "total" to total.toLong())
    )

    /** Das Rettungs-Angebot: gezeigt, angenommen (Spot gesehen) oder ausgeschlagen. */
    fun streakRescue(action: String, streak: Int) = AnalyticsEvent(
        "streak_rescue",
        mapOf("action" to action, "streak" to streak.toLong())
    )

    /** Ein Skin wurde in diesem Lauf verdient. */
    fun skinUnlocked(runNumber: Int) = AnalyticsEvent(
        "skin_unlocked",
        mapOf("run_no" to runNumber.toLong())
    )

    /** Antwort auf die Erinnerungs-Frage oder Umlegen des Schalters. */
    fun reminder(action: String) = AnalyticsEvent("reminder", mapOf("action" to action))

    /** Die App wurde über eine Erinnerung geöffnet ([kind]: "daily" oder "risk"). */
    fun reminderOpened(kind: String) = AnalyticsEvent("reminder_opened", mapOf("kind" to kind))

    /** Eine Seite wurde geöffnet: collection, stats, settings, help. */
    fun openScreen(screen: String) = AnalyticsEvent("open_screen", mapOf("screen" to screen))

    /** Die Score-Karte wurde geteilt. */
    fun share(score: Int, daily: Boolean) = AnalyticsEvent(
        "share",
        mapOf("score" to score.toLong(), "daily" to flag(daily))
    )

    private fun flag(value: Boolean): Long = if (value) 1L else 0L

    private fun twistList(twists: Set<Twist>): String =
        if (twists.isEmpty()) "none"
        else twists.map { it.name.lowercase() }.sorted().joinToString("+")
}
