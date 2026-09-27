package de.robinrehbein.punkt.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import de.robinrehbein.punkt.R
import de.robinrehbein.punkt.ui.data.GameStore
import de.robinrehbein.punkt.ui.platform.AnalyticsEvent

/**
 * Die Nutzungsstatistik auf Android (ab v2.36): eine dünne Hülle um
 * Firebase Analytics.
 *
 * Zwei Schalter entscheiden, ob überhaupt etwas passiert, und beide
 * müssen an sein:
 *
 * 1. **Konfiguriert** ([available]): In `res/values/analytics.xml` steht
 *    ein Firebase-Projekt. Sonst wird keine einzige Firebase-Methode
 *    aufgerufen — genau wie AdsManager ohne AdMob-IDs.
 * 2. **Eingewilligt** ([GameStore.analyticsConsent]): Die Person hat im
 *    Spiel JA gesagt. Bis dahin hält das Manifest die Erfassung aus
 *    (`firebase_analytics_collection_enabled = false`), und Firebase
 *    speichert auch keine automatischen Ereignisse vor.
 *
 * Werbe-ID und Werbe-Signale bleiben immer aus (Manifest und
 * [grantConsent]): Die Zahlen sind zum Abstimmen des Spiels da.
 */
class GameAnalytics(context: Context, private val store: GameStore) {

    private val appContext = context.applicationContext

    /** Ist ein Firebase-Projekt eingetragen? */
    val available: Boolean = listOf(
        R.string.google_app_id,
        R.string.google_api_key,
        R.string.project_id
    ).all { appContext.getString(it).isNotBlank() }

    private val firebase: FirebaseAnalytics? by lazy {
        if (!available) return@lazy null
        try {
            FirebaseAnalytics.getInstance(appContext)
        } catch (t: Throwable) {
            // Ein falsch eingetragenes Projekt darf das Spiel nicht
            // mitreißen — dann gibt es eben keine Zahlen.
            Log.w(TAG, "Firebase Analytics nicht verfügbar", t)
            null
        }
    }

    /** Beim Start: den gespeicherten Stand der Einwilligung anwenden. */
    fun applyStoredConsent() {
        if (!available) return
        setConsent(store.analyticsConsent == true)
    }

    /**
     * Einwilligung gegeben oder zurückgenommen. Beim Widerruf wird auch
     * gelöscht, was Firebase noch nicht gesendet hat, samt der
     * zufälligen Instanz-Kennung.
     */
    fun setConsent(granted: Boolean) {
        val fa = firebase ?: return
        if (granted) {
            grantConsent(fa)
            fa.setAnalyticsCollectionEnabled(true)
        } else {
            fa.setAnalyticsCollectionEnabled(false)
            fa.resetAnalyticsData()
        }
    }

    private fun grantConsent(fa: FirebaseAnalytics) {
        fa.setConsent(
            mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to FirebaseAnalytics.ConsentStatus.GRANTED,
                FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED
            )
        )
    }

    /** Ein Ereignis senden — nur mit Einwilligung, sonst passiert nichts. */
    fun log(event: AnalyticsEvent) {
        if (store.analyticsConsent != true) return
        val fa = firebase ?: return
        fa.logEvent(event.name, event.params.toBundle())
    }

    private fun Map<String, Any>.toBundle(): Bundle = Bundle().also { bundle ->
        forEach { (key, value) ->
            when (value) {
                is Long -> bundle.putLong(key, value)
                is Int -> bundle.putLong(key, value.toLong())
                // Firebase kürzt Texte über 100 Zeichen ohnehin; hier
                // schon, damit nie ein halber Wert ankommt.
                else -> bundle.putString(key, value.toString().take(MAX_TEXT))
            }
        }
    }

    private companion object {
        const val TAG = "GameAnalytics"
        const val MAX_TEXT = 100
    }
}
