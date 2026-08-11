package de.robinrehbein.punkt.wear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import de.robinrehbein.punkt.game.TimingGame
import kotlinx.coroutines.isActive

/** Rot fürs "neuer Rekord"-Feedback, wie RecordRed in GameOverlays.kt. */
private val WearRecordRed = Color(0xFFE53935)

/** Banner-Orange und Feier-Gold, wie in ScoreHud/GameOverOverlay am Phone. */
private val WearBannerOrange = Color(0xFFFF8A3C)
private val WearCelebrateGold = Color(0xFFFFE95E)

/** Über diese Restzeit blendet das Rekord-Banner am Ende weich aus. */
private const val BANNER_FADE_SECONDS = 0.4f

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
                    WearRunningOverlay(
                        score = controller.score,
                        // Banner nur im Lauf — während der Todes-Animation
                        // gehört die Bühne dem fallenden Vogel.
                        recordBannerTimeLeft = if (controller.phase == TimingGame.Phase.RUNNING)
                            controller.recordBannerTimeLeft else 0f
                    )
                TimingGame.Phase.OVER -> WearOverOverlay(
                    score = controller.score,
                    bestScore = controller.bestScore,
                    isNewRecord = controller.isNewRecord,
                    taunt = controller.taunt,
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
            // Wie am Phone: BEST nur zeigen, wenn schon ein Lauf gezählt hat —
            // ab Bronze mit der aktuellen Medaillen-Münze daneben.
            if (bestScore > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WearMedalTier.forScore(bestScore)?.let { tier ->
                        WearMedalCoin(tier = tier, coinSize = 14.dp)
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = stringResource(R.string.best, bestScore),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
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
private fun WearRunningOverlay(score: Int, recordBannerTimeLeft: Float) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = score.toString(),
            color = Color.White,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold
        )
        // "REKORD GEKNACKT!" am oberen Rand, sobald der Lauf den alten
        // Bestwert überholt (Timer im Controller, wie die Live-Feier am
        // Phone) — blendet am Ende weich aus statt hart zu verschwinden.
        if (recordBannerTimeLeft > 0f) {
            Text(
                text = stringResource(R.string.banner_record),
                color = WearBannerOrange.copy(
                    alpha = (recordBannerTimeLeft / BANNER_FADE_SECONDS).coerceAtMost(1f)
                ),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp)
            )
        }
    }
}

@Composable
private fun WearOverOverlay(
    score: Int,
    bestScore: Int,
    isNewRecord: Boolean,
    taunt: String,
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
            // Medaillen-Zeile ab Bronze: Münze plus Stufen-Name in der
            // Medaillen-Farbe — klein unter dem Score, der bleibt der Star.
            WearMedalTier.forScore(score)?.let { tier ->
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WearMedalCoin(tier = tier, coinSize = 14.dp)
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(tier.nameRes),
                        color = tier.body,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = stringResource(R.string.best, bestScore),
                color = if (isNewRecord) WearRecordRed else Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            // Bei neuem Rekord gewinnt die Feier, sonst der Spott — wie am
            // Phone (GameOverOverlay), nur eine Nummer kleiner.
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isNewRecord) stringResource(R.string.new_record) else taunt,
                color = if (isNewRecord) WearCelebrateGold else Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
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

/** Medaillen-Münze als kleines Canvas — Zeichnung liegt im WearRenderer. */
@Composable
private fun WearMedalCoin(tier: WearMedalTier, coinSize: Dp) {
    Canvas(modifier = Modifier.size(coinSize)) {
        drawWearMedalCoin(tier)
    }
}
