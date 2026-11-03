package com.flirttime.feature.chat.model

import com.flirttime.core.navigation.ChatMember
import com.flirttime.feature.chat.util.ChatTimeFormatter

/** Inbox row rendered by `ConversationAdapter`. */
data class ConversationUi(
    val id: String,
    val title: String,
    val avatarUrl: String?,
    val isPinned: Boolean,
    val isMuted: Boolean,
    val isVerified: Boolean,
    val isOnline: Boolean,
    val isGroup: Boolean,
    val lastMessagePreview: String,
    val lastMessageAtRaw: String?,
    val timestampLabel: String,
    val unreadCount: Int,
    val isLastMessageFromMe: Boolean,
    val isLastMessageRead: Boolean,
    /** Peer (non-me) participant of a direct chat — required for blocking. */
    val peerUserId: String? = null,
    val peerUserName: String? = null,
    val isArchived: Boolean = false,
    val isBlocked: Boolean = false,
    /** Known participants of a group thread (empty for direct chats). */
    val members: List<ChatMember> = emptyList()
)

enum class DeliveryState { NONE, SENDING, SENT, DELIVERED, READ, FAILED }

enum class MessageKind { TEXT, IMAGE, VIDEO, AUDIO, LOCATION, UNSUPPORTED }

/** One aggregated emoji reaction group under a bubble. */
data class ReactionUi(
    val emoji: String,
    val count: Int,
    val reactedByMe: Boolean,
    val userIds: List<String> = emptyList(),
    val names: List<String> = emptyList()
) {
    /** Toggles my reaction locally, exactly like the iOS optimistic update. */
    fun toggleMine(userId: String, userName: String?): ReactionUi? {
        if (!reactedByMe) {
            return copy(
                count = count + 1,
                reactedByMe = true,
                userIds = (userIds + userId).distinct(),
                names = (names + listOfNotNull(userName)).distinct()
            )
        }
        val nextCount = (count - 1).coerceAtLeast(0)
        if (nextCount == 0) return null
        return copy(count = nextCount, reactedByMe = false, userIds = userIds - userId)
    }
}

/** Quoted message shown inside a reply bubble. */
data class ReplyPreviewUi(
    val id: String,
    val senderName: String?,
    val body: String,
    val kind: MessageKind,
    val isMine: Boolean
)

/** A single chat bubble (or a centred system notice). */
data class MessageUi(
    val id: String,
    val conversationId: String,
    val body: String,
    val kind: MessageKind,
    val mediaUrl: String?,
    val isMine: Boolean,
    val isSystem: Boolean,
    val senderName: String?,
    /** Sender identity, needed to draw avatars in group threads. */
    val senderId: String? = null,
    val senderAvatarUrl: String? = null,
    val senderIsVerified: Boolean = false,
    val createdAtRaw: String?,
    val timeLabel: String,
    val deliveryState: DeliveryState,
    val pendingId: String? = null,
    val isFailed: Boolean = false,
    /** Local file path while a picked attachment is still uploading. */
    val localUri: String? = null,
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0L,
    val mediaWidth: Int = 0,
    val mediaHeight: Int = 0,
    val reactions: List<ReactionUi> = emptyList(),
    val isPinned: Boolean = false,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    /** Per-recipient receipt status (`userId -> read|delivered|sent`), used by Message Info. */
    val receipts: Map<String, String> = emptyMap(),
    val reply: ReplyPreviewUi? = null,
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,
    val locationLabel: String? = null
) {
    val isMedia: Boolean get() =
        kind != MessageKind.TEXT && kind != MessageKind.LOCATION && kind != MessageKind.UNSUPPORTED

    /** True while the bubble is an unacknowledged local temp (`local-uuid`). */
    val isOptimisticTemporary: Boolean get() = id.startsWith("local-")

    /** iOS shows at most four pills, most-reacted first. */
    fun visibleReactions(): List<ReactionUi> =
        reactions.sortedByDescending { it.count }.take(MAX_REACTION_PILLS)

    companion object {
        const val MAX_REACTION_PILLS = 4
    }
}

sealed interface MessageRow {
    data class Separator(val label: String) : MessageRow
    data class Message(val data: MessageUi) : MessageRow
}

