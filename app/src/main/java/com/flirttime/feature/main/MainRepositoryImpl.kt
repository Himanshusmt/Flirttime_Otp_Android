package com.flirttime.feature.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.flirttime.Resource
import com.flirttime.core.common.MultipartHelper
import com.flirttime.core.database.SessionManager
import com.flirttime.core.network.safeApiCall
import com.flirttime.feature.auth.AuthUserDto
import com.flirttime.feature.main.explore.InteractionsResponseData
import com.flirttime.feature.profile.AudioIntroDto
import com.flirttime.feature.profile.DiscoveryActionRequest
import com.flirttime.feature.profile.DiscoveryActionResponseData
import com.flirttime.feature.profile.DiscoveryPeopleResponseData
import com.flirttime.feature.profile.MoreAboutMeResponseData
import com.flirttime.feature.profile.MyFullProfileData
import com.flirttime.feature.profile.OtherUserProfileResponseData
import com.flirttime.feature.profile.UpdateInterestsRequest
import com.flirttime.feature.profile.UpdateProfileRequest
import com.flirttime.feature.profile.UserInterestDto
import com.flirttime.feature.profile.UserPhotoDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class MainRepositoryImpl(
    private val mainApiService: MainApiService,
    private val sessionManager: SessionManager,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) : MainRepository {

    // ==========================================
    // 1. EXPLORE SCREEN
    // ==========================================

    override fun getInteractions(
        type: String,
        limit: Int,
        cursor: String?
    ): Flow<Resource<InteractionsResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getInteractions(
                type = type,
                limit = limit,
                cursor = cursor
            )
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun getInteractionCounts(): Flow<Resource<com.flirttime.feature.main.explore.InteractionCountsData>> = flow {
        val result = safeApiCall {
            mainApiService.getInteractionCounts()
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun markInteractionRead(id: String): Flow<Resource<Unit>> = flow {
        val result = safeApiCall {
            mainApiService.markInteractionRead(id)
        }
        when (result) {
            is Resource.Success -> emit(Resource.Success(Unit))
            is Resource.Error -> emit(Resource.Error(result.message, result.code))
            is Resource.Loading -> emit(Resource.Loading)
        }
    }.flowOn(Dispatchers.IO)


    // ==========================================
    // 2. PROFILE DETAILS & MEDIA (ME)
    // ==========================================

    override fun updateProfile(request: UpdateProfileRequest): Flow<Resource<AuthUserDto>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.updateProfile(request)
        }
        if (result is Resource.Success) {
            val user = result.data
            val resolvedFirstName = user.firstName ?: request.fullName.substringBefore(" ").trim()
            val resolvedLastName = user.lastName ?: if (request.fullName.contains(" ")) request.fullName.substringAfter(" ").trim() else ""
            val resolvedNickName = user.nickName ?: request.username
            sessionManager.saveUserProfile(
                firstName = resolvedFirstName,
                lastName = resolvedLastName,
                nickName = resolvedNickName,
                dob = user.dateOfBirth ?: request.dateOfBirth,
                gender = user.gender ?: request.gender,
                bio = user.about ?: request.about
            )
            sessionManager.setProfileComplete(true)
            sessionManager.setPageRedirect("user_info")
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    private data class S3PresignedUploadResult(
        val uploadUrl: String,
        val publicUrl: String,
        val imageKey: String
    )

    private suspend fun uploadToS3Presigned(
        context: Context,
        imageUri: Uri,
        folder: String
    ): Resource<S3PresignedUploadResult> {
        val compressedBytes = try {
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return Resource.Error("Unable to decode selected image.")
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val bytes = baos.toByteArray()
            bitmap.recycle()
            bytes
        } catch (e: Exception) {
            return Resource.Error("Failed to process image: ${e.localizedMessage}")
        }

        if (compressedBytes.isEmpty()) {
            return Resource.Error("Selected image data is empty.")
        }

        val presignedResult = safeApiCall {
            mainApiService.getPresignedUrl(
                mapOf(
                    "folder" to folder,
                    "mimeType" to "image/jpeg",
                    "expiresInSeconds" to 300
                )
            )
        }
        if (presignedResult !is Resource.Success) {
            val err = (presignedResult as? Resource.Error)?.message ?: "Failed to get presigned upload URL"
            return Resource.Error(err)
        }

        val presignedData = presignedResult.data
        val uploadUrl = presignedData.uploadUrl
        val publicUrl = presignedData.publicUrl ?: presignedData.uploadUrl.orEmpty()
        val imageKey = presignedData.effectiveKey

        if (uploadUrl.isNullOrBlank()) {
            return Resource.Error("Storage service did not return an upload URL")
        }

        val putResponse = try {
            val putBody = compressedBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val putRequest = Request.Builder()
                .url(uploadUrl)
                .put(putBody)
                .header("Content-Type", "image/jpeg")
                .build()
            okHttpClient.newCall(putRequest).execute()
        } catch (e: Exception) {
            return Resource.Error("Network error uploading image to storage: ${e.localizedMessage}")
        }

        if (!putResponse.isSuccessful) {
            val putErr = putResponse.body?.string().orEmpty()
            return Resource.Error("Failed to upload image to cloud storage (HTTP ${putResponse.code}): $putErr")
        }

        return Resource.Success(
            S3PresignedUploadResult(
                uploadUrl = uploadUrl,
                publicUrl = publicUrl.ifBlank { imageKey },
                imageKey = imageKey.ifBlank { publicUrl }
            )
        )
    }

    override fun uploadProfilePhoto(context: Context, imageUri: Uri): Flow<Resource<AuthUserDto>> = flow {
        emit(Resource.Loading)
        // 1. Try S3 presigned upload with folder "photos"
        val s3Result = uploadToS3Presigned(context, imageUri, folder = "photos")
        if (s3Result is Resource.Success) {
            val key = s3Result.data.imageKey
            val keyResult = safeApiCall {
                mainApiService.setProfilePhotoKey(mapOf("imageKey" to key))
            }
            if (keyResult is Resource.Success) {
                val photoUrl = s3Result.data.publicUrl
                sessionManager.setProfileImageUri(photoUrl)
                sessionManager.setPageRedirect("avatar")
                emit(Resource.Success(AuthUserDto(profilePicture = photoUrl)))
                return@flow
            }
        }

        // 2. Fallback to direct multipart
        val part = MultipartHelper.createPartFromUri(context, imageUri, "profilePhoto")
        if (part == null) {
            emit(Resource.Error("Unable to process selected image."))
            return@flow
        }
        val result = safeApiCall {
            mainApiService.uploadProfilePhoto(part)
        }
        if (result is Resource.Success) {
            val user = result.data
            val photoUrl = user.profilePicture ?: imageUri.toString()
            sessionManager.setProfileImageUri(photoUrl)
            sessionManager.setPageRedirect("avatar")
        } else if (result is Resource.Error) {
            Log.e("MainRepository", "uploadProfilePhoto failed: ${result.message} | code: ${result.code}")
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun uploadCoverPhoto(context: Context, imageUri: Uri): Flow<Resource<String>> = flow {
        emit(Resource.Loading)

        // 1. S3 Presigned upload with folder "uploads" (as specified by server)
        val s3Result = uploadToS3Presigned(context, imageUri, folder = "uploads")
        if (s3Result !is Resource.Success) {
            val err = (s3Result as? Resource.Error)?.message ?: "Failed to upload cover photo to storage"
            Log.e("MainRepository", "uploadCoverPhoto S3 upload failed: $err")
            emit(Resource.Error(err))
            return@flow
        }

        val publicUrl = s3Result.data.publicUrl
        val imageKey = s3Result.data.imageKey
        val coverField = if (imageKey.isNotBlank()) imageKey else publicUrl

        // 2. Update whole profile on backend via PATCH /api/v1/users/profile
        val storedFullName = sessionManager.getFullName()
        val storedUsername = sessionManager.getUsername()
        val firstName = sessionManager.getFirstName()
        val lastName = sessionManager.getLastName()
        val nickName = sessionManager.getNickName()
        val fullName = when {
            storedFullName.isNotBlank() -> storedFullName
            firstName.isNotBlank() && lastName.isNotBlank() -> "$firstName $lastName"
            firstName.isNotBlank() -> firstName
            nickName.isNotBlank() -> nickName
            else -> "User"
        }
        val username = when {
            storedUsername.isNotBlank() -> storedUsername
            nickName.isNotBlank() -> nickName
            firstName.isNotBlank() -> firstName.lowercase().replace(" ", "")
            else -> "user_${System.currentTimeMillis() % 10000}"
        }
        val dob = sessionManager.getDob().ifBlank { "2000-01-01" }
        val gender = sessionManager.getGender().ifBlank { "male" }
        val about = sessionManager.getBio().ifBlank { "Hey there! I am using FlirtTime." }
        val location = sessionManager.getLocation().ifBlank { null }

        val updateProfileResult = safeApiCall {
            mainApiService.updateProfile(
                UpdateProfileRequest(
                    fullName = fullName,
                    username = username,
                    dateOfBirth = dob,
                    gender = gender,
                    about = about,
                    location = location,
                    coverPicture = coverField
                )
            )
        }
        when (updateProfileResult) {
            is Resource.Success -> {
                sessionManager.setCoverPhotoUrl(publicUrl)
                emit(Resource.Success(publicUrl))
            }
            is Resource.Error -> {
                val errMsg = updateProfileResult.message ?: "Failed to update profile cover photo on server"
                emit(Resource.Error(errMsg, updateProfileResult.code, updateProfileResult.cause, updateProfileResult.errorBody))
            }
            is Resource.Loading -> Unit
        }
    }.flowOn(Dispatchers.IO)

    override fun getMyFullProfile(): Flow<Resource<MyFullProfileData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getMyProfile()
        }

        if (result is Resource.Success) {
            val envelope = result.data
            val profile = envelope.profile
            val resolvedName = profile?.displayName?.takeIf { it.isNotBlank() }
                ?: profile?.fullName?.takeIf { it.isNotBlank() }
                ?: profile?.username?.takeIf { it.isNotBlank() }
                ?: sessionManager.getFullName().ifBlank { sessionManager.getFirstName() }.ifBlank { "User" }

            val parsed = MyFullProfileData(
                name = resolvedName,
                fullName = profile?.fullName ?: resolvedName,
                username = profile?.username ?: sessionManager.getUsername(),
                dateOfBirth = sessionManager.getDob(),
                gender = sessionManager.getGender(),
                age = profile?.age,
                location = profile?.city ?: profile?.state ?: sessionManager.getLocation(),
                bio = profile?.bio ?: profile?.about ?: sessionManager.getBio(),
                completionPercent = profile?.completionPercentage ?: 90,
                isVerified = profile?.isVerified ?: sessionManager.isVerified(),
                profilePhotoUrl = profile?.profilePhoto?.url ?: sessionManager.getProfileImageUri().ifBlank { null },
                coverPhotoUrl = profile?.coverPhoto?.url ?: sessionManager.getCoverPhotoUrl().ifBlank { null },
                voiceIntroUrl = envelope.voiceIntro?.effectiveUrl ?: sessionManager.getAudioIntroUrl().ifBlank { null },
                voiceDuration = envelope.voiceIntro?.duration ?: sessionManager.getAudioIntroDuration(),
                photos = profile?.photos ?: emptyList(),
                interests = resolveInterestsWithDefaultEmojis(envelope.interests ?: emptyList()),
                moreAboutMe = envelope.moreAboutMe
            )

            if (parsed.fullName.isNotBlank()) sessionManager.setFullName(parsed.fullName)
            if (parsed.username.isNotBlank()) sessionManager.setUsername(parsed.username)
            if (parsed.dateOfBirth.isNotBlank()) sessionManager.setDob(parsed.dateOfBirth)
            if (parsed.gender.isNotBlank()) sessionManager.setGender(parsed.gender)
            if (parsed.bio.isNotBlank()) sessionManager.setBio(parsed.bio)
            if (parsed.name.isNotBlank()) {
                val fName = parsed.name.substringBefore(" ").trim()
                val lName = if (parsed.name.contains(" ")) parsed.name.substringAfter(" ").trim() else ""
                if (fName.isNotBlank()) sessionManager.setFirstName(fName)
                if (lName.isNotBlank()) sessionManager.setLastName(lName)
            }
            if (parsed.location.isNotBlank()) sessionManager.setLocation(parsed.location)
            if (!parsed.coverPhotoUrl.isNullOrBlank()) sessionManager.setCoverPhotoUrl(parsed.coverPhotoUrl)
            if (!parsed.profilePhotoUrl.isNullOrBlank()) sessionManager.setProfileImageUri(parsed.profilePhotoUrl)
            if (!parsed.voiceIntroUrl.isNullOrBlank()) {
                sessionManager.setAudioIntroUrl(parsed.voiceIntroUrl)
                sessionManager.setAudioIntroDuration(parsed.voiceDuration)
            }
            sessionManager.setVerified(parsed.isVerified)
            sessionManager.setProfileComplete(parsed.completionPercent >= 100)

            emit(Resource.Success(parsed))
            return@flow
        }

        // Fallback to local session data if network fails
        val firstName = sessionManager.getFirstName()
        val lastName = sessionManager.getLastName()
        val nickName = sessionManager.getNickName()
        val name = when {
            firstName.isNotBlank() && lastName.isNotBlank() -> "$firstName $lastName"
            firstName.isNotBlank() -> firstName
            nickName.isNotBlank() -> nickName
            else -> "User"
        }
        val fallback = MyFullProfileData(
            name = name,
            age = null,
            location = sessionManager.getLocation(),
            bio = sessionManager.getBio(),
            completionPercent = if (sessionManager.isProfileComplete()) 100 else 90,
            isVerified = sessionManager.isVerified(),
            profilePhotoUrl = sessionManager.getProfileImageUri().ifBlank { null },
            coverPhotoUrl = sessionManager.getCoverPhotoUrl().ifBlank { null },
            voiceIntroUrl = sessionManager.getAudioIntroUrl().ifBlank { null },
            voiceDuration = sessionManager.getAudioIntroDuration()
        )
        emit(Resource.Success(fallback))
    }.flowOn(Dispatchers.IO)

    override fun getOnboardingPhotos(): Flow<Resource<List<UserPhotoDto>>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getOnboardingPhotos()
        }
        var photos = if (result is Resource.Success) {
            result.data.getEffectivePhotos()
        } else {
            emptyList()
        }

        if (photos.isEmpty()) {
            val myProfileResult = safeApiCall {
                mainApiService.getMyProfile()
            }
            if (myProfileResult is Resource.Success) {
                val profilePhotos = myProfileResult.data.profile?.photos.orEmpty()
                if (profilePhotos.isNotEmpty()) {
                    photos = profilePhotos
                }
            }
        }

        if (photos.isNotEmpty()) {
            emit(Resource.Success(photos))
        } else if (result is Resource.Success) {
            emit(Resource.Success(emptyList()))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun uploadOnboardingPhotos(
        context: Context,
        uris: List<Uri>
    ): Flow<Resource<List<UserPhotoDto>>> = flow {
        emit(Resource.Loading)

        // 1. Upload each image to S3 presigned URL with folder "photos"
        val uploadedKeys = mutableListOf<String>()
        for (uri in uris) {
            val s3Res = uploadToS3Presigned(context, uri, folder = "photos")
            if (s3Res is Resource.Success) {
                uploadedKeys.add(s3Res.data.imageKey)
            }
        }

        // 2. If at least 3 keys uploaded, replace gallery using PUT /api/v1/users/photos with { "imageKeys": [...] }
        if (uploadedKeys.size >= 3) {
            val replaceResult = safeApiCall {
                mainApiService.updateGalleryPhotoKeys(mapOf("imageKeys" to uploadedKeys))
            }
            if (replaceResult is Resource.Success) {
                safeApiCall {
                    mainApiService.setProfilePhotoKey(mapOf("imageKey" to uploadedKeys.first()))
                }
                val profileRes = safeApiCall { mainApiService.getMyProfile() }
                if (profileRes is Resource.Success) {
                    val photos = profileRes.data.profile?.photos.orEmpty()
                    if (photos.isNotEmpty()) {
                        emit(Resource.Success(photos))
                        return@flow
                    }
                }
            }
        }

        // 3. Fallback to direct multipart
        val parts = mutableListOf<okhttp3.MultipartBody.Part>()
        for (uri in uris) {
            val part = MultipartHelper.createPartFromUri(context, uri, "photos")
            if (part != null) {
                parts.add(part)
            }
        }
        if (parts.isEmpty()) {
            emit(Resource.Error("No valid photos to upload."))
            return@flow
        }
        var result = safeApiCall {
            mainApiService.uploadOnboardingPhotos(parts)
        }
        if (result is Resource.Error && (result.code == 404 || result.code == 405)) {
            result = safeApiCall {
                mainApiService.updateGalleryPhotos(parts)
            }
        }
        if (result is Resource.Success) {
            emit(Resource.Success(result.data.getEffectivePhotos()))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun addOrReplaceGalleryPhoto(
        context: Context,
        slotIndex: Int,
        imageUri: Uri,
        currentPhotos: List<UserPhotoDto>
    ): Flow<Resource<List<UserPhotoDto>>> = flow {
        emit(Resource.Loading)
        // 1. Upload single image to S3 presigned URL with folder "photos"
        val s3Result = uploadToS3Presigned(context, imageUri, folder = "photos")
        if (s3Result is Resource.Success) {
            val newKey = s3Result.data.imageKey
            val targetKey = if (newKey.isNotBlank()) newKey else s3Result.data.publicUrl

            val updatedKeys = mutableListOf<String>()
            val maxSlots = maxOf(currentPhotos.size, slotIndex + 1)
            for (i in 0 until maxSlots) {
                if (i == slotIndex) {
                    updatedKeys.add(targetKey)
                } else {
                    val existing = currentPhotos.getOrNull(i)
                    val existingKey = existing?.effectiveKey?.takeIf { it.isNotBlank() }
                        ?: existing?.url?.takeIf { it.isNotBlank() }
                    if (existingKey != null) {
                        updatedKeys.add(existingKey)
                    }
                }
            }

            if (updatedKeys.size >= 3) {
                val replaceResult = safeApiCall {
                    mainApiService.updateGalleryPhotoKeys(mapOf("imageKeys" to updatedKeys))
                }
                if (replaceResult is Resource.Success) {
                    val profileRes = safeApiCall { mainApiService.getMyProfile() }
                    if (profileRes is Resource.Success) {
                        val photos = profileRes.data.profile?.photos.orEmpty()
                        if (photos.isNotEmpty()) {
                            emit(Resource.Success(photos))
                            return@flow
                        }
                    }
                }
            }
        }

        // Fallback: refresh current gallery
        getOnboardingPhotos().collect { emit(it) }
    }.flowOn(Dispatchers.IO)

    override fun deleteOnboardingPhoto(photoId: String): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.deleteOnboardingPhoto(photoId)
        }
        if (result is Resource.Success) {
            emit(Resource.Success(Unit))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun setPrimaryPhoto(photoId: String): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        var result = safeApiCall {
            mainApiService.setPrimaryPhoto(photoId)
        }
        if (result is Resource.Error && (result.code == 405 || result.code == 404)) {
            result = safeApiCall {
                mainApiService.setPrimaryPhotoPut(photoId)
            }
        }
        if (result is Resource.Success) {
            emit(Resource.Success(Unit))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun uploadAudioIntro(
        context: Context,
        audioUri: Uri,
        durationSeconds: Int
    ): Flow<Resource<AudioIntroDto>> = flow {
        emit(Resource.Loading)
        val part = MultipartHelper.createPartFromUri(context, audioUri, "audio")
        if (part == null) {
            emit(Resource.Error("Unable to process audio file."))
            return@flow
        }
        val durationPart = MultipartHelper.createTextPart(durationSeconds.toString())
        val result = safeApiCall {
            mainApiService.uploadAudioIntro(part, durationPart)
        }
        if (result is Resource.Success) {
            emit(Resource.Success(result.data.toEffectiveAudio()))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun getAudioIntro(): Flow<Resource<AudioIntroDto>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getAudioIntro()
        }
        if (result is Resource.Success) {
            emit(Resource.Success(result.data.toEffectiveAudio()))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun deleteAudioIntro(): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.deleteAudioIntro()
        }
        if (result is Resource.Success) {
            emit(Resource.Success(Unit))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun getSavedInterests(): Flow<Resource<List<UserInterestDto>>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getSavedInterests()
        }
        var interests = if (result is Resource.Success) {
            result.data.interests.orEmpty()
        } else {
            emptyList()
        }

        if (interests.isEmpty()) {
            val myProfileResult = safeApiCall {
                mainApiService.getMyProfile()
            }
            if (myProfileResult is Resource.Success) {
                val profileInterests = myProfileResult.data.interests.orEmpty()
                if (profileInterests.isNotEmpty()) {
                    interests = profileInterests
                }
            }
        }

        interests = resolveInterestsWithDefaultEmojis(interests)

        if (interests.isNotEmpty()) {
            emit(Resource.Success(interests))
        } else if (result is Resource.Success) {
            emit(Resource.Success(emptyList()))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun updateInterests(interestIds: List<String>): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val body = UpdateInterestsRequest(interestIds)
        val putResult = safeApiCall {
            mainApiService.updateInterestsPut(body)
        }
        if (putResult is Resource.Success) {
            emit(Resource.Success(Unit))
            return@flow
        }

        val postResult = safeApiCall {
            mainApiService.updateInterestsPost(body)
        }
        if (postResult is Resource.Success) {
            emit(Resource.Success(Unit))
            return@flow
        }

        val errorMessage = (putResult as? Resource.Error)?.message
            ?: (postResult as? Resource.Error)?.message
            ?: "Failed to update interests"
        emit(Resource.Error(errorMessage))
    }.flowOn(Dispatchers.IO)

    override fun getInterestsCatalog(): Flow<Resource<List<UserInterestDto>>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getInterestsCatalog()
        }
        var catalog = if (result is Resource.Success) {
            result.data.interests.orEmpty()
        } else {
            emptyList()
        }

        if (catalog.isEmpty()) {
            val savedResult = safeApiCall { mainApiService.getSavedInterests() }
            if (savedResult is Resource.Success) {
                val savedList = savedResult.data.interests.orEmpty()
                if (savedList.isNotEmpty()) {
                    catalog = savedList
                }
            }
        }

        catalog = resolveInterestsWithDefaultEmojis(catalog)
        if (catalog.isNotEmpty()) {
            emit(Resource.Success(catalog))
        } else if (result is Resource.Success) {
            emit(Resource.Success(emptyList()))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun getMoreAboutMe(): Flow<Resource<MoreAboutMeResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getMoreAboutMe()
        }
        if (result is Resource.Success) {
            emit(Resource.Success(result.data))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun updateMoreAboutMe(
        key: String,
        value: String?
    ): Flow<Resource<MoreAboutMeResponseData>> = flow {
        emit(Resource.Loading)
        val body = mapOf(key to value)
        val result = safeApiCall {
            mainApiService.updateMoreAboutMe(body)
        }
        if (result is Resource.Success) {
            emit(Resource.Success(result.data))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)


    // ==========================================
    // 3. OTHER USER PROFILE & DISCOVERY CANDIDATES
    // ==========================================

    override fun getOtherUserProfile(
        userId: String
    ): Flow<Resource<OtherUserProfileResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getOtherUserProfile(userId = userId)
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun submitDiscoveryAction(
        targetUserId: String,
        action: String
    ): Flow<Resource<DiscoveryActionResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.submitDiscoveryAction(
                DiscoveryActionRequest(
                    targetUserId = targetUserId,
                    action = action
                )
            )
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun getDiscoveryPeople(
        page: Int,
        limit: Int,
        interestId: String?
    ): Flow<Resource<DiscoveryPeopleResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getDiscoveryPeople(
                page = page,
                limit = limit,
                interestId = interestId
            )
        }
        emit(result)
    }.flowOn(Dispatchers.IO)



    private fun resolveInterestsWithDefaultEmojis(interests: List<UserInterestDto>): List<UserInterestDto> {
        return interests.map { item ->
            val name = item.name.orEmpty()
            val existingIcon = item.icon ?: item.emoji
            val resolvedEmoji = if (!existingIcon.isNullOrBlank() && existingIcon.any { it.code > 127 }) {
                existingIcon
            } else {
                getEmojiForInterestName(name)
            }
            item.copy(icon = resolvedEmoji, emoji = resolvedEmoji)
        }
    }

    private fun getEmojiForInterestName(name: String): String = when (name.lowercase().trim()) {
        "photography", "photo" -> "📸"
        "shopping" -> "🛍️"
        "karaoke" -> "🎤"
        "yoga" -> "🧘"
        "cooking", "food" -> "🍳"
        "tennis" -> "🎾"
        "run", "running" -> "🏃"
        "swimming" -> "🏊"
        "art", "painting", "drawing" -> "🎨"
        "travelling", "travel" -> "✈️"
        "extreme sports", "extreme" -> "🏄"
        "music" -> "🎵"
        "drink", "drinks", "bar" -> "🍹"
        "video games", "gaming", "games" -> "🎮"
        "reading", "books" -> "📚"
        "dance", "dancing" -> "💃"
        "fitness", "gym", "exercise" -> "💪"
        "coffee" -> "☕"
        "pets", "dogs", "cats" -> "🐶"
        "movies", "cinema" -> "🎬"
        "hiking" -> "🥾"
        else -> "✨"
    }
}

