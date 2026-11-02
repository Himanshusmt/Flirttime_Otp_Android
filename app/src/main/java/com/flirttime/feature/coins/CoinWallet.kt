package com.flirttime.feature.coins

import com.flirttime.Resource
import com.flirttime.feature.main.matches.MatchesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * iOS `CoinWallet.shared` — one wallet balance for Profile, Discover, gifts, and Super Like.
 */
class CoinWallet(
    private val matchesRepository: MatchesRepository
) {
    private val _balance = MutableStateFlow(0)
    val balance: StateFlow<Int> = _balance.asStateFlow()

    val current: Int get() = _balance.value

    fun update(coins: Int) {
        _balance.value = coins.coerceAtLeast(0)
    }

    suspend fun refresh() {
        when (val result = matchesRepository.getWalletBalance().first { it !is Resource.Loading }) {
            is Resource.Success -> update(result.data.balance)
            else -> Unit
        }
    }
}
