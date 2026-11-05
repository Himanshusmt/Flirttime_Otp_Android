package com.flirttime.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.Destination
import com.flirttime.feature.auth.AuthRepository
import com.flirttime.feature.main.MainRepository
import com.flirttime.feature.main.matches.FreeTierLimitsStore
import com.flirttime.feature.notification.FcmTokenRegistrar
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SplashViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val mainRepository: MainRepository,
    private val limitsStore: FreeTierLimitsStore
) : ViewModel() {

    private val _navigationEvent = MutableSharedFlow<Destination>(replay = 1)
    val navigationEvent: SharedFlow<Destination> = _navigationEvent.asSharedFlow()

    fun determineStartDestination() {
        viewModelScope.launch {
            val token = sessionManager.getToken()
            val isIntroCompleted = sessionManager.isIntroComplete()

            if (token.isBlank()) {
                val target = if (isIntroCompleted) {
                    Destination.Login
                } else {
                    Destination.Intro1
                }
                _navigationEvent.emit(target)
                return@launch
            }

            // User has token: Validate session & fetch fresh profile from backend
            authRepository.getCurrentUser().collect { resource ->
                when (resource) {
                    is Resource.Loading -> Unit
                    is Resource.Success -> {
                        val user = resource.data
                        // Fetch real FCM token and register with backend
                        FcmTokenRegistrar.refreshAndRegister(sessionManager, authRepository)

                        limitsStore.refresh()
                        if (user.isProfileComplete == true) {
                            _navigationEvent.emit(Destination.Main(targetTab = 0))
                        } else {
                            routeByOnboardingProgress()
                        }
                    }
                    is Resource.Error -> {
                        if (resource.code == 401 || resource.code == 403) {
                            // Token is invalid/expired -> Clear session and redirect to Login
                            sessionManager.clearSession()
                            _navigationEvent.emit(Destination.Login(sessionExpired = true))
                        } else {
                            // Network failure / temporary server outage -> Gracefully fallback to cached state
                            if (sessionManager.isProfileComplete()) {
                                _navigationEvent.emit(Destination.Main(targetTab = 0))
                            } else {
                                routeByOnboardingProgress()
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun routeByOnboardingProgress() {
        mainRepository.getOnboardingProgress().collect { resource ->
            when (resource) {
                is Resource.Success -> _navigationEvent.emit(resource.data.destination)
                is Resource.Error -> _navigationEvent.emit(Destination.IntroduceYourself)
                is Resource.Loading -> Unit
            }
        }
    }
}
