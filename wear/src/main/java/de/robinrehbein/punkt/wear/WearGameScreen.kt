package de.robinrehbein.punkt.wear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.SkinPaint
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
 * Abdunklung hinter dem Skin-Wähler. Nicht ganz deckend: Die Bahn dahinter
 * bleibt als Kontext sichtbar, die Namen bleiben trotzdem lesbar.
 */
private val WearScrim = Color(0xF00E1018)

/**
 * Wear-OS-Version von "STOPP": Classic- und Daily-Modus aus :core plus
 * freischaltbare Skins — kein Teilen, keine Notifications. Feedback über
 * Haptik plus dieselben Chiptune-Sounds wie am Phone (WearAudio). Rekord,
 * Daily-Stand und Skin-Wahl liegen lokal auf der Uhr, unabhängig vom
 * Telefon-Store.
 *
 * `controller` lebt in MainActivity statt hier via remember{}, damit
 * onKeyDown (Hardware-Zusatztasten) und dieser Screen denselben Zustand
 * und denselben tap()-Weg teilen.
 */
@Composable
internal fun WearGameScreen(controller: WearGameController) {
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
                drawWearWorld(
                    game = controller.game,
                    skin = controller.skin,
                    hour = controller.clockHour,
                    month = controller.clockMonth
                )
            }

            when (controller.phase) {
                GamePhase.READY -> WearReadyOverlay(
                    blinkVisible = blinkVisible,
                    bestScore = controller.bestScore,
                    soundOn = controller.soundOn,
                    onToggleSound = { controller.toggleSound() },
                    dailyMode = controller.dailyMode,
                    dailyBestToday = controller.dailyBestToday,
                    dailyStreak = controller.dailyStreak,
                    onToggleMode = { controller.toggleDailyMode() },
                    skin = controller.skin,
                    hour = controller.clockHour,
                    month = controller.clockMonth,
                    onOpenSkins = { controller.openSkinPicker() }
                )
                GamePhase.RUNNING, GamePhase.DYING ->
                    WearRunningOverlay(
                        score = controller.score,
                        daily = controller.dailyMode,
                        // Banner nur im Lauf — während der Todes-Animation
                        // gehört die Bühne dem fallenden Vogel.
                        recordBannerTimeLeft = if (controller.phase == GamePhase.RUNNING)
                            controller.recordBannerTimeLeft else 0f
                    )
                GamePhase.OVER -> WearOverOverlay(
                    score = controller.score,
                    bestScore = controller.bestScore,
                    isNewRecord = controller.isNewRecord,
                    taunt = controller.taunt,
                    tapHintVisible = controller.phaseElapsed >= TimingGame.RESTART_LOCK_SECONDS,
                    blinkVisible = blinkVisible,
                    dailyMode = controller.dailyMode,
                    dailyBestToday = controller.dailyBestToday,
                    dailyStreak = controller.dailyStreak,
                    onToggleMode = { controller.toggleDailyMode() }
                )
            }

            // Der Wähler liegt über allem: Sein eigener Tap-Handler
            // schluckt den ganzflächigen Start-Tap der Parent-Box, damit
            // ein Griff daneben nicht mitten in der Auswahl einen Lauf
            // startet.
            if (controller.skinPickerOpen) {
                WearSkinPicker(
                    skins = controller.unlockedSkins,
                    selected = controller.skin,
                    collected = controller.collectedSkins,
                    hour = controller.clockHour,
                    month = controller.clockMonth,
                    onPick = { controller.chooseSkin(it) },
                    onClose = { controller.closeSkinPicker() }
                )
            }
        }
    }
}

/**
 * Skin-Wähler: eine scrollbare Liste aller freigeschalteten Skins.
 *
 * Warum keine Durchtipp-Münze mehr: Mit 42 Skins wäre "einen weiter je
 * Tap" im Schnitt ein Dutzend Taps für einen bestimmten Skin, und man
 * sähe dabei nie, was noch kommt. In der Liste ist jeder sichtbare Skin
 * genau einen Tap entfernt, und die Kopfzeile zeigt nebenbei den
 * Sammlungsstand.
 *
 * Bedienbar bleibt sie auf jedem Weg: Wischen scrollt, die Drehkrone
 * schiebt den Cursor Skin für Skin weiter (MainActivity leitet sie
 * dorthin um, solange der Wähler offen ist — der Rotary-Tap wäre hier
 * sinnlos), und ein Tap neben der Liste oder auf die letzte Zeile
 * schließt. Der Cursor wählt sofort sichtbar aus; festgeschrieben wird
 * beim Schließen (siehe WearGameController).
 */