private const val TYPE_TEXT = "text"
private const val TYPE_IMAGE = "image"
private const val TYPE_VIDEO = "video"
private const val TYPE_AUDIO = "audio"
private const val TYPE_VOICE = "voice"
private const val TYPE_LOCATION = "location"
private const val TYPE_SYSTEM = "system"

private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".heic")
private val AUDIO_EXTENSIONS = listOf(".m4a", ".mp3", ".aac", ".wav", ".ogg", ".opus")

fun ConversationRowDto.toUi(currentUserId: String): ConversationUi? {
    val conversationId = id?.trim().orEmpty()
    if (conversationId.isEmpty()) return null

    val peerUser: ChatParticipantDto? = participants?.firstOrNull { it.userId != currentUserId }
    val resolvedTitle = title?.trim()?.takeIf { it.isNotEmpty() }
        ?: peer?.resolvedName()?.takeIf { it.isNotEmpty() }
        ?: peerUser?.fullName?.trim()?.takeIf { it.isNotEmpty() }
        ?: peerUser?.userName?.trim()?.takeIf { it.isNotEmpty() }
        ?: "Chat"

    val resolvedAvatar = avatar?.takeIf { it.isNotBlank() }
        ?: peer?.profilePicture?.takeIf { it.isNotBlank() }
        ?: peerUser?.profilePicture?.takeIf { it.isNotBlank() }

    val resolvedUnread = unreadCount ?: settings?.unreadCount ?: 0
    val senderOfLastMessage = lastMessage?.senderId ?: lastMessageSenderId
    val fromMe = !senderOfLastMessage.isNullOrEmpty() && senderOfLastMessage == currentUserId

    val preview = buildLastMessagePreview(
        type = lastMessage?.messageType ?: lastMessageType,
        content = lastMessage?.contentPreview ?: lastMessagePreview
    )

    return ConversationUi(
        id = conversationId,
        title = resolvedTitle,
        avatarUrl = resolvedAvatar,
        isPinned = settings?.isPinned ?: (isPinned == true),
        isMuted = settings?.isMuted == true,
        isVerified = peer?.isVerified == true || peerUser?.isVerified == true,
        isOnline = peer?.isOnline == true,
        isGroup = type.equals("group", ignoreCase = true) || (participantsCount ?: 0) > 2,
        lastMessagePreview = preview,
        lastMessageAtRaw = lastMessageAt ?: lastMessage?.createdAt,
        timestampLabel = ChatTimeFormatter.inboxRelativeLabel(lastMessageAt ?: lastMessage?.createdAt),
        unreadCount = resolvedUnread,
        isLastMessageFromMe = fromMe,
        isLastMessageRead = isReadReceipt(lastMessage?.status ?: lastMessageReceiptStatus),
        peerUserId = peer?.id?.takeIf { it.isNotBlank() }
            ?: peerUser?.userId?.takeIf { it.isNotBlank() },
        peerUserName = peer?.username?.takeIf { it.isNotBlank() }
            ?: peerUser?.userName?.takeIf { it.isNotBlank() },
        isArchived = settings?.isArchived == true,
        isBlocked = settings?.isBlocked == true,
        members = participants.orEmpty().mapNotNull { participant ->
            val userId = participant.userId?.trim().orEmpty()
            if (userId.isEmpty()) return@mapNotNull null
            ChatMember(
                userId = userId,
                name = participant.fullName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: participant.userName?.trim().orEmpty(),
                avatarUrl = participant.profilePicture?.takeIf { it.isNotBlank() },
                isVerified = participant.isVerified == true
            )
        }
    )
}

private fun buildLastMessagePreview(type: String?, content: String?): String {
    val trimmed = content?.trim().orEmpty()
    return when (type?.lowercase()) {
        TYPE_IMAGE -> "Photo"
        TYPE_VIDEO -> "Video"
        TYPE_AUDIO, TYPE_VOICE -> "Voice message"
        TYPE_LOCATION -> trimmed.ifEmpty { "📍 Location" }
        TYPE_SYSTEM -> trimmed
        else -> trimmed
    }.let { if (it.isEmpty()) "Say hi to start the conversation" else it }
}

private fun isReadReceipt(status: String?): Boolean =
    status.equals("read", ignoreCase = true) || status.equals("seen", ignoreCase = true)

