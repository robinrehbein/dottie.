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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import de.robinrehbein.punkt.game.DeathCause
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.SoundSetId
import de.robinrehbein.punkt.game.Twist
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.TimingGame
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.isActive

/** Rot fürs "neuer Rekord"-Feedback, wie RecordRed in ui/.../world/Palette.kt. */
private val WearRecordRed = Color(0xFFE53935)

/** Banner-Orange und Feier-Gold, wie in ScoreHud/GameOverOverlay am Phone. */
private val WearBannerOrange = Color(0xFFFF8A3C)
private val WearCelebrateGold = Color(0xFFFFE95E)

/** Über diese Restzeit blendet das Rekord-Banner am Ende weich aus. */
private const val BANNER_FADE_SECONDS = 0.4f

/**
 * Abdunklung hinter den Wählern. Nicht ganz deckend: Die Bahn dahinter
 * bleibt als Kontext sichtbar, die Namen bleiben trotzdem lesbar.
 */
private val WearScrim = Color(0xF00E1018)

/**
 * Wear-OS-Version von "STOPP": Classic- und Daily-Modus aus :core, alle
 * Twists mit derselben Rückmeldung wie am Telefon (PERFEKT, BLIND, KETTE,
 * Todesursache, Twist-Erklärung) und eine kleine Sammlung aus Vogel, Welt
 * und Ton — kein Teilen, keine Notifications, keine Statistik-Seite.
 * Feedback über Haptik plus dieselben Chiptune-Sounds wie am Phone
 * (WearAudio). Stände und Wahl liegen lokal auf der Uhr und gleichen sich
 * mit dem Telefon ab (StatsSync).
 *
 * `controller` lebt in MainActivity statt hier via remember{}, damit
 * onKeyDown (Hardware-Tasten) und dieser Screen denselben Zustand
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

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(controller) {
                    // Ganzflächiger Tap-Handler: TimingGame regelt READY/
                    // RUNNING/OVER (inkl. RESTART_LOCK) selbst — hier reicht
                    // es, den Tap durchzureichen. Gezählt wird beim
                    // AUFSETZEN des Fingers (onPress), wie am Telefon und
                    // wie bei den Hardware-Tasten: onTap käme erst beim
                    // Loslassen, also je nach Finger 50-150 ms zu spät.
                    // Die Knöpfe der Overlays verbrauchen ihr Aufsetzen
                    // selbst, dort startet also kein Lauf mit.
                    detectTapGestures(onPress = { controller.tap() })
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                controller.frameTick // Frame-Abhängigkeit: erzwingt Neuzeichnen.
                drawWearWorld(
                    game = controller.game,
                    skin = controller.skin,
                    hour = controller.clockHour,
                    month = controller.clockMonth,
                    scene = controller.scene,
                    dotWobble = WearNotYet.wobble(controller.notYetTimeLeft),
                    fx = controller.fx
                )
            }

            when (controller.phase) {
                GamePhase.READY -> WearReadyOverlay(
                    notYet = controller.notYetTimeLeft > 0f,
                    bestScore = controller.bestScore,
                    soundOn = controller.soundOn,
                    dailyMode = controller.dailyMode,
                    dailyBestToday = controller.dailyBestToday,
                    dailyStreak = controller.dailyStreak,
                    onToggleMode = { controller.toggleDailyMode() },
                    skin = controller.skin,
                    scene = controller.scene,
                    hour = controller.clockHour,
                    month = controller.clockMonth,
                    onOpen = { controller.openPicker(it) }
                )
                GamePhase.RUNNING, GamePhase.DYING ->
                    WearRunningOverlay(
                        score = controller.score,
                        daily = controller.dailyMode,
                        // Banner nur im Lauf — während der Todes-Animation
                        // gehört die Bühne dem fallenden Vogel.
                        recordBannerTimeLeft = if (controller.phase == GamePhase.RUNNING)
                            controller.recordBannerTimeLeft else 0f,
                        chainBannerTimeLeft = if (controller.phase == GamePhase.RUNNING)
                            controller.chainBannerTimeLeft else 0f,
                        perfectPoints = controller.perfectPoints,
                        deathCause = if (controller.phase == GamePhase.DYING)
                            controller.deathCause else DeathCause.NONE
                    )
                GamePhase.OVER -> WearOverOverlay(
                    score = controller.score,
                    bestScore = controller.bestScore,
                    isNewRecord = controller.isNewRecord,
                    taunt = controller.taunt,
                    deathCause = controller.deathCause,
                    lesson = controller.lesson,
                    tapHintVisible = controller.phaseElapsed >= TimingGame.RESTART_LOCK_SECONDS,
                    dailyMode = controller.dailyMode,
                    dailyBestToday = controller.dailyBestToday,
                    dailyStreak = controller.dailyStreak,
                    onToggleMode = { controller.toggleDailyMode() }
                )
            }

            // BLIND! +n am Ring, an der Stelle des Treffers ein Stück zur
            // Mitte hin — wie BlindPop am Telefon.
            if (controller.blindPoints > 0 && controller.phase == GamePhase.RUNNING) {
                WearBlindPop(points = controller.blindPoints, angle = controller.blindAngle)
            }

            // Der Wähler liegt über allem: Sein eigener Tap-Handler
            // schluckt den ganzflächigen Start-Tap der Parent-Box, damit
            // ein Griff daneben nicht mitten in der Auswahl einen Lauf
            // startet.
            when (controller.picker) {
                WearPickerKind.SKIN -> WearPickerList(
                    header = stringResource(R.string.skins, controller.collectedSkins, SkinPaint.collectableCount()),
                    entries = controller.unlockedSkins,
                    selected = controller.skin,
                    extraTop = 0,
                    onClose = { controller.closePicker() }
                ) { entry, isSelected ->
                    WearPickerRow(
                        label = entry.name,
                        selected = isSelected,
                        onPick = { controller.chooseSkin(entry) }
                    ) { drawWearSkinCoin(entry, controller.clockHour, controller.clockMonth) }
                }
                WearPickerKind.SCENE -> WearPickerList(
                    header = stringResource(R.string.worlds, controller.unlockedScenes.size, SceneId.entries.size),
                    entries = controller.unlockedScenes,
                    selected = controller.scene,
                    extraTop = 0,
                    onClose = { controller.closePicker() }
                ) { entry, isSelected ->
                    WearPickerRow(
                        label = stringResource(sceneNameRes(entry)),
                        selected = isSelected,
                        onPick = { controller.chooseScene(entry) }
                    ) { drawWearSceneCoin(entry) }
                }
                WearPickerKind.SOUND -> WearPickerList(
                    header = stringResource(R.string.sounds, controller.unlockedSounds.size, SoundSetId.entries.size),
                    entries = controller.unlockedSounds,
                    selected = controller.soundSet,
                    // Oben im Ton-Wähler steht der Schalter TON: AN/AUS —
                    // er ist vom Startbildschirm hierher umgezogen, damit
                    // dort Platz für Vogel, Welt und Ton nebeneinander ist.
                    extraTop = 1,
                    top = {
                        Text(
                            text = stringResource(if (controller.soundOn) R.string.sound_on else R.string.sound_off),
                            color = WearDotBody,
                            fontSize = 14.sp,
                            fontFamily = WearBytesized,
                            modifier = Modifier
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { controller.toggleSound() })
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    },
                    onClose = { controller.closePicker() }
                ) { entry, isSelected ->
                    WearPickerRow(
                        label = stringResource(soundNameRes(entry)),
                        selected = isSelected,
                        onPick = { controller.chooseSound(entry) }
                    ) { drawWearSoundBars(entry, if (isSelected) WearDotBody else Color.White) }
                }
                null -> Unit
            }
        }
    }
}

/**
 * Ein Wähler: scrollbare Liste der freigeschalteten Einträge unter einer
 * Kopfzeile mit dem Sammlungsstand. Dieselbe Bedienung für Vogel, Welt
 * und Ton:
 *
 * - Wischen scrollt, ein Tap auf eine Zeile wählt (Vogel und Welt
 *   schließen dabei, Ton spielt die Hörprobe und bleibt offen).
 * - Die Drehkrone/Lünette schiebt den Cursor Eintrag für Eintrag weiter
 *   (MainActivity leitet sie dorthin um, solange ein Wähler offen ist).
 * - Eine Multifunktionstaste oder ZURÜCK bestätigt und schließt, ein Tap
 *   neben die Liste oder auf die letzte Zeile ebenso.
 *
 * Der Cursor wählt sofort sichtbar aus; festgeschrieben (und ans Telefon
 * gemeldet) wird beim Schließen, siehe WearGameController.closePicker.
 *
 * Warum eine Liste statt einer Durchtipp-Münze: Mit 46 Skins wäre „einen
 * weiter je Tap“ im Schnitt ein Dutzend Taps für einen bestimmten, und
 * man sähe dabei nie, was noch kommt.
 */
