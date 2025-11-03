package de.robinrehbein.punkt.game.logic

import de.robinrehbein.punkt.game.models.GameConfig
import de.robinrehbein.punkt.game.models.GameMode
import de.robinrehbein.punkt.game.models.LevelVariation

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
        return when (level) {
            // Levels 1-2: Pure basics
            1, 2 -> emptyList()
            
            // Early color introduction
            3 -> listOf(LevelVariation.EarlyBlue)
            4 -> emptyList()
            5 -> listOf(LevelVariation.EarlyTeal)
            
            // Basic variations introduction (6-15)
            6 -> listOf(LevelVariation.TinyDot)
            7 -> listOf(LevelVariation.LightningFast)
            8 -> listOf(LevelVariation.MemoryChallenge)
            9 -> listOf(LevelVariation.SlowMovement)
            10 -> listOf(LevelVariation.VibratingDot)
            
            // Combining two variations (11-25)
            11 -> listOf(LevelVariation.TinyDot, LevelVariation.EarlyBlue)
            12 -> listOf(LevelVariation.LightningFast, LevelVariation.EarlyTeal)
            13 -> listOf(LevelVariation.MemoryChallenge, LevelVariation.TinyDot)
            14 -> listOf(LevelVariation.SlowMovement, LevelVariation.VibratingDot)
            15 -> listOf(LevelVariation.LinearMovement, LevelVariation.LightningFast)
            
            16 -> listOf(LevelVariation.ShrinkingDot, LevelVariation.TinyDot)
            17 -> listOf(LevelVariation.DoubleTap, LevelVariation.MemoryChallenge)
            18 -> listOf(LevelVariation.TinyHitTolerance, LevelVariation.VibratingDot)
            19 -> listOf(LevelVariation.FastMovement, LevelVariation.LightningFast)
            20 -> listOf(LevelVariation.TwinDots, LevelVariation.SlowMovement)
            
            21 -> listOf(LevelVariation.TapTimeChallenge, LevelVariation.TinyDot)
            22 -> listOf(LevelVariation.ShrinkingDot, LevelVariation.FastMovement)
            23 -> listOf(LevelVariation.DoubleTap, LevelVariation.VibratingDot)
            24 -> listOf(LevelVariation.TinyHitTolerance, LevelVariation.MemoryChallenge)
            25 -> listOf(LevelVariation.TwinDots, LevelVariation.LightningFast)
            
            // Triple combinations (26-40)
            26 -> listOf(LevelVariation.TinyDot, LevelVariation.LightningFast, LevelVariation.SlowMovement)
            27 -> listOf(LevelVariation.MemoryChallenge, LevelVariation.VibratingDot, LevelVariation.TinyHitTolerance)
            28 -> listOf(LevelVariation.ShrinkingDot, LevelVariation.DoubleTap, LevelVariation.FastMovement)
            29 -> listOf(LevelVariation.TwinDots, LevelVariation.TapTimeChallenge, LevelVariation.LinearMovement)
            30 -> listOf(LevelVariation.TinyDot, LevelVariation.VibratingDot, LevelVariation.MemoryChallenge)
            
            31 -> listOf(LevelVariation.LightningFast, LevelVariation.ShrinkingDot, LevelVariation.TinyHitTolerance)
            32 -> listOf(LevelVariation.FastMovement, LevelVariation.DoubleTap, LevelVariation.SlowMovement)
            33 -> listOf(LevelVariation.TwinDots, LevelVariation.VibratingDot, LevelVariation.LinearMovement)
            34 -> listOf(LevelVariation.MemoryChallenge, LevelVariation.TapTimeChallenge, LevelVariation.TinyDot)
            35 -> listOf(LevelVariation.ShrinkingDot, LevelVariation.TinyHitTolerance, LevelVariation.FastMovement)
            
            36 -> listOf(LevelVariation.LightningFast, LevelVariation.TwinDots, LevelVariation.VibratingDot)
            37 -> listOf(LevelVariation.DoubleTap, LevelVariation.LinearMovement, LevelVariation.MemoryChallenge)
            38 -> listOf(LevelVariation.TinyDot, LevelVariation.FastMovement, LevelVariation.TapTimeChallenge)
            39 -> listOf(LevelVariation.ShrinkingDot, LevelVariation.SlowMovement, LevelVariation.TinyHitTolerance)
            40 -> listOf(LevelVariation.ChaosMode, LevelVariation.LightningFast)
            
            // Quadruple combinations and chaos (41-60)
            41 -> listOf(LevelVariation.TinyDot, LevelVariation.LightningFast, LevelVariation.VibratingDot, LevelVariation.SlowMovement)
            42 -> listOf(LevelVariation.MemoryChallenge, LevelVariation.ShrinkingDot, LevelVariation.DoubleTap, LevelVariation.TinyHitTolerance)
            43 -> listOf(LevelVariation.TwinDots, LevelVariation.FastMovement, LevelVariation.TapTimeChallenge, LevelVariation.LinearMovement)
            44 -> listOf(LevelVariation.ChaosMode, LevelVariation.MemoryChallenge, LevelVariation.TinyDot)
            45 -> listOf(LevelVariation.LightningFast, LevelVariation.VibratingDot, LevelVariation.ShrinkingDot, LevelVariation.TinyHitTolerance)
            
            46 -> listOf(LevelVariation.FastMovement, LevelVariation.DoubleTap, LevelVariation.TwinDots, LevelVariation.SlowMovement)
            47 -> listOf(LevelVariation.ChaosMode, LevelVariation.TapTimeChallenge, LevelVariation.LinearMovement)
            48 -> listOf(LevelVariation.MemoryChallenge, LevelVariation.TinyDot, LevelVariation.VibratingDot, LevelVariation.ShrinkingDot)
            49 -> listOf(LevelVariation.LightningFast, LevelVariation.FastMovement, LevelVariation.TinyHitTolerance, LevelVariation.DoubleTap)
            50 -> listOf(LevelVariation.ChaosMode, LevelVariation.TwinDots, LevelVariation.SlowMovement)
            
            // Intense combinations (51-70)
            51 -> listOf(LevelVariation.TinyDot, LevelVariation.LightningFast, LevelVariation.VibratingDot, LevelVariation.FastMovement, LevelVariation.ShrinkingDot)
            52 -> listOf(LevelVariation.ChaosMode, LevelVariation.MemoryChallenge, LevelVariation.DoubleTap, LevelVariation.TinyHitTolerance)
            53 -> listOf(LevelVariation.TwinDots, LevelVariation.TapTimeChallenge, LevelVariation.LinearMovement, LevelVariation.VibratingDot)
            54 -> listOf(LevelVariation.LightningFast, LevelVariation.ShrinkingDot, LevelVariation.FastMovement, LevelVariation.SlowMovement)
            55 -> listOf(LevelVariation.ChaosMode, LevelVariation.TinyDot, LevelVariation.MemoryChallenge, LevelVariation.TinyHitTolerance)
            
            56 -> listOf(LevelVariation.DoubleTap, LevelVariation.TwinDots, LevelVariation.VibratingDot, LevelVariation.LinearMovement)
            57 -> listOf(LevelVariation.ChaosMode, LevelVariation.LightningFast, LevelVariation.FastMovement, LevelVariation.ShrinkingDot)
            58 -> listOf(LevelVariation.TapTimeChallenge, LevelVariation.MemoryChallenge, LevelVariation.TinyDot, LevelVariation.SlowMovement)
            59 -> listOf(LevelVariation.TinyHitTolerance, LevelVariation.VibratingDot, LevelVariation.DoubleTap, LevelVariation.TwinDots)
            60 -> listOf(LevelVariation.ChaosMode, LevelVariation.LinearMovement, LevelVariation.FastMovement)
            
            // Maximum chaos (61-80)
            61 -> listOf(LevelVariation.TinyDot, LevelVariation.LightningFast, LevelVariation.MemoryChallenge, LevelVariation.VibratingDot, LevelVariation.ShrinkingDot, LevelVariation.FastMovement)
            62 -> listOf(LevelVariation.ChaosMode, LevelVariation.DoubleTap, LevelVariation.TwinDots, LevelVariation.TapTimeChallenge, LevelVariation.TinyHitTolerance)
            63 -> listOf(LevelVariation.LinearMovement, LevelVariation.SlowMovement, LevelVariation.VibratingDot, LevelVariation.LightningFast, LevelVariation.MemoryChallenge)
            64 -> listOf(LevelVariation.ChaosMode, LevelVariation.TinyDot, LevelVariation.ShrinkingDot, LevelVariation.FastMovement, LevelVariation.DoubleTap)
            65 -> listOf(LevelVariation.TwinDots, LevelVariation.TapTimeChallenge, LevelVariation.TinyHitTolerance, LevelVariation.VibratingDot, LevelVariation.LinearMovement)
            
            66 -> listOf(LevelVariation.ChaosMode, LevelVariation.LightningFast, LevelVariation.MemoryChallenge, LevelVariation.ShrinkingDot, LevelVariation.SlowMovement)
            67 -> listOf(LevelVariation.TinyDot, LevelVariation.FastMovement, LevelVariation.DoubleTap, LevelVariation.TwinDots, LevelVariation.VibratingDot)
            68 -> listOf(LevelVariation.ChaosMode, LevelVariation.TapTimeChallenge, LevelVariation.TinyHitTolerance, LevelVariation.LinearMovement, LevelVariation.MemoryChallenge)
            69 -> listOf(LevelVariation.LightningFast, LevelVariation.ShrinkingDot, LevelVariation.FastMovement, LevelVariation.SlowMovement, LevelVariation.VibratingDot)
            70 -> listOf(LevelVariation.ChaosMode, LevelVariation.TinyDot, LevelVariation.DoubleTap, LevelVariation.TwinDots, LevelVariation.TapTimeChallenge)
            
            // Pre-nightmare insanity (71-99)
            in 71..79 -> listOf(
                LevelVariation.ChaosMode,
                LevelVariation.TinyDot,
                LevelVariation.LightningFast,
                LevelVariation.MemoryChallenge,
                LevelVariation.VibratingDot,
                LevelVariation.ShrinkingDot,
                LevelVariation.FastMovement,
                LevelVariation.DoubleTap
            )
            
            in 80..89 -> listOf(
                LevelVariation.ChaosMode,
                LevelVariation.TinyDot,
                LevelVariation.LightningFast,
                LevelVariation.MemoryChallenge,
                LevelVariation.VibratingDot,
                LevelVariation.ShrinkingDot,
                LevelVariation.FastMovement,
                LevelVariation.TwinDots,
                LevelVariation.TapTimeChallenge,
                LevelVariation.TinyHitTolerance
            )
            
            in 90..99 -> listOf(
                LevelVariation.ChaosMode,
                LevelVariation.TinyDot,
                LevelVariation.LightningFast,
                LevelVariation.MemoryChallenge,
                LevelVariation.VibratingDot,
                LevelVariation.ShrinkingDot,
                LevelVariation.FastMovement,
                LevelVariation.LinearMovement,
                LevelVariation.TwinDots,
                LevelVariation.TapTimeChallenge,
                LevelVariation.TinyHitTolerance,
                LevelVariation.DoubleTap
            )
            
            // Level 100+ = Pure Nightmare
            else -> listOf(LevelVariation.Nightmare)
        }
    }
}