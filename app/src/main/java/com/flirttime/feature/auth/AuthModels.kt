package com.flirttime.feature.auth

import com.google.gson.annotations.SerializedName

// Phone & Email OTP Request / Response Models
data class OtpRequest(
    @SerializedName("countryCode") val countryCode: String,
    @SerializedName("phone") val phone: String
)

data class EmailOtpRequest(
    @SerializedName("email") val email: String
)

data class OtpResponseData(
    @SerializedName("verificationId") val verificationId: String,
    @SerializedName("expiresIn") val expiresIn: Int? = 300,
    @SerializedName("resendAfter") val resendAfter: Int? = 60
)

data class VerifyOtpRequest(
    @SerializedName("verificationId") val verificationId: String,
    @SerializedName("otp") val otp: String
)

data class ResendOtpRequest(
    @SerializedName("verificationId") val verificationId: String
)

data class VerifyOtpResponseData(
    @SerializedName("token") val token: String? = null,
    @SerializedName("accessToken") val accessToken: String? = null,
    @SerializedName("user") val user: AuthUserDto? = null,
    @SerializedName("isNewUser") val isNewUser: Boolean? = null
) {
    fun getEffectiveToken(): String? = token ?: accessToken
}

data class AuthUserDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("isPhoneVerified") val isPhoneVerified: Boolean? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("isActive") val isActive: Boolean? = null,
    @SerializedName("fullName") val fullName: String? = null,
    @SerializedName("firstName") val firstName: String? = null,
    @SerializedName("lastName") val lastName: String? = null,
    @SerializedName("nickName") val nickName: String? = null,
    @SerializedName("dateOfBirth") val dateOfBirth: String? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("about") val about: String? = null,
    @SerializedName("isProfileComplete") val isProfileComplete: Boolean? = null,
    @SerializedName("isFaceVerified") val isFaceVerified: Boolean? = null,
    @SerializedName("faceVerifiedAt") val faceVerifiedAt: String? = null,
    @SerializedName("profilePicture") val profilePicture: String? = null,
    @SerializedName("coverPicture") val coverPicture: String? = null,
    @SerializedName("coverPhoto") val coverPhoto: CoverPhotoDto? = null,
    @SerializedName("coverPhotoUrl") val coverPhotoUrl: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("coins") val coins: Int? = null,
    @SerializedName("isSubscribed") val isSubscribed: Boolean? = null,
    @SerializedName("subscriptionExpiresAt") val subscriptionExpiresAt: String? = null,
    @SerializedName("activeSubscriptionPlanId") val activeSubscriptionPlanId: String? = null,
    @SerializedName("notificationSettings") val notificationSettings: NotificationSettingsDto? = null,
    @SerializedName("countryCode") val countryCode: String? = null,
    @SerializedName("pageRedirect") val pageRedirect: String? = null
) {
    val effectiveCoverPhotoUrl: String?
        get() = coverPhotoUrl?.ifBlank { null }
            ?: coverPicture?.ifBlank { null }
            ?: coverPhoto?.url?.ifBlank { null }
            ?: coverPhoto?.thumbnailUrl?.ifBlank { null }
}

data class CoverPhotoDto(
    @SerializedName("url") val url: String? = null,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String? = null
)

data class UsernameAvailabilityResponseData(
    @SerializedName("available") val available: Boolean? = null,
    @SerializedName("isAvailable") val isAvailable: Boolean? = null,
    @SerializedName("taken") val taken: Boolean? = null
) {
    val isUsernameAvailable: Boolean
        get() = available ?: isAvailable ?: taken?.not() ?: true
}

data class NotificationSettingsDto(
    @SerializedName("push_notifications", alternate = ["push"]) val pushNotifications: Boolean? = true,
    @SerializedName("new_messages", alternate = ["new_message", "newMessage"]) val newMessages: Boolean? = true,
    @SerializedName("new_matches", alternate = ["newMatches"]) val newMatches: Boolean? = true,
    @SerializedName("like_comments", alternate = ["likes_comments_calls", "likesCommentsCalls"]) val likeComments: Boolean? = true,
    @SerializedName("profile_visits", alternate = ["profile_visit", "profileVisit"]) val profileVisits: Boolean? = true,
    @SerializedName("dating_tips", alternate = ["new_tips_available", "datingTips", "app_updates"]) val datingTips: Boolean? = false,
    @SerializedName("email") val email: Boolean? = null
) {
    val push: Boolean? get() = pushNotifications
    val newMessage: Boolean? get() = newMessages
    val appUpdates: Boolean? get() = datingTips
    val profileVisit: Boolean? get() = profileVisits
    val likesCommentsCalls: Boolean? get() = likeComments
}

data class AuthMeResponseData(
    @SerializedName("user") val user: AuthUserDto? = null
)

data class RegisterDeviceRequest(
    @SerializedName("deviceToken") val deviceToken: String,
    @SerializedName("platform") val platform: String = "android",
    @SerializedName("appVersion") val appVersion: String,
    @SerializedName("deviceModel") val deviceModel: String
)

data class UnregisterDeviceRequest(
    @SerializedName("deviceToken") val deviceToken: String
)

data class NotificationSettingsResponseData(
    @SerializedName("notificationSettings") val notificationSettings: NotificationSettingsDto? = null,
    @SerializedName("push_notifications", alternate = ["push"]) val pushNotifications: Boolean? = null,
    @SerializedName("new_messages", alternate = ["new_message"]) val newMessages: Boolean? = null,
    @SerializedName("new_matches") val newMatches: Boolean? = null,
    @SerializedName("like_comments", alternate = ["likes_comments_calls"]) val likeComments: Boolean? = null,
    @SerializedName("profile_visits", alternate = ["profile_visit"]) val profileVisits: Boolean? = null,
    @SerializedName("dating_tips", alternate = ["new_tips_available", "app_updates"]) val datingTips: Boolean? = null,
    @SerializedName("email") val email: Boolean? = null
) {
    fun toEffectiveSettings(): NotificationSettingsDto {
        return notificationSettings ?: NotificationSettingsDto(
            pushNotifications = pushNotifications,
            newMessages = newMessages,
            newMatches = newMatches,
            likeComments = likeComments,
            profileVisits = profileVisits,
            datingTips = datingTips,
            email = email
        )
    }
}

// Legacy / Support Models
data class CountryDataResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: List<CountryItem> = emptyList()
)

data class CountryItem(
    @SerializedName("country_code") val countryCode: String,
    @SerializedName("name") val name: String,
    @SerializedName("flags") val flags: Int? = null
)

data class LoginByPhoneRequest(
    @SerializedName("phone_code") val phoneCode: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("fcm_token") val fcmToken: String,
    @SerializedName("device") val device: String = "android",
    @SerializedName("firebase_token") val firebaseToken: String? = null
)

data class LoginByPhoneResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("data") val data: UserData? = null,
    @SerializedName("is_deleted") val isDeleted: Boolean? = false
)

data class UserData(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("phone_code") val phoneCode: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("page_redirect") val pageRedirect: String? = null
)
