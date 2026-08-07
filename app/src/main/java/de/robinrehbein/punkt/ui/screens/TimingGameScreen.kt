package de.robinrehbein.punkt.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.data.ScoreStore
import de.robinrehbein.punkt.game.GameHaptics
import de.robinrehbein.punkt.game.GameMode
import de.robinrehbein.punkt.game.TimingGame
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/** Fallen-Zone: klar als Gefahr lesbar, aber unter Zeitdruck verwechselbar. */
private val FakeZoneColor = Color(0xFFB44FD8)
private val FakeZoneCoreColor = Color(0xFF8A2FB0)

/** Himmelsfarbe pro 5er-Stufe: von Tag über Abendrot bis Nacht. */
private val SkyStages = listOf(
    Color(0xFF4EC0CA), // 0+  Tag (türkis)
    Color(0xFF5B9BD5), // 5+  Blau
    Color(0xFF7B6FD0), // 10+ Lila
    Color(0xFFC0616F), // 15+ Altrosa
    Color(0xFFD98A3D), // 20+ Sonnenuntergang
    Color(0xFF3D4A8C), // 25+ Dämmerung
    Color(0xFF2A2640)  // 30+ Nacht
)

private fun twistBannerText(twist: TimingGame.Twist): String = when (twist) {
    TimingGame.Twist.PULSE -> "NEU: PULS-ZONE!"
    TimingGame.Twist.DRIFT -> "NEU: WANDERNDE ZONE!"
    TimingGame.Twist.GHOST -> "NEU: GEISTER-PUNKT!"
    TimingGame.Twist.FAKE -> "NEU: FALLEN-ZONE!"
    TimingGame.Twist.CHAIN -> "NEU: KETTEN-ZONE!"
}

/** Nicht-Compose-Zeitgeber für das Twist-Banner. */
private class BannerState {
    var timeLeft = 0f
}

/**
 * Spielprinzip "STOPP": Der Punkt kreist automatisch auf einer Bahn.
 * Ein Tap, während er in der Zielzone ist, zählt — daneben getappt oder
 * die Zone überfahren ist sofort das Ende. Präzision statt Dauerfeuer.
 * Mit steigendem Score schalten sich Twists frei (Puls, Drift, Geist,
 * Falle, Kette), die pro Zone zufällig gemischt werden.
 */
