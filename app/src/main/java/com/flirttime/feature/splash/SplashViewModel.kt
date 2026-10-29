package com.flirttime.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.Destination
import com.flirttime.feature.auth.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

class SplashViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
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
                        // Register FCM device token with backend
                        authRepository.registerDevice().launchIn(viewModelScope)

                        // Route to appropriate screen based on user verification status
                        val target = when {
                            user.pageRedirect == "home" -> Destination.Main(targetTab = 2)
                            user.pageRedirect == "gesture" -> Destination.VerificationProcess
                            user.pageRedirect == "avatar" -> Destination.GestureVerification
                            user.pageRedirect == "user_info" -> Destination.ProfileVerification
                            user.isProfileComplete == true -> Destination.Main(targetTab = 2)
                            user.isProfileComplete != true -> Destination.IntroduceYourself
                            else -> Destination.Main(targetTab = 2)
                        }
                        _navigationEvent.emit(target)
                    }
                    is Resource.Error -> {
                        if (resource.code == 401 || resource.code == 403) {
                            // Token is invalid/expired -> Clear session and redirect to Login
                            sessionManager.clearSession()
                            _navigationEvent.emit(Destination.Login)
                        } else {
                            // Network failure / temporary server outage -> Gracefully fallback to cached state
                            val cachedRedirect = sessionManager.getPageRedirect()
                            val cachedProfileComplete = sessionManager.isProfileComplete()
                            val fallbackTarget = when {
                                cachedRedirect == "home" -> Destination.Main(targetTab = 2)
                                cachedRedirect == "gesture" -> Destination.VerificationProcess
                                cachedRedirect == "avatar" -> Destination.GestureVerification
                                cachedRedirect == "user_info" -> Destination.ProfileVerification
                                cachedProfileComplete -> Destination.Main(targetTab = 2)
                                else -> Destination.IntroduceYourself
                            }
                            _navigationEvent.emit(fallbackTarget)
                        }
                    }
                }
            }
        }
    }
}
