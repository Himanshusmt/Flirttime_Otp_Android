package com.flirttime.feature.auth.otp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.feature.auth.AuthRepository
import com.flirttime.feature.auth.OtpResponseData
import com.flirttime.feature.auth.VerifyOtpResponseData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OtpViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _verifyOtpState = MutableStateFlow<Resource<VerifyOtpResponseData>?>(null)
    val verifyOtpState: StateFlow<Resource<VerifyOtpResponseData>?> = _verifyOtpState.asStateFlow()

    private val _resendOtpState = MutableStateFlow<Resource<OtpResponseData>?>(null)
    val resendOtpState: StateFlow<Resource<OtpResponseData>?> = _resendOtpState.asStateFlow()

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

    fun resetVerifyState() {
        _verifyOtpState.value = null
    }

    fun resetResendState() {
        _resendOtpState.value = null
    }
}