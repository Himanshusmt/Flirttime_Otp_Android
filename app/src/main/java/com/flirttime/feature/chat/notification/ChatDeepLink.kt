package com.flirttime.feature.chat.notification

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.feature.chat.detail.ChatDetailActivity

/** One chat a push payload (or a `flirttime://chat/<id>` URI) points at. */
data class ChatTarget(
    val conversationId: String,
    val title: String? = null,
    val avatarUrl: String? = null,
    val isGroup: Boolean = false,
    val isVerified: Boolean = false,
    val peerUserId: String? = null
)

/**
 * Resolves push / deep-link payloads into the chat screen.
 *
 * Mirrors iOS `NotificationManager.openChat`: a payload may carry `conversationId`,
 * `navTargetId` or `entityId` with an optional `senderName` / `title`, and older payloads
 * only ship a bare id.
 */
object ChatDeepLink {

    private val CONVERSATION_KEYS = listOf(
        "conversationId", "conversation_id", "chatId", "chat_id",
        "navTargetId", "entityId", "entity_id"
    )

    private val TITLE_KEYS = listOf("title", "senderName", "sender_name", "chatTitle")
    private val AVATAR_KEYS = listOf("avatar", "avatarUrl", "senderAvatar", "profilePicture")

    /** Builds the explicit intent used by notification taps. */
    fun buildChatIntent(
        context: Context,
        conversationId: String,
        title: String? = null,
        avatarUrl: String? = null,
        isGroup: Boolean = false,
        isVerified: Boolean = false,
        peerUserId: String? = null
    ): Intent {
        val intent = Intent(context, ChatDetailActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AppNavigator.EXTRA_CONVERSATION_ID, conversationId)
            title?.takeIf { it.isNotBlank() }?.let { putExtra(AppNavigator.EXTRA_CHAT_TITLE, it) }
            avatarUrl?.takeIf { it.isNotBlank() }?.let { putExtra(AppNavigator.EXTRA_CHAT_AVATAR, it) }
            putExtra(AppNavigator.EXTRA_CHAT_IS_GROUP, isGroup)
            putExtra(AppNavigator.EXTRA_CHAT_IS_VERIFIED, isVerified)
            peerUserId?.takeIf { it.isNotBlank() }
                ?.let { putExtra(AppNavigator.EXTRA_CHAT_PEER_USER_ID, it) }
        }
        return intent
    }

    /** Extracts the chat target from extras or a `flirttime://chat/<id>` data URI. */
    fun parse(intent: Intent?): ChatTarget? {
        intent ?: return null
        val extras = intent.extras
        var conversationId = CONVERSATION_KEYS.firstNotNullOfOrNull { key ->
            extras?.getString(key)?.trim()?.takeIf { it.isNotEmpty() }
        }
        val data: Uri? = intent.data
        if (conversationId == null) conversationId = conversationIdFromUri(data)
        if (conversationId.isNullOrBlank()) return null

        val title = TITLE_KEYS.firstNotNullOfOrNull { key ->
            extras?.getString(key)?.trim()?.takeIf { it.isNotEmpty() }
        } ?: data?.getQueryParameter("title")

        val avatar = AVATAR_KEYS.firstNotNullOfOrNull { key ->
            extras?.getString(key)?.trim()?.takeIf { it.isNotEmpty() }
        } ?: data?.getQueryParameter("avatar")

        val isGroup = (extras?.getBoolean("isGroup", false) ?: false) ||
            data?.getQueryParameter("group").equals("1", ignoreCase = true)

        return ChatTarget(
            conversationId = conversationId,
            title = title,
            avatarUrl = avatar,
            isGroup = isGroup,
            isVerified = extras?.getBoolean(AppNavigator.EXTRA_CHAT_IS_VERIFIED, false) ?: false
        )
    }

    /** Opens the chat referenced by [intent]; returns false when there is nothing to open. */
    fun openFromIntent(context: Context, intent: Intent?): Boolean {
        val target = parse(intent) ?: return false
        context.startActivity(
            buildChatIntent(
                context = context,
                conversationId = target.conversationId,
                title = target.title,
                avatarUrl = target.avatarUrl,
                isGroup = target.isGroup,
                isVerified = target.isVerified,
                peerUserId = target.peerUserId
            )
        )
        return true
    }

    /** Reads the id from `flirttime://chat/<id>` (also tolerates `?conversationId=`). */
    fun conversationIdFromUri(data: Uri?): String? {
        data ?: return null
        if (!data.scheme.equals(ChatNotificationCenter.DEEP_LINK_SCHEME, ignoreCase = true)) {
            return null
        }
        if (!data.host.equals(ChatNotificationCenter.DEEP_LINK_HOST, ignoreCase = true)) {
            return null
        }
        val fromPath = data.pathSegments?.lastOrNull()?.trim()
        if (!fromPath.isNullOrEmpty()) return fromPath
        return data.getQueryParameter("conversationId")?.trim()?.takeIf { it.isNotEmpty() }
    }
}
