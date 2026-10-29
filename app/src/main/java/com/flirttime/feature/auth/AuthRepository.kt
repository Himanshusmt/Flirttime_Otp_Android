package com.flirttime.feature.auth

import com.flirttime.Resource
import com.flirttime.feature.auth.AuthUserDto
import com.flirttime.feature.auth.CountryItem
import com.flirttime.feature.auth.LoginByPhoneResponse
import com.flirttime.feature.auth.OtpResponseData
import com.flirttime.feature.auth.VerifyOtpResponseData
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    // Current Authenticated User & Device Token
    fun getCurrentUser(): Flow<Resource<AuthUserDto>>

    fun checkUsernameAvailability(username: String): Flow<Resource<Boolean>>

    fun registerDevice(
        deviceToken: String? = null,
        appVersion: String? = null,
        deviceModel: String? = null
    ): Flow<Resource<Unit>>

    // Phone & OTP Flow
    fun requestPhoneOtp(
        countryCode: String,
        phone: String
    ): Flow<Resource<OtpResponseData>>

    fun verifyPhoneOtp(
        verificationId: String,
        otp: String
    ): Flow<Resource<VerifyOtpResponseData>>

    fun resendPhoneOtp(
        verificationId: String
    ): Flow<Resource<OtpResponseData>>

    // Email & OTP Flow
    fun requestEmailOtp(
        email: String
    ): Flow<Resource<OtpResponseData>>

    fun verifyEmailOtp(
        verificationId: String,
        otp: String
    ): Flow<Resource<VerifyOtpResponseData>>

    fun resendEmailOtp(
        verificationId: String
    ): Flow<Resource<OtpResponseData>>

    // Logout
    fun logout(): Flow<Resource<Unit>>

    // Legacy & Social Auth
    fun getCountries(): Flow<Resource<List<CountryItem>>>
    fun loginByPhone(
        phoneCode: String,
        phone: String,
        fcmToken: String,
        firebaseToken: String?
    ): Flow<Resource<LoginByPhoneResponse>>
    fun loginByGoogle(
        idToken: String,
        fcmToken: String
    ): Flow<Resource<LoginByPhoneResponse>>
}
