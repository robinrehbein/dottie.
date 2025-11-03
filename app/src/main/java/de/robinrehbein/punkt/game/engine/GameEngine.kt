package de.robinrehbein.punkt.game.engine

import androidx.compose.ui.geometry.Offset
import de.robinrehbein.punkt.game.animations.AnimationFactory
import de.robinrehbein.punkt.game.logic.TimingController
import de.robinrehbein.punkt.game.logic.HitDetection
import de.robinrehbein.punkt.game.logic.LevelManager
import de.robinrehbein.punkt.game.models.HitResult
import de.robinrehbein.punkt.game.models.HitType
import de.robinrehbein.punkt.game.models.Point
import de.robinrehbein.punkt.game.models.finalHitTolerance
import de.robinrehbein.punkt.game.models.finalPointSize
import de.robinrehbein.punkt.game.models.finalShowDuration
import de.robinrehbein.punkt.game.models.finalWaitDuration
import de.robinrehbein.punkt.game.models.tapTimeLimit
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

class GameEngine(private var screenWidth: Float = 0f, private var screenHeight: Float = 0f) {
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

    fun updateScreenSize(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
    }

    fun startGame() {
        gameScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        _currentLevel.value = 1
        _score.value = 0
        _lives.value = 3
        startLevel()
    }

    fun handleTap(x: Float, y: Float) {
        val currentState = _gameState.value
        if (currentState is GameState.WaitingForTap) {
            gameScope?.launch {
                processHit(currentState.targetPoint, x, y)
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
            val config = levelManager.getConfigForLevel(_currentLevel.value)
            val point = generateRandomPoint(config.finalPointSize)

            // Phase 1: Punkt anzeigen
            _gameState.value = GameState.ShowingPoint(point)
            timingController.waitForDuration(config.finalShowDuration)

            // Phase 2: Warten
            _gameState.value = GameState.WaitingPhase(point)
            timingController.waitForDuration(config.finalWaitDuration)

            // Phase 3: Bereit für Tap
            _gameState.value = GameState.WaitingForTap(point)

            val timeout = config.tapTimeLimit
            delay(timeout)

            // Prüfen ob immer noch auf Tap gewartet wird
            if (_gameState.value is GameState.WaitingForTap) {
                processTimeout()
            }
        }
    }

    private suspend fun processHit(targetPoint: Point, tapX: Float, tapY: Float) {
        val config = levelManager.getConfigForLevel(_currentLevel.value)
        val hitResult = hitDetection.checkHit(targetPoint, tapX, tapY, config.finalHitTolerance)

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
        if (currentState is GameState.WaitingForTap) {

            _streak.value = 0

            val earnedPoints = scoreCalculator.calculateScore(missResult, _currentLevel.value, _streak.value)
            _score.value += earnedPoints

            _gameState.value = GameState.Feedback(missResult, currentState.targetPoint)
            delay(1000)

            loseLive()
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
        val margin = radius + 50f
        val x = Random.nextFloat() * (screenWidth - 2 * margin) + margin
        val y = Random.nextFloat() * (screenHeight - 2 * margin) + margin
        return Point(x, y, radius)
    }
}