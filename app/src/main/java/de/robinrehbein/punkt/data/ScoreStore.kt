package de.robinrehbein.punkt.data

import android.content.Context
import de.robinrehbein.punkt.game.GameMode

/**
 * Persistiert Highscore und Anzahl der Versuche pro Spielmodus über
 * SharedPreferences — synchron und simpel, genau richtig für vier Zahlen.
 */
class ScoreStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun bestScore(mode: GameMode): Int = prefs.getInt(bestKey(mode), 0)

    fun runCount(mode: GameMode): Int = prefs.getInt(runKey(mode), 0)

    /** Meldet einen beendeten Lauf; liefert true, wenn es ein neuer Rekord war. */
    fun submitRun(mode: GameMode, score: Int): Boolean {
        prefs.edit().putInt(runKey(mode), runCount(mode) + 1).apply()
        if (score > bestScore(mode)) {
            prefs.edit().putInt(bestKey(mode), score).apply()
            return true
        }
        return false
    }

    private fun bestKey(mode: GameMode) = when (mode) {
        GameMode.GRAVITY_FLIP -> KEY_BEST_GRAVITY
        GameMode.TIME_STOP -> KEY_BEST_TIMING
    }

    private fun runKey(mode: GameMode) = when (mode) {
        GameMode.GRAVITY_FLIP -> KEY_RUNS_GRAVITY
        GameMode.TIME_STOP -> KEY_RUNS_TIMING
    }

    private companion object {
        const val PREFS_NAME = "punkt_scores"
        const val KEY_BEST_GRAVITY = "best_score"
        const val KEY_RUNS_GRAVITY = "run_count"
        const val KEY_BEST_TIMING = "best_score_timing"
        const val KEY_RUNS_TIMING = "run_count_timing"
    }
}
