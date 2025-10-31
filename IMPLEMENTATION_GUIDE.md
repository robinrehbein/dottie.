# Punkt - Detaillierte Implementierungsanweisungen

## 🚀 Implementierungsreihenfolge für Code-Phase

Diese Anleitung führt durch die schrittweise Implementierung von "Punkt" basierend auf der definierten Architektur.

## Phase 1: Core Foundation Setup

### 1.1 Dependencies hinzufügen

**Datei**: [`app/build.gradle.kts`](app/build.gradle.kts)

Ergänze folgende Dependencies:

```kotlin
dependencies {
    // Bestehende Dependencies...
    
    // ViewModel und Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // DataStore für Settings
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Compose Animation
    implementation("androidx.compose.animation:animation:$compose_version")
    
    // Optional: Hilt für Dependency Injection (später)
    // implementation("com.google.dagger:hilt-android:2.48")
    // kapt("com.google.dagger:hilt-compiler:2.48")
}
```

### 1.2 Basis-Datenmodelle erstellen

**Datei**: `app/src/main/java/de/robinrehbein/punkt/game/models/Point.kt`

```kotlin
package de.robinrehbein.punkt.game.models

data class Point(
    val x: Float,
    val y: Float,
    val radius: Float,
    val appearanceTime: Long = System.currentTimeMillis(),
    val disappearanceTime: Long = 0L
) {
    val isVisible: Boolean
        get() = System.currentTimeMillis() in appearanceTime..disappearanceTime
}
```

**Datei**: `app/src/main/java/de/robinrehbein/punkt/game/models/GameConfig.kt`

```kotlin
package de.robinrehbein.punkt.game.models

data class GameConfig(
    val level: Int,
    val pointSize: Float,
    val showDuration: Long,
    val waitDuration: Long,
    val hitTolerance: Float,
    val gameMode: GameMode = GameMode.CLASSIC
)

enum class GameMode {
    CLASSIC,
    GHOST_POINT,
    MOVING_TARGET,
    VIBRATING_POINT
}
```

**Datei**: `app/src/main/java/de/robinrehbein/punkt/game/models/HitResult.kt`

```kotlin
package de.robinrehbein.punkt.game.models

data class HitResult(
    val isHit: Boolean,
    val distance: Float,
    val accuracy: Float, // 0.0 - 1.0
    val points: Int,
    val hitType: HitType
)

enum class HitType {
    PERFECT,    // Zentrum getroffen
    GOOD,       // Nah am Zentrum
    MISS        // Zu weit entfernt
}
```

### 1.3 GameState System

**Datei**: `app/src/main/java/de/robinrehbein/punkt/game/engine/GameState.kt`

```kotlin
package de.robinrehbein.punkt.game.engine

sealed class GameState {
    object Menu : GameState()
    object Ready : GameState()
    data class ShowingPoint(val point: de.robinrehbein.punkt.game.models.Point) : GameState()
    data class WaitingPhase(val targetPoint: de.robinrehbein.punkt.game.models.Point) : GameState()
    data class WaitingForTap(val targetPoint: de.robinrehbein.punkt.game.models.Point) : GameState()
    data class Feedback(
        val hitResult: de.robinrehbein.punkt.game.models.HitResult,
        val targetPoint: de.robinrehbein.punkt.game.models.Point
    ) : GameState()
    data class GameOver(val finalScore: Int, val level: Int) : GameState()
    object Paused : GameState()
}
```

## Phase 2: Game Engine Implementation

### 2.1 TimingController

**Datei**: `app/src/main/java/de/robinrehbein/punkt/game/engine/TimingController.kt`

```kotlin
package de.robinrehbein.punkt.game.engine

import kotlinx.coroutines.*

class TimingController {
    private var currentJob: Job? = null
    
    suspend fun waitForDuration(duration: Long): Boolean {
        return try {
            delay(duration)
            true
        } catch (e: CancellationException) {
            false
        }
    }
    
    fun startCountdown(
        duration: Long,
        onTick: (remaining: Long) -> Unit = {},
        onComplete: () -> Unit
    ): Job {
        currentJob?.cancel()
        currentJob = CoroutineScope(Dispatchers.Main).launch {
            val startTime = System.currentTimeMillis()
            val endTime = startTime + duration
            
            while (System.currentTimeMillis() < endTime) {
                val remaining = endTime - System.currentTimeMillis()
                onTick(remaining)
                delay(16) // ~60 FPS
            }
            onComplete()
        }
        return currentJob!!
    }
    
    fun cancel() {
        currentJob?.cancel()
        currentJob = null
    }
}
```

