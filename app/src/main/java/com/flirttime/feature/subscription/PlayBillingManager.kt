package com.flirttime.feature.subscription

import android.app.Activity
import android.content.Context
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
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/**
 * Google Play Billing wrapper – the Android counterpart of iOS `StoreManager` (StoreKit 2).
 * Handles product lookup, the purchase sheet, restore and acknowledging a purchase once the backend verified it.
 */
class PlayBillingManager(context: Context) : PurchasesUpdatedListener {

    sealed interface PurchaseOutcome {
        data class Purchased(val purchase: Purchase) : PurchaseOutcome
        data object Pending : PurchaseOutcome
        data object Cancelled : PurchaseOutcome
        data class Failed(val message: String) : PurchaseOutcome
    }

    private val appContext = context.applicationContext
    val packageName: String get() = appContext.packageName
    private val connectMutex = Mutex()
    private var pendingPurchase: CompletableDeferred<PurchaseOutcome>? = null

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private suspend fun ensureConnected(): Boolean = connectMutex.withLock {
        if (client.isReady) return@withLock true
        suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() = Unit
            })
        }
    }

    /** iOS `StoreManager.products(for:)`. Empty when Play can't be reached or the SKUs aren't published. */
    suspend fun products(ids: List<String>): List<ProductDetails> {
        if (ids.isEmpty() || !ensureConnected()) return emptyList()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ids.distinct().map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
            )
            .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "queryProductDetails failed: ${result.billingResult.debugMessage}")
        }
        return result.productDetailsList.orEmpty()
    }

    /** iOS `StoreManager.purchase(_:)`. */
    suspend fun purchase(activity: Activity, details: ProductDetails): PurchaseOutcome {
        if (!ensureConnected()) return PurchaseOutcome.Failed("Google Play isn't available on this device right now.")
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: return PurchaseOutcome.Failed(PRODUCT_UNAVAILABLE)

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val deferred = CompletableDeferred<PurchaseOutcome>()
        pendingPurchase = deferred
        val launch = client.launchBillingFlow(activity, flowParams)
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingPurchase = null
            return outcomeFor(launch)
        }
        return deferred.await()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val deferred = pendingPurchase
        pendingPurchase = null
        val outcome: PurchaseOutcome = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            val purchase = purchases?.firstOrNull()
            when {
                purchase == null -> PurchaseOutcome.Cancelled
                purchase.purchaseState == Purchase.PurchaseState.PENDING -> PurchaseOutcome.Pending
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED -> PurchaseOutcome.Purchased(purchase)
                else -> PurchaseOutcome.Cancelled
            }
        } else {
            outcomeFor(result)
        }
        deferred?.complete(outcome)
    }

    /** iOS `StoreManager.restorePurchases()`: the newest active Premium purchase on this Google account. */
    suspend fun restore(productIds: Set<String>): Purchase? {
        if (!ensureConnected()) return null
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        )
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return null
        return result.purchasesList
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .filter { productIds.isEmpty() || it.products.any(productIds::contains) }
            .maxByOrNull { it.purchaseTime }
    }

    /** Google refunds un-acknowledged purchases after 3 days, so only acknowledge once the backend activated Premium. */
    suspend fun acknowledge(purchase: Purchase) {
        if (purchase.isAcknowledged || !ensureConnected()) return
        val result = client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "acknowledgePurchase failed: ${result.debugMessage}")
        }
    }

    private fun outcomeFor(result: BillingResult): PurchaseOutcome = when (result.responseCode) {
        BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseOutcome.Cancelled
        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
            PurchaseOutcome.Failed("You already own this plan. Tap Restore to activate it.")
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> PurchaseOutcome.Failed(PRODUCT_UNAVAILABLE)
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ->
            PurchaseOutcome.Failed("Google Play isn't available on this device right now.")
        else -> PurchaseOutcome.Failed(result.debugMessage.ifBlank { "Purchase failed. Please try again." })
    }

    companion object {
        private const val TAG = "PlayBillingManager"
        const val PRODUCT_UNAVAILABLE = "This item isn't available on Google Play right now."
    }
}
