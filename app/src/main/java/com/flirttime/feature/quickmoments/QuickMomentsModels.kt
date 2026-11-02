package com.flirttime.feature.quickmoments

import com.google.gson.annotations.SerializedName

data class QuickMomentsTrayResponseData(
    @SerializedName("myStatus") val myStatus: QuickMomentOwnBubbleDto? = null,
    @SerializedName("otherUsers") val otherUsers: List<QuickMomentOtherUserTrayDto>? = null,
    @SerializedName("items") val items: List<QuickMomentTrayItemDto>? = null,
    @SerializedName("stories") val stories: List<QuickMomentTrayItemDto>? = null,
    @SerializedName("tray") val tray: List<QuickMomentTrayItemDto>? = null,
    @SerializedName("ownBubble") val ownBubble: QuickMomentOwnBubbleDto? = null
) {
    fun getEffectiveItems(): List<QuickMomentTrayItemDto> = items ?: stories ?: tray ?: emptyList()
    fun getEffectiveOwnBubble(): QuickMomentOwnBubbleDto? = myStatus ?: ownBubble
}

data class QuickMomentPreviewDto(
    @SerializedName("type") val type: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("backgroundColor") val backgroundColor: String? = null,
    @SerializedName("imageKey") val imageKey: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerializedName("mediaUrl") val mediaUrl: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null
) {
    val displayImageUrl: String?
        get() = mediaUrl?.ifBlank { null } ?: imageUrl?.ifBlank { null } ?: thumbnailUrl?.ifBlank { null }
}

data class QuickMomentBadgeDto(
    @SerializedName("text") val text: String? = null,
    @SerializedName("topEmojis") val topEmojis: List<String>? = null,
    @SerializedName("count") val count: Int? = null
)

data class QuickMomentOtherUserTrayDto(
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("isVerified") val isVerified: Boolean? = null,
    @SerializedName("isSubscribed") val isSubscribed: Boolean? = null,
    @SerializedName("hasUnread") val hasUnread: Boolean? = null,
    @SerializedName("totalStories") val totalStories: Int? = null,
    @SerializedName("totalMoments") val totalMoments: Int? = null,
    @SerializedName("unreadCount") val unreadCount: Int? = null,
    @SerializedName("latestTimeAgo") val latestTimeAgo: String? = null,
    @SerializedName("firstMomentId") val firstMomentId: String? = null,
    @SerializedName("preview") val preview: QuickMomentPreviewDto? = null,
    @SerializedName("badge") val badge: QuickMomentBadgeDto? = null,
    // Flexible fallbacks
    @SerializedName("id") val id: String? = null,
    @SerializedName("user") val user: StoryViewerUserDto? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("userName") val userName: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("fullName") val fullName: String? = null,
    @SerializedName("profilePhoto") val profilePhoto: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("previewUrl") val previewUrl: String? = null,
    @SerializedName("previewText") val previewText: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("caption") val caption: String? = null,
    @SerializedName("backgroundColor") val backgroundColor: String? = null,
    @SerializedName("previewBackground") val previewBackground: String? = null,
    @SerializedName("relativeAge") val relativeAge: String? = null,
    @SerializedName("stories") val stories: List<QuickMomentSlideDto>? = null
) {
    val effectiveId: String get() = firstMomentId?.ifBlank { null } ?: id?.ifBlank { null } ?: userId.orEmpty()
    val effectiveUserId: String get() = userId ?: user?.effectiveId.orEmpty()
    val effectiveName: String get() = name?.ifBlank { null } ?: displayName?.ifBlank { null } ?: fullName?.ifBlank { null } ?: user?.effectiveName ?: username ?: userName ?: "User"
    val effectiveAvatar: String? get() = avatar?.ifBlank { null } ?: profilePhoto?.ifBlank { null } ?: avatarUrl?.ifBlank { null } ?: user?.effectiveAvatar
    val effectiveText: String? get() = preview?.text?.ifBlank { null } ?: previewText?.ifBlank { null } ?: text?.ifBlank { null } ?: caption?.ifBlank { null }
    val effectiveMediaUrl: String? get() = preview?.displayImageUrl ?: previewUrl?.ifBlank { null }
    val effectiveBackground: String? get() = preview?.backgroundColor?.ifBlank { null } ?: backgroundColor?.ifBlank { null } ?: previewBackground
    val effectiveTimeAgo: String? get() = latestTimeAgo?.ifBlank { null } ?: relativeAge ?: "1h"
}

