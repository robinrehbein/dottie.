package de.robinrehbein.punkt.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import de.robinrehbein.punkt.data.ScoreStore
import de.robinrehbein.punkt.game.GameHaptics
import de.robinrehbein.punkt.game.GameMode
import de.robinrehbein.punkt.game.PunktGame
import kotlinx.coroutines.isActive
import kotlin.math.floor
import kotlin.math.sin

/**
 * Spielprinzip "FLIP": Der Punkt rollt automatisch vorwärts, jeder Tap
 * kippt die Schwerkraft um. Hindernis-Säulen wachsen von Boden und Decke.
 */
@Composable
fun GravityGameScreen(
    onSwitchMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = remember { GameHaptics(context) }
    val store = remember { ScoreStore(context) }
    val game = remember { PunktGame() }
    val fx = remember { FxState() }
    val mode = GameMode.GRAVITY_FLIP

    var frameTick by remember { mutableLongStateOf(0L) }
    var phase by remember { mutableStateOf(PunktGame.Phase.READY) }
    var score by remember { mutableIntStateOf(0) }
    var bestScore by remember { mutableIntStateOf(store.bestScore(mode)) }
    var runNumber by remember { mutableIntStateOf(store.runCount(mode)) }
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
                        PunktGame.GameEvent.SCORED -> haptics.score()
                        PunktGame.GameEvent.DIED -> {
                            haptics.death()
                            fx.flashAlpha = 1f
                            fx.shakeTime = 0.4f
                            val previousBest = store.bestScore(mode)
                            isNewRecord = store.submitRun(mode, game.score)
                            taunt = pickTaunt(game.score, previousBest, isNewRecord)
                            bestScore = store.bestScore(mode)
                            runNumber = store.runCount(mode)
                            if (isNewRecord) haptics.newRecord()
                        }
                        PunktGame.GameEvent.LANDED -> haptics.thud()
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
                    if (event == PunktGame.GameEvent.FLIPPED ||
                        event == PunktGame.GameEvent.STARTED
                    ) {
                        haptics.flip()
                    }
                })
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            frameTick // Frame-Abhängigkeit: erzwingt Neuzeichnen pro Tick.
            game.setAspectRatio(size.width / size.height)
            drawGravityWorld(game, fx)
        }

        when (phase) {
            PunktGame.Phase.READY -> ReadyOverlay(
                bestScore = bestScore,
                runNumber = runNumber,
                hint = "TIPPEN = SCHWERKRAFT KIPPEN",
                switchLabel = "WECHSEL ZU: ${GameMode.TIME_STOP.displayName}",
                onSwitchMode = onSwitchMode
            )
            PunktGame.Phase.RUNNING, PunktGame.Phase.DYING -> ScoreHud(score = score)
            PunktGame.Phase.OVER -> GameOverOverlay(
                score = score,
                bestScore = bestScore,
                isNewRecord = isNewRecord,
                taunt = taunt,
                onRestart = { game.reset() },
                switchLabel = "WECHSEL ZU: ${GameMode.TIME_STOP.displayName}",
                onSwitchMode = onSwitchMode
            )
        }
    }
}

// ===== Welt-Rendering =====

private fun DrawScope.drawGravityWorld(game: PunktGame, fx: FxState) {
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

        val groundTopPx = game.playBottom() * h
        val ceilingBottomPx = game.playTop() * h

        drawClouds(game, cell, groundTopPx)
        drawBushes(game, cell, groundTopPx)
        drawObstacles(game, cell, groundTopPx, ceilingBottomPx)
        drawGround(game, cell, groundTopPx)
        drawCeiling(game, cell, ceilingBottomPx)
        drawDot(game, cell)
    }

    // Weißer Blitz beim Aufprall
    if (fx.flashAlpha > 0f) {
        drawRect(color = Color.White.copy(alpha = fx.flashAlpha.coerceAtMost(1f)))
    }
}

