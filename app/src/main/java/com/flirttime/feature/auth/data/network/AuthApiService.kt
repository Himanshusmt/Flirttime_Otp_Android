package com.flirttime.feature.auth.data.network

import com.flirttime.core.network.model.ApiResponse
import com.flirttime.feature.auth.data.model.EmailOtpRequest
import com.flirttime.feature.auth.data.model.OtpRequest
import com.flirttime.feature.auth.data.model.OtpResponseData
import com.flirttime.feature.auth.data.model.ResendOtpRequest
import com.flirttime.feature.auth.data.model.VerifyOtpRequest
import com.flirttime.feature.auth.data.model.VerifyOtpResponseData
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {

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
