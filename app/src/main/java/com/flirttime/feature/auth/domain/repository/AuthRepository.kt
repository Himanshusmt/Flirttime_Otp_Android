package com.flirttime.feature.auth.domain.repository

import com.flirttime.Resource
import com.flirttime.feature.auth.data.model.CountryItem
import com.flirttime.feature.auth.data.model.LoginByPhoneResponse
import com.flirttime.feature.auth.data.model.OtpResponseData
import com.flirttime.feature.auth.data.model.VerifyOtpResponseData
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
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
