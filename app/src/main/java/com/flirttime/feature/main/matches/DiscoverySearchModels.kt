package com.flirttime.feature.main.matches

import com.flirttime.core.common.MediaUrlHelper
import com.google.gson.annotations.SerializedName

// 1. Search Request & Filters
data class DiscoverySearchRequest(
    @SerializedName("page") val page: Int = 1,
    @SerializedName("limit") val limit: Int = 20,
    @SerializedName("genders") val genders: List<String>? = null,
    @SerializedName("ageMin") val ageMin: Int? = null,
    @SerializedName("ageMax") val ageMax: Int? = null,
    @SerializedName("maxDistanceKm") val maxDistanceKm: Int? = null,
    @SerializedName("lookingFor") val lookingFor: List<String>? = null,
    @SerializedName("interestIds") val interestIds: List<String>? = null,
    @SerializedName("verifiedOnly") val verifiedOnly: Boolean? = false,
    @SerializedName("onlineOnly") val onlineOnly: Boolean? = false
)

data class DiscoveryFiltersDto(
    @SerializedName("genders") val genders: List<String>? = null,
    @SerializedName("ageMin") val ageMin: Int? = null,
    @SerializedName("ageMax") val ageMax: Int? = null,
    @SerializedName("maxDistanceKm") val maxDistanceKm: Int? = null,
    @SerializedName("lookingFor") val lookingFor: List<String>? = null,
    @SerializedName("interestIds") val interestIds: List<String>? = null,
    @SerializedName("verifiedOnly") val verifiedOnly: Boolean? = null,
    @SerializedName("onlineOnly") val onlineOnly: Boolean? = null
)

data class DiscoveryFiltersResponseData(
    @SerializedName("filters") val filters: DiscoveryFiltersDto? = null,
    @SerializedName("genders") val genders: List<String>? = null,
    @SerializedName("ageMin") val ageMin: Int? = null,
    @SerializedName("ageMax") val ageMax: Int? = null,
    @SerializedName("maxDistanceKm") val maxDistanceKm: Int? = null,
    @SerializedName("lookingFor") val lookingFor: List<String>? = null,
    @SerializedName("interestIds") val interestIds: List<String>? = null,
    @SerializedName("verifiedOnly") val verifiedOnly: Boolean? = null,
    @SerializedName("onlineOnly") val onlineOnly: Boolean? = null
) {
    fun toEffectiveFilters(): DiscoveryFiltersDto {
        return filters ?: DiscoveryFiltersDto(
            genders = genders,
            ageMin = ageMin,
            ageMax = ageMax,
            maxDistanceKm = maxDistanceKm,
            lookingFor = lookingFor,
            interestIds = interestIds,
            verifiedOnly = verifiedOnly,
            onlineOnly = onlineOnly
        )
    }
}

// 2. Search Response
data class DiscoverySearchResponseData(
    @SerializedName("profiles") val profiles: List<DiscoveryProfileCardDto>? = null,
    @SerializedName("users") val users: List<DiscoveryProfileCardDto>? = null,
    @SerializedName("data") val data: List<DiscoveryProfileCardDto>? = null,
    @SerializedName("items") val items: List<DiscoveryProfileCardDto>? = null,
    @SerializedName("pagination") val pagination: DiscoverySearchPaginationDto? = null
) {
    fun getEffectiveProfiles(): List<DiscoveryProfileCardDto> = profiles ?: users ?: data ?: items ?: emptyList()
}

data class DiscoverySearchPaginationDto(
    @SerializedName("page") val page: Int = 1,
    @SerializedName("limit") val limit: Int = 10,
    @SerializedName("totalCount") val totalCount: Int = 0,
    @SerializedName("totalPages") val totalPages: Int = 1,
    @SerializedName("hasMore") val hasMore: Boolean = false
)

data class DiscoveryCardPhotoDto(
    @SerializedName("url") val url: String? = null,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerializedName("isPrimary") val isPrimary: Boolean? = null,
    @SerializedName("displayOrder") val displayOrder: Int? = null
)

data class DiscoveryCardInterestDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("emoji") val emoji: String? = null
)

