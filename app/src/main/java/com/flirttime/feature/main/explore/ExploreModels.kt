package com.flirttime.feature.main.explore

import com.flirttime.feature.profile.UserSearchItemDto
import com.google.gson.annotations.SerializedName

data class InteractionsResponseData(
    @SerializedName("type") val type: String? = null,
    @SerializedName("isLocked") val isLocked: Boolean? = null,
    @SerializedName("totalCount") val totalCount: Int? = null,
    @SerializedName("items") val items: List<InteractionItemDto>? = null,
    @SerializedName("pagination") val pagination: InteractionPaginationDto? = null
)

data class InteractionPaginationDto(
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("nextCursor") val nextCursor: String? = null,
    @SerializedName("hasMore") val hasMore: Boolean? = null
)

data class InteractionItemDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("user") val user: UserSearchItemDto? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("age") val age: Int? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("profilePhoto") val profilePhoto: String? = null,
    @SerializedName("profilePhotoUrl") val profilePhotoUrl: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("distanceKm") val distanceKm: Double? = null,
    @SerializedName("distanceText") val distanceText: String? = null,
    @SerializedName("isVerified") val isVerified: Boolean? = null,
    @SerializedName("isSubscribed") val isSubscribed: Boolean? = null,
    @SerializedName("compliment") val compliment: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null
) {
    val effectiveId: String
        get() = user?.effectiveId ?: userId ?: id.orEmpty()

    val effectiveUsername: String
        get() = user?.effectiveName ?: username ?: name.orEmpty()

    val effectiveAge: Int?
        get() = user?.age ?: age

    val effectivePhoto: String?
        get() = user?.effectivePhoto?.takeIf { it.isNotBlank() } ?: profilePhotoUrl ?: profilePhoto

    val effectiveLocation: String
        get() = user?.effectiveLocation?.takeIf { it.isNotBlank() } ?: city ?: "Nearby"

    val effectiveDistance: String?
        get() = user?.distanceText ?: distanceText ?: distanceKm?.let { "${it.toInt()} km away" }
}

fun InteractionItemDto.toExploreProfileItem(isLocked: Boolean = false): ExploreProfileItem {
    return ExploreProfileItem(
        id = effectiveId,
        username = effectiveUsername,
        age = effectiveAge,
        hasRibbonBadge = isVerified ?: user?.isVerified ?: true,
        distance = effectiveDistance,
        location = effectiveLocation,
        imageUrl = if (isLocked) null else effectivePhoto,
        isPlaceholder = isLocked || effectivePhoto.isNullOrBlank()
    )
}