@Composable
fun TimingGameScreen(
    onSwitchMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = remember { GameHaptics(context) }
    val store = remember { ScoreStore(context) }
    val game = remember { TimingGame() }
    val fx = remember { FxState() }
    val bannerState = remember { BannerState() }
    val mode = GameMode.TIME_STOP

    var frameTick by remember { mutableLongStateOf(0L) }
    var phase by remember { mutableStateOf(TimingGame.Phase.READY) }
    var score by remember { mutableIntStateOf(0) }
    var bestScore by remember { mutableIntStateOf(store.bestScore(mode)) }
    var runNumber by remember { mutableIntStateOf(store.runCount(mode)) }
    var isNewRecord by remember { mutableStateOf(false) }
    var taunt by remember { mutableStateOf("") }
    var showPerfect by remember { mutableStateOf(false) }
    var bannerText by remember { mutableStateOf("") }

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
                bannerState.timeLeft = (bannerState.timeLeft - dt).coerceAtLeast(0f)
                if (bannerState.timeLeft <= 0f && bannerText.isNotEmpty()) {
                    bannerText = ""
                }

                events.forEach { event ->
                    when (event) {
                        is TimingGame.GameEvent.Hit -> haptics.score()
                        is TimingGame.GameEvent.PerfectHit -> haptics.perfect()
                        is TimingGame.GameEvent.ChainNext -> {
                            bannerText = "NOCH EINE!"
                            bannerState.timeLeft = 1.2f
                        }
                        is TimingGame.GameEvent.TwistUnlocked -> {
                            bannerText = twistBannerText(event.twist)
                            bannerState.timeLeft = 2.2f
                            haptics.newRecord()
                        }
                        is TimingGame.GameEvent.Died -> {
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
                        is TimingGame.GameEvent.Settled -> haptics.thud()
                        else -> Unit
                    }
                }

                phase = game.phase
                score = game.score
                showPerfect = game.lastHitPerfect && game.timeSinceHit < 0.6f &&
                    game.phase == TimingGame.Phase.RUNNING
                frameTick++
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    game.tap()
                })
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            frameTick // Frame-Abhängigkeit: erzwingt Neuzeichnen pro Tick.
            drawTimingWorld(game, fx)
        }

        if (showPerfect) {
            Text(
                text = "PERFEKT! +2",
                style = ScoreShadowStyle,
                fontSize = 28.sp,
                color = Color(0xFFFFE95E),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 260.dp)
            )
        }

        if (bannerText.isNotEmpty() && phase == TimingGame.Phase.RUNNING) {
            Text(
                text = bannerText,
                style = ScoreShadowStyle,
                fontSize = 30.sp,
                color = Color(0xFFFF8A3C),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 160.dp)
            )
        }

        when (phase) {
            TimingGame.Phase.READY -> ReadyOverlay(
                bestScore = bestScore,
                runNumber = runNumber,
                hint = "STOPPE DEN PUNKT IN DER GRUENEN ZONE",
                switchLabel = "MODUS: ${GameMode.GRAVITY_FLIP.displayName}",
                onSwitchMode = onSwitchMode
            )
            TimingGame.Phase.RUNNING, TimingGame.Phase.DYING -> ScoreHud(score = score)
            TimingGame.Phase.OVER -> GameOverOverlay(
                score = score,
                bestScore = bestScore,
                isNewRecord = isNewRecord,
                taunt = taunt,
                onRestart = { game.reset() },
                switchLabel = "MODUS: ${GameMode.GRAVITY_FLIP.displayName}",
                onSwitchMode = onSwitchMode
            )
        }
    }
}

// ===== Welt-Rendering =====

private fun DrawScope.drawTimingWorld(game: TimingGame, fx: FxState) {
    val h = size.height
    val w = size.width
    val cell = floor(h / 220f).coerceAtLeast(2f)

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
        // Himmel färbt sich mit jeder 5er-Stufe weiter Richtung Nacht.
        val sky = SkyStages[(game.score / 5).coerceAtMost(SkyStages.size - 1)]
        drawRect(color = sky, topLeft = Offset(-40f, -40f), size = Size(w + 80f, h + 80f))

        // Langsam driftende Wolken
        val drift = game.elapsed * h * 0.01f
        drawCloud(w * 0.1f - drift % (w * 1.4f), h * 0.16f, cell)
        drawCloud(w * 0.75f - drift % (w * 1.4f), h * 0.24f, cell)

        drawStaticGround(cell)

        // Kreisbahn mit Zielzone, ggf. Fallen-Zone und Punkt
        val cx = w / 2f
        val cy = h * 0.44f
        val radius = min(w * 0.36f, h * 0.28f)
        drawTrack(game, cx, cy, radius, cell)
        if (game.isDotVisible) {
            drawTimingDot(game, cx, cy, radius)
        }
    }

    // Weißer Blitz beim Aufprall
    if (fx.flashAlpha > 0f) {
        drawRect(color = Color.White.copy(alpha = fx.flashAlpha.coerceAtMost(1f)))
    }
}

