package de.robinrehbein.punkt.game.animations

import android.R.attr.radius
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
    val position: Offset,
    val velocity: Offset,
    val color: Color,
    val size: Float,
    val lifespan: Long = 1000L
)

data class ExplosionAnimation(
    override val position: Offset,
    override val startTime: Long = System.currentTimeMillis(),
    override val duration: Long = 1000L,
    private val initialParticles: List<Particle>
) : GameAnimation() {

    override fun draw(drawScope: DrawScope) {
        if (!isActive) return
        
        val elapsed = progress // Use normalized progress (0f to 1f)
        val deltaTime = elapsed * duration / 1000f // Convert to seconds for physics
        
        // Calculate current particle positions based on initial state and elapsed time
        initialParticles.forEach { particle ->
            // Physics: position = initial_position + velocity * time + 0.5 * gravity * time^2
            val gravity = 600f // Gravity in pixels per second squared
            val currentPosition = Offset(
                x = particle.position.x + particle.velocity.x * deltaTime,
                y = particle.position.y + particle.velocity.y * deltaTime + 0.5f * gravity * deltaTime * deltaTime
            )
            
            // Fade out over time
            val alpha = (1f - elapsed).coerceIn(0f, 1f)
            
            // Shrink particles over time
            val currentSize = particle.size * (1f - elapsed * 0.1f).coerceAtLeast(0.5f)

            /*drawScope.drawCircle(
                color = particle.color.copy(alpha = alpha),
                radius = currentSize,
                center = currentPosition
            )*/

            drawScope.drawRoundRect(
                color = particle.color.copy(alpha = alpha),
                topLeft = currentPosition - Offset(currentSize, currentSize),
                size = Size(currentSize * 4, currentSize * 4),
                cornerRadius = CornerRadius(currentSize, currentSize)
            )
        }
    }
}

