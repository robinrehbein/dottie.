package de.robinrehbein.punkt.ads

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import de.robinrehbein.punkt.R
import de.robinrehbein.punkt.data.ScoreStore

/**
 * Alles rund um AdMob an einer Stelle: UMP-Consent, ein Rewarded-Spot
 * fürs Weiterspielen und gelegentliche Interstitials.
 *
 * Die Integration ist hart feature-geflaggt, genau wie die
 * Play-Games-Bestenlisten: Solange in res/values/ads.xml eine der drei
 * IDs leer ist, wird das SDK NIE initialisiert — kein Consent-Dialog,
 * keine Requests, kein Google-Kontakt. Die App verhält sich dann exakt
 * wie ohne die Abhängigkeit. Das ist die wichtigste Eigenschaft hier:
 * Werbung entsteht ausschließlich durch bewusstes Eintragen echter IDs
 * (Anleitung in PUBLISHING.md).
 *
 * Zweite Abschaltung: der Kauf "Werbung entfernen" ([ScoreStore.adsRemoved]).
 *
 * Fehler werden grundsätzlich geschluckt und nur geloggt. Werbung ist
 * Beiwerk — sie darf einen Lauf nie blockieren und erst recht nicht die
 * App abstürzen lassen. Im Zweifel erscheint einfach keine Anzeige.
 */
class AdsManager(
    private val activity: Activity?,
    private val store: ScoreStore
) {

    private val appId: String = activity?.getString(R.string.admob_app_id).orEmpty()
    private val rewardedId: String = activity?.getString(R.string.admob_rewarded_id).orEmpty()
    private val interstitialId: String =
        activity?.getString(R.string.admob_interstitial_id).orEmpty()

    /**
     * Sind überhaupt AdMob-IDs hinterlegt? Getrennt von [enabled], weil
     * auch der "Werbung entfernen"-Kauf daran hängt: Ohne Werbung im
     * Spiel gibt es nichts zu entfernen — und damit auch keinen
     * BillingClient.
     */
    val configured: Boolean = activity != null &&
        appId.isNotBlank() && rewardedId.isNotBlank() && interstitialId.isNotBlank()

    /**
     * Darf Werbung laufen? Beobachtbar, weil der Kauf sie mitten in der
     * Sitzung dauerhaft abschaltet und die UI ihre Buttons sofort
     * verstecken muss.
     */
    var enabled by mutableStateOf(configured && !store.adsRemoved)
        private set

    /** Liegt ein Rewarded-Spot bereit? Gate für den WEITERSPIELEN-Button. */
    var rewardedReady by mutableStateOf(false)
        private set

    private var rewarded: RewardedAd? = null
    private var interstitial: InterstitialAd? = null
    private var started = false
    private var consentInformation: ConsentInformation? = null

    /** Frequenz-Deckel für Interstitials (siehe [InterstitialGate]). */
    private val gate = InterstitialGate { System.currentTimeMillis() }

    /**
     * Einmal beim Start aufrufen. Ohne konfigurierte IDs ein No-op —
     * hier entscheidet sich, ob Google überhaupt kontaktiert wird.
     */
    fun start() {
        if (!enabled || activity == null || started) return
        started = true
        try {
            requestConsentThenInitialize(activity)
        } catch (t: Throwable) {
            Log.w(TAG, "Ads-Start fehlgeschlagen — es bleibt werbefrei", t)
        }
    }

    /**
     * Consent zuerst: Vor dem ersten Request muss klar sein, ob wir in
     * der EU personalisierte (oder überhaupt) Anzeigen ausliefern
     * dürfen. Jeder Fehlerpfad endet gleich — wir fragen [canRequestAds]
     * und lassen es sonst bleiben.
     */
    private fun requestConsentThenInitialize(activity: Activity) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation = info
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent-Formular: ${formError.message}")
                    }
                    initializeIfAllowed()
                }
            },
            { requestError ->
                Log.w(TAG, "Consent-Abfrage: ${requestError.message}")
                initializeIfAllowed()
            }
        )
    }

    private fun initializeIfAllowed() {
        if (!enabled || activity == null) return
        if (consentInformation?.canRequestAds() != true) {
            Log.i(TAG, "Kein Consent für Anzeigen — es bleibt werbefrei")
            return
        }
        try {
            MobileAds.initialize(activity) {
                loadRewarded()
                loadInterstitial()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "MobileAds-Init fehlgeschlagen", t)
        }
    }

    // ===== Rewarded: Weiterspielen nach dem Tod =====

    private fun loadRewarded() {
        if (!enabled || activity == null || rewarded != null) return
        RewardedAd.load(
            activity,
            rewardedId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewarded = ad
                    rewardedReady = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewarded = null
                    rewardedReady = false
                    Log.i(TAG, "Rewarded nicht geladen: ${error.message}")
                }
            }
        )
    }

    /**
     * Zeigt den Rewarded-Spot. [onReward] läuft nur, wenn Google die
     * Belohnung wirklich bestätigt — ein Abbruch führt zu gar nichts,
     * das Game-Over bleibt dann einfach stehen.
     */
    fun showRewarded(activity: Activity, onReward: () -> Unit) {
        val ad = rewarded
        if (!enabled || ad == null) return
        rewarded = null
        rewardedReady = false
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                // Nächsten Spot vorladen, damit der Button beim übernächsten
                // Tod wieder sofort bereitsteht.
                loadRewarded()
                if (earned) onReward()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.i(TAG, "Rewarded nicht gezeigt: ${error.message}")
                loadRewarded()
            }
        }
        try {
            ad.show(activity) { earned = true }
        } catch (t: Throwable) {
            Log.w(TAG, "Rewarded-Anzeige fehlgeschlagen", t)
            loadRewarded()
        }
    }

    // ===== Interstitial: gelegentlich beim Game-Over =====

    private fun loadInterstitial() {
        if (!enabled || activity == null || interstitial != null) return
        InterstitialAd.load(
            activity,
            interstitialId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                    Log.i(TAG, "Interstitial nicht geladen: ${error.message}")
                }
            }
        )
    }

    /**
     * Meldet ein endgültiges Game-Over (also keins, aus dem gerade per
     * Rewarded weitergespielt wurde) und zeigt ggf. ein Interstitial.
     * Die Häufigkeit regelt allein [InterstitialGate].
     */
    fun onGameOver(activity: Activity) {
        if (!enabled) return
        if (!gate.onDeathShouldShow()) return
        val ad = interstitial ?: return
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadInterstitial()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.i(TAG, "Interstitial nicht gezeigt: ${error.message}")
                loadInterstitial()
            }
        }
        try {
            ad.show(activity)
            gate.markShown()
        } catch (t: Throwable) {
            Log.w(TAG, "Interstitial-Anzeige fehlgeschlagen", t)
            loadInterstitial()
        }
    }

    /**
     * Nach dem Kauf "Werbung entfernen": alles Geladene verwerfen und
     * nichts Neues mehr anfordern. Der Kauf wirkt damit sofort, nicht
     * erst beim nächsten App-Start.
     */
    fun disableAfterPurchase() {
        enabled = false
        rewardedReady = false
        rewarded = null
        interstitial = null
    }

    private companion object {
        const val TAG = "AdsManager"
    }
}
