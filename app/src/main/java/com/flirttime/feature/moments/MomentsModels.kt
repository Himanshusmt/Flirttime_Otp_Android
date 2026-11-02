package com.flirttime.feature.moments

import com.flirttime.feature.moments.MomentPost
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class MomentsFeedResponseData(
    @SerializedName("type")
    val type: String? = null,
    @SerializedName("page")
    val page: Int? = null,
    @SerializedName("limit")
    val limit: Int? = null,
    @SerializedName("totalCount")
    val totalCount: Int? = null,
    @SerializedName("totalPages")
    val totalPages: Int? = null,
    @SerializedName("moments")
    val moments: List<MomentDto>? = null
)

data class MomentDto(
    @SerializedName("id")
    val id: String? = null,
    @SerializedName("momentId")
    val momentId: String? = null,
    @SerializedName("momentCount")
    val momentCount: Int? = null,
    @SerializedName("compatibility")
    val compatibility: MomentCompatibilityDto? = null,
    @SerializedName("caption")
    val caption: String? = null,
    @SerializedName("audience")
    val audience: String? = null,
    @SerializedName("publishedAt")
    val publishedAt: String? = null,
    @SerializedName("expiresAt")
    val expiresAt: String? = null,
    @SerializedName("relativeAge")
    val relativeAge: String? = null,
    @SerializedName("timeAgo")
    val timeAgo: String? = null,
    @SerializedName("locationLabel")
    val locationLabel: String? = null,
    /** Plain string or `{ "name": "Mumbai" }` (iOS `MomentLocation`). */
    @SerializedName("location")
    val location: JsonElement? = null,
    @SerializedName("creatorId")
    val creatorId: String? = null,
    @SerializedName("createdAt")
    val createdAt: String? = null,
    @SerializedName("photo")
    val photo: String? = null,
    @SerializedName("totalMomentGifts")
    val totalMomentGifts: Int? = null,
    @SerializedName("creator")
    val creator: MomentCreatorDto? = null,
    @SerializedName("media")
    val media: List<MomentMediaDto>? = null,
    @SerializedName("imageUrl")
    val imageUrl: String? = null,
    @SerializedName("mediaUrl")
    val mediaUrl: String? = null,
    @SerializedName("thumbnailUrl")
    val thumbnailUrl: String? = null,
    @SerializedName("firstImageUrl")
    val firstImageUrl: String? = null,
    @SerializedName("mediaCount")
    val mediaCount: Int? = null,
    @SerializedName("imageCount")
    val imageCount: Int? = null,
    @SerializedName("hasMultipleMedia")
    val hasMultipleMedia: Boolean? = null,
    @SerializedName("hasMultipleImages")
    val hasMultipleImages: Boolean? = null,
    @SerializedName("likesCount")
    val likesCount: Int? = null,
    @SerializedName("likeCount")
    val likeCount: Int? = null,
    @SerializedName("like_count")
    val likeCountSnake: Int? = null,
    @SerializedName("likes_count")
    val likesCountSnake: Int? = null,
    @SerializedName("commentsCount")
    val commentsCount: Int? = null,
    @SerializedName("commentCount")
    val commentCount: Int? = null,
    @SerializedName("comment_count")
    val commentCountSnake: Int? = null,
    @SerializedName("giftsCount")
    val giftsCount: Int? = null,
    @SerializedName("giftCount")
    val giftCount: Int? = null,
    @SerializedName("isLiked")
    val isLiked: Boolean? = null,
    @SerializedName("hasLiked")
    val hasLiked: Boolean? = null,
    @SerializedName("isLike")
    val isLike: Boolean? = null,
    @SerializedName("is_like")
    val isLikeSnake: Boolean? = null,
    @SerializedName("isLikedByMe")
    val isLikedByMe: Boolean? = null,
    @SerializedName("likedByMe")
    val likedByMe: Boolean? = null,
    @SerializedName("liked")
    val liked: Boolean? = null,
    @SerializedName("hasReacted")
    val hasReacted: Boolean? = null,
    @SerializedName("viewerReaction")
    val viewerReaction: String? = null
) {
    val locationName: String?
        get() {
            val element = location ?: return null
            if (element.isJsonNull) return null
            if (element.isJsonPrimitive) return element.asString.trim().takeIf { it.isNotEmpty() }
            if (!element.isJsonObject) return null
            val obj = element.asJsonObject
            return listOf("name", "label", "city").firstNotNullOfOrNull { key ->
                obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }
            }
        }

    val displayImageUrl: String?
        get() = firstImageUrl?.takeIf { it.isNotBlank() }
            ?: thumbnailUrl?.takeIf { it.isNotBlank() }
            ?: imageUrl?.takeIf { it.isNotBlank() }
            ?: mediaUrl?.takeIf { it.isNotBlank() }
            ?: media?.firstOrNull()?.let { it.imageUrl ?: it.mediaUrl ?: it.thumbnailUrl }

    val effectiveLikes: Int
        get() = likeCount ?: likesCount ?: likeCountSnake ?: likesCountSnake ?: 0

    val effectiveComments: Int
        get() = commentCount ?: commentsCount ?: commentCountSnake ?: 0

    val isMultiMedia: Boolean
        get() = hasMultipleImages == true || hasMultipleMedia == true || (imageCount ?: mediaCount ?: (media?.size ?: 0)) > 1
}

