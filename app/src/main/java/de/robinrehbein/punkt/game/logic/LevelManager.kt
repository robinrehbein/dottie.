package de.robinrehbein.punkt.game.logic

import de.robinrehbein.punkt.game.models.GameConfig
import de.robinrehbein.punkt.game.models.GameMode
import de.robinrehbein.punkt.game.models.LevelVariation
import kotlin.random.Random

class LevelManager {
    fun getConfigForLevel(level: Int, gameMode: GameMode = GameMode.CLASSIC): GameConfig {
        val variations = getVariationsForLevel(level)
        
        return GameConfig(
            level = level,
            gameMode = gameMode,
            levelVariations = variations
        )
    }

    private fun getVariationsForLevel(level: Int): List<LevelVariation> {
        return when {
            // Level 100+ = Always Nightmare mode
            level >= 100 -> listOf(LevelVariation.Nightmare)
            
            // Levels 1-14: Single variations only
            level in 1..14 -> getSingleVariation(level)
            
            // Levels 15-29: Stack 2 variations
            level in 15..29 -> getTwoVariations(level)
            
            // Levels 30-59: Stack 3 variations
            level in 30..59 -> getThreeVariations(level)
            
            // Levels 60-99: Stack 4 variations
            level in 60..99 -> getFourVariations(level)
            
            else -> emptyList()
        }
    }

    private fun getSingleVariation(level: Int): List<LevelVariation> {
        return when (level) {
            1, 2 -> emptyList() // Pure basics
            3 -> listOf(LevelVariation.EarlyBlue)
            4 -> listOf(LevelVariation.EarlyTeal)
            5 -> listOf(LevelVariation.TinyDot)
            6 -> listOf(LevelVariation.LightningFast)
            7 -> listOf(LevelVariation.MemoryChallenge)
            8 -> listOf(LevelVariation.SlowMovement)
            9 -> listOf(LevelVariation.VibratingDot)
            10 -> listOf(LevelVariation.FastMovement)
            11 -> listOf(LevelVariation.LinearMovement)
            12 -> listOf(LevelVariation.ShrinkingDot)
            13 -> listOf(LevelVariation.CircularMovement)
            14 -> listOf(LevelVariation.ZigzagMovement)
            else -> emptyList()
        }
    }

    private fun getTwoVariations(level: Int): List<LevelVariation> {
        val baseVariations = getAllBaseVariations()
        val movementVariations = getMovementVariations()
        val specialVariations = getSpecialVariations()
        
        return when (level) {
            15 -> listOf(LevelVariation.TinyDot, LevelVariation.LightningFast)
            16 -> listOf(LevelVariation.MemoryChallenge, LevelVariation.VibratingDot)
            17 -> listOf(LevelVariation.SlowMovement, LevelVariation.ShrinkingDot)
            18 -> listOf(LevelVariation.FastMovement, LevelVariation.TinyHitTolerance)
            19 -> listOf(LevelVariation.CircularMovement, LevelVariation.TinyDot)
            20 -> listOf(LevelVariation.ZigzagMovement, LevelVariation.LightningFast)
            21 -> listOf(LevelVariation.SineWaveMovement, LevelVariation.MemoryChallenge)
            22 -> listOf(LevelVariation.DoubleTap, LevelVariation.VibratingDot)
            23 -> listOf(LevelVariation.TwinDots, LevelVariation.SlowMovement)
            24 -> listOf(LevelVariation.TapTimeChallenge, LevelVariation.FastMovement)
            25 -> listOf(LevelVariation.LinearMovement, LevelVariation.ShrinkingDot)
            26 -> listOf(LevelVariation.CircularMovement, LevelVariation.TinyHitTolerance)
            27 -> listOf(LevelVariation.ZigzagMovement, LevelVariation.DoubleTap)
            28 -> listOf(LevelVariation.SineWaveMovement, LevelVariation.TwinDots)
            29 -> listOf(LevelVariation.ChaosMode, LevelVariation.TapTimeChallenge)
            else -> generateRandomVariations(baseVariations, 2, level)
        }
    }

