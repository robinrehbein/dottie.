package de.robinrehbein.punkt.game.animations

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

object AnimationFactory {

    fun createPerfectHitAnimation(position: Offset): List<GameAnimation> {
        return listOf(
            ExplosionAnimation(position = position, initialParticles = createGoldenParticles(position))
        )
    }

    fun createGoodHitAnimation(position: Offset): List<GameAnimation> {
        return listOf(
            ExplosionAnimation(position = position, initialParticles = createRainbowParticles(position))
        )
    }

    private fun createGoldenParticles(position: Offset): List<Particle> {
        return (0..15).map {
            val angle = (it * 24f) * (Math.PI / 180f)
            val speed = Random.nextFloat() * 3f + 2f // Reasonable speed for per-frame movement
            val velocityX = (Math.cos(angle) * speed).toFloat()
            val velocityY = (Math.sin(angle) * speed).toFloat() - 1f // Slight upward bias

            Particle(
                position = position,
                velocity = Offset(velocityX, velocityY),
                color = when (Random.nextInt(4)) {
                    0 -> Color.Yellow
                    1 -> Color(0xFFFFD700) // Gold
                    2 -> Color(0xFFFFA500) // Orange
                    else -> Color(0xFFFFE55C) // Light Gold
                },
                size = Random.nextFloat() * 6f + 3f
            )
        }
    }

    private fun createRainbowParticles(position: Offset): List<Particle> {
        val colors = listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Blue,
            Color.Magenta,
            Color.Cyan
        )

        return (0..12).map {
            val angle = (it * 30f) * (Math.PI / 180f)
            val speed = Random.nextFloat() * 4f + 1.5f // Reasonable speed for per-frame movement
            val velocityX = (Math.cos(angle) * speed).toFloat()
            val velocityY = (Math.sin(angle) * speed).toFloat() - 0.5f // Slight upward bias

            Particle(
                position = position,
                velocity = Offset(velocityX, velocityY),
                color = colors.random(),
                size = Random.nextFloat() * 8f + 4f
            )
        }
    }
}