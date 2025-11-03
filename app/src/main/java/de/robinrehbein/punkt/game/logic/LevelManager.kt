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
        return when {
            level <= 5 -> emptyList()

            level <= 10 -> listOf(LevelVariation.TinyDot)

            level <= 15 -> listOf(LevelVariation.LightningFast)

            level <= 20 -> listOf(LevelVariation.MemoryChallenge)

            level <= 25 -> listOf(
                LevelVariation.TinyDot,
                LevelVariation.LightningFast
            )

            level <= 30 -> listOf(
                LevelVariation.TinyDot,
                LevelVariation.LinearMovement
            )

            level <= 35 -> listOf(
                LevelVariation.TinyDot,
                LevelVariation.ShrinkingDot
            )

            level <= 40 -> listOf(
                LevelVariation.LightningFast,
                LevelVariation.MemoryChallenge,
                LevelVariation.TinyHitTolerance
            )

            else -> listOf(LevelVariation.Nightmare)
        }
    }
}