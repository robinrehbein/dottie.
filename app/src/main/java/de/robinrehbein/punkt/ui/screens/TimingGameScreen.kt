package de.robinrehbein.punkt.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.BuildConfig
import de.robinrehbein.punkt.R
import de.robinrehbein.punkt.ads.AdsManager
import de.robinrehbein.punkt.billing.BillingManager
import de.robinrehbein.punkt.data.ScoreStore
import de.robinrehbein.punkt.game.DailyChallenge
import de.robinrehbein.punkt.game.DotScene
import de.robinrehbein.punkt.game.DotSkin
import de.robinrehbein.punkt.game.DotSound
import de.robinrehbein.punkt.game.GameAudio
import de.robinrehbein.punkt.game.GameHaptics
import de.robinrehbein.punkt.game.Goal
import de.robinrehbein.punkt.game.Ground
import de.robinrehbein.punkt.game.MedalTier
import de.robinrehbein.punkt.game.Progress
import de.robinrehbein.punkt.game.Prop
import de.robinrehbein.punkt.game.PropShape
import de.robinrehbein.punkt.game.RockPart
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SkinState
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.notify.DailyReminder
import de.robinrehbein.punkt.play.Leaderboards
import de.robinrehbein.punkt.share.ScoreCard
import de.robinrehbein.punkt.sync.StatsSync
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Fallen-Zone: klar als Gefahr lesbar, aber unter Zeitdruck verwechselbar. */
private val FakeZoneColor = Color(0xFFB44FD8)
private val FakeZoneCoreColor = Color(0xFF8A2FB0)

/**
 * Himmel, Wolken, Requisiten und Boden kommen aus ScenePaint (:core) —
 * die Kulisse ist Daten, kein Zeichencode. Welche Himmelsstufe zu einem
 * Score gehört, rechnet weiterhin SkinPaint.skyStage; der Zähler läuft im
 * Umlauf, nach der Nacht geht es zurück Richtung Tag.
 */

private fun twistBannerText(context: Context, twist: TimingGame.Twist): String =
    context.getString(
        when (twist) {
            TimingGame.Twist.PULSE -> R.string.banner_twist_pulse
            TimingGame.Twist.DRIFT -> R.string.banner_twist_drift
            TimingGame.Twist.GHOST -> R.string.banner_twist_ghost
            TimingGame.Twist.FAKE -> R.string.banner_twist_fake
            TimingGame.Twist.CHAIN -> R.string.banner_twist_chain
        }
    )

/** Nicht-Compose-Zeitgeber für das Twist-Banner. */
private class BannerState {
    var timeLeft = 0f

    /** Priorität des laufenden Banners — höher gewinnt, gleich überschreibt. */
    var priority = 0

    /** Zuletzt gesehene Himmels-Stufe (score / 5), für Stufen-Feedback. */
    var lastStage = 0

    /** Wurde der Rekord in diesem Lauf schon live gefeiert? */
    var recordCelebrated = false
}

/** Dauer der Freischalt-Zelebration (goldener Ring + Schimmer). */
private const val CELEBRATE_SECONDS = 1.1f

/**
 * Mario-Tod: Nach dem Todes-Freeze hüpft der Vogel mit dieser
 * Anfangsgeschwindigkeit nach oben und fällt dann mit der Gravitation
 * unten aus dem Bild — beides in Bildhöhen pro Sekunde(²).
 */
private const val DEATH_HOP_SPEED = 1.6f
private const val DEATH_GRAVITY = 6f

/**
 * Während des Hüpfers dreht sich der Vogel um 180° auf den Rücken und
 * fällt kopfüber — die Drehung ist am Scheitelpunkt (~0,27s) fertig.
 */
private const val DEATH_FLIP_SECONDS = 0.3f

/** Nicht-Compose-Zustand des laufenden Versuchs. */
private class RunState {
    /** Tag, dem der Lauf zugerechnet wird (fixiert beim Start). */
    var epochDay = 0L

    /** Kalender des Laufs — Monat und Jahr gehören zu [epochDay]. */
    var month = 6
    var year = 0

    /** Stunde für TAGESZEIT; wie [month] die Uhr des Geräts. */
    var hour = 12

    /** Höchste Perfekt-Serie dieses Laufs, für die Bestleistung. */
    var maxPerfect = 0

    /**
     * Uhr und Kalender einmal je Lauf ablesen. Bewusst nicht pro Frame:
     * TAGESZEIT und JAHRESZEIT ändern sich in Stunden, nicht in
     * Millisekunden — und ein Systemaufruf im Zeichenpfad wäre teuer für
     * nichts. Ein Lauf über Mitternacht behält damit sein Kleid, genau
     * wie er seinen Tag behält.
     */
    fun readClock(now: LocalDateTime = LocalDateTime.now()) {
        epochDay = now.toLocalDate().toEpochDay()
        month = now.monthValue
        year = now.year
        hour = now.hour
    }
}

/**
 * Spielprinzip "STOPP": Der Punkt kreist automatisch auf einer Bahn.
 * Ein Tap, während er in der Zielzone ist, zählt — daneben getappt oder
 * die Zone überfahren ist sofort das Ende. Präzision statt Dauerfeuer.
 * Mit steigendem Score schalten sich Twists frei (Puls, Drift, Geist,
 * Falle, Kette), die pro Zone zufällig gemischt werden.
 */
