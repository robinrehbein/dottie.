package de.robinrehbein.punkt.game.models

data class HitResult(
    val isHit: Boolean,
    val distance: Float,
    val accuracy: Float,
    val points: Int,
    val hitType: HitType,
    val reactionTimeMs: Long = 0L
)

enum class HitType {
    PERFECT,
    GOOD,
    BAD,
    MISS
}