data class UserMomentsResponseData(
    @SerializedName("userId")
    val userId: String? = null,
    @SerializedName("momentCount")
    val momentCount: Int? = null,
    @SerializedName("compatibility")
    val compatibility: MomentCompatibilityDto? = null,
    @SerializedName("page")
    val page: Int? = null,
    @SerializedName("limit")
    val limit: Int? = null,
    @SerializedName("totalCount")
    val totalCount: Int? = null,
    @SerializedName("totalPages")
    val totalPages: Int? = null,
    @SerializedName("moments")
    val moments: List<MomentDto>? = null
)

data class MomentCompatibilityDto(
    @SerializedName("interestMatchPercentage")
    val interestMatchPercentage: Int? = null
)

data class MomentLocationDto(
    @SerializedName("name")
    val name: String? = null
)

data class MomentCreatorDto(
    @SerializedName("id")
    val id: String? = null,
    @SerializedName("creatorId")
    val creatorId: String? = null,
    @SerializedName("userId")
    val userId: String? = null,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("fullName")
    val fullName: String? = null,
    @SerializedName("username")
    val username: String? = null,
    @SerializedName("profilePhoto")
    val profilePhoto: String? = null,
    @SerializedName("profilePicture")
    val profilePicture: String? = null,
    @SerializedName("isVerified")
    val isVerified: Boolean? = null,
    @SerializedName("isFaceVerified")
    val isFaceVerified: Boolean? = null,
    @SerializedName("isSubscribed")
    val isSubscribed: Boolean? = null,
    @SerializedName("city")
    val city: String? = null,
    @SerializedName("location")
    val location: String? = null
)

data class MomentMediaDto(
    @SerializedName("mediaId")
    val mediaId: String? = null,
    @SerializedName("imageKey")
    val imageKey: String? = null,
    @SerializedName("imageUrl")
    val imageUrl: String? = null,
    @SerializedName("mediaUrl")
    val mediaUrl: String? = null,
    @SerializedName("thumbnailUrl")
    val thumbnailUrl: String? = null,
    @SerializedName("publicUrl")
    val publicUrl: String? = null,
    @SerializedName("url")
    val url: String? = null,
    @SerializedName("mediaType")
    val mediaType: String? = null,
    @SerializedName("displayOrder")
    val displayOrder: Int? = null,
    @SerializedName("orderIndex")
    val orderIndex: Int? = null
)

