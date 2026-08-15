package de.robinrehbein.punkt.ui.screens

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.robinrehbein.punkt.BuildConfig
import de.robinrehbein.punkt.ads.AdsManager
import de.robinrehbein.punkt.billing.BillingManager
import de.robinrehbein.punkt.game.GameAudio
import de.robinrehbein.punkt.game.GameHaptics
import de.robinrehbein.punkt.notify.DailyReminder
import de.robinrehbein.punkt.play.Leaderboards
import de.robinrehbein.punkt.share.ScoreCard
import de.robinrehbein.punkt.sync.StatsSync
import de.robinrehbein.punkt.ui.data.AndroidKeyValueStore
import de.robinrehbein.punkt.ui.data.GameStore
import de.robinrehbein.punkt.ui.platform.PlatformHooks

/**
 * Die Android-Schale um [GameScreen].
 *
 * Das Spiel selbst steht seit v2.24 in `:ui` und laeuft dort auf beiden
 * Plattformen. Hier bleibt nur, was es auf dem iPhone nicht gibt:
 * Werbung, Kauf, Bestenlisten, der Abgleich mit der Uhr, die
 * Benachrichtigungs-Berechtigung und das Teilen — plus die drei Dienste,
 * die Android anders baut (Speicher, Klang, Haptik).
 *
 * Diese Datei ist die einzige der Oberflaeche, die noch `Activity` und
 * `LocalLifecycleOwner` kennt. Genau das war der Sinn der Uebung.
 */
@Composable
fun TimingGameScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity
    val store = remember { GameStore(AndroidKeyValueStore(context)) }
    val sounds = remember { GameAudio(context) }
    val feedback = remember { GameHaptics(context) }

    val leaderboards = remember { Leaderboards(activity) }
    // Werbung und Kauf haengen wie die Bestenlisten an der Activity —
    // alle drei sind ohne konfigurierte IDs komplett inaktiv.
    val ads = remember { AdsManager(activity, store) }
    val billing = remember {
        BillingManager(
            activity = activity,
            store = store,
            onAdsRemoved = { ads.disableAfterPurchase() },
            onPatronOwned = {}
        )
    }
    val statsSync = remember {
        StatsSync(
            context = context,
            read = { store.syncState() },
            write = { store.applySync(it) }
        )
    }

    // Ab Android 13 braucht die Erinnerung die Notification-Permission.
    // Der Rueckruf traegt das Ergebnis zurueck in den Bildschirm: Ein
    // aktivierter Schalter ohne Zustellung waere eine Luege.
    var reminderResult: ((Boolean) -> Unit)? = remember { null }
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) DailyReminder.schedule(context)
        reminderResult?.invoke(granted)
    }

    LaunchedEffect(Unit) { leaderboards.connect() }
    LaunchedEffect(Unit) {
        ads.start()
        billing.connect()
    }
    // Falls die Erinnerung aktiv ist: Planung idempotent auffrischen
    // (uebersteht App-Updates und geloeschte WorkManager-Jobs).
    LaunchedEffect(Unit) {
        if (store.reminderEnabled) DailyReminder.schedule(context)
    }
    DisposableEffect(Unit) {
        onDispose { billing.release() }
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

    GameScreen(
        store = store,
        sounds = sounds,
        feedback = feedback,
        modifier = modifier,
        hooks = PlatformHooks(
            adsEnabled = ads.enabled,
            onRunEnded = { activity?.let { ads.onGameOver(it) } },
            rewardedReady = ads.rewardedReady,
            onWatchAdFor = { _, onEarned ->
                activity?.let { ads.showRewarded(it) { onEarned() } }
            },
            privacyVisible = ads.enabled && ads.privacyOptionsRequired,
            onPrivacy = { activity?.let { ads.showPrivacyOptions(it) } },
            removeAdsPrice = if (ads.enabled) billing.priceLabel else null,
            onRemoveAds = { activity?.let { billing.purchase(it) } },
            patronPrice = billing.patronPriceLabel,
            onPatron = { activity?.let { billing.purchasePatron(it) } },
            leaderboardAvailable = leaderboards.available,
            onShowLeaderboard = { leaderboards.show() },
            onSubmitBest = { leaderboards.submitBest(it) },
            onSubmitDaily = { leaderboards.submitDaily(it) },
            onPublishSync = { statsSync.publish() },
            reminderSupported = true,
            setReminder = { wanted, onResult ->
                when {
                    !wanted -> {
                        DailyReminder.cancel(context)
                        onResult(false)
                    }
                    Build.VERSION.SDK_INT >= 33 && DailyReminder.needsPermission(context) -> {
                        reminderResult = onResult
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    else -> {
                        DailyReminder.schedule(context)
                        onResult(true)
                    }
                }
            },
            onShare = { anfrage ->
                ScoreCard.share(
                    context = context,
                    score = anfrage.score,
                    bestScore = anfrage.bestScore,
                    isNewRecord = anfrage.isNewRecord,
                    skin = store.selectedSkin,
                    scene = store.selectedScene,
                    sceneName = anfrage.sceneName,
                    recordText = anfrage.recordText,
                    daily = anfrage.daily,
                    dailyStreak = anfrage.dailyStreak,
                    // Rahmen und Beiname haengen am Gesamtstand, nicht am
                    // Lauf — der Stand wird erst beim Tippen auf TEILEN
                    // geholt, damit er den eben gezaehlten Lauf enthaelt.
                    stats = store.stats()
                )
            },
            diagnostics = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                "WERBUNG: ${ads.status}\n" +
                "KAUF: ${billing.status}"
        )
    )
}