@Composable
private fun <T> WearPickerList(
    header: String,
    entries: List<T>,
    selected: T,
    extraTop: Int,
    onClose: () -> Unit,
    top: (@Composable () -> Unit)? = null,
    row: @Composable (entry: T, selected: Boolean) -> Unit
) {
    val listState = rememberScalingLazyListState()
    val cursor = entries.indexOf(selected).coerceAtLeast(0)
    // Die Liste zieht dem Cursor nach, damit die Auswahl bei Krone und
    // beim Öffnen nie außerhalb des Bildes steht (+1 für die Kopfzeile).
    LaunchedEffect(cursor) { listState.animateScrollToItem(cursor + 1 + extraTop) }

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
                    text = header,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    fontFamily = WearBytesized
                )
            }
            if (top != null) item { top() }
            items(entries.size) { index ->
                val entry = entries[index]
                row(entry, entry == selected)
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
 * Eine Zeile eines Wählers: Vorschau plus Name. Der gewählte Eintrag
 * steht in Gold, das reicht als Marke und spart ein Häkchen-Symbol auf
 * einer ohnehin schmalen Zeile.
 */
@Composable
private fun WearPickerRow(
    label: String,
    selected: Boolean,
    onPick: () -> Unit,
    preview: DrawScope.() -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // Tap-Fläche über die ganze Zeilenbreite inkl. Polster — auf
            // dem kleinen Display zählt jedes zusätzliche Pixel Ziel.
            .pointerInput(label) {
                detectTapGestures(onTap = { onPick() })
            }
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Canvas(modifier = Modifier.size(18.dp)) { preview() }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = if (selected) WearDotBody else Color.White,
            fontSize = 14.sp,
            fontFamily = WearBytesized
        )
    }
}