    private fun getThreeVariations(level: Int): List<LevelVariation> {
        val baseVariations = getAllBaseVariations()
        val movementVariations = getMovementVariations()
        val specialVariations = getSpecialVariations()
        
        return when (level) {
            30 -> listOf(LevelVariation.TinyDot, LevelVariation.LightningFast, LevelVariation.CircularMovement)
            31 -> listOf(LevelVariation.MemoryChallenge, LevelVariation.VibratingDot, LevelVariation.ZigzagMovement)
            32 -> listOf(LevelVariation.ShrinkingDot, LevelVariation.TinyHitTolerance, LevelVariation.SineWaveMovement)
            33 -> listOf(LevelVariation.DoubleTap, LevelVariation.FastMovement, LevelVariation.LinearMovement)
            34 -> listOf(LevelVariation.TwinDots, LevelVariation.SlowMovement, LevelVariation.TapTimeChallenge)
            35 -> listOf(LevelVariation.TinyDot, LevelVariation.CircularMovement, LevelVariation.VibratingDot)
            36 -> listOf(LevelVariation.LightningFast, LevelVariation.ZigzagMovement, LevelVariation.ShrinkingDot)
            37 -> listOf(LevelVariation.MemoryChallenge, LevelVariation.SineWaveMovement, LevelVariation.TinyHitTolerance)
            38 -> listOf(LevelVariation.FastMovement, LevelVariation.DoubleTap, LevelVariation.LinearMovement)
            39 -> listOf(LevelVariation.SlowMovement, LevelVariation.TwinDots, LevelVariation.TapTimeChallenge)
            40 -> listOf(LevelVariation.ChaosMode, LevelVariation.CircularMovement, LevelVariation.VibratingDot)
            41 -> listOf(LevelVariation.TinyDot, LevelVariation.ZigzagMovement, LevelVariation.LightningFast)
            42 -> listOf(LevelVariation.MemoryChallenge, LevelVariation.SineWaveMovement, LevelVariation.ShrinkingDot)
            43 -> listOf(LevelVariation.FastMovement, LevelVariation.TinyHitTolerance, LevelVariation.DoubleTap)
            44 -> listOf(LevelVariation.SlowMovement, LevelVariation.TwinDots, LevelVariation.LinearMovement)
            45 -> listOf(LevelVariation.CircularMovement, LevelVariation.VibratingDot, LevelVariation.TapTimeChallenge)
            46 -> listOf(LevelVariation.ZigzagMovement, LevelVariation.TinyDot, LevelVariation.ChaosMode)
            47 -> listOf(LevelVariation.SineWaveMovement, LevelVariation.LightningFast, LevelVariation.MemoryChallenge)
            48 -> listOf(LevelVariation.LinearMovement, LevelVariation.ShrinkingDot, LevelVariation.FastMovement)
            49 -> listOf(LevelVariation.DoubleTap, LevelVariation.TinyHitTolerance, LevelVariation.SlowMovement)
            50 -> listOf(LevelVariation.TwinDots, LevelVariation.CircularMovement, LevelVariation.TapTimeChallenge)
            51 -> listOf(LevelVariation.VibratingDot, LevelVariation.ZigzagMovement, LevelVariation.ChaosMode)
            52 -> listOf(LevelVariation.TinyDot, LevelVariation.SineWaveMovement, LevelVariation.LightningFast)
            53 -> listOf(LevelVariation.MemoryChallenge, LevelVariation.LinearMovement, LevelVariation.ShrinkingDot)
            54 -> listOf(LevelVariation.FastMovement, LevelVariation.DoubleTap, LevelVariation.TinyHitTolerance)
            55 -> listOf(LevelVariation.SlowMovement, LevelVariation.TwinDots, LevelVariation.CircularMovement)
            56 -> listOf(LevelVariation.TapTimeChallenge, LevelVariation.VibratingDot, LevelVariation.ZigzagMovement)
            57 -> listOf(LevelVariation.ChaosMode, LevelVariation.TinyDot, LevelVariation.SineWaveMovement)
            58 -> listOf(LevelVariation.LightningFast, LevelVariation.MemoryChallenge, LevelVariation.LinearMovement)
            59 -> listOf(LevelVariation.ShrinkingDot, LevelVariation.FastMovement, LevelVariation.DoubleTap)
            else -> generateRandomVariations(baseVariations, 3, level)
        }
    }

