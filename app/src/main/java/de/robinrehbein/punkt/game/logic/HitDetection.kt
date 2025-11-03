package de.robinrehbein.punkt.game.logic

import de.robinrehbein.punkt.game.models.*
import kotlin.math.*

class HitDetection {
    fun checkHit(targetPoint: Point, tapX: Float, tapY: Float, tolerance: Float, reactionTimeMs: Long = 0L): HitResult {
        val currentPosition = targetPoint.getCurrentPosition()
        val currentRadius = targetPoint.getCurrentRadius()
        val distance = calculateDistance(currentPosition.x, currentPosition.y, tapX, tapY)
        val isHit = distance <= tolerance

        val accuracy = if (isHit) {
            1.0f - (distance / tolerance)

        } else {
            0.0f
        }

        val hitType = when {
            distance <= currentRadius * 0.8f -> HitType.PERFECT
            distance <= currentRadius * 1.2f -> HitType.GOOD
            distance <= tolerance + currentRadius -> HitType.BAD
            else -> HitType.MISS
        }

        val points = calculatePoints(hitType, accuracy)

        return HitResult(isHit, distance, accuracy, points, hitType, reactionTimeMs)
    }

    private fun calculatePoints(hitType: HitType, accuracy: Float): Int {
        return when(hitType) {
            HitType.PERFECT -> (100 + (accuracy * 50)).toInt()
            HitType.GOOD -> (50 + (accuracy * 25)).toInt()
            HitType.BAD -> (10 + (accuracy * 10)).toInt()
            HitType.MISS -> (-(accuracy * 10)).toInt()
        }
    }
    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
    }
}