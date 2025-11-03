package de.robinrehbein.punkt.game.models

import androidx.compose.ui.graphics.Color

data class GameConfig(
    val level: Int,
    val gameMode: GameMode = GameMode.CLASSIC,
    val levelVariations: List<LevelVariation> = emptyList()
)

sealed class LevelVariation {
    open val pointSizeMultiplier: Float = 1f
    open val showDurationMultiplier: Float = 1f
    open val waitDurationMultiplier: Float = 1f
    open val hitToleranceMultiplier: Float = 1f

    open val movementSpeed: Float = 0f
    open val shrinkingRate: Float = 0f
    open val vibrationIntensity: Float = 0f // New property for vibrating points
    open val tapTimeLimit: Long = 2000L
    open val requiredTaps: Int = 1
    open val pointCount: Int = 1
    open val background: Color = Color.Black

    data object LightningFast : LevelVariation() {
        override val showDurationMultiplier: Float = 0.4f
        override val background: Color = Color(0xFF1B4332) // Dark green
    }

    data object MemoryChallenge : LevelVariation() {
        override val waitDurationMultiplier: Float = 2.5f
        override val background: Color = Color(0xFF6A1B1B) // Dark red
    }

    data object TinyDot : LevelVariation() {
        override val pointSizeMultiplier: Float = 0.5f
        override val background: Color = Color(0xFF2D1B69) // Dark purple
    }

    data object TinyHitTolerance : LevelVariation() {
        override val hitToleranceMultiplier: Float = 0.5f
    }

    data object LinearMovement : LevelVariation() {
        override val movementSpeed: Float = 10f
    }

    data object ShrinkingDot : LevelVariation() {
        override val shrinkingRate: Float = 0.05f
    }

    data object TapTimeChallenge : LevelVariation() {
        override val tapTimeLimit: Long = 1000L
        override val requiredTaps: Int = 5
    }

    data object DoubleTap : LevelVariation() {
        override val requiredTaps: Int = 2
    }

    data object TwinDots : LevelVariation() {
        override val pointCount: Int = 2
    }

    // New early-level variations with background colors
    data object EarlyBlue : LevelVariation() {
        override val background: Color = Color(0xFF1B2951) // Dark blue
    }

    data object EarlyTeal : LevelVariation() {
        override val background: Color = Color(0xFF1B4D4D) // Dark teal
    }

    // Vibrating point variation
    data object VibratingDot : LevelVariation() {
        override val vibrationIntensity: Float = 5f
        override val background: Color = Color(0xFF4A1B69) // Dark violet
    }

    // Fast moving point
    data object FastMovement : LevelVariation() {
        override val movementSpeed: Float = 25f
        override val background: Color = Color(0xFF1B6951) // Dark emerald
    }

    // Slow moving point
    data object SlowMovement : LevelVariation() {
        override val movementSpeed: Float = 8f
        override val background: Color = Color(0xFF2D4A1B) // Dark olive
    }

    // Multiple challenges combined
    data object ChaosMode : LevelVariation() {
        override val pointSizeMultiplier: Float = 0.7f
        override val showDurationMultiplier: Float = 0.8f
        override val movementSpeed: Float = 15f
        override val vibrationIntensity: Float = 3f
        override val background: Color = Color(0xFF6A4C1B) // Dark orange
    }

    // Enhanced Nightmare - much more challenging
    data object Nightmare : LevelVariation() {
        override val pointSizeMultiplier: Float = 0.2f // Even smaller
        override val showDurationMultiplier: Float = 0.3f // Even faster
        override val waitDurationMultiplier: Float = 4.0f // Longer wait
        override val movementSpeed: Float = 80f // Much faster movement
        override val shrinkingRate: Float = 8f // Faster shrinking
        override val vibrationIntensity: Float = 10f // High vibration
        override val hitToleranceMultiplier: Float = 0.3f // Much smaller hit area
        override val background: Color = Color(0xFF8B0000) // Dark red
    }
}

private fun getBaseSizeForLevel(level: Int): Float {
    return when {
        level <= 10 -> 50f - (level * 1f).coerceAtMost(20f)
        // level <= 10 -> 50f - (level * 2f).coerceAtMost(20f)
        level <= 30 -> 30f - ((level - 10) * 1f).coerceAtMost(15f)
        else -> 15f
    }
}

private fun getBaseDurationForLevel(level: Int): Long {
    return when {
        level <= 10 -> 1000L
        level <= 30 -> 800L
        else -> 600L
    }
}

private fun getBaseWaitDurationForLevel(level: Int): Long {
    return when {
        level <= 10 -> 2000L
        level <= 30 -> 2500L + ((level - 10) * 100L)
        else -> 4500L
    }
}

private fun getBaseToleranceForLevel(level: Int): Float {
    return 50f
//    return when {
//        level <= 10 -> 60f - (level * 2f).coerceAtMost(20f)
//        level <= 30 -> 40f - ((level - 10) * 1f).coerceAtMost(15f)
//        else -> 25f
//    }
}

val GameConfig.finalPointSize: Float
    get() {
        val baseSize = getBaseSizeForLevel(level)
        val multiplier = levelVariations.fold(1.0f) { acc, variation ->
            acc * variation.pointSizeMultiplier
        }
        return baseSize * multiplier
    }

val GameConfig.finalShowDuration: Long
    get() {
        val baseDuration = getBaseDurationForLevel(level)
        val multiplier = levelVariations.fold(1.0f) { acc, variation ->
            acc * variation.showDurationMultiplier
        }
        return (baseDuration * multiplier).toLong()
    }

val GameConfig.finalWaitDuration: Long
    get() {
        val baseDuration = getBaseWaitDurationForLevel(level)
        val multiplier = levelVariations.fold(1.0f) { acc, variation ->
            acc * variation.waitDurationMultiplier
        }
        return (baseDuration * multiplier).toLong()
    }

val GameConfig.finalHitTolerance: Float
    get() {
        val baseTolerance = getBaseToleranceForLevel(level)
        val multiplier = levelVariations.fold(1.0f) { acc, variation ->
            acc * variation.hitToleranceMultiplier
        }
        return baseTolerance * multiplier
    }

val GameConfig.movementSpeed: Float
    get() = levelVariations.sumOf { it.movementSpeed.toDouble() }.toFloat()

val GameConfig.shrinkingRate: Float
    get() = levelVariations.sumOf { it.shrinkingRate.toDouble() }.toFloat()

val GameConfig.vibrationIntensity: Float
    get() = levelVariations.sumOf { it.vibrationIntensity.toDouble() }.toFloat()

// ===== OVERRIDE PROPERTIES (letzter gewinnt) =====

val GameConfig.tapTimeLimit: Long
    get() = levelVariations.lastOrNull()?.tapTimeLimit ?: 2000L

val GameConfig.requiredTaps: Int
    get() = levelVariations.maxOfOrNull { it.requiredTaps } ?: 1

val GameConfig.pointCount: Int
    get() = levelVariations.maxOfOrNull { it.pointCount } ?: 1

val GameConfig.backgroundColor: Color
    get() = levelVariations.lastOrNull { it.background != Color(0xFF1A1A2E) }?.background ?: Color(0xFF1A1A2E)

val GameConfig.hasWaitingPhase: Boolean
    get() = when (gameMode) {
        GameMode.CLASSIC -> true
        GameMode.SPEEDRUN -> false
    }