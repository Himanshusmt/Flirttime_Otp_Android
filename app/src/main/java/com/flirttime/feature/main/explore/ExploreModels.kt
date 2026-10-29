package com.flirttime.feature.main.explore

import com.flirttime.feature.profile.UserPhotoDto
import com.google.gson.annotations.SerializedName

data class InteractionsResponseData(
    @SerializedName("type") val type: String? = null,
    @SerializedName("isLocked") val isLocked: Boolean? = null,
    @SerializedName("is_locked") val isLockedSnake: Boolean? = null,
    @SerializedName("totalCount") val totalCount: Int? = null,
    @SerializedName("total_count") val totalCountSnake: Int? = null,
    @SerializedName("items") val items: List<InteractionItemDto>? = null,
    @SerializedName("interactions") val interactions: List<InteractionItemDto>? = null,
    @SerializedName("likes") val likes: List<InteractionItemDto>? = null,
    @SerializedName("matches") val matches: List<InteractionItemDto>? = null,
    @SerializedName("compliments") val compliments: List<InteractionItemDto>? = null,
    @SerializedName("pagination") val pagination: InteractionPaginationDto? = null
) {
    val effectiveItems: List<InteractionItemDto>
        get() = items ?: interactions ?: likes ?: matches ?: compliments ?: emptyList()

    val effectiveIsLocked: Boolean
        get() = isLocked ?: isLockedSnake ?: false

    val effectiveTotalCount: Int
        get() = totalCount ?: totalCountSnake ?: effectiveItems.size
}

data class InteractionPaginationDto(
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("nextCursor") val nextCursor: String? = null,
    @SerializedName("next_cursor") val nextCursorSnake: String? = null,
    @SerializedName("cursor") val cursor: String? = null,
    @SerializedName("hasMore") val hasMore: Boolean? = null,
    @SerializedName("has_more") val hasMoreSnake: Boolean? = null
) {
    val effectiveCursor: String?
        get() = nextCursor ?: nextCursorSnake ?: cursor

    val effectiveHasMore: Boolean
        get() = hasMore ?: hasMoreSnake ?: (effectiveCursor != null)
}

data class InteractionCountsData(
    @SerializedName("likes") val likes: Int? = null,
    @SerializedName("you_liked") val youLikedSnake: Int? = null,
    @SerializedName("youLiked") val youLikedCamel: Int? = null,
    @SerializedName("matches") val matches: Int? = null,
    @SerializedName("compliments") val compliments: Int? = null,
    @SerializedName("total") val total: Int? = null
) {
    val effectiveLikes: Int get() = likes ?: 0
    val effectiveYouLiked: Int get() = youLikedSnake ?: youLikedCamel ?: 0
    val effectiveMatches: Int get() = matches ?: 0
    val effectiveCompliments: Int get() = compliments ?: 0
}

data class InteractionDetailDto(
    @SerializedName("action") val action: String? = null,
    @SerializedName("isMatched") val isMatched: Boolean? = null,
    @SerializedName("matched") val matched: Boolean? = null,
    @SerializedName("isRead") val isRead: Boolean? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("categoryLabel") val categoryLabel: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("momentPreview") val momentPreview: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null
)

/**
 * Universal person representation in interaction responses, capturing camelCase, snake_case,
 * and all known photo and metadata aliases used across backend iterations.
 */
