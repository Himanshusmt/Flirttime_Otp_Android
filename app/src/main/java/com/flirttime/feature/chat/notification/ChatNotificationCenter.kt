package com.flirttime.feature.chat.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import com.flirttime.MainActivity
import com.flirttime.R
import com.flirttime.core.navigation.AppNavigator

/**
 * Local chat notifications that deep-link straight into one thread.
 *
 * iOS routes `NotificationManager.openChat(...)` into the messages tab; the Android twin
 * builds a [PendingIntent] for `ChatDetailActivity` so a tap lands on the exact chat, and
 * also accepts a `flirttime://chat/<conversationId>` URI.
 */
object ChatNotificationCenter {

    const val CHANNEL_ID = "chat_messages"
    private const val CHANNEL_NAME = "Messages"

    /** Scheme + host understood by `ChatDetailActivity`'s intent filter. */
    const val DEEP_LINK_SCHEME = "flirttime"
    const val DEEP_LINK_HOST = "chat"

    /** Builds the chat deep link for a conversation. */
    fun deepLink(conversationId: String): Uri =
        Uri.Builder()
            .scheme(DEEP_LINK_SCHEME)
            .authority(DEEP_LINK_HOST)
            .appendPath(conversationId)
            .build()

    /** Posts (or updates) the notification for one conversation. */
    fun notifyMessage(
        context: Context,
        conversationId: String,
        title: String,
        body: String,
        avatarUrl: String? = null,
        isGroup: Boolean = false,
        isVerified: Boolean = false,
        unreadCount: Int = 1
    ) {
        if (conversationId.isBlank()) return
        if (!canPost(context)) return
        ensureChannel(context)

        val intent = ChatDeepLink.buildChatIntent(
            context = context,
            conversationId = conversationId,
            title = title,
            avatarUrl = avatarUrl,
            isGroup = isGroup,
            isVerified = isVerified
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_badge_message)
            .setContentTitle(title.ifBlank { "New message" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setNumber(unreadCount)
            .setContentIntent(contentIntent(context, conversationId, intent))
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(conversationId.hashCode(), notification)
        }
    }

    fun cancel(context: Context, conversationId: String) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(conversationId.hashCode())
        }
    }

    /**
     * Tap target for [conversationId]: the inbox sits underneath the thread so Back returns
     * to the chat list instead of leaving the app (cold start from a push).
     */
    private fun contentIntent(context: Context, conversationId: String, chatIntent: Intent): PendingIntent? =
        TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(Intent(context, MainActivity::class.java))
            .addNextIntent(chatIntent)
            .getPendingIntent(
                conversationId.hashCode(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

    private fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Direct and group chat messages"
                setShowBadge(true)
            }
        )
    }

    /** Intent extras understood by [ChatDeepLink] when a push payload has no title. */
    fun chatExtras(conversationId: String): Intent =
        Intent().putExtra(AppNavigator.EXTRA_CONVERSATION_ID, conversationId)
}