data class ToggleLikeRequest(
    @SerializedName("isLiked")
    val isLiked: Boolean? = null
)

data class MomentLikeResponseData(
    @SerializedName("isLiked")
    val isLiked: Boolean? = null,
    @SerializedName("hasLiked")
    val hasLiked: Boolean? = null,
    @SerializedName("liked")
    val liked: Boolean? = null,
    @SerializedName("is_like")
    val isLikeSnake: Boolean? = null,
    @SerializedName("likesCount")
    val likesCount: Int? = null,
    @SerializedName("likeCount")
    val likeCount: Int? = null,
    @SerializedName("likes_count")
    val likesCountSnake: Int? = null,
    @SerializedName("like_count")
    val likeCountSnake: Int? = null
) {
    val effectiveIsLiked: Boolean?
        get() = isLiked ?: hasLiked ?: liked ?: isLikeSnake

    val effectiveLikeCount: Int?
        get() = likesCount ?: likeCount ?: likesCountSnake ?: likeCountSnake
}

data class MomentsFeedResult(
    val moments: List<MomentPost>,
    val page: Int,
    val totalPages: Int,
    val totalCount: Int
)

/**
 * Maps an API moment onto the feed model exactly like iOS `MomentItem.vibe(fallbackAuthor:)`.
 * `fallback*` values fill in moments that arrive without a `creator` (e.g. `moments/me`).
 */
fun MomentDto.toUiItem(
    fallbackName: String? = null,
    fallbackAvatarUrl: String? = null,
    fallbackVerified: Boolean? = null,
    fallbackLocation: String? = null,
    fallbackUserId: String? = null
): MomentPost {
    val effectiveId = id?.takeIf { it.isNotBlank() } ?: momentId.orEmpty()

    // iOS author.displayName: username first, then full name.
    val displayName = creator?.username?.takeIf { it.isNotBlank() }
        ?: creator?.name?.takeIf { it.isNotBlank() }
        ?: creator?.fullName?.takeIf { it.isNotBlank() }
        ?: fallbackName?.takeIf { it.isNotBlank() }
        ?: "User"

    val avatar = creator?.profilePicture?.takeIf { it.isNotBlank() }
        ?: creator?.profilePhoto?.takeIf { it.isNotBlank() }
        ?: fallbackAvatarUrl?.takeIf { it.isNotBlank() }

    val verified = creator?.isVerified ?: creator?.isFaceVerified ?: fallbackVerified ?: false

    // Place tagged on the moment, then the author's own location.
    val loc = locationLabel?.takeIf { it.isNotBlank() }
        ?: locationName
        ?: creator?.city?.takeIf { it.isNotBlank() }
        ?: creator?.location?.takeIf { it.isNotBlank() }
        ?: fallbackLocation?.takeIf { it.isNotBlank() }

    // iOS shows its own relative time from createdAt ("now", "5m", "2h", "1d", "1w").
    val time = com.flirttime.core.common.IsoDate.timeAgo(createdAt ?: publishedAt)
        .ifBlank { timeAgo?.takeIf { it.isNotBlank() } ?: relativeAge?.takeIf { it.isNotBlank() }.orEmpty() }

    val locationAndTime = when {
        loc != null && time.isNotEmpty() -> "$loc · $time"
        loc != null -> loc
        else -> time
    }

    val extractedMediaUrls = media
        ?.sortedBy { it.orderIndex ?: it.displayOrder ?: 0 }
        ?.mapNotNull { item ->
            listOf(item.imageUrl, item.mediaUrl, item.publicUrl, item.url, item.thumbnailUrl)
                .firstOrNull { !it.isNullOrBlank() }
        }
        ?: emptyList()

    val fallbackMediaUrl = listOf(imageUrl, photo, thumbnailUrl, firstImageUrl, mediaUrl)
        .firstOrNull { !it.isNullOrBlank() }
    val finalMediaUrls = if (extractedMediaUrls.isNotEmpty()) extractedMediaUrls else listOfNotNull(fallbackMediaUrl)

    // iOS: isLiked ?? hasReacted ?? viewerReaction != nil
    val resolvedIsLiked = isLiked ?: hasLiked ?: isLikedByMe ?: likedByMe ?: liked ?: isLike ?: isLikeSnake
        ?: hasReacted ?: !viewerReaction.isNullOrBlank()

    val resolvedLikes = likesCount ?: likeCount ?: likeCountSnake ?: likesCountSnake ?: 0
    val resolvedComments = commentsCount ?: commentCount ?: commentCountSnake ?: 0
    val resolvedGifts = giftsCount ?: giftCount ?: totalMomentGifts ?: 0

    return MomentPost(
        id = effectiveId,
        userId = creator?.userId ?: creator?.id ?: creator?.creatorId ?: creatorId ?: fallbackUserId,
        username = displayName,
        avatarUrl = avatar,
        avatarRes = null,
        isVerified = verified,
        locationAndTime = locationAndTime,
        caption = caption?.trim().orEmpty(),
        mediaUrl = finalMediaUrls.firstOrNull(),
        mediaRes = null,
        mediaUrls = finalMediaUrls,
        currentMediaIndex = 0,
        likeCount = resolvedLikes,
        isLiked = resolvedIsLiked,
        commentCount = resolvedComments,
        giftCount = resolvedGifts
    )
}

