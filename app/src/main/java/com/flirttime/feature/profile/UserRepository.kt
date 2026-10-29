package com.flirttime.feature.profile

import android.content.Context
import android.net.Uri
import com.flirttime.Resource
import com.flirttime.feature.auth.AuthUserDto
import com.flirttime.feature.auth.NotificationSettingsDto
import kotlinx.coroutines.flow.Flow

/**
 * UserRepository handles Account, Settings, Profile Change OTP, Moderation, and Verification
 * corresponding to `UserApiService`.
 *
 * Core screen feature data operations (Matches, Explore, Profile Details & Media, Other User Profile)
 * are handled by `MainRepository` and `MatchesRepository`.
 */
interface UserRepository {

    // ==========================================
    // 1. EMAIL CHANGE OTP
    // ==========================================
    fun requestProfileEmailOtp(email: String): Flow<Resource<ProfileOtpResponseData>>
    fun verifyProfileEmailOtp(verificationId: String, otp: String): Flow<Resource<VerifyProfileEmailResponseData>>
    fun resendProfileEmailOtp(verificationId: String): Flow<Resource<ProfileOtpResponseData>>

    // ==========================================
    // 2. PHONE CHANGE OTP
    // ==========================================
    fun requestProfilePhoneOtp(countryCode: String, phone: String): Flow<Resource<ProfileOtpResponseData>>
    fun verifyProfilePhoneOtp(verificationId: String, otp: String): Flow<Resource<VerifyProfilePhoneResponseData>>
    fun resendProfilePhoneOtp(verificationId: String): Flow<Resource<ProfileOtpResponseData>>

    // ==========================================
    // 3. NOTIFICATION SETTINGS
    // ==========================================
    fun getNotificationSettings(): Flow<Resource<NotificationSettingsDto>>
    fun updateNotificationSettings(settings: NotificationSettingsDto): Flow<Resource<NotificationSettingsDto>>

    // ==========================================
    // 4. BLOCKED & REPORTED USERS
    // ==========================================
    fun getBlockedUsers(page: Int = 1, limit: Int = 20): Flow<Resource<BlockedUsersResponseData>>
    fun unblockUser(userId: String): Flow<Resource<Unit>>
    fun getReports(page: Int = 1, limit: Int = 20, status: String? = null, targetType: String? = null): Flow<Resource<ReportsResponseData>>

    // ==========================================
    // 5. ACCOUNT MANAGEMENT & VERIFICATION
    // ==========================================
    fun deleteAccount(): Flow<Resource<Unit>>
    fun uploadGesturePhoto(context: Context, imageUri: Uri): Flow<Resource<AuthUserDto>>
}
