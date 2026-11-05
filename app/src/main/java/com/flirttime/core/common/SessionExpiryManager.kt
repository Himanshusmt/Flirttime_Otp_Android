package com.flirttime.core.common

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.core.ui.dialog.SessionExpiredDialog
import com.flirttime.feature.chat.socket.ChatSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * App-wide coordinator for session expiration and token invalidation.
 * Handles atomic de-duplication across concurrent requests, session data clearing,
 * socket termination, and presents the themed [SessionExpiredDialog] over the top Activity.
 */
class SessionExpiryManager(
    private val context: Context,
    private val sessionManager: SessionManager,
    private val chatSocketManager: Lazy<ChatSocketManager>
) {

    private val isHandlingExpiry = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Triggered when an authenticated network request returns HTTP 401 Unauthorized
     * or token expiration is detected.
     */
    fun handleSessionExpired(reason: String = "Session expired or token invalid") {
        // Atomic de-duplication: Ensure only one expiry flow runs even if multiple requests fail at once
        if (!isHandlingExpiry.compareAndSet(false, true)) {
            Log.w(TAG, "Session expiry already being handled; ignoring duplicate trigger: $reason")
            return
        }

        Log.e(TAG, "Triggering app-wide session expiration. Reason: $reason")

        // 1. Terminate background socket connections
        try {
            chatSocketManager.value.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disconnect chat socket on session expiry: ${e.message}", e)
        }

        // 2. Clear stored auth tokens, profile caches, and fastCache immediately
        CoroutineScope(Dispatchers.IO).launch {
            try {
                sessionManager.clearSession()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear session data: ${e.message}", e)
            }
        }

        // 3. Coordinate UI on Main Thread
        mainHandler.post {
            val topActivity = AppForegroundTracker.currentActivity

            if (topActivity != null && !topActivity.isFinishing && !topActivity.isDestroyed) {
                val screenName = topActivity::class.java.simpleName

                // Prevent recursive loops if the user is already on an auth or onboarding screen
                if (isAuthOrOnboardingScreen(screenName)) {
                    Log.d(TAG, "Current activity is already an auth screen ($screenName); skipping dialog.")
                    return@post
                }

                SessionExpiredDialog.show(
                    context = topActivity,
                    onConfirm = {
                        AppNavigator.navigateTo(
                            context = topActivity,
                            destination = Destination.Login,
                            clearBackStack = true,
                            finishCurrent = true
                        )
                    }
                )
            } else {
                // If no foreground activity is available, navigate to Login using application context
                AppNavigator.navigateTo(
                    context = context,
                    destination = Destination.Login,
                    clearBackStack = true
                )
            }
        }
    }

    /**
     * Resets the expiry lock. Typically called after a successful login or when LoginActivity is launched.
     */
    fun reset() {
        isHandlingExpiry.set(false)
    }

    private fun isAuthOrOnboardingScreen(screenName: String): Boolean {
        return screenName == "LoginActivity" ||
                screenName == "PhoneInputActivity" ||
                screenName == "EmailVerifyActivity" ||
                screenName == "OtpActivity" ||
                screenName == "SplashActivity" ||
                screenName.startsWith("Intro") ||
                screenName == "PermissionActivity"
    }

    companion object {
        private const val TAG = "SessionExpiryManager"
    }
}