fun ChatMessageDto.toUi(currentUserId: String, fallbackConversationId: String): MessageUi {
    val resolvedKind = resolveKind()
    val resolvedSenderId = sender?.id ?: senderId
    val isMine = !resolvedSenderId.isNullOrEmpty() && resolvedSenderId == currentUserId
    val resolvedType = (messageType ?: type)?.lowercase()
    val firstMedia = media?.firstOrNull()

    val thumbnailUrl = (this.metadata?.stringValue("thumbnailUrl"))
        ?: firstMedia?.thumbnail?.takeIf { it.isNotBlank() }
    val remoteMediaUrl = firstMedia?.url?.takeIf { it.isNotBlank() }
        ?: thumbnail
        ?: this.metadata?.stringValue("imageUrl")
        ?: this.metadata?.stringValue("mediaUrl")

    val durationSeconds = firstMedia?.durationSeconds()
        ?: this.metadata?.doubleValue("audio_duration", "audioDuration", "duration")

    val width = firstMedia?.width ?: this.metadata?.intValue("width") ?: 0
    val height = firstMedia?.height ?: this.metadata?.intValue("height") ?: 0

    val locationLat = metadata?.doubleValue("latitude", "lat")
    val locationLng = metadata?.doubleValue("longitude", "lng", "lon")
    val locationName = metadata?.stringValue("name")
    val locationAddress = metadata?.stringValue("address")
        ?: locationName
        ?: content?.trim()?.takeIf { resolvedKind == MessageKind.LOCATION && it.isNotEmpty() }

    return MessageUi(
        id = id ?: clientMessageId ?: "",
        conversationId = conversationId?.takeIf { it.isNotBlank() } ?: fallbackConversationId,
        body = resolvedBody(resolvedKind, firstMedia?.id, isMine),
        kind = resolvedKind,
        mediaUrl = remoteMediaUrl,
        isMine = isMine,
        isSystem = resolvedType == TYPE_SYSTEM,
        senderName = sender?.fullName?.trim()?.takeIf { it.isNotEmpty() }
            ?: sender?.userName?.trim()?.takeIf { it.isNotEmpty() },
        senderId = resolvedSenderId?.takeIf { it.isNotBlank() },
        senderAvatarUrl = sender?.profilePicture?.takeIf { it.isNotBlank() },
        senderIsVerified = sender?.isVerified == true,
        createdAtRaw = createdAt ?: sentAt,
        timeLabel = ChatTimeFormatter.messageTimeLabel(createdAt ?: sentAt),
        deliveryState = resolveDeliveryState(isMine),
        pendingId = clientMessageId,
        thumbnailUrl = thumbnailUrl,
        durationMs = ((durationSeconds ?: 0.0) * 1000).toLong(),
        mediaWidth = width,
        mediaHeight = height,
        reactions = reactions.parseReactions(currentUserId),
        isPinned = isPinned == true,
        isEdited = isEdited == true,
        isDeleted = isDeleted == true,
        receipts = resolvedReceipts(),
        reply = resolvedReply().parseReply(currentUserId),
        locationLatitude = locationLat,
        locationLongitude = locationLng,
        locationLabel = locationAddress?.takeIf { it.isNotBlank() }
    )
}

/** `statuses[]` → `userId -> status`, powering the Message Info sheet. */
private fun ChatMessageDto.resolvedReceipts(): Map<String, String> =
    statuses.orEmpty()
        .mapNotNull { status ->
            val userId = status.userId?.trim().orEmpty()
            val value = status.status?.trim().orEmpty()
            if (userId.isEmpty() || value.isEmpty()) null else userId to value.lowercase()
        }
        .toMap()

private fun ChatMessageDto.resolveKind(): MessageKind {
    val resolvedType = (messageType ?: type)?.lowercase()
    return when {
        resolvedType == TYPE_IMAGE -> MessageKind.IMAGE
        resolvedType == TYPE_VIDEO -> MessageKind.VIDEO
        resolvedType == TYPE_AUDIO || resolvedType == TYPE_VOICE -> MessageKind.AUDIO
        resolvedType == TYPE_LOCATION -> MessageKind.LOCATION
        resolvedType == TYPE_TEXT || resolvedType.isNullOrEmpty() -> MessageKind.TEXT
        isDeleted == true -> MessageKind.TEXT
        else -> MessageKind.UNSUPPORTED
    }
}

