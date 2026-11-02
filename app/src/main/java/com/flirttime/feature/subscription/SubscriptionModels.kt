package com.flirttime.feature.subscription

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import java.util.Locale
import kotlin.math.max

/** iOS `PlanFeature`. `image` is either an SF Symbol name or a (relative / absolute) image path. */
data class PlanFeatureDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("image") val image: String? = null
)

/** Android port of iOS `SubscriptionPlanData` (`GET api/v1/subscriptions`). */
data class SubscriptionPlanDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("price") val price: String? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("duration_days") val durationDays: Int? = null,
    @SerializedName("is_active") val isActive: Boolean? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("bonus_coins") val bonusCoins: Int? = null,
    @SerializedName("is_recurring") val isRecurring: Boolean? = null,
    @SerializedName("billing_cycle") val billingCycle: String? = null,
    @SerializedName("trial_period") val trialPeriod: String? = null,
    @SerializedName("features") val features: List<PlanFeatureDto>? = null,
    @SerializedName("google_product_id") val googleProductId: String? = null,
    @SerializedName("apple_product_id") val appleProductId: String? = null,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("sort_order") val sortOrder: Int? = null,
    @SerializedName("is_popular") val isPopular: Boolean? = null,
    @SerializedName("icon_url") val iconUrl: String? = null
) {

    /** "1 Week", "6 Months", "1 Year"… */
    val durationTitle: String
        get() {
            val days = durationDays
            if (days == null || days <= 0) return billingCycle?.replaceFirstChar { it.uppercase() }.orEmpty()
            return when {
                days < 28 -> if (days % 7 == 0) plural(days / 7, "Week") else plural(days, "Day")
                days < 360 -> plural(Math.round(days / 30.0).toInt(), "Month")
                else -> plural(Math.round(days / 365.0).toInt(), "Year")
            }
        }

    /** "week", "month", "6 months", "year" for "₹499 every month". */
    val renewalPeriod: String
        get() {
            val title = durationTitle.lowercase(Locale.ROOT)
            return if (title.startsWith("1 ")) title.removePrefix("1 ") else title
        }

    val weeks: Double
        get() = max(durationDays ?: 7, 1) / 7.0

    private fun plural(count: Int, unit: String) = "$count $unit${if (count == 1) "" else "s"}"
}

/** iOS `SubscriptionPurchaseResponse` – the backend may set either `status` or `success`. */
data class SubscriptionPurchaseResult(
    val isSuccessful: Boolean,
    val message: String,
    val isActive: Boolean?,
    val walletBalance: Int?
) {
    companion object {
        fun from(root: JsonObject): SubscriptionPurchaseResult {
            fun flag(key: String): Boolean? =
                root.get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() }

            val successful = flag("status") == true || flag("success") == true
            val message = root.get("message")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            val isActive = data?.get("isActive")?.takeIf { it.isJsonPrimitive }
                ?.let { runCatching { it.asBoolean }.getOrNull() }
            val balance = data?.get("currentBalance")?.takeIf { it.isJsonPrimitive }
                ?.let { runCatching { it.asInt }.getOrNull() }
            return SubscriptionPurchaseResult(successful, message, isActive, balance)
        }
    }
}
