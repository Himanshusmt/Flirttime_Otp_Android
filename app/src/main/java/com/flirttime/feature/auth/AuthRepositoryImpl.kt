package com.flirttime.feature.auth

import com.flirttime.Resource
import com.flirttime.core.database.SessionManager
import com.flirttime.core.network.ApiService
import com.flirttime.core.network.safeApiCall
import com.flirttime.core.network.safeRawApiCall
import com.flirttime.feature.auth.CountryItem
import com.flirttime.feature.auth.EmailOtpRequest
import com.flirttime.feature.auth.LoginByPhoneResponse
import com.flirttime.feature.auth.OtpRequest
import com.flirttime.feature.auth.OtpResponseData
import com.flirttime.feature.auth.ResendOtpRequest
import com.flirttime.feature.auth.VerifyOtpRequest
import com.flirttime.feature.auth.VerifyOtpResponseData
import com.flirttime.feature.auth.AuthApiService
import com.flirttime.feature.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class AuthRepositoryImpl(
    private val apiService: ApiService,
    private val authApiService: AuthApiService,
    private val sessionManager: SessionManager
) : AuthRepository {

    override fun getCurrentUser(): Flow<Resource<com.flirttime.feature.auth.AuthUserDto>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            authApiService.getCurrentUser()
        }
        if (result is Resource.Success) {
            val user = result.data.user
            if (user != null) {
                user.id?.let { sessionManager.setUserId(it) }
                user.isProfileComplete?.let { sessionManager.setProfileComplete(it) }
                user.isFaceVerified?.let { sessionManager.setVerified(it) }
                user.profilePicture?.let { sessionManager.setProfileImageUri(it) }
                user.username?.let { if (it.isNotBlank()) sessionManager.setUsername(it) }
                user.fullName?.let { if (it.isNotBlank()) sessionManager.setFullName(it) }
                user.email?.let { if (it.isNotBlank()) sessionManager.setEmail(it) }
                user.phone?.let { if (it.isNotBlank()) sessionManager.setPhone(it) }
                val nameToSave = user.firstName ?: user.fullName ?: ""
                if (nameToSave.isNotBlank()) {
                    sessionManager.saveUserProfile(
                        firstName = nameToSave,
                        lastName = user.lastName.orEmpty(),
                        nickName = user.nickName.orEmpty(),
                        dob = user.dateOfBirth.orEmpty(),
                        gender = user.gender.orEmpty(),
                        bio = user.about.orEmpty()
                    )
                }
                user.pageRedirect?.let { redirect ->
                    sessionManager.setPageRedirect(redirect)
                }
                emit(Resource.Success(user))
            } else {
                emit(Resource.Error("User data is empty"))
            }
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun checkUsernameAvailability(username: String): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            authApiService.checkUsernameAvailability(username)
        }
        if (result is Resource.Success) {
            val available = result.data.isUsernameAvailable
            emit(Resource.Success(available))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun registerDevice(
        deviceToken: String?,
        appVersion: String?,
        deviceModel: String?
    ): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val token = deviceToken ?: sessionManager.getOrCreateDeviceToken()
        val version = appVersion ?: com.flirttime.BuildConfig.VERSION_NAME
        val model = deviceModel ?: "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

        val request = com.flirttime.feature.auth.RegisterDeviceRequest(
            deviceToken = token,
            platform = "android",
            appVersion = version,
            deviceModel = model
        )
        val result = safeApiCall {
            authApiService.registerDevice(request)
        }
        if (result is Resource.Success) {
            emit(Resource.Success(Unit))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun logout(): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        try {
            val deviceToken = sessionManager.getOrCreateDeviceToken()
            safeApiCall {
                authApiService.unregisterDevice(com.flirttime.feature.auth.UnregisterDeviceRequest(deviceToken))
            }
        } catch (_: Exception) {
        }
        safeApiCall {
            authApiService.logout()
        }
        sessionManager.clearSession()
        emit(Resource.Success(Unit))
    }.flowOn(Dispatchers.IO)

    override fun requestPhoneOtp(
        countryCode: String,
        phone: String
    ): Flow<Resource<OtpResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            authApiService.requestPhoneOtp(OtpRequest(countryCode = countryCode, phone = phone))
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun verifyPhoneOtp(
        verificationId: String,
        otp: String
    ): Flow<Resource<VerifyOtpResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            authApiService.verifyPhoneOtp(VerifyOtpRequest(verificationId = verificationId, otp = otp))
        }
        if (result is Resource.Success) {
            val authData = result.data
            authData.getEffectiveToken()?.let { token ->
                sessionManager.setToken(token)
            }
            authData.user?.let { user ->
                user.id?.let { sessionManager.setUserId(it) }
                user.isProfileComplete?.let { sessionManager.setProfileComplete(it) }
                user.isFaceVerified?.let { sessionManager.setVerified(it) }
                user.profilePicture?.let { sessionManager.setProfileImageUri(it) }
                val nameToSave = user.firstName ?: user.fullName ?: ""
                if (nameToSave.isNotBlank()) {
                    sessionManager.saveUserProfile(
                        firstName = nameToSave,
                        lastName = user.lastName.orEmpty(),
                        nickName = user.nickName.orEmpty(),
                        dob = user.dateOfBirth.orEmpty(),
                        gender = user.gender.orEmpty(),
                        bio = user.about.orEmpty()
                    )
                }
                user.pageRedirect?.let { redirect ->
                    sessionManager.setPageRedirect(redirect)
                }
            }
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun resendPhoneOtp(
        verificationId: String
    ): Flow<Resource<OtpResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            authApiService.resendPhoneOtp(ResendOtpRequest(verificationId = verificationId))
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun requestEmailOtp(
        email: String
    ): Flow<Resource<OtpResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            authApiService.requestEmailOtp(EmailOtpRequest(email = email))
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun verifyEmailOtp(
        verificationId: String,
        otp: String
    ): Flow<Resource<VerifyOtpResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            authApiService.verifyEmailOtp(VerifyOtpRequest(verificationId = verificationId, otp = otp))
        }
        if (result is Resource.Success) {
            val authData = result.data
            authData.getEffectiveToken()?.let { token ->
                sessionManager.setToken(token)
            }
            authData.user?.let { user ->
                user.id?.let { sessionManager.setUserId(it) }
                user.isProfileComplete?.let { sessionManager.setProfileComplete(it) }
                user.isFaceVerified?.let { sessionManager.setVerified(it) }
                user.profilePicture?.let { sessionManager.setProfileImageUri(it) }
                val nameToSave = user.firstName ?: user.fullName ?: ""
                if (nameToSave.isNotBlank()) {
                    sessionManager.saveUserProfile(
                        firstName = nameToSave,
                        lastName = user.lastName.orEmpty(),
                        nickName = user.nickName.orEmpty(),
                        dob = user.dateOfBirth.orEmpty(),
                        gender = user.gender.orEmpty(),
                        bio = user.about.orEmpty()
                    )
                }
                user.pageRedirect?.let { redirect ->
                    sessionManager.setPageRedirect(redirect)
                }
            }
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun resendEmailOtp(
        verificationId: String
    ): Flow<Resource<OtpResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            authApiService.resendEmailOtp(ResendOtpRequest(verificationId = verificationId))
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun getCountries(): Flow<Resource<List<CountryItem>>> = flow {
        emit(Resource.Loading)
        val result = safeRawApiCall {
            apiService.getCountries()
        }
        if (result is Resource.Success) {
            emit(Resource.Success(result.data.data))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun loginByPhone(
        phoneCode: String,
        phone: String,
        fcmToken: String,
        firebaseToken: String?
    ): Flow<Resource<LoginByPhoneResponse>> = flow {
        emit(Resource.Loading)
        val payload = mutableMapOf(
            "phone_code" to phoneCode,
            "phone" to phone,
            "fcm_token" to fcmToken,
            "device" to "android"
        )
        if (!firebaseToken.isNullOrBlank()) {
            payload["firebase_token"] = firebaseToken
        }
        val result = safeRawApiCall {
            apiService.loginByPhone(payload)
        }
        if (result is Resource.Success) {
            val response = result.data
            response.token?.let { token ->
                sessionManager.setToken(token)
            }
            response.data?.let { data ->
                data.id?.let { sessionManager.setUserId(it.toString()) }
                data.pageRedirect?.let { sessionManager.setPageRedirect(it) }
            }
            emit(Resource.Success(response))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun loginByGoogle(
        idToken: String,
        fcmToken: String
    ): Flow<Resource<LoginByPhoneResponse>> = flow {
        emit(Resource.Loading)
        val payload = mapOf(
            "token" to idToken,
            "fcm_token" to fcmToken,
            "device" to "android"
        )
        val result = safeRawApiCall {
            apiService.loginByGoogle(payload)
        }
        if (result is Resource.Success) {
            val response = result.data
            response.token?.let { token ->
                sessionManager.setToken(token)
            }
            response.data?.let { data ->
                data.id?.let { sessionManager.setUserId(it.toString()) }
                data.pageRedirect?.let { sessionManager.setPageRedirect(it) }
            }
            emit(Resource.Success(response))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)
}
