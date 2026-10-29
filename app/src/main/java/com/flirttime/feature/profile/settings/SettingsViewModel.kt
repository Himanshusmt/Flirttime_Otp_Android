package com.flirttime.feature.profile.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.core.database.SessionManager
import com.flirttime.feature.auth.AuthUserDto
import com.flirttime.feature.auth.NotificationSettingsDto
import com.flirttime.feature.auth.AuthRepository
import com.flirttime.feature.main.MainRepository
import com.flirttime.feature.profile.BlockedUsersResponseData
import com.flirttime.feature.profile.ReportsResponseData
import com.flirttime.feature.profile.UpdateProfileRequest
import com.flirttime.feature.profile.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val mainRepository: MainRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _notificationSettingsState = MutableStateFlow<Resource<NotificationSettingsDto>?>(null)
    val notificationSettingsState: StateFlow<Resource<NotificationSettingsDto>?> = _notificationSettingsState.asStateFlow()

    private val _updateSettingsState = MutableStateFlow<Resource<NotificationSettingsDto>?>(null)
    val updateSettingsState: StateFlow<Resource<NotificationSettingsDto>?> = _updateSettingsState.asStateFlow()

    private val _blockedUsersState = MutableStateFlow<Resource<BlockedUsersResponseData>?>(null)
    val blockedUsersState: StateFlow<Resource<BlockedUsersResponseData>?> = _blockedUsersState.asStateFlow()

    private val _unblockState = MutableStateFlow<Resource<Unit>?>(null)
    val unblockState: StateFlow<Resource<Unit>?> = _unblockState.asStateFlow()

    private val _reportsState = MutableStateFlow<Resource<ReportsResponseData>?>(null)
    val reportsState: StateFlow<Resource<ReportsResponseData>?> = _reportsState.asStateFlow()

    private val _usernameAvailabilityState = MutableStateFlow<Resource<Boolean>?>(null)
    val usernameAvailabilityState: StateFlow<Resource<Boolean>?> = _usernameAvailabilityState.asStateFlow()

    private val _updateProfileState = MutableStateFlow<Resource<AuthUserDto>?>(null)
    val updateProfileState: StateFlow<Resource<AuthUserDto>?> = _updateProfileState.asStateFlow()

    private val _otpRequestState = MutableStateFlow<Resource<String>?>(null) // returns verificationId
    val otpRequestState: StateFlow<Resource<String>?> = _otpRequestState.asStateFlow()

    private val _otpVerifyState = MutableStateFlow<Resource<Unit>?>(null)
    val otpVerifyState: StateFlow<Resource<Unit>?> = _otpVerifyState.asStateFlow()

    private val _logoutState = MutableStateFlow<Resource<Unit>?>(null)
    val logoutState: StateFlow<Resource<Unit>?> = _logoutState.asStateFlow()

    private val _deleteAccountState = MutableStateFlow<Resource<Unit>?>(null)
    val deleteAccountState: StateFlow<Resource<Unit>?> = _deleteAccountState.asStateFlow()

    private var currentSettings: NotificationSettingsDto = NotificationSettingsDto()

    init {
        refreshHeaderUser()
        loadNotificationSettings()
    }

    fun refreshHeaderUser() {
        viewModelScope.launch {
            authRepository.getCurrentUser().collectLatest { }
        }
        viewModelScope.launch {
            mainRepository.getMyFullProfile().collectLatest { }
        }
    }

    fun loadNotificationSettings() {
        viewModelScope.launch {
            userRepository.getNotificationSettings().collectLatest { resource ->
                if (resource is Resource.Success) {
                    currentSettings = resource.data
                }
                _notificationSettingsState.value = resource
            }
        }
    }

    fun togglePushNotifications(enabled: Boolean) {
        optimisticUpdate { it.copy(pushNotifications = enabled) }
    }

    fun toggleNewMessages(enabled: Boolean) {
        optimisticUpdate { it.copy(newMessages = enabled) }
    }

    fun toggleNewMatches(enabled: Boolean) {
        optimisticUpdate { it.copy(newMatches = enabled) }
    }

    fun toggleLikeComments(enabled: Boolean) {
        optimisticUpdate { it.copy(likeComments = enabled) }
    }

    fun toggleProfileVisits(enabled: Boolean) {
        optimisticUpdate { it.copy(profileVisits = enabled) }
    }

    fun toggleDatingTips(enabled: Boolean) {
        optimisticUpdate { it.copy(datingTips = enabled) }
    }

    fun toggleEmailNotifications(enabled: Boolean) {
        optimisticUpdate { it.copy(email = enabled) }
    }

    fun toggleAppUpdates(enabled: Boolean) {
        toggleDatingTips(enabled)
    }

    private fun optimisticUpdate(transform: (NotificationSettingsDto) -> NotificationSettingsDto) {
        val previousSettings = currentSettings
        val updated = transform(currentSettings)
        currentSettings = updated
        _notificationSettingsState.value = Resource.Success(updated)

        viewModelScope.launch {
            userRepository.updateNotificationSettings(updated).collectLatest { resource ->
                when (resource) {
                    is Resource.Success -> {
                        currentSettings = resource.data
                        _notificationSettingsState.value = Resource.Success(resource.data)
                    }
                    is Resource.Error -> {
                        currentSettings = previousSettings
                        _notificationSettingsState.value = Resource.Success(previousSettings)
                        _updateSettingsState.value = resource
                    }
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    // Blocked & Reported
    fun loadBlockedUsers(page: Int = 1, limit: Int = 20) {
        viewModelScope.launch {
            userRepository.getBlockedUsers(page, limit).collectLatest { resource ->
                _blockedUsersState.value = resource
            }
        }
    }

    fun unblockUser(userId: String) {
        viewModelScope.launch {
            userRepository.unblockUser(userId).collectLatest { resource ->
                _unblockState.value = resource
                if (resource is Resource.Success) {
                    loadBlockedUsers()
                }
            }
        }
    }

    fun loadReports(page: Int = 1, limit: Int = 20, status: String? = null, targetType: String? = null) {
        viewModelScope.launch {
            userRepository.getReports(page, limit, status, targetType).collectLatest { resource ->
                _reportsState.value = resource
            }
        }
    }

    fun checkUsername(username: String) {
        viewModelScope.launch {
            authRepository.checkUsernameAvailability(username).collectLatest { resource ->
                _usernameAvailabilityState.value = resource
            }
        }
    }

    // Personal Details Profile Update (Atomic Merge)
    fun updatePersonalDetails(
        fullName: String? = null,
        username: String? = null,
        location: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        viewModelScope.launch {
            val curFullName = fullName?.trim()?.ifBlank { null }
                ?: sessionManager.getFullName().ifBlank { sessionManager.getFirstName() }
            val curUsername = username?.trim()?.ifBlank { null }
                ?: sessionManager.getUsername().ifBlank { sessionManager.getNickName() }
            val curDob = sessionManager.getDob().ifBlank { "2000-01-01" }
            val curGender = sessionManager.getGender().ifBlank { "male" }
            val curBio = sessionManager.getBio()
            val curLoc = location?.trim()?.ifBlank { null }
                ?: sessionManager.getLocation().ifBlank { null }

            val request = UpdateProfileRequest(
                fullName = curFullName,
                username = curUsername,
                dateOfBirth = curDob,
                gender = curGender,
                about = curBio,
                location = curLoc,
                latitude = latitude,
                longitude = longitude
            )

            mainRepository.updateProfile(request).collectLatest { resource ->
                if (resource is Resource.Success) {
                    fullName?.let { sessionManager.setFullName(it) }
                    username?.let { sessionManager.setUsername(it) }
                    location?.let { sessionManager.setLocation(it) }
                    refreshHeaderUser()
                }
                _updateProfileState.value = resource
            }
        }
    }

    fun resetUpdateProfileState() {
        _updateProfileState.value = null
    }

    // Email OTP
    fun requestEmailOtp(email: String) {
        viewModelScope.launch {
            _otpRequestState.value = Resource.Loading
            userRepository.requestProfileEmailOtp(email.lowercase().trim()).collectLatest { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val vId = resource.data.verificationId
                        if (!vId.isNullOrBlank()) {
                            _otpRequestState.value = Resource.Success(vId)
                        } else {
                            _otpRequestState.value = Resource.Error("Missing verification ID")
                        }
                    }
                    is Resource.Error -> {
                        _otpRequestState.value = Resource.Error(resource.message)
                    }
                    Resource.Loading -> Unit
                }
            }
        }
    }

    fun verifyEmailOtp(verificationId: String, otp: String, email: String) {
        viewModelScope.launch {
            _otpVerifyState.value = Resource.Loading
            userRepository.verifyProfileEmailOtp(verificationId, otp.trim()).collectLatest { resource ->
                when (resource) {
                    is Resource.Success -> {
                        sessionManager.setEmail(email.lowercase().trim())
                        refreshHeaderUser()
                        _otpVerifyState.value = Resource.Success(Unit)
                    }
                    is Resource.Error -> {
                        _otpVerifyState.value = Resource.Error(resource.message)
                    }
                    Resource.Loading -> Unit
                }
            }
        }
    }

    fun resendEmailOtp(verificationId: String) {
        viewModelScope.launch {
            userRepository.resendProfileEmailOtp(verificationId).collectLatest { }
        }
    }

    // Phone OTP
    fun requestPhoneOtp(countryCode: String, phone: String) {
        viewModelScope.launch {
            _otpRequestState.value = Resource.Loading
            val effectiveCode = if (countryCode.startsWith("+")) countryCode else "+$countryCode"
            userRepository.requestProfilePhoneOtp(effectiveCode, phone.trim()).collectLatest { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val vId = resource.data.verificationId
                        if (!vId.isNullOrBlank()) {
                            _otpRequestState.value = Resource.Success(vId)
                        } else {
                            _otpRequestState.value = Resource.Error("Missing verification ID")
                        }
                    }
                    is Resource.Error -> {
                        _otpRequestState.value = Resource.Error(resource.message)
                    }
                    Resource.Loading -> Unit
                }
            }
        }
    }

    fun verifyPhoneOtp(verificationId: String, otp: String, fullPhone: String) {
        viewModelScope.launch {
            _otpVerifyState.value = Resource.Loading
            userRepository.verifyProfilePhoneOtp(verificationId, otp.trim()).collectLatest { resource ->
                when (resource) {
                    is Resource.Success -> {
                        sessionManager.setPhone(fullPhone.trim())
                        refreshHeaderUser()
                        _otpVerifyState.value = Resource.Success(Unit)
                    }
                    is Resource.Error -> {
                        _otpVerifyState.value = Resource.Error(resource.message)
                    }
                    Resource.Loading -> Unit
                }
            }
        }
    }

    fun resendPhoneOtp(verificationId: String) {
        viewModelScope.launch {
            userRepository.resendProfilePhoneOtp(verificationId).collectLatest { }
        }
    }

    fun resetOtpStates() {
        _otpRequestState.value = null
        _otpVerifyState.value = null
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout().collectLatest { resource ->
                _logoutState.value = resource
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            userRepository.deleteAccount().collectLatest { resource ->
                _deleteAccountState.value = resource
            }
        }
    }

    fun resetLogoutState() {
        _logoutState.value = null
    }

    fun resetDeleteState() {
        _deleteAccountState.value = null
    }
}
