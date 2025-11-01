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

        return (baseScore * levelMultiplier * streakMultiplier).toInt()
    }
}