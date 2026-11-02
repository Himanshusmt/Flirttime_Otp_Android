package com.flirttime.feature.chat.model

import com.google.gson.annotations.SerializedName

/**
 * Wire models for the FlirtTime chat API (`/api/v1/chats`).
 *
 * Every field is nullable on purpose: Gson instantiates these reflectively and leaves
 * missing keys as null, so non-null Kotlin types would be unsafe. Where the backend has
 * shipped more than one spelling for the same concept (FlirtTime vs. legacy OneVibe),
 * [SerializedName.alternate] absorbs the variance the same way the iOS client does.
 */
data class ChatSenderDto(
    @SerializedName(value = "id", alternate = ["userId", "senderId"])
    val id: String? = null,
    @SerializedName(value = "userName", alternate = ["username"])
    val userName: String? = null,
    @SerializedName(value = "fullName", alternate = ["displayName"])
    val fullName: String? = null,
    @SerializedName(value = "profilePicture", alternate = ["profileImageUrl", "profileImage", "avatar"])
    val profilePicture: String? = null,
    @SerializedName(value = "isVerified", alternate = ["verified"])
    val isVerified: Boolean? = null
)

data class ChatPeerDto(
    @SerializedName(value = "id", alternate = ["userId"])
    val id: String? = null,
    @SerializedName(value = "username", alternate = ["userName"])
    val username: String? = null,
    @SerializedName(value = "fullName", alternate = ["displayName"])
    val fullName: String? = null,
    @SerializedName(value = "profilePicture", alternate = ["profileImageUrl", "profileImage", "avatar"])
    val profilePicture: String? = null,
    @SerializedName(value = "isVerified", alternate = ["verified"])
    val isVerified: Boolean? = null,
    @SerializedName(value = "isOnline", alternate = ["online"])
    val isOnline: Boolean? = null
) {
    fun resolvedName(): String = fullName?.trim()?.takeIf { it.isNotEmpty() }
        ?: username?.trim().orEmpty()
}

data class ChatParticipantDto(
    @SerializedName(value = "userId", alternate = ["id"])
    val userId: String? = null,
    @SerializedName(value = "userName", alternate = ["username"])
    val userName: String? = null,
    @SerializedName(value = "fullName", alternate = ["displayName"])
    val fullName: String? = null,
    @SerializedName(value = "profilePicture", alternate = ["profileImageUrl", "avatar"])
    val profilePicture: String? = null,
    @SerializedName(value = "isVerified", alternate = ["verified"])
    val isVerified: Boolean? = null
)

data class ChatMediaDto(
    @SerializedName(value = "url", alternate = ["filePath", "path"])
    val url: String? = null,
    @SerializedName(value = "id", alternate = ["mediaId", "key"])
    val id: String? = null,
    val type: String? = null,
    @SerializedName(value = "thumbnail", alternate = ["thumbnailUrl"])
    val thumbnail: String? = null,
    val duration: Any? = null,
    val fileName: String? = null,
    val fileSize: Any? = null,
    val width: Int? = null,
    val height: Int? = null,
    val mimeType: String? = null
) {
    /** Duration arrives as a number or a numeric string depending on the backend. */
    fun durationSeconds(): Double? = when (val raw = duration) {
        is Number -> raw.toDouble()
        is String -> raw.toDoubleOrNull()
        else -> null
    }
}

data class ChatMessageStatusDto(
    @SerializedName("messageId") val messageId: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("status") val status: String? = null
)

/** A single message. Field-alias resolution mirrors `ConversationMessage.swift`. */
data class ChatMessageDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("conversationId") val conversationId: String? = null,
    @SerializedName("sender") val sender: ChatSenderDto? = null,
    @SerializedName("senderId") val senderId: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("messageType") val messageType: String? = null,
    @SerializedName(value = "content", alternate = ["body"])
    val content: String? = null,
    @SerializedName(value = "media", alternate = ["medias"])
    val media: List<ChatMediaDto>? = null,
    @SerializedName("thumbnail") val thumbnail: String? = null,
    @SerializedName(value = "status", alternate = ["receiptStatus", "messageStatus"])
    val status: String? = null,
    @SerializedName("myStatus") val myStatus: ChatMessageStatusDto? = null,
    @SerializedName("statuses") val statuses: List<ChatMessageStatusDto>? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("sentAt") val sentAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
    @SerializedName("seenAt") val seenAt: String? = null,
    @SerializedName("deliveredAt") val deliveredAt: String? = null,
    @SerializedName("isEdited") val isEdited: Boolean? = null,
    @SerializedName("isDeleted") val isDeleted: Boolean? = null,
    @SerializedName("isPinned") val isPinned: Boolean? = null,
    @SerializedName(value = "clientMessageId", alternate = ["clientMsgId"])
    val clientMessageId: String? = null,
    @SerializedName("metadata") val metadata: Map<String, Any>? = null,
    /**
     * Kept as [Any] on purpose: the backend ships either a list of reaction groups or a
     * `{ count, data: [...] }` wrapper, and a strict type would make Gson reject the
     * whole page. Parsed by `ReactionParsing` in `ChatUiModels.kt`.
     */
    @SerializedName("reactions") val reactions: Any? = null,
    /** `replyTo` is an object, a bare id string, or absent — see [resolvedReply]. */
    @SerializedName("replyTo") val replyTo: Any? = null,
    @SerializedName("replyToId") val replyToId: Any? = null
) {
    /** Prefers the hydrated `replyTo` object, then a `replyToId` id string / object. */
    fun resolvedReply(): Any? {
        val direct = replyTo
        if (direct != null && !(direct is String && direct.isBlank())) return direct
        return replyToId
    }
}

