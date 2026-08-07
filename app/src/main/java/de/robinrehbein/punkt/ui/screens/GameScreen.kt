package de.robinrehbein.punkt.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.data.ScoreStore
import de.robinrehbein.punkt.game.FlappyGame
import de.robinrehbein.punkt.game.GameHaptics
import de.robinrehbein.punkt.ui.components.PixelButton
import de.robinrehbein.punkt.ui.theme.Bytesized
import kotlinx.coroutines.isActive
import kotlin.math.floor
import kotlin.math.sin

// ===== Farbpalette im Flappy-Bird-Stil =====
private val SkyColor = Color(0xFF4EC0CA)
private val CloudColor = Color(0xFFE9FCFD)
private val BushColor = Color(0xFF71C837)
private val BushShadeColor = Color(0xFF5AA82C)
private val GroundSand = Color(0xFFDED895)
private val GroundSandShade = Color(0xFFD3C87E)
private val GrassLight = Color(0xFF9DE85A)
private val GrassDark = Color(0xFF74BF2E)
private val OutlineColor = Color(0xFF543847)
private val PipeBody = Color(0xFF74BF2E)
private val PipeLight = Color(0xFF9DE85A)
private val PipeDark = Color(0xFF547F22)
private val DotBody = Color(0xFFFFD847)
private val DotShade = Color(0xFFF5A623)
private val DotWing = Color(0xFFFAF3DC)
private val BeakColor = Color(0xFFFF7043)
private val PanelSand = Color(0xFFDED895)
private val TextDark = Color(0xFF543847)
private val RecordRed = Color(0xFFE53935)

private val ScoreShadowStyle = TextStyle(
    fontFamily = Bytesized,
    shadow = Shadow(color = OutlineColor, offset = Offset(4f, 4f), blurRadius = 0f)
)

/** Nicht-Compose-State für Effekte, wird pro Frame im Canvas gelesen. */
private class FxState {
    var flashAlpha = 0f
    var shakeTime = 0f
}

/**
 * Der komplette Spiel-Screen: Ready-Overlay, laufendes Spiel und
 * Game-Over-Panel in einem, damit der Neustart ohne Navigation sofort geht.
 */
@Composable
fun GameScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = remember { GameHaptics(context) }
    val store = remember { ScoreStore(context) }
    val game = remember { FlappyGame() }
    val fx = remember { FxState() }

    var frameTick by remember { mutableLongStateOf(0L) }
    var phase by remember { mutableStateOf(FlappyGame.Phase.READY) }
    var score by remember { mutableIntStateOf(0) }
    var bestScore by remember { mutableIntStateOf(store.bestScore) }
    var runNumber by remember { mutableIntStateOf(store.runCount) }
    var isNewRecord by remember { mutableStateOf(false) }
    var taunt by remember { mutableStateOf("") }

    // Game-Loop: ein Update pro gerendertem Frame.
    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        while (isActive) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (lastFrameNanos == 0L) 0f else (now - lastFrameNanos) / 1_000_000_000f
                lastFrameNanos = now

                val events = game.update(dt)
                fx.flashAlpha = (fx.flashAlpha - dt * 3.5f).coerceAtLeast(0f)
                fx.shakeTime = (fx.shakeTime - dt).coerceAtLeast(0f)

                events.forEach { event ->
                    when (event) {
                        FlappyGame.GameEvent.SCORED -> haptics.score()
                        FlappyGame.GameEvent.DIED -> {
                            haptics.death()
                            fx.flashAlpha = 1f
                            fx.shakeTime = 0.4f
                            val previousBest = store.bestScore
                            isNewRecord = store.submitRun(game.score)
                            taunt = pickTaunt(game.score, previousBest, isNewRecord)
                            bestScore = store.bestScore
                            runNumber = store.runCount
                            if (isNewRecord) haptics.newRecord()
                        }
                        FlappyGame.GameEvent.LANDED -> haptics.thud()
                        else -> Unit
                    }
                }

                phase = game.phase
                score = game.score
                frameTick++
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val event = game.tap()
                    if (event == FlappyGame.GameEvent.FLAPPED ||
                        event == FlappyGame.GameEvent.STARTED
                    ) {
                        haptics.flap()
                    }
                })
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            frameTick // Frame-Abhängigkeit: erzwingt Neuzeichnen pro Tick.
            game.setAspectRatio(size.width / size.height)
            drawWorld(game, fx)
        }

        when (phase) {
            FlappyGame.Phase.READY -> ReadyOverlay(
                bestScore = bestScore,
                runNumber = runNumber
            )
            FlappyGame.Phase.RUNNING, FlappyGame.Phase.DYING -> ScoreHud(score = score)
            FlappyGame.Phase.OVER -> GameOverOverlay(
                score = score,
                bestScore = bestScore,
                isNewRecord = isNewRecord,
                taunt = taunt,
                onRestart = { game.reset() }
            )
        }
    }
}

