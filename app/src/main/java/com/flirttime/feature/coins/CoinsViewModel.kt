package com.flirttime.feature.coins

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.flirttime.Resource
import com.flirttime.feature.main.profile.UserProfileViewModel
import com.flirttime.feature.subscription.PlayBillingManager
import com.flirttime.feature.subscription.PremiumStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private fun formatServerPrice(price: String?, currency: String?): String {
    val amount = price?.toDoubleOrNull() ?: return price.orEmpty()
    return try {
        val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
        if (!currency.isNullOrBlank()) format.currency = Currency.getInstance(currency)
        format.format(amount)
    } catch (_: Exception) {
        "$currency $price".trim()
    }
}

data class CoinPackOption(
    val pack: CoinPackDto,
    val product: ProductDetails?
) {
    val displayPrice: String
        get() = product?.oneTimePurchaseOfferDetails?.formattedPrice
            ?: formatServerPrice(pack.price, pack.currency)

    val isPopular: Boolean get() = pack.tag.equals("popular", ignoreCase = true)
    val isBestValue: Boolean get() = pack.tag.equals("best_value", ignoreCase = true)
}

sealed class CoinShopState {
    data object Loading : CoinShopState()
    data object Loaded : CoinShopState()
    data class Failed(val message: String) : CoinShopState()
}

sealed class CoinPurchaseEvent {
    data class Purchased(val coins: Int, val message: String) : CoinPurchaseEvent()
    data object Pending : CoinPurchaseEvent()
    data class Failed(val message: String) : CoinPurchaseEvent()
}

class CoinsViewModel(
    private val coinRepository: CoinRepository,
    private val billing: PlayBillingManager,
    val coinWallet: CoinWallet,
    private val userProfile: UserProfileViewModel
) : ViewModel() {

    private val _state = MutableStateFlow<CoinShopState>(CoinShopState.Loading)
    val state: StateFlow<CoinShopState> = _state.asStateFlow()

    private val _options = MutableStateFlow<List<CoinPackOption>>(emptyList())
    val options: StateFlow<List<CoinPackOption>> = _options.asStateFlow()

    private val _purchasingPackId = MutableStateFlow<Int?>(null)
    val purchasingPackId: StateFlow<Int?> = _purchasingPackId.asStateFlow()

    private val _events = MutableSharedFlow<CoinPurchaseEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<CoinPurchaseEvent> = _events.asSharedFlow()

    val isPremium: Boolean get() = PremiumStatus.isPremium(userProfile)

    fun load() {
        viewModelScope.launch { coinWallet.refresh() }
        viewModelScope.launch {
            _state.value = CoinShopState.Loading
            when (val result = coinRepository.getPackages()) {
                is Resource.Success -> {
                    val packs = result.data
                    val products = billing.inAppProducts(packs.mapNotNull { it.storeProductId })
                    val byId = products.associateBy { it.productId }
                    _options.value = packs.map { pack ->
                        CoinPackOption(pack, pack.storeProductId?.let { byId[it] })
                    }
                    _state.value = CoinShopState.Loaded
                }
                is Resource.Error -> _state.value = CoinShopState.Failed(
                    result.message.ifBlank { "Couldn't load coin packs. Try again." }
                )
                Resource.Loading -> Unit
            }
        }
    }

    fun purchase(activity: Activity, option: CoinPackOption) {
        if (_purchasingPackId.value != null) return
        val product = option.product
        val packId = option.pack.id
        if (product == null || packId == null) {
            _events.tryEmit(CoinPurchaseEvent.Failed(PlayBillingManager.PRODUCT_UNAVAILABLE))
            return
        }
        _purchasingPackId.value = packId
        viewModelScope.launch {
            when (val outcome = billing.purchase(activity, product)) {
                is PlayBillingManager.PurchaseOutcome.Purchased -> verifyAndCredit(option.pack, outcome.purchase)
                PlayBillingManager.PurchaseOutcome.Pending -> {
                    _purchasingPackId.value = null
                    _events.tryEmit(CoinPurchaseEvent.Pending)
                }
                PlayBillingManager.PurchaseOutcome.Cancelled -> _purchasingPackId.value = null
                is PlayBillingManager.PurchaseOutcome.Failed -> {
                    _purchasingPackId.value = null
                    _events.tryEmit(CoinPurchaseEvent.Failed(outcome.message))
                }
            }
        }
    }

    private suspend fun verifyAndCredit(pack: CoinPackDto, purchase: com.android.billingclient.api.Purchase) {
        val body = CoinPurchaseVerifyRequest(
            packageId = pack.id ?: 0,
            productId = purchase.products.firstOrNull() ?: pack.storeProductId.orEmpty(),
            transactionId = purchase.orderId ?: purchase.purchaseToken,
            purchaseToken = purchase.purchaseToken,
            packageName = billing.packageName
        )
        when (val result = coinRepository.verifyPurchase(body)) {
            is Resource.Success -> {
                _purchasingPackId.value = null
                val credited = result.data.coinsCredited ?: pack.totalCoins
                result.data.walletBalance?.let { coinWallet.update(it) } ?: coinWallet.refresh()
                billing.consume(purchase)
                _events.tryEmit(
                    CoinPurchaseEvent.Purchased(
                        coins = credited,
                        message = "Added $credited coins to your wallet."
                    )
                )
            }
            is Resource.Error -> {
                _purchasingPackId.value = null
                coinWallet.refresh()
                _events.tryEmit(
                    CoinPurchaseEvent.Failed(
                        result.message.ifBlank { "Your purchase went through but the coins haven't arrived yet. They'll be added shortly." }
                    )
                )
            }
            Resource.Loading -> Unit
        }
    }
}
