package de.robinrehbein.punkt.data

import android.content.Context

/**
 * Persistiert Highscore und Anzahl der Versuche über SharedPreferences —
 * synchron und simpel, genau richtig für zwei Zahlen.
 */
class ScoreStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var bestScore: Int
        get() = prefs.getInt(KEY_BEST, 0)
        set(value) = prefs.edit().putInt(KEY_BEST, value).apply()

    var runCount: Int
        get() = prefs.getInt(KEY_RUNS, 0)
        set(value) = prefs.edit().putInt(KEY_RUNS, value).apply()

    /** Meldet einen beendeten Lauf; liefert true, wenn es ein neuer Rekord war. */
    fun submitRun(score: Int): Boolean {
        runCount += 1
        if (score > bestScore) {
            bestScore = score
            return true
        }
        return false
    }

    private companion object {
        const val PREFS_NAME = "punkt_scores"
        const val KEY_BEST = "best_score"
        const val KEY_RUNS = "run_count"
    }
}