private fun ChatMessageDto.resolvedBody(kind: MessageKind, mediaId: String?, isMine: Boolean): String {
    if (isDeleted == true) return if (isMine) "You deleted this message" else "This message was deleted"
    val trimmed = content?.trim().orEmpty()
    return when (kind) {
        MessageKind.TEXT -> trimmed
        MessageKind.LOCATION -> {
            metadata?.stringValue("address")
                ?: metadata?.stringValue("name")
                ?: trimmed.ifEmpty { "📍 Location" }
        }
        MessageKind.UNSUPPORTED -> trimmed.ifEmpty { "Unsupported message" }
        // Media bubbles carry the URL/key in `content`; only real captions are shown.
        else -> trimmed.takeIf {
            !it.startsWith("http", ignoreCase = true) &&
                !it.equals(mediaId, ignoreCase = true)
        }.orEmpty()
    }
}

/** Parses `reactions` in its list form or the `{ count, data: [...] }` wrapper form. */
internal fun Any?.parseReactions(currentUserId: String): List<ReactionUi> {
    val groups: Collection<*> = when (val raw = this) {
        is Collection<*> -> raw
        is Map<*, *> -> (raw["reactions"] ?: raw["data"] ?: raw["items"]) as? Collection<*> ?: emptyList<Any>()
        else -> emptyList<Any>()
    }

    return groups.mapNotNull { group ->
        when (group) {
            is String -> group.trim().takeIf { it.isNotEmpty() }?.let { ReactionUi(it, 1, false) }
            is Map<*, *> -> {
                val emoji = group["emoji"]?.toString()?.trim().orEmpty()
                if (emoji.isEmpty()) return@mapNotNull null
                val rawUsers = (group["users"] ?: group["userIds"] ?: group["reactedBy"])
                    as? Collection<*>
                val userIds = rawUsers.orEmpty().mapNotNull { it.reactionUserId() }.distinct()
                val names = rawUsers.orEmpty().mapNotNull { it.reactionUserName() }.distinct()
                val storedCount = when (val count = group["count"]) {
                    is Number -> count.toInt()
                    is String -> count.toIntOrNull()
                    else -> null
                }
                val reactedByMe = (group["reactedByMe"] as? Boolean)
                    ?: (currentUserId.isNotEmpty() && userIds.contains(currentUserId))
                ReactionUi(
                    emoji = emoji,
                    count = maxOf(userIds.size, storedCount ?: 0),
                    reactedByMe = reactedByMe,
                    userIds = userIds,
                    names = names
                )
            }
            else -> null
        }
    }.filter { it.count > 0 }
}