    private fun getFourVariations(level: Int): List<LevelVariation> {
        val baseVariations = getAllBaseVariations()
        
        return when (level) {
            60 -> listOf(LevelVariation.TinyDot, LevelVariation.LightningFast, LevelVariation.CircularMovement, LevelVariation.VibratingDot)
            61 -> listOf(LevelVariation.MemoryChallenge, LevelVariation.ZigzagMovement, LevelVariation.ShrinkingDot, LevelVariation.TinyHitTolerance)
            62 -> listOf(LevelVariation.FastMovement, LevelVariation.SineWaveMovement, LevelVariation.DoubleTap, LevelVariation.SlowMovement)
            63 -> listOf(LevelVariation.TwinDots, LevelVariation.LinearMovement, LevelVariation.TapTimeChallenge, LevelVariation.ChaosMode)
            64 -> listOf(LevelVariation.TinyDot, LevelVariation.CircularMovement, LevelVariation.VibratingDot, LevelVariation.LightningFast)
            65 -> listOf(LevelVariation.MemoryChallenge, LevelVariation.ZigzagMovement, LevelVariation.ShrinkingDot, LevelVariation.FastMovement)
            66 -> listOf(LevelVariation.SineWaveMovement, LevelVariation.TinyHitTolerance, LevelVariation.DoubleTap, LevelVariation.SlowMovement)
            67 -> listOf(LevelVariation.LinearMovement, LevelVariation.TwinDots, LevelVariation.TapTimeChallenge, LevelVariation.VibratingDot)
            68 -> listOf(LevelVariation.ChaosMode, LevelVariation.CircularMovement, LevelVariation.TinyDot, LevelVariation.LightningFast)
            69 -> listOf(LevelVariation.MemoryChallenge, LevelVariation.ZigzagMovement, LevelVariation.ShrinkingDot, LevelVariation.FastMovement)
            70 -> listOf(LevelVariation.SineWaveMovement, LevelVariation.TinyHitTolerance, LevelVariation.DoubleTap, LevelVariation.SlowMovement)
            
            // Levels 71-79: Intense combinations
            in 71..79 -> listOf(
                LevelVariation.ChaosMode,
                LevelVariation.TinyDot,
                LevelVariation.CircularMovement,
                LevelVariation.VibratingDot
            )
            
            // Levels 80-89: Maximum chaos
            in 80..89 -> listOf(
                LevelVariation.ChaosMode,
                LevelVariation.ZigzagMovement,
                LevelVariation.LightningFast,
                LevelVariation.ShrinkingDot
            )
            
            // Levels 90-99: Pre-nightmare insanity
            in 90..99 -> listOf(
                LevelVariation.ChaosMode,
                LevelVariation.SineWaveMovement,
                LevelVariation.TinyHitTolerance,
                LevelVariation.DoubleTap
            )
            
            else -> generateRandomVariations(baseVariations, 4, level)
        }
    }

    private fun getAllBaseVariations(): List<LevelVariation> {
        return listOf(
            LevelVariation.TinyDot,
            LevelVariation.LightningFast,
            LevelVariation.MemoryChallenge,
            LevelVariation.TinyHitTolerance,
            LevelVariation.ShrinkingDot,
            LevelVariation.DoubleTap,
            LevelVariation.TwinDots,
            LevelVariation.TapTimeChallenge,
            LevelVariation.VibratingDot,
            LevelVariation.EarlyBlue,
            LevelVariation.EarlyTeal
        )
    }

    private fun getMovementVariations(): List<LevelVariation> {
        return listOf(
            LevelVariation.LinearMovement,
            LevelVariation.FastMovement,
            LevelVariation.SlowMovement,
            LevelVariation.CircularMovement,
            LevelVariation.ZigzagMovement,
            LevelVariation.SineWaveMovement
        )
    }

    private fun getSpecialVariations(): List<LevelVariation> {
        return listOf(
            LevelVariation.ChaosMode
        )
    }

    private fun generateRandomVariations(
        availableVariations: List<LevelVariation>,
        count: Int,
        level: Int
    ): List<LevelVariation> {
        // Use level as seed for consistent results
        val random = Random(level.toLong())
        
        // Ensure we don't have conflicting movement types
        val movementVariations = getMovementVariations()
        val nonMovementVariations = availableVariations.filter { it !in movementVariations }
        
        val result = mutableListOf<LevelVariation>()
        
        // Add at most one movement variation
        if (count > 0 && random.nextBoolean()) {
            result.add(movementVariations.random(random))
        }
        
        // Fill remaining slots with non-movement variations
        val remainingCount = count - result.size
        val shuffled = nonMovementVariations.shuffled(random)
        result.addAll(shuffled.take(remainingCount))
        
        return result.take(count)
    }
}