@Composable
private fun WearSkinPicker(
    skins: List<WearDotSkin>,
    selected: WearDotSkin,
    collected: Int,
    hour: Int,
    month: Int,
    onPick: (WearDotSkin) -> Unit,
    onClose: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val cursor = skins.indexOf(selected).coerceAtLeast(0)
    // Die Liste zieht dem Cursor nach, damit die Auswahl bei Krone und
    // beim Öffnen nie außerhalb des Bildes steht (+1 für die Kopfzeile).
    LaunchedEffect(cursor) { listState.animateScrollToItem(cursor + 1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WearScrim)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClose() })
            }
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = stringResource(
                        R.string.skins,
                        collected,
                        SkinPaint.collectableCount()
                    ),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    fontFamily = WearBytesized
                )
            }
            items(skins.size) { index ->
                val entry = skins[index]
                WearSkinRow(
                    skin = entry,
                    selected = entry == selected,
                    hour = hour,
                    month = month,
                    onPick = { onPick(entry) }
                )
            }
            item {
                Text(
                    text = stringResource(R.string.back),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    fontFamily = WearBytesized,
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onClose() })
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * Eine Zeile des Wählers: Vorschau-Münze plus Name. Der Name der
 * Aufzählung IST die Bezeichnung — die Uhr ist einsprachig (siehe
 * WearDotSkin). Der gewählte Skin steht in Gold, das reicht als Marke und
 * spart ein Häkchen-Symbol auf einer ohnehin schmalen Zeile.
 */
@Composable
private fun WearSkinRow(
    skin: WearDotSkin,
    selected: Boolean,
    hour: Int,
    month: Int,
    onPick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // Tap-Fläche über die ganze Zeilenbreite inkl. Polster — auf
            // dem kleinen Display zählt jedes zusätzliche Pixel Ziel.
            .pointerInput(skin) {
                detectTapGestures(onTap = { onPick() })
            }
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            drawWearSkinCoin(skin, hour, month)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = skin.name,
            color = if (selected) WearDotBody else Color.White,
            fontSize = 14.sp,
            fontFamily = WearBytesized
        )
    }
}

@Composable
private fun WearReadyOverlay(
    blinkVisible: Boolean,
    bestScore: Int,
    soundOn: Boolean,
    onToggleSound: () -> Unit,
    dailyMode: Boolean,
    dailyBestToday: Int,
    dailyStreak: Int,
    onToggleMode: () -> Unit,
    skin: WearDotSkin,
    hour: Int,
    month: Int,
    onOpenSkins: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.tap),
                color = Color.White.copy(alpha = if (blinkVisible) 1f else 0.25f),
                fontSize = 26.sp,
                fontFamily = WearBytesized
            )
            if (dailyMode) {
                // Im DAILY-Modus zählen die Tages-Stände statt des
                // Classic-Rekords: Tagesbest und Serie in einer Zeile —
                // sie fehlt, solange beides bei 0 steht (wie am Phone).
                if (dailyBestToday > 0 || dailyStreak > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = listOfNotNull(
                            if (dailyBestToday > 0) {
                                stringResource(R.string.today_score, dailyBestToday)
                            } else {
                                null
                            },
                            if (dailyStreak > 0) {
                                stringResource(R.string.streak, dailyStreak)
                            } else {
                                null
                            }
                        ).joinToString("  ·  "),
                        color = WearDotBody,
                        fontSize = 14.sp,
                        fontFamily = WearBytesized
                    )
                }
            } else if (bestScore > 0) {
                // Wie am Phone: BEST nur zeigen, wenn schon ein Lauf gezählt
                // hat — ab Bronze mit der aktuellen Medaillen-Münze daneben.
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
                        fontFamily = WearBytesized
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            WearModeSwitch(dailyMode = dailyMode, onToggle = onToggleMode)
            // Untere Zeile: Skin-Münze und Ton-Schalter nebeneinander, beide
            // mit eigenem Tap-Handler: detectTapGestures konsumiert das
            // Up-Event, dadurch feuert der ganzflächige Start-Tap der
            // Parent-Box hier nicht mit. Das Padding liegt INNERHALB des
            // pointerInput-Knotens und vergrößert so die Tap-Fläche.
            // Die Vorschau-Münze zeigt den gewählten Skin und öffnet den
            // Wähler — nicht der (in READY weiter kreisende) Vogel: ein
            // bewegtes Ziel wäre auf dem kleinen Display kaum zu treffen.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onOpenSkins() })
                        }
                        .padding(6.dp)
                ) {
                    Canvas(modifier = Modifier.size(16.dp)) {
                        drawWearSkinCoin(skin, hour, month)
                    }
                }
                Text(
                    text = stringResource(if (soundOn) R.string.sound_on else R.string.sound_off),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    fontFamily = WearBytesized,
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onToggleSound() })
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * Modus-Umschalter CLASSIC/DAILY als eine tappbare Zeile — der aktive
 * Modus leuchtet gold, der andere liegt gedimmt daneben, ein Tap wechselt.
 * Nur zwei Modi, darum reicht ein gemeinsames Tap-Ziel für die ganze
 * Zeile (größer und damit treffsicherer als zwei einzelne Wörter).
 */
