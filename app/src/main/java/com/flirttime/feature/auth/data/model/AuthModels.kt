package com.flirttime.feature.auth.data.model

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
    @SerializedName("city") val city: String? = null,
    @SerializedName("coins") val coins: Int? = null,
    @SerializedName("isSubscribed") val isSubscribed: Boolean? = null,
    @SerializedName("countryCode") val countryCode: String? = null,
    @SerializedName("pageRedirect") val pageRedirect: String? = null
)

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