data class DiscoveryProfileCardDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("age") val age: Int? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("about") val about: String? = null,
    @SerializedName("isVerified") val isVerified: Boolean? = null,
    @SerializedName("isFaceVerified") val isFaceVerified: Boolean? = null,
    @SerializedName("isOnline") val isOnline: Boolean? = null,
    @SerializedName("distanceKm") val distanceKm: Double? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("lookingFor") val lookingFor: String? = null,
    @SerializedName("primaryPhotoUrl") val primaryPhotoUrl: String? = null,
    @SerializedName("profilePhotoUrl") val profilePhotoUrl: String? = null,
    @SerializedName("profilePhoto") val profilePhoto: String? = null,
    @SerializedName("photos") val photos: List<DiscoveryCardPhotoDto>? = null,
    @SerializedName("interests") val interests: List<DiscoveryCardInterestDto>? = null,
    @SerializedName("sharedInterests") val sharedInterests: List<DiscoveryCardInterestDto>? = null,
    @SerializedName("commonInterestsCount") val commonInterestsCount: Int? = null,
    @SerializedName("profileMatchPercentage") val profileMatchPercentage: Int? = null,
    @SerializedName("vibeScore") val vibeScore: Int? = null,
    @SerializedName("totalMoments") val totalMoments: Int? = null,
    @SerializedName("momentCount") val momentCount: Int? = null
) {
    fun toMatchProfile(): MatchProfile {
        val effectiveId = id ?: userId.orEmpty()
        val effectiveName = name ?: username.orEmpty()
        val photoPath = primaryPhotoUrl ?: profilePhotoUrl ?: profilePhoto ?: photos?.firstOrNull()?.url
        val resolvedPhoto = MediaUrlHelper.resolveMediaUrl(photoPath)

        val shared = sharedInterests.orEmpty()
        val sharedIds = shared.mapNotNull { it.id }.filter { it.isNotBlank() }.toSet()
        val sharedNames = shared.mapNotNull { it.name }.filter { it.isNotBlank() }.map { it.lowercase() }.toSet()

        val allInterests = interests.orEmpty()
        val interestItems = if (allInterests.isNotEmpty()) {
            allInterests.filter { !it.name.isNullOrBlank() }.map { item ->
                val label = item.name.orEmpty()
                val isShared = sharedIds.contains(item.id) || sharedNames.contains(label.lowercase())
                InterestItem(
                    emoji = item.emoji ?: getEmojiForInterestTag(label),
                    label = label,
                    isShared = isShared
                )
            }
        } else {
            shared.filter { !it.name.isNullOrBlank() }.map { item ->
                InterestItem(
                    emoji = item.emoji ?: getEmojiForInterestTag(item.name.orEmpty()),
                    label = item.name.orEmpty(),
                    isShared = true
                )
            }
        }

        val goalLabel = when (lookingFor?.lowercase()) {
            "long_term" -> "Long-term relationship"
            "casual" -> "Casual dating"
            "not_sure" -> "Not sure yet"
            else -> lookingFor?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: "Dating"
        }

        val locString = when {
            !location.isNullOrBlank() -> location
            !city.isNullOrBlank() && distanceKm != null -> "$city · ${distanceKm.toInt()} km away"
            !city.isNullOrBlank() -> city
            distanceKm != null -> "${distanceKm.toInt()} km away"
            else -> ""
        }

        return MatchProfile(
            id = effectiveId,
            name = effectiveName,
            age = age ?: 24,
            isVerified = isVerified == true || isFaceVerified == true,
            location = locString,
            momentsCount = totalMoments ?: 0,
            matchPercentage = profileMatchPercentage ?: vibeScore ?: 85,
            relationshipGoal = goalLabel,
            sharedInterestsCount = commonInterestsCount ?: sharedInterests?.size ?: 0,
            interests = interestItems,
            imageUrl = resolvedPhoto
        )
    }
}

// 3. Connection Request
data class ConnectionRequest(
    @SerializedName("targetUserId") val targetUserId: String,
    @SerializedName("reason") val reason: String? = null
)

data class ConnectionResponseData(
    @SerializedName("requestId") val requestId: String? = null,
    @SerializedName("targetUserId") val targetUserId: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("isMatched") val isMatched: Boolean? = null,
    @SerializedName("matchId") val matchId: String? = null,
    @SerializedName("reason") val reason: String? = null
)

// 4. User Limits & Quotas
data class UserLimitsResponseData(
    @SerializedName("isSubscribed") val isSubscribed: Boolean? = null,
    @SerializedName("likes") val likes: LimitDetailsDto? = null,
    @SerializedName("nopes") val nopes: NopesLimitDto? = null,
    @SerializedName("superLikes") val superLikes: SuperLikesLimitDto? = null,
    @SerializedName("tabs") val tabs: Map<String, Boolean>? = null,
    @SerializedName("chat") val chat: Map<String, Boolean>? = null
)

data class LimitDetailsDto(
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("used") val used: Int? = null,
    @SerializedName("remaining") val remaining: Int? = null,
    @SerializedName("resetsAt") val resetsAt: String? = null
)

data class NopesLimitDto(
    @SerializedName("isUnlimited") val isUnlimited: Boolean? = null
)

data class SuperLikesLimitDto(
    @SerializedName("hasFree") val hasFree: Boolean? = null,
    @SerializedName("freeRemaining") val freeRemaining: Int? = null,
    @SerializedName("costInCoins") val costInCoins: Int? = null,
    @SerializedName("userCoins") val userCoins: Int? = null
)

// 5. Wallet Balance
data class WalletBalanceData(
    @SerializedName("balance") val balance: Int = 0
)