data class ExplorePersonDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("user_id") val userIdSnake: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("fullName") val fullName: String? = null,
    @SerializedName("full_name") val fullNameSnake: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("display_name") val displayNameSnake: String? = null,
    @SerializedName("age") val age: Int? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("profilePhoto") val profilePhoto: String? = null,
    @SerializedName("profile_photo") val profilePhotoSnake: String? = null,
    @SerializedName("profilePhotoUrl") val profilePhotoUrl: String? = null,
    @SerializedName("profile_photo_url") val profilePhotoUrlSnake: String? = null,
    @SerializedName("profilePicture") val profilePicture: String? = null,
    @SerializedName("profile_picture") val profilePictureSnake: String? = null,
    @SerializedName("photo") val photo: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("avatar_url") val avatarUrlSnake: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("image_url") val imageUrlSnake: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("primaryPhotoUrl") val primaryPhotoUrl: String? = null,
    @SerializedName("primary_photo_url") val primaryPhotoUrlSnake: String? = null,
    @SerializedName("photos") val photos: List<UserPhotoDto>? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("location") val location: Any? = null,
    @SerializedName("locationLabel") val locationLabel: String? = null,
    @SerializedName("location_label") val locationLabelSnake: String? = null,
    @SerializedName("distanceKm") val distanceKm: Double? = null,
    @SerializedName("distance_km") val distanceKmSnake: Double? = null,
    @SerializedName("distanceText") val distanceText: String? = null,
    @SerializedName("distance_text") val distanceTextSnake: String? = null,
    @SerializedName("isVerified") val isVerified: Boolean? = null,
    @SerializedName("is_verified") val isVerifiedSnake: Boolean? = null,
    @SerializedName("isFaceVerified") val isFaceVerified: Boolean? = null,
    @SerializedName("verified") val verified: Boolean? = null,
    @SerializedName("isOnline") val isOnline: Boolean? = null,
    @SerializedName("is_online") val isOnlineSnake: Boolean? = null,
    @SerializedName("online") val online: Boolean? = null,
    @SerializedName("isBlurred") val isBlurred: Boolean? = null,
    @SerializedName("is_blurred") val isBlurredSnake: Boolean? = null
) {
    val effectiveId: String
        get() = id?.takeIf { it.isNotBlank() } ?: userId ?: userIdSnake.orEmpty()

    val effectiveName: String
        get() = username?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: fullName?.takeIf { it.isNotBlank() }
            ?: fullNameSnake?.takeIf { it.isNotBlank() }
            ?: displayName?.takeIf { it.isNotBlank() }
            ?: displayNameSnake.orEmpty()

    val effectivePhoto: String?
        get() = profilePhotoUrl?.takeIf { it.isNotBlank() }
            ?: profilePhotoUrlSnake?.takeIf { it.isNotBlank() }
            ?: profilePicture?.takeIf { it.isNotBlank() }
            ?: profilePictureSnake?.takeIf { it.isNotBlank() }
            ?: profilePhoto?.takeIf { it.isNotBlank() }
            ?: profilePhotoSnake?.takeIf { it.isNotBlank() }
            ?: photo?.takeIf { it.isNotBlank() }
            ?: avatar?.takeIf { it.isNotBlank() }
            ?: avatarUrl?.takeIf { it.isNotBlank() }
            ?: avatarUrlSnake?.takeIf { it.isNotBlank() }
            ?: imageUrl?.takeIf { it.isNotBlank() }
            ?: imageUrlSnake?.takeIf { it.isNotBlank() }
            ?: image?.takeIf { it.isNotBlank() }
            ?: primaryPhotoUrl?.takeIf { it.isNotBlank() }
            ?: primaryPhotoUrlSnake?.takeIf { it.isNotBlank() }
            ?: photos?.firstOrNull { it.isPrimary == true }?.url?.takeIf { it.isNotBlank() }
            ?: photos?.firstOrNull()?.url?.takeIf { it.isNotBlank() }
            ?: photos?.firstOrNull()?.thumbnailUrl?.takeIf { it.isNotBlank() }
            ?: photos?.firstOrNull()?.effectiveKey?.takeIf { it.isNotBlank() }

    val effectiveLocation: String
        get() {
            val locStr = when (location) {
                is String -> location
                is Map<*, *> -> (location["city"] ?: location["label"] ?: location["name"]) as? String
                else -> null
            }
            return locationLabel ?: locationLabelSnake ?: locStr ?: city ?: "Nearby"
        }

    val effectiveDistance: String?
        get() {
            val km = distanceKm ?: distanceKmSnake
            return distanceText ?: distanceTextSnake ?: km?.let { "${it.toInt()} km away" }
        }

    val effectiveVerified: Boolean
        get() = isVerified ?: isVerifiedSnake ?: isFaceVerified ?: verified ?: false

    val effectiveOnline: Boolean
        get() = isOnline ?: isOnlineSnake ?: online ?: false
}

