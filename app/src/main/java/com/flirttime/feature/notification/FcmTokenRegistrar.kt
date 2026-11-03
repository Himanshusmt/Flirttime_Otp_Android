package com.flirttime.feature.notification

import com.flirttime.core.database.SessionManager
import com.flirttime.feature.auth.AuthRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Fetches the real FCM registration token, persists it, and registers the device with the API.
 */
object FcmTokenRegistrar {

    suspend fun refreshAndRegister(
        sessionManager: SessionManager,
        authRepository: AuthRepository
    ) = withContext(Dispatchers.IO) {
        val token = fetchToken() ?: return@withContext
        sessionManager.setDeviceToken(token)
        val jwt = sessionManager.getToken()
        if (jwt.isBlank()) return@withContext
        authRepository.registerDevice(deviceToken = token).collect { /* ignore */ }
    }

    suspend fun currentTokenOrFallback(sessionManager: SessionManager): String {
        val fcm = fetchToken()
        if (!fcm.isNullOrBlank()) {
            sessionManager.setDeviceToken(fcm)
            return fcm
        }
        return sessionManager.getOrCreateDeviceToken()
    }

    private suspend fun fetchToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (cont.isActive) cont.resume(token)
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }
    }
}