data class ConversationLastMessageDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("messageType") val messageType: String? = null,
    @SerializedName(value = "contentPreview", alternate = ["content", "body"])
    val contentPreview: String? = null,
    @SerializedName("senderId") val senderId: String? = null,
    @SerializedName(value = "createdAt", alternate = ["sentAt"])
    val createdAt: String? = null,
    @SerializedName("status") val status: String? = null
)

data class ConversationSettingsDto(
    @SerializedName("isMuted") val isMuted: Boolean? = null,
    @SerializedName("isPinned") val isPinned: Boolean? = null,
    @SerializedName("isArchived") val isArchived: Boolean? = null,
    @SerializedName("isBlocked") val isBlocked: Boolean? = null,
    @SerializedName("unreadCount") val unreadCount: Int? = null
)

/** One inbox row from `GET /api/v1/chats`. */
data class ConversationRowDto(
    @SerializedName(value = "id", alternate = ["conversationId"])
    val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName(value = "lastMessageAt", alternate = ["createdAt"])
    val lastMessageAt: String? = null,
    @SerializedName(value = "avatar", alternate = ["avatarPath"])
    val avatar: String? = null,
    @SerializedName(value = "participants", alternate = ["members"])
    val participants: List<ChatParticipantDto>? = null,
    @SerializedName(value = "unreadCount", alternate = ["unread", "unread_count"])
    val unreadCount: Int? = null,
    @SerializedName("lastMessage") val lastMessage: ConversationLastMessageDto? = null,
    @SerializedName("settings") val settings: ConversationSettingsDto? = null,
    @SerializedName(value = "peer", alternate = ["participant"])
    val peer: ChatPeerDto? = null,
    @SerializedName("lastMessagePreview") val lastMessagePreview: String? = null,
    @SerializedName("lastMessageType") val lastMessageType: String? = null,
    @SerializedName("lastMessageSenderId") val lastMessageSenderId: String? = null,
    @SerializedName("lastMessageReceiptStatus") val lastMessageReceiptStatus: String? = null,
    @SerializedName("isPinned") val isPinned: Boolean? = null,
    @SerializedName("participantsCount") val participantsCount: Int? = null
)

data class ConversationsDataDto(
    @SerializedName("count") val count: Int? = null,
    @SerializedName(value = "rows", alternate = ["chats", "conversations", "items"])
    val rows: List<ConversationRowDto>? = null,
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("nextCursor") val nextCursor: String? = null
)

/** `GET /api/v1/chats` — tolerates `data:{rows}`, a bare list and top-level aliases. */
data class ConversationsResponseDto(
    @SerializedName(value = "success", alternate = ["status"]) val success: Boolean? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: ConversationsDataDto? = null,
    @SerializedName("rows") val rows: List<ConversationRowDto>? = null,
    @SerializedName("chats") val chats: List<ConversationRowDto>? = null,
    @SerializedName("conversations") val conversations: List<ConversationRowDto>? = null
) {
    fun resolvedRows(): List<ConversationRowDto> =
        data?.rows?.takeIf { it.isNotEmpty() }
            ?: rows
            ?: chats
            ?: conversations
            ?: data?.rows
            ?: emptyList()
}

data class ChatPaginationDto(
    @SerializedName("page") val page: Int? = null,
    @SerializedName("totalPages") val totalPages: Int? = null,
    @SerializedName("pageSize") val pageSize: Int? = null,
    @SerializedName("total") val total: Int? = null,
    @SerializedName("hasMore") val hasMore: Boolean? = null,
    @SerializedName("limit") val limit: Int? = null
) {
    fun resolvedHasMore(page: Int, received: Int, requested: Int): Boolean =
        hasMore ?: totalPages?.let { page < it } ?: (received >= requested)
}