// ----------------- Comments & Gifts DTOs -----------------

data class PostCommentRequest(
    @SerializedName("comment")
    val comment: String
)

data class SendMomentGiftRequest(
    @SerializedName("giftCode")
    val giftCode: String,
    @SerializedName("quantity")
    val quantity: Int = 1,
    @SerializedName("message")
    val message: String? = null
)

data class MomentCommentUserDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("fullName") val fullName: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("profilePicture") val profilePicture: String? = null,
    @SerializedName("profilePhoto") val profilePhoto: String? = null,
    @SerializedName("isVerified") val isVerified: Boolean? = null,
    @SerializedName("isFaceVerified") val isFaceVerified: Boolean? = null,
    @SerializedName("city") val city: String? = null
)

data class MomentCommentDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("commentId") val commentId: String? = null,
    @SerializedName("comment") val comment: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("timeAgo") val timeAgo: String? = null,
    @SerializedName("isMyComment") val isMyComment: Boolean? = null,
    @SerializedName("canDelete") val canDelete: Boolean? = null,
    @SerializedName("likesCount") val likesCount: Int? = null,
    @SerializedName("repliesCount") val repliesCount: Int? = null,
    @SerializedName("replyCount") val replyCount: Int? = null,
    @SerializedName("user") val user: MomentCommentUserDto? = null,
    @SerializedName("commenter") val commenter: MomentCommentUserDto? = null,
    @SerializedName("author") val author: MomentCommentUserDto? = null
)

data class MomentCommentsResponseData(
    @SerializedName("page") val page: Int? = null,
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("totalCount") val totalCount: Int? = null,
    @SerializedName("totalPages") val totalPages: Int? = null,
    @SerializedName("comments") val comments: List<MomentCommentDto>? = null
)

data class MomentGiftsCatalogResponseData(
    @SerializedName("coinBalance") val coinBalance: Int? = null,
    @SerializedName("coins") val coins: Int? = null,
    @SerializedName("balance") val balance: Int? = null,
    @SerializedName("gifts") val gifts: List<MomentGiftOptionDto>? = null
)

data class MomentGiftOptionDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("giftCode") val giftCode: String? = null,
    @SerializedName("code") val code: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("coinPrice") val coinPrice: Int? = null,
    @SerializedName("coins") val coins: Int? = null,
    @SerializedName("price") val price: Int? = null,
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("emoji") val emoji: String? = null
)