### 2.2 HitDetection System

**Datei**: `app/src/main/java/de/robinrehbein/punkt/game/logic/HitDetection.kt`

```kotlin
package de.robinrehbein.punkt.game.logic

import de.robinrehbein.punkt.game.models.*
import kotlin.math.*

class HitDetection {
    
    fun checkHit(targetPoint: Point, tapX: Float, tapY: Float, tolerance: Float): HitResult {
        val distance = calculateDistance(targetPoint.x, targetPoint.y, tapX, tapY)
        val isHit = distance <= tolerance
        
        val accuracy = if (isHit) {
            // Accuracy von 1.0 (perfekt) bis 0.0 (am Rand der Toleranz)
            1.0f - (distance / tolerance)
        } else {
            0.0f
        }
        
        val hitType = when {
            distance <= targetPoint.radius * 0.3f -> HitType.PERFECT
            distance <= tolerance -> HitType.GOOD
            else -> HitType.MISS
        }
        
        val points = calculatePoints(hitType, accuracy)
        
        return HitResult(
            isHit = isHit,
            distance = distance,
            accuracy = accuracy,
            points = points,
            hitType = hitType
        )
    }
    
    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x2 - x1).pow(2) + (y2 - y1).pow(2))
    }
    
    private fun calculatePoints(hitType: HitType, accuracy: Float): Int {
        return when (hitType) {
            HitType.PERFECT -> (100 + (accuracy * 50)).toInt()
            HitType.GOOD -> (50 + (accuracy * 25)).toInt()
            HitType.MISS -> 0
        }
    }
}
```

### 2.3 LevelManager

**Datei**: `app/src/main/java/de/robinrehbein/punkt/game/logic/LevelManager.kt`

```kotlin
package de.robinrehbein.punkt.game.logic

import de.robinrehbein.punkt.game.models.GameConfig
import de.robinrehbein.punkt.game.models.GameMode

class LevelManager {
    
    fun getConfigForLevel(level: Int): GameConfig {
        return when {
            level <= 10 -> createBeginnerConfig(level)
            level <= 30 -> createIntermediateConfig(level)
            else -> createAdvancedConfig(level)
        }
    }
    
    private fun createBeginnerConfig(level: Int): GameConfig {
        return GameConfig(
            level = level,
            pointSize = 40f - (level * 2f).coerceAtMost(20f),
            showDuration = 1000L,
            waitDuration = 2000L,
            hitTolerance = 60f - (level * 2f).coerceAtMost(20f)
        )
    }
    
    private fun createIntermediateConfig(level: Int): GameConfig {
        val adjustedLevel = level - 10
        return GameConfig(
            level = level,
            pointSize = 20f - (adjustedLevel * 1f).coerceAtMost(10f),
            showDuration = 800L,
            waitDuration = 2000L + (adjustedLevel * 200L).coerceAtMost(3000L),
            hitTolerance = 40f - (adjustedLevel * 1f).coerceAtMost(20f)
        )
    }
    
    private fun createAdvancedConfig(level: Int): GameConfig {
        return GameConfig(
            level = level,
            pointSize = 10f,
            showDuration = 500L,
            waitDuration = 5000L + ((level - 30) * 100L).coerceAtMost(2000L),
            hitTolerance = 20f,
            gameMode = if (level > 40) GameMode.GHOST_POINT else GameMode.CLASSIC
        )
    }
}
```

### 2.4 GameEngine (Herzstück)

**Datei**: `app/src/main/java/de/robinrehbein/punkt/game/engine/GameEngine.kt`