data class MessagesPageDto(
    @SerializedName("conversationId") val conversationId: String? = null,
    @SerializedName(value = "messages", alternate = ["rows"])
    val messages: List<ChatMessageDto>? = null,
    @SerializedName("pinnedMessages") val pinnedMessages: List<ChatMessageDto>? = null,
    @SerializedName("pagination") val pagination: ChatPaginationDto? = null
)

data class MessagesDataDto(
    @SerializedName(value = "status", alternate = ["success"]) val status: Boolean? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: MessagesPageDto? = null,
    @SerializedName("conversationId") val conversationId: String? = null,
    @SerializedName(value = "messages", alternate = ["rows"])
    val messages: List<ChatMessageDto>? = null,
    @SerializedName("pinnedMessages") val pinnedMessages: List<ChatMessageDto>? = null,
    @SerializedName("pagination") val pagination: ChatPaginationDto? = null
) {
    /** Flattens both `{data:{messages}}` (legacy) and `{messages, pagination}` (FlirtTime). */
    fun resolvedPage(): MessagesPageDto = data ?: MessagesPageDto(
        conversationId = conversationId,
        messages = messages,
        pinnedMessages = pinnedMessages,
        pagination = pagination
    )
}

/** `GET /api/v1/chats/{id}/messages`. */
data class MessagesResponseDto(
    @SerializedName(value = "success", alternate = ["status"]) val success: Boolean? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: MessagesDataDto? = null
)

/** `data` of a send response: `{ message }`, `{ messages: [...] }`, or the message itself. */
data class SendMessageDataDto(
    @SerializedName("message") val message: ChatMessageDto? = null,
    @SerializedName("messages") val messages: List<ChatMessageDto>? = null,
    @SerializedName("id") val id: String? = null,
    @SerializedName("conversationId") val conversationId: String? = null,
    @SerializedName(value = "content", alternate = ["body"]) val content: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("messageType") val messageType: String? = null,
    @SerializedName(value = "createdAt", alternate = ["sentAt"]) val createdAt: String? = null,
    @SerializedName("sender") val sender: ChatSenderDto? = null,
    @SerializedName("status") val status: String? = null
) {
    fun toMessage(): ChatMessageDto? {
        message?.let { return it }
        messages?.firstOrNull()?.let { return it }
        val directId = id ?: return null
        return ChatMessageDto(
            id = directId,
            conversationId = conversationId,
            sender = sender,
            type = type ?: "text",
            messageType = messageType ?: "text",
            content = content,
            status = status ?: "sent",
            createdAt = createdAt
        )
    }
}

/** `POST /api/v1/chats/{id}/messages`. */
data class SendMessageResponseDto(
    @SerializedName(value = "success", alternate = ["status"]) val success: Boolean? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: SendMessageDataDto? = null
)

/** `PATCH /api/v1/chats/{c}/messages/{m}` — text edits only. */
data class EditMessageRequest(
    @SerializedName(value = "content", alternate = ["body"]) val content: String
)

/** `DELETE /api/v1/chats/{c}/messages/{m}` — `deleteType` is `me` or `everyone`. */
data class DeleteMessageRequest(
    @SerializedName("deleteType") val deleteType: String
)

/** `POST /api/v1/chats/{c}/messages/{m}/reactions`. */
data class ReactionRequest(
    @SerializedName("emoji") val emoji: String
)

/** `POST /api/v1/chats/{c}/messages/{m}/pin`. */
data class PinMessageRequest(
    @SerializedName("isPinned") val isPinned: Boolean,
    @SerializedName("pinned") val pinned: Boolean = isPinned,
    @SerializedName("scope") val scope: String? = null,
    @SerializedName("pinDuration") val pinDuration: String? = null
)

/** `POST /api/v1/chats/messages/forward`. */
data class ForwardMessagesRequest(
    @SerializedName("messageIds") val messageIds: List<String>,
    @SerializedName("recipientIds") val recipientIds: List<String>,
    @SerializedName("clientMessageId") val clientMessageId: String? = null
)

/** `data` of an edit / pin / reaction response (message, reactions or both). */
data class ChatActionDataDto(
    @SerializedName("message") val message: ChatMessageDto? = null,
    @SerializedName("reactions") val reactions: Any? = null,
    @SerializedName(value = "id", alternate = ["messageId"]) val id: String? = null,
    @SerializedName("isPinned") val isPinned: Boolean? = null,
    @SerializedName("pinned") val pinned: Boolean? = null
)

/** Lenient envelope for `PATCH /messages/{id}`, `POST .../reactions` and `POST .../pin`. */
data class ChatActionResponseDto(
    @SerializedName(value = "success", alternate = ["status"]) val success: Boolean? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: ChatActionDataDto? = null,
    @SerializedName("reactions") val reactions: Any? = null
) {
    /** Reactions arrive under `data.reactions`, top level, or embedded in `data.message`. */
    fun resolvedReactions(): Any? =
        data?.reactions ?: reactions ?: data?.message?.reactions
}

