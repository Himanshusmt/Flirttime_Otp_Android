package com.flirttime.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.feature.auth.AuthRepository
import com.flirttime.feature.auth.LoginByPhoneResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _loginState = MutableStateFlow<Resource<LoginByPhoneResponse>?>(null)
    val loginState: StateFlow<Resource<LoginByPhoneResponse>?> = _loginState.asStateFlow()

    fun loginWithGoogle(idToken: String, fcmToken: String) {
        viewModelScope.launch {
            authRepository.loginByGoogle(idToken, fcmToken).collect { result ->
                _loginState.value = result
            }
        }
    }

    fun resetState() {
        _loginState.value = null
    }
}