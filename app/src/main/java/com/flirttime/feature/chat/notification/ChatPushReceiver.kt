package com.flirttime.feature.chat.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flirttime.core.common.AppForegroundTracker

/**
 * Entry point for chat pushes while the app is in the background.
 *
 * A push provider (FCM / HMS / the backend's socket worker) delivers the payload here; the
 * receiver turns it into a notification whose tap deep-links into [ChatDeepLink]'s target
 * chat, exactly like iOS `NotificationManager.openChat`.
 */
class ChatPushReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val target = ChatDeepLink.parse(intent) ?: return
        val body = intent.getStringExtra(EXTRA_BODY)
            ?: intent.getStringExtra("message")
            ?: "Sent you a message"

        if (AppForegroundTracker.isForeground) {
            // The open inbox / thread already reflects it in place.
            ChatNotificationCenter.cancel(context, target.conversationId)
            return
        }

        ChatNotificationCenter.notifyMessage(
            context = context,
            conversationId = target.conversationId,
            title = target.title ?: "New message",
            body = body,
            avatarUrl = target.avatarUrl,
            isGroup = target.isGroup,
            isVerified = target.isVerified
        )
    }

    companion object {
        const val ACTION_CHAT_PUSH = "com.flirttime.action.CHAT_PUSH"
        const val EXTRA_BODY = "body"
    }
}
