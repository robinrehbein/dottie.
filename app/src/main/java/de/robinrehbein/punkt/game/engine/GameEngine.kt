package de.robinrehbein.punkt.game.engine

import VibrationManager
import androidx.compose.ui.geometry.Offset
import de.robinrehbein.punkt.game.animations.AnimationFactory
import de.robinrehbein.punkt.game.logic.TimingController
import de.robinrehbein.punkt.game.logic.HitDetection
import de.robinrehbein.punkt.game.logic.LevelManager
import de.robinrehbein.punkt.game.models.GameMode
import de.robinrehbein.punkt.game.models.HitResult
import de.robinrehbein.punkt.game.models.HitType
import de.robinrehbein.punkt.game.models.Point
import de.robinrehbein.punkt.game.models.finalHitTolerance
import de.robinrehbein.punkt.game.models.finalPointSize
import de.robinrehbein.punkt.game.models.finalShowDuration
import de.robinrehbein.punkt.game.models.finalWaitDuration
import de.robinrehbein.punkt.game.models.hasWaitingPhase
import de.robinrehbein.punkt.game.models.tapTimeLimit
import de.robinrehbein.punkt.game.models.movementSpeed
import de.robinrehbein.punkt.game.models.movementType
import de.robinrehbein.punkt.game.models.vibrationIntensity
import de.robinrehbein.punkt.game.models.shrinkingRate
import de.robinrehbein.punkt.scoring.ScoreCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class GameEngine(
    private var screenWidth: Float = 0f,
    private var screenHeight: Float = 0f,
    private val vibrationManager: VibrationManager? = null,
    private val gameMode: GameMode = GameMode.CLASSIC
) {
    private val _gameState = MutableStateFlow<GameState>(GameState.Menu)
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _streak = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak.asStateFlow()

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    private val _currentLevel = MutableStateFlow(1)
    val currentLevel: StateFlow<Int> = _currentLevel.asStateFlow()

    private val _lives = MutableStateFlow(3)
    val lives: StateFlow<Int> = _lives.asStateFlow()

    private val timingController = TimingController()
    private val levelManager = LevelManager()
    private val hitDetection = HitDetection()
    private val scoreCalculator = ScoreCalculator()

    private var gameScope: CoroutineScope? = null
    private var tapStartTime: Long = 0L

    fun updateScreenSize(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
    }

    fun startGame() {
        gameScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        _currentLevel.value = 1
        _score.value = 0
        _lives.value = 3
        
        gameScope?.launch {
            // Countdown Phase (3, 2, 1) - only before first level
            for (countdownNumber in 3 downTo 1) {
                _gameState.value = GameState.Countdown(countdownNumber)
                timingController.waitForDuration(1000L)
            }
            
            startLevel()
        }
    }

    fun handleTap(x: Float, y: Float) {
        val currentState = _gameState.value
        when (currentState) {
            is GameState.WaitingForTap -> {
                // Classic mode: tap during "TAP NOW!" phase
                gameScope?.launch {
                    processHit(currentState.targetPoint, x, y)
                }
            }
            is GameState.ShowingPoint -> {
                // Speedrun mode: tap directly on visible point
                val config = levelManager.getConfigForLevel(_currentLevel.value, gameMode)
                if (!config.hasWaitingPhase) {
                    gameScope?.launch {
                        processHit(currentState.point, x, y)
                    }
                }
            }
            else -> {
                // Ignore taps in other states
            }
        }
    }

    fun pauseGame() {
        timingController.cancel()
        _gameState.value = GameState.Paused
    }

    fun resumeGame() {

    }

    fun stopGame() {
        timingController.cancel()
        gameScope?.cancel()
        _gameState.value = GameState.Menu
    }

    private fun startLevel() {
        gameScope?.launch {
            val config = levelManager.getConfigForLevel(_currentLevel.value, gameMode)
            val point = generateRandomPoint(config.finalPointSize)

            // Phase 1: Punkt anzeigen
            _gameState.value = GameState.ShowingPoint(point)
            tapStartTime = System.currentTimeMillis() // Set tap start time for Speedrun mode
            timingController.waitForDuration(config.finalShowDuration)

            // Phase 2: Conditional waiting phase and tap state
            if (config.hasWaitingPhase) {
                // Classic mode: waiting phase + "TAP NOW!"
                _gameState.value = GameState.WaitingPhase(point)
                timingController.waitForDuration(config.finalWaitDuration)
                tapStartTime = System.currentTimeMillis() // Reset tap start time for Classic mode
                _gameState.value = GameState.WaitingForTap(point)
                
                val timeout = config.tapTimeLimit
                delay(timeout)

                // Prüfen ob immer noch auf Tap gewartet wird
                if (_gameState.value is GameState.WaitingForTap) {
                    processTimeout()
                }
            } else {
                // Speedrun mode: point stays visible and clickable
                // No state change needed - stays in ShowingPoint
                val timeout = config.tapTimeLimit
                delay(timeout)

                // Prüfen ob immer noch im ShowingPoint state (nicht getappt)
                if (_gameState.value is GameState.ShowingPoint) {
                    processTimeout()
                }
            }
        }
    }

    private suspend fun processHit(targetPoint: Point, tapX: Float, tapY: Float) {
        val config = levelManager.getConfigForLevel(_currentLevel.value, gameMode)
        val reactionTime = System.currentTimeMillis() - tapStartTime
        val hitResult = hitDetection.checkHit(targetPoint, tapX, tapY, config.finalHitTolerance, reactionTime)
        vibrationManager?.vibrateForHit(hitResult.hitType)

        if (hitResult.isHit) {
            _streak.value++
        } else {
            _streak.value = 0
        }

        val earnedPoints = scoreCalculator.calculateScore(hitResult, _currentLevel.value, _streak.value)
        _score.value += earnedPoints

        val animations = when (hitResult.hitType) {
            HitType.PERFECT -> AnimationFactory.createPerfectHitAnimation(Offset(tapX, tapY))
            HitType.GOOD -> AnimationFactory.createGoodHitAnimation(Offset(tapX, tapY))
            else -> emptyList()
        }

        _gameState.value = GameState.Feedback(hitResult, targetPoint, animations)
        delay(1000)

        if (hitResult.isHit) {
            _currentLevel.value++
            startLevel()
        } else {
            loseLive()
        }
    }

    private suspend fun processTimeout() {
        val missResult = HitResult(false, Float.MAX_VALUE, 0f, 0, HitType.MISS)
        val currentState = _gameState.value
        when (currentState) {
            is GameState.WaitingForTap -> {
                // Classic mode timeout
                vibrationManager?.vibrateForHit(HitType.MISS)

                _streak.value = 0

                val earnedPoints = scoreCalculator.calculateScore(missResult, _currentLevel.value, _streak.value)
                _score.value += earnedPoints

                _gameState.value = GameState.Feedback(missResult, currentState.targetPoint)
                delay(1000)

                loseLive()
            }
            is GameState.ShowingPoint -> {
                // Speedrun mode timeout
                vibrationManager?.vibrateForHit(HitType.MISS)

                _streak.value = 0

                val earnedPoints = scoreCalculator.calculateScore(missResult, _currentLevel.value, _streak.value)
                _score.value += earnedPoints

                _gameState.value = GameState.Feedback(missResult, currentState.point)
                delay(1000)

                loseLive()
            }
            else -> {
                // No timeout handling needed for other states
            }
        }
    }

    private fun loseLive() {
        _lives.value--
        if (_lives.value <= 0) {
            _gameState.value = GameState.GameOver(_score.value, _currentLevel.value)
        } else {
            startLevel()
        }
    }

    private fun generateRandomPoint(radius: Float): Point {
        val config = levelManager.getConfigForLevel(_currentLevel.value, gameMode)
        val margin = radius + 50f
        val x = Random.nextFloat() * (screenWidth - 2 * margin) + margin
        val y = Random.nextFloat() * (screenHeight - 2 * margin) + margin
        
        return Point(
            initialX = x,
            initialY = y,
            radius = radius,
            movementSpeed = config.movementSpeed,
            movementType = config.movementType,
            vibrationIntensity = config.vibrationIntensity,
            shrinkingRate = config.shrinkingRate,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
    }
}