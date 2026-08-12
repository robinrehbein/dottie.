package de.robinrehbein.punkt.billing

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import de.robinrehbein.punkt.data.ScoreStore

/**
 * Play Billing für den einen einmaligen Kauf "Werbung entfernen"
 * ([PRODUCT_ID], non-consumable).
 *
 * Genau wie [de.robinrehbein.punkt.ads.AdsManager] hart feature-geflaggt:
 * Ohne AdMob-IDs gibt es keine Werbung, also auch nichts zu entfernen —
 * dann wird gar kein BillingClient gebaut und Google nie kontaktiert.
 *
 * Beim Start läuft eine Wiederherstellung ([queryPurchases]): Der Kauf
 * hängt am Google-Konto, nicht am Gerät. Nach einer Neuinstallation ist
 * die App dadurch von selbst wieder werbefrei, ohne "Kauf
 * wiederherstellen"-Knopf.
 *
 * Fehler werden geschluckt und nur geloggt: Ein kaputter Billing-Dienst
 * darf das Spiel nicht behindern — im schlimmsten Fall passiert beim
 * Tippen auf den Kauf-Button einfach nichts.
 */
class BillingManager(
    private val activity: Activity?,
    private val store: ScoreStore,
    private val configured: Boolean,
    private val onAdsRemoved: () -> Unit
) {

    private var client: BillingClient? = null
    private var productDetails: ProductDetails? = null

    private val purchasesUpdated = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        } else {
            // Abbruch durch die Nutzerin ist der Normalfall, kein Fehler.
            Log.i(TAG, "Kauf nicht abgeschlossen: ${result.responseCode}")
        }
    }

    /** Einmal beim Start aufrufen; ohne AdMob-IDs ein No-op. */
    fun connect() {
        if (!configured || activity == null || client != null) return
        try {
            val billing = BillingClient.newBuilder(activity)
                .setListener(purchasesUpdated)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
                )
                .build()
            client = billing
            billing.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        Log.i(TAG, "Billing-Verbindung: ${result.responseCode}")
                        return
                    }
                    queryProduct()
                    queryPurchases()
                }

                override fun onBillingServiceDisconnected() {
                    // Kein Reconnect-Karussell: Der nächste App-Start
                    // versucht es ohnehin neu, und ohne Verbindung ist
                    // der Kauf-Button eben wirkungslos.
                    Log.i(TAG, "Billing-Verbindung getrennt")
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Billing-Start fehlgeschlagen", t)
        }
    }

    /** Preis und Angebot laden — ohne Details lässt sich nichts kaufen. */
    private fun queryProduct() {
        val billing = client ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        try {
            billing.queryProductDetailsAsync(params) { result, details ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    productDetails = details.firstOrNull()
                } else {
                    Log.i(TAG, "Produkt nicht gefunden: ${result.responseCode}")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Produktabfrage fehlgeschlagen", t)
        }
    }

    /**
     * Wiederherstellung: Was Google als gekauft kennt, gilt — auch nach
     * Geräte- oder Neuinstallation.
     */
    private fun queryPurchases() {
        val billing = client ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        try {
            billing.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases.forEach { handlePurchase(it) }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Kaufabfrage fehlgeschlagen", t)
        }
    }

    /** Startet den Kaufdialog für "Werbung entfernen". */
    fun purchase(activity: Activity) {
        val billing = client ?: return
        val details = productDetails ?: return
        try {
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .build()
                    )
                )
                .build()
            billing.launchBillingFlow(activity, flowParams)
        } catch (t: Throwable) {
            Log.w(TAG, "Kaufdialog fehlgeschlagen", t)
        }
    }

    /**
     * Schaltet die Werbung ab und bestätigt den Kauf. Ohne Acknowledge
     * innerhalb von drei Tagen erstattet Google automatisch zurück —
     * deshalb passiert es hier bei jedem Auftauchen des Kaufs, nicht nur
     * direkt nach dem Kaufdialog.
     */
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (PRODUCT_ID !in purchase.products) return

        if (!store.adsRemoved) {
            store.adsRemoved = true
            onAdsRemoved()
        }

        if (purchase.isAcknowledged) return
        val billing = client ?: return
        try {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billing.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "Acknowledge: ${result.responseCode}")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Acknowledge fehlgeschlagen", t)
        }
    }

    /** Verbindung schließen, wenn der Screen verschwindet. */
    fun release() {
        try {
            client?.endConnection()
        } catch (t: Throwable) {
            Log.w(TAG, "Billing-Ende fehlgeschlagen", t)
        }
        client = null
        productDetails = null
    }

    companion object {
        /** Einmaliger, nicht verbrauchbarer Kauf (Play Console). */
        const val PRODUCT_ID = "remove_ads"
        private const val TAG = "BillingManager"
    }
}