/**
 * BLIND! +n nach einem Treffer im Nebel: auf dem Strahl von der Mitte
 * zum Trefferpunkt, innerhalb des Rings — wie blindPopAnchor am Telefon.
 * Wolkenweiß mit Blaustich, damit es nicht mit dem gelben PERFEKT
 * verwechselt wird.
 */
@Composable
private fun WearBlindPop(points: Int, angle: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val w = constraints.maxWidth
                val h = constraints.maxHeight
                // Etwas weiter außen als am Telefon (0,5): Auf der Uhr
                // steht der Score groß in der Ringmitte.
                val reach = minOf(w, h) * 0.38f * 0.62f
                val x = w / 2f + cos(angle) * reach
                val y = h / 2f + sin(angle) * reach
                layout(w, h) {
                    placeable.place(
                        (x - placeable.width / 2f).roundToInt(),
                        (y - placeable.height / 2f).roundToInt()
                    )
                }
            }
    ) {
        Text(
            text = stringResource(R.string.blind_plus, points),
            color = WearBlindColor,
            fontSize = 14.sp,
            fontFamily = WearBytesized
        )
    }
}

/** Wolkenweiß mit Blaustich, wie BlindPopColor am Telefon. */
private val WearBlindColor = Color(0xFFE8F2FF)

