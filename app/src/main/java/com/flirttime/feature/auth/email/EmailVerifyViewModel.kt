package com.flirttime.feature.auth.email

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.feature.auth.AuthRepository
import com.flirttime.feature.auth.OtpResponseData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EmailVerifyViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _requestOtpState = MutableStateFlow<Resource<OtpResponseData>?>(null)
    val requestOtpState: StateFlow<Resource<OtpResponseData>?> = _requestOtpState.asStateFlow()

    fun requestEmailOtp(email: String) {
        viewModelScope.launch {
            authRepository.requestEmailOtp(email).collect { result ->
                _requestOtpState.value = result
            }
        }
    }

    fun resetRequestOtpState() {
        _requestOtpState.value = null
    }
}