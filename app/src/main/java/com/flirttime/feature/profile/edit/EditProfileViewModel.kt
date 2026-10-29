package com.flirttime.feature.profile.edit

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.core.database.SessionManager
import com.flirttime.feature.auth.AuthUserDto
import com.flirttime.feature.auth.AuthRepository
import com.flirttime.feature.profile.AudioIntroDto
import com.flirttime.feature.profile.MoreAboutMeResponseData
import com.flirttime.feature.profile.UpdateProfileRequest
import com.flirttime.feature.profile.UserInterestDto
import com.flirttime.feature.profile.UserPhotoDto
import com.flirttime.feature.main.MainRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EditProfileViewModel(
    private val mainRepository: MainRepository,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _photosState = MutableStateFlow<Resource<List<UserPhotoDto>>?>(null)
    val photosState: StateFlow<Resource<List<UserPhotoDto>>?> = _photosState.asStateFlow()

    private val _uploadPrimaryPhotoState = MutableStateFlow<Resource<AuthUserDto>?>(null)
    val uploadPrimaryPhotoState: StateFlow<Resource<AuthUserDto>?> = _uploadPrimaryPhotoState.asStateFlow()

    private val _uploadCoverPhotoState = MutableStateFlow<Resource<String>?>(null)
    val uploadCoverPhotoState: StateFlow<Resource<String>?> = _uploadCoverPhotoState.asStateFlow()

    private val _uploadGalleryPhotosState = MutableStateFlow<Resource<List<UserPhotoDto>>?>(null)
    val uploadGalleryPhotosState: StateFlow<Resource<List<UserPhotoDto>>?> = _uploadGalleryPhotosState.asStateFlow()

    private val _deletePhotoState = MutableStateFlow<Resource<Unit>?>(null)
    val deletePhotoState: StateFlow<Resource<Unit>?> = _deletePhotoState.asStateFlow()

    private val _setPrimaryPhotoState = MutableStateFlow<Resource<Unit>?>(null)
    val setPrimaryPhotoState: StateFlow<Resource<Unit>?> = _setPrimaryPhotoState.asStateFlow()

    private val _audioState = MutableStateFlow<Resource<AudioIntroDto>?>(null)
    val audioState: StateFlow<Resource<AudioIntroDto>?> = _audioState.asStateFlow()

    private val _uploadAudioState = MutableStateFlow<Resource<AudioIntroDto>?>(null)
    val uploadAudioState: StateFlow<Resource<AudioIntroDto>?> = _uploadAudioState.asStateFlow()

    private val _deleteAudioState = MutableStateFlow<Resource<Unit>?>(null)
    val deleteAudioState: StateFlow<Resource<Unit>?> = _deleteAudioState.asStateFlow()

    private val _updateProfileState = MutableStateFlow<Resource<AuthUserDto>?>(null)
    val updateProfileState: StateFlow<Resource<AuthUserDto>?> = _updateProfileState.asStateFlow()

    private val _savedInterestsState = MutableStateFlow<Resource<List<UserInterestDto>>?>(null)
    val savedInterestsState: StateFlow<Resource<List<UserInterestDto>>?> = _savedInterestsState.asStateFlow()

    private val _interestsCatalogState = MutableStateFlow<Resource<List<UserInterestDto>>?>(null)
    val interestsCatalogState: StateFlow<Resource<List<UserInterestDto>>?> = _interestsCatalogState.asStateFlow()

    private val _updateInterestsState = MutableStateFlow<Resource<Unit>?>(null)
    val updateInterestsState: StateFlow<Resource<Unit>?> = _updateInterestsState.asStateFlow()

    private val _moreAboutMeState = MutableStateFlow<Resource<MoreAboutMeResponseData>?>(null)
    val moreAboutMeState: StateFlow<Resource<MoreAboutMeResponseData>?> = _moreAboutMeState.asStateFlow()

    private val _updateMoreAboutMeState = MutableStateFlow<Resource<MoreAboutMeResponseData>?>(null)
    val updateMoreAboutMeState: StateFlow<Resource<MoreAboutMeResponseData>?> = _updateMoreAboutMeState.asStateFlow()

    init {
        fetchProfile()
        fetchPhotos()
        fetchAudioIntro()
        fetchSavedInterests()
        fetchMoreAboutMe()
    }

    fun fetchProfile() {
        viewModelScope.launch {
            mainRepository.getMyFullProfile().collectLatest { resource ->
                if (resource is Resource.Success) {
                    val full = resource.data
                    if (full.coverPhotoUrl?.isNotBlank() == true) {
                        sessionManager.setCoverPhotoUrl(full.coverPhotoUrl)
                    }
                    if (full.bio.isNotBlank()) {
                        sessionManager.setBio(full.bio)
                    }
                    if (full.location.isNotBlank()) {
                        sessionManager.setLocation(full.location)
                    }
                    if (full.profilePhotoUrl?.isNotBlank() == true) {
                        sessionManager.setProfileImageUri(full.profilePhotoUrl)
                    }
                    if (full.photos.isNotEmpty() && _photosState.value?.getOrNull().isNullOrEmpty()) {
                        _photosState.value = Resource.Success(full.photos)
                    }
                    if (full.interests.isNotEmpty() && _savedInterestsState.value?.getOrNull().isNullOrEmpty()) {
                        _savedInterestsState.value = Resource.Success(full.interests)
                    }
                    if (full.audioIntro != null && _audioState.value?.getOrNull()?.effectiveUrl.isNullOrBlank()) {
                        _audioState.value = Resource.Success(full.audioIntro)
                    }
                }
            }
        }
    }

    fun fetchPhotos() {
        viewModelScope.launch {
            mainRepository.getOnboardingPhotos().collectLatest { resource ->
                _photosState.value = resource
            }
        }
    }

    fun uploadPrimaryPhoto(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            mainRepository.uploadProfilePhoto(context, imageUri).collectLatest { resource ->
                if (resource is Resource.Success) {
                    resource.data.profilePicture?.let { sessionManager.setProfileImageUri(it) }
                    fetchPhotos()
                }
                _uploadPrimaryPhotoState.value = resource
            }
        }
    }

    fun uploadCoverPhoto(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            mainRepository.uploadCoverPhoto(context, imageUri).collectLatest { resource ->
                if (resource is Resource.Success) {
                    sessionManager.setCoverPhotoUrl(resource.data)
                }
                _uploadCoverPhotoState.value = resource
            }
        }
    }

    fun resetUploadCoverPhotoState() {
        _uploadCoverPhotoState.value = null
    }

    fun uploadGalleryPhotos(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            mainRepository.uploadOnboardingPhotos(context, uris).collectLatest { resource ->
                if (resource is Resource.Success) {
                    fetchPhotos()
                }
                _uploadGalleryPhotosState.value = resource
            }
        }
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            mainRepository.deleteOnboardingPhoto(photoId).collectLatest { resource ->
                if (resource is Resource.Success) {
                    fetchPhotos()
                }
                _deletePhotoState.value = resource
            }
        }
    }

    fun fetchAudioIntro() {
        viewModelScope.launch {
            mainRepository.getAudioIntro().collectLatest { resource ->
                _audioState.value = resource
            }
        }
    }

    fun uploadAudio(context: Context, audioUri: Uri, durationSeconds: Int) {
        viewModelScope.launch {
            mainRepository.uploadAudioIntro(context, audioUri, durationSeconds).collectLatest { resource ->
                if (resource is Resource.Success) {
                    fetchAudioIntro()
                }
                _uploadAudioState.value = resource
            }
        }
    }

    fun deleteAudio() {
        viewModelScope.launch {
            mainRepository.deleteAudioIntro().collectLatest { resource ->
                if (resource is Resource.Success) {
                    _audioState.value = Resource.Success(AudioIntroDto())
                }
                _deleteAudioState.value = resource
            }
        }
    }

    fun saveBio(bio: String) {
        viewModelScope.launch {
            val firstName = sessionManager.getFirstName()
            val lastName = sessionManager.getLastName()
            val nickName = sessionManager.getNickName()
            val fullName = when {
                firstName.isNotBlank() && lastName.isNotBlank() -> "$firstName $lastName"
                firstName.isNotBlank() -> firstName
                nickName.isNotBlank() -> nickName
                else -> "User"
            }
            val username = if (nickName.isNotBlank()) nickName else firstName.lowercase().replace(" ", "")
            val dob = sessionManager.getDob().ifBlank { "2000-01-01" }
            val gender = sessionManager.getGender().ifBlank { "male" }
            val location = sessionManager.getLocation().ifBlank { null }
            val coverUrl = sessionManager.getCoverPhotoUrl().ifBlank { null }

            val request = UpdateProfileRequest(
                fullName = fullName,
                username = username,
                dateOfBirth = dob,
                gender = gender,
                about = bio,
                location = location,
                coverPicture = coverUrl
            )
            mainRepository.updateProfile(request).collectLatest { resource ->
                if (resource is Resource.Success) {
                    sessionManager.setBio(bio)
                }
                _updateProfileState.value = resource
            }
        }
    }

    fun setPrimaryPhoto(photoId: String) {
        viewModelScope.launch {
            mainRepository.setPrimaryPhoto(photoId).collectLatest { resource ->
                if (resource is Resource.Success) {
                    fetchPhotos()
                    fetchProfile()
                }
                _setPrimaryPhotoState.value = resource
            }
        }
    }

    fun resetSetPrimaryPhotoState() {
        _setPrimaryPhotoState.value = null
    }

    fun saveProfile(
        firstName: String,
        lastName: String,
        nickName: String,
        dob: String,
        gender: String,
        location: String,
        bio: String
    ) {
        viewModelScope.launch {
            val fullName = when {
                firstName.isNotBlank() && lastName.isNotBlank() -> "$firstName $lastName"
                firstName.isNotBlank() -> firstName
                nickName.isNotBlank() -> nickName
                else -> "User"
            }
            val username = if (nickName.isNotBlank()) nickName else firstName.lowercase().replace(" ", "")
            val coverUrl = sessionManager.getCoverPhotoUrl().ifBlank { null }

            val request = UpdateProfileRequest(
                fullName = fullName,
                username = username,
                dateOfBirth = dob,
                gender = gender,
                location = location.ifBlank { null },
                about = bio,
                coverPicture = coverUrl
            )
            mainRepository.updateProfile(request).collectLatest { resource ->
                if (resource is Resource.Success) {
                    sessionManager.saveUserProfile(
                        firstName = firstName,
                        lastName = lastName,
                        nickName = nickName,
                        dob = dob,
                        gender = gender,
                        bio = bio
                    )
                    sessionManager.setLocation(location)
                }
                _updateProfileState.value = resource
            }
        }
    }

    fun resetUpdateProfileState() {
        _updateProfileState.value = null
    }

    fun resetUploadPrimaryPhotoState() {
        _uploadPrimaryPhotoState.value = null
    }

    fun resetUploadAudioState() {
        _uploadAudioState.value = null
    }

    fun resetDeletePhotoState() {
        _deletePhotoState.value = null
    }

    fun fetchSavedInterests() {
        viewModelScope.launch {
            mainRepository.getSavedInterests().collectLatest { resource ->
                _savedInterestsState.value = resource
            }
        }
    }

    fun fetchInterestsCatalog() {
        viewModelScope.launch {
            mainRepository.getInterestsCatalog().collectLatest { resource ->
                _interestsCatalogState.value = resource
            }
        }
    }

    fun updateInterests(interestIds: List<String>) {
        viewModelScope.launch {
            mainRepository.updateInterests(interestIds).collectLatest { resource ->
                if (resource is Resource.Success) {
                    fetchSavedInterests()
                }
                _updateInterestsState.value = resource
            }
        }
    }

    fun fetchMoreAboutMe() {
        viewModelScope.launch {
            mainRepository.getMoreAboutMe().collectLatest { resource ->
                _moreAboutMeState.value = resource
            }
        }
    }

    fun updateMoreAboutMe(key: String, value: String?) {
        viewModelScope.launch {
            mainRepository.updateMoreAboutMe(key, value).collectLatest { resource ->
                if (resource is Resource.Success) {
                    _moreAboutMeState.value = resource
                }
                _updateMoreAboutMeState.value = resource
            }
        }
    }
}