// ===== Overlays =====

@Composable
private fun ScoreHud(score: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Text(
            text = score.toString(),
            style = ScoreShadowStyle,
            fontSize = 72.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        )
    }
}

@Composable
private fun ReadyOverlay(bestScore: Int, runNumber: Int) {
    val blink by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp)
        ) {
            Text(
                text = "PUNKT.",
                style = ScoreShadowStyle,
                fontSize = 64.sp,
                color = Color.White
            )
            if (bestScore > 0) {
                Text(
                    text = "REKORD: $bestScore",
                    style = ScoreShadowStyle,
                    fontSize = 22.sp,
                    color = Color.White
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 140.dp)
        ) {
            Text(
                text = "TIPPE ZUM FLIEGEN",
                style = ScoreShadowStyle,
                fontSize = 26.sp,
                color = Color.White.copy(alpha = blink)
            )
        }

        if (runNumber > 0) {
            Text(
                text = "VERSUCH #${runNumber + 1}",
                style = ScoreShadowStyle,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
            )
        }
    }
}

@Composable
private fun GameOverOverlay(
    score: Int,
    bestScore: Int,
    isNewRecord: Boolean,
    taunt: String,
    onRestart: () -> Unit
) {
    val blink by rememberInfiniteTransition(label = "overBlink").animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "overBlinkAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = "GAME OVER",
                style = ScoreShadowStyle,
                fontSize = 48.sp,
                color = Color(0xFFFF8A3C)
            )

            Spacer(modifier = Modifier.height(16.dp))

            PixelPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MedalBadge(score = score)
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "PUNKTE",
                            fontFamily = Bytesized,
                            fontSize = 16.sp,
                            color = TextDark
                        )
                        Text(
                            text = score.toString(),
                            fontFamily = Bytesized,
                            fontSize = 40.sp,
                            color = TextDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "REKORD",
                            fontFamily = Bytesized,
                            fontSize = 16.sp,
                            color = if (isNewRecord) RecordRed else TextDark
                        )
                        Text(
                            text = bestScore.toString(),
                            fontFamily = Bytesized,
                            fontSize = 40.sp,
                            color = if (isNewRecord) RecordRed else TextDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isNewRecord) "NEUER REKORD!" else taunt,
                style = ScoreShadowStyle,
                fontSize = 24.sp,
                color = if (isNewRecord) Color(0xFFFFE95E) else Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            PixelButton(
                text = "NOCHMAL!",
                onClick = onRestart,
                backgroundColor = PanelSand,
                borderColor = TextDark,
                textColor = TextDark,
                width = 200.dp,
                height = 60.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "oder tippe irgendwo",
                fontFamily = Bytesized,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = blink)
            )
        }
    }
}

/** Beiger Panel-Hintergrund mit dunklem Pixelrahmen wie im Original. */
@Composable
private fun PixelPanel(content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.matchParentSize()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val border = 4.dp.toPx()
                drawRect(color = OutlineColor)
                drawRect(
                    color = PanelSand,
                    topLeft = Offset(border, border),
                    size = Size(size.width - 2 * border, size.height - 2 * border)
                )
            }
        }
        Box(modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp)) {
            content()
        }
    }
}

