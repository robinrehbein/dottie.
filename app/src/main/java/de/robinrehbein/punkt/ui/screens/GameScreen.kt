package de.robinrehbein.punkt.ui.screens

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import de.robinrehbein.punkt.R
import de.robinrehbein.punkt.game.engine.GameState
import de.robinrehbein.punkt.game.logic.LevelManager
import de.robinrehbein.punkt.game.models.GameMode
import de.robinrehbein.punkt.game.models.HitType
import de.robinrehbein.punkt.game.models.backgroundColor
import de.robinrehbein.punkt.ui.components.PixelButton
import de.robinrehbein.punkt.viewmodels.GameViewModel

/**
 * Bytesized FontFamily for consistent typography
 */
private val Bytesized = FontFamily(
    Font(R.font.bytesized_regular, FontWeight.Normal)
)

/**
 * Factory for creating GameViewModel with gameMode parameter
 */
class GameViewModelFactory(
    private val context: Context,
    private val gameMode: GameMode
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            return GameViewModel(context, gameMode) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun GameScreen(
    gameMode: GameMode,
    onBackToStart: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: GameViewModel = viewModel(
        key = "GameViewModel_${gameMode.name}",
        factory = GameViewModelFactory(context, gameMode)
    )
    val gameState by viewModel.gameState.collectAsState()
    val score by viewModel.score.collectAsState()
    val currentLevel by viewModel.currentLevel.collectAsState()
    val lives by viewModel.lives.collectAsState()
    val streak by viewModel.streak.collectAsState()

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val level by viewModel.currentLevel.collectAsState()

    val config = remember(level) {
        LevelManager().getConfigForLevel(level)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "particle_animation")

    val animationTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "animation_time"
    )

    // Get system bar insets for safe area calculations
    val systemBarsInsets = WindowInsets.systemBars
    val statusBarHeight = with(density) { systemBarsInsets.getTop(density).toDp() }
    val navigationBarHeight = with(density) { systemBarsInsets.getBottom(density).toDp() }
    
    // Calculate UI overlay height (approximate height of GameOverlay)
    val overlayHeight = 80.dp // Approximate height for score, level, lives display
    
    LaunchedEffect(configuration, systemBarsInsets) {
        val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
        val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
        
        // Calculate playable area dimensions (excluding system UI and game overlay)
        val playableHeight = screenHeight - with(density) {
            (statusBarHeight + navigationBarHeight + overlayHeight).toPx()
        }
        
        viewModel.updateScreenSize(screenWidth, playableHeight)
    }

    // Auto-start game when screen is displayed
    LaunchedEffect(Unit) {
        viewModel.startGame()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(config.backgroundColor)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // UI Overlay at the top
        GameOverlay(
            score = score,
            currentLevel = currentLevel,
            lives = lives,
            streak = streak,
            statusBarHeight = statusBarHeight,
            modifier = Modifier.fillMaxWidth()
        )

        // Game Canvas takes remaining space
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(gameState) {
                    detectTapGestures { offset ->
                        // Adjust tap coordinates to account for overlay offset
                        viewModel.handleTap(offset.x, offset.y)
                    }
                }
        ) {
            GameCanvas(
                gameState = gameState,
                animationTime = animationTime,
                modifier = Modifier.fillMaxSize(),
            )

            GameCenterMessage(
                gameState = gameState,
                onStartGame = { viewModel.startGame() },
                onBackToStart = onBackToStart,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun GameCanvas(
    gameState: GameState,
    animationTime: Float,
    modifier: Modifier
) {
    Canvas(modifier = modifier) {        // Use animationTime to trigger recomposition for smooth animations
        val currentTime = animationTime // This ensures Canvas recomposes when animationTime changes
        
        when (gameState) {
            is GameState.ShowingPoint -> {
                val currentPosition = gameState.point.getCurrentPosition()
                val currentRadius = gameState.point.getCurrentRadius()
                drawPixelPoint(
                    center = currentPosition,
                    radius = currentRadius
                )
            }
            is GameState.Feedback -> {
                val feedbackColor = when (gameState.hitResult.hitType) {
                    HitType.MISS -> Color.Black
                    HitType.BAD -> Color.Red
                    HitType.GOOD -> Color.Yellow
                    HitType.PERFECT -> Color.Green
                }

                val targetPosition = gameState.targetPoint.getCurrentPosition()
                val targetRadius = gameState.targetPoint.getCurrentRadius()
                
                drawCircle(
                    color = feedbackColor.copy(alpha = 0.6f),
                    radius = targetRadius + 10f,
                    center = targetPosition
                )

                drawPixelPoint(
                    center = targetPosition,
                    radius = targetRadius,
                    alpha = 0.3f
                )
                
                // Draw animations from GameState
                // Use animationTime to trigger recomposition for smooth animations
                gameState.animations.forEach { animation ->
                    if (animation.isActive) {
                        animation.draw(this)
                    }
                }
            }
            is GameState.WaitingForTap -> {
                // Optional: Schwacher Hinweis
                /* drawCircle(
                    color = Color.White.copy(alpha = 0.1f),
                    radius = gameState.targetPoint.radius,
                    center = Offset(gameState.targetPoint.x, gameState.targetPoint.y)
                ) */


            }

            else -> {
                // Leerer Bildschirm

            }
        }
    }
}

@Composable
fun GameOverlay(
    score: Int,
    currentLevel: Int,
    lives: Int,
    streak: Int,
    statusBarHeight: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 16.dp
            )
            .fillMaxWidth()
    ) {
        // Game Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SCORE: $score",
                color = Color.White,
                fontFamily = Bytesized,
                fontSize = 16.sp
            )

            Text(
                text = "LEVEL: $currentLevel",
                color = Color.White,
                fontFamily = Bytesized,
                fontSize = 14.sp
            )

        }

        Spacer(modifier = Modifier.height(8.dp))

        // Lives
        Row (
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row {
                Text(
                    text = "LIVES: ",
                    color = Color.White,
                    fontFamily = Bytesized,
                    fontSize = 14.sp
                )
                repeat(lives) {
                    Text(
                        text = "❤️",
                        fontSize = 16.sp
                    )
                }
            }

            Text(
                text = "STREAK: $streak",
                color = Color.White,
                fontFamily = Bytesized,
                fontSize = 14.sp
            )
        }

        // No state-specific UI needed since game auto-starts
    }
}

@Composable
fun GameCenterMessage(
    gameState: GameState,
    onStartGame: () -> Unit,
    onBackToStart: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (gameState) {
            is GameState.GameOver -> {
                Text(
                    text = "GAME OVER",
                    color = Color(0xFFFF5555),
                    fontFamily = Bytesized,
                    fontSize = 32.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Final Score: ${gameState.finalScore}",
                    color = Color.White,
                    fontFamily = Bytesized,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PixelButton(
                        text = "NOCHMAL",
                        onClick = onStartGame,
                        backgroundColor = Color(0xFFE8B4E8),
                        borderColor = Color(0xFF5555FF),
                        textColor = Color(0xFF5555FF),
                        width = 140.dp,
                        height = 50.dp
                    )
                    PixelButton(
                        text = "HAUPTMENÜ",
                        onClick = onBackToStart,
                        backgroundColor = Color(0xFFB0B0B0),
                        borderColor = Color(0xFF666666),
                        textColor = Color(0xFF666666),
                        width = 140.dp,
                        height = 50.dp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

            }

            is GameState.WaitingForTap -> {
                Text(
                    text = "TAP NOW!",
                    color = Color(0xFFFFDD55),
                    fontFamily = Bytesized,
                    fontSize = 22.sp
                )
            }

            is GameState.Countdown -> {
                Text(
                    text = gameState.number.toString(),
                    color = Color.White,
                    fontFamily = Bytesized,
                    fontSize = 48.sp
                )
            }

            is GameState.Feedback -> {
                val reactionTimeSeconds = gameState.hitResult.reactionTimeMs / 1000.0
                val reactionTimeText = String.format("%.1fs", reactionTimeSeconds)
                
                val message = when (gameState.hitResult.hitType) {
                    HitType.PERFECT -> "PERFECT! +${gameState.hitResult.points}"
                    HitType.GOOD -> "GOOD! +${gameState.hitResult.points}"
                    HitType.BAD -> "BAD! -${gameState.hitResult.points}"
                    HitType.MISS -> "MISSED!"
                }

                val color = when (gameState.hitResult.hitType) {
                    HitType.PERFECT -> Color(0xFF55FF55)
                    HitType.GOOD -> Color(0xFFFFDD55)
                    HitType.BAD -> Color(0xFFFF5555)
                    HitType.MISS -> Color(0xFF888888)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = message,
                        color = color,
                        fontFamily = Bytesized,
                        fontSize = 18.sp
                    )
                    if (gameState.hitResult.reactionTimeMs > 0) {
                        Text(
                            text = reactionTimeText,
                            color = Color.White.copy(alpha = 0.8f),
                            fontFamily = Bytesized,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            else -> {
                // Keine zentrale Nachricht
            }
        }
    }
}

/**
 * Draws a pixel-art styled point with pixelated edges
 */
fun DrawScope.drawPixelPoint(
    center: Offset,
    radius: Float,
    backgroundColor: Color = Color(0xFFE8B4E8),
    borderColor: Color = Color(0xFF5555FF),
    alpha: Float = 1.0f
) {
    val pixelSize = (radius * 0.15f).coerceAtLeast(2f)
    val adjustedBackgroundColor = backgroundColor.copy(alpha = alpha)
    val adjustedBorderColor = borderColor.copy(alpha = alpha)
    
    // Draw main body (slightly smaller than radius to leave room for border)
    val bodyRadius = radius - pixelSize
    drawRect(
        color = adjustedBackgroundColor,
        topLeft = Offset(center.x - bodyRadius, center.y - bodyRadius),
        size = Size(bodyRadius * 2, bodyRadius * 2)
    )
    
    // Draw pixelated border - top and bottom
    drawRect(
        color = adjustedBorderColor,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, pixelSize)
    )
    drawRect(
        color = adjustedBorderColor,
        topLeft = Offset(center.x - radius, center.y + radius - pixelSize),
        size = Size(radius * 2, pixelSize)
    )
    
    // Draw pixelated border - left and right with stepped pattern
    val steps = ((radius * 2 - 2 * pixelSize) / pixelSize).toInt().coerceAtLeast(1)
    val stepHeight = (radius * 2 - 2 * pixelSize) / steps
    
    for (i in 0 until steps) {
        val y = center.y - radius + pixelSize + i * stepHeight
        val stepWidth = when {
            i < steps / 4 -> pixelSize * 1.5f // Wider at top
            i < steps * 3 / 4 -> pixelSize // Normal width in middle
            else -> pixelSize * 1.5f // Wider at bottom
        }
        
        // Left border
        drawRect(
            color = adjustedBorderColor,
            topLeft = Offset(center.x - radius, y),
            size = Size(stepWidth, stepHeight + 1f)
        )
        
        // Right border
        drawRect(
            color = adjustedBorderColor,
            topLeft = Offset(center.x + radius - stepWidth, y),
            size = Size(stepWidth, stepHeight + 1f)
        )
    }
    
    // Add corner pixels for more authentic pixel-art look
    val cornerSize = pixelSize * 0.7f
    
    // Top-left corner
    drawRect(
        color = adjustedBorderColor,
        topLeft = Offset(center.x - radius + pixelSize, center.y - radius + pixelSize),
        size = Size(cornerSize, cornerSize)
    )
    
    // Top-right corner
    drawRect(
        color = adjustedBorderColor,
        topLeft = Offset(center.x + radius - pixelSize - cornerSize, center.y - radius + pixelSize),
        size = Size(cornerSize, cornerSize)
    )
    
    // Bottom-left corner
    drawRect(
        color = adjustedBorderColor,
        topLeft = Offset(center.x - radius + pixelSize, center.y + radius - pixelSize - cornerSize),
        size = Size(cornerSize, cornerSize)
    )
    
    // Bottom-right corner
    drawRect(
        color = adjustedBorderColor,
        topLeft = Offset(center.x + radius - pixelSize - cornerSize, center.y + radius - pixelSize - cornerSize),
        size = Size(cornerSize, cornerSize)
    )
}