/** Text zu einer Todesursache, oder null für NONE. */
private fun deathCauseRes(cause: DeathCause): Int? = when (cause) {
    DeathCause.NONE -> null
    DeathCause.EARLY -> R.string.death_early
    DeathCause.LATE -> R.string.death_late
    DeathCause.MISSED -> R.string.death_missed
    DeathCause.TRAP -> R.string.death_trap
}

/** Kurze Twist-Erklärung fürs Game-Over. */
private fun lessonRes(twist: Twist): Int = when (twist) {
    Twist.PULSE -> R.string.lesson_pulse
    Twist.DRIFT -> R.string.lesson_drift
    Twist.GHOST -> R.string.lesson_ghost
    Twist.FAKE -> R.string.lesson_fake
    Twist.CHAIN -> R.string.lesson_chain
    Twist.MIRROR -> R.string.lesson_mirror
    Twist.TEMPO -> R.string.lesson_tempo
}

/** Name einer Welt, wie am Telefon übersetzt. */
private fun sceneNameRes(scene: SceneId): Int = when (scene) {
    SceneId.WIESE -> R.string.scene_wiese
    SceneId.WUESTE -> R.string.scene_wueste
    SceneId.MEER -> R.string.scene_meer
    SceneId.BERG -> R.string.scene_berg
    SceneId.STADT -> R.string.scene_stadt
    SceneId.WELTRAUM -> R.string.scene_weltraum
}

/** Name eines Ton-Sets, wie am Telefon übersetzt. */
private fun soundNameRes(set: SoundSetId): Int = when (set) {
    SoundSetId.KLASSIK -> R.string.sound_klassik
    SoundSetId.GLOCKE -> R.string.sound_glocke
    SoundSetId.AMBOSS -> R.string.sound_amboss
    SoundSetId.TROMMEL -> R.string.sound_trommel
    SoundSetId.ORGEL -> R.string.sound_orgel
    SoundSetId.PFEIFE -> R.string.sound_pfeife
    SoundSetId.LASER -> R.string.sound_laser
    SoundSetId.ROBOTER -> R.string.sound_roboter
}