data class QuickMomentTrayItemDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("user") val user: StoryViewerUserDto? = null,
    @SerializedName("userName") val userName: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("fullName") val fullName: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("profilePhoto") val profilePhoto: String? = null,
    @SerializedName("hasUnread") val hasUnread: Boolean? = null,
    @SerializedName("isViewed") val isViewed: Boolean? = null,
    @SerializedName("firstMomentId") val firstMomentId: String? = null,
    @SerializedName("preview") val preview: QuickMomentPreviewDto? = null,
    @SerializedName("previewUrl") val previewUrl: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("mediaUrl") val mediaUrl: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("caption") val caption: String? = null,
    @SerializedName("previewText") val previewText: String? = null,
    @SerializedName("backgroundColor") val backgroundColor: String? = null,
    @SerializedName("previewBackground") val previewBackground: String? = null,
    @SerializedName("relativeAge") val relativeAge: String? = null,
    @SerializedName("latestTimeAgo") val latestTimeAgo: String? = null,
    @SerializedName("stories") val stories: List<QuickMomentSlideDto>? = null
) {
    val effectiveId: String get() = firstMomentId?.ifBlank { null } ?: id?.ifBlank { null } ?: userId.orEmpty()
    val effectiveUserId: String get() = userId ?: user?.effectiveId.orEmpty()
    val effectiveName: String get() = name?.ifBlank { null } ?: user?.effectiveName ?: displayName?.ifBlank { null } ?: fullName?.ifBlank { null } ?: username ?: userName ?: "User"
    val effectiveAvatar: String? get() = avatar?.ifBlank { null } ?: user?.effectiveAvatar ?: avatarUrl?.ifBlank { null } ?: profilePhoto?.ifBlank { null } ?: previewUrl
    val effectiveText: String? get() = preview?.text?.ifBlank { null } ?: text?.ifBlank { null } ?: previewText?.ifBlank { null } ?: caption?.ifBlank { null }
    val effectiveMediaUrl: String? get() = preview?.displayImageUrl ?: previewUrl?.ifBlank { null } ?: mediaUrl?.ifBlank { null } ?: imageUrl
    val effectiveBackground: String? get() = preview?.backgroundColor?.ifBlank { null } ?: backgroundColor?.ifBlank { null } ?: previewBackground
    val effectiveTimeAgo: String? get() = latestTimeAgo?.ifBlank { null } ?: relativeAge ?: "1h"
    val hasUnreadStories: Boolean get() = hasUnread == true || isViewed == false
}

data class QuickMomentOwnBubbleDto(
    @SerializedName("hasActive") val hasActive: Boolean? = null,
    @SerializedName("totalActive") val totalActive: Int? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("addLabel") val addLabel: String? = null,
    @SerializedName("latestTimeAgo") val latestTimeAgo: String? = null,
    @SerializedName("badge") val badge: Any? = null,
    @SerializedName("stories") val stories: List<QuickMomentSlideDto>? = null,
    @SerializedName("user") val user: StoryViewerUserDto? = null,
    @SerializedName("hasActiveStories") val hasActiveStories: Boolean? = null,
    @SerializedName("canAdd") val canAdd: Boolean? = null
)

data class MyQuickMomentsResponseData(
    @SerializedName("items") val items: List<QuickMomentSlideDto>? = null,
    @SerializedName("stories") val stories: List<QuickMomentSlideDto>? = null,
    @SerializedName("totalCount") val totalCount: Int? = null,
    @SerializedName("canAdd") val canAdd: Boolean? = null,
    @SerializedName("reactionSummary") val reactionSummary: String? = null
) {
    fun getEffectiveStories(): List<QuickMomentSlideDto> = items ?: stories ?: emptyList()
}

