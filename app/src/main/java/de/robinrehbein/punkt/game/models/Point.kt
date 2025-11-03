package de.robinrehbein.punkt.game.models

import androidx.compose.ui.geometry.Offset
import kotlin.math.*

data class Point(
    val initialX: Float,
    val initialY: Float,
    val radius: Float,
    val appearanceTime: Long = System.currentTimeMillis(),
    val disappearanceTime: Long = 0L,
    val movementSpeed: Float = 0f,
    val movementType: MovementType = MovementType.LINEAR,
    val vibrationIntensity: Float = 0f,
    val shrinkingRate: Float = 0f,
    val screenWidth: Float = 0f,
    val screenHeight: Float = 0f
) {
    val isVisible: Boolean
        get() = System.currentTimeMillis() in appearanceTime..disappearanceTime

    /**
     * Get current position based on movement type and elapsed time
     */
    fun getCurrentPosition(): Offset {
        if (movementSpeed == 0f) {
            return getVibratedPosition(Offset(initialX, initialY))
        }

        val elapsedTime = (System.currentTimeMillis() - appearanceTime) / 1000f
        val basePosition = when (movementType) {
            MovementType.LINEAR -> getLinearPosition(elapsedTime)
            MovementType.CIRCULAR -> getCircularPosition(elapsedTime)
            MovementType.ZIGZAG -> getZigzagPosition(elapsedTime)
            MovementType.SINE_WAVE -> getSineWavePosition(elapsedTime)
        }

        return getVibratedPosition(basePosition)
    }

    /**
     * Get current radius considering shrinking
     */
    fun getCurrentRadius(): Float {
        if (shrinkingRate == 0f) return radius
        
        val elapsedTime = (System.currentTimeMillis() - appearanceTime) / 1000f
        val shrunkRadius = radius - (shrinkingRate * elapsedTime)
        return shrunkRadius.coerceAtLeast(radius * 0.1f) // Minimum 10% of original size
    }

    private fun getLinearPosition(elapsedTime: Float): Offset {
        // Simple linear movement in a random direction
        val angle = (initialX + initialY).hashCode() % 360 * PI / 180
        val distance = movementSpeed * elapsedTime
        
        var newX = initialX + (cos(angle) * distance).toFloat()
        var newY = initialY + (sin(angle) * distance).toFloat()
        
        // Bounce off walls
        if (newX < radius || newX > screenWidth - radius) {
            newX = newX.coerceIn(radius, screenWidth - radius)
        }
        if (newY < radius || newY > screenHeight - radius) {
            newY = newY.coerceIn(radius, screenHeight - radius)
        }
        
        return Offset(newX, newY)
    }

    private fun getCircularPosition(elapsedTime: Float): Offset {
        val circleRadius = min(screenWidth, screenHeight) * 0.15f
        val angularSpeed = movementSpeed / circleRadius
        val angle = angularSpeed * elapsedTime
        
        val centerX = initialX
        val centerY = initialY
        
        val newX = centerX + circleRadius * cos(angle).toFloat()
        val newY = centerY + circleRadius * sin(angle).toFloat()
        
        // Keep within bounds
        val boundedX = newX.coerceIn(radius, screenWidth - radius)
        val boundedY = newY.coerceIn(radius, screenHeight - radius)
        
        return Offset(boundedX, boundedY)
    }

    private fun getZigzagPosition(elapsedTime: Float): Offset {
        val zigzagFrequency = 2f // How often it changes direction
        val zigzagAmplitude = min(screenWidth, screenHeight) * 0.1f
        
        // Primary movement direction
        val primaryAngle = (initialX + initialY).hashCode() % 360 * PI / 180
        val primaryDistance = movementSpeed * elapsedTime * 0.7f
        
        // Zigzag perpendicular movement
        val zigzagOffset = sin(elapsedTime * zigzagFrequency * 2 * PI).toFloat() * zigzagAmplitude
        val perpAngle = primaryAngle + PI / 2
        
        var newX = initialX + (cos(primaryAngle) * primaryDistance).toFloat() + (cos(perpAngle) * zigzagOffset).toFloat()
        var newY = initialY + (sin(primaryAngle) * primaryDistance).toFloat() + (sin(perpAngle) * zigzagOffset).toFloat()
        
        // Keep within bounds
        newX = newX.coerceIn(radius, screenWidth - radius)
        newY = newY.coerceIn(radius, screenHeight - radius)
        
        return Offset(newX, newY)
    }

    private fun getSineWavePosition(elapsedTime: Float): Offset {
        val waveFrequency = 1.5f
        val waveAmplitude = min(screenWidth, screenHeight) * 0.12f
        
        // Primary movement direction
        val primaryAngle = (initialX + initialY).hashCode() % 360 * PI / 180
        val primaryDistance = movementSpeed * elapsedTime * 0.8f
        
        // Sine wave perpendicular movement
        val sineOffset = sin(elapsedTime * waveFrequency * 2 * PI).toFloat() * waveAmplitude
        val perpAngle = primaryAngle + PI / 2
        
        var newX = initialX + (cos(primaryAngle) * primaryDistance).toFloat() + (cos(perpAngle) * sineOffset).toFloat()
        var newY = initialY + (sin(primaryAngle) * primaryDistance).toFloat() + (sin(perpAngle) * sineOffset).toFloat()
        
        // Keep within bounds
        newX = newX.coerceIn(radius, screenWidth - radius)
        newY = newY.coerceIn(radius, screenHeight - radius)
        
        return Offset(newX, newY)
    }

    private fun getVibratedPosition(basePosition: Offset): Offset {
        if (vibrationIntensity == 0f) return basePosition
        
        val time = System.currentTimeMillis() / 100f // Fast vibration
        val vibrateX = sin(time * 0.1f) * vibrationIntensity
        val vibrateY = cos(time * 0.13f) * vibrationIntensity // Slightly different frequency for more natural vibration
        
        return Offset(
            basePosition.x + vibrateX,
            basePosition.y + vibrateY
        )
    }

    // Convenience properties for backward compatibility
    val x: Float get() = getCurrentPosition().x
    val y: Float get() = getCurrentPosition().y
}