```kotlin
package de.robinrehbein.punkt.game.engine

import de.robinrehbein.punkt.game.logic.*
import de.robinrehbein.punkt.game.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random

class GameEngine {
    private val _gameState = MutableStateFlow<GameState>(GameState.Menu)
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()
    
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
    
    fun startGame() {
        gameScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        _currentLevel.value = 1
        _score.value = 0
        _lives.value = 3
        startLevel()
    }
    
    private fun startLevel() {
        gameScope?.launch {
            val config = levelManager.getConfigForLevel(_currentLevel.value)
            val point = generateRandomPoint(config.pointSize)
            
            // Phase 1: Punkt anzeigen
            _gameState.value = GameState.ShowingPoint(point)
            timingController.waitForDuration(config.showDuration)
            
            // Phase 2: Warten (Punkt ist unsichtbar)
            _gameState.value = GameState.WaitingPhase(point)
            timingController.waitForDuration(config.waitDuration)
            
            // Phase 3: Bereit für Tap
            _gameState.value = GameState.WaitingForTap(point)
        }
    }
    
    fun handleTap(x: Float, y: Float) {
        val currentState = _gameState.value
        if (currentState is GameState.WaitingForTap) {
            gameScope?.launch {
                val config = levelManager.getConfigForLevel(_currentLevel.value)
                val hitResult = hitDetection.checkHit(
                    currentState.targetPoint, 
                    x, 
                    y, 
                    config.hitTolerance
                )
                
                val finalScore = scoreCalculator.calculateScore(
                    hitResult, 
                    _currentLevel.value, 
                    0 // TODO: Streak implementieren
                )
                
                _score.value += finalScore
                _gameState.value = GameState.Feedback(hitResult, currentState.targetPoint)
                
                // Feedback anzeigen
                delay(1000)
                
                if (hitResult.isHit) {
                    // Nächstes Level
                    _currentLevel.value++
                    startLevel()
                } else {
                    // Leben verlieren
                    _lives.value--
                    if (_lives.value <= 0) {
                        _gameState.value = GameState.GameOver(_score.value, _currentLevel.value)
                    } else {
                        startLevel()
                    }
                }
            }
        }
    }
    
    private fun generateRandomPoint(radius: Float): Point {
        // TODO: Screen-Größe berücksichtigen
        val screenWidth = 1080f // Placeholder
        val screenHeight = 1920f // Placeholder
        
        val margin = radius + 50f
        val x = Random.nextFloat() * (screenWidth - 2 * margin) + margin
        val y = Random.nextFloat() * (screenHeight - 2 * margin) + margin
        
        return Point(x, y, radius)
    }
    
    fun pauseGame() {
        timingController.cancel()
        _gameState.value = GameState.Paused
    }
    
    fun resumeGame() {
        // TODO: Resume logic implementieren
    }
    
    fun stopGame() {
        timingController.cancel()
        gameScope?.cancel()
        _gameState.value = GameState.Menu
    }
}
```

## Phase 3: UI Implementation

### 3.1 GameViewModel

**Datei**: `app/src/main/java/de/robinrehbein/punkt/viewmodels/GameViewModel.kt`

```kotlin
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
        gameEngine.startGame()
    }
    
    fun handleTap(x: Float, y: Float) {
        gameEngine.handleTap(x, y)
    }
    
    fun pauseGame() {
        gameEngine.pauseGame()
    }
    
    fun resumeGame() {
        gameEngine.resumeGame()
    }
    
    override fun onCleared() {
        super.onCleared()
        gameEngine.stopGame()
    }
}
```

### 3.2 GameScreen Composable

**Datei**: `app/src/main/java/de/robinrehbein/punkt/ui/screens/GameScreen.kt`

