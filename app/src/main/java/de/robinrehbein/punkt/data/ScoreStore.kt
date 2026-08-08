package de.robinrehbein.punkt.data

import android.content.Context

/**
 * Persistiert Highscore und Anzahl der Versuche über SharedPreferences —
 * synchron und simpel, genau richtig für zwei Zahlen.
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

    /** Ton an/aus — überlebt App-Neustarts. */
    var soundMuted: Boolean
        get() = prefs.getBoolean(KEY_MUTED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_MUTED, value).apply()
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

    private companion object {
        const val PREFS_NAME = "punkt_scores"
        const val KEY_BEST = "best_score_timing"
        const val KEY_RUNS = "run_count_timing"
        const val KEY_MUTED = "sound_muted"
    }
}
