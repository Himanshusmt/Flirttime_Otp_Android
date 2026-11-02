package com.flirttime.feature.main.matches

import com.flirttime.Resource
import com.flirttime.core.database.SessionManager
import com.flirttime.feature.coins.CoinWallet
import com.flirttime.feature.main.explore.ExploreTab
import com.flirttime.feature.main.profile.UserProfileViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * iOS `FreeTierLimitsStore` — GET /api/v1/users/{id}/limits.
 */
class FreeTierLimitsStore(
    private val matchesRepository: MatchesRepository,
    private val sessionManager: SessionManager,
    private val coinWallet: CoinWallet,
    private val userProfile: UserProfileViewModel
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()

    private val _limits = MutableStateFlow<UserLimitsResponseData?>(null)
    val limits: StateFlow<UserLimitsResponseData?> = _limits.asStateFlow()

    @Volatile
    private var skipRefreshUntilMs: Long = 0L

    val isPremium: Boolean
        get() = _limits.value?.isSubscribed == true ||
            userProfile.membershipState.value?.isCurrentlyActive == true ||
            userProfile.userProfileState.value?.isSubscribed == true

    val canLike: Boolean
        get() {
            if (isPremium) return true
            val remaining = _limits.value?.likes?.remaining ?: return true
            return remaining > 0
        }

    val hasFreeSuperLike: Boolean
        get() = _limits.value?.superLikes?.hasFree == true ||
            (_limits.value?.superLikes?.freeRemaining ?: 0) > 0

    val canSuperLike: Boolean
        get() {
            if (hasFreeSuperLike) return true
            val cost = _limits.value?.superLikes?.costInCoins ?: DEFAULT_SUPER_LIKE_COST
            val coins = _limits.value?.superLikes?.userCoins ?: coinWallet.current
            return coins >= cost
        }

    fun isTabLocked(tab: ExploreTab): Boolean {
        val tabs = _limits.value?.tabs
        return when (tab) {
            ExploreTab.LIKES -> tabs?.likesTabLocked ?: !isPremium
            ExploreTab.COMPLIMENTS -> tabs?.complimentsTabLocked ?: !isPremium
            ExploreTab.YOU_LIKED -> tabs?.youLikedTabLocked ?: false
            ExploreTab.MATCHES -> tabs?.matchesTabLocked ?: false
        }
    }

    fun canStartChat(isMatched: Boolean): Boolean {
        if (isMatched || isPremium) return true
        val chat = _limits.value?.chat
        if (chat?.canChatWithoutMatch == true) return true
        if (chat?.requiresMatch == false) return true
        return false
    }

    fun refresh() {
        scope.launch { refreshNow() }
    }

    suspend fun refreshNow() {
        refreshMutex.withLock {
            if (!sessionManager.isLoggedIn()) {
                _limits.value = null
                return
            }
            if (skipRefreshUntilMs > System.currentTimeMillis()) return
            val userId = sessionManager.getUserId()
            if (userId.isBlank()) return

            when (val result = matchesRepository.getUserLimits(userId).first { it !is Resource.Loading }) {
                is Resource.Success -> {
                    skipRefreshUntilMs = 0L
                    apply(result.data)
                }
                is Resource.Error -> {
                    val status = result.code ?: 0
                    if (status == 0 || status == 400 || status == 404) {
                        skipRefreshUntilMs = System.currentTimeMillis() + SKIP_MS
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun apply(limits: UserLimitsResponseData) {
        _limits.value = limits
        limits.isSubscribed?.let { userProfile.applySubscriptionStatus(it) }
        limits.superLikes?.userCoins?.let { coinWallet.update(it) }
    }

    companion object {
        const val DEFAULT_SUPER_LIKE_COST = 20
        private const val SKIP_MS = 120_000L
    }
}
