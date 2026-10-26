package com.flirttime.feature.profile.data.repository

import android.content.Context
import android.net.Uri
import com.flirttime.Resource
import com.flirttime.core.common.MultipartHelper
import com.flirttime.core.database.SessionManager
import com.flirttime.core.network.safeApiCall
import com.flirttime.feature.auth.data.model.AuthUserDto
import com.flirttime.feature.profile.data.model.UpdateProfileRequest
import com.flirttime.feature.profile.data.network.UserApiService
import com.flirttime.feature.profile.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class UserRepositoryImpl(
    private val userApiService: UserApiService,
    private val sessionManager: SessionManager
) : UserRepository {

    override fun updateProfile(request: UpdateProfileRequest): Flow<Resource<AuthUserDto>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            userApiService.updateProfile(request)
        }
        if (result is Resource.Success) {
            val user = result.data
            sessionManager.saveUserProfile(
                firstName = user.firstName ?: request.firstName,
                lastName = user.lastName ?: request.lastName,
                nickName = user.nickName ?: request.nickName,
                dob = user.dateOfBirth ?: request.dateOfBirth,
                gender = user.gender ?: request.gender,
                bio = user.about ?: request.about
            )
            sessionManager.setProfileComplete(true)
            sessionManager.setPageRedirect("user_info")
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun uploadProfilePhoto(context: Context, imageUri: Uri): Flow<Resource<AuthUserDto>> = flow {
        emit(Resource.Loading)
        val part = MultipartHelper.createPartFromUri(context, imageUri, "profilePhoto")
        if (part == null) {
            emit(Resource.Error("Unable to process selected image."))
            return@flow
        }
        val result = safeApiCall {
            userApiService.uploadProfilePhoto(part)
        }
        if (result is Resource.Success) {
            val user = result.data
            val photoUrl = user.profilePicture ?: imageUri.toString()
            sessionManager.setProfileImageUri(photoUrl)
            sessionManager.setPageRedirect("avatar")
        }
        emit(result)
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