/** Medaille ab 10 Punkten: Bronze, Silber, Gold, Platin. */
@Composable
private fun MedalBadge(score: Int) {
    val medalColor = when {
        score >= 40 -> Color(0xFFE5E4E2) // Platin
        score >= 30 -> Color(0xFFFFD700) // Gold
        score >= 20 -> Color(0xFFC0C0C0) // Silber
        score >= 10 -> Color(0xFFCD7F32) // Bronze
        else -> null
    }
    Box(
        modifier = Modifier.size(72.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val border = 3.dp.toPx()
            drawRect(color = OutlineColor)
            drawRect(
                color = GroundSandShade,
                topLeft = Offset(border, border),
                size = Size(size.width - 2 * border, size.height - 2 * border)
            )
            if (medalColor != null) {
                drawPixelCircle(
                    color = medalColor,
                    outline = OutlineColor,
                    centerX = size.width / 2f,
                    centerY = size.height / 2f,
                    radius = size.width * 0.3f
                )
            }
        }
        if (medalColor == null) {
            Text(
                text = "-",
                fontFamily = Bytesized,
                fontSize = 24.sp,
                color = TextDark
            )
        }
    }
}

// ===== Spott-Texte für den Rage-Faktor =====

private fun pickTaunt(score: Int, previousBest: Int, isNewRecord: Boolean): String {
    if (isNewRecord) return "NEUER REKORD!"
    val gap = previousBest - score
    val pool = when {
        score == 0 -> listOf(
            "ERNSTHAFT?",
            "DIE ERSTE ROEHRE...",
            "DAS GING SCHNELL.",
            "WAR DAS ABSICHT?"
        )
        gap in 1..3 -> listOf(
            "SO NAH! NUR $gap GEFEHLT!",
            "FAST! NOCH $gap!",
            "AAARGH! $gap ZU WENIG!"
        )
        score < previousBest / 2 -> listOf(
            "DAS WAR NIX.",
            "DU KANNST MEHR.",
            "SCHON VERGESSEN WIE?"
        )
        else -> listOf(
            "NOCHMAL!",
            "GLEICH KLAPPTS!",
            "DIE ROEHRE KAM AUS DEM NICHTS.",
            "NICHT AUFGEBEN!"
        )
    }
    return pool[(score + previousBest) % pool.size]
}

// ===== Welt-Rendering =====

private fun DrawScope.drawWorld(game: FlappyGame, fx: FxState) {
    val h = size.height
    val w = size.width
    val cell = floor(h / 220f).coerceAtLeast(2f) // "Pixel"-Größe des Retro-Rasters

    // Screen-Shake beim Tod
    val shake = if (fx.shakeTime > 0f) {
        val strength = fx.shakeTime * 28f
        Offset(
            (sin(fx.shakeTime * 91f) * strength),
            (sin(fx.shakeTime * 77f) * strength)
        )
    } else {
        Offset.Zero
    }

    translate(shake.x, shake.y) {
        // Himmel
        drawRect(color = SkyColor, topLeft = Offset(-40f, -40f), size = Size(w + 80f, h + 80f))

        val groundTopPx = game.groundTop() * h

        drawClouds(game, cell, groundTopPx)
        drawBushes(game, cell, groundTopPx)
        drawPipes(game, cell, groundTopPx)
        drawGround(game, cell, groundTopPx)
        drawDot(game, cell)
    }

    // Weißer Blitz beim Aufprall
    if (fx.flashAlpha > 0f) {
        drawRect(color = Color.White.copy(alpha = fx.flashAlpha.coerceAtMost(1f)))
    }
}

private fun DrawScope.drawClouds(game: FlappyGame, cell: Float, groundTopPx: Float) {
    val h = size.height
    val w = size.width
    val spacing = w * 0.55f
    val parallax = game.scrollOffset * h * 0.25f
    val baseY = groundTopPx - h * 0.26f

    var i = -1
    while (i * spacing - (parallax % spacing) < w + spacing) {
        val x = i * spacing - (parallax % spacing) - spacing / 2f
        val bump = if ((i + floor(parallax / spacing).toInt()) % 2 == 0) 0f else h * 0.04f
        drawCloud(x, baseY - bump, cell)
        i++
    }
}

private fun DrawScope.drawCloud(x: Float, y: Float, cell: Float) {
    val u = cell * 2f
    drawRect(color = CloudColor, topLeft = Offset(x, y + u * 2), size = Size(u * 14, u * 3))
    drawRect(color = CloudColor, topLeft = Offset(x + u * 2, y), size = Size(u * 7, u * 2))
    drawRect(color = CloudColor, topLeft = Offset(x + u * 4, y - u * 1.5f), size = Size(u * 4, u * 1.5f))
}

private fun DrawScope.drawBushes(game: FlappyGame, cell: Float, groundTopPx: Float) {
    val h = size.height
    val w = size.width
    val spacing = w * 0.3f
    val parallax = game.scrollOffset * h * 0.5f

    var i = -1
    while (i * spacing - (parallax % spacing) < w + spacing) {
        val x = i * spacing - (parallax % spacing)
        val tall = (i + floor(parallax / spacing).toInt()).mod(3) == 0
        val bushH = if (tall) h * 0.07f else h * 0.045f
        drawBush(x, groundTopPx, spacing * 1.2f, bushH, cell)
        i++
    }
}

private fun DrawScope.drawBush(x: Float, groundTopPx: Float, width: Float, height: Float, cell: Float) {
    // Drei gestufte Ebenen ergeben den blockigen Hügel.
    drawRect(
        color = BushShadeColor,
        topLeft = Offset(x, groundTopPx - height),
        size = Size(width, height)
    )
    drawRect(
        color = BushColor,
        topLeft = Offset(x + width * 0.12f, groundTopPx - height * 1.35f),
        size = Size(width * 0.76f, height * 1.35f)
    )
    drawRect(
        color = BushColor,
        topLeft = Offset(x + width * 0.3f, groundTopPx - height * 1.6f),
        size = Size(width * 0.4f, height * 0.4f)
    )
}

private fun DrawScope.drawPipes(game: FlappyGame, cell: Float, groundTopPx: Float) {
    val h = size.height
    game.pipes.forEach { pipe ->
        val x = pipe.x * h
        val pw = FlappyGame.PIPE_WIDTH * h
        val gapTopPx = pipe.gapTop * h
        val gapBottomPx = pipe.gapBottom * h
        val rimH = cell * 10f
        val rimOverhang = cell * 2.5f

        // Obere Röhre
        drawPipeBody(x, 0f, pw, gapTopPx - rimH, cell)
        drawPipeRim(x - rimOverhang, gapTopPx - rimH, pw + 2 * rimOverhang, rimH, cell)

        // Untere Röhre
        drawPipeRim(x - rimOverhang, gapBottomPx, pw + 2 * rimOverhang, rimH, cell)
        drawPipeBody(x, gapBottomPx + rimH, pw, groundTopPx - gapBottomPx - rimH, cell)
    }
}

private fun DrawScope.drawPipeBody(x: Float, y: Float, width: Float, height: Float, cell: Float) {
    if (height <= 0f) return
    drawRect(color = OutlineColor, topLeft = Offset(x, y), size = Size(width, height))
    drawRect(
        color = PipeBody,
        topLeft = Offset(x + cell, y),
        size = Size(width - 2 * cell, height)
    )
    // Highlight links, Schatten rechts
    drawRect(
        color = PipeLight,
        topLeft = Offset(x + cell * 2, y),
        size = Size(cell * 3, height)
    )
    drawRect(
        color = PipeDark,
        topLeft = Offset(x + width - cell * 4, y),
        size = Size(cell * 3, height)
    )
}

private fun DrawScope.drawPipeRim(x: Float, y: Float, width: Float, height: Float, cell: Float) {
    drawRect(color = OutlineColor, topLeft = Offset(x, y), size = Size(width, height))
    drawRect(
        color = PipeBody,
        topLeft = Offset(x + cell, y + cell),
        size = Size(width - 2 * cell, height - 2 * cell)
    )
    drawRect(
        color = PipeLight,
        topLeft = Offset(x + cell * 2, y + cell),
        size = Size(cell * 3, height - 2 * cell)
    )
    drawRect(
        color = PipeDark,
        topLeft = Offset(x + width - cell * 5, y + cell),
        size = Size(cell * 3, height - 2 * cell)
    )
}

private fun DrawScope.drawGround(game: FlappyGame, cell: Float, groundTopPx: Float) {
    val h = size.height
    val w = size.width

    // Sandfläche bis zum unteren Bildschirmrand
    drawRect(
        color = GroundSand,
        topLeft = Offset(0f, groundTopPx),
        size = Size(w, h - groundTopPx)
    )
    // Dezente Sandstreifen
    drawRect(
        color = GroundSandShade,
        topLeft = Offset(0f, groundTopPx + cell * 8),
        size = Size(w, cell * 2)
    )

    // Grasnarbe mit scrollenden Zähnen
    val toothW = cell * 5f
    val scrollPx = (game.scrollOffset * h) % (toothW * 2)
    drawRect(color = GrassDark, topLeft = Offset(0f, groundTopPx), size = Size(w, cell * 5))
    var x = -scrollPx
    while (x < w) {
        drawRect(
            color = GrassLight,
            topLeft = Offset(x, groundTopPx),
            size = Size(toothW, cell * 4)
        )
        x += toothW * 2
    }
    // Dunkle Kontur über dem Gras
    drawRect(color = OutlineColor, topLeft = Offset(0f, groundTopPx - cell), size = Size(w, cell))
}

private fun DrawScope.drawDot(game: FlappyGame, cell: Float) {
    val h = size.height
    val cx = game.birdX * h
    val cy = game.birdY * h
    val r = FlappyGame.BIRD_RADIUS * h

    val angle = when (game.phase) {
        FlappyGame.Phase.RUNNING, FlappyGame.Phase.DYING ->
            (game.birdVelocity * 70f).coerceIn(-25f, 90f)
        else -> 0f
    }

    val wingUp = when (game.phase) {
        FlappyGame.Phase.READY -> sin(game.elapsed * 10f) > 0f
        FlappyGame.Phase.RUNNING -> game.timeSinceFlap < 0.18f
        else -> false
    }

    rotate(degrees = angle, pivot = Offset(cx, cy)) {
        drawPixelCircle(
            color = DotBody,
            outline = OutlineColor,
            centerX = cx,
            centerY = cy,
            radius = r,
            shade = DotShade
        )

        val u = (r * 2f) / GRID // Zellgröße des Punkt-Rasters
        fun rect(col: Float, row: Float, cols: Float, rows: Float, color: Color) {
            drawRect(
                color = color,
                topLeft = Offset(cx - r + col * u, cy - r + row * u),
                size = Size(cols * u, rows * u)
            )
        }

        // Flügel
        val wingRow = if (wingUp) 4.5f else 7f
        rect(1.5f, wingRow, 4f, 3f, OutlineColor)
        rect(2f, wingRow + 0.5f, 3f, 2f, DotWing)

        // Auge mit Pupille
        rect(7.5f, 2.5f, 3f, 3.5f, Color.White)
        rect(9f, 3.5f, 1.3f, 1.8f, OutlineColor)

        // Schnabel
        rect(10.5f, 6.5f, 3.5f, 2.2f, OutlineColor)
        rect(10.8f, 6.8f, 3f, 1.6f, BeakColor)
    }
}

private const val GRID = 13f

/** Zeichnet einen blockigen "Pixel"-Kreis aus Rasterzellen. */
private fun DrawScope.drawPixelCircle(
    color: Color,
    outline: Color,
    centerX: Float,
    centerY: Float,
    radius: Float,
    shade: Color = color
) {
    val n = GRID.toInt()
    val u = (radius * 2f) / GRID
    val mid = (GRID - 1f) / 2f
    val rr = GRID / 2f - 0.25f

    for (row in 0 until n) {
        for (col in 0 until n) {
            val dx = col - mid
            val dy = row - mid
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            if (dist <= rr) {
                val cellColor = when {
                    dist > rr - 1.1f -> outline
                    row + col > GRID * 1.15f -> shade
                    else -> color
                }
                drawRect(
                    color = cellColor,
                    topLeft = Offset(centerX - radius + col * u, centerY - radius + row * u),
                    size = Size(u + 0.5f, u + 0.5f)
                )
            }
        }
    }
}
