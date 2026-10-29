package com.flirttime.feature.quickmoments

import com.google.gson.annotations.SerializedName

data class QuickMomentsTrayResponseData(
    @SerializedName("items") val items: List<QuickMomentTrayItemDto>? = null,
    @SerializedName("stories") val stories: List<QuickMomentTrayItemDto>? = null,
    @SerializedName("tray") val tray: List<QuickMomentTrayItemDto>? = null,
    @SerializedName("ownBubble") val ownBubble: QuickMomentOwnBubbleDto? = null,
    @SerializedName("myStatus") val myStatus: QuickMomentOwnBubbleDto? = null
) {
    fun getEffectiveItems(): List<QuickMomentTrayItemDto> = items ?: stories ?: tray ?: emptyList()
    fun getEffectiveOwnBubble(): QuickMomentOwnBubbleDto? = ownBubble ?: myStatus
}

data class QuickMomentTrayItemDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("userName") val userName: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("profilePhoto") val profilePhoto: String? = null,
    @SerializedName("hasUnread") val hasUnread: Boolean? = null,
    @SerializedName("isViewed") val isViewed: Boolean? = null,
    @SerializedName("previewUrl") val previewUrl: String? = null,
    @SerializedName("storyCount") val storyCount: Int? = null,
    @SerializedName("relativeAge") val relativeAge: String? = null,
    @SerializedName("reactionBadge") val reactionBadge: String? = null
) {
    val effectiveId: String get() = id ?: userId.orEmpty()
    val effectiveName: String get() = displayName ?: userName ?: "User"
    val effectiveAvatar: String? get() = avatarUrl ?: profilePhoto ?: previewUrl
    val hasUnreadStories: Boolean get() = hasUnread == true || isViewed == false
}

data class QuickMomentOwnBubbleDto(
    @SerializedName("hasActiveStories") val hasActiveStories: Boolean? = null,
    @SerializedName("storyCount") val storyCount: Int? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
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
    @SerializedName("mediaUrl") val mediaUrl: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerializedName("caption") val caption: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("expiresAt") val expiresAt: String? = null,
    @SerializedName("relativeAge") val relativeAge: String? = null,
    @SerializedName("viewCount") val viewCount: Int? = null,
    @SerializedName("reactionCount") val reactionCount: Int? = null,
    @SerializedName("replyCount") val replyCount: Int? = null
) {
    val displayImageUrl: String?
        get() = mediaUrl ?: imageUrl ?: thumbnailUrl
}

data class QuickMomentReactionRequest(
    @SerializedName("reaction") val reaction: String
)

data class QuickMomentReplyRequest(
    @SerializedName("message") val message: String
)