data class QuickMomentSlideDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("caption") val caption: String? = null,
    @SerializedName("imageKey") val imageKey: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerializedName("mediaUrl") val mediaUrl: String? = null,
    @SerializedName("backgroundColor") val backgroundColor: String? = null,
    @SerializedName("textColor") val textColor: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("expiresAt") val expiresAt: String? = null,
    @SerializedName("relativeAge") val relativeAge: String? = null,
    @SerializedName("viewsCount") val viewsCount: Int? = null,
    @SerializedName("viewCount") val viewCount: Int? = null,
    @SerializedName("reactionsCount") val reactionsCount: Int? = null,
    @SerializedName("totalReactions") val totalReactions: Int? = null,
    @SerializedName("reactionCount") val reactionCount: Int? = null,
    @SerializedName("repliesCount") val repliesCount: Int? = null,
    @SerializedName("replyCount") val replyCount: Int? = null,
    @SerializedName("locationLabel") val locationLabel: String? = null,
    @SerializedName("totalReplies") val totalReplies: Int? = null,
    /** `["😍"]`, `[{emoji, count}]` or `"😍🔥"` - same shapes iOS `QuickMoment.emojis(in:)` accepts. */
    @SerializedName("topReactions", alternate = ["topEmojis"]) val topReactions: com.google.gson.JsonElement? = null
) {
    /** Reactions + replies on the owner's own Quick Moment (iOS `activity` badge count). */
    val activityCount: Int
        get() = (reactionsCount ?: reactionCount ?: totalReactions ?: 0) +
            (repliesCount ?: replyCount ?: totalReplies ?: 0)

    /** Most used reaction emojis first. */
    val topReactionEmojis: List<String>
        get() {
            val element = topReactions ?: return emptyList()
            return when {
                element.isJsonArray -> {
                    val items = element.asJsonArray.toList()
                    if (items.all { it.isJsonPrimitive }) {
                        items.map { it.asString }.filter { it.isNotBlank() }
                    } else {
                        items.filter { it.isJsonObject }
                            .map { it.asJsonObject }
                            .sortedByDescending { it.get("count")?.takeIf { c -> c.isJsonPrimitive }?.asInt ?: 0 }
                            .mapNotNull { (it.get("emoji") ?: it.get("reaction"))?.takeIf { e -> e.isJsonPrimitive }?.asString }
                            .filter { it.isNotBlank() }
                    }
                }
                element.isJsonPrimitive -> {
                    val text = element.asString
                    val result = mutableListOf<String>()
                    var i = 0
                    while (i < text.length) {
                        val cp = text.codePointAt(i)
                        val len = Character.charCount(cp)
                        if (cp > 0x2000 && !Character.isLetterOrDigit(cp) && !Character.isWhitespace(cp)) {
                            result.add(text.substring(i, i + len))
                        }
                        i += len
                    }
                    result
                }
                else -> emptyList()
            }
        }

    val displayImageUrl: String?
        get() = mediaUrl?.ifBlank { null } ?: imageUrl?.ifBlank { null } ?: thumbnailUrl?.ifBlank { null }

    val effectiveText: String?
        get() = text?.ifBlank { null } ?: caption?.ifBlank { null }

    val effectiveViewCount: Int
        get() = viewsCount ?: viewCount ?: 0

    val isTextStory: Boolean
        get() = type.equals("text", ignoreCase = true) || (!effectiveText.isNullOrBlank() && displayImageUrl.isNullOrBlank())
}

data class CreateTextQuickMomentRequest(
    @SerializedName("type") val type: String = "text",
    @SerializedName("text") val text: String,
    @SerializedName("backgroundColor") val backgroundColor: String = "#EA3355",
    @SerializedName("textColor") val textColor: String = "#ffffff"
)

data class QuickMomentReactionRequest(
    @SerializedName("reaction") val reaction: String
)

data class QuickMomentReplyRequest(
    @SerializedName("message") val message: String
)

data class QuickMomentViewersResponseData(
    @SerializedName("viewers") val viewers: List<StoryViewerItemDto>? = null,
    @SerializedName("totalViews") val totalViews: Int? = null
)

data class StoryViewerItemDto(
    @SerializedName("viewedAt") val viewedAt: String? = null,
    @SerializedName("timeAgo") val timeAgo: String? = null,
    @SerializedName("viewer") val viewer: StoryViewerUserDto? = null
)

data class StoryViewerUserDto(
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("id") val id: String? = null,
    @SerializedName("_id") val mongoId: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("userName") val userName: String? = null,
    @SerializedName("fullName") val fullName: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("firstName") val firstName: String? = null,
    @SerializedName("profilePhoto") val profilePhoto: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("photoUrl") val photoUrl: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("isVerified") val isVerified: Boolean? = null
) {
    val effectiveId: String get() = userId ?: id ?: mongoId.orEmpty()
    val effectiveName: String get() = displayName ?: name ?: fullName ?: firstName ?: username ?: userName ?: "User"
    val effectiveAvatar: String? get() = profilePhoto ?: avatarUrl ?: photoUrl ?: avatar
}

data class QuickMomentReactionsResponseData(
    @SerializedName("totalReactions") val totalReactions: Int? = null,
    @SerializedName("breakdown") val breakdown: List<ReactionCountDto>? = null,
    @SerializedName("reactions") val reactions: List<StoryReactionItemDto>? = null
)

data class ReactionCountDto(
    @SerializedName("emoji") val emoji: String,
    @SerializedName("count") val count: Int
)

data class StoryReactionItemDto(
    @SerializedName("emoji") val emoji: String? = null,
    @SerializedName("reactedAt") val reactedAt: String? = null,
    @SerializedName("timeAgo") val timeAgo: String? = null,
    @SerializedName("reactor") val reactor: StoryViewerUserDto? = null
)

sealed class QuickMomentCardItem {
    data class AddNewCard(
        val userProfilePhoto: String? = null
    ) : QuickMomentCardItem()

    data class StoryCard(
        val storyId: String,
        val userId: String? = null,
        val userName: String,
        val userAvatar: String? = null,
        val relativeAge: String? = null,
        val textContent: String? = null,
        val mediaUrl: String? = null,
        val backgroundColor: String? = null,
        val isOwnStory: Boolean = false,
        val hasUnread: Boolean = true,
        val viewCount: Int? = null,
        val originalSlide: QuickMomentSlideDto? = null
    ) : QuickMomentCardItem()
}
