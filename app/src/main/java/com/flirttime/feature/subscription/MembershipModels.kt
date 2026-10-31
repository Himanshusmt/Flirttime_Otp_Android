package com.flirttime.feature.subscription

import com.flirttime.core.common.IsoDate
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Calendar
import java.util.Currency
import java.util.Date
import java.util.Locale

/**
 * Active (or recent) Premium membership for the signed-in user.
 *
 * Android port of iOS `MySubscriptionData` (`GET /api/v1/subscriptions/my`).
 * The backend payload shape varies (`plan` / `subscription` blocks or a flat object), so parsing is
 * deliberately tolerant and mirrors the iOS decoder key-for-key.
 */
data class MySubscription(
    val id: String?,
    val planName: String?,
    val productId: String?,
    val platform: String?,
    val status: String?,
    val isActive: Boolean?,
    val autoRenew: Boolean?,
    val price: String?,
    val currency: String?,
    val billingCycle: String?,
    val purchasedAt: String?,
    val expiresAt: String?,
    val nextRenewalDate: String?,
    val bonusCoins: Int?,
    val remainingValidity: RemainingValidity?,
    val benefitTexts: List<String>?
) {

    data class RemainingValidity(val days: Int?, val hours: Int?, val minutes: Int?)

    /** Enough fields to treat this as a real membership payload (vs. an empty `{}`). */
    val hasSubscriptionContent: Boolean
        get() = planName != null || productId != null || status != null ||
            isActive != null || expiresAt != null || id != null

    val isCurrentlyActive: Boolean
        get() {
            isActive?.let { return it }
            status?.let {
                val value = it.lowercase(Locale.ROOT)
                return value == "active" || value == "subscribed" || value == "trialing"
            }
            val end = parseDate(expiresAt) ?: return false
            return end.after(Date())
        }

    val displayPlanName: String
        get() {
            if (!planName.isNullOrBlank()) return planName
            if (!billingCycle.isNullOrBlank()) return billingCycle.replaceFirstChar { it.uppercase() }
            return "Premium"
        }

    val displayStatus: String
        get() {
            if (isCurrentlyActive) return "Active"
            if (!status.isNullOrBlank()) return status.replaceFirstChar { it.uppercase() }
            return "Inactive"
        }

    private val displayPrice: String?
        get() {
            val raw = price?.takeIf { it.isNotBlank() } ?: return null
            val amount = raw.toBigDecimalOrNull()
            val code = currency?.takeIf { it.isNotBlank() }
            if (amount != null && code != null) {
                runCatching {
                    val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
                    format.currency = Currency.getInstance(code.uppercase(Locale.ROOT))
                    if (amount.scale() <= 0 || amount.stripTrailingZeros().scale() <= 0) {
                        format.maximumFractionDigits = 0
                    }
                    return format.format(amount)
                }
            }
            return if (!currency.isNullOrBlank()) "$currency $raw" else raw
        }

    val priceLine: String?
        get() {
            val formatted = displayPrice ?: return null
            val cycle = billingCycle?.trim().orEmpty()
            return if (cycle.isNotEmpty()) "$formatted / ${cycle.lowercase(Locale.ROOT)}" else formatted
        }

    val formattedExpiry: String?
        get() = formatDateTime(expiresAt)

    val formattedPurchaseDate: String?
        get() = formatDateTime(purchasedAt)

    val formattedNextRenewal: String?
        get() = formatDateTime(nextRenewalDate ?: if (autoRenew == true) expiresAt else null)

    /** Whole days until expiry. Prefers server `remainingValidity.days`. */
    val daysRemaining: Int?
        get() {
            remainingValidity?.days?.let { return maxOf(it, 0) }
            val end = parseDate(expiresAt) ?: return null
            val startOfToday = startOfDay(Date())
            val startOfEnd = startOfDay(end)
            val diff = (startOfEnd.time - startOfToday.time) / DAY_MILLIS
            return maxOf(diff.toInt(), 0)
        }

    /** Show the countdown only in the final 5 days before expiry. */
    private val shouldShowDaysRemaining: Boolean
        get() {
            if (!isCurrentlyActive) return false
            val days = daysRemaining ?: return false
            return days <= 5
        }

    val daysRemainingTitle: String?
        get() {
            if (!shouldShowDaysRemaining) return null
            remainingValidity?.let { remaining ->
                remaining.days?.takeIf { it > 0 }?.let { return "$it day${if (it == 1) "" else "s"} left" }
                remaining.hours?.takeIf { it > 0 }?.let { return "$it hour${if (it == 1) "" else "s"} left" }
                remaining.minutes?.takeIf { it > 0 }?.let { return "$it min${if (it == 1) "" else "s"} left" }
            }
            val days = daysRemaining ?: return null
            if (days == 0) return "Ends today"
            return "$days day${if (days == 1) "" else "s"} left"
        }

    val daysRemainingSubtitle: String
        get() {
            if (autoRenew == true) return "Auto-renewal is on — renews before this ends"
            return if (isCurrentlyActive) "Subscription ends soon" else "Subscription ended"
        }

    val autoRenewalLine: String
        get() {
            if (autoRenew == true) {
                val next = formattedNextRenewal
                return if (next != null) "Auto-renewal On · Next on $next" else "Auto-renewal On"
            }
            return "Auto-renewal Off"
        }

    val renewalSummary: String
        get() {
            if (autoRenew == true) {
                val next = formattedNextRenewal
                return if (next != null) "Renews on $next" else "Renews automatically"
            }
            val expiry = formattedExpiry ?: return "Active membership"
            return if (isCurrentlyActive) "Expires on $expiry" else "Ended on $expiry"
        }

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        val DEFAULT_BENEFITS = listOf(
            "Unlimited Likes",
            "See who likes you",
            "Free chats & compliments",
            "Match without ads",
            "Priority in Discover",
            "Weekly Super Likes"
        )

        /**
         * Parses the full response of `GET /api/v1/subscriptions/my`.
         * Preferred shape: `{ data: { isSubscribed, plan, subscription, benefits } }`.
         * Returns `null` when the user has no subscription record.
         */
        fun fromResponse(root: JsonObject): MySubscription? {
            root.obj("data")?.let { data ->
                fromObject(data)?.takeIf { it.hasSubscriptionContent }?.let { return it }
            }
            listOf("subscription", "membership").forEach { key ->
                root.obj(key)?.let { nested ->
                    fromObject(nested)?.takeIf { it.hasSubscriptionContent }?.let { return it }
                }
            }
            return fromObject(root)?.takeIf { it.hasSubscriptionContent }
        }

        private fun fromObject(c: JsonObject): MySubscription? {
            val plan = c.obj("plan")
            val detail = c.obj("subscription")

            val id = detail?.string("id", "subscriptionId")
                ?: c.string("id", "subscriptionId")

            val planName = plan?.string("name", "title", "planName")
                ?: c.string("planName", "name", "title", "plan_name")
            val productId = detail?.string("productId", "product_id")
                ?: plan?.string("productId", "product_id", "googleProductId", "google_product_id", "appleProductId", "apple_product_id")
                ?: c.string("productId", "product_id", "googleProductId", "google_product_id", "appleProductId", "apple_product_id")
            val platform = detail?.string("platform")
                ?: c.string("platform", "store", "paymentGateway", "payment_gateway")
            val status = detail?.string("status") ?: c.string("status")

            val topLevelActive = c.bool("isActive", "is_active", "isSubscribed", "is_subscribed")
            val expired = detail?.bool("isExpired", "is_expired") ?: c.bool("isExpired", "is_expired")
            val isActive: Boolean? = when {
                topLevelActive != null -> topLevelActive
                expired != null -> !expired
                status != null -> status.lowercase(Locale.ROOT).let { it == "active" || it == "subscribed" || it == "trialing" }
                else -> null
            }

            val autoRenew = detail?.bool("autoRenewing", "auto_renewing", "autoRenew", "auto_renew")
                ?: c.bool(
                    "autoRenew", "auto_renew", "autoRenewing", "auto_renewing",
                    "willAutoRenew", "will_auto_renew", "isRecurring", "is_recurring"
                )

            val price = plan?.string("price")?.takeIf { it.isNotBlank() }
                ?: c.string("price", "paidAmount", "paid_amount", "amount")
            val currency = plan?.string("currency") ?: c.string("currency")
            val billingCycle = plan?.string("billingCycle", "billing_cycle")
                ?: c.string("billingCycle", "billing_cycle", "duration", "period")

            val purchasedAt = detail?.string("startsAt", "starts_at", "startDate", "start_date")
                ?: c.string(
                    "purchasedAt", "purchaseDate", "purchase_date", "startDate", "start_date",
                    "startsAt", "starts_at", "createdAt", "created_at"
                )
            val expiresAt = detail?.string("expiresAt", "expires_at", "endDate", "end_date")
                ?: c.string(
                    "expiresAt", "expirationDate", "expiration_date", "endDate", "end_date",
                    "renewsAt", "renews_at", "nextBillingDate", "next_billing_date"
                )
            val nextRenewalDate = detail?.string("nextRenewalDate", "next_renewal_date")
                ?: c.string("nextRenewalDate", "next_renewal_date", "renewsAt", "renews_at")

            val bonusCoins = plan?.int("includedCoins", "included_coins", "bonusCoins", "bonus_coins")
                ?: c.int("bonusCoins", "bonus_coins", "includedCoins", "included_coins")

            val remaining = (detail?.obj("remainingValidity") ?: detail?.obj("remaining_validity")
                ?: c.obj("remainingValidity") ?: c.obj("remaining_validity"))
                ?.let { RemainingValidity(it.int("days"), it.int("hours"), it.int("minutes")) }

            val benefits = c.array("benefits")
                ?.mapNotNull { element ->
                    when {
                        element.isJsonPrimitive -> element.asString
                        element.isJsonObject -> element.asJsonObject.string("text")
                        else -> null
                    }
                }
                ?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }

            return MySubscription(
                id = id,
                planName = planName,
                productId = productId,
                platform = platform,
                status = status,
                isActive = isActive,
                autoRenew = autoRenew,
                price = price,
                currency = currency,
                billingCycle = billingCycle,
                purchasedAt = purchasedAt,
                expiresAt = expiresAt,
                nextRenewalDate = nextRenewalDate,
                bonusCoins = bonusCoins,
                remainingValidity = remaining,
                benefitTexts = benefits
            )
        }

        // ---- Date helpers (mirrors iOS `MomentsDate.date(from:)` + `yyyy-MM-dd` fallback) ----

        private fun parseDate(raw: String?): Date? = IsoDate.parse(raw)

        private fun startOfDay(date: Date): Date =
            Calendar.getInstance().apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

        private fun formatDateTime(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val date = parseDate(raw) ?: return raw
            return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(date)
        }
    }
}

// ---- Tolerant JSON accessors (equivalent to iOS `QMKey` container helpers) ----

private fun JsonObject.obj(key: String): JsonObject? =
    get(key)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.array(key: String): List<JsonElement>? =
    get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.toList()

private fun JsonObject.string(vararg keys: String): String? {
    for (key in keys) {
        val element = get(key) ?: continue
        if (element.isJsonPrimitive) {
            val value = element.asString
            if (value.isNotEmpty()) return value
        }
    }
    return null
}

private fun JsonObject.int(vararg keys: String): Int? {
    for (key in keys) {
        val element = get(key) ?: continue
        if (element.isJsonPrimitive) {
            runCatching { element.asNumber.toInt() }.getOrNull()?.let { return it }
            element.asString.toDoubleOrNull()?.let { return it.toInt() }
        }
    }
    return null
}

private fun JsonObject.bool(vararg keys: String): Boolean? {
    for (key in keys) {
        val element = get(key) ?: continue
        if (element.isJsonPrimitive) {
            val prim = element.asJsonPrimitive
            if (prim.isBoolean) return prim.asBoolean
            if (prim.isNumber) return prim.asInt != 0
            when (prim.asString.lowercase(Locale.ROOT)) {
                "true", "1", "yes" -> return true
                "false", "0", "no" -> return false
            }
        }
    }
    return null
}
