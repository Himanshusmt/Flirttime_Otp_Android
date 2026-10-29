package com.flirttime.feature.main

import android.content.Context
import android.net.Uri
import com.flirttime.Resource
import com.flirttime.feature.auth.AuthUserDto
import com.flirttime.feature.main.explore.InteractionsResponseData
import com.flirttime.feature.profile.AudioIntroDto
import com.flirttime.feature.profile.DiscoveryActionResponseData
import com.flirttime.feature.profile.DiscoveryPeopleResponseData
import com.flirttime.feature.profile.MoreAboutMeResponseData
import com.flirttime.feature.profile.MyFullProfileData
import com.flirttime.feature.profile.OtherUserProfileResponseData
import com.flirttime.feature.profile.UpdateProfileRequest
import com.flirttime.feature.profile.UserInterestDto
import com.flirttime.feature.profile.UserPhotoDto
import kotlinx.coroutines.flow.Flow

/**
 * MainRepository handles data operations for screens under `feature/main`:
 * Explore, Profile Details & Media, and Other User Profile.
 */
interface MainRepository {

    // ==========================================
    // 1. EXPLORE SCREEN
    // ==========================================
    fun getInteractions(
        type: String,
        limit: Int = 20,
        cursor: String? = null
    ): Flow<Resource<InteractionsResponseData>>


    // ==========================================
    // 2. PROFILE DETAILS & MEDIA (ME)
    // ==========================================
    fun getMyFullProfile(): Flow<Resource<MyFullProfileData>>

    fun updateProfile(request: UpdateProfileRequest): Flow<Resource<AuthUserDto>>

    fun uploadProfilePhoto(context: Context, imageUri: Uri): Flow<Resource<AuthUserDto>>

    fun uploadCoverPhoto(context: Context, imageUri: Uri): Flow<Resource<String>>

    fun getOnboardingPhotos(): Flow<Resource<List<UserPhotoDto>>>

    fun uploadOnboardingPhotos(context: Context, uris: List<Uri>): Flow<Resource<List<UserPhotoDto>>>

    fun addOrReplaceGalleryPhoto(
        context: Context,
        slotIndex: Int,
        imageUri: Uri,
        currentPhotos: List<UserPhotoDto>
    ): Flow<Resource<List<UserPhotoDto>>>

    fun deleteOnboardingPhoto(photoId: String): Flow<Resource<Unit>>

    fun setPrimaryPhoto(photoId: String): Flow<Resource<Unit>>

    fun uploadAudioIntro(context: Context, audioUri: Uri, durationSeconds: Int): Flow<Resource<AudioIntroDto>>

    fun getAudioIntro(): Flow<Resource<AudioIntroDto>>

    fun deleteAudioIntro(): Flow<Resource<Unit>>

    fun getSavedInterests(): Flow<Resource<List<UserInterestDto>>>

    fun updateInterests(interestIds: List<String>): Flow<Resource<Unit>>

    fun getInterestsCatalog(): Flow<Resource<List<UserInterestDto>>>

    fun getMoreAboutMe(): Flow<Resource<MoreAboutMeResponseData>>

    fun updateMoreAboutMe(key: String, value: String?): Flow<Resource<MoreAboutMeResponseData>>


    // ==========================================
    // 3. OTHER USER PROFILE & DISCOVERY CANDIDATES
    // ==========================================
    fun getOtherUserProfile(userId: String): Flow<Resource<OtherUserProfileResponseData>>

    fun submitDiscoveryAction(targetUserId: String, action: String): Flow<Resource<DiscoveryActionResponseData>>

    fun getDiscoveryPeople(page: Int = 1, limit: Int = 10, interestId: String? = null): Flow<Resource<DiscoveryPeopleResponseData>>
}