data class InteractionItemDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("interactionId") val interactionId: String? = null,
    @SerializedName("interaction_id") val interactionIdSnake: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("user_id") val userIdSnake: String? = null,
    @SerializedName("user") val user: ExplorePersonDto? = null,
    @SerializedName("targetUser") val targetUser: ExplorePersonDto? = null,
    @SerializedName("target_user") val targetUserSnake: ExplorePersonDto? = null,
    @SerializedName("toUser") val toUser: ExplorePersonDto? = null,
    @SerializedName("to_user") val toUserSnake: ExplorePersonDto? = null,
    @SerializedName("fromUser") val fromUser: ExplorePersonDto? = null,
    @SerializedName("from_user") val fromUserSnake: ExplorePersonDto? = null,
    @SerializedName("sender") val sender: ExplorePersonDto? = null,
    @SerializedName("receiver") val receiver: ExplorePersonDto? = null,
    @SerializedName("otherUser") val otherUser: ExplorePersonDto? = null,
    @SerializedName("other_user") val otherUserSnake: ExplorePersonDto? = null,
    @SerializedName("counterpart") val counterpart: ExplorePersonDto? = null,
    @SerializedName("profile") val profile: ExplorePersonDto? = null,
    @SerializedName("interaction") val interaction: InteractionDetailDto? = null,
    // Flat root fields
    @SerializedName("name") val name: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("fullName") val fullName: String? = null,
    @SerializedName("full_name") val fullNameSnake: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("age") val age: Int? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("profilePhoto") val profilePhoto: String? = null,
    @SerializedName("profile_photo") val profilePhotoSnake: String? = null,
    @SerializedName("profilePhotoUrl") val profilePhotoUrl: String? = null,
    @SerializedName("profile_photo_url") val profilePhotoUrlSnake: String? = null,
    @SerializedName("profilePicture") val profilePicture: String? = null,
    @SerializedName("profile_picture") val profilePictureSnake: String? = null,
    @SerializedName("photo") val photo: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("primaryPhotoUrl") val primaryPhotoUrl: String? = null,
    @SerializedName("photos") val photos: List<UserPhotoDto>? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("locationLabel") val locationLabel: String? = null,
    @SerializedName("distanceKm") val distanceKm: Double? = null,
    @SerializedName("distance_km") val distanceKmSnake: Double? = null,
    @SerializedName("distanceText") val distanceText: String? = null,
    @SerializedName("distance_text") val distanceTextSnake: String? = null,
    @SerializedName("isVerified") val isVerified: Boolean? = null,
    @SerializedName("is_verified") val isVerifiedSnake: Boolean? = null,
    @SerializedName("isFaceVerified") val isFaceVerified: Boolean? = null,
    @SerializedName("verified") val verified: Boolean? = null,
    @SerializedName("isOnline") val isOnline: Boolean? = null,
    @SerializedName("is_online") val isOnlineSnake: Boolean? = null,
    @SerializedName("online") val online: Boolean? = null,
    @SerializedName("isMatched") val isMatched: Boolean? = null,
    @SerializedName("matched") val matched: Boolean? = null,
    @SerializedName("isSuperLike") val isSuperLike: Boolean? = null,
    @SerializedName("isBlurred") val isBlurred: Boolean? = null,
    @SerializedName("is_blurred") val isBlurredSnake: Boolean? = null,
    @SerializedName("isSubscribed") val isSubscribed: Boolean? = null,
    @SerializedName("compliment") val compliment: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("categoryLabel") val categoryLabel: String? = null,
    @SerializedName("isRead") val isRead: Boolean? = null,
    @SerializedName("createdAt") val createdAt: String? = null
) {
    fun getEffectivePerson(tab: ExploreTab): ExplorePersonDto? {
        return when (tab) {
            ExploreTab.YOU_LIKED -> targetUser ?: targetUserSnake ?: toUser ?: toUserSnake ?: counterpart ?: otherUser ?: otherUserSnake ?: receiver ?: profile ?: user ?: sender
            ExploreTab.LIKES -> fromUser ?: fromUserSnake ?: sender ?: user ?: profile ?: counterpart ?: otherUser ?: otherUserSnake ?: targetUser
            ExploreTab.MATCHES -> otherUser ?: otherUserSnake ?: counterpart ?: targetUser ?: toUser ?: user ?: profile
            ExploreTab.COMPLIMENTS -> fromUser ?: fromUserSnake ?: sender ?: user ?: profile ?: counterpart ?: otherUser ?: targetUser
        }
    }

    fun getEffectiveId(tab: ExploreTab): String {
        val person = getEffectivePerson(tab)
        return person?.effectiveId?.takeIf { it.isNotBlank() }
            ?: id?.takeIf { it.isNotBlank() }
            ?: interactionId
            ?: interactionIdSnake
            ?: userId
            ?: userIdSnake.orEmpty()
    }

    fun getEffectiveUsername(tab: ExploreTab): String {
        val person = getEffectivePerson(tab)
        return person?.effectiveName?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }
            ?: fullName?.takeIf { it.isNotBlank() }
            ?: fullNameSnake?.takeIf { it.isNotBlank() }
            ?: displayName?.takeIf { it.isNotBlank() }
            ?: name.orEmpty()
    }

    fun getEffectiveAge(tab: ExploreTab): Int? {
        val person = getEffectivePerson(tab)
        return person?.age ?: age
    }

    fun getEffectivePhoto(tab: ExploreTab): String? {
        val person = getEffectivePerson(tab)
        val pPhoto = person?.effectivePhoto
        if (!pPhoto.isNullOrBlank()) return pPhoto

        return profilePhotoUrl?.takeIf { it.isNotBlank() }
            ?: profilePhotoUrlSnake?.takeIf { it.isNotBlank() }
            ?: profilePicture?.takeIf { it.isNotBlank() }
            ?: profilePictureSnake?.takeIf { it.isNotBlank() }
            ?: profilePhoto?.takeIf { it.isNotBlank() }
            ?: profilePhotoSnake?.takeIf { it.isNotBlank() }
            ?: photo?.takeIf { it.isNotBlank() }
            ?: avatar?.takeIf { it.isNotBlank() }
            ?: avatarUrl?.takeIf { it.isNotBlank() }
            ?: imageUrl?.takeIf { it.isNotBlank() }
            ?: image?.takeIf { it.isNotBlank() }
            ?: primaryPhotoUrl?.takeIf { it.isNotBlank() }
            ?: photos?.firstOrNull { it.isPrimary == true }?.url?.takeIf { it.isNotBlank() }
            ?: photos?.firstOrNull()?.url?.takeIf { it.isNotBlank() }
            ?: photos?.firstOrNull()?.thumbnailUrl?.takeIf { it.isNotBlank() }
            ?: photos?.firstOrNull()?.effectiveKey?.takeIf { it.isNotBlank() }
    }

    fun getEffectiveLocation(tab: ExploreTab): String {
        val person = getEffectivePerson(tab)
        return person?.effectiveLocation ?: locationLabel ?: city ?: location ?: "Nearby"
    }

    fun getEffectiveDistance(tab: ExploreTab): String? {
        val person = getEffectivePerson(tab)
        val km = distanceKm ?: distanceKmSnake
        return person?.effectiveDistance ?: distanceText ?: distanceTextSnake ?: km?.let { "${it.toInt()} km away" }
    }
}

