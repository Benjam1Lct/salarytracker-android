package com.benjamin.salarytracker

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Gère les abonnements via Google Play Billing.
 *
 *  - Produit abonnement : [SUBSCRIPTION_ID] (créé dans la Play Console).
 *  - À chaque achat : acquittement + appel de la Cloud Function `verifyPurchase`
 *    qui vérifie l'achat côté serveur et écrit l'entitlement en RTDB.
 *
 * L'état "abonné" réel est lu depuis la RTDB (`subscriptions/{uid}/active`) par le
 * ViewModel — ici on ne fait que déclencher l'achat et la vérification serveur.
 */
class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        const val SUBSCRIPTION_ID = "salarytracker_pro"
    }

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails

    /** Prix formaté à afficher (ex. "2,99 €/mois"), ou null si non chargé. */
    private val _formattedPrice = MutableStateFlow<String?>(null)
    val formattedPrice: StateFlow<String?> = _formattedPrice

    /** Callback déclenché après une vérification serveur réussie. */
    var onPurchaseVerified: (() -> Unit)? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    fun connect() {
        if (billingClient.isReady) {
            queryProduct()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    queryExistingPurchases()
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SUBSCRIPTION_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            ).build()

        billingClient.queryProductDetailsAsync(params) { result, products ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && products.isNotEmpty()) {
                val details = products.first()
                _productDetails.value = details
                _formattedPrice.value = details.subscriptionOfferDetails
                    ?.firstOrNull()
                    ?.pricingPhases?.pricingPhaseList?.firstOrNull()
                    ?.formattedPrice
            }
        }
    }

    /** Relance la vérif des achats déjà possédés (restauration au démarrage). */
    private fun queryExistingPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { handlePurchase(it) }
            }
        }
    }

    /** Lance le flux d'achat depuis une Activity. */
    fun launchPurchase(activity: Activity) {
        val details = _productDetails.value ?: return
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            ).build()
        billingClient.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        // 1. Acquitter l'achat (obligatoire sous 3 jours sinon remboursement auto).
        if (!purchase.isAcknowledged) {
            billingClient.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken).build()
            ) { /* ack result */ }
        }

        // 2. Vérification serveur → écrit l'entitlement en RTDB.
        verifyOnServer(purchase.purchaseToken)
    }

    private fun verifyOnServer(purchaseToken: String) {
        val functions = com.google.firebase.functions.FirebaseFunctions.getInstance("europe-west1")
        functions.getHttpsCallable("verifyPurchase")
            .call(mapOf("purchaseToken" to purchaseToken, "productId" to SUBSCRIPTION_ID))
            .addOnSuccessListener { onPurchaseVerified?.invoke() }
    }

    fun end() {
        try { billingClient.endConnection() } catch (_: Exception) {}
    }
}
