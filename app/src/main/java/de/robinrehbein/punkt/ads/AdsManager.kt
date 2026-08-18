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
import de.robinrehbein.punkt.ui.data.AndroidKeyValueStore
import de.robinrehbein.punkt.ui.data.GameStore

/**
 * Alles rund um AdMob an einer Stelle: UMP-Consent, ein Rewarded-Spot
 * für den Skin-Tagespass und gelegentliche Interstitials.
 *
 * Die Integration ist hart feature-geflaggt, genau wie die
 * Play-Games-Bestenlisten: Solange in res/values/ads.xml eine der drei
 * IDs leer ist, wird das SDK NIE initialisiert — kein Consent-Dialog,
 * keine Requests, kein Google-Kontakt. Die App verhält sich dann exakt
 * wie ohne die Abhängigkeit. Das ist die wichtigste Eigenschaft hier:
 * Werbung entsteht ausschließlich durch bewusstes Eintragen echter IDs
 * (Anleitung in PUBLISHING.md).
 *
 * Zweite Abschaltung: der Kauf "Werbung entfernen" ([GameStore.adsRemoved]).
 *
 * Fehler werden grundsätzlich geschluckt und nur geloggt. Werbung ist
 * Beiwerk — sie darf einen Lauf nie blockieren und erst recht nicht die
 * App abstürzen lassen. Im Zweifel erscheint einfach keine Anzeige.
 */
class AdsManager(
    private val activity: Activity?,
    private val store: GameStore
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

    /** Liegt ein Rewarded-Spot bereit? Gate für das Tagespass-Angebot. */
    var rewardedReady by mutableStateOf(false)
        private set

    /**
     * Muss die App einen dauerhaften Weg zurück ins Einwilligungs-Formular
     * anbieten? Googles UMP beantwortet das je nach Region: In der EU ist
     * eine einmal erteilte Einwilligung ohne Widerrufsmöglichkeit
     * wertlos, außerhalb gibt es oft gar kein Formular. Beobachtbar, weil
     * die Antwort erst nach dem Netzabruf feststeht — der Startscreen
     * blendet die Zeile dann nach.
     */
    var privacyOptionsRequired by mutableStateOf(false)
        private set

    /**
     * Klartext-Zustand für die versteckte Diagnose-Zeile (langer Druck auf
     * den Titel). Von außen sieht "keine Einwilligung" genauso aus wie
     * "keine Anzeige verfügbar" — nämlich nach gar nichts. Ohne Rechner
     * und Logcat ist das sonst nicht auseinanderzuhalten, und genau daran
     * ist beim Gerätetest schon zweimal eine Stunde draufgegangen.
     */
    var status by mutableStateOf(if (activity == null) "keine Activity" else "nicht gestartet")
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
        if (!configured) { status = "aus — keine IDs"; return }
        if (store.adsRemoved) { status = "aus — gekauft"; return }
        if (!enabled || activity == null || started) return
        started = true
        status = "frage Einwilligung ab"

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
                    if (formError != null) status = "Consent-Formular: ${formError.message}"
                    updatePrivacyOptionsRequired()
                    initializeIfAllowed()
                }
            },
            { requestError ->
                Log.w(TAG, "Consent-Abfrage: ${requestError.message}")
                status = "Consent-Abfrage fehlgeschlagen: ${requestError.message}"
                updatePrivacyOptionsRequired()
                initializeIfAllowed()
            }
        )
    }

    private fun updatePrivacyOptionsRequired() {
        privacyOptionsRequired = consentInformation?.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    /**
     * Öffnet Googles Einwilligungs-Formular erneut, damit sich eine
     * einmal getroffene Wahl auch wieder ändern lässt. Ohne diesen Weg
     * wäre die Einwilligung nach DSGVO nicht widerrufbar — und Google
     * verlangt ihn ausdrücklich, sobald [privacyOptionsRequired] gilt.
     */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Datenschutz-Formular: ${formError.message}")
                return@showPrivacyOptionsForm
            }
            // Ein Widerruf kann die Auslieferung beenden — dann laufen die
            // bereits geladenen Spots ins Leere. Neu bewerten statt raten.
            updatePrivacyOptionsRequired()
            if (consentInformation?.canRequestAds() != true) {
                rewarded = null
                rewardedReady = false
                interstitial = null
            } else {
                loadRewarded()
                loadInterstitial()
            }
        }
    }

    private fun initializeIfAllowed() {
        if (!enabled || activity == null) return
        if (consentInformation?.canRequestAds() != true) {
            Log.i(TAG, "Kein Consent für Anzeigen — es bleibt werbefrei")
            // Der haeufigste Stolperstein: In AdMob fehlt eine
            // veroeffentlichte DSGVO-Mitteilung, dann gibt es kein
            // Formular, der Status bleibt "erforderlich" und das SDK
            // startet nie — auch das Anzeigenprueftool reagiert dann nicht.
            status = "keine Einwilligung — SDK nicht gestartet"
            return
        }
        try {
            status = "SDK startet"
            MobileAds.initialize(activity) {
                status = "SDK bereit"
                loadRewarded()
                loadInterstitial()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "MobileAds-Init fehlgeschlagen", t)
            status = "SDK-Start fehlgeschlagen"
        }
    }

    // ===== Rewarded: Skin-Tagespass =====

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
                    status = "Spot bereit"
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewarded = null
                    rewardedReady = false
                    // "No fill" (Code 3) heisst: alles richtig eingebaut,
                    // Google hat nur gerade keine Anzeige. Bei frischen
                    // Anzeigenbloecken stundenlang der Normalfall.
                    status = "Spot: ${error.message} (Code ${error.code})"
                    Log.i(TAG, "Rewarded nicht geladen: ${error.message}")
                }
            }
        )
    }

    /**
     * Zeigt den Rewarded-Spot. [onReward] läuft nur, wenn Google die
     * Belohnung wirklich bestätigt — ein Abbruch führt zu gar nichts,
     * der Skin bleibt dann einfach gesperrt.
     */
    fun showRewarded(activity: Activity, onReward: () -> Unit) {
        val ad = rewarded
        if (!enabled || ad == null) return
        rewarded = null
        rewardedReady = false
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                // Nächsten Spot vorladen, damit das Angebot beim nächsten
                // Blick in die Skins wieder sofort bereitsteht.
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
     * Meldet ein Game-Over und zeigt ggf. ein Interstitial. Jeder Tod ist
     * endgültig — die Häufigkeit regelt deshalb allein [InterstitialGate].
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
