package com.flirttime.feature.subscription

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.flirttime.Resource
import com.flirttime.feature.main.profile.UserProfileViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToInt

/** iOS `PremiumViewModel.PlanOption`: a server plan paired with its Google Play product (when Play has one). */
data class PlanOption(
    val plan: SubscriptionPlanDto,
    val product: ProductDetails?
) {
    val id: Int get() = plan.id ?: 0
    val title: String get() = plan.name ?: plan.durationTitle

    private val pricePhase: ProductDetails.PricingPhase?
        get() = product?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()

    private val currencyCode: String
        get() = pricePhase?.priceCurrencyCode ?: plan.currency?.takeIf { it.isNotBlank() } ?: "INR"

    /** Google Play's localized price when available, otherwise the server price. */
    val displayPrice: String
        get() = pricePhase?.formattedPrice
            ?: formatCurrency(plan.price?.toBigDecimalOrNull() ?: BigDecimal.ZERO, plan.currency)

    val weeklyAmount: Double
        get() {
            val total = pricePhase?.let { it.priceAmountMicros / 1_000_000.0 }
                ?: plan.price?.toDoubleOrNull()
                ?: 0.0
            return total / plan.weeks
        }

    val pricePerWeek: String?
        get() {
            if ((plan.durationDays ?: 0) <= 7) return null
            val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
            runCatching { format.currency = Currency.getInstance(currencyCode) }
            format.maximumFractionDigits = 0
            format.minimumFractionDigits = 0
            return "${format.format(weeklyAmount.roundToInt())}/wk"
        }

    /** ("6", "months") for the plan tile. */
    val durationParts: Pair<String, String>
        get() {
            val parts = plan.durationTitle.split(" ", limit = 2)
            if (parts.size != 2) return plan.durationTitle to ""
            return parts[0] to parts[1].lowercase(Locale.ROOT)
        }

    companion object {
        fun formatCurrency(amount: BigDecimal, currency: String?): String {
            val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
            runCatching { format.currency = Currency.getInstance((currency ?: "INR").uppercase(Locale.ROOT)) }
            format.minimumFractionDigits = 0
            format.maximumFractionDigits = 2
            return format.format(amount)
        }
    }
}

sealed interface PremiumState {
    data object Loading : PremiumState
    data object Loaded : PremiumState
    data class Failed(val message: String) : PremiumState
}

sealed interface PurchaseEvent {
    data class Purchased(val message: String) : PurchaseEvent
    data class Restored(val message: String) : PurchaseEvent
    data object Pending : PurchaseEvent
    data class Failed(val message: String) : PurchaseEvent
}

/**
 * iOS `SubscriptionPlanStore`: Premium plans from `subscriptions`, paired with their Play products.
 * Shared across screens so the Premium screen opens with plans and prices ready.
 */
class SubscriptionPlanStore(
    private val repository: SubscriptionRepository,
    private val billing: PlayBillingManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<PremiumState>(PremiumState.Loading)
    val state: StateFlow<PremiumState> = _state.asStateFlow()

    private val _options = MutableStateFlow<List<PlanOption>>(emptyList())
    val options: StateFlow<List<PlanOption>> = _options.asStateFlow()

    private var isLoading = false
    private var hasLoaded = false

    /** Reloads only when nothing has been loaded yet or the last load failed. */
    fun preloadIfNeeded() {
        if (!isLoading && !hasLoaded) preload()
    }

    fun preload() {
        if (isLoading) return
        isLoading = true
        _state.value = PremiumState.Loading
        scope.launch {
            when (val result = repository.getPlans()) {
                is Resource.Success -> {
                    val plans = result.data
                    val products = runCatching {
                        billing.products(plans.mapNotNull { it.googleProductId?.takeIf(String::isNotBlank) })
                    }.getOrDefault(emptyList())
                    val options = plans.map { plan -> PlanOption(plan, products.firstOrNull { it.productId == plan.googleProductId }) }
                    _options.value = options
                    hasLoaded = options.isNotEmpty()
                    _state.value = if (options.isEmpty()) PremiumState.Failed("No plans available right now") else PremiumState.Loaded
                }
                is Resource.Error -> _state.value = PremiumState.Failed(result.message)
                is Resource.Loading -> Unit
            }
            isLoading = false
        }
    }
}