/** Parses a quoted message: full object, bare id string, or `{ id, content, sender }`. */
internal fun Any?.parseReply(currentUserId: String): ReplyPreviewUi? = when (val raw = this) {
    is String -> raw.trim().takeIf { it.isNotEmpty() }?.let {
        ReplyPreviewUi(id = it, senderName = null, body = "", kind = MessageKind.TEXT, isMine = false)
    }
    is Map<*, *> -> {
        val id = listOf("id", "messageId", "_id")
            .firstNotNullOfOrNull { key -> raw[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
        if (id == null) {
            null
        } else {
            val sender = raw["sender"]
            val senderId = when (sender) {
                is String -> sender
                is Map<*, *> -> listOf("id", "userId", "_id")
                    .firstNotNullOfOrNull { key -> sender[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
                else -> null
            } ?: raw["senderId"]?.toString()
            val senderName = (sender as? Map<*, *>)?.let { map ->
                listOf("fullName", "displayName", "full_name", "userName", "user_name", "username")
                    .firstNotNullOfOrNull { key ->
                        map[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    }
            }
            val content = (raw["content"] ?: raw["body"])?.toString()?.trim().orEmpty()
            val type = ((raw["type"] ?: raw["messageType"])?.toString() ?: "").lowercase()
            val kind = when (type) {
                TYPE_IMAGE -> MessageKind.IMAGE
                TYPE_VIDEO -> MessageKind.VIDEO
                TYPE_AUDIO, TYPE_VOICE -> MessageKind.AUDIO
                TYPE_LOCATION -> MessageKind.LOCATION
                TYPE_TEXT, "" -> MessageKind.TEXT
                else -> MessageKind.UNSUPPORTED
            }
            ReplyPreviewUi(
                id = id,
                senderName = senderName,
                body = when (kind) {
                    MessageKind.TEXT -> content
                    MessageKind.LOCATION -> content.ifEmpty { "📍 Location" }
                    else -> ""
                },
                kind = kind,
                isMine = !senderId.isNullOrEmpty() && senderId == currentUserId
            )
        }
    }
    else -> null
}

private fun Any?.reactionUserId(): String? = when (this) {
    is String -> trim().takeIf { it.isNotEmpty() }
    is Map<*, *> -> listOf("userId", "user_id", "id")
        .firstNotNullOfOrNull { key -> this[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
    else -> null
}

private fun Any?.reactionUserName(): String? = when (this) {
    is Map<*, *> -> listOf("fullName", "full_name", "userName", "user_name", "username")
        .firstNotNullOfOrNull { key -> this[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
    else -> null
}

private fun Map<String, Any>?.stringValue(key: String): String? =
    (this?.get(key) as? String)?.takeIf { it.isNotBlank() }

private fun Map<String, Any>?.doubleValue(vararg keys: String): Double? {
    val source = this ?: return null
    for (key in keys) {
        val value = when (val raw = source[key]) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        }
        if (value != null) return value
    }
    return null
}

private fun Map<String, Any>?.intValue(key: String): Int? =
    when (val raw = this?.get(key)) {
        is Number -> raw.toInt()
        is String -> raw.toIntOrNull()
        else -> null
    }

private fun ChatMessageDto.resolveDeliveryState(isMine: Boolean): DeliveryState {
    if (!isMine) return DeliveryState.NONE
    val lowered = status?.lowercase()
    val readBySomeone = myStatus?.status?.equals("read", ignoreCase = true) == true ||
        statuses?.any { it.status.equals("read", ignoreCase = true) } == true ||
        !seenAt.isNullOrEmpty()

    return when {
        lowered == "failed" || lowered == "error" -> DeliveryState.FAILED
        lowered == "read" || lowered == "seen" -> DeliveryState.READ
        readBySomeone -> DeliveryState.READ
        lowered == "delivered" || !deliveredAt.isNullOrEmpty() -> DeliveryState.DELIVERED
        lowered == "sending" || lowered == "pending" -> DeliveryState.SENDING
        else -> DeliveryState.SENT
    }
}

/** One selectable row of the forward recipient picker. */
data class ForwardRecipientUi(
    val userId: String,
    val name: String,
    val userName: String?,
    val avatarUrl: String?
)

fun ForwardRecipientDto.toUi(): ForwardRecipientUi? {
    val resolvedId = userId?.trim().orEmpty().ifEmpty { peer?.id?.trim().orEmpty() }
    if (resolvedId.isEmpty()) return null

    val resolvedName = fullName?.trim()?.takeIf { it.isNotEmpty() }
        ?: peer?.fullName?.trim()?.takeIf { it.isNotEmpty() }
        ?: userName?.trim()?.takeIf { it.isNotEmpty() }
        ?: peer?.username?.trim().orEmpty()
    val resolvedUserName = userName?.trim()?.takeIf { it.isNotEmpty() }
        ?: peer?.username?.trim()?.takeIf { it.isNotEmpty() }
    val resolvedAvatar = profilePicture?.takeIf { it.isNotBlank() }
        ?: peer?.profilePicture?.takeIf { it.isNotBlank() }

    return ForwardRecipientUi(
        userId = resolvedId,
        name = resolvedName.ifEmpty { "User" },
        userName = resolvedUserName,
        avatarUrl = resolvedAvatar
    )
}

@Suppress("unused")
internal fun looksLikeImage(url: String?): Boolean =
    url != null && IMAGE_EXTENSIONS.any { url.lowercase().contains(it) }

@Suppress("unused")
internal fun looksLikeAudio(url: String?): Boolean =
    url != null && AUDIO_EXTENSIONS.any { url.lowercase().contains(it) }