data class SendGiftResponseData(
    @SerializedName("remainingCoins") val remainingCoins: Int? = null,
    @SerializedName("newBalance") val newBalance: Int? = null,
    @SerializedName("coins") val coins: Int? = null,
    @SerializedName("giftCount") val giftCount: Int? = null,
    @SerializedName("giftsCount") val giftsCount: Int? = null
)

// ----------------- Received Gifts DTOs (Owner's View) -----------------

data class MomentReceivedGiftsResponseData(
    @SerializedName("totalGifts") val totalGifts: Int? = null,
    @SerializedName("totalCoins") val totalCoins: Int? = null,
    @SerializedName("giftCount") val giftCount: Int? = null,
    @SerializedName("coins") val coins: Int? = null,
    @SerializedName("page") val page: Int? = null,
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("totalCount") val totalCount: Int? = null,
    @SerializedName("totalPages") val totalPages: Int? = null,
    @SerializedName("gifts") val gifts: List<ReceivedGiftDto>? = null,
    @SerializedName("items") val items: List<ReceivedGiftDto>? = null
) {
    val effectiveGifts: List<ReceivedGiftDto>
        get() = gifts ?: items ?: emptyList()

    val effectiveTotalGifts: Int
        get() = totalGifts ?: giftCount ?: totalCount ?: effectiveGifts.size

    val effectiveTotalCoins: Int
        get() = totalCoins ?: coins ?: effectiveGifts.sumOf { it.effectiveCoins }
}

data class ReceivedGiftDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("giftId") val giftId: String? = null,
    @SerializedName("giftCode") val giftCode: String? = null,
    @SerializedName("giftName") val giftName: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("coins") val coins: Int? = null,
    @SerializedName("coinPrice") val coinPrice: Int? = null,
    @SerializedName("quantity") val quantity: Int? = null,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("sender") val sender: GiftSenderDto? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("timeAgo") val timeAgo: String? = null
) {
    val displayName: String
        get() = giftName?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: "Gift"

    val effectiveCoins: Int
        get() = coins ?: coinPrice ?: 1

    val senderName: String
        get() = sender?.name?.takeIf { it.isNotBlank() }
            ?: sender?.fullName?.takeIf { it.isNotBlank() }
            ?: sender?.username?.takeIf { it.isNotBlank() }
            ?: "User"

    val senderAvatar: String?
        get() = sender?.profilePicture?.takeIf { it.isNotBlank() }
            ?: sender?.profilePhoto?.takeIf { it.isNotBlank() }
            ?: sender?.avatarUrl?.takeIf { it.isNotBlank() }
}

data class GiftSenderDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("fullName") val fullName: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("profilePicture") val profilePicture: String? = null,
    @SerializedName("profilePhoto") val profilePhoto: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null
)

// ----------------- Moment likes (GET moments/{id}/likes) -----------------

data class MomentLikePersonDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("fullName") val fullName: String? = null,
    @SerializedName("profilePicture") val profilePicture: String? = null,
    @SerializedName("profilePhoto") val profilePhoto: String? = null,
    @SerializedName("profilePhotoUrl") val profilePhotoUrl: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("isVerified") val isVerified: Boolean? = null,
    @SerializedName("city") val city: String? = null
) {
    val displayName: String
        get() = username?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() }
            ?: fullName?.takeIf { it.isNotBlank() } ?: "Someone"

    val photo: String?
        get() = listOf(profilePicture, profilePhoto, profilePhotoUrl, avatar)
            .firstOrNull { !it.isNullOrBlank() }?.trim()
}

data class MomentLikeDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("reaction") val reaction: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("user") val user: MomentLikePersonDto? = null
)

data class MomentLikesResponseData(
    @SerializedName("totalLikes") val totalLikes: Int? = null,
    @SerializedName("page") val page: Int? = null,
    @SerializedName("totalPages") val totalPages: Int? = null,
    @SerializedName("likes") val likes: List<MomentLikeDto>? = null
)