```kotlin
package de.robinrehbein.punkt.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.robinrehbein.punkt.game.engine.GameState
import de.robinrehbein.punkt.viewmodels.GameViewModel

@Composable
fun GameScreen(
    viewModel: GameViewModel = viewModel()
) {
    val gameState by viewModel.gameState.collectAsState()
    val score by viewModel.score.collectAsState()
    val level by viewModel.currentLevel.collectAsState()
    val lives by viewModel.lives.collectAsState()
    
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    viewModel.handleTap(offset.x, offset.y)
                }
            }
    ) {
        // Game Canvas
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            when (val state = gameState) {
                is GameState.ShowingPoint -> {
                    drawCircle(
                        color = Color.White,
                        radius = state.point.radius,
                        center = Offset(state.point.x, state.point.y)
                    )
                }
                is GameState.Feedback -> {
                    // Feedback-Visualisierung
                    val color = when (state.hitResult.hitType) {
                        de.robinrehbein.punkt.game.models.HitType.PERFECT -> Color.Green
                        de.robinrehbein.punkt.game.models.HitType.GOOD -> Color.Yellow
                        de.robinrehbein.punkt.game.models.HitType.MISS -> Color.Red
                    }
                    
                    drawCircle(
                        color = color.copy(alpha = 0.5f),
                        radius = state.targetPoint.radius,
                        center = Offset(state.targetPoint.x, state.targetPoint.y)
                    )
                }
                else -> {
                    // Leerer Bildschirm für andere States
                }
            }
        }
        
        // UI Overlay
        GameOverlay(
            score = score,
            level = level,
            lives = lives,
            gameState = gameState,
            onStartGame = { viewModel.startGame() },
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}

@Composable
fun GameOverlay(
    score: Int,
    level: Int,
    lives: Int,
    gameState: GameState,
    onStartGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        Text(
            text = "Score: $score",
            color = Color.White,
            fontSize = 18.sp
        )
        Text(
            text = "Level: $level",
            color = Color.White,
            fontSize = 16.sp
        )
        Text(
            text = "Lives: $lives",
            color = Color.White,
            fontSize = 16.sp
        )
        
        when (gameState) {
            is GameState.Menu -> {
                Button(
                    onClick = onStartGame,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Start Game")
                }
            }
            is GameState.GameOver -> {
                Text(
                    text = "Game Over!",
                    color = Color.Red,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Button(
                    onClick = onStartGame,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Restart")
                }
            }
            else -> {
                // Spiel läuft
            }
        }
    }
}
```

### 3.3 MainActivity anpassen

**Datei**: [`app/src/main/java/de/robinrehbein/punkt/MainActivity.kt`](app/src/main/java/de/robinrehbein/punkt/MainActivity.kt)

```kotlin
package de.robinrehbein.punkt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import de.robinrehbein.punkt.ui.screens.GameScreen
import de.robinrehbein.punkt.ui.theme.PunktTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PunktTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GameScreen()
                }
            }
        }
    }
}
```

## Phase 4: Fehlende Komponenten

### 4.1 ScoreCalculator

**Datei**: `app/src/main/java/de/robinrehbein/punkt/scoring/ScoreCalculator.kt`

```kotlin
package de.robinrehbein.punkt.scoring

import de.robinrehbein.punkt.game.models.HitResult

class ScoreCalculator {
    fun calculateScore(hitResult: HitResult, level: Int, streak: Int): Int {
        if (!hitResult.isHit) return 0
        
        val baseScore = hitResult.points
        val levelMultiplier = 1 + (level * 0.1f)
        val streakMultiplier = 1 + (streak * 0.05f)
        
        return (baseScore * levelMultiplier * streakMultiplier).toInt()
    }
}
```

## 🎯 Nächste Implementierungsschritte

1. **Erstelle die Ordnerstruktur** wie in der Architektur definiert
2. **Implementiere die Datenmodelle** (Point, GameConfig, HitResult)
3. **Baue das GameEngine System** schrittweise auf
4. **Erstelle die UI-Komponenten** mit Jetpack Compose
5. **Teste die Grundfunktionalität** mit einfachen Levels
6. **Erweitere um erweiterte Features** (verschiedene Modi, Animationen)

## 🔧 Debugging-Tipps

- Verwende `Log.d()` für Timing-Debugging
- Teste Hit-Detection mit sichtbaren Debug-Kreisen
- Implementiere zunächst ohne Animationen, dann erweitern
- Nutze Compose Preview für UI-Komponenten

Diese Implementierungsanleitung bietet eine klare Roadmap für die Umsetzung von "Punkt" basierend auf der definierten Architektur.