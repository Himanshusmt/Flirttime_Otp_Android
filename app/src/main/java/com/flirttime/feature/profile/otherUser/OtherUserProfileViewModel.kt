package com.flirttime.feature.profile.otherUser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.feature.coins.CoinWallet
import com.flirttime.feature.main.MainRepository
import com.flirttime.feature.main.matches.FreeTierLimitsStore
import com.flirttime.feature.main.matches.MatchesRepository
import com.flirttime.feature.moments.MomentDto
import com.flirttime.feature.moments.MomentsRepository
import com.flirttime.feature.profile.DiscoveryActionResponseData
import com.flirttime.feature.profile.UserSearchItemDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class OtherUserActionGate {
    data object LikeLimit : OtherUserActionGate()
    data object InsufficientCoins : OtherUserActionGate()
}

class OtherUserProfileViewModel(
    private val mainRepository: MainRepository,
    private val momentsRepository: MomentsRepository,
    private val matchesRepository: MatchesRepository,
    private val limitsStore: FreeTierLimitsStore,
    private val coinWallet: CoinWallet
) : ViewModel() {

    private val _profileState = MutableStateFlow<Resource<UserSearchItemDto>>(Resource.Loading)
    val profileState: StateFlow<Resource<UserSearchItemDto>> = _profileState.asStateFlow()

    private val _momentsState = MutableStateFlow<List<MomentDto>>(emptyList())
    val momentsState: StateFlow<List<MomentDto>> = _momentsState.asStateFlow()

    private val _actionEvent = MutableSharedFlow<Pair<String, DiscoveryActionResponseData?>>()
    val actionEvent: SharedFlow<Pair<String, DiscoveryActionResponseData?>> = _actionEvent.asSharedFlow()

    private val _blockResult = MutableSharedFlow<Resource<Unit>>()
    val blockResult: SharedFlow<Resource<Unit>> = _blockResult.asSharedFlow()

    private val _reportResult = MutableSharedFlow<Resource<Unit>>()
    val reportResult: SharedFlow<Resource<Unit>> = _reportResult.asSharedFlow()

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private val _actionGate = MutableSharedFlow<OtherUserActionGate>()
    val actionGate: SharedFlow<OtherUserActionGate> = _actionGate.asSharedFlow()

    fun loadProfile(userId: String, initialData: UserSearchItemDto? = null) {
        if (initialData != null) {
            _profileState.value = Resource.Success(initialData)
        }

        viewModelScope.launch {
            mainRepository.getOtherUserProfile(userId).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val targetUser = resource.data.targetUser
                        _profileState.value = Resource.Success(targetUser)
                        if (!targetUser.moments.isNullOrEmpty()) {
                            _momentsState.value = targetUser.moments
                        }
                    }
                    is Resource.Error -> {
                        if (initialData == null) {
                            _profileState.value = Resource.Error(
                                message = resource.message,
                                code = resource.code,
                                cause = resource.cause,
                                errorBody = resource.errorBody
                            )
                        }
                    }
                    is Resource.Loading -> {
                        if (initialData == null) {
                            _profileState.value = Resource.Loading
                        }
                    }
                }
            }
        }

        loadUserMoments(userId)
    }

    private fun loadUserMoments(userId: String) {
        viewModelScope.launch {
            momentsRepository.getUserMoments(userId = userId, page = 1, limit = 20).collect { resource ->
                if (resource is Resource.Success && resource.data.moments?.isNotEmpty() == true) {
                    _momentsState.value = resource.data.moments
                }
            }
        }
    }

    fun sendConnectionRequest(targetUserId: String, reason: ConnectReason?) {
        if (!limitsStore.canLike) {
            viewModelScope.launch { _actionGate.emit(OtherUserActionGate.LikeLimit) }
            return
        }
        val reasonText = reason?.apiValue
        viewModelScope.launch {
            matchesRepository.sendConnectionRequest(targetUserId, reasonText).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        limitsStore.refresh()
                        val payload = resource.data
                        val mapped = DiscoveryActionResponseData(
                            action = "like",
                            targetUserId = payload?.targetUserId ?: targetUserId,
                            isMatched = payload?.isMatched,
                            matchId = payload?.matchId
                        )
                        _actionEvent.emit("like" to mapped)
                    }
                    is Resource.Error -> {
                        val msg = resource.message.orEmpty()
                        val body = resource.errorBody.orEmpty()
                        val isDailyLimit = resource.code == 429 ||
                            msg.contains("DAILY_LIKE_LIMIT_REACHED", ignoreCase = true) ||
                            body.contains("DAILY_LIKE_LIMIT_REACHED", ignoreCase = true) ||
                            msg.contains("DAILY_SWIPE_LIMIT_REACHED", ignoreCase = true)
                        when {
                            isDailyLimit -> _actionGate.emit(OtherUserActionGate.LikeLimit)
                            else -> _errorMessage.emit(resource.message ?: "Failed to send connection")
                        }
                    }
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    fun submitAction(targetUserId: String, action: String) {
        val normalized = action.lowercase()
        if (normalized == "like" && !limitsStore.canLike) {
            viewModelScope.launch { _actionGate.emit(OtherUserActionGate.LikeLimit) }
            return
        }
        if (normalized == "super_like" && !limitsStore.canSuperLike) {
            viewModelScope.launch { _actionGate.emit(OtherUserActionGate.InsufficientCoins) }
            return
        }
        viewModelScope.launch {
            mainRepository.submitDiscoveryAction(targetUserId = targetUserId, action = action).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        resource.data?.remainingCoins?.let { coinWallet.update(it) }
                        limitsStore.refresh()
                        _actionEvent.emit(action to resource.data)
                    }
                    is Resource.Error -> {
                        val msg = resource.message.orEmpty()
                        val body = resource.errorBody.orEmpty()
                        val isDailyLimit = resource.code == 429 ||
                            msg.contains("DAILY_LIKE_LIMIT_REACHED", ignoreCase = true) ||
                            body.contains("DAILY_LIKE_LIMIT_REACHED", ignoreCase = true) ||
                            msg.contains("DAILY_SWIPE_LIMIT_REACHED", ignoreCase = true)
                        val isCoins = resource.code == 402 || resource.code == 403 ||
                            msg.contains("INSUFFICIENT_COINS", ignoreCase = true) ||
                            body.contains("INSUFFICIENT_COINS", ignoreCase = true)
                        when {
                            isDailyLimit -> _actionGate.emit(OtherUserActionGate.LikeLimit)
                            isCoins -> _actionGate.emit(OtherUserActionGate.InsufficientCoins)
                            else -> _errorMessage.emit(resource.message ?: "Failed to perform action")
                        }
                    }
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    fun blockUser(userId: String, reason: String = "Blocked from Profile") {
        viewModelScope.launch {
            matchesRepository.blockUser(userId = userId, reason = reason).collect { resource ->
                _blockResult.emit(resource)
            }
        }
    }

    fun reportUser(userId: String, reason: String = "Inappropriate profile", description: String? = null) {
        viewModelScope.launch {
            matchesRepository.reportUser(userId = userId, reason = reason, description = description).collect { resource ->
                _reportResult.emit(resource)
            }
        }
    }
}
