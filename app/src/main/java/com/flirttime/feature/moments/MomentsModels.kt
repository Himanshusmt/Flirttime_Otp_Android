package com.flirttime.feature.moments

import com.flirttime.feature.moments.MomentPost
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
    @SerializedName("location")
    val location: MomentLocationDto? = null,
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
    @SerializedName("commentsCount")
    val commentsCount: Int? = null,
    @SerializedName("commentCount")
    val commentCount: Int? = null,
    @SerializedName("giftsCount")
    val giftsCount: Int? = null,
    @SerializedName("isLiked")
    val isLiked: Boolean? = null
) {
    val displayImageUrl: String?
        get() = firstImageUrl?.takeIf { it.isNotBlank() }
            ?: thumbnailUrl?.takeIf { it.isNotBlank() }
            ?: imageUrl?.takeIf { it.isNotBlank() }
            ?: mediaUrl?.takeIf { it.isNotBlank() }
            ?: media?.firstOrNull()?.let { it.imageUrl ?: it.mediaUrl ?: it.thumbnailUrl }

    val effectiveLikes: Int
        get() = likeCount ?: likesCount ?: 0

    val effectiveComments: Int
        get() = commentCount ?: commentsCount ?: 0

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
    @SerializedName("mediaType")
    val mediaType: String? = null,
    @SerializedName("displayOrder")
    val displayOrder: Int? = null
)

data class ToggleLikeRequest(
    @SerializedName("isLiked")
    val isLiked: Boolean? = null
)

data class MomentsFeedResult(
    val moments: List<MomentPost>,
    val page: Int,
    val totalPages: Int,
    val totalCount: Int
)

fun MomentDto.toUiItem(): MomentPost {
    val effectiveId = id ?: momentId.orEmpty()
    val displayName = creator?.fullName?.takeIf { it.isNotBlank() }
        ?: creator?.name?.takeIf { it.isNotBlank() }
        ?: creator?.username?.takeIf { it.isNotBlank() }
        ?: "User"

    val avatar = creator?.profilePicture?.takeIf { it.isNotBlank() }
        ?: creator?.profilePhoto?.takeIf { it.isNotBlank() }

    val verified = (creator?.isVerified == true) || (creator?.isFaceVerified == true)

    val loc = locationLabel?.takeIf { it.isNotBlank() }
        ?: location?.name?.takeIf { it.isNotBlank() }
        ?: creator?.city?.takeIf { it.isNotBlank() }
        ?: creator?.location?.takeIf { it.isNotBlank() }

    val time = timeAgo?.takeIf { it.isNotBlank() }
        ?: relativeAge?.takeIf { it.isNotBlank() }

    val locationAndTime = when {
        loc != null && time != null -> "$loc · $time"
        loc != null -> loc
        time != null -> time
        else -> ""
    }

    val extractedMediaUrls = media
        ?.sortedBy { it.displayOrder ?: 0 }
        ?.mapNotNull { it.imageUrl?.takeIf { url -> url.isNotBlank() } ?: it.mediaUrl?.takeIf { url -> url.isNotBlank() } }
        ?: emptyList()

    val fallbackMediaUrl = firstImageUrl?.takeIf { it.isNotBlank() }
        ?: thumbnailUrl?.takeIf { it.isNotBlank() }
        ?: imageUrl?.takeIf { it.isNotBlank() }
        ?: mediaUrl?.takeIf { it.isNotBlank() }
    val finalMediaUrls = if (extractedMediaUrls.isNotEmpty()) extractedMediaUrls else listOfNotNull(fallbackMediaUrl)

    return MomentPost(
        id = effectiveId,
        userId = creator?.userId ?: creator?.creatorId ?: creator?.id,
        username = displayName,
        avatarUrl = avatar,
        avatarRes = null,
        isVerified = verified,
        locationAndTime = locationAndTime,
        caption = caption.orEmpty(),
        mediaUrl = finalMediaUrls.firstOrNull(),
        mediaRes = null,
        mediaUrls = finalMediaUrls,
        currentMediaIndex = 0,
        likeCount = likeCount ?: likesCount ?: 0,
        isLiked = isLiked ?: false,
        commentCount = commentCount ?: commentsCount ?: 0,
        giftCount = giftsCount ?: 0
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
