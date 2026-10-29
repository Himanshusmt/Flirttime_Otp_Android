package com.flirttime.feature.profile

import com.flirttime.core.network.ApiResponse
import com.flirttime.feature.auth.AuthUserDto
import com.flirttime.feature.auth.NotificationSettingsDto
import com.flirttime.feature.auth.NotificationSettingsResponseData
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * UserApiService handles Account, Settings, Profile Change OTP, Moderation, and Verification.
 * Core screen feature endpoints (Matches, Explore, Moments, Quick Moments, Profile details)
 * are handled by `MainApiService`.
 */
interface UserApiService {

    // ==========================================
    // 1. EMAIL CHANGE OTP
    // ==========================================

    @POST("api/v1/users/profile/email/request-otp")
    suspend fun profileEmailRequestOtp(
        @Body body: Map<String, String>
    ): ApiResponse<ProfileOtpResponseData>

    @POST("api/v1/users/profile/email/verify-otp")
    suspend fun profileEmailVerifyOtp(
        @Body body: Map<String, String>
    ): ApiResponse<VerifyProfileEmailResponseData>

    @POST("api/v1/users/profile/email/resend-otp")
    suspend fun profileEmailResendOtp(
        @Body body: Map<String, String>
    ): ApiResponse<ProfileOtpResponseData>


    // ==========================================
    // 2. PHONE CHANGE OTP
    // ==========================================

    @POST("api/v1/users/profile/phone/request-otp")
    suspend fun profilePhoneRequestOtp(
        @Body body: Map<String, String>
    ): ApiResponse<ProfileOtpResponseData>

    @POST("api/v1/users/profile/phone/verify-otp")
    suspend fun profilePhoneVerifyOtp(
        @Body body: Map<String, String>
    ): ApiResponse<VerifyProfilePhoneResponseData>

    @POST("api/v1/users/profile/phone/resend-otp")
    suspend fun profilePhoneResendOtp(
        @Body body: Map<String, String>
    ): ApiResponse<ProfileOtpResponseData>


    // ==========================================
    // 3. NOTIFICATION SETTINGS
    // ==========================================

    @GET("api/v1/users/notification-settings")
    suspend fun getNotificationSettings(): ApiResponse<NotificationSettingsResponseData>

    @PATCH("api/v1/users/notification-settings")
    suspend fun updateNotificationSettings(
        @Body body: NotificationSettingsDto
    ): ApiResponse<NotificationSettingsResponseData>


    // ==========================================
    // 4. BLOCKED & REPORTED USERS
    // ==========================================

    @GET("api/v1/users/blocked")
    suspend fun getBlockedUsers(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<BlockedUsersResponseData>

    @DELETE("api/v1/users/{userId}/block")
    suspend fun unblockUser(
        @Path("userId") userId: String
    ): ApiResponse<Any>

    @GET("api/v1/users/reports")
    suspend fun getReports(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("status") status: String? = null,
        @Query("targetType") targetType: String? = null
    ): ApiResponse<ReportsResponseData>


    // ==========================================
    // 5. ACCOUNT MANAGEMENT & VERIFICATION
    // ==========================================

    @DELETE("api/v1/users/account")
    suspend fun deleteAccount(): ApiResponse<Any>

    @Multipart
    @POST("api/v1/gesture-upload")
    suspend fun uploadGesturePhoto(
        @Part selfie_image: MultipartBody.Part,
        @Part("verification_type") verificationType: RequestBody
    ): ApiResponse<AuthUserDto>
}