@Composable
fun TimingGameScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = remember { GameHaptics(context) }
    val store = remember { ScoreStore(context) }
    val audio = remember {
        GameAudio(context).apply {
            muted = store.soundMuted
            soundSet = store.selectedSound.id
        }
    }
    val game = remember { TimingGame() }
    val fx = remember { FxState() }
    val bannerState = remember { BannerState() }
    // Schon beim Aufbau die Uhr lesen: Der Vogel kreist auch im
    // Startbild, und TAGESZEIT soll dort nicht bis zum ersten Lauf im
    // Standard-Mittag stehen.
    val runState = remember { RunState().apply { readClock() } }
    val leaderboards = remember { Leaderboards(context as? Activity) }
    // Werbung und Kauf hängen wie die Bestenlisten an der Activity aus dem
    // LocalContext — beide sind ohne konfigurierte IDs komplett inaktiv.
    val ads = remember { AdsManager(context as? Activity, store) }
    // Der Gönner-Kauf liegt zusätzlich als Compose-Zustand vor: Er kann
    // bei offenem Skin-Menü eintreffen, und die drei neuen Skins sollen
    // dann sofort dastehen statt erst beim nächsten Öffnen.
    var patronOwned by remember { mutableStateOf(store.patronOwned) }
    // Ebenfalls als Zustand: Wer waehrend der Sitzung werbefrei kauft,
    // soll die Goenner-Zeile sofort in ihrer ehrlichen Fassung sehen
    // (siehe SkinOverlay) — nicht erst beim naechsten Start.
    var adsRemoved by remember { mutableStateOf(store.adsRemoved) }
    val billing = remember {
        BillingManager(
            activity = context as? Activity,
            store = store,
            onAdsRemoved = {
                ads.disableAfterPurchase()
                adsRemoved = true
            },
            onPatronOwned = { patronOwned = true }
        )
    }

    // Abgleich mit der Uhr. Laeuft nur, solange die App im Vordergrund
    // ist — beim naechsten Oeffnen wird ohnehin nachgeholt, was die
    // Gegenseite zwischenzeitlich abgelegt hat.
    val statsSync = remember {
        StatsSync(
            context = context,
            read = { store.syncState() },
            write = { store.applySync(it) }
        )
    }

    var frameTick by remember { mutableLongStateOf(0L) }
    var phase by remember { mutableStateOf(TimingGame.Phase.READY) }
    var score by remember { mutableIntStateOf(0) }
    var bestScore by remember { mutableIntStateOf(store.bestScore) }
    var isNewRecord by remember { mutableStateOf(false) }
    var taunt by remember { mutableStateOf("") }
    var showPerfect by remember { mutableStateOf(false) }
    var perfectPoints by remember { mutableIntStateOf(2) }
    var bannerText by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }
    // Einstellungs-Overlay hinter dem Zahnrad: Ton, Erinnerung, Hilfe,
    // Werbe-Kauf und Datenschutz.
    var showSettings by remember { mutableStateOf(false) }
    var soundOn by remember { mutableStateOf(!store.soundMuted) }
    var dailyMode by remember { mutableStateOf(false) }
    var skin by remember { mutableStateOf(store.selectedSkin) }
    // Die Kulisse ist die zweite Sammlung: kein Tagespass, kein
    // Verfallsdatum — deshalb reicht ein schlichter Zustand.
    var scene by remember { mutableStateOf(store.selectedScene) }
    // Und das Ton-Set als dritte Sammlung — dieselbe Bauart, nur hört
    // man sie, statt sie zu sehen.
    var sound by remember { mutableStateOf(store.selectedSound) }
    // Der per Spot geliehene Skin des heutigen Tages (null = keiner).
    var skinPass by remember {
        mutableStateOf(store.skinPassFor(LocalDate.now().toEpochDay()))
    }
    var showSkins by remember { mutableStateOf(false) }
    // Statistik-Seite: Zahlen und Ziele werden beim Öffnen einmal
    // gerechnet und festgehalten. Pro Frame nachzurechnen wäre für eine
    // Seite, die stillsteht, solange sie offen ist, reine Verschwendung.
    var showStats by remember { mutableStateOf(false) }
    var statsSnapshot by remember { mutableStateOf(store.stats()) }
    var statsGoals by remember { mutableStateOf(emptyList<Goal>()) }
    // Das nächstliegende Ziel für die Zeile im Game-Over und auf dem
    // Startbildschirm — im Game-Over gefüllt in dem Moment, in dem der
    // Lauf gezählt ist, damit der Balken den gerade beendeten Lauf schon
    // enthält; für den Startbildschirm beim Eintritt in READY.
    var nextGoal by remember { mutableStateOf<Goal?>(null) }
    // Versteckte Diagnose-Zeile (langer Druck auf den Titel).
    var showDiagnostics by remember { mutableStateOf(false) }
    var skinUnlockedThisRun by remember { mutableStateOf(false) }
    var newMedalThisRun by remember { mutableStateOf(false) }
    var dailyBestToday by remember {
        mutableIntStateOf(store.dailyBestFor(LocalDate.now().toEpochDay()))
    }
    var dailyStreak by remember {
        mutableIntStateOf(store.dailyStreakPreviewFor(LocalDate.now().toEpochDay()))
    }
    var reminderOn by remember { mutableStateOf(store.reminderEnabled) }

    /**
     * Frischt den Tagespass auf und holt eine nicht mehr gedeckte Auswahl
     * zurück auf KLASSIK. Nötig, weil der Pass um Mitternacht verfällt und
     * eine Sitzung diesen Wechsel überleben kann — sonst liefe der
     * geliehene Skin still weiter, bis in die Score-Card hinein.
     */
    fun refreshSkinPass(today: Long) {
        val pass = store.skinPassFor(today)
        skinPass = pass
        if (!skin.isAvailable(store.stats(), pass)) {
            skin = DotSkin.KLASSIK
            store.selectedSkin = DotSkin.KLASSIK
        }
    }

    // Ab Android 13 braucht die Erinnerung die Notification-Permission —
    // erst nach erteilter Erlaubnis wird der Schalter wirklich aktiv.
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            reminderOn = true
            store.reminderEnabled = true
            DailyReminder.schedule(context)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audio.release()
            billing.release()
        }
    }

    // Der Abgleich haengt am Lebenszyklus statt an der Composition: Beim
    // Zurueckkehren in die App soll er neu ziehen, und im Hintergrund
    // soll kein Listener offen bleiben.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> statsSync.start()
                Lifecycle.Event.ON_STOP -> statsSync.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            statsSync.stop()
        }
    }

    // Play-Games-Sign-in (No-op, solange keine IDs konfiguriert sind).
    LaunchedEffect(Unit) { leaderboards.connect() }

    // Beim Start prüfen, ob die gespeicherte Auswahl heute noch gilt.
    LaunchedEffect(Unit) { refreshSkinPass(LocalDate.now().toEpochDay()) }

    // Die Ziel-Zeile des Startbildschirms wird beim Eintritt in READY
    // gerechnet und nicht pro Frame: Sie ändert sich nur durch einen Lauf,
    // und genau danach steht sie hier wieder an. Der Kalender wird dabei
    // frisch abgelesen — daran hängt, ob ein Saison-Ziel überhaupt gilt.
    LaunchedEffect(phase) {
        if (phase == TimingGame.Phase.READY) {
            val now = LocalDateTime.now()
            nextGoal = Progress.nextGoal(
                stats = store.stats().toSkinStats(),
                month = now.monthValue,
                seasonDays = store.seasonDaysFor(now.monthValue, now.year)
            )
        }
    }

    // Werbung und Kauf hochfahren — beides No-op ohne AdMob-IDs.
    LaunchedEffect(Unit) {
        ads.start()
        billing.connect()
    }

    // Falls die Erinnerung aktiv ist: Planung idempotent auffrischen
    // (übersteht App-Updates und gelöschte WorkManager-Jobs).
    LaunchedEffect(Unit) {
        if (store.reminderEnabled) DailyReminder.schedule(context)
    }

    // Vor jedem Lauf-Start: Tag fixieren und die Zufallsquelle passend zum
    // Modus setzen — die Daily bekommt den Tages-Seed, damit jeder Versuch
    // des Tages dieselbe Zonen-Abfolge spielt.
    fun prepareRun() {
        // Tag, Monat, Jahr und Stunde kommen aus derselben Ablesung —
        // sonst könnte ein Lauf um Mitternacht seinen Tag aus dem einen
        // und seinen Monat aus dem anderen Datum bekommen.
        runState.readClock()
        val today = runState.epochDay
        // Jeder Lauf-Start ist auch der Moment, den Tagespass nachzuziehen.
        refreshSkinPass(today)
        game.reseed(if (dailyMode) DailyChallenge.seedFor(today) else null)
    }

    // Banner mit Priorität: Ein wichtigeres Banner (Twist-Ankündigung)
    // wird nicht von einem beiläufigen ("NOCH EINE!") überschrieben.
    fun showBanner(text: String, seconds: Float, priority: Int) {
        if (bannerState.timeLeft > 0f && bannerState.priority > priority) return
        bannerText = text
        bannerState.timeLeft = seconds
        bannerState.priority = priority
    }

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
                fx.celebrateTime = (fx.celebrateTime - dt).coerceAtLeast(0f)
                if (fx.deathTime >= 0f) fx.deathTime += dt
                bannerState.timeLeft = (bannerState.timeLeft - dt).coerceAtLeast(0f)
                if (bannerState.timeLeft <= 0f && bannerText.isNotEmpty()) {
                    bannerText = ""
                }

                var twistUnlockedThisFrame = false
                events.forEach { event ->
                    when (event) {
                        is TimingGame.GameEvent.Started -> {
                            // Auch beim Sofort-Neustart aus dem Game-Over:
                            // Banner, Stufen-Zähler und Rekord-Feier auf Anfang.
                            bannerState.lastStage = 0
                            bannerState.recordCelebrated = false
                            bannerState.timeLeft = 0f
                            bannerText = ""
                            runState.maxPerfect = 0
                            skinUnlockedThisRun = false
                            newMedalThisRun = false
                            fx.deathTime = -1f
                            audio.start()
                        }
                        is TimingGame.GameEvent.Hit -> {
                            haptics.score()
                            audio.hit(game.score)
                        }
                        is TimingGame.GameEvent.PerfectHit -> {
                            haptics.perfect()
                            audio.perfect(game.perfectStreak)
                            perfectPoints = game.lastHitPoints
                            runState.maxPerfect = max(runState.maxPerfect, game.perfectStreak)
                        }
                        is TimingGame.GameEvent.ChainNext -> {
                            showBanner(context.getString(R.string.banner_chain), 1.2f, priority = 1)
                            audio.chain()
                        }
                        is TimingGame.GameEvent.TwistUnlocked -> {
                            twistUnlockedThisFrame = true
                            showBanner(twistBannerText(context, event.twist), 2.2f, priority = 2)
                            fx.celebrateTime = CELEBRATE_SECONDS
                            haptics.unlock()
                            audio.unlock()
                        }
                        is TimingGame.GameEvent.Died -> {
                            haptics.death()
                            audio.death()
                            fx.flashAlpha = 1f
                            fx.shakeTime = 0.4f
                            fx.celebrateTime = 0f
                            fx.deathTime = 0f
                            val previousBest = store.bestScore
                            newMedalThisRun = MedalTier.isUpgrade(game.score, previousBest)
                            // Gezählt wird, was VERDIENT ist: Saison zählt
                            // mit, Gönner nie — ein Kauf ist keine Leistung
                            // und darf die Feier nicht auslösen.
                            val earnedBefore = DotSkin.earnedCount(store.stats())
                            isNewRecord = store.submitRun(
                                score = game.score,
                                epochDay = runState.epochDay,
                                month = runState.month,
                                year = runState.year
                            )
                            store.submitPerfectStreak(runState.maxPerfect)
                            if (dailyMode) {
                                store.submitDailyRun(runState.epochDay, game.score)
                                dailyBestToday = store.dailyBestFor(runState.epochDay)
                                dailyStreak = store.dailyStreak
                                leaderboards.submitDaily(game.score)
                            }
                            leaderboards.submitBest(game.score)
                            skinUnlockedThisRun =
                                DotSkin.earnedCount(store.stats()) > earnedBefore
                            // Erst zählen, dann zielen: Der Balken im
                            // Game-Over zeigt den Stand NACH diesem Lauf.
                            nextGoal = Progress.nextGoal(
                                stats = store.stats().toSkinStats(),
                                month = runState.month,
                                seasonDays = store.seasonDaysFor(runState.month, runState.year)
                            )
                            taunt = pickTaunt(context, game.score, previousBest, isNewRecord)
                            bestScore = store.bestScore
                            // Jeder beendete Lauf ist ein moeglicher neuer
                            // Stand fuer die Uhr. Ohne Aenderung ist der
                            // Aufruf ein No-op.
                            statsSync.publish()
                            if (isNewRecord && !bannerState.recordCelebrated) {
                                haptics.newRecord()
                            }
                        }
                        is TimingGame.GameEvent.Settled -> {
                            haptics.thud()
                            // Der Rekord-Jingle lief meist schon live im Lauf;
                            // sonst (z. B. allererster Lauf) kommt er jetzt.
                            if (isNewRecord && !bannerState.recordCelebrated) {
                                audio.newRecord()
                            } else {
                                audio.thud()
                            }
                            // Tot ist tot: Jedes Game-Over ist endgültig, also
                            // darf hier auch ein Interstitial kommen — wie oft,
                            // entscheidet allein das Gate. Die Daily bleibt
                            // ausgenommen, sie ist der ruhige Tageslauf.
                            if (!dailyMode) {
                                (context as? Activity)?.let { ads.onGameOver(it) }
                            }
                        }
                        else -> Unit
                    }
                }

                // Rekord live feiern: In dem Moment, in dem der Lauf den
                // alten Bestwert überholt — nicht erst beim Tod.
                if (game.phase == TimingGame.Phase.RUNNING &&
                    !bannerState.recordCelebrated &&
                    bestScore > 0 && game.score > bestScore
                ) {
                    bannerState.recordCelebrated = true
                    showBanner(context.getString(R.string.banner_record), 2.2f, priority = 2)
                    fx.celebrateTime = CELEBRATE_SECONDS
                    haptics.newRecord()
                    audio.newRecord()
                }

                // Stufen-Feedback: jede 5er-Stufe färbt den Himmel um — das
                // wird gefeiert, sofern nicht ohnehin gerade ein Twist-Banner
                // die große Bühne bekommt.
                val stage = game.score / 5
                if (game.phase == TimingGame.Phase.RUNNING && stage > bannerState.lastStage) {
                    bannerState.lastStage = stage
                    if (!twistUnlockedThisFrame) {
                        showBanner(context.getString(R.string.banner_stage), 1.6f, priority = 1)
                        fx.celebrateTime = CELEBRATE_SECONDS
                        haptics.unlock()
                        audio.unlock()
                    }
                }
                if (game.phase == TimingGame.Phase.READY) {
                    bannerState.lastStage = 0
                }

                phase = game.phase
                score = game.score
                showPerfect = game.lastHitPerfect && game.timeSinceHit < 0.6f &&
                    game.phase == TimingGame.Phase.RUNNING
                frameTick++
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    // Ein Tap in READY/OVER startet gleich einen Lauf —
                    // vorher Seed und Tag für den aktuellen Modus setzen.
                    if (game.phase == TimingGame.Phase.READY ||
                        game.phase == TimingGame.Phase.OVER
                    ) {
                        prepareRun()
                    }
                    game.tap()
                })
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            frameTick // Frame-Abhängigkeit: erzwingt Neuzeichnen pro Tick.
            // Stunde und Monat kommen aus dem Lauf-Zustand, nicht frisch
            // von der Uhr — sie werden je Lauf einmal abgelesen.
            drawTimingWorld(game, fx, skin, scene, runState.hour, runState.month)
        }

        // Positionen relativ zur Bildhöhe: Die Bahn endet spätestens bei
        // 72% der Höhe, der Perfekt-Text sitzt knapp darunter — auf jedem
        // Display, statt auf festen dp-Werten.
        if (showPerfect) {
            Text(
                text = stringResource(R.string.perfect_plus, perfectPoints),
                style = ScoreShadowStyle,
                fontSize = 28.sp,
                color = Color(0xFFFFE95E),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = maxHeight * 0.74f)
            )
        }

        when (phase) {
            TimingGame.Phase.READY -> ReadyOverlay(
                bestScore = bestScore,
                hint = stringResource(R.string.ready_hint),
                dailyStreak = dailyStreak,
                goal = nextGoal,
                onDaily = {
                    dailyMode = true
                    prepareRun()
                    game.tap()
                },
                onSkins = {
                    // Vor dem Öffnen nachziehen: Der Startscreen kann seit
                    // dem letzten Lauf einen Tageswechsel gesehen haben.
                    refreshSkinPass(LocalDate.now().toEpochDay())
                    showSkins = true
                },
                onStats = {
                    // Kalender frisch ablesen: Der Startscreen kann seit
                    // dem letzten Lauf einen Monatswechsel gesehen haben,
                    // und daran hängt, ob ein Saison-Ziel gilt.
                    val now = LocalDateTime.now()
                    statsSnapshot = store.stats()
                    statsGoals = Progress.nextGoals(
                        stats = statsSnapshot.toSkinStats(),
                        month = now.monthValue,
                        seasonDays = store.seasonDaysFor(now.monthValue, now.year)
                    )
                    showStats = true
                },
                onSettings = { showSettings = true },
                diagnostics = if (showDiagnostics) {
                    "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "WERBUNG: ${ads.status}\n" +
                        "KAUF: ${billing.status}"
                } else {
                    null
                },
                onToggleDiagnostics = { showDiagnostics = !showDiagnostics }
            )
            TimingGame.Phase.RUNNING, TimingGame.Phase.DYING ->
                ScoreHud(
                    score = score,
                    daily = dailyMode,
                    banner = if (phase == TimingGame.Phase.RUNNING) bannerText else ""
                )
            TimingGame.Phase.OVER -> GameOverOverlay(
                score = score,
                bestScore = bestScore,
                isNewRecord = isNewRecord,
                taunt = taunt,
                daily = dailyMode,
                dailyBest = dailyBestToday,
                dailyStreak = dailyStreak,
                skinUnlocked = skinUnlockedThisRun,
                newMedal = newMedalThisRun,
                goal = nextGoal,
                onShare = {
                    ScoreCard.share(
                        context = context,
                        score = score,
                        bestScore = bestScore,
                        isNewRecord = isNewRecord,
                        skin = skin,
                        scene = scene,
                        daily = dailyMode,
                        dailyStreak = dailyStreak,
                        // Rahmen und Beiname hängen am Gesamtstand, nicht
                        // am Lauf — der Stand wird erst beim Tippen auf
                        // TEILEN geholt, damit er den eben gezählten Lauf
                        // sicher enthält.
                        stats = store.stats().toSkinStats()
                    )
                },
                onMenu = {
                    dailyMode = false
                    game.reset()
                    // Auch die Effekte zurücksetzen — sonst läuft die
                    // Sturz-Animation weiter und der Vogel fehlt im
                    // Startbild, obwohl er dort wieder kreisen soll.
                    fx.reset()
                    bannerState.timeLeft = 0f
                    bannerText = ""
                    bannerState.lastStage = 0
                    bannerState.recordCelebrated = false
                },
                onHelp = { showHelp = true }
            )
        }

        if (showHelp) {
            HelpOverlay(onClose = { showHelp = false })
        }

        if (showStats) {
            StatsOverlay(
                stats = statsSnapshot,
                goals = statsGoals,
                onClose = { showStats = false },
                leaderboardAvailable = leaderboards.available,
                onLeaderboard = { leaderboards.show() }
            )
        }

        if (showSettings) {
            SettingsOverlay(
                soundOn = soundOn,
                onToggleSound = {
                    soundOn = !soundOn
                    store.soundMuted = !soundOn
                    audio.muted = !soundOn
                },
                reminderOn = reminderOn,
                onToggleReminder = {
                    when {
                        reminderOn -> {
                            reminderOn = false
                            store.reminderEnabled = false
                            DailyReminder.cancel(context)
                        }
                        Build.VERSION.SDK_INT >= 33 && DailyReminder.needsPermission(context) ->
                            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else -> {
                            reminderOn = true
                            store.reminderEnabled = true
                            DailyReminder.schedule(context)
                        }
                    }
                },
                // Die Hilfe bleibt ein eigenes Overlay: Sie ist keine
                // Einstellung, sie wird nur von hier aus geöffnet.
                onHelp = {
                    showSettings = false
                    showHelp = true
                },
                onClose = { showSettings = false },
                // Kein Preis von Google = kein Angebot. Ein Knopf, der
                // ins Leere greift, ist schlimmer als gar keiner.
                removeAdsPrice = if (ads.enabled) billing.priceLabel else null,
                onRemoveAds = { (context as? Activity)?.let { billing.purchase(it) } },
                privacyVisible = ads.enabled && ads.privacyOptionsRequired,
                onPrivacy = { (context as? Activity)?.let { ads.showPrivacyOptions(it) } }
            )
        }

        if (showSkins) {
            SkinOverlay(
                // patronOwned wird hier bewusst noch einmal gelesen: Erst
                // dieser Zugriff lässt die Liste nach dem Kauf neu zeichnen.
                stats = store.stats().copy(patronOwned = patronOwned),
                // Wer schon werbefrei ist, liest am Goenner-Angebot, was
                // fuer ihn wirklich neu ist — sonst zahlt er die
                // Werbefreiheit ein zweites Mal, ohne es zu merken.
                adsAlreadyRemoved = adsRemoved,
                selected = skin,
                selectedSound = sound,
                onSelectSound = {
                    sound = it
                    store.selectedSound = it
                    audio.soundSet = it.id
                    // Die Hörprobe ist der ganze Sinn der Zeile: Ohne sie
                    // waehlt man einen Klang nach seinem Namen.
                    audio.preview(it.id)
                    // Wie Skin- und Kulissen-Wahl eine Entscheidung: Sie
                    // muss sofort raus, sonst ueberschreibt sie beim
                    // naechsten Abgleich die juengere Wahl der Gegenseite.
                    statsSync.publish()
                },
                selectedScene = scene,
                onSelectScene = {
                    scene = it
                    store.selectedScene = it
                    // Wie die Skin-Wahl eine Entscheidung: Sie muss sofort
                    // raus, sonst ueberschreibt sie beim naechsten
                    // Abgleich die juengere Wahl auf der Gegenseite.
                    statsSync.publish()
                    showSkins = false
                },
                onSelect = {
                    skin = it
                    store.selectedSkin = it
                    // Die Skin-Wahl ist der einzige Wert, bei dem "neuer
                    // gewinnt" gilt — sie muss deshalb sofort raus, sonst
                    // ueberschreibt sie beim naechsten Abgleich die
                    // juengere Wahl auf der Uhr.
                    statsSync.publish()
                    showSkins = false
                },
                onClose = { showSkins = false },
                skinPass = skinPass,
                adOfferReady = ads.enabled && ads.rewardedReady,
                onWatchAdFor = { wanted ->
                    // Erst der bestätigte Spot, dann der Pass: Bei Abbruch
                    // passiert nichts, das Overlay bleibt stehen.
                    (context as? Activity)?.let { activity ->
                        ads.showRewarded(activity) {
                            val today = LocalDate.now().toEpochDay()
                            store.grantSkinPass(today, wanted)
                            skinPass = wanted
                            skin = wanted
                            store.selectedSkin = wanted
                        }
                    }
                },
                // Kein Preis von Google = kein Angebot, wie bei "Werbung
                // entfernen". Wer das Paket hat, sieht statt des Preises
                // ein Danke.
                patronPrice = billing.patronPriceLabel,
                onPatron = { (context as? Activity)?.let { billing.purchasePatron(it) } }
            )
        }
    }
}