private fun DrawScope.drawStaticGround(cell: Float) {
    val h = size.height
    val w = size.width
    val groundTop = h * 0.88f

    // Büsche vor dem Boden
    var bx = -w * 0.05f
    var i = 0
    while (bx < w) {
        val bushH = if (i % 3 == 0) h * 0.07f else h * 0.045f
        drawRect(
            color = BushShadeColor,
            topLeft = Offset(bx, groundTop - bushH),
            size = Size(w * 0.36f, bushH)
        )
        drawRect(
            color = BushColor,
            topLeft = Offset(bx + w * 0.04f, groundTop - bushH * 1.35f),
            size = Size(w * 0.28f, bushH * 1.35f)
        )
        bx += w * 0.3f
        i++
    }

    // Sand + Grasnarbe
    drawRect(
        color = GroundSand,
        topLeft = Offset(0f, groundTop),
        size = Size(w, h - groundTop)
    )
    drawRect(
        color = GroundSandShade,
        topLeft = Offset(0f, groundTop + cell * 8),
        size = Size(w, cell * 2)
    )
    val toothW = cell * 5f
    drawRect(color = GrassDark, topLeft = Offset(0f, groundTop), size = Size(w, cell * 5))
    var x = 0f
    while (x < w) {
        drawRect(
            color = GrassLight,
            topLeft = Offset(x, groundTop),
            size = Size(toothW, cell * 4)
        )
        x += toothW * 2
    }
    drawRect(color = OutlineColor, topLeft = Offset(0f, groundTop - cell), size = Size(w, cell))
}

/**
 * Die Kreisbahn als Kette blockiger Zellen. Die Zielzone ist grün mit
 * hellem Perfekt-Kern, die Fallen-Zone violett — alles im Pixel-Raster.
 */
private fun DrawScope.drawTrack(
    game: TimingGame,
    cx: Float,
    cy: Float,
    radius: Float,
    cell: Float
) {
    val segments = 72
    val zoneHalf = game.effectiveZoneHalf()
    for (k in 0 until segments) {
        val a = k.toFloat() / segments * (2f * Math.PI.toFloat())
        val px = cx + cos(a) * radius
        val py = cy + sin(a) * radius

        val relativeZone = TimingGame.wrapToPi(a - game.zoneCenter)
        val inZone = abs(relativeZone) <= zoneHalf
        val inPerfectCore = abs(relativeZone) <= zoneHalf * TimingGame.PERFECT_SHARE

        val inFake = game.hasFakeZone &&
            abs(TimingGame.wrapToPi(a - game.fakeZoneCenter)) <= game.zoneHalfWidth
        val inFakeCore = game.hasFakeZone &&
            abs(TimingGame.wrapToPi(a - game.fakeZoneCenter)) <=
            game.zoneHalfWidth * TimingGame.PERFECT_SHARE

        val highlighted = inZone || inFake
        val outer = if (highlighted) cell * 5f else cell * 3f
        val inner = if (highlighted) cell * 3.4f else cell * 1.8f
        val innerColor = when {
            inPerfectCore -> GrassLight
            inZone -> GrassDark
            inFakeCore -> FakeZoneCoreColor
            inFake -> FakeZoneColor
            else -> GroundSandShade
        }

        drawRect(
            color = OutlineColor,
            topLeft = Offset(px - outer / 2f, py - outer / 2f),
            size = Size(outer, outer)
        )
        drawRect(
            color = innerColor,
            topLeft = Offset(px - inner / 2f, py - inner / 2f),
            size = Size(inner, inner)
        )
    }
}

private fun DrawScope.drawTimingDot(
    game: TimingGame,
    cx: Float,
    cy: Float,
    radius: Float
) {
    val h = size.height
    val px = cx + cos(game.angle) * radius
    val py = cy + sin(game.angle) * radius
    val r = h * 0.026f

    drawPixelCircle(
        color = DotBody,
        outline = OutlineColor,
        centerX = px,
        centerY = py,
        radius = r,
        shade = DotShade
    )

    val u = (r * 2f) / GRID
    fun rect(col: Float, row: Float, cols: Float, rows: Float, color: Color) {
        drawRect(
            color = color,
            topLeft = Offset(px - r + col * u, py - r + row * u),
            size = Size(cols * u, rows * u)
        )
    }

    // Glanzpunkt und Auge wie beim Flip-Punkt
    rect(2.5f, 2.5f, 2f, 2f, DotShine)
    rect(7.5f, 3f, 3.5f, 4f, Color.White)
    rect(9.5f, 4f, 1.5f, 2f, OutlineColor)
}
