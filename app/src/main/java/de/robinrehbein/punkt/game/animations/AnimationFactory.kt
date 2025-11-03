package de.robinrehbein.punkt.game.animations

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.random.Random

object AnimationFactory {

    fun createPerfectHitAnimation(position: Offset): List<GameAnimation> {
        return listOf(
            ExplosionAnimation(position = position, initialParticles = createPerfectParticles(position))
        )
    }

    fun createGoodHitAnimation(position: Offset): List<GameAnimation> {
        return listOf(
            ExplosionAnimation(position = position, initialParticles = createParticles(position))
        )
    }

    private fun createParticles(position: Offset): List<Particle> {
        return (0..15).map {
            val angle = (it * 24f) * (Math.PI / 180f)
            val speed = Random.nextFloat() * 300f + 200f // Reasonable speed for per-frame movement
            val velocityX = (Math.cos(angle) * speed).toFloat()
            val velocityY = (Math.sin(angle) * speed).toFloat() - 30f // Slight upward bias

            Particle(
                position = position,
                velocity = Offset(velocityX, velocityY),
                color = when (Random.nextInt(4)) {
                    0 -> Color.White
                    1 -> Color.LightGray
                    else -> Color.Gray
                },
                size = Random.nextFloat() * 6f + 6f
            )
        }
    }

    private fun createPerfectParticles(position: Offset): List<Particle> {
        val colors = listOf(
            Color.Magenta,
            Color.Cyan
        )

        return (0..12).map {
            val angle = (it * 30f) * (Math.PI / 180f)
            val speed = Random.nextFloat() * 400f + 200f // Reasonable speed for per-frame movement
            val velocityX = (Math.cos(angle) * speed).toFloat()
            val velocityY = (Math.sin(angle) * speed).toFloat() - 30f // Slight upward bias

            Particle(
                position = position,
                velocity = Offset(velocityX, velocityY),
                color = colors.random(),
                size = Random.nextFloat() * 8f + 8f
            )
        }
    }
}