// ===== Welt-Rendering =====

private fun DrawScope.drawTimingWorld(
    game: TimingGame,
    fx: FxState,
    skin: DotSkin,
    scene: DotScene,
    hour: Int,
    month: Int
) {
    val h = size.height
    val w = size.width
    val cell = floor(h / 220f).coerceAtLeast(2f)
    val kulisse = scene.scene

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
        // Himmel färbt sich mit jeder 5er-Stufe weiter Richtung Nacht —
        // welche sieben Töne das sind, sagt die Kulisse.
        val sky = Color(kulisse.sky[SkinPaint.skyStage(game.score)])
        drawRect(color = sky, topLeft = Offset(-40f, -40f), size = Size(w + 80f, h + 80f))

        // Langsam driftende Wolken. Im Vakuum gibt es keine — dann bleibt
        // der Himmel leer, statt graue Attrappen zu zeigen.
        kulisse.cloud?.let { cloud ->
            val drift = game.elapsed * h * 0.01f
            drawCloud(w * 0.1f - drift % (w * 1.4f), h * 0.16f, cell, Color(cloud))
            drawCloud(w * 0.75f - drift % (w * 1.4f), h * 0.24f, cell, Color(cloud))
        }

        drawScenery(game, cell, kulisse.props)
        kulisse.ground?.let { drawGroundStrip(cell, it) }

        // Kreisbahn mit Zielzone, ggf. Fallen-Zone und Punkt. Sie zieht
        // ihre Farben bewusst NICHT aus der Kulisse: Worauf getippt wird,
        // sieht überall gleich aus — sonst wäre die Kulisse ein Vorteil.
        val cx = w / 2f
        val cy = h * 0.44f
        val radius = min(w * 0.36f, h * 0.28f)
        drawTrack(game, cx, cy, radius, cell)
        if (game.isDotVisible) {
            drawTimingDot(game, fx, cx, cy, radius, skin, hour, month)
        }
        if (fx.celebrateTime > 0f) {
            drawUnlockBurst(fx.celebrateTime, cx, cy, radius, cell)
        }
    }

    // Weißer Blitz beim Aufprall
    if (fx.flashAlpha > 0f) {
        drawRect(color = Color.White.copy(alpha = fx.flashAlpha.coerceAtMost(1f)))
    }
}

