package com.flirttime.feature.profile

import com.flirttime.feature.main.matches.DiscoveryFiltersDto
import com.google.gson.annotations.SerializedName

data class SearchPaginationDto(
    @SerializedName("page") val page: Int? = null,
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("total") val total: Int? = null,
    @SerializedName("totalPages") val totalPages: Int? = null,
    @SerializedName("hasMore") val hasMore: Boolean? = null
)

data class UserSearchItemDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("age") val age: Int? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("profilePhoto") val profilePhoto: String? = null,
    @SerializedName("profilePhotoUrl") val profilePhotoUrl: String? = null,
    @SerializedName("coverPhoto") val coverPhoto: String? = null,
    @SerializedName("coverPhotoUrl") val coverPhotoUrl: String? = null,
    @SerializedName("isVerified") val isVerified: Boolean? = null,
    @SerializedName("isSubscribed") val isSubscribed: Boolean? = null,
    @SerializedName("isOnline") val isOnline: Boolean? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("state") val state: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("location") val location: UserLocationDto? = null,
    @SerializedName("fullLocation") val fullLocation: String? = null,
    @SerializedName("distanceKm") val distanceKm: Double? = null,
    @SerializedName("distanceText") val distanceText: String? = null,
    @SerializedName("about") val about: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("interests") val interests: List<UserInterestDto>? = null,
    @SerializedName("sharedInterests") val sharedInterests: List<String>? = null,
    @SerializedName("momentCount") val momentCount: Int? = null,
    @SerializedName("photos") val photos: List<UserPhotoDto>? = null,
    @SerializedName("compatibility") val compatibility: UserCompatibilityDto? = null,
    @SerializedName("hasViewedQuickMoment") val hasViewedQuickMoment: Boolean? = null
) {
    val effectiveId: String
        get() = userId ?: id.orEmpty()

    val effectiveName: String
        get() = username ?: name.orEmpty()

    val effectivePhoto: String
        get() = profilePhotoUrl ?: profilePhoto.orEmpty()

    val effectiveBio: String
        get() = about ?: bio.orEmpty()

    val effectiveLocation: String
        get() {
            val dist = distanceText ?: distanceKm?.let { "${it.toInt()} km away" }
            val loc = fullLocation ?: location?.label ?: location?.name ?: city ?: ""
            return if (dist != null && loc.isNotBlank()) "$loc · $dist" else loc.ifBlank { dist ?: "" }
        }
}

data class UserLocationDto(
    @SerializedName("city") val city: String? = null,
    @SerializedName("state") val state: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("label") val label: String? = null,
    @SerializedName("fullLocation") val fullLocation: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null
)

data class UserInterestDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("interestId") val interestId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("emoji") val emoji: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("isActive") val isActive: Boolean? = null,
    @SerializedName("displayOrder") val displayOrder: Int? = null,
    @SerializedName("isCommon") val isCommon: Boolean? = null
) {
    val effectiveId: String
        get() = id ?: interestId ?: ""

    val effectiveEmoji: String
        get() = emoji ?: icon ?: "✨"
}

data class UserPhotoDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerializedName("key") val key: String? = null,
    @SerializedName("imageKey") val imageKey: String? = null,
    @SerializedName("isPrimary") val isPrimary: Boolean? = null,
    @SerializedName("displayOrder") val displayOrder: Int? = null
) {
    val effectiveKey: String?
        get() = imageKey ?: key
}

data class UserCompatibilityDto(
    @SerializedName("interestMatchPercentage") val interestMatchPercentage: Int? = null,
    @SerializedName("vibeScore") val vibeScore: Int? = null,
    @SerializedName("explanation") val explanation: String? = null
)

// 2. Full Other User Profile Response
data class OtherUserProfileResponseData(
    @SerializedName("id") val id: String? = null,
    @SerializedName("user") val user: UserSearchItemDto? = null,
    @SerializedName("profile") val profile: UserSearchItemDto? = null
) {
    val targetUser: UserSearchItemDto?
        get() = profile ?: user
}

// 3. Discovery Action Request / Response
data class DiscoveryActionRequest(
    @SerializedName("targetUserId") val targetUserId: String,
    @SerializedName("action") val action: String // "like", "nope", "super_like"
)

data class DiscoveryActionDailyQuotaDto(
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("used") val used: Int? = null,
    @SerializedName("remaining") val remaining: Int? = null,
    @SerializedName("resetsAt") val resetsAt: String? = null
)

data class DiscoveryActionResponseData(
    @SerializedName("action") val action: String? = null,
    @SerializedName("targetUserId") val targetUserId: String? = null,
    @SerializedName("isMatched") val isMatched: Boolean? = null,
    @SerializedName("matchId") val matchId: String? = null,
    @SerializedName("coinsDeducted") val coinsDeducted: Int? = null,
    @SerializedName("remainingCoins") val remainingCoins: Int? = null,
    @SerializedName("dailyQuota") val dailyQuota: DiscoveryActionDailyQuotaDto? = null,
    @SerializedName("message") val message: String? = null
)

// 4. Discovery People Response
data class DiscoveryPeopleResponseData(
    @SerializedName("filter") val filter: DiscoveryFiltersDto? = null,
    @SerializedName("users") val users: List<DiscoveryUserDto>? = null,
    @SerializedName("profiles") val profiles: List<DiscoveryUserDto>? = null,
    @SerializedName("data") val data: List<DiscoveryUserDto>? = null,
    @SerializedName("items") val items: List<DiscoveryUserDto>? = null,
    @SerializedName("pagination") val pagination: SearchPaginationDto? = null
) {
    fun getEffectiveUsers(): List<DiscoveryUserDto> = users ?: profiles ?: data ?: items ?: emptyList()
}

data class DiscoveryUserDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("age") val age: Int? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("about") val about: String? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("lookingFor") val lookingFor: String? = null,
    @SerializedName("profilePhoto") val profilePhoto: String? = null,
    @SerializedName("profilePhotoUrl") val profilePhotoUrl: String? = null,
    @SerializedName("primaryPhotoUrl") val primaryPhotoUrl: String? = null,
    @SerializedName("photos") val photos: List<UserPhotoDto>? = null,
    @SerializedName("isVerified") val isVerified: Boolean? = null,
    @SerializedName("isFaceVerified") val isFaceVerified: Boolean? = null,
    @SerializedName("isSubscribed") val isSubscribed: Boolean? = null,
    @SerializedName("isOnline") val isOnline: Boolean? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("distanceKm") val distanceKm: Double? = null,
    @SerializedName("distanceText") val distanceText: String? = null,
    @SerializedName("vibeScore") val vibeScore: Int? = null,
    @SerializedName("sharedInterests") val sharedInterests: List<String>? = null,
    @SerializedName("interestTags") val interestTags: List<String>? = null,
    @SerializedName("momentCount") val momentCount: Int? = null,
    @SerializedName("totalMoments") val totalMoments: Int? = null,
    @SerializedName("compatibility") val compatibility: UserCompatibilityDto? = null,
    @SerializedName("hasViewedQuickMoment") val hasViewedQuickMoment: Boolean? = null
) {
    val effectiveId: String
        get() = userId ?: id.orEmpty()

    val effectiveName: String
        get() = name ?: username.orEmpty()

    val effectivePhoto: String
        get() = primaryPhotoUrl ?: profilePhotoUrl ?: profilePhoto ?: photos?.firstOrNull()?.let { it.url ?: it.effectiveKey } ?: ""
}
