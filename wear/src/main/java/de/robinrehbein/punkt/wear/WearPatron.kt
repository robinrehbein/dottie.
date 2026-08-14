package de.robinrehbein.punkt.wear

import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Fragt Play, ob das Gönner-Paket ("patron_pack") gekauft ist — und sonst
 * nichts. Kein Produktkatalog, kein Kaufdialog: Auf der Uhr kauft
 * niemand, hier wird nur eingelöst.
 *
 * Warum die Uhr selbst fragt, statt sich ein Flag vom Telefon schicken zu
 * lassen: Der Kauf hängt am Google-Konto, nicht am Gerät, und Wear-App
 * und Phone-App teilen sich die Paket-ID — dieselbe Abfrage
 * ([BillingClient.queryPurchasesAsync]) liefert hier also dieselbe
 * Antwort wie dort. Ein über den Data Layer geschicktes Besitz-Flag wäre
 * dagegen fälschbar und ginge ohne gekoppeltes Telefon gar nicht.
 *
 * Fehlt Play (keine Play-Dienste auf der Uhr, seitlich installiert),
 * bleibt alles inert: Die Verbindung scheitert, es wird nichts gemeldet,
 * die Gönner-Skins bleiben schlicht gesperrt. Kein Fehlerdialog, keine
 * tote UI — dieselbe Haltung wie beim BillingManager am Telefon.
 *
 * Bestätigt (acknowledged) wird hier bewusst nicht: Gekauft wird am
 * Telefon, und dort quittiert der BillingManager den Kauf sofort. Die Uhr
 * ist reine Leserin.
 *
 * @param onOwned wird gerufen, wenn Play den Kauf bestätigt — nie mit
 *                "nicht gekauft": Eine ausbleibende oder leere Antwort
 *                heißt "Play weiß gerade nichts", nicht "nie gekauft",
 *                und darf den lokalen Spiegel nicht zurücknehmen.
 */
internal class WearPatron(
    context: Context,
    private val onOwned: () -> Unit
) {

    /** ApplicationContext: Zum Lesen reicht er, und er leakt nicht. */
    private val appContext = context.applicationContext

    private var client: BillingClient? = null
    private var connected = false

    /**
     * Verbinden und abfragen. Mehrfach aufrufbar (onStart): Steht die
     * Verbindung schon, wird nur neu gefragt — der Kauf kann seit dem
     * letzten Blick am Telefon passiert sein.
     */
    fun connect() {
        val existing = client
        if (existing != null) {
            if (connected) queryPurchases(existing)
            return
        }
        try {
            val billing = BillingClient.newBuilder(appContext)
                // Beides verlangt der Builder, obwohl hier nie gekauft
                // wird: ein Listener für Kaufergebnisse (er feuert nie)
                // und die Pending-Params für Einmalkäufe.
                .setListener(NOOP_LISTENER)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
                )
                .build()
            client = billing
            billing.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        // Code 3 (BILLING_UNAVAILABLE) heißt fast immer:
                        // keine Play-Dienste oder nicht über Play
                        // installiert. Dann bleibt es eben gesperrt.
                        Log.i(TAG, "Keine Play-Verbindung: ${result.responseCode}")
                        return
                    }
                    connected = true
                    queryPurchases(billing)
                }

                override fun onBillingServiceDisconnected() {
                    // Kein Reconnect-Karussell: Der nächste onStart
                    // versucht es ohnehin neu.
                    connected = false
                    Log.i(TAG, "Play-Verbindung getrennt")
                }
            })
        } catch (t: Throwable) {
            // Ohne Play-Bibliothek auf dem Gerät fliegt hier schon der
            // Aufbau — auch das ist kein Grund, das Spiel zu stören.
            Log.w(TAG, "Play-Abfrage nicht möglich", t)
        }
    }

    private fun queryPurchases(billing: BillingClient) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        try {
            billing.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "Kaufabfrage: ${result.responseCode}")
                    return@queryPurchasesAsync
                }
                val owned = purchases.any { purchase ->
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        PATRON_ID in purchase.products
                }
                if (owned) onOwned()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Kaufabfrage fehlgeschlagen", t)
        }
    }

    /** Verbindung schließen; aus MainActivity.onDestroy gerufen. */
    fun release() {
        try {
            client?.endConnection()
        } catch (t: Throwable) {
            Log.w(TAG, "Play-Verbindung schließen fehlgeschlagen", t)
        }
        client = null
        connected = false
    }

    private companion object {
        /** Dieselbe Produkt-ID wie in BillingManager am Telefon. */
        const val PATRON_ID = "patron_pack"
        const val TAG = "WearPatron"

        /** Hier wird nie gekauft — es gibt also auch nichts zu melden. */
        val NOOP_LISTENER = PurchasesUpdatedListener { _, _ -> }
    }
}
