package de.robinrehbein.punkt.game.logic

import de.robinrehbein.punkt.game.models.*
import kotlin.math.*

class HitDetection {
    fun checkHit(targetPoint: Point, tapX: Float, tapY: Float, tolerance: Float): HitResult {
        val distance = calculateDistance(targetPoint.x, targetPoint.y, tapX, tapY)
        val isHit = distance <= tolerance

        val accuracy = if (isHit) {
            1.0f - (distance / tolerance)

        } else {
            0.0f
        }

        val hitType = when {
            distance <= targetPoint.radius * 0.8f -> HitType.PERFECT
            distance <= targetPoint.radius * 1.2f -> HitType.GOOD
            distance <= tolerance + targetPoint.radius -> HitType.BAD
            else -> HitType.MISS
        }

        val points = calculatePoints(hitType, accuracy)

        return HitResult(isHit, distance, accuracy, points, hitType)
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