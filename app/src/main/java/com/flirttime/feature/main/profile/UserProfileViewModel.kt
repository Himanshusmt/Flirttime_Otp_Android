package com.flirttime.feature.main.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.core.database.SessionManager
import com.flirttime.feature.auth.AuthUserDto
import com.flirttime.feature.auth.AuthRepository
import com.flirttime.feature.main.MainRepository
import com.flirttime.feature.moments.MomentDto
import com.flirttime.feature.moments.UserMomentsResponseData
import com.flirttime.feature.moments.MomentsRepository
import com.flirttime.feature.main.matches.MatchesRepository
import com.flirttime.feature.profile.AudioIntroDto
import com.flirttime.feature.profile.MoreAboutMeResponseData
import com.flirttime.feature.profile.UpdateProfileRequest
import com.flirttime.feature.profile.UserInterestDto
import com.flirttime.feature.profile.UserPhotoDto
import com.flirttime.feature.subscription.MySubscription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class UserProfileUiModel(
    val name: String,
    val age: Int?,
    val location: String,
    val bio: String,
    val completionPercent: Int,
    val isVerified: Boolean,
    val profilePhotoUrl: String?,
    val coverPhotoUrl: String?,
    val isSubscribed: Boolean = false
)

class UserProfileViewModel(
    private val authRepository: AuthRepository,
    private val matchesRepository: MatchesRepository,
    private val momentsRepository: MomentsRepository,
    private val mainRepository: MainRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private companion object {
        /** iOS fetches 10 moments so the 3x3 grid can detect an overflow ("+N" tile). */
        const val MY_MOMENTS_PREVIEW_LIMIT = 10
    }

    private var cachedFullProfile: com.flirttime.feature.profile.MyFullProfileData? = null

    private val _userProfileState = MutableStateFlow<UserProfileUiModel?>(null)
    val userProfileState: StateFlow<UserProfileUiModel?> = _userProfileState.asStateFlow()

    private val _userMomentsState = MutableStateFlow<Resource<UserMomentsResponseData>?>(null)
    val userMomentsState: StateFlow<Resource<UserMomentsResponseData>?> = _userMomentsState.asStateFlow()

    private val _savedInterestsState = MutableStateFlow<Resource<List<UserInterestDto>>?>(null)
    val savedInterestsState: StateFlow<Resource<List<UserInterestDto>>?> = _savedInterestsState.asStateFlow()

    private val _userPhotosState = MutableStateFlow<Resource<List<UserPhotoDto>>?>(null)
    val userPhotosState: StateFlow<Resource<List<UserPhotoDto>>?> = _userPhotosState.asStateFlow()

    private val _userAudioState = MutableStateFlow<Resource<AudioIntroDto>?>(
        sessionManager.getCachedAudioIntroUrl().takeIf { it.isNotBlank() }?.let { url ->
            Resource.Success(
                AudioIntroDto(
                    url = url,
                    audioUrl = url,
                    duration = sessionManager.getCachedAudioIntroDuration()
                )
            )
        } ?: Resource.Loading
    )
    val userAudioState: StateFlow<Resource<AudioIntroDto>?> = _userAudioState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _uploadCoverPhotoState = MutableStateFlow<Resource<String>?>(null)
    val uploadCoverPhotoState: StateFlow<Resource<String>?> = _uploadCoverPhotoState.asStateFlow()

    private val _myQuickMomentsState = MutableStateFlow<Resource<com.flirttime.feature.quickmoments.MyQuickMomentsResponseData>?>(null)
    val myQuickMomentsState: StateFlow<Resource<com.flirttime.feature.quickmoments.MyQuickMomentsResponseData>?> = _myQuickMomentsState.asStateFlow()

    private val _coinBalanceState = MutableStateFlow<Int>(0)
    val coinBalanceState: StateFlow<Int> = _coinBalanceState.asStateFlow()

    /** Cached `GET subscriptions/my` (iOS `SubscriptionMembershipStore.membership`). `null` until loaded / no membership. */
    private val _membershipState = MutableStateFlow<MySubscription?>(null)
    val membershipState: StateFlow<MySubscription?> = _membershipState.asStateFlow()

    private val _uploadPrimaryPhotoState = MutableStateFlow<Resource<AuthUserDto>?>(null)
    val uploadPrimaryPhotoState: StateFlow<Resource<AuthUserDto>?> = _uploadPrimaryPhotoState.asStateFlow()

    private val _setPrimaryPhotoState = MutableStateFlow<Resource<Unit>?>(null)
    val setPrimaryPhotoState: StateFlow<Resource<Unit>?> = _setPrimaryPhotoState.asStateFlow()

    private val _uploadGalleryPhotosState = MutableStateFlow<Resource<List<UserPhotoDto>>?>(null)
    val uploadGalleryPhotosState: StateFlow<Resource<List<UserPhotoDto>>?> = _uploadGalleryPhotosState.asStateFlow()

    private val _deletePhotoState = MutableStateFlow<Resource<Unit>?>(null)
    val deletePhotoState: StateFlow<Resource<Unit>?> = _deletePhotoState.asStateFlow()

    private val _interestsCatalogState = MutableStateFlow<Resource<List<UserInterestDto>>?>(null)
    val interestsCatalogState: StateFlow<Resource<List<UserInterestDto>>?> = _interestsCatalogState.asStateFlow()

    private val _updateInterestsState = MutableStateFlow<Resource<Unit>?>(null)
    val updateInterestsState: StateFlow<Resource<Unit>?> = _updateInterestsState.asStateFlow()

    private val _moreAboutMeState = MutableStateFlow<Resource<MoreAboutMeResponseData>?>(null)
    val moreAboutMeState: StateFlow<Resource<MoreAboutMeResponseData>?> = _moreAboutMeState.asStateFlow()

    private val _updateMoreAboutMeState = MutableStateFlow<Resource<MoreAboutMeResponseData>?>(null)
    val updateMoreAboutMeState: StateFlow<Resource<MoreAboutMeResponseData>?> = _updateMoreAboutMeState.asStateFlow()

    private val _updateProfileState = MutableStateFlow<Resource<AuthUserDto>?>(null)
    val updateProfileState: StateFlow<Resource<AuthUserDto>?> = _updateProfileState.asStateFlow()

    private val _uploadAudioState = MutableStateFlow<Resource<AudioIntroDto>?>(null)
    val uploadAudioState: StateFlow<Resource<AudioIntroDto>?> = _uploadAudioState.asStateFlow()

    private val _deleteAudioState = MutableStateFlow<Resource<Unit>?>(null)
    val deleteAudioState: StateFlow<Resource<Unit>?> = _deleteAudioState.asStateFlow()

    val photosState: StateFlow<Resource<List<UserPhotoDto>>?> get() = userPhotosState
    val audioState: StateFlow<Resource<AudioIntroDto>?> get() = userAudioState

    init {
        loadLocalProfile()
        fetchUserProfile()
        fetchMoreAboutMe()
        loadWalletBalance()
        loadMyQuickMoments()
        loadMyMoments()
        loadMembership()
    }

    private fun loadLocalProfile() {
        viewModelScope.launch {
            val firstName = sessionManager.getFirstName()
            val lastName = sessionManager.getLastName()
            val nickName = sessionManager.getNickName()
            val dob = sessionManager.getDob()
            val bio = sessionManager.getBio()
            val location = sessionManager.getLocation()
            val photoUri = sessionManager.getProfileImageUri()
            val coverUri = sessionManager.getCoverPhotoUrl()
            val isVerified = sessionManager.isVerified()
            val isComplete = sessionManager.isProfileComplete()
            val audioUrl = sessionManager.getAudioIntroUrl()
            val audioDuration = sessionManager.getAudioIntroDuration()

            // iOS header shows the username first (see `ProfileViewController.headerName`).
            val effectiveName = when {
                sessionManager.getUsername().isNotBlank() -> sessionManager.getUsername()
                firstName.isNotBlank() && lastName.isNotBlank() -> "$firstName $lastName"
                firstName.isNotBlank() -> firstName
                nickName.isNotBlank() -> nickName
                else -> "User"
            }
            // iOS only shows an age when the profile has one; no placeholder age.
            val age = calculateAge(dob)
            val completion = if (isComplete) 100 else 90

            _userProfileState.value = UserProfileUiModel(
                name = effectiveName,
                age = age,
                location = location,
                bio = bio,
                completionPercent = completion,
                isVerified = isVerified,
                profilePhotoUrl = photoUri.ifBlank { null },
                coverPhotoUrl = coverUri.ifBlank { null }
            )

            // Pre-populate voice note from cache immediately so voice player renders on frame 1
            if (audioUrl.isNotBlank() && _userAudioState.value == null) {
                _userAudioState.value = Resource.Success(
                    AudioIntroDto(
                        url = audioUrl,
                        audioUrl = audioUrl,
                        duration = audioDuration
                    )
                )
            }
        }
    }

    fun fetchUserProfile() {
        viewModelScope.launch {
            // First load local profile only if state is not loaded yet (prevents double flicker)
            if (_userProfileState.value == null) {
                loadLocalProfile()
            }
            awaitProfile()
        }
    }

    private suspend fun awaitProfile() {
        mainRepository.getMyFullProfile().collectLatest { resource ->
            when (resource) {
                is Resource.Success -> applyFullProfile(resource.data)
                is Resource.Error ->
                    android.util.Log.e("UserProfileViewModel", "getMyFullProfile failed: ${resource.message}")
                is Resource.Loading -> Unit
            }
        }
    }

    /**
     * Mirrors iOS `ProfileViewController.loadData()`: profile, quick moments, my moments, wallet and
     * membership are all reloaded, and the pull-to-refresh spinner stops only when every request finished.
     */
    fun loadAll() {
        fetchUserProfile()
        loadMyQuickMoments()
        loadMyMoments()
        loadWalletBalance()
        loadMembership()
    }

    fun refreshProfile() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                coroutineScope {
                    launch { runCatching { awaitProfile() } }
                    launch { runCatching { awaitMyQuickMoments() } }
                    launch { runCatching { awaitMyMoments() } }
                    launch { runCatching { awaitWalletBalance() } }
                    launch { runCatching { awaitMembership() } }
                }
            } catch (e: Exception) {
                android.util.Log.e("UserProfileViewModel", "Error refreshing profile: ${e.localizedMessage}", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun uploadCoverPhoto(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            _uploadCoverPhotoState.value = Resource.Loading
            mainRepository.uploadCoverPhoto(context, imageUri).collectLatest { resource ->
                if (resource is Resource.Success) {
                    sessionManager.setCoverPhotoUrl(resource.data)
                    _userProfileState.value = _userProfileState.value?.copy(coverPhotoUrl = resource.data)
                }
                _uploadCoverPhotoState.value = resource
            }
        }
    }

    fun resetUploadCoverPhotoState() {
        _uploadCoverPhotoState.value = null
    }

    private suspend fun applyFullProfile(full: com.flirttime.feature.profile.MyFullProfileData) {
        cachedFullProfile = full
        val localDob = sessionManager.getDob()
        // iOS shows "name, age" only when the profile actually has an age.
        val calculatedAge = full.age ?: calculateAge(localDob)
        val current = _userProfileState.value

        val resolvedLocation = when {
            full.location.isNotBlank() -> full.location
            sessionManager.getLocation().isNotBlank() -> sessionManager.getLocation()
            else -> current?.location.orEmpty()
        }

        val resolvedBio = when {
            full.bio.isNotBlank() -> full.bio
            sessionManager.getBio().isNotBlank() -> sessionManager.getBio()
            else -> current?.bio.orEmpty()
        }

        _userProfileState.value = UserProfileUiModel(
            name = full.headerName.ifBlank { full.name }.ifBlank { current?.name ?: "User" },
            age = calculatedAge,
            location = resolvedLocation,
            bio = resolvedBio,
            completionPercent = full.completionPercent,
            isVerified = full.isVerified,
            profilePhotoUrl = full.profilePhotoUrl ?: sessionManager.getProfileImageUri().ifBlank { null },
            coverPhotoUrl = full.coverPhotoUrl ?: sessionManager.getCoverPhotoUrl().ifBlank { null },
            isSubscribed = full.isSubscribed
        )

        // The offline fallback is built from local session data only; never let it wipe server lists.
        if (full.isOfflineFallback) return

        // 1. Photos
        _userPhotosState.value = Resource.Success(full.photos)

        // 2. Interests
        _savedInterestsState.value = Resource.Success(full.interests)

        // 3. More About Me (atomic from profile)
        if (full.moreAboutMe != null) {
            _moreAboutMeState.value = Resource.Success(full.moreAboutMe)
        }

        // 4. Moments come from their own call (`GET moments/me`, see `loadMyMoments`) exactly like iOS,
        //    because `users/profile` does not carry moments or their total count.

        // 5. Voice Intro: server truth, only playable once `status == "ready"` (iOS `userVoiceIntro`).
        if (full.audioIntro != null) {
            _userAudioState.value = Resource.Success(full.audioIntro)
        } else if (!full.voiceIntroUrl.isNullOrBlank()) {
            _userAudioState.value = Resource.Success(
                AudioIntroDto(
                    url = full.voiceIntroUrl,
                    audioUrl = full.voiceIntroUrl,
                    playbackUrl = full.voiceIntroUrl,
                    duration = full.voiceDuration,
                    status = "ready"
                )
            )
        } else {
            sessionManager.setAudioIntroUrl("")
            sessionManager.setAudioIntroDuration(0)
            _userAudioState.value = Resource.Success(AudioIntroDto())
        }

        // 6. Quick Moments (from master profile)
        if (full.quickMoments != null) {
            _myQuickMomentsState.value = Resource.Success(full.quickMoments)
        }
    }

    fun loadMyQuickMoments() {
        viewModelScope.launch { awaitMyQuickMoments() }
    }

    private suspend fun awaitMyQuickMoments() {
        matchesRepository.getMyQuickMoments().collectLatest { resource ->
            // Keep the previous list when a refresh fails (iOS ignores quick-moment errors).
            if (resource is Resource.Success || _myQuickMomentsState.value == null) {
                _myQuickMomentsState.value = resource
            }
        }
    }

    fun loadWalletBalance() {
        viewModelScope.launch { awaitWalletBalance() }
    }

    private suspend fun awaitWalletBalance() {
        matchesRepository.getWalletBalance().collectLatest { res ->
            if (res is Resource.Success) {
                _coinBalanceState.value = res.data.balance
            }
        }
    }

    /**
     * Latest moments for the Profile grid: `GET moments/me?page=1&limit=10` (iOS
     * `ProfileViewModel.getMyMoments`). The 10th item only previews the "+N" tile; `totalCount` drives it.
     */
    fun loadMyMoments() {
        viewModelScope.launch { awaitMyMoments() }
    }

    private suspend fun awaitMyMoments() {
        momentsRepository.getMyMoments(page = 1, limit = MY_MOMENTS_PREVIEW_LIMIT).collectLatest { result ->
            when (result) {
                is Resource.Success -> {
                    val moments = result.data.moments.orEmpty().take(MY_MOMENTS_PREVIEW_LIMIT)
                    val total = maxOf(result.data.totalCount ?: result.data.momentCount ?: moments.size, moments.size)
                    _userMomentsState.value = Resource.Success(
                        UserMomentsResponseData(moments = moments, totalCount = total)
                    )
                }
                is Resource.Error -> {
                    android.util.Log.e("UserProfileViewModel", "getMyMoments error: ${result.message}")
                    // iOS: on failure show the empty state only if nothing has loaded yet.
                    if (_userMomentsState.value == null) {
                        _userMomentsState.value = Resource.Success(
                            UserMomentsResponseData(moments = emptyList(), totalCount = 0)
                        )
                    }
                }
                is Resource.Loading -> Unit
            }
        }
    }

    /** `GET subscriptions/my` (iOS `SubscriptionMembershipStore.refresh`). A failure keeps the last membership. */
    fun loadMembership() {
        viewModelScope.launch { awaitMembership() }
    }

    private suspend fun awaitMembership() {
        mainRepository.getMySubscription().collectLatest { result ->
            if (result is Resource.Success) {
                _membershipState.value = result.data
            } else if (result is Resource.Error) {
                android.util.Log.e("UserProfileViewModel", "my subscription failed: ${result.message}")
            }
        }
    }

    fun deleteQuickMoment(momentId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            matchesRepository.deleteQuickMoment(momentId).collect { res ->
                if (res is Resource.Success) {
                    onDeleted()
                    loadMyQuickMoments()
                }
            }
        }
    }

    fun loadUserMoments(userId: String? = null) {
        viewModelScope.launch {
            val storedId = sessionManager.getUserId()
            val isCurrentUser = userId.isNullOrBlank() || userId == "me" || (storedId.isNotBlank() && userId == storedId)

            if (isCurrentUser) {
                momentsRepository.getMyMoments(page = 1, limit = 4).collect { result ->
                    if (result is Resource.Error) {
                        android.util.Log.e("UserProfileViewModel", "getMyMoments error: ${result.message}")
                    }
                    _userMomentsState.value = result
                }
            } else {
                momentsRepository.getUserMoments(userId = userId!!, page = 1, limit = 20).collect { result ->
                    if (result is Resource.Error) {
                        android.util.Log.e("UserProfileViewModel", "loadUserMoments error: ${result.message}")
                    }
                    _userMomentsState.value = result
                }
            }
        }
    }

    fun loadSavedInterests() {
        viewModelScope.launch {
            mainRepository.getSavedInterests().collect { result ->
                _savedInterestsState.value = result
            }
        }
    }

    fun loadUserPhotos() {
        viewModelScope.launch {
            mainRepository.getOnboardingPhotos().collect { result ->
                _userPhotosState.value = result
            }
        }
    }

    fun fetchPhotos() = loadUserPhotos()
    fun fetchProfile() = fetchUserProfile()
    fun fetchAudioIntro() = loadUserAudio()

    fun loadUserAudio() {
        viewModelScope.launch {
            // First emit cached audio if available
            val cachedUrl = sessionManager.getAudioIntroUrl()
            val cachedDuration = sessionManager.getAudioIntroDuration()
            if (cachedUrl.isNotBlank()) {
                _userAudioState.value = Resource.Success(
                    AudioIntroDto(
                        url = cachedUrl,
                        audioUrl = cachedUrl,
                        duration = cachedDuration
                    )
                )
            }

            mainRepository.getAudioIntro().collect { result ->
                if (result is Resource.Success) {
                    val audio = result.data
                    val url = audio.effectiveUrl.orEmpty()
                    val duration = audio.duration ?: 0
                    if (url.isNotBlank()) {
                        sessionManager.setAudioIntroUrl(url)
                        sessionManager.setAudioIntroDuration(duration)
                    }
                }
                _userAudioState.value = result
            }
        }
    }

    fun uploadAudio(context: Context, audioUri: Uri, durationSeconds: Int) {
        viewModelScope.launch {
            mainRepository.uploadAudioIntro(context, audioUri, durationSeconds).collect { result ->
                if (result is Resource.Success) {
                    val audio = result.data
                    val url = audio.effectiveUrl.orEmpty()
                    if (url.isNotBlank()) {
                        sessionManager.setAudioIntroUrl(url)
                        sessionManager.setAudioIntroDuration(durationSeconds)
                    }
                }
                _userAudioState.value = result
                _uploadAudioState.value = result
            }
        }
    }

    fun deleteAudio() {
        viewModelScope.launch {
            mainRepository.deleteAudioIntro().collect { result ->
                if (result is Resource.Success) {
                    sessionManager.setAudioIntroUrl("")
                    sessionManager.setAudioIntroDuration(0)
                    _userAudioState.value = Resource.Success(AudioIntroDto())
                }
                _deleteAudioState.value = result
            }
        }
    }

    fun resetUploadAudioState() {
        _uploadAudioState.value = null
    }

    fun resetDeleteAudioState() {
        _deleteAudioState.value = null
    }

    private fun calculateAge(dobString: String?): Int? {
        if (dobString.isNullOrBlank()) return null
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        )
        for (format in formats) {
            try {
                val birthDate = format.parse(dobString) ?: continue
                val birth = Calendar.getInstance().apply { time = birthDate }
                val today = Calendar.getInstance()
                var age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
                if (today.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) {
                    age--
                }
                if (age in 10..120) return age
            } catch (_: Exception) { }
        }
        return null
    }

    fun getPreviewMoments(): List<MomentDto> = emptyList()

    fun uploadPrimaryPhoto(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            mainRepository.uploadProfilePhoto(context, imageUri).collectLatest { resource ->
                if (resource is Resource.Success) {
                    val photoUrl = resource.data.profilePicture
                    if (!photoUrl.isNullOrBlank()) {
                        sessionManager.setProfileImageUri(photoUrl)
                        _userProfileState.value = _userProfileState.value?.copy(profilePhotoUrl = photoUrl)
                    }
                    loadUserPhotos()
                }
                _uploadPrimaryPhotoState.value = resource
            }
        }
    }

    fun setPrimaryPhoto(photoId: String) {
        viewModelScope.launch {
            mainRepository.setPrimaryPhoto(photoId).collectLatest { resource ->
                if (resource is Resource.Success) {
                    loadUserPhotos()
                    fetchUserProfile()
                }
                _setPrimaryPhotoState.value = resource
            }
        }
    }

    fun addOrReplaceGalleryPhoto(context: Context, slotIndex: Int, uri: Uri) {
        viewModelScope.launch {
            _uploadGalleryPhotosState.value = Resource.Loading
            val currentList = _userPhotosState.value?.getOrNull() ?: emptyList()
            mainRepository.addOrReplaceGalleryPhoto(context, slotIndex, uri, currentList).collectLatest { resource ->
                if (resource is Resource.Success) {
                    _userPhotosState.value = resource
                    fetchUserProfile()
                }
                _uploadGalleryPhotosState.value = resource
            }
        }
    }

    fun uploadGalleryPhotos(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            mainRepository.uploadOnboardingPhotos(context, uris).collectLatest { resource ->
                if (resource is Resource.Success) {
                    _userPhotosState.value = resource
                    fetchUserProfile()
                }
                _uploadGalleryPhotosState.value = resource
            }
        }
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            mainRepository.deleteOnboardingPhoto(photoId).collectLatest { resource ->
                if (resource is Resource.Success) {
                    fetchUserProfile()
                }
                _deletePhotoState.value = resource
            }
        }
    }

    fun saveBio(bio: String) {
        viewModelScope.launch {
            val current = cachedFullProfile
            val storedFullName = sessionManager.getFullName()
            val storedUsername = sessionManager.getUsername()
            val firstName = sessionManager.getFirstName()
            val lastName = sessionManager.getLastName()
            val nickName = sessionManager.getNickName()

            val fullName = when {
                current?.fullName?.isNotBlank() == true -> current.fullName
                storedFullName.isNotBlank() -> storedFullName
                firstName.isNotBlank() && lastName.isNotBlank() -> "$firstName $lastName"
                firstName.isNotBlank() -> firstName
                nickName.isNotBlank() -> nickName
                else -> current?.name?.takeIf { it.isNotBlank() } ?: "User"
            }
            val username = when {
                current?.username?.isNotBlank() == true -> current.username
                storedUsername.isNotBlank() -> storedUsername
                nickName.isNotBlank() -> nickName
                firstName.isNotBlank() -> firstName.lowercase().replace(" ", "")
                else -> "user_${System.currentTimeMillis() % 10000}"
            }
            val dob = when {
                current?.dateOfBirth?.isNotBlank() == true -> current.dateOfBirth
                sessionManager.getDob().isNotBlank() -> sessionManager.getDob()
                else -> "2000-01-01"
            }
            val gender = when {
                current?.gender?.isNotBlank() == true -> current.gender
                sessionManager.getGender().isNotBlank() -> sessionManager.getGender()
                else -> "male"
            }
            val location = when {
                current?.location?.isNotBlank() == true -> current.location
                sessionManager.getLocation().isNotBlank() -> sessionManager.getLocation()
                else -> null
            }
            val coverUrl = when {
                current?.coverPhotoUrl?.isNotBlank() == true -> current.coverPhotoUrl
                sessionManager.getCoverPhotoUrl().isNotBlank() -> sessionManager.getCoverPhotoUrl()
                else -> null
            }

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
                    _userProfileState.value = _userProfileState.value?.copy(bio = bio)
                }
                _updateProfileState.value = resource
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
                    loadSavedInterests()
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

    fun resetUploadPrimaryPhotoState() {
        _uploadPrimaryPhotoState.value = null
    }

    fun resetSetPrimaryPhotoState() {
        _setPrimaryPhotoState.value = null
    }

    fun resetUpdateProfileState() {
        _updateProfileState.value = null
    }
}