/**
 * Requisiten vor dem Boden. Die Szenerie driftet wie die Wolken nach
 * links — nur schneller, weil sie näher am Betrachter ist (Parallaxe) —
 * und wickelt rechts wieder ein. Dazu wiegt ein leichter Wind sie, pro
 * Requisite phasenversetzt.
 *
 * Welche Requisite an welchem Platz steht, sagt die Kulisse: Die Liste
 * wird zyklisch abgelaufen, genau wie der Bestand bisher k % 4 benutzt
 * hat. Der Akzent (Blütenfarbe, Fensterfarbe) wechselt eine Ebene
 * langsamer, also erst mit der nächsten Wiederholung.
 */
private fun DrawScope.drawScenery(game: TimingGame, cell: Float, props: List<Prop>) {
    val h = size.height
    val w = size.width
    // Basis knapp unter der Grasnarben-Oberkante — der Boden-Streifen
    // wird danach gezeichnet und verdeckt die Wurzeln sauber.
    val groundY = ScenePaint.groundY(h) + cell * 2f

    val drift = game.elapsed * h * 0.016f
    val spacing = w * 0.26f
    val count = (w / spacing).toInt() + 3
    val total = spacing * count
    for (k in 0 until count) {
        val x = ((k * spacing - drift) % total + total) % total - spacing
        val wind = sin(game.elapsed * 1.4f + k * 1.7f) * cell * 0.6f
        val prop = props[k % props.size]
        val accent = if (prop.accents.isEmpty()) {
            Color.Transparent
        } else {
            Color(prop.accents[(k / props.size) % prop.accents.size])
        }
        drawProp(prop, x, groundY, h * prop.size, wind * prop.sway, cell, accent)
    }
}

