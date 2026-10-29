package com.flirttime.feature.profile

import com.flirttime.feature.auth.CoverPhotoDto
import com.flirttime.feature.moments.MomentDto
import com.flirttime.feature.quickmoments.MyQuickMomentsResponseData
import com.google.gson.annotations.SerializedName

data class MyFullProfileData(
    val name: String,
    val fullName: String = "",
    val username: String = "",
    val dateOfBirth: String = "",
    val gender: String = "",
    val age: Int?,
    val location: String,
    val bio: String,
    val completionPercent: Int,
    val isVerified: Boolean,
    val profilePhotoUrl: String?,
    val coverPhotoUrl: String?,
    val voiceIntroUrl: String?,
    val voiceDuration: Int,
    val photos: List<UserPhotoDto> = emptyList(),
    val interests: List<UserInterestDto> = emptyList(),
    val moments: List<MomentDto> = emptyList(),
    val audioIntro: AudioIntroDto? = null,
    val quickMoments: MyQuickMomentsResponseData? = null,
    val moreAboutMe: MoreAboutMeResponseData? = null
)

data class UpdateProfileRequest(
    @SerializedName("fullName") val fullName: String,
    @SerializedName("username") val username: String,
    @SerializedName("dateOfBirth") val dateOfBirth: String,
    @SerializedName("gender") val gender: String,
    @SerializedName("about") val about: String,
    @SerializedName("location") val location: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("coverPicture") val coverPicture: String? = null
)

data class UserPhotosResponseData(
    @SerializedName("photos") val photos: List<UserPhotoDto>? = null
) {
    fun getEffectivePhotos(): List<UserPhotoDto> = photos ?: emptyList()
}

// Interests Models
data class UserInterestsResponseData(
    @SerializedName("interests") val interests: List<UserInterestDto>? = null,
    @SerializedName("items") val items: List<UserInterestDto>? = null,
    @SerializedName("data") val data: List<UserInterestDto>? = null
) {
    fun getEffectiveInterests(): List<UserInterestDto> = interests ?: items ?: data ?: emptyList()
}

data class UpdateInterestsRequest(
    @SerializedName("interestIds") val interestIds: List<String>
)

// More About Me Models
data class MoreAboutMeItemDto(
    @SerializedName("key") val key: String,
    @SerializedName("label") val label: String,
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("value") val value: String? = null
)

data class MoreAboutMeResponseData(
    @SerializedName("drinking") val drinking: String? = null,
    @SerializedName("education") val education: String? = null,
    @SerializedName("height") val height: String? = null,
    @SerializedName("religion") val religion: String? = null,
    @SerializedName("sexuality") val sexuality: String? = null,
    @SerializedName("smoking") val smoking: String? = null,
    @SerializedName("weight") val weight: String? = null,
    @SerializedName("eyeColor") val eyeColor: String? = null,
    @SerializedName("hairColor") val hairColor: String? = null,
    @SerializedName("living") val living: String? = null,
    @SerializedName("relationship") val relationship: String? = null,
    @SerializedName("children") val children: String? = null,
    @SerializedName("items") val items: List<MoreAboutMeItemDto>? = null
) {
    fun getEffectiveItems(): List<MoreAboutMeItemDto> {
        if (!items.isNullOrEmpty()) return items
        return listOf(
            MoreAboutMeItemDto("drinking", "Drinking", "🍷", drinking),
            MoreAboutMeItemDto("education", "Education", "📖", education),
            MoreAboutMeItemDto("eyeColor", "Eyecolor", "👁", eyeColor),
            MoreAboutMeItemDto("hairColor", "Haircolor", "💇", hairColor),
            MoreAboutMeItemDto("height", "Height", "📏", height),
            MoreAboutMeItemDto("living", "Living", "🏠", living),
            MoreAboutMeItemDto("relationship", "Relationship", "👩❤️👨", relationship),
            MoreAboutMeItemDto("religion", "Religion", "🛕", religion),
            MoreAboutMeItemDto("sexuality", "Sexuality", "⚧", sexuality),
            MoreAboutMeItemDto("smoking", "Smoking", "🚬", smoking),
            MoreAboutMeItemDto("weight", "Weight", "⚖️", weight),
            MoreAboutMeItemDto("children", "Children", "👶", children)
        )
    }
}

data class AudioIntroDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("audioUrl") val audioUrl: String? = null,
    @SerializedName("playbackUrl") val playbackUrl: String? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null
) {
    val effectiveUrl: String?
        get() = playbackUrl ?: audioUrl ?: url

    val isPlayable: Boolean
        get() = (status.isNullOrBlank() || status.equals("ready", ignoreCase = true)) && !effectiveUrl.isNullOrBlank()
}

data class AudioIntroResponseData(
    @SerializedName("audio") val audio: AudioIntroDto? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("audioUrl") val audioUrl: String? = null,
    @SerializedName("duration") val duration: Int? = null
) {
    fun toEffectiveAudio(): AudioIntroDto {
        return audio ?: AudioIntroDto(
            url = url ?: audioUrl,
            audioUrl = audioUrl ?: url,
            duration = duration
        )
    }
}

// Blocked & Reported Models
data class BlockedUserDto(
    @SerializedName("userId") val userId: String = "",
    @SerializedName("name") val name: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("profilePhoto") val profilePhoto: String? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("blockedAt") val blockedAt: String? = null
)

