package com.flirttime.feature.subscription

import com.flirttime.Resource
import com.flirttime.core.network.safeApiCall
import com.flirttime.core.network.safeRawApiCall
import com.flirttime.feature.main.MainApiService

/** iOS `AppSessionManager.subscriptionPlans()` / `verifySubscriptionPurchase()`. */
class SubscriptionRepository(private val api: MainApiService) {

    /** Active plans in `sort_order` (iOS `SubscriptionPlanStore.preload`). */
    suspend fun getPlans(): Resource<List<SubscriptionPlanDto>> =
        when (val result = safeApiCall { api.getSubscriptionPlans() }) {
            is Resource.Success -> {
                // `safeApiCall` yields `Unit` when the server sends no `data`.
                @Suppress("UNCHECKED_CAST")
                val plans = (result.data as Any?) as? List<SubscriptionPlanDto> ?: emptyList()
                Resource.Success(
                    plans
                        .filter { it.isActive != false }
                        .sortedBy { it.sortOrder ?: Int.MAX_VALUE }
                )
            }
            is Resource.Error -> Resource.Error(
                message = result.message.ifBlank { "Couldn't load plans right now" },
                code = result.code,
                cause = result.cause,
                errorBody = result.errorBody
            )
            is Resource.Loading -> Resource.Loading
        }

    /**
     * `POST api/v1/subscriptions/purchase/verify`. iOS sends `platform`, `productId`, `transactionId` and the
     * App Store JWS; on Android the Play `purchaseToken` takes the JWS' place.
     */
    suspend fun verifyPurchase(
        productId: String,
        transactionId: String,
        purchaseToken: String,
        packageName: String
    ): Resource<SubscriptionPurchaseResult> {
        val body = mapOf(
            "platform" to "android",
            "productId" to productId,
            "transactionId" to transactionId,
            "purchaseToken" to purchaseToken,
            "packageName" to packageName
        )
        return when (val result = safeRawApiCall { api.verifySubscriptionPurchase(body) }) {
            is Resource.Success -> Resource.Success(SubscriptionPurchaseResult.from(result.data))
            is Resource.Error -> result
            is Resource.Loading -> Resource.Loading
        }
    }
}
