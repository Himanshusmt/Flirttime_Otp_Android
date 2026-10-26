package com.flirttime.feature.moments.data.model

import com.flirttime.feature.main.presentation.moments.MomentPost
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
    @SerializedName("mediaCount")
    val mediaCount: Int? = null,
    @SerializedName("hasMultipleMedia")
    val hasMultipleMedia: Boolean? = null,
    @SerializedName("likesCount")
    val likesCount: Int? = null,
    @SerializedName("commentsCount")
    val commentsCount: Int? = null,
    @SerializedName("giftsCount")
    val giftsCount: Int? = null,
    @SerializedName("isLiked")
    val isLiked: Boolean? = null
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

    val fallbackMediaUrl = imageUrl?.takeIf { it.isNotBlank() } ?: mediaUrl?.takeIf { it.isNotBlank() }
    val finalMediaUrls = if (extractedMediaUrls.isNotEmpty()) extractedMediaUrls else listOfNotNull(fallbackMediaUrl)

    return MomentPost(
        id = effectiveId,
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
        likeCount = likesCount ?: 0,
        isLiked = isLiked ?: false,
        commentCount = commentsCount ?: 0,
        giftCount = giftsCount ?: 0
    )
}
