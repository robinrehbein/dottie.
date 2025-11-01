package de.robinrehbein.punkt.game.models

data class HitResult(val isHit: Boolean, val distance: Float, val accuracy: Float, val points: Int, val hitType: HitType)

enum class HitType {
    PERFECT,
    GOOD,
    BAD,
    MISS
}