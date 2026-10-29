package com.flirttime.feature.auth

import com.flirttime.core.network.ApiResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApiService {

    // Current Authenticated User
    @GET("api/v1/auth/me")
    suspend fun getCurrentUser(): ApiResponse<AuthMeResponseData>

    // Check Username Availability
    @GET("api/v1/auth/username/availability")
    suspend fun checkUsernameAvailability(
        @retrofit2.http.Query("username") username: String
    ): ApiResponse<UsernameAvailabilityResponseData>

    // Logout
    @POST("api/v1/auth/logout")
    suspend fun logout(): ApiResponse<Any>

    // Device Registration (FCM / Push Notifications)
    @POST("api/v1/devices/register")
    suspend fun registerDevice(
        @Body body: RegisterDeviceRequest
    ): ApiResponse<Any>

    // Device Unregistration (Logout)
    @retrofit2.http.HTTP(method = "DELETE", path = "api/v1/devices/unregister", hasBody = true)
    suspend fun unregisterDevice(
        @Body body: UnregisterDeviceRequest
    ): ApiResponse<Any>

    // Phone OTP Endpoints
    @POST("api/v1/auth/phone/request-otp")
    suspend fun requestPhoneOtp(
        @Body body: OtpRequest
    ): ApiResponse<OtpResponseData>

    @POST("api/v1/auth/phone/verify-otp")
    suspend fun verifyPhoneOtp(
        @Body body: VerifyOtpRequest
    ): ApiResponse<VerifyOtpResponseData>

    @POST("api/v1/auth/phone/resend-otp")
    suspend fun resendPhoneOtp(
        @Body body: ResendOtpRequest
    ): ApiResponse<OtpResponseData>

    // Email OTP Endpoints
    @POST("api/v1/auth/email/request-otp")
    suspend fun requestEmailOtp(
        @Body body: EmailOtpRequest
    ): ApiResponse<OtpResponseData>

    @POST("api/v1/auth/email/verify-otp")
    suspend fun verifyEmailOtp(
        @Body body: VerifyOtpRequest
    ): ApiResponse<VerifyOtpResponseData>

    @POST("api/v1/auth/email/resend-otp")
    suspend fun resendEmailOtp(
        @Body body: ResendOtpRequest
    ): ApiResponse<OtpResponseData>
}
