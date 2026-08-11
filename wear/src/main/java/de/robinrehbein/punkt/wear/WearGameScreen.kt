package de.robinrehbein.punkt.wear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import de.robinrehbein.punkt.game.TimingGame
import kotlinx.coroutines.isActive

/** Rot fürs "neuer Rekord"-Feedback, wie RecordRed in GameOverlays.kt. */
private val WearRecordRed = Color(0xFFE53935)

/**
 * Wear-OS-Prototyp von "STOPP": nur der Classic-Modus aus :core, kein
 * Daily, keine Skins, kein Teilen — Haptik plus dieselben Chiptune-Sounds
 * wie am Phone (WearAudio). Der Rekord liegt lokal auf der Uhr,
 * unabhängig vom Telefon-Store.
 *
 * `controller` lebt in MainActivity statt hier via remember{}, damit
 * onKeyDown (Hardware-Zusatztasten) und dieser Screen denselben Zustand
 * und denselben tap()-Weg teilen.
 */
@Composable
fun WearGameScreen(controller: WearGameController) {
    // Frame-Loop: ein controller.update() pro gerendertem Frame, wie am
    // Phone (siehe TimingGameScreen.kt) — dt-Clamping übernimmt TimingGame
    // selbst.
    LaunchedEffect(controller) {
        var lastFrameNanos = 0L
        while (isActive) {
            withFrameNanos { now ->
                val dt = if (lastFrameNanos == 0L) 0f else (now - lastFrameNanos) / 1_000_000_000f
                lastFrameNanos = now
                controller.update(dt)
            }
        }
    }

    // Blinken über einen eigenen, im Frame-Takt mitlaufenden Zähler statt
    // rememberInfiniteTransition/animateFloat — spart die Abhängigkeit auf
    // androidx.compose.animation, die dieses Modul sonst nicht braucht.
    val blinkVisible = (controller.blinkClock * 1.6f) % 1f < 0.65f

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(controller) {
                    // Ganzflächiger Tap-Handler: TimingGame regelt READY/
                    // RUNNING/OVER (inkl. RESTART_LOCK) selbst — hier reicht
                    // es, den Tap einfach durchzureichen.
                    detectTapGestures(onTap = { controller.tap() })
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                controller.frameTick // Frame-Abhängigkeit: erzwingt Neuzeichnen.
                drawWearWorld(controller.game)
            }

            when (controller.phase) {
                TimingGame.Phase.READY -> WearReadyOverlay(
                    blinkVisible = blinkVisible,
                    bestScore = controller.bestScore,
                    soundOn = controller.soundOn,
                    onToggleSound = { controller.toggleSound() }
                )
                TimingGame.Phase.RUNNING, TimingGame.Phase.DYING ->
                    WearRunningOverlay(score = controller.score)
                TimingGame.Phase.OVER -> WearOverOverlay(
                    score = controller.score,
                    bestScore = controller.bestScore,
                    isNewRecord = controller.isNewRecord,
                    tapHintVisible = controller.phaseElapsed >= TimingGame.RESTART_LOCK_SECONDS,
                    blinkVisible = blinkVisible
                )
            }
        }
    }
}

@Composable
private fun WearReadyOverlay(
    blinkVisible: Boolean,
    bestScore: Int,
    soundOn: Boolean,
    onToggleSound: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.tap),
                color = Color.White.copy(alpha = if (blinkVisible) 1f else 0.25f),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            // Wie am Phone: BEST nur zeigen, wenn schon ein Lauf gezählt hat.
            if (bestScore > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.best, bestScore),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            // Ton-Schalter, dezent unter den Texten. Eigener Tap-Handler:
            // detectTapGestures konsumiert das Up-Event, dadurch feuert der
            // ganzflächige Start-Tap des Parent-Box hier nicht mit. Das
            // Padding liegt INNERHALB des pointerInput-Knotens und
            // vergrößert so die Tap-Fläche über den Text hinaus.
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(if (soundOn) R.string.sound_on else R.string.sound_off),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onToggleSound() })
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun WearRunningOverlay(score: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = score.toString(),
            color = Color.White,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun WearOverOverlay(
    score: Int,
    bestScore: Int,
    isNewRecord: Boolean,
    tapHintVisible: Boolean,
    blinkVisible: Boolean
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score.toString(),
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.best, bestScore),
                color = if (isNewRecord) WearRecordRed else Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            // Erst nach RESTART_LOCK blinken (statt nur ein/aus schalten),
            // sonst wirkt ein Wut-Tap direkt nach dem Tod wie eine
            // funktionslose Anzeige statt wie eine echte Sperre.
            if (tapHintVisible) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.tap),
                    color = Color.White.copy(alpha = if (blinkVisible) 1f else 0.25f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
