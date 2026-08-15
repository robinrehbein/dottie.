package de.robinrehbein.punkt.billing

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import de.robinrehbein.punkt.ui.data.AndroidKeyValueStore
import de.robinrehbein.punkt.ui.data.GameStore

/**
 * Play Billing für die beiden einmaligen Käufe: "Werbung entfernen"
 * ([PRODUCT_ID]) und das Gönner-Paket ([PATRON_ID]) — beide
 * non-consumable.
 *
 * Der Kauf hängt bewusst NICHT mehr an der Werbe-Konfiguration. Solange
 * es nur "Werbung entfernen" gab, war die Kopplung richtig: ohne Werbung
 * nichts zu entfernen. Mit dem Gönner-Paket stimmt sie nicht mehr — drei
 * Skins sind auch ohne eine einzige Anzeige verkäuflich, und ein leeres
 * ads.xml hätte das Produkt sonst unerreichbar gemacht, ohne dass
 * irgendwo ein Fehler erscheint.
 *
 * Ein eigener Schalter ist dafür nicht nötig: Der BillingClient ist von
 * selbst harmlos, wenn nichts da ist. Ohne Play-Dienste scheitert der
 * Verbindungsaufbau, ohne angelegtes Produkt bleibt die Antwort leer —
 * und in beiden Fällen erscheint schlicht keine Kauf-Zeile.
 *
 * Beim Start läuft eine Wiederherstellung ([queryPurchases]): Die Käufe
 * hängen am Google-Konto, nicht am Gerät. Nach einer Neuinstallation ist
 * die App dadurch von selbst wieder werbefrei und die Gönner-Skins sind
 * zurück, ohne "Kauf wiederherstellen"-Knopf.
 *
 * Fehler werden geschluckt und nur geloggt: Ein kaputter Billing-Dienst
 * darf das Spiel nicht behindern. Sichtbar wird ein Angebot erst, wenn
 * Google tatsächlich ein kaufbares Produkt liefert ([priceLabel],
 * [patronPriceLabel]) — ein Knopf, der ins Leere greift, ist schlimmer
 * als gar keiner. Das gilt pro Produkt: Fehlt in der Play Console nur
 * das Gönner-Paket, bleibt der Rest unverändert kaufbar.
 */
class BillingManager(
    private val activity: Activity?,
    private val store: GameStore,
    private val onAdsRemoved: () -> Unit,
    private val onPatronOwned: () -> Unit = {}
) {

    private var client: BillingClient? = null
    private var productDetails: ProductDetails? = null
    private var patronDetails: ProductDetails? = null

    /**
     * Der von Google formatierte Preis ("0,99 €"), sobald das Produkt
     * abrufbar ist — sonst null. Doppelte Aufgabe: Er ist die Bedingung
     * dafür, dass das Angebot überhaupt erscheint, und zugleich der Text
     * daneben. Der Preis kommt bewusst von Google und steht nirgends im
     * Code: Er hängt an Land, Währung und Steuersatz und würde als feste
     * Zeichenkette in der Hälfte der Welt falsch dastehen.
     */
    var priceLabel by mutableStateOf<String?>(null)
        private set

    /** Wie [priceLabel], nur für das Gönner-Paket. */
    var patronPriceLabel by mutableStateOf<String?>(null)
        private set

    /** Klartext-Zustand für die versteckte Diagnose-Zeile. */
    var status by mutableStateOf("nicht verbunden")
        private set

    private val purchasesUpdated = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        } else {
            // Abbruch durch die Nutzerin ist der Normalfall, kein Fehler.
            Log.i(TAG, "Kauf nicht abgeschlossen: ${result.responseCode}")
        }
    }

    /** Einmal beim Start aufrufen; ohne Activity ein No-op. */
    fun connect() {
        if (activity == null || client != null) return
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
                        // Code 3 (BILLING_UNAVAILABLE) heisst fast immer:
                        // App nicht ueber Play installiert.
                        status = "keine Play-Verbindung (Code ${result.responseCode})"
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
                    status = "Verbindung getrennt"
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Billing-Start fehlgeschlagen", t)
        }
    }

    /**
     * Preise und Angebote laden — ohne Details lässt sich nichts kaufen.
     * Beide Produkte wandern in EINE Abfrage: Google beantwortet sie
     * zusammen, und was fehlt, fehlt eben einzeln.
     */
    private fun queryProduct() {
        val billing = client ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(PRODUCT_ID, PATRON_ID).map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
            )
            .build()
        try {
            billing.queryProductDetailsAsync(params) { result, details ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    val found = details.firstOrNull { it.productId == PRODUCT_ID }
                    productDetails = found
                    priceLabel = found?.oneTimePurchaseOfferDetails?.formattedPrice
                    val patron = details.firstOrNull { it.productId == PATRON_ID }
                    patronDetails = patron
                    patronPriceLabel = patron?.oneTimePurchaseOfferDetails?.formattedPrice
                    status = when {
                        found == null -> "Produkt $PRODUCT_ID nicht in der Antwort"
                        priceLabel == null -> "Produkt da, aber ohne Preis"
                        else -> "kaufbar für $priceLabel" +
                            (patronPriceLabel?.let { ", Gönner $it" } ?: ", ohne Gönner-Paket")
                    }
                } else {
                    // Produkt in der Play Console nicht angelegt, App nicht
                    // über Play installiert, kein Play-Dienst: In allen drei
                    // Fällen bleibt das Angebot unsichtbar statt tot.
                    Log.i(TAG, "Produkt nicht gefunden: ${result.responseCode}")
                    // Frisch angelegte Produkte brauchen Stunden, bis sie
                    // ueber diese Abfrage auffindbar sind.
                    status = "Produkt nicht gefunden (Code ${result.responseCode})"
                    productDetails = null
                    priceLabel = null
                    patronDetails = null
                    patronPriceLabel = null
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
    fun purchase(activity: Activity) = launchFlow(activity, productDetails)

    /** Startet den Kaufdialog für das Gönner-Paket. */
    fun purchasePatron(activity: Activity) = launchFlow(activity, patronDetails)

    private fun launchFlow(activity: Activity, details: ProductDetails?) {
        val billing = client ?: return
        if (details == null) return
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
     * Schaltet frei, was der Kauf enthält, und bestätigt ihn. Ohne
     * Acknowledge innerhalb von drei Tagen erstattet Google automatisch
     * zurück — deshalb passiert es hier bei jedem Auftauchen des Kaufs,
     * nicht nur direkt nach dem Kaufdialog.
     */
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val patron = PATRON_ID in purchase.products
        val removeAds = PRODUCT_ID in purchase.products
        if (!patron && !removeAds) return

        if (patron && !store.patronOwned) {
            store.patronOwned = true
            onPatronOwned()
        }

        // Das Gönner-Paket enthält "Werbung entfernen" — wer es kauft,
        // soll nicht zweimal zahlen, um in Ruhe gelassen zu werden.
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
        patronDetails = null
    }

    companion object {
        /** Einmalige, nicht verbrauchbare Käufe (Play Console). */
        const val PRODUCT_ID = "remove_ads"
        const val PATRON_ID = "patron_pack"
        private const val TAG = "BillingManager"
    }
}
