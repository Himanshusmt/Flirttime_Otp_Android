package com.flirttime.feature.profile.otherUser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.feature.main.MainRepository
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

class OtherUserProfileViewModel(
    private val mainRepository: MainRepository,
    private val momentsRepository: MomentsRepository,
    private val matchesRepository: MatchesRepository
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

    fun submitAction(targetUserId: String, action: String) {
        viewModelScope.launch {
            mainRepository.submitDiscoveryAction(targetUserId = targetUserId, action = action).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        _actionEvent.emit(action to resource.data)
                    }
                    is Resource.Error -> {
                        _errorMessage.emit(resource.message ?: "Failed to perform action")
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