@Composable
private fun WearModeSwitch(dailyMode: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onToggle() })
            }
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.classic),
            color = if (dailyMode) Color.White.copy(alpha = 0.35f) else WearDotBody,
            fontSize = 13.sp,
            fontFamily = WearBytesized
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.daily),
            color = if (dailyMode) WearDotBody else Color.White.copy(alpha = 0.35f),
            fontSize = 13.sp,
            fontFamily = WearBytesized
        )
    }
}

@Composable
private fun WearRunningOverlay(score: Int, daily: Boolean, recordBannerTimeLeft: Float) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score.toString(),
                color = Color.White,
                fontSize = 44.sp,
                fontFamily = WearBytesized
            )
            // Dezenter Modus-Hinweis direkt unterm Score (wie ScoreHud am
            // Phone) — oben gehört der Platz dem Rekord-Banner, und am
            // runden Rand würde die Zeile ohnehin angeschnitten.
            if (daily) {
                Text(
                    text = stringResource(R.string.daily),
                    color = WearDotBody,
                    fontSize = 12.sp,
                    fontFamily = WearBytesized
                )
            }
        }
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
                fontFamily = WearBytesized,
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
    blinkVisible: Boolean,
    dailyMode: Boolean,
    dailyBestToday: Int,
    dailyStreak: Int,
    onToggleMode: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score.toString(),
                color = Color.White,
                fontSize = 40.sp,
                fontFamily = WearBytesized
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
                        fontFamily = WearBytesized
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = stringResource(R.string.best, bestScore),
                color = if (isNewRecord) WearRecordRed else Color.White,
                fontSize = 18.sp,
                fontFamily = WearBytesized
            )
            // Bei neuem Rekord gewinnt die Feier, sonst der Spott — wie am
            // Phone (GameOverOverlay), nur eine Nummer kleiner.
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isNewRecord) stringResource(R.string.new_record) else taunt,
                color = if (isNewRecord) WearCelebrateGold else Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontFamily = WearBytesized
            )
            // Nach einem Daily-Lauf: Tagesbest und Serie kompakt in einer
            // Zeile (wie die Daily-Zeile im GameOverOverlay am Phone).
            if (dailyMode && (dailyBestToday > 0 || dailyStreak > 0)) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(
                        if (dailyBestToday > 0) {
                            stringResource(R.string.today_score, dailyBestToday)
                        } else {
                            null
                        },
                        if (dailyStreak > 0) {
                            stringResource(R.string.streak, dailyStreak)
                        } else {
                            null
                        }
                    ).joinToString("  ·  "),
                    color = WearDotBody,
                    fontSize = 12.sp,
                    fontFamily = WearBytesized
                )
            }
            // Erst nach RESTART_LOCK blinken (statt nur ein/aus schalten),
            // sonst wirkt ein Wut-Tap direkt nach dem Tod wie eine
            // funktionslose Anzeige statt wie eine echte Sperre.
            if (tapHintVisible) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.tap),
                    color = Color.White.copy(alpha = if (blinkVisible) 1f else 0.25f),
                    fontSize = 16.sp,
                    fontFamily = WearBytesized
                )
            }
            // Der Umschalter auch hier: Ein Tap in OVER startet sofort den
            // nächsten Lauf, zurück ins READY-Overlay führt sonst kein Weg —
            // ohne diese Zeile käme man aus der Daily nie zurück zu CLASSIC
            // (und umgekehrt), ohne die App zu verlassen.
            Spacer(modifier = Modifier.height(2.dp))
            WearModeSwitch(dailyMode = dailyMode, onToggle = onToggleMode)
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
