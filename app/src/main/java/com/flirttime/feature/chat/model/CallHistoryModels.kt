package com.flirttime.feature.chat.model

import com.flirttime.feature.chat.util.ChatTimeFormatter
import com.google.gson.annotations.SerializedName

/** Wire user summary for call history peers. */
data class CallHistoryUserDto(
    @SerializedName(value = "id", alternate = ["userId"])
    val id: String? = null,
    @SerializedName(value = "fullName", alternate = ["displayName", "name"])
    val fullName: String? = null,
    @SerializedName(value = "userName", alternate = ["username"])
    val userName: String? = null,
    @SerializedName(value = "profilePicture", alternate = ["profileImageUrl", "profileImage", "avatar", "avatarUrl"])
    val profilePicture: String? = null
) {
    fun displayName(): String =
        fullName?.trim()?.takeIf { it.isNotEmpty() }
            ?: userName?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Unknown"
}

/**
 * One call history row from `GET /api/v1/calls`.
 * Neutral naming — REST history only; no Agora / VoIP fields required for UI.
 */
data class CallHistoryDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("conversationId") val conversationId: String? = null,
    @SerializedName(value = "type", alternate = ["callType"])
    val type: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("callerId") val callerId: String? = null,
    @SerializedName("calleeId") val calleeId: String? = null,
    @SerializedName("caller") val caller: CallHistoryUserDto? = null,
    @SerializedName("callee") val callee: CallHistoryUserDto? = null,
    @SerializedName("user") val user: CallHistoryUserDto? = null,
    @SerializedName("direction") val direction: String? = null,
    @SerializedName("participants") val participants: List<CallHistoryUserDto>? = null,
    @SerializedName("startedAt") val startedAt: String? = null,
    @SerializedName("endedAt") val endedAt: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("duration") val duration: Any? = null,
    @SerializedName(value = "conversationTitle", alternate = ["title"])
    val conversationTitle: String? = null,
    @SerializedName("isGroup") val isGroup: Boolean? = null,
    @SerializedName("conversationType") val conversationType: String? = null
)

data class CallHistoryDataDto(
    @SerializedName(value = "calls", alternate = ["items", "history", "results", "rows"])
    val calls: List<CallHistoryDto>? = null,
    @SerializedName("page") val page: Int? = null,
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("pageSize") val pageSize: Int? = null,
    @SerializedName("total") val total: Int? = null,
    @SerializedName("totalPages") val totalPages: Int? = null,
    @SerializedName("hasMore") val hasMore: Boolean? = null,
    @SerializedName("pagination") val pagination: ChatPaginationDto? = null
)

data class CallHistoryResponseDto(
    @SerializedName(value = "success", alternate = ["status"])
    val success: Boolean? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: CallHistoryDataDto? = null,
    @SerializedName(value = "calls", alternate = ["items", "history", "results", "rows"])
    val calls: List<CallHistoryDto>? = null,
    @SerializedName("page") val page: Int? = null,
    @SerializedName("hasMore") val hasMore: Boolean? = null
) {
    fun resolvedCalls(): List<CallHistoryDto> =
        data?.calls?.takeIf { it.isNotEmpty() }
            ?: calls
            ?: data?.calls
            ?: emptyList()

    fun resolvedHasMore(page: Int, limit: Int, received: Int): Boolean {
        val pagination = data?.pagination
        data?.hasMore?.let { return it }
        hasMore?.let { return it }
        pagination?.hasMore?.let { return it }
        val totalPages = data?.totalPages ?: pagination?.totalPages
        if (totalPages != null) return page < totalPages
        val total = data?.total ?: pagination?.total
        val pageSize = data?.limit ?: data?.pageSize ?: pagination?.pageSize ?: pagination?.limit ?: limit
        if (total != null && pageSize > 0) return page * pageSize < total
        return received >= limit
    }
}

data class CallHistoryPage(
    val calls: List<CallHistoryDto>,
    val page: Int,
    val hasMore: Boolean
)

enum class CallHistoryDirection {
    INCOMING,
    OUTGOING,
    MISSED
}

enum class CallHistoryMedia {
    AUDIO,
    VIDEO
}

data class CallHistoryUi(
    val id: String,
    val conversationId: String,
    val peerId: String,
    val peerName: String,
    val peerAvatar: String?,
    val direction: CallHistoryDirection,
    val media: CallHistoryMedia,
    val isGroup: Boolean,
    val timestampLabel: String,
    val durationLabel: String?
) {
    val isMissed: Boolean get() = direction == CallHistoryDirection.MISSED
    val isVideo: Boolean get() = media == CallHistoryMedia.VIDEO

    fun subtitle(): String {
        val kind = when (direction) {
            CallHistoryDirection.INCOMING -> "Incoming"
            CallHistoryDirection.OUTGOING -> "Outgoing"
            CallHistoryDirection.MISSED -> "Missed"
        }
        val mediaLabel = if (isVideo) "Video" else "Audio"
        return if (durationLabel.isNullOrBlank()) {
            "$kind $mediaLabel"
        } else {
            "$kind $mediaLabel · $durationLabel"
        }
    }
}