/** Verteilt eine Requisite auf die Zeichnung ihrer Form. */
private fun DrawScope.drawProp(
    prop: Prop,
    cx: Float,
    groundY: Float,
    s: Float,
    sway: Float,
    cell: Float,
    accent: Color
) {
    val dark = Color(prop.dark)
    val body = Color(prop.body)
    val light = Color(prop.light)
    val stem = Color(prop.stem)
    val stemShade = Color(prop.stemShade)
    when (prop.shape) {
        PropShape.BAUM -> drawPixelTree(cx, groundY, s, sway, cell, dark, body, light, stem, stemShade)
        PropShape.BLUME -> drawPixelFlower(cx, groundY, s, sway, cell, dark, body, light, accent)
        PropShape.STRAUCH -> drawPixelBush(cx, groundY, s, sway, cell, dark, body, light)
        PropShape.KAKTUS -> drawPixelCactus(cx, groundY, s, sway, cell, dark, body, light, accent)
        PropShape.WELLE -> drawPixelWave(cx, groundY, s, sway, cell, dark, body, light, accent)
        PropShape.NADELBAUM ->
            drawPixelFir(cx, groundY, s, sway, cell, dark, body, light, stem, stemShade)
        PropShape.HOCHHAUS -> drawPixelTower(cx, groundY, s, cell, dark, body, light, accent)
        PropShape.FELS -> drawPixelRock(cx, groundY, s, sway, cell, dark, body, light)
    }
}

/**
 * Formen mit sich überlappenden Teilen (Kaktus, Hochhaus) brauchen zwei
 * Durchgänge: erst alle Konturen, dann alle Füllungen. Sonst legt die
 * Kontur des einen Blocks einen Balken über die Füllung des anderen.
 */
private fun DrawScope.drawOutlinedBlocks(cell: Float, blocks: List<Pair<Rect, Color>>) {
    blocks.forEach { (r, _) ->
        drawRect(
            color = OutlineColor,
            topLeft = Offset(r.x - cell, r.y - cell),
            size = Size(r.w + cell * 2f, r.h + cell * 2f)
        )
    }
    blocks.forEach { (r, color) ->
        drawRect(color = color, topLeft = Offset(r.x, r.y), size = Size(r.w, r.h))
    }
}

/** Rechteck in Weltkoordinaten — nur als Bündel für [drawOutlinedBlocks]. */
private data class Rect(val x: Float, val y: Float, val w: Float, val h: Float)

/** Pixel-Baum: Stamm mit Schattenseite, dreistufige Krone im Wind. */
private fun DrawScope.drawPixelTree(
    cx: Float,
    groundY: Float,
    s: Float,
    sway: Float,
    cell: Float,
    dark: Color,
    body: Color,
    light: Color,
    stem: Color,
    stemShade: Color
) {
    val trunkW = s * 0.30f
    val trunkH = s * 0.60f
    drawRect(
        color = OutlineColor,
        topLeft = Offset(cx - trunkW / 2f - cell, groundY - trunkH - cell),
        size = Size(trunkW + cell * 2f, trunkH + cell)
    )
    drawRect(
        color = stem,
        topLeft = Offset(cx - trunkW / 2f, groundY - trunkH),
        size = Size(trunkW, trunkH)
    )
    drawRect(
        color = stemShade,
        topLeft = Offset(cx, groundY - trunkH),
        size = Size(trunkW / 2f, trunkH)
    )

    // Krone: von unten (breit, dunkel) nach oben (schmal, hell); der Wind
    // greift oben stärker.
    val layers = listOf(
        Triple(s * 1.6f, s * 0.45f, dark),
        Triple(s * 1.2f, s * 0.40f, body),
        Triple(s * 0.7f, s * 0.35f, light)
    )
    var layerTop = groundY - trunkH
    layers.forEachIndexed { i, (lw, lh, color) ->
        layerTop -= lh
        val lx = cx + sway * (0.35f + 0.35f * i)
        drawRect(
            color = OutlineColor,
            topLeft = Offset(lx - lw / 2f - cell, layerTop - cell),
            size = Size(lw + cell * 2f, lh + cell * 2f)
        )
        drawRect(
            color = color,
            topLeft = Offset(lx - lw / 2f, layerTop),
            size = Size(lw, lh)
        )
    }
}

