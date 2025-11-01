package de.robinrehbein.punkt.game.animations

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

sealed class GameAnimation {
    abstract val startTime: Long
    abstract val duration: Long
    abstract val position: Offset

    val isActive: Boolean
        get() = System.currentTimeMillis() < startTime + duration

    val progress: Float
        get() = ((System.currentTimeMillis() - startTime).toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    abstract fun draw(drawScope: DrawScope)
}

data class Particle(
    val startX: Float,
    val startY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val color: Color,
    val size: Float,
    val lifespan: Long = 500L
)

data class ExplosionAnimation(
    override val position: Offset,
    override val startTime: Long = System.currentTimeMillis(),
    override val duration: Long = 1000L,
    val particles: List<Particle>
) : GameAnimation() {

    override fun draw(drawScope: DrawScope) {
        val elapsed = (System.currentTimeMillis() - startTime) / 1000f

        particles.forEach { particle ->
            val currentX = position.x + (particle.velocityX * elapsed)
            val currentY = position.y + (particle.velocityY * elapsed) + (0.5f * 500f * elapsed * elapsed)

            val alpha = (1f - progress).coerceIn(0f, 1f)

            val currentSize = particle.size * (1f - progress * 0.3f).coerceAtLeast(0.1f)

            drawScope.drawCircle(
                color = particle.color.copy(alpha = alpha),
                radius = currentSize,
                center = Offset(currentX, currentY)
            )
        }
    }
}

