package de.robinrehbein.punkt.game.animations

import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages game animations independently of game state.
 * Allows animations to persist across state changes and provides centralized control.
 */
class AnimationManager {
    private val _activeAnimations = MutableStateFlow<List<GameAnimation>>(emptyList())
    val activeAnimations: StateFlow<List<GameAnimation>> = _activeAnimations.asStateFlow()
    
    private var animationScope: CoroutineScope? = null
    private var isRunning = false
    
    /**
     * Starts the animation manager and begins the update loop
     */
    fun start() {
        if (isRunning) return
        
        isRunning = true
        animationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        
        // Start the animation update loop
        animationScope?.launch {
            while (isActive && isRunning) {
                updateAnimations()
                delay(16) // ~60 FPS update rate
            }
        }
    }
    
    /**
     * Stops the animation manager and clears all animations
     */
    fun stop() {
        isRunning = false
        animationScope?.cancel()
        animationScope = null
        _activeAnimations.value = emptyList()
    }
    
    /**
     * Adds new animations to the active list
     */
    fun addAnimations(animations: List<GameAnimation>) {
        if (animations.isEmpty()) return
        
        _activeAnimations.value = _activeAnimations.value + animations
    }
    
    /**
     * Adds a single animation to the active list
     */
    fun addAnimation(animation: GameAnimation) {
        addAnimations(listOf(animation))
    }
    
    /**
     * Removes all animations of a specific type
     */
    fun <T : GameAnimation> removeAnimationsOfType(type: Class<T>) {
        _activeAnimations.value = _activeAnimations.value.filterNot { type.isInstance(it) }
    }
    
    /**
     * Clears all active animations
     */
    fun clearAnimations() {
        _activeAnimations.value = emptyList()
    }
    
    /**
     * Updates the animation list by removing inactive animations
     */
    private fun updateAnimations() {
        val currentAnimations = _activeAnimations.value
        val activeAnimations = currentAnimations.filter { it.isActive }
        
        // Only update if the list changed to avoid unnecessary recompositions
        if (activeAnimations.size != currentAnimations.size) {
            _activeAnimations.value = activeAnimations
        }
    }
    
    /**
     * Draws all active animations to the provided DrawScope
     */
    fun drawAnimations(drawScope: DrawScope) {
        _activeAnimations.value.forEach { animation ->
            if (animation.isActive) {
                animation.draw(drawScope)
            }
        }
    }
    
    /**
     * Returns the number of currently active animations
     */
    fun getActiveAnimationCount(): Int {
        return _activeAnimations.value.count { it.isActive }
    }
    
    /**
     * Checks if there are any active animations
     */
    fun hasActiveAnimations(): Boolean {
        return _activeAnimations.value.any { it.isActive }
    }
    
    /**
     * Gets debug information about current animations
     */
    fun getDebugInfo(): String {
        val animations = _activeAnimations.value
        return buildString {
            appendLine("AnimationManager Debug Info:")
            appendLine("- Running: $isRunning")
            appendLine("- Total animations: ${animations.size}")
            appendLine("- Active animations: ${animations.count { it.isActive }}")
            animations.forEachIndexed { index, animation ->
                appendLine("  [$index] ${animation::class.simpleName}: active=${animation.isActive}, progress=${animation.progress}")
            }
        }
    }
}