private fun DrawScope.drawClouds(game: PunktGame, cell: Float, groundTopPx: Float) {
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

private fun DrawScope.drawBushes(game: PunktGame, cell: Float, groundTopPx: Float) {
    val h = size.height
    val w = size.width
    val spacing = w * 0.3f
    val parallax = game.scrollOffset * h * 0.5f

    var i = -1
    while (i * spacing - (parallax % spacing) < w + spacing) {
        val x = i * spacing - (parallax % spacing)
        val tall = (i + floor(parallax / spacing).toInt()).mod(3) == 0
        val bushH = if (tall) h * 0.07f else h * 0.045f
        drawBush(x, groundTopPx, spacing * 1.2f, bushH)
        i++
    }
}

private fun DrawScope.drawBush(x: Float, groundTopPx: Float, width: Float, height: Float) {
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

private fun DrawScope.drawObstacles(
    game: PunktGame,
    cell: Float,
    groundTopPx: Float,
    ceilingBottomPx: Float
) {
    val h = size.height
    game.obstacles.forEach { obstacle ->
        val x = obstacle.x * h
        val bw = PunktGame.OBSTACLE_WIDTH * h

        if (obstacle.floorHeight > 0f) {
            val blockH = obstacle.floorHeight * h
            drawBlock(x, groundTopPx - blockH, bw, blockH, cell, capOnTop = true)
        }
        if (obstacle.ceilingHeight > 0f) {
            val blockH = obstacle.ceilingHeight * h
            drawBlock(x, ceilingBottomPx, bw, blockH, cell, capOnTop = false)
        }
    }
}

/**
 * Eine Hindernis-Säule im Kisten-Look: Umriss, Körper, Licht/Schatten
 * und eine helle Kappe an der Seite, die in den Korridor zeigt.
 */
private fun DrawScope.drawBlock(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    cell: Float,
    capOnTop: Boolean
) {
    drawRect(color = OutlineColor, topLeft = Offset(x, y), size = Size(width, height))
    drawRect(
        color = BlockBody,
        topLeft = Offset(x + cell, y + cell),
        size = Size(width - 2 * cell, height - 2 * cell)
    )
    // Highlight links, Schatten rechts
    drawRect(
        color = BlockLight,
        topLeft = Offset(x + cell, y + cell),
        size = Size(cell * 2, height - 2 * cell)
    )
    drawRect(
        color = BlockDark,
        topLeft = Offset(x + width - cell * 3, y + cell),
        size = Size(cell * 2, height - 2 * cell)
    )
    // Helle Kappe an der gefährlichen Kante
    val capY = if (capOnTop) y + cell else y + height - cell * 4
    drawRect(
        color = BlockCap,
        topLeft = Offset(x + cell, capY),
        size = Size(width - 2 * cell, cell * 3)
    )
}

private fun DrawScope.drawGround(game: PunktGame, cell: Float, groundTopPx: Float) {
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

/** Die Decke: ein gespiegelter Boden, an dem der Punkt ebenfalls rollt. */
private fun DrawScope.drawCeiling(game: PunktGame, cell: Float, ceilingBottomPx: Float) {
    val h = size.height
    val w = size.width

    // Sandband vom oberen Bildschirmrand bis zur Spielfeld-Oberkante
    drawRect(
        color = GroundSand,
        topLeft = Offset(0f, -40f),
        size = Size(w, ceilingBottomPx + 40f)
    )
    drawRect(
        color = GroundSandShade,
        topLeft = Offset(0f, ceilingBottomPx - cell * 10),
        size = Size(w, cell * 2)
    )

    // Gespiegelte Grasnarbe: Zähne zeigen nach unten
    val toothW = cell * 5f
    val scrollPx = (game.scrollOffset * h) % (toothW * 2)
    drawRect(
        color = GrassDark,
        topLeft = Offset(0f, ceilingBottomPx - cell * 5),
        size = Size(w, cell * 5)
    )
    var x = -scrollPx + toothW
    while (x < w) {
        drawRect(
            color = GrassLight,
            topLeft = Offset(x, ceilingBottomPx - cell * 4),
            size = Size(toothW, cell * 4)
        )
        x += toothW * 2
    }
    // Dunkle Kontur unter dem Gras
    drawRect(color = OutlineColor, topLeft = Offset(0f, ceilingBottomPx), size = Size(w, cell))
}

private fun DrawScope.drawDot(game: PunktGame, cell: Float) {
    val h = size.height
    val cx = game.dotX * h
    val cy = game.dotY * h
    val r = PunktGame.DOT_RADIUS * h

    // Leichte Neigung in Fallrichtung, gespiegelt bei umgekehrter Schwerkraft
    val angle = when (game.phase) {
        PunktGame.Phase.RUNNING, PunktGame.Phase.DYING ->
            (game.dotVelocity * 30f).coerceIn(-30f, 30f)
        else -> 0f
    }

    val inverted = game.gravityDir < 0

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
            // Bei umgedrehter Schwerkraft steht der Punkt "auf dem Kopf".
            val actualRow = if (inverted) GRID - row - rows else row
            drawRect(
                color = color,
                topLeft = Offset(cx - r + col * u, cy - r + actualRow * u),
                size = Size(cols * u, rows * u)
            )
        }

        // Glanzpunkt oben links
        rect(2.5f, 2.5f, 2f, 2f, DotShine)

        // Auge mit Pupille, blickt in Laufrichtung
        rect(7.5f, 3f, 3.5f, 4f, Color.White)
        rect(9.5f, 4f, 1.5f, 2f, OutlineColor)
    }
}
