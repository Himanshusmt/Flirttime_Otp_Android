package com.flirttime.feature.profile

import android.content.Context
import android.net.Uri
import com.flirttime.Resource
import com.flirttime.core.common.MultipartHelper
import com.flirttime.core.database.SessionManager
import com.flirttime.core.network.safeApiCall
import com.flirttime.feature.auth.AuthUserDto
import com.flirttime.feature.auth.NotificationSettingsDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class UserRepositoryImpl(
    private val userApiService: UserApiService,
    private val sessionManager: SessionManager
) : UserRepository {

    // ==========================================
    // 1. EMAIL CHANGE OTP
    // ==========================================

    override fun requestProfileEmailOtp(email: String): Flow<Resource<ProfileOtpResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            userApiService.profileEmailRequestOtp(mapOf("email" to email))
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun verifyProfileEmailOtp(verificationId: String, otp: String): Flow<Resource<VerifyProfileEmailResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            userApiService.profileEmailVerifyOtp(
                mapOf(
                    "verificationId" to verificationId,
                    "otp" to otp
                )
            )
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun resendProfileEmailOtp(verificationId: String): Flow<Resource<ProfileOtpResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            userApiService.profileEmailResendOtp(mapOf("verificationId" to verificationId))
        }
        emit(result)
    }.flowOn(Dispatchers.IO)


    // ==========================================
    // 2. PHONE CHANGE OTP
    // ==========================================

    override fun requestProfilePhoneOtp(countryCode: String, phone: String): Flow<Resource<ProfileOtpResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            userApiService.profilePhoneRequestOtp(
                mapOf(
                    "countryCode" to countryCode,
                    "phone" to phone
                )
            )
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun verifyProfilePhoneOtp(verificationId: String, otp: String): Flow<Resource<VerifyProfilePhoneResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            userApiService.profilePhoneVerifyOtp(
                mapOf(
                    "verificationId" to verificationId,
                    "otp" to otp
                )
            )
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun resendProfilePhoneOtp(verificationId: String): Flow<Resource<ProfileOtpResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            userApiService.profilePhoneResendOtp(mapOf("verificationId" to verificationId))
        }
        emit(result)
    }.flowOn(Dispatchers.IO)


    // ==========================================
    // 3. NOTIFICATION SETTINGS
    // ==========================================

    override fun getNotificationSettings(): Flow<Resource<NotificationSettingsDto>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            userApiService.getNotificationSettings()
        }
        if (result is Resource.Success) {
            emit(Resource.Success(result.data.toEffectiveSettings()))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun updateNotificationSettings(
        settings: NotificationSettingsDto
    ): Flow<Resource<NotificationSettingsDto>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            userApiService.updateNotificationSettings(settings)
        }
        if (result is Resource.Success) {
            emit(Resource.Success(result.data.toEffectiveSettings()))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)


    // ==========================================
    // 4. BLOCKED & REPORTED USERS
    // ==========================================

    override fun getBlockedUsers(page: Int, limit: Int): Flow<Resource<BlockedUsersResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            userApiService.getBlockedUsers(page = page, limit = limit)
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun unblockUser(userId: String): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            userApiService.unblockUser(userId)
        }
        if (result is Resource.Success) {
            emit(Resource.Success(Unit))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun getReports(
        page: Int,
        limit: Int,
        status: String?,
        targetType: String?
    ): Flow<Resource<ReportsResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            userApiService.getReports(
                page = page,
                limit = limit,
                status = status,
                targetType = targetType
            )
        }
        emit(result)
    }.flowOn(Dispatchers.IO)


    // ==========================================
    // 5. ACCOUNT MANAGEMENT & VERIFICATION
    // ==========================================

    override fun deleteAccount(): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            userApiService.deleteAccount()
        }
        if (result is Resource.Success) {
            sessionManager.clearSession()
            emit(Resource.Success(Unit))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun uploadGesturePhoto(context: Context, imageUri: Uri): Flow<Resource<AuthUserDto>> = flow {
        emit(Resource.Loading)
        val part = MultipartHelper.createPartFromUri(context, imageUri, "selfie_image")
        if (part == null) {
            emit(Resource.Error("Unable to process gesture photo."))
            return@flow
        }
        val typePart = MultipartHelper.createTextPart("gesture")
        val result = safeApiCall {
            userApiService.uploadGesturePhoto(part, typePart)
        }
        if (result is Resource.Success) {
            sessionManager.setVerified(true)
            sessionManager.setPageRedirect("gesture")
        }
        emit(result)
    }.flowOn(Dispatchers.IO)
}