/**
 * Pixel-Strauch: runde Beeren-Silhouette statt Torten-Stufen — der Bauch
 * in der Mitte ist die breiteste Lage, oben sitzt eine helle Kuppe, und
 * zwei Licht-Tupfer geben der Fläche Textur.
 */
private fun DrawScope.drawPixelBush(
    cx: Float,
    groundY: Float,
    s: Float,
    sway: Float,
    cell: Float,
    dark: Color,
    body: Color,
    light: Color
) {
    val layers = listOf(
        Triple(s * 2.1f, s * 0.55f, dark), // Sockel
        Triple(s * 2.7f, s * 0.70f, body), // Bauch — am breitesten
        Triple(s * 1.5f, s * 0.55f, light) // Kuppe
    )
    var layerTop = groundY
    layers.forEachIndexed { i, (lw, lh, color) ->
        layerTop -= lh
        val lx = cx + sway * (0.2f + 0.3f * i)
        drawRect(
            color = OutlineColor,
            topLeft = Offset(lx - lw / 2f - cell, layerTop - cell),
            size = Size(lw + cell * 2f, lh + cell * 2f)
        )
        drawRect(
            color = color,
            topLeft = Offset(lx - lw / 2f, layerTop),
            size = Size(lw, lh)
        )
    }

    // Licht-Tupfer auf dem Bauch
    val u = cell * 1.5f
    drawRect(
        color = light,
        topLeft = Offset(cx - s * 1.0f + sway * 0.4f, groundY - s * 1.05f),
        size = Size(u * 2f, u)
    )
    drawRect(
        color = light,
        topLeft = Offset(cx + s * 0.35f + sway * 0.4f, groundY - s * 0.8f),
        size = Size(u, u)
    )
}

/**
 * Pixel-Blume: Stiel mit Blättern und großer Blüte (vier Blütenblätter
 * um eine helle Mitte). Die Blüte wiegt im Wind, der Stiel bleibt unten
 * verwurzelt.
 */
private fun DrawScope.drawPixelFlower(
    cx: Float,
    groundY: Float,
    s: Float,
    sway: Float,
    cell: Float,
    dark: Color,
    body: Color,
    light: Color,
    petal: Color
) {
    val stemH = s * 1.15f
    val bx = cx + sway
    val by = groundY - stemH

    // Stiel (mit Outline), oben leicht zur Blüte versetzt gezeichnet
    drawRect(
        color = OutlineColor,
        topLeft = Offset(cx - cell * 1.5f, by),
        size = Size(cell * 3f, stemH)
    )
    drawRect(
        color = dark,
        topLeft = Offset(cx - cell * 0.75f, by),
        size = Size(cell * 1.5f, stemH)
    )

    // Zwei Blätter auf halber Höhe
    val leafY = groundY - stemH * 0.45f
    drawRect(
        color = OutlineColor,
        topLeft = Offset(cx - s * 0.6f - cell, leafY - cell),
        size = Size(s * 0.6f + cell * 2f, cell * 3f)
    )
    drawRect(
        color = body,
        topLeft = Offset(cx - s * 0.6f, leafY),
        size = Size(s * 0.6f, cell * 1.5f)
    )
    drawRect(
        color = OutlineColor,
        topLeft = Offset(cx - cell, leafY + cell * 3f),
        size = Size(s * 0.55f + cell * 2f, cell * 3f)
    )
    drawRect(
        color = body,
        topLeft = Offset(cx, leafY + cell * 4f),
        size = Size(s * 0.55f, cell * 1.5f)
    )

    // Blüte: Plus aus vier Blütenblättern um die helle Mitte
    val u = s * 0.38f
    fun block(x: Float, y: Float, color: Color) {
        drawRect(
            color = OutlineColor,
            topLeft = Offset(x - cell, y - cell),
            size = Size(u + cell * 2f, u + cell * 2f)
        )
        drawRect(color = color, topLeft = Offset(x, y), size = Size(u, u))
    }
    block(bx - u / 2f, by - u * 1.5f, petal)          // oben
    block(bx - u * 1.5f, by - u / 2f, petal)          // links
    block(bx + u / 2f, by - u / 2f, petal)            // rechts
    block(bx - u / 2f, by + u / 2f, petal)            // unten
    block(bx - u / 2f, by - u / 2f, light)            // Mitte
}

/**
 * Kaktus: Säule mit zwei versetzten Armen und einer Blüte obendrauf. Die
 * Arme sitzen auf verschiedenen Höhen — zwei gleich hohe Arme sähen aus
 * wie ein Zeichen, nicht wie eine Pflanze.
 */
private fun DrawScope.drawPixelCactus(
    cx: Float,
    groundY: Float,
    s: Float,
    sway: Float,
    cell: Float,
    dark: Color,
    body: Color,
    light: Color,
    bloom: Color
) {
    val stemW = s * 0.34f
    val stemH = s * 1.5f
    val armW = s * 0.20f
    val leftY = groundY - stemH * 0.55f
    val rightY = groundY - stemH * 0.78f
    val lean = sway * 0.4f

    drawOutlinedBlocks(
        cell,
        listOf(
            Rect(cx - stemW / 2f, groundY - stemH, stemW, stemH) to body,
            Rect(cx - s * 0.75f + lean, leftY, s * 0.75f, armW) to body,
            Rect(cx - s * 0.75f + lean, leftY - s * 0.45f, armW, s * 0.45f + armW) to body,
            Rect(cx + lean, rightY, s * 0.75f, armW) to body,
            Rect(cx + s * 0.75f - armW + lean, rightY - s * 0.38f, armW, s * 0.38f + armW) to body
        )
    )

    // Schattenseite rechts, Lichtkante links — wie beim Vogel.
    drawRect(
        color = dark,
        topLeft = Offset(cx + stemW * 0.12f, groundY - stemH),
        size = Size(stemW * 0.38f, stemH)
    )
    drawRect(
        color = light,
        topLeft = Offset(cx - stemW / 2f, groundY - stemH),
        size = Size(stemW * 0.26f, stemH * 0.92f)
    )

    val fw = s * 0.26f
    drawRect(
        color = OutlineColor,
        topLeft = Offset(cx - fw / 2f - cell, groundY - stemH - fw - cell),
        size = Size(fw + cell * 2f, fw + cell * 2f)
    )
    drawRect(
        color = bloom,
        topLeft = Offset(cx - fw / 2f, groundY - stemH - fw),
        size = Size(fw, fw)
    )
}

/**
 * Welle: flacher, breiter Stapel mit Schaumtupfern. Bewusst breiter als
 * hoch — eine Welle, die wie ein Busch stünde, läse sich als Pflanze.
 */
