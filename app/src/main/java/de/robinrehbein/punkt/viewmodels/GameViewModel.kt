package de.robinrehbein.punkt.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.robinrehbein.punkt.game.engine.GameEngine
import de.robinrehbein.punkt.game.engine.GameState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {
    private val gameEngine = GameEngine()

    val gameState: StateFlow<GameState> = gameEngine.gameState

    val score: StateFlow<Int> = gameEngine.score

    val currentLevel: StateFlow<Int> = gameEngine.currentLevel

    val lives: StateFlow<Int> = gameEngine.lives

    fun startGame() {
        viewModelScope.launch {
            gameEngine.startGame()
        }
    }

    fun pauseGame() {
        viewModelScope.launch {
            gameEngine.pauseGame()
        }
    }

    fun handleTap(x: Float, y: Float) {
        viewModelScope.launch {
            gameEngine.handleTap(x, y)
        }
    }

    fun resumeGame() {
        viewModelScope.launch {
            gameEngine.resumeGame()
        }
    }
    fun stopGame() {
        viewModelScope.launch {
            gameEngine.stopGame()
        }
    }

    fun restartLevel() {
        viewModelScope.launch {
            gameEngine.stopGame()
            gameEngine.startGame()
        }
    }

    override fun onCleared() {
        super.onCleared()
        gameEngine.stopGame()
    }

    fun getDebugInfo(): String {
        return """
            Current State: ${gameState.value}
            Score: ${score.value}
            Level: ${currentLevel.value}
            Lives: ${lives.value}
        """.trimIndent()
    }

    fun isGameActive(): Boolean {
        return when(gameState.value) {
            is GameState.ShowingPoint, is GameState.WaitingPhase, is GameState.WaitingForTap, is GameState.Feedback -> true
            else -> false
        }
    }

    fun canPause(): Boolean { return isGameActive() && gameState.value !is GameState.Feedback }

    fun updateScreenSize(width: Float, height: Float) {
        gameEngine.updateScreenSize(width, height)
    }
}