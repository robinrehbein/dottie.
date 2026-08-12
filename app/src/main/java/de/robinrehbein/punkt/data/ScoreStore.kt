package de.robinrehbein.punkt.data

import android.content.Context
import de.robinrehbein.punkt.game.DailyChallenge
import de.robinrehbein.punkt.game.DotSkin

/**
 * Persistiert Highscore, Daily-Challenge-Stand, Bestleistungen und den
 * gewählten Skin über SharedPreferences — synchron und simpel, genau
 * richtig für eine Handvoll Zahlen.
 *
 * Die Keys tragen noch das "_timing"-Suffix aus der Zeit, als es neben
 * STOPP auch den FLIP-Modus gab (bis v2.5, Tag "v2.5-mit-flip") — so
 * überleben bestehende Highscores das Update.
 */
class ScoreStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val bestScore: Int
        get() = prefs.getInt(KEY_BEST, 0)

    val runCount: Int
        get() = prefs.getInt(KEY_RUNS, 0)

    /** Beste jemals erreichte Perfekt-Serie (für Skin-Freischaltungen). */
    val bestPerfectStreak: Int
        get() = prefs.getInt(KEY_BEST_PERFECT, 0)

    /** Ton an/aus — überlebt App-Neustarts. */
    var soundMuted: Boolean
        get() = prefs.getBoolean(KEY_MUTED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_MUTED, value).apply()
        }

    /** Tägliche Daily-Challenge-Erinnerung (Opt-in, lokal). */
    var reminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDER, false)
        set(value) {
            prefs.edit().putBoolean(KEY_REMINDER, value).apply()
        }

    /**
     * Werbefrei gekauft ("remove_ads"). Play Billing ist die Wahrheit —
     * dieser Wert ist nur der lokale Spiegel, damit die UI beim Start
     * ohne Netz sofort weiß, dass keine Werbung erscheinen darf.
     */
    var adsRemoved: Boolean
        get() = prefs.getBoolean(KEY_ADS_REMOVED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ADS_REMOVED, value).apply()
        }

    /** Gewählter Punkt-Skin, KLASSIK als Fallback. */
    var selectedSkin: DotSkin
        get() = DotSkin.fromName(prefs.getString(KEY_SKIN, null))
        set(value) {
            prefs.edit().putString(KEY_SKIN, value.name).apply()
        }

    // ===== Daily Challenge =====

    /** Tagesbest-Score — gilt nur für den in [dailyDay] gespeicherten Tag. */
    val dailyBest: Int
        get() = prefs.getInt(KEY_DAILY_BEST, 0)

    /** Epoch-Day, zu dem [dailyBest] gehört. */
    val dailyDay: Long
        get() = prefs.getLong(KEY_DAILY_DAY, 0L)

    /** Aktuelle Serie an Tagen mit mindestens einem Daily-Lauf. */
    val dailyStreak: Int
        get() = prefs.getInt(KEY_DAILY_STREAK, 0)

    /** Tagesbest für einen konkreten Tag — 0, wenn der Tag nicht passt. */
    fun dailyBestFor(epochDay: Long): Int =
        if (dailyDay == epochDay) dailyBest else 0

    /**
     * Die Serie, wie sie ein Daily-Lauf HEUTE fortschreiben würde. Für die
     * Anzeige auf dem Startscreen: War gestern der letzte Lauf, läuft die
     * Serie noch; liegt er länger zurück, ist sie faktisch gerissen.
     */
    fun dailyStreakPreviewFor(epochDay: Long): Int = when {
        dailyDay == epochDay -> dailyStreak
        dailyDay == epochDay - 1 -> dailyStreak
        else -> 0
    }

    /** Meldet einen beendeten Lauf; liefert true, wenn es ein neuer Rekord war. */
    fun submitRun(score: Int): Boolean {
        prefs.edit().putInt(KEY_RUNS, runCount + 1).apply()
        if (score > bestScore) {
            prefs.edit().putInt(KEY_BEST, score).apply()
            return true
        }
        return false
    }

    /** Meldet die höchste Perfekt-Serie eines Laufs. */
    fun submitPerfectStreak(streak: Int) {
        if (streak > bestPerfectStreak) {
            prefs.edit().putInt(KEY_BEST_PERFECT, streak).apply()
        }
    }

    /**
     * Meldet einen beendeten Daily-Lauf: schreibt die Tages-Serie fort
     * (nur der erste Lauf des Tages zählt dafür) und aktualisiert den
     * Tagesbest-Score. Liefert true bei neuem Tagesbest.
     */
    fun submitDailyRun(epochDay: Long, score: Int): Boolean {
        val firstRunToday = dailyDay != epochDay
        if (firstRunToday) {
            val streak = DailyChallenge.nextStreak(
                lastPlayedEpochDay = dailyDay,
                currentStreak = dailyStreak,
                todayEpochDay = epochDay
            )
            prefs.edit()
                .putInt(KEY_DAILY_STREAK, streak)
                .putLong(KEY_DAILY_DAY, epochDay)
                .putInt(KEY_DAILY_BEST, score)
                .apply()
            return score > 0
        }
        if (score > dailyBest) {
            prefs.edit().putInt(KEY_DAILY_BEST, score).apply()
            return true
        }
        return false
    }

    /** Aktuelle Bestleistungen gebündelt, für Skin-Freischaltungen. */
    fun stats(): DotSkin.Stats = DotSkin.Stats(
        bestScore = bestScore,
        bestPerfectStreak = bestPerfectStreak,
        bestDailyStreak = dailyStreak
    )

    private companion object {
        const val PREFS_NAME = "punkt_scores"
        const val KEY_BEST = "best_score_timing"
        const val KEY_RUNS = "run_count_timing"
        const val KEY_MUTED = "sound_muted"
        const val KEY_REMINDER = "daily_reminder"
        const val KEY_BEST_PERFECT = "best_perfect_streak"
        const val KEY_SKIN = "selected_skin"
        const val KEY_ADS_REMOVED = "ads_removed"
        const val KEY_DAILY_BEST = "daily_best"
        const val KEY_DAILY_DAY = "daily_day"
        const val KEY_DAILY_STREAK = "daily_streak"
    }
}
