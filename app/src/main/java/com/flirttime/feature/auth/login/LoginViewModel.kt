package com.flirttime.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.Destination
import com.flirttime.feature.auth.AuthRepository
import com.flirttime.feature.auth.LoginByPhoneResponse
import com.flirttime.feature.main.MainRepository
import com.flirttime.feature.main.matches.FreeTierLimitsStore
import com.flirttime.feature.notification.FcmTokenRegistrar
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val mainRepository: MainRepository,
    private val limitsStore: FreeTierLimitsStore,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _loginState = MutableStateFlow<Resource<LoginByPhoneResponse>?>(null)
    val loginState: StateFlow<Resource<LoginByPhoneResponse>?> = _loginState.asStateFlow()

    private val _postLoginDestination = MutableSharedFlow<Destination>(extraBufferCapacity = 1)
    val postLoginDestination: SharedFlow<Destination> = _postLoginDestination.asSharedFlow()

    fun loginWithGoogle(idToken: String, fcmToken: String) {
        viewModelScope.launch {
            authRepository.loginByGoogle(idToken, fcmToken).collect { result ->
                _loginState.value = result
                if (result is Resource.Success) {
                    FcmTokenRegistrar.refreshAndRegister(sessionManager, authRepository)
                    val profileComplete = resolveProfileComplete()
                    routeAfterLogin(profileComplete)
                }
            }
        }
    }

    private suspend fun resolveProfileComplete(): Boolean {
        if (sessionManager.isProfileComplete()) return true
        return when (val userResult = authRepository.getCurrentUser().first { it !is Resource.Loading }) {
            is Resource.Success -> userResult.data.isProfileComplete == true
            else -> false
        }
    }

    fun routeAfterLogin(isProfileComplete: Boolean) {
        viewModelScope.launch {
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

    fun resetState() {
        _loginState.value = null
    }
}
