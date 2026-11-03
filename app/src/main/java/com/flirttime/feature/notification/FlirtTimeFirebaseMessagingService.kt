package com.flirttime.feature.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.flirttime.MainActivity
import com.flirttime.R
import com.flirttime.core.common.AppForegroundTracker
import com.flirttime.core.database.SessionManager
import com.flirttime.feature.auth.AuthRepository
import com.flirttime.feature.chat.notification.ChatNotificationCenter
import com.flirttime.feature.chat.notification.ChatPushReceiver
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * System FCM entry point.
 *
 * - [onNewToken] persists the real FCM token and re-registers with the backend when logged in.
 * - [onMessageReceived] routes chat payloads into [ChatNotificationCenter] / [ChatPushReceiver]
 *   and other types into a tappable system notification that opens via [NotificationRouter].
 */
class FlirtTimeFirebaseMessagingService : FirebaseMessagingService() {

    private val sessionManager: SessionManager by inject()
    private val authRepository: AuthRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            sessionManager.setDeviceToken(token)
            val jwt = sessionManager.getToken()
            if (jwt.isNotBlank()) {
                authRepository.registerDevice(deviceToken = token).collect { /* fire-and-forget */ }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data.isEmpty() && message.notification == null) return

        val payload = LinkedHashMap<String, String>()
        data.forEach { (k, v) -> if (!v.isNullOrBlank()) payload[k] = v }
        message.notification?.title?.let { payload.putIfAbsent("title", it) }
        message.notification?.body?.let { payload.putIfAbsent("body", it) }

        val routeType = NotificationRouteType.fromPayload(
            listOf(
                payload["route"],
                payload["routeType"],
                payload["type"],
                payload["targetType"],
                payload["action"],
                payload["target"]
            )
        )

        if (routeType == NotificationRouteType.MESSAGE || isChatPayload(payload)) {
            handleChatPush(payload)
            return
        }

        if (AppForegroundTracker.isForeground) {
            // In-app NotificationActivity / badge refresh covers the live UI; skip tray spam.
            return
        }

        showRoutedNotification(routeType, payload, message.notification?.title, message.notification?.body)
    }

    private fun handleChatPush(payload: Map<String, String>) {
        val conversationId = first(
            payload,
            "conversationId", "conversation_id", "chatId", "navTargetId", "entityId"
        ) ?: return

        val title = first(payload, "senderName", "title") ?: "New message"
        val body = first(payload, "body", "message", "content") ?: "Sent you a message"
        val avatarUrl = first(payload, "avatarUrl", "avatar", "senderAvatar")
        val isGroup = payload["isGroup"]?.equals("true", ignoreCase = true) == true ||
            payload["conversationType"]?.equals("group", ignoreCase = true) == true

        if (AppForegroundTracker.isForeground) {
            ChatNotificationCenter.cancel(this, conversationId)
            return
        }

        ChatNotificationCenter.notifyMessage(
            context = this,
            conversationId = conversationId,
            title = title,
            body = body,
            avatarUrl = avatarUrl,
            isGroup = isGroup,
            isVerified = payload["isVerified"]?.equals("true", ignoreCase = true) == true
        )
    }

    private fun showRoutedNotification(
        routeType: NotificationRouteType,
        payload: Map<String, String>,
        fallbackTitle: String?,
        fallbackBody: String?
    ) {
        ensureChannel()

        val title = first(payload, "title", "senderName") ?: fallbackTitle ?: "Flirt Time"
        val body = first(payload, "body", "message", "description", "content")
            ?: fallbackBody
            ?: "You have a new notification"

        val openIntent = Intent(this, NotificationClickActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationClickActivity.EXTRA_ROUTE_TYPE, routeType.name)
            payload.forEach { (key, value) -> putExtra(NotificationClickActivity.EXTRA_PAYLOAD_PREFIX + key, value) }
            putExtra(NotificationClickActivity.EXTRA_PAYLOAD_KEYS, payload.keys.toTypedArray())
        }

        val pending = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(Intent(this@FlirtTimeFirebaseMessagingService, MainActivity::class.java))
            addNextIntent(openIntent)
            getPendingIntent(
                (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()

        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify((title + body).hashCode(), notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    private fun isChatPayload(payload: Map<String, String>): Boolean {
        val type = first(payload, "type", "routeType", "targetType")?.lowercase().orEmpty()
        return type.contains("message") || type.contains("chat") ||
            payload.containsKey("conversationId") || payload.containsKey("conversation_id")
    }

    private fun first(payload: Map<String, String>, vararg keys: String): String? {
        for (key in keys) {
            val value = payload[key]?.trim().orEmpty()
            if (value.isNotEmpty()) return value
        }
        return null
    }

    companion object {
        const val CHANNEL_ID = "flirttime_general"
        private const val CHANNEL_NAME = "Updates"

        /** Kept for documentation / local tests that still broadcast chat pushes. */
        @Suppress("unused")
        fun chatPushAction(): String = ChatPushReceiver.ACTION_CHAT_PUSH
    }
}