fun InteractionItemDto.toExploreProfileItem(tab: ExploreTab, isTabLocked: Boolean = false): ExploreProfileItem {
    val person = getEffectivePerson(tab)
    val cardIsBlurred = isBlurred ?: isBlurredSnake ?: person?.isBlurred ?: person?.isBlurredSnake ?: false
    val effectiveLocked = when (tab) {
        ExploreTab.LIKES -> cardIsBlurred || isTabLocked
        ExploreTab.YOU_LIKED, ExploreTab.MATCHES -> cardIsBlurred
        ExploreTab.COMPLIMENTS -> cardIsBlurred || isTabLocked
    }

    val effectiveSuperLike = when (tab) {
        ExploreTab.YOU_LIKED -> (isMatched ?: matched ?: interaction?.isMatched ?: interaction?.matched ?: false) ||
                (isSuperLike ?: (interaction?.action == "super_like"))
        else -> isSuperLike ?: (interaction?.action == "super_like")
    }

    val effectiveSubtitle = when (tab) {
        ExploreTab.COMPLIMENTS -> {
            val cat = category ?: categoryLabel ?: interaction?.category ?: interaction?.categoryLabel ?: compliment
            val msg = message ?: interaction?.message
            val formattedMsg = msg?.takeIf { it.isNotBlank() }?.let { "“$it”" }
            listOfNotNull(cat, formattedMsg).joinToString(" · ").ifBlank { getEffectiveLocation(tab) }
        }
        else -> getEffectiveLocation(tab)
    }

    val photo = getEffectivePhoto(tab)
    val effectiveId = getEffectiveId(tab)
    val effectiveName = getEffectiveUsername(tab)
    val effectiveAge = getEffectiveAge(tab)
    val isVerifiedUser = isVerified ?: isVerifiedSnake ?: isFaceVerified ?: verified ?: person?.effectiveVerified ?: false
    val isOnlineUser = isOnline ?: isOnlineSnake ?: online ?: person?.effectiveOnline ?: false

    return ExploreProfileItem(
        id = effectiveId,
        interactionId = interactionId ?: interactionIdSnake ?: id.orEmpty(),
        username = effectiveName,
        age = effectiveAge,
        hasRibbonBadge = isVerifiedUser,
        isVerified = isVerifiedUser,
        isOnline = isOnlineUser,
        isSuperLike = effectiveSuperLike,
        isMatched = isMatched ?: matched ?: interaction?.isMatched ?: interaction?.matched ?: false,
        distance = getEffectiveDistance(tab),
        location = getEffectiveLocation(tab),
        subtitle = effectiveSubtitle,
        imageUrl = photo,
        isLocked = effectiveLocked,
        isPlaceholder = photo.isNullOrBlank()
    )
}
