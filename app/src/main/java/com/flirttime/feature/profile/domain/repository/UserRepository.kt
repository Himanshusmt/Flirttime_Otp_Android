package com.flirttime.feature.profile.domain.repository

import android.content.Context
import android.net.Uri
import com.flirttime.Resource
import com.flirttime.feature.auth.data.model.AuthUserDto
import com.flirttime.feature.profile.data.model.UpdateProfileRequest
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun updateProfile(request: UpdateProfileRequest): Flow<Resource<AuthUserDto>>
    fun uploadProfilePhoto(context: Context, imageUri: Uri): Flow<Resource<AuthUserDto>>
    fun uploadGesturePhoto(context: Context, imageUri: Uri): Flow<Resource<AuthUserDto>>
}