data class BlockedUsersResponseData(
    @SerializedName("blockedUsers") val blockedUsers: List<BlockedUserDto> = emptyList(),
    @SerializedName("pagination") val pagination: PaginationDto? = null
)

data class PaginationDto(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("page") val page: Int = 1,
    @SerializedName("limit") val limit: Int = 20,
    @SerializedName("totalPages") val totalPages: Int = 1
)

data class ReportUserDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("targetUserId") val targetUserId: String? = null,
    @SerializedName("targetType") val targetType: String? = null,
    @SerializedName("targetName") val targetName: String? = null,
    @SerializedName("targetPhoto") val targetPhoto: String? = null,
    @SerializedName("momentId") val momentId: String? = null,
    @SerializedName("momentThumbnail") val momentThumbnail: String? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null
)

data class ReportsResponseData(
    @SerializedName("reports") val reports: List<ReportUserDto> = emptyList(),
    @SerializedName("pagination") val pagination: PaginationDto? = null
)

// Profile Full Envelope Response (GET /api/v1/users/profile)
data class MyProfileEnvelopeData(
    @SerializedName("profile") val profile: MyProfileDetailsDto? = null,
    @SerializedName("interests") val interests: List<UserInterestDto>? = null,
    @SerializedName("voiceIntro") val voiceIntro: AudioIntroDto? = null,
    @SerializedName("moreAboutMe") val moreAboutMe: MoreAboutMeResponseData? = null,
    @SerializedName("isSubscribed") val isSubscribed: Boolean? = null
)

data class MyProfileDetailsDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("fullName") val fullName: String? = null,
    @SerializedName("age") val age: Int? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("about") val about: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("state") val state: String? = null,
    @SerializedName("profilePhoto") val profilePhoto: UserPhotoDto? = null,
    @SerializedName("coverPhoto") val coverPhoto: CoverPhotoDto? = null,
    @SerializedName("photos") val photos: List<UserPhotoDto>? = null,
    @SerializedName("isVerified") val isVerified: Boolean? = null,
    @SerializedName("completionPercentage") val completionPercentage: Int? = null,
    @SerializedName("isSubscribed") val isSubscribed: Boolean? = null
)

// Presigned Media URL (POST /api/v1/media/presigned-url)
data class PresignedUrlResponseData(
    @SerializedName("uploadUrl") val uploadUrl: String? = null,
    @SerializedName("publicUrl") val publicUrl: String? = null,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerializedName("imageKey") val imageKey: String? = null,
    @SerializedName("key") val key: String? = null,
    @SerializedName("expiresIn") val expiresIn: Int? = null
) {
    val effectiveKey: String
        get() = imageKey ?: key ?: ""
    val effectiveUrl: String
        get() = publicUrl ?: uploadUrl ?: ""
}

// Interests Catalog & Onboarding Interests
data class InterestsCatalogResponseData(
    @SerializedName("interests") val interests: List<UserInterestDto>? = null
)

data class OnboardingInterestsResponseData(
    @SerializedName("interests") val interests: List<UserInterestDto>? = null,
    @SerializedName("interestIds") val interestIds: List<String>? = null
)

// Notification Counts (GET /api/v1/notifications/counts)
data class NotificationCountsResponseData(
    @SerializedName("unreadCount") val unreadCount: Int? = null,
    @SerializedName("unread") val unread: Int? = null,
    @SerializedName("total") val total: Int? = null,
    @SerializedName("count") val count: Int? = null
) {
    val effectiveCount: Int
        get() = unreadCount ?: unread ?: total ?: count ?: 0
}

// Discovery Filter Options (GET /api/v1/discovery/filters/options)
data class DiscoveryFilterOptionsData(
    @SerializedName("genders") val genders: List<FilterOptionItem>? = null,
    @SerializedName("age") val age: AgeFilterBounds? = null,
    @SerializedName("distance") val distance: DistanceFilterBounds? = null,
    @SerializedName("lookingFor") val lookingFor: List<FilterOptionItem>? = null,
    @SerializedName("interests") val interests: List<UserInterestDto>? = null
)

data class FilterOptionItem(
    @SerializedName("value") val value: String? = null,
    @SerializedName("label") val label: String? = null
)

data class AgeFilterBounds(
    @SerializedName("minAllowed") val minAllowed: Int? = null,
    @SerializedName("maxAllowed") val maxAllowed: Int? = null,
    @SerializedName("defaultMin") val defaultMin: Int? = null,
    @SerializedName("defaultMax") val defaultMax: Int? = null
)

data class DistanceFilterBounds(
    @SerializedName("minKm") val minKm: Int? = null,
    @SerializedName("maxKm") val maxKm: Int? = null,
    @SerializedName("defaultKm") val defaultKm: Int? = null
)

// Profile OTP Models (Email / Phone)
data class ProfileOtpResponseData(
    @SerializedName("verificationId") val verificationId: String? = null,
    @SerializedName("expiresIn") val expiresIn: Int? = null,
    @SerializedName("resendAfter") val resendAfter: Int? = null
)

data class VerifyProfileEmailResponseData(
    @SerializedName("isEmailVerified") val isEmailVerified: Boolean? = null,
    @SerializedName("email") val email: String? = null
)

data class VerifyProfilePhoneResponseData(
    @SerializedName("isPhoneVerified") val isPhoneVerified: Boolean? = null,
    @SerializedName("phone") val phone: String? = null
)

