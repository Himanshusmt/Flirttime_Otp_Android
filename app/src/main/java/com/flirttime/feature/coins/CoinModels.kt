package com.flirttime.feature.coins

import com.google.gson.annotations.SerializedName

data class CoinPackDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("coins") val coins: Int? = null,
    @SerializedName("price") val price: String? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName(value = "bonus_coins", alternate = ["bonusCoins"]) val bonusCoins: Int? = null,
    @SerializedName(value = "is_active", alternate = ["isActive"]) val isActive: Boolean? = null,
    @SerializedName(value = "apple_product_id", alternate = ["appleProductId"]) val appleProductId: String? = null,
    @SerializedName(value = "google_product_id", alternate = ["googleProductId"]) val googleProductId: String? = null,
    @SerializedName(value = "sort_order", alternate = ["sortOrder"]) val sortOrder: Int? = null,
    @SerializedName("tag") val tag: String? = null
) {
    val storeProductId: String?
        get() = googleProductId?.takeIf { it.isNotBlank() } ?: appleProductId?.takeIf { it.isNotBlank() }

    val totalCoins: Int
        get() = (coins ?: 0) + (bonusCoins ?: 0)
}

data class CoinPurchaseVerifyRequest(
    @SerializedName("platform") val platform: String = "android",
    @SerializedName("packageId") val packageId: Int,
    @SerializedName("productId") val productId: String,
    @SerializedName("transactionId") val transactionId: String,
    @SerializedName("purchaseToken") val purchaseToken: String,
    @SerializedName("packageName") val packageName: String
)

data class CoinPurchaseData(
    @SerializedName("alreadyProcessed") val alreadyProcessed: Boolean? = null,
    @SerializedName("purchaseId") val purchaseId: String? = null,
    @SerializedName("coinsCredited") val coinsCredited: Int? = null,
    @SerializedName("baseCoins") val baseCoins: Int? = null,
    @SerializedName("bonusCoins") val bonusCoins: Int? = null,
    @SerializedName("previousBalance") val previousBalance: Int? = null,
    @SerializedName("currentBalance") val currentBalance: Int? = null,
    @SerializedName("balance") val balance: Int? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("acknowledged") val acknowledged: Boolean? = null
) {
    val walletBalance: Int? get() = currentBalance ?: balance
}
