package de.robinrehbein.punkt.scoring

import de.robinrehbein.punkt.game.models.HitResult
import kotlin.math.pow

class ScoreCalculator {
    fun calculateScore(hitResult: HitResult, level: Int, streak: Int): Int {
        if (!hitResult.isHit) {
            return 0
        }

        val baseScore = hitResult.points
        val levelMultiplier = level.toDouble().pow(0.75)
        val streakMultiplier = streak.toDouble().pow(0.25)
        val reactionTimeMultiplier = calculateReactionTimeMultiplier(hitResult.reactionTimeMs)

        return (baseScore * levelMultiplier * streakMultiplier * reactionTimeMultiplier).toInt()
    }

    private fun calculateReactionTimeMultiplier(reactionTimeMs: Long): Double {
        // Optimal reaction time is around 200-300ms
        // Faster reactions get higher multipliers, slower get lower
        return when {
            reactionTimeMs <= 200 -> 2.0  // Very fast: 2x multiplier
            reactionTimeMs <= 300 -> 1.8  // Fast: 1.8x multiplier
            reactionTimeMs <= 400 -> 1.5  // Good: 1.5x multiplier
            reactionTimeMs <= 500 -> 1.2  // Average: 1.2x multiplier
            reactionTimeMs <= 700 -> 1.0  // Slow: 1x multiplier
            else -> 0.8  // Very slow: 0.8x multiplier
        }
    }
}