/** Android port of iOS `PremiumViewModel` (plans, selection, purchase, restore, membership refresh). */
class PremiumViewModel(
    private val planStore: SubscriptionPlanStore,
    private val repository: SubscriptionRepository,
    private val billing: PlayBillingManager,
    private val userProfile: UserProfileViewModel
) : ViewModel() {

    val state: StateFlow<PremiumState> = planStore.state
    val options: StateFlow<List<PlanOption>> = planStore.options

    private val _selectedIndex = MutableStateFlow(0)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val _isSubscribed = MutableStateFlow(PremiumStatus.isPremium(userProfile))
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _events = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PurchaseEvent> = _events.asSharedFlow()

    val selectedOption: PlanOption?
        get() = options.value.getOrNull(_selectedIndex.value)

    init {
        // iOS: `selectedIndex = options.firstIndex { isPopular } ?? 0` whenever the plans change.
        viewModelScope.launch {
            options.collect { list ->
                _selectedIndex.value = list.indexOfFirst { it.plan.isPopular == true }.takeIf { it >= 0 } ?: 0
            }
        }
        // The membership card / profile flag are the source of truth for "already Premium".
        viewModelScope.launch {
            userProfile.membershipState.collect { _ -> _isSubscribed.value = PremiumStatus.isPremium(userProfile) }
        }
        viewModelScope.launch {
            userProfile.userProfileState.collect { _ -> _isSubscribed.value = PremiumStatus.isPremium(userProfile) }
        }
    }

    fun select(index: Int) {
        if (!_isPurchasing.value) _selectedIndex.value = index
    }

    /** Uses the plans preloaded earlier; refetches only if that load failed or hasn't run. */
    fun load() {
        planStore.preloadIfNeeded()
        refreshStatus()
    }

    fun retry() {
        planStore.preload()
    }

    fun refreshStatus() {
        userProfile.loadMembership()
        _isSubscribed.value = PremiumStatus.isPremium(userProfile)
    }

    /** iOS `savingsPercent(for:)`: saving per week against the shortest plan; `null` below 5%. */
    fun savingsPercent(option: PlanOption): Int? {
        val base = options.value.minByOrNull { it.plan.durationDays ?: 0 } ?: return null
        if (base.id == option.id || base.weeklyAmount <= 0) return null
        val percent = ((1 - option.weeklyAmount / base.weeklyAmount) * 100).roundToInt()
        return if (percent >= 5) percent else null
    }

    // region Purchase

    fun purchaseSelected(activity: Activity) {
        if (_isPurchasing.value) return
        val option = selectedOption ?: return
        val product = option.product
        if (product == null) {
            _events.tryEmit(PurchaseEvent.Failed(PlayBillingManager.PRODUCT_UNAVAILABLE))
            return
        }
        _isPurchasing.value = true
        viewModelScope.launch {
            when (val outcome = billing.purchase(activity, product)) {
                is PlayBillingManager.PurchaseOutcome.Purchased ->
                    verifyAndActivate(outcome.purchase, restored = false)
                PlayBillingManager.PurchaseOutcome.Pending -> {
                    _isPurchasing.value = false
                    _events.tryEmit(PurchaseEvent.Pending)
                }
                PlayBillingManager.PurchaseOutcome.Cancelled -> _isPurchasing.value = false
                is PlayBillingManager.PurchaseOutcome.Failed -> {
                    _isPurchasing.value = false
                    _events.tryEmit(PurchaseEvent.Failed(outcome.message))
                }
            }
        }
    }

    fun restore() {
        if (_isPurchasing.value) return
        _isPurchasing.value = true
        _isRestoring.value = true
        viewModelScope.launch {
            val ids = options.value.mapNotNull { it.plan.googleProductId }.toSet()
            val purchase = runCatching { billing.restore(ids) }.getOrNull()
            if (purchase == null) {
                resetProgress()
                _events.tryEmit(PurchaseEvent.Failed("We couldn't find an active subscription for this Google account."))
                return@launch
            }
            verifyAndActivate(purchase, restored = true)
        }
    }

    private fun resetProgress() {
        _isPurchasing.value = false
        _isRestoring.value = false
    }

    /** Sends the Play purchase to the backend, which verifies it and activates Premium once per transaction. */
    private suspend fun verifyAndActivate(purchase: Purchase, restored: Boolean) {
        val productId = purchase.products.firstOrNull().orEmpty()
        val result = repository.verifyPurchase(
            productId = productId,
            transactionId = purchase.orderId ?: purchase.purchaseToken,
            purchaseToken = purchase.purchaseToken,
            packageName = billing.packageName
        )
        resetProgress()
        val failureMessage = "Your purchase went through but we couldn't activate it yet. Tap Restore to try again."
        when (result) {
            is Resource.Success -> {
                val response = result.data
                if (!response.isSuccessful) {
                    _events.tryEmit(PurchaseEvent.Failed(failureMessage))
                    return
                }
                billing.acknowledge(purchase)
                _isSubscribed.value = response.isActive ?: true
                // Keep Profile (membership card, premium banner, coins) in sync with the server.
                userProfile.fetchUserProfile()
                userProfile.loadMembership()
                userProfile.loadWalletBalance()
                val message = response.message.ifBlank {
                    if (restored) "Your Premium membership has been restored." else "Welcome to Flirttime Premium!"
                }
                _events.tryEmit(if (restored) PurchaseEvent.Restored(message) else PurchaseEvent.Purchased(message))
            }
            is Resource.Error -> {
                // Play already charged; the purchase stays un-acknowledged so Restore can retry.
                _events.tryEmit(PurchaseEvent.Failed(result.message.ifBlank { failureMessage }))
            }
            is Resource.Loading -> Unit
        }
    }

    // endregion
}

/** iOS `FreeTierLimitsStore.shared.isPremium`: the server `isSubscribed` flag / active membership. */
object PremiumStatus {
    fun isPremium(userProfile: UserProfileViewModel): Boolean =
        userProfile.membershipState.value?.isCurrentlyActive == true ||
            userProfile.userProfileState.value?.isSubscribed == true
}
