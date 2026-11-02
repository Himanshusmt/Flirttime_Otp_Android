package com.flirttime.feature.chat

import com.flirttime.Resource
import com.flirttime.feature.chat.model.ChatMediaUpload
import com.flirttime.feature.chat.model.ChatMessageDto
import com.flirttime.feature.chat.model.ConversationRowDto
import com.flirttime.feature.chat.model.ForwardRecipientDto
import com.flirttime.feature.chat.model.MessagesPageDto

/** One page of forward-picker recipients. */
data class ForwardRecipientPage(
    val recipients: List<ForwardRecipientDto>,
    val hasMore: Boolean
)

/**
 * Domain-facing chat contract. Every call returns [Resource] so callers never see
 * Retrofit/Gson exceptions (see `SafeApiCall` for the mapping rules).
 */
interface ChatRepository {

    suspend fun getConversations(
        page: Int = 1,
        limit: Int = 30,
        search: String? = null,
        archived: Boolean? = null
    ): Resource<List<ConversationRowDto>>

    suspend fun getMessages(
        conversationId: String,
        page: Int = 1,
        limit: Int = 30,
        before: String? = null
    ): Resource<MessagesPageDto>

    suspend fun sendMessage(
        conversationId: String,
        text: String,
        clientMessageId: String,
        replyToId: String? = null
    ): Resource<ChatMessageDto>

    suspend fun markConversationRead(
        conversationId: String,
        lastReadMessageId: String?
    ): Resource<Unit>

    /** Uploads an attachment and returns the storage `mediaId` the chat API expects. */
    suspend fun uploadChatMedia(
        kind: String,
        contentType: String,
        fileName: String,
        bytes: ByteArray
    ): Resource<ChatMediaUpload>

    /** Sends an image / video / audio message once its attachment is uploaded. */
    suspend fun sendMediaMessage(
        conversationId: String,
        kind: String,
        upload: ChatMediaUpload,
        clientMessageId: String,
        fileName: String,
        fileSizeBytes: Long,
        durationSeconds: Double? = null,
        width: Int? = null,
        height: Int? = null,
        caption: String? = null,
        replyToId: String? = null
    ): Resource<ChatMessageDto>

    /** Edits the text of an outgoing message. */
    suspend fun editMessage(
        conversationId: String,
        messageId: String,
        content: String
    ): Resource<Unit>

    /** `deleteForEveryone = false` hides the bubble for me only. */
    suspend fun deleteMessage(
        conversationId: String,
        messageId: String,
        deleteForEveryone: Boolean
    ): Resource<Unit>

    /** Toggles my reaction for [emoji]; the server echoes the new reaction groups. */
    suspend fun reactToMessage(
        conversationId: String,
        messageId: String,
        emoji: String
    ): Resource<Any?>

    /** Pins / unpins a message; [pinDuration] is one of `24h`, `7d`, `30d`. */
    suspend fun pinMessage(
        conversationId: String,
        messageId: String,
        pinned: Boolean,
        pinDuration: String? = null
    ): Resource<Unit>

    suspend fun forwardMessages(
        messageIds: List<String>,
        recipientIds: List<String>
    ): Resource<Unit>

    suspend fun getForwardRecipients(
        page: Int = 1,
        limit: Int = 20,
        search: String? = null
    ): Resource<ForwardRecipientPage>

    /**
     * Applies thread-level preferences. Mirrors iOS `applyConversationSettings`:
     * pin goes through `/pin`, archive through `/archive` (with a settings fallback),
     * everything else through `/settings`.
     */
    suspend fun updateConversationSettings(
        conversationId: String,
        isMuted: Boolean? = null,
        isPinned: Boolean? = null,
        isArchived: Boolean? = null
    ): Resource<Unit>

    /** Flags the thread back to unread in the inbox (`POST /chats/{id}/unread`). */
    suspend fun markConversationUnread(conversationId: String): Resource<Unit>

    /** `forEveryone = false` only removes the thread from my inbox. */
    suspend fun deleteConversation(
        conversationId: String,
        forEveryone: Boolean = false
    ): Resource<Unit>

    suspend fun blockUser(userId: String, conversationId: String? = null): Resource<Unit>

    suspend fun unblockUser(userId: String): Resource<Unit>
}