@Composable
private fun WearReadyOverlay(
    notYet: Boolean,
    bestScore: Int,
    soundOn: Boolean,
    dailyMode: Boolean,
    dailyBestToday: Int,
    dailyStreak: Int,
    onToggleMode: () -> Unit,
    skin: WearDotSkin,
    scene: SceneId,
    hour: Int,
    month: Int,
    onOpen: (WearPickerKind) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // TIPP steht ruhig (Plan 8.5 AP-24): Der Lauf startet erst mit
            // einem Tap im Grün, ein blinkendes TIPP lockte zum Tap zur
            // falschen Zeit. Ein Tap daneben ersetzt es 0,7 s lang durch
            // NOCH NICHT, auf jedem Eingabeweg. Feste Zeilenhöhe, damit
            // der Wechsel die Zeilen darunter nicht verschiebt.
            Box(modifier = Modifier.height(34.dp), contentAlignment = Alignment.Center) {
                if (notYet) {
                    Text(
                        text = stringResource(R.string.not_yet),
                        color = WearBannerOrange,
                        fontSize = 20.sp,
                        fontFamily = WearBytesized
                    )
                } else {
                    Text(
                        text = stringResource(R.string.tap),
                        color = Color.White,
                        fontSize = 26.sp,
                        fontFamily = WearBytesized
                    )
                }
            }
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
            // Untere Zeile: die kleine Sammlung — Vogel, Welt, Ton. Jeder
            // Knopf hat einen eigenen Tap-Handler: detectTapGestures
            // verbraucht das Aufsetzen, dadurch startet der ganzflächige
            // Tap der Parent-Box hier nicht mit. Das Padding liegt
            // INNERHALB des pointerInput-Knotens und vergrößert so die
            // Tap-Fläche. Die Knöpfe zeigen die aktuelle Wahl — nicht der
            // (in READY weiter kreisende) Vogel: Ein bewegtes Ziel wäre
            // auf dem kleinen Display kaum zu treffen.
            Row(verticalAlignment = Alignment.CenterVertically) {
                WearIconButton(onTap = { onOpen(WearPickerKind.SKIN) }) {
                    drawWearSkinCoin(skin, hour, month)
                }
                WearIconButton(onTap = { onOpen(WearPickerKind.SCENE) }) {
                    drawWearSceneCoin(scene)
                }
                WearIconButton(onTap = { onOpen(WearPickerKind.SOUND) }) {
                    drawWearSpeaker(muted = !soundOn, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

/** Ein kleiner Symbol-Knopf der Startzeile: 16 dp Bild, 6 dp Tap-Polster ringsum. */
@Composable
private fun WearIconButton(onTap: () -> Unit, icon: DrawScope.() -> Unit) {
    Box(
        modifier = Modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            }
            .padding(6.dp)
    ) {
        Canvas(modifier = Modifier.size(16.dp)) { icon() }
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
private fun WearRunningOverlay(
    score: Int,
    daily: Boolean,
    recordBannerTimeLeft: Float,
    chainBannerTimeLeft: Float,
    perfectPoints: Int,
    deathCause: DeathCause
) {
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
            // Feste Zeilenhöhe für PERFEKT bzw. die Todesursache, damit
            // der Score nicht bei jedem Perfekt-Treffer hüpft. Am Telefon
            // stehen beide unter dem Ring; auf der Uhr ist unter dem Ring
            // kein Platz, dafür liegt der Blick ohnehin in der Mitte.
            Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                val cause = deathCauseRes(deathCause)
                when {
                    cause != null -> Text(
                        text = stringResource(cause),
                        color = WearBannerOrange,
                        fontSize = 16.sp,
                        fontFamily = WearBytesized
                    )
                    perfectPoints > 0 -> Text(
                        text = stringResource(R.string.perfect_plus, perfectPoints),
                        color = WearCelebrateGold,
                        fontSize = 15.sp,
                        fontFamily = WearBytesized
                    )
                }
            }
        }
        // "REKORD GEKNACKT!" am oberen Rand, sobald der Lauf den alten
        // Bestwert überholt (Timer im Controller, wie die Live-Feier am
        // Phone) — blendet am Ende weich aus statt hart zu verschwinden.
        // „NOCH EINE!“ (KETTE) steht an derselben Stelle; der Rekord hat
        // Vorrang, wie am Telefon (Priorität 2 gegen 1).
        val banner = when {
            recordBannerTimeLeft > 0f -> R.string.banner_record to recordBannerTimeLeft
            chainBannerTimeLeft > 0f -> R.string.banner_chain to chainBannerTimeLeft
            else -> null
        }
        if (banner != null) {
            Text(
                text = stringResource(banner.first),
                color = WearBannerOrange.copy(
                    alpha = (banner.second / BANNER_FADE_SECONDS).coerceAtMost(1f)
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
    deathCause: DeathCause,
    lesson: Twist?,
    tapHintVisible: Boolean,
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
            // Warum es vorbei ist, klein unter dem Score — wie am Telefon
            // unter dem Titel des Game-Overs.
            deathCauseRes(deathCause)?.let { cause ->
                Text(
                    text = stringResource(cause),
                    color = WearBannerOrange,
                    fontSize = 12.sp,
                    fontFamily = WearBytesized
                )
            }
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
            // Bei neuem Rekord gewinnt die Feier, danach die Erklärung
            // eines neuen Twists (einmal je Twist, siehe WearLessons), sonst
            // der Spott — eine Zeile, mehr trägt das runde Display nicht.
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    isNewRecord -> stringResource(R.string.new_record)
                    lesson != null -> stringResource(lessonRes(lesson))
                    else -> taunt
                },
                color = when {
                    isNewRecord -> WearCelebrateGold
                    lesson != null -> WearDotBody
                    else -> Color.White.copy(alpha = 0.8f)
                },
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
            // Erst nach RESTART_LOCK zeigen, sonst wirkt ein Wut-Tap direkt
            // nach dem Tod wie eine funktionslose Anzeige statt wie eine
            // echte Sperre. Ohne Blinken, wie im READY.
            if (tapHintVisible) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.tap),
                    color = Color.White,
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
