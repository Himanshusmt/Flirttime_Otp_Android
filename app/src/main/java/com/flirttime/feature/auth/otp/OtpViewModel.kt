package com.flirttime.feature.auth.otp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.Destination
import com.flirttime.feature.auth.AuthRepository
import com.flirttime.feature.auth.OtpResponseData
import com.flirttime.feature.auth.VerifyOtpResponseData
import com.flirttime.feature.main.MainRepository
import com.flirttime.feature.main.matches.FreeTierLimitsStore
import com.flirttime.feature.notification.FcmTokenRegistrar
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OtpViewModel(
    private val authRepository: AuthRepository,
    private val mainRepository: MainRepository,
    private val limitsStore: FreeTierLimitsStore,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _verifyOtpState = MutableStateFlow<Resource<VerifyOtpResponseData>?>(null)
    val verifyOtpState: StateFlow<Resource<VerifyOtpResponseData>?> = _verifyOtpState.asStateFlow()

    private val _resendOtpState = MutableStateFlow<Resource<OtpResponseData>?>(null)
    val resendOtpState: StateFlow<Resource<OtpResponseData>?> = _resendOtpState.asStateFlow()

    private val _postLoginDestination = MutableSharedFlow<Destination>(extraBufferCapacity = 1)
    val postLoginDestination: SharedFlow<Destination> = _postLoginDestination.asSharedFlow()

    fun verifyOtp(otpFrom: String, verificationId: String, otp: String) {
        viewModelScope.launch {
            val flow = if (otpFrom.equals("email", ignoreCase = true)) {
                authRepository.verifyEmailOtp(verificationId, otp)
            } else {
                authRepository.verifyPhoneOtp(verificationId, otp)
            }
            flow.collect { result ->
                _verifyOtpState.value = result
            }
        }
    }

    fun resendOtp(otpFrom: String, verificationId: String) {
        viewModelScope.launch {
            val flow = if (otpFrom.equals("email", ignoreCase = true)) {
                authRepository.resendEmailOtp(verificationId)
            } else {
                authRepository.resendPhoneOtp(verificationId)
            }
            flow.collect { result ->
                _resendOtpState.value = result
            }
        }
    }

    fun routeAfterLogin(isProfileComplete: Boolean) {
        viewModelScope.launch {
            FcmTokenRegistrar.refreshAndRegister(sessionManager, authRepository)
            limitsStore.refresh()
            if (isProfileComplete) {
                _postLoginDestination.emit(Destination.Main())
                return@launch
            }
            mainRepository.getOnboardingProgress().collect { resource ->
                when (resource) {
                    is Resource.Success -> _postLoginDestination.emit(resource.data.destination)
                    is Resource.Error -> _postLoginDestination.emit(Destination.IntroduceYourself)
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    fun resetVerifyState() {
        _verifyOtpState.value = null
    }

    fun resetResendState() {
        _resendOtpState.value = null
    }
}