private fun DrawScope.drawPixelWave(
    cx: Float,
    groundY: Float,
    s: Float,
    sway: Float,
    cell: Float,
    dark: Color,
    body: Color,
    light: Color,
    foam: Color
) {
    val layers = listOf(
        Triple(s * 3.0f, s * 0.30f, dark),
        Triple(s * 2.2f, s * 0.26f, body),
        Triple(s * 1.2f, s * 0.22f, light)
    )
    var layerTop = groundY
    var lx = cx
    layers.forEachIndexed { i, (lw, lh, color) ->
        layerTop -= lh
        lx = cx + sway * (0.3f + 0.4f * i)
        drawRect(
            color = OutlineColor,
            topLeft = Offset(lx - lw / 2f - cell, layerTop - cell),
            size = Size(lw + cell * 2f, lh + cell * 2f)
        )
        drawRect(color = color, topLeft = Offset(lx - lw / 2f, layerTop), size = Size(lw, lh))
    }

    val u = cell * 1.5f
    drawRect(color = foam, topLeft = Offset(lx - s * 0.5f, layerTop), size = Size(u * 2f, u))
    drawRect(color = foam, topLeft = Offset(lx + s * 0.2f, layerTop + u), size = Size(u, u))
}

/** Nadelbaum: schmaler Stamm, drei spitze Lagen, helle Spitze obendrauf. */
private fun DrawScope.drawPixelFir(
    cx: Float,
    groundY: Float,
    s: Float,
    sway: Float,
    cell: Float,
    dark: Color,
    body: Color,
    light: Color,
    stem: Color,
    stemShade: Color
) {
    val trunkW = s * 0.22f
    val trunkH = s * 0.30f
    drawRect(
        color = OutlineColor,
        topLeft = Offset(cx - trunkW / 2f - cell, groundY - trunkH - cell),
        size = Size(trunkW + cell * 2f, trunkH + cell)
    )
    drawRect(
        color = stem,
        topLeft = Offset(cx - trunkW / 2f, groundY - trunkH),
        size = Size(trunkW, trunkH)
    )
    drawRect(
        color = stemShade,
        topLeft = Offset(cx, groundY - trunkH),
        size = Size(trunkW / 2f, trunkH)
    )

    val layers = listOf(
        Triple(s * 1.50f, s * 0.42f, dark),
        Triple(s * 1.05f, s * 0.38f, body),
        Triple(s * 0.60f, s * 0.34f, body)
    )
    var layerTop = groundY - trunkH
    var lx = cx
    layers.forEachIndexed { i, (lw, lh, color) ->
        layerTop -= lh
        lx = cx + sway * (0.3f + 0.3f * i)
        drawRect(
            color = OutlineColor,
            topLeft = Offset(lx - lw / 2f - cell, layerTop - cell),
            size = Size(lw + cell * 2f, lh + cell * 2f)
        )
        drawRect(color = color, topLeft = Offset(lx - lw / 2f, layerTop), size = Size(lw, lh))
    }

    val tw = s * 0.24f
    val th = s * 0.26f
    lx = cx + sway * 1.2f
    drawRect(
        color = OutlineColor,
        topLeft = Offset(lx - tw / 2f - cell, layerTop - th - cell),
        size = Size(tw + cell * 2f, th + cell * 2f)
    )
    drawRect(color = light, topLeft = Offset(lx - tw / 2f, layerTop - th), size = Size(tw, th))
}

/**
 * Hochhaus: ein Block mit Schattenseite, heller Dachkante und einem
 * Fensterraster. Ohne Wind — ein wankendes Haus wäre ein Witz, den das
 * Spiel an dieser Stelle nicht macht.
 */
private fun DrawScope.drawPixelTower(
    cx: Float,
    groundY: Float,
    s: Float,
    cell: Float,
    dark: Color,
    body: Color,
    light: Color,
    window: Color
) {
    val w = s * 0.9f
    val hgt = s * 2.4f
    drawRect(
        color = OutlineColor,
        topLeft = Offset(cx - w / 2f - cell, groundY - hgt - cell),
        size = Size(w + cell * 2f, hgt + cell)
    )
    drawRect(color = body, topLeft = Offset(cx - w / 2f, groundY - hgt), size = Size(w, hgt))
    drawRect(color = dark, topLeft = Offset(cx, groundY - hgt), size = Size(w / 2f, hgt))
    drawRect(color = light, topLeft = Offset(cx - w / 2f, groundY - hgt), size = Size(w, s * 0.16f))

    // Fensterraster: jedes dritte Fenster bleibt dunkel, sonst sähe die
    // Fassade aus wie ein Schachbrett aus Licht.
    val uw = w * 0.22f
    val uh = s * 0.16f
    for (r in 0 until 5) {
        val fy = groundY - hgt + s * 0.34f + r * s * 0.36f
        if (fy + uh > groundY - s * 0.1f) break
        for (c in 0 until 2) {
            val fx = cx - w * 0.30f + c * w * 0.34f
            drawRect(
                color = if ((r + c) % 3 == 0) dark else window,
                topLeft = Offset(fx, fy),
                size = Size(uw, uh)
            )
        }
    }
}

/**
 * Fels: Umriss aus [ScenePaint.ROCK_PARTS], unsymmetrisch und mit
 * Lichtseite. Erst alle Konturen, dann alle Flächen — sonst schnitte die
 * Kontur eines höheren Stücks in die Fläche des darunterliegenden, und
 * der Stein bekäme Fugen, die er nicht hat.
 */
private fun DrawScope.drawPixelRock(
    cx: Float,
    groundY: Float,
    s: Float,
    sway: Float,
    cell: Float,
    dark: Color,
    body: Color,
    light: Color
) {
    val parts = ScenePaint.ROCK_PARTS
    fun left(p: RockPart) = cx + sway * (0.15f + 0.25f * p.y) + p.x * s
    fun top(p: RockPart) = groundY - (p.y + p.h) * s

    parts.forEach { p ->
        drawRect(
            color = OutlineColor,
            topLeft = Offset(left(p) - cell, top(p) - cell),
            size = Size(p.w * s + cell * 2f, p.h * s + cell * 2f)
        )
    }
    parts.forEach { p ->
        drawRect(
            color = when (p.tone) {
                0 -> dark
                1 -> body
                else -> light
            },
            topLeft = Offset(left(p), top(p)),
            size = Size(p.w * s, p.h * s)
        )
    }
}

/**
 * Bodenstreifen: Grundfläche mit dunklerem Band, darüber die Narbe aus
 * zwei Tönen. Der statische Boden unter allem — welche Farben, sagt die
 * Kulisse; wo er beginnt, sagt ScenePaint.groundY und sonst niemand.
 */