/** `POST /api/v1/chats/messages/forward` — payload shape varies, only success matters. */
data class ForwardMessagesResponseDto(
    @SerializedName(value = "success", alternate = ["status"]) val success: Boolean? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: Any? = null
)

/** One row of `GET /api/v1/chats/forward/recipients`. */
data class ForwardRecipientDto(
    @SerializedName(value = "userId", alternate = ["recipientId", "id", "conversationId"])
    val userId: String? = null,
    @SerializedName(value = "fullName", alternate = ["displayName", "name", "title"])
    val fullName: String? = null,
    @SerializedName(value = "userName", alternate = ["username"])
    val userName: String? = null,
    @SerializedName(value = "profilePicture", alternate = ["profileImageUrl", "profileImage", "avatar", "avatarUrl"])
    val profilePicture: String? = null,
    @SerializedName(value = "peer", alternate = ["user", "recipient", "participant"])
    val peer: ChatPeerDto? = null
)

/** `GET /api/v1/chats/forward/recipients?page&limit&search`. */
data class ForwardRecipientsResponseDto(
    @SerializedName(value = "success", alternate = ["status"]) val success: Boolean? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName(value = "recipients", alternate = ["users", "rows", "items"])
    val recipients: List<ForwardRecipientDto>? = null,
    @SerializedName("data") val data: ForwardRecipientsDataDto? = null,
    @SerializedName("hasMore") val hasMore: Boolean? = null,
    @SerializedName("total") val total: Int? = null
) {
    fun resolvedRecipients(): List<ForwardRecipientDto> =
        data?.recipients?.takeIf { it.isNotEmpty() } ?: recipients ?: emptyList()

    fun resolvedHasMore(received: Int, requested: Int): Boolean =
        data?.hasMore ?: hasMore
            ?: data?.pagination?.hasMore
            ?: total?.let { resolvedRecipients().size < it }
            ?: (received >= requested)
}

data class ForwardRecipientsDataDto(
    @SerializedName(value = "recipients", alternate = ["users", "rows", "items"])
    val recipients: List<ForwardRecipientDto>? = null,
    @SerializedName("hasMore") val hasMore: Boolean? = null,
    @SerializedName("pagination") val pagination: ChatPaginationDto? = null
)

/** Body of `POST /api/v1/chats/{id}/messages`. */
data class SendMessageRequest(
    @SerializedName(value = "content", alternate = ["body"])
    val content: String? = null,
    @SerializedName("messageType") val messageType: String = "text",
    @SerializedName("type") val type: String = "text",
    @SerializedName("clientMessageId") val clientMessageId: String? = null,
    @SerializedName("mediaIds") val mediaIds: List<String>? = null,
    @SerializedName("metadata") val metadata: Map<String, Any>? = null,
    @SerializedName("replyToId") val replyToId: String? = null
)

/** `PATCH /api/v1/chats/{id}/settings`. */
data class UpdateSettingsRequest(
    @SerializedName("isMuted") val isMuted: Boolean? = null,
    @SerializedName("isPinned") val isPinned: Boolean? = null,
    @SerializedName("isArchived") val isArchived: Boolean? = null
)

/** `PATCH /api/v1/chats/{id}/pin` — mirrors the iOS `isPinned` / `pinned` alias pair. */
data class PinConversationRequest(
    @SerializedName("isPinned") val isPinned: Boolean,
    @SerializedName("pinned") val pinned: Boolean = isPinned
)

/** `PATCH /api/v1/chat/conversations/{id}/archive` (fallback `/chats/{id}/archive`). */
data class ArchiveConversationRequest(
    @SerializedName("isArchived") val isArchived: Boolean,
    @SerializedName("archived") val archived: Boolean = isArchived
)

/** `DELETE /api/v1/chats/{id}` — `me` hides the thread, `everyone` removes it for all. */
data class DeleteConversationRequest(
    @SerializedName(value = "deleteType", alternate = ["scope"])
    val deleteType: String = "me"
)

/** `POST /api/v1/users/{id}/block`. */
data class BlockUserRequest(
    @SerializedName("reason") val reason: String = "Inappropriate behavior",
    @SerializedName("description") val description: String = reason,
    @SerializedName("conversationId") val conversationId: String? = null
)

/** Minimal ack envelope for fire-and-forget endpoints such as `POST /chats/{id}/read`. */
data class SimpleResponseDto(
    @SerializedName(value = "success", alternate = ["status"]) val success: Boolean? = null,
    @SerializedName("message") val message: String? = null
)

