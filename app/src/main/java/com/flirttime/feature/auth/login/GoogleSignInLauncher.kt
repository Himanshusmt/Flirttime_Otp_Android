package com.flirttime.feature.auth.login

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.flirttime.Resource
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.feature.notification.FcmTokenRegistrar
import kotlinx.coroutines.launch

fun AppCompatActivity.observeGoogleLoginState(
    loginViewModel: LoginViewModel,
    onLoading: (Boolean) -> Unit,
    onError: (String) -> Unit
) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                loginViewModel.loginState.collect { resource ->
                    when (resource) {
                        is Resource.Loading -> onLoading(true)
                        is Resource.Success -> {
                            onLoading(false)
                            loginViewModel.resetState()
                        }
                        is Resource.Error -> {
                            onLoading(false)
                            onError(resource.message)
                            loginViewModel.resetState()
                        }
                        null -> onLoading(false)
                    }
                }
            }
            launch {
                loginViewModel.postLoginDestination.collect { destination ->
                    AppNavigator.navigateTo(
                        context = this@observeGoogleLoginState,
                        destination = destination,
                        clearBackStack = true
                    )
                }
            }
        }
    }
}

fun AppCompatActivity.launchGoogleSignIn(
    loginViewModel: LoginViewModel,
    sessionManager: SessionManager,
    onLoading: (Boolean) -> Unit,
    onError: (String) -> Unit
) {
    lifecycleScope.launch {
        onLoading(true)
        val idTokenResult = GoogleSignInHelper.signIn(this@launchGoogleSignIn)
        if (idTokenResult.isFailure) {
            onLoading(false)
            onError(idTokenResult.exceptionOrNull()?.localizedMessage ?: "Google Sign-In failed")
            return@launch
        }
        val fcmToken = FcmTokenRegistrar.currentTokenOrFallback(sessionManager)
        loginViewModel.loginWithGoogle(idTokenResult.getOrThrow(), fcmToken)
    }
}
