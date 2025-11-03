package de.robinrehbein.punkt.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.robinrehbein.punkt.game.engine.GameState
import de.robinrehbein.punkt.game.logic.LevelManager
import de.robinrehbein.punkt.game.models.HitType
import de.robinrehbein.punkt.game.models.backgroundColor
import de.robinrehbein.punkt.viewmodels.GameViewModel

@Composable
fun GameScreen(
    viewModel: GameViewModel = viewModel(),
    onBackToStart: () -> Unit = {}
) {
    val gameState by viewModel.gameState.collectAsState()
    val score by viewModel.score.collectAsState()
    val currentLevel by viewModel.currentLevel.collectAsState()
    val lives by viewModel.lives.collectAsState()

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

    LaunchedEffect(configuration) {
        val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
        val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
        viewModel.updateScreenSize(screenWidth, screenHeight)
    }

    // Auto-start game when screen is displayed
    LaunchedEffect(Unit) {
        viewModel.startGame()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(config.backgroundColor)
            .pointerInput(gameState) {
                detectTapGestures { offset ->
                    viewModel.handleTap(offset.x, offset.y)
                }
            }
    ) {
        GameCanvas(
            gameState = gameState,
            animationTime = animationTime,
            modifier = Modifier.fillMaxSize(),
        )

        GameOverlay(
            score = score,
            currentLevel = currentLevel,
            lives = lives,
            modifier = Modifier.align(Alignment.TopStart)
        )

        GameCenterMessage(
            gameState = gameState,
            onStartGame = { viewModel.startGame() },
            onBackToStart = onBackToStart,
            modifier = Modifier.align(Alignment.Center)
        )

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
                drawCircle(
                    color = Color.White,
                    radius = gameState.point.radius,
                    center = Offset(gameState.point.x, gameState.point.y)
                )
            }
            is GameState.Feedback -> {
                val feedbackColor = when (gameState.hitResult.hitType) {
                    HitType.MISS -> Color.Black
                    HitType.BAD -> Color.Red
                    HitType.GOOD -> Color.Yellow
                    HitType.PERFECT -> Color.Green
                }

                drawCircle(
                    color = feedbackColor.copy(alpha = 0.6f),
                    radius = gameState.targetPoint.radius + 10f,
                    center = Offset(gameState.targetPoint.x, gameState.targetPoint.y)
                )

                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = gameState.targetPoint.radius,
                    center = Offset(gameState.targetPoint.x, gameState.targetPoint.y)
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        // Game Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Score: $score",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Level: $currentLevel",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Lives
        Row {
            Text(
                text = "Lives: ",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
            repeat(lives) {
                Text(
                    text = "❤️",
                    fontSize = 16.sp
                )
            }
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
                    color = Color.Red,
                    style = MaterialTheme.typography.headlineLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Final Score: ${gameState.finalScore}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = onStartGame,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = "NOCHMAL",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Button(
                        onClick = onBackToStart,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Gray,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "HAUPTMENÜ",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

            }

            is GameState.WaitingForTap -> {
                Text(
                    text = "TAP NOW!",
                    color = Color.Yellow,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            is GameState.Feedback -> {
                val message = when (gameState.hitResult.hitType) {
                    HitType.PERFECT -> "PERFECT! +${gameState.hitResult.points}"
                    HitType.GOOD -> "GOOD! +${gameState.hitResult.points}"
                    HitType.BAD -> "BAD! -${gameState.hitResult.points}"
                    HitType.MISS -> "MISSED!"
                }

                val color = when (gameState.hitResult.hitType) {
                    HitType.PERFECT -> Color.Green
                    HitType.GOOD -> Color.Yellow
                    HitType.BAD -> Color.Red
                    HitType.MISS -> Color.Black
                }

                Text(
                    text = message,
                    color = color,
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            else -> {
                // Keine zentrale Nachricht
            }
        }
    }
}