private fun DrawScope.drawGroundStrip(cell: Float, ground: Ground) {
    val h = size.height
    val w = size.width
    val groundTop = ScenePaint.groundY(h)

    drawRect(
        color = Color(ground.sand),
        topLeft = Offset(0f, groundTop),
        size = Size(w, h - groundTop)
    )
    drawRect(
        color = Color(ground.sandShade),
        topLeft = Offset(0f, groundTop + cell * 8),
        size = Size(w, cell * 2)
    )
    val toothW = cell * 5f
    drawRect(
        color = Color(ground.turfDark),
        topLeft = Offset(0f, groundTop),
        size = Size(w, cell * 5)
    )
    var x = 0f
    while (x < w) {
        drawRect(
            color = Color(ground.turfLight),
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
    // 60 statt 72 Segmente: Die einzelnen Kettenglieder bekommen sichtbaren
    // Abstand (Perlenketten-Look), statt sich zu überlappen. Die Zonen
    // bleiben durch ihre größeren Blöcke bewusst ein durchgehendes Band.
    val segments = 60
    val zoneHalf = game.effectiveZoneHalf()
    for (k in 0 until segments) {
        val a = k.toFloat() / segments * (2f * Math.PI.toFloat())
        val px = cx + cos(a) * radius
        val py = cy + sin(a) * radius

        val relativeZone = TimingGame.wrapToPi(a - game.zoneCenter)
        val inZone = abs(relativeZone) <= zoneHalf
        // Kern und Fallenbreite kommen aus der Engine, nicht aus dem
        // Renderer: Was hier leuchtet, ist exakt das Fenster, das der Tap
        // auch wertet — und die Falle misst sich wie die echte Zone.
        val coreHalf = game.perfectHalf()
        val inPerfectCore = abs(relativeZone) <= coreHalf

        val fakeHalf = game.fakeZoneHalf()
        val inFake = game.hasFakeZone &&
            abs(TimingGame.wrapToPi(a - game.fakeZoneCenter)) <= fakeHalf
        val inFakeCore = game.hasFakeZone &&
            abs(TimingGame.wrapToPi(a - game.fakeZoneCenter)) <= coreHalf

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

/**
 * Freischalt-Zelebration: ein goldener Ring aus Pixel-Blöcken, der von der
 * Bahn nach außen aufsteigt und dabei verblasst — plus kurzer Goldschimmer
 * über dem ganzen Bild direkt am Anfang.
 */
private fun DrawScope.drawUnlockBurst(
    timeLeft: Float,
    cx: Float,
    cy: Float,
    radius: Float,
    cell: Float
) {
    val progress = 1f - (timeLeft / CELEBRATE_SECONDS).coerceIn(0f, 1f)
    val fade = 1f - progress

    // Goldschimmer, nur im ersten Drittel spürbar
    val glow = (fade - 0.66f).coerceAtLeast(0f) * 0.9f
    if (glow > 0f) {
        drawRect(color = DotBody.copy(alpha = glow))
    }

    // Zwei versetzte Pixel-Ringe wandern nach außen
    val sparks = 20
    for (ring in 0 until 2) {
        val ringProgress = (progress - ring * 0.15f).coerceIn(0f, 1f)
        if (ringProgress <= 0f) continue
        val burstRadius = radius * (0.55f + ringProgress * 0.9f)
        val blockSize = cell * (3.5f - ring) * fade
        if (blockSize <= 0f) continue
        val color = (if (ring == 0) DotBody else DotShine).copy(alpha = fade)
        for (k in 0 until sparks) {
            val a = (k.toFloat() / sparks + ring * 0.025f) * (2f * Math.PI.toFloat())
            val px = cx + cos(a) * burstRadius
            val py = cy + sin(a) * burstRadius
            drawRect(
                color = color,
                topLeft = Offset(px - blockSize / 2f, py - blockSize / 2f),
                size = Size(blockSize, blockSize)
            )
        }
    }
}

private fun DrawScope.drawTimingDot(
    game: TimingGame,
    fx: FxState,
    cx: Float,
    cy: Float,
    radius: Float,
    skin: DotSkin,
    hour: Int,
    month: Int
) {
    val h = size.height
    val px = cx + cos(game.angle) * radius
    var py = cy + sin(game.angle) * radius
    val r = h * 0.026f

    // Mario-Tod: Während des Todes-Freeze bleibt der Vogel stehen, dann
    // hüpft er nach oben, dreht sich dabei auf den Rücken und fällt
    // kopfüber mit Gravitation unten aus dem Bild.
    var flip = 0f
    if (fx.deathTime >= 0f) {
        val t = fx.deathTime - TimingGame.DEATH_FREEZE_SECONDS
        if (t > 0f) {
            py += (-DEATH_HOP_SPEED * t + 0.5f * DEATH_GRAVITY * t * t) * h
            if (py - r * 2f > h) return
            flip = 180f * (t / DEATH_FLIP_SECONDS).coerceAtMost(1f)
        }
    }

    val state = SkinState(
        elapsed = game.elapsed,
        score = game.score,
        perfectStreak = game.perfectStreak,
        hour = hour,
        month = month
    )

    fun drawBird(centerX: Float, centerY: Float, alpha: Float = 1f) {
        drawPixelCircle(
            outline = OutlineColor,
            centerX = centerX,
            centerY = centerY,
            radius = r,
            alpha = alpha
        ) { col, row -> Color(skin.cell(col, row, state)) }

        val u = (r * 2f) / GRID
        fun rect(col: Float, row: Float, cols: Float, rows: Float, color: Color) {
            drawRect(
                color = color,
                topLeft = Offset(centerX - r + col * u, centerY - r + row * u),
                size = Size(cols * u, rows * u),
                alpha = alpha
            )
        }

        // Glanzpunkt und Auge folgen der sichtbaren Flugrichtung: Die
        // horizontale Geschwindigkeit ist ~ -sin(angle) * direction — zeigt
        // sie nach links, wird das Gesicht gespiegelt. Der Wechsel passiert
        // genau dort, wo der Vogel senkrecht fliegt, und fällt kaum auf.
        //
        // Auf sehr hellen Skins (Koi, Chrom) ginge das weiße Auge im
        // Körper unter — dort bekommt es zum Körper hin eine Kontur.
        // Wo der Körper von selbst genug Kontrast hat, bleibt sie weg:
        // Sie wirkte dort wie ein Kasten ums Auge. Zur Silhouette hin
        // fehlt sie immer, dort grenzt ohnehin die Kontur des Kreises an.
        val facingLeft = sin(game.angle) * game.direction > 0f
        val shine = Color(skin.shineColor(state))
        val eyeOutline = skin.needsEyeOutline
        if (facingLeft) {
            rect(GRID - 4.5f, 2.5f, 2f, 2f, shine)
            if (eyeOutline) {
                rect(5.5f, 3f, 0.5f, 4f, OutlineColor)
                rect(2f, 2.5f, 3.5f, 0.5f, OutlineColor)
                rect(2f, 7f, 3.5f, 0.5f, OutlineColor)
            }
            rect(2f, 3f, 3.5f, 4f, Color.White)
            rect(2f, 4f, 1.5f, 2f, OutlineColor)
        } else {
            rect(2.5f, 2.5f, 2f, 2f, shine)
            if (eyeOutline) {
                rect(7f, 3f, 0.5f, 4f, OutlineColor)
                rect(7.5f, 2.5f, 3.5f, 0.5f, OutlineColor)
                rect(7.5f, 7f, 3.5f, 0.5f, OutlineColor)
            }
            rect(7.5f, 3f, 3.5f, 4f, Color.White)
            rect(9.5f, 4f, 1.5f, 2f, OutlineColor)
        }
    }

    // Schweif-Skins (Tinte) lassen Nachbilder auf der Bahn zurück. Die
    // Positionen werden aus dem Winkel zurückgerechnet statt gespeichert —
    // damit sehen alle Ports identisch aus, ohne eigenen Zustand.
    if (skin.hasTrail && game.phase == TimingGame.Phase.RUNNING) {
        for (step in SkinPaint.TRAIL_STEPS downTo 1) {
            val a = game.angle - game.direction * step * SkinPaint.TRAIL_SPACING
            drawBird(
                centerX = cx + cos(a) * radius,
                centerY = cy + sin(a) * radius,
                alpha = 0.34f / step
            )
        }
    }

    if (flip > 0f) {
        rotate(degrees = flip, pivot = Offset(px, py)) { drawBird(px, py) }
    } else {
        drawBird(px, py)
    }
}
