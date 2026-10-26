package com.flirttime.feature.profile.data.network

import com.flirttime.core.network.model.ApiResponse
import com.flirttime.feature.auth.data.model.AuthUserDto
import com.flirttime.feature.profile.data.model.UpdateProfileRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part

interface UserApiService {

    @PATCH("api/v1/users/profile")
    suspend fun updateProfile(
        @Body body: UpdateProfileRequest
    ): ApiResponse<AuthUserDto>

    @Multipart
    @POST("api/v1/onboarding/profile-photo")
    suspend fun uploadProfilePhoto(
        @Part profilePhoto: MultipartBody.Part
    ): ApiResponse<AuthUserDto>

    @Multipart
    @POST("api/v1/gesture-upload")
    suspend fun uploadGesturePhoto(
        @Part selfie_image: MultipartBody.Part,
        @Part("verification_type") verificationType: RequestBody
    ): ApiResponse<AuthUserDto>
}