fun CallHistoryDto.toUi(currentUserId: String): CallHistoryUi? {
    val callId = id?.trim().orEmpty()
    if (callId.isEmpty()) return null

    val me = currentUserId.trim()
    val typeRaw = type?.trim()?.lowercase().orEmpty()
    val media = if (typeRaw == "video") CallHistoryMedia.VIDEO else CallHistoryMedia.AUDIO

    val apiDirection = direction?.trim()?.lowercase().orEmpty()
    val statusRaw = status?.trim()?.lowercase().orEmpty()
    val isOutgoing = apiDirection == "outgoing" ||
        (me.isNotEmpty() && callerId?.trim().equals(me, ignoreCase = true))

    val peer = resolvePeer(me)
    val peerId = peer?.id?.trim()?.takeIf { it.isNotEmpty() }
        ?: if (isOutgoing) calleeId?.trim().orEmpty() else callerId?.trim().orEmpty()

    val group = isGroup == true ||
        conversationType.equals("group", ignoreCase = true) ||
        conversationType.equals("group_call", ignoreCase = true) ||
        (participants?.size ?: 0) > 2

    val name = when {
        group -> conversationTitle?.trim()?.takeIf { it.isNotEmpty() } ?: "Group Call"
        else -> peer?.displayName()?.takeIf { it != "Unknown" }
            ?: conversationTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Unknown"
    }

    val directionUi = when {
        apiDirection == "missed" || statusRaw == "missed" -> CallHistoryDirection.MISSED
        !isOutgoing && statusRaw in setOf("declined", "rejected", "busy") -> CallHistoryDirection.MISSED
        apiDirection == "incoming" -> CallHistoryDirection.INCOMING
        isOutgoing -> CallHistoryDirection.OUTGOING
        else -> CallHistoryDirection.INCOMING
    }

    val timestamp = startedAt ?: createdAt ?: endedAt
    return CallHistoryUi(
        id = callId,
        conversationId = conversationId?.trim().orEmpty(),
        peerId = peerId,
        peerName = name,
        peerAvatar = peer?.profilePicture,
        direction = directionUi,
        media = media,
        isGroup = group,
        timestampLabel = ChatTimeFormatter.inboxRelativeLabel(timestamp),
        durationLabel = resolveDurationLabel()
    )
}

private fun CallHistoryDto.resolvePeer(meId: String): CallHistoryUserDto? {
    fun notMe(candidate: CallHistoryUserDto?): CallHistoryUserDto? {
        val id = candidate?.id?.trim().orEmpty()
        if (candidate == null) return null
        if (meId.isNotEmpty() && id.equals(meId, ignoreCase = true)) return null
        return candidate
    }

    if (meId.isNotEmpty() && callerId?.trim().equals(meId, ignoreCase = true)) {
        return notMe(callee) ?: notMe(user) ?: participants?.firstOrNull {
            !it.id.orEmpty().equals(meId, ignoreCase = true)
        }
    }
    if (meId.isNotEmpty() && calleeId?.trim().equals(meId, ignoreCase = true)) {
        return notMe(caller) ?: notMe(user) ?: participants?.firstOrNull {
            !it.id.orEmpty().equals(meId, ignoreCase = true)
        }
    }
    return notMe(user) ?: notMe(caller) ?: notMe(callee)
        ?: participants?.firstOrNull { !it.id.orEmpty().equals(meId, ignoreCase = true) }
}

private fun CallHistoryDto.resolveDurationLabel(): String? {
    val parsedDuration = when (val raw = duration) {
        is Number -> raw.toDouble()
        is String -> raw.toDoubleOrNull()
        else -> null
    }
    val seconds = parsedDuration?.takeIf { it > 0 }
        ?: run {
            val start = ChatTimeFormatter.parse(startedAt)?.time ?: return null
            val end = ChatTimeFormatter.parse(endedAt)?.time ?: return null
            ((end - start) / 1000.0).takeIf { it > 0 }
        }
        ?: return null
    val total = seconds.toInt().coerceAtLeast(0)
    val minutes = total / 60
    val remainder = total % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val mins = minutes % 60
        String.format("%d:%02d:%02d", hours, mins, remainder)
    } else {
        String.format("%d:%02d", minutes, remainder)
    }
}
