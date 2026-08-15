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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.robinrehbein.punkt.BuildConfig
import de.robinrehbein.punkt.R
import de.robinrehbein.punkt.ads.AdsManager
import de.robinrehbein.punkt.billing.BillingManager
import de.robinrehbein.punkt.data.ScoreStore
import de.robinrehbein.punkt.game.DailyChallenge
import de.robinrehbein.punkt.game.GameAudio
import de.robinrehbein.punkt.game.GameEventChainNext
import de.robinrehbein.punkt.game.GameEventDied
import de.robinrehbein.punkt.game.GameEventHit
import de.robinrehbein.punkt.game.GameEventPerfectHit
import de.robinrehbein.punkt.game.GameEventSettled
import de.robinrehbein.punkt.game.GameEventStarted
import de.robinrehbein.punkt.game.GameEventTwistUnlocked
import de.robinrehbein.punkt.game.GameHaptics
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.Goal
import de.robinrehbein.punkt.game.Ground
import de.robinrehbein.punkt.game.MedalPaint
import de.robinrehbein.punkt.game.Progress
import de.robinrehbein.punkt.game.Prop
import de.robinrehbein.punkt.game.PropShape
import de.robinrehbein.punkt.game.RockPart
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SkinState
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.game.Twist
import de.robinrehbein.punkt.notify.DailyReminder
import de.robinrehbein.punkt.play.Leaderboards
import de.robinrehbein.punkt.share.ScoreCard
import de.robinrehbein.punkt.sync.StatsSync
import de.robinrehbein.punkt.ui.resources.Res
import de.robinrehbein.punkt.ui.resources.new_record
import de.robinrehbein.punkt.ui.resources.banner_chain
import de.robinrehbein.punkt.ui.resources.banner_record
import de.robinrehbein.punkt.ui.resources.banner_stage
import de.robinrehbein.punkt.ui.resources.banner_twist_chain
import de.robinrehbein.punkt.ui.resources.banner_twist_drift
import de.robinrehbein.punkt.ui.resources.banner_twist_fake
import de.robinrehbein.punkt.ui.resources.banner_twist_ghost
import de.robinrehbein.punkt.ui.resources.banner_twist_pulse
import de.robinrehbein.punkt.ui.resources.perfect_plus
import de.robinrehbein.punkt.ui.resources.ready_hint
import de.robinrehbein.punkt.ui.screens.rememberTaunter
import de.robinrehbein.punkt.ui.text.sceneTitle
import de.robinrehbein.punkt.ui.screens.rememberTwistBanners
import de.robinrehbein.punkt.ui.world.CELEBRATE_SECONDS
import de.robinrehbein.punkt.ui.world.FxState
import de.robinrehbein.punkt.ui.world.drawTimingWorld
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.stringResource


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
            soundSet = store.selectedSound
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

    // Texte, die in Ereignis-Handlern gebraucht werden: Lesen geht nur
    // waehrend der Zusammensetzung, gebraucht werden sie im Moment eines
    // Treffers oder des Todes. Also einmal hier, dann als reine Werte.
    val twistBanners = rememberTwistBanners()
    val taunter = rememberTaunter()
    val bannerChainText = stringResource(Res.string.banner_chain)
    val bannerRecordText = stringResource(Res.string.banner_record)
    val bannerStageText = stringResource(Res.string.banner_stage)
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
    var phase by remember { mutableStateOf(GamePhase.READY) }
    var score by remember { mutableIntStateOf(0) }
    var bestScore by remember { mutableIntStateOf(store.bestScore) }
    var runNumber by remember { mutableIntStateOf(store.runCount) }
    var isNewRecord by remember { mutableStateOf(false) }
    var taunt by remember { mutableStateOf("") }
    var showPerfect by remember { mutableStateOf(false) }
    var perfectPoints by remember { mutableIntStateOf(2) }
    var bannerText by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }
    var soundOn by remember { mutableStateOf(!store.soundMuted) }
    var dailyMode by remember { mutableStateOf(false) }
    var skin by remember { mutableStateOf(store.selectedSkin) }
    // Die Kulisse ist die zweite Sammlung: kein Tagespass, kein
    // Verfallsdatum — deshalb reicht ein schlichter Zustand.
    var scene by remember { mutableStateOf(store.selectedScene) }
    // Und das Ton-Set als dritte Sammlung — dieselbe Bauart, nur hört
    // man sie, statt sie zu sehen.
    var sound by remember { mutableStateOf(store.selectedSound) }

    // Die Score-Karte zeichnet auf eine Android-Leinwand und kann keine
    // Texte lesen — Kulissenname und REKORD-Zeile kommen deshalb von
    // hier (siehe ScoreCard.share).
    val sceneNameText = sceneTitle(scene)
    val newRecordText = stringResource(Res.string.new_record)
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
    // Das nächstliegende Ziel für die Zeile im Game-Over — gefüllt in dem
    // Moment, in dem der Lauf gezählt ist, damit der Balken den gerade
    // beendeten Lauf schon enthält.
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
        if (!SkinPaint.isUnlocked(skin, store.stats()) && skin != pass) {
            skin = SkinId.KLASSIK
            store.selectedSkin = SkinId.KLASSIK
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
        if (dailyMode) game.reseed(DailyChallenge.seedFor(today)) else game.reseedSystem()
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
                        is GameEventStarted -> {
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
                        is GameEventHit -> {
                            haptics.score()
                            audio.hit(game.score)
                        }
                        is GameEventPerfectHit -> {
                            haptics.perfect()
                            audio.perfect(game.perfectStreak)
                            perfectPoints = game.lastHitPoints
                            runState.maxPerfect = max(runState.maxPerfect, game.perfectStreak)
                        }
                        is GameEventChainNext -> {
                            showBanner(bannerChainText, 1.2f, priority = 1)
                            audio.chain()
                        }
                        is GameEventTwistUnlocked -> {
                            twistUnlockedThisFrame = true
                            showBanner(twistBanners(event.twist), 2.2f, priority = 2)
                            fx.celebrateTime = CELEBRATE_SECONDS
                            haptics.unlock()
                            audio.unlock()
                        }
                        is GameEventDied -> {
                            haptics.death()
                            audio.death()
                            fx.flashAlpha = 1f
                            fx.shakeTime = 0.4f
                            fx.celebrateTime = 0f
                            fx.deathTime = 0f
                            val previousBest = store.bestScore
                            newMedalThisRun = MedalPaint.isUpgrade(game.score, previousBest)
                            // Gezählt wird, was VERDIENT ist: Saison zählt
                            // mit, Gönner nie — ein Kauf ist keine Leistung
                            // und darf die Feier nicht auslösen.
                            val earnedBefore = SkinPaint.earnedCount(store.stats())
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
                                SkinPaint.earnedCount(store.stats()) > earnedBefore
                            // Erst zählen, dann zielen: Der Balken im
                            // Game-Over zeigt den Stand NACH diesem Lauf.
                            nextGoal = Progress.nextGoal(
                                stats = store.stats(),
                                month = runState.month,
                                seasonDays = store.seasonDaysFor(runState.month, runState.year)
                            )
                            taunt = taunter(game.score, previousBest, isNewRecord)
                            bestScore = store.bestScore
                            runNumber = store.runCount
                            // Jeder beendete Lauf ist ein moeglicher neuer
                            // Stand fuer die Uhr. Ohne Aenderung ist der
                            // Aufruf ein No-op.
                            statsSync.publish()
                            if (isNewRecord && !bannerState.recordCelebrated) {
                                haptics.newRecord()
                            }
                        }
                        is GameEventSettled -> {
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
                if (game.phase == GamePhase.RUNNING &&
                    !bannerState.recordCelebrated &&
                    bestScore > 0 && game.score > bestScore
                ) {
                    bannerState.recordCelebrated = true
                    showBanner(bannerRecordText, 2.2f, priority = 2)
                    fx.celebrateTime = CELEBRATE_SECONDS
                    haptics.newRecord()
                    audio.newRecord()
                }

                // Stufen-Feedback: jede 5er-Stufe färbt den Himmel um — das
                // wird gefeiert, sofern nicht ohnehin gerade ein Twist-Banner
                // die große Bühne bekommt.
                val stage = game.score / 5
                if (game.phase == GamePhase.RUNNING && stage > bannerState.lastStage) {
                    bannerState.lastStage = stage
                    if (!twistUnlockedThisFrame) {
                        showBanner(bannerStageText, 1.6f, priority = 1)
                        fx.celebrateTime = CELEBRATE_SECONDS
                        haptics.unlock()
                        audio.unlock()
                    }
                }
                if (game.phase == GamePhase.READY) {
                    bannerState.lastStage = 0
                }

                phase = game.phase
                score = game.score
                showPerfect = game.lastHitPerfect && game.timeSinceHit < 0.6f &&
                    game.phase == GamePhase.RUNNING
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
                    if (game.phase == GamePhase.READY ||
                        game.phase == GamePhase.OVER
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
                text = stringResource(Res.string.perfect_plus, perfectPoints),
                style = ScoreShadowStyle,
                fontSize = 28.sp,
                color = Color(0xFFFFE95E),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = maxHeight * 0.74f)
            )
        }

        when (phase) {
            GamePhase.READY -> ReadyOverlay(
                bestScore = bestScore,
                runNumber = runNumber,
                hint = stringResource(Res.string.ready_hint),
                dailyBest = dailyBestToday,
                dailyStreak = dailyStreak,
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
                        stats = statsSnapshot,
                        month = now.monthValue,
                        seasonDays = store.seasonDaysFor(now.monthValue, now.year)
                    )
                    showStats = true
                },
                leaderboardAvailable = leaderboards.available,
                onLeaderboard = { leaderboards.show() },
                onHelp = { showHelp = true },
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
                // Kein Preis von Google = kein Angebot. Ein Knopf, der
                // ins Leere greift, ist schlimmer als gar keiner.
                removeAdsPrice = if (ads.enabled) billing.priceLabel else null,
                onRemoveAds = { (context as? Activity)?.let { billing.purchase(it) } },
                privacyVisible = ads.enabled && ads.privacyOptionsRequired,
                onPrivacy = { (context as? Activity)?.let { ads.showPrivacyOptions(it) } },
                diagnostics = if (showDiagnostics) {
                    "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "WERBUNG: ${ads.status}\n" +
                        "KAUF: ${billing.status}"
                } else {
                    null
                },
                onToggleDiagnostics = { showDiagnostics = !showDiagnostics }
            )
            GamePhase.RUNNING, GamePhase.DYING ->
                ScoreHud(
                    score = score,
                    daily = dailyMode,
                    banner = if (phase == GamePhase.RUNNING) bannerText else ""
                )
            GamePhase.OVER -> GameOverOverlay(
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
                        sceneName = sceneNameText,
                        recordText = newRecordText,
                        daily = dailyMode,
                        dailyStreak = dailyStreak,
                        // Rahmen und Beiname hängen am Gesamtstand, nicht
                        // am Lauf — der Stand wird erst beim Tippen auf
                        // TEILEN geholt, damit er den eben gezählten Lauf
                        // sicher enthält.
                        stats = store.stats()
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
                onClose = { showStats = false }
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
                    audio.soundSet = it
                    // Die Hörprobe ist der ganze Sinn der Zeile: Ohne sie
                    // waehlt man einen Klang nach seinem Namen.
                    audio.preview(it)
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
