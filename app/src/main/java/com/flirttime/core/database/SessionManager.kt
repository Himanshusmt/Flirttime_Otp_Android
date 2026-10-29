package com.flirttime.core.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "flirttime_preferences")

class SessionManager(private val context: Context) {

    private val dataStore = context.dataStore

    val tokenFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_TOKEN] ?: ""
        }

    val userIdFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_USER_ID] ?: ""
        }

    private val fastCache = context.getSharedPreferences("flirttime_fast_cache", Context.MODE_PRIVATE)

    @Volatile
    private var cachedToken: String = fastCache.getString("cached_jwt_token", "").orEmpty()

    @Volatile
    private var cachedUserId: String = fastCache.getString("cached_user_id", "").orEmpty()

    /**
     * Non-blocking hot-cached token retrieval for network interceptors.
     */
    fun getCachedToken(): String = cachedToken

    /**
     * Non-blocking hot-cached user ID retrieval.
     */
    fun getCachedUserId(): String = cachedUserId

    /**
     * Non-blocking hot-cached audio intro URL retrieval (zero delay on UI startup).
     */
    fun getCachedAudioIntroUrl(): String = fastCache.getString("cached_audio_intro_url", "").orEmpty()

    /**
     * Non-blocking hot-cached audio intro duration retrieval.
     */
    fun getCachedAudioIntroDuration(): Int = fastCache.getInt("cached_audio_intro_duration", 0)

    fun setCachedAudioIntro(url: String, duration: Int) {
        fastCache.edit()
            .putString("cached_audio_intro_url", url)
            .putInt("cached_audio_intro_duration", duration)
            .apply()
    }

    val pageRedirectFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_PAGE_REDIRECT] ?: ""
        }

    val isIntroCompleteFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_IS_INTRO_COMPLETE] ?: false
        }

    val firstNameFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_FIRST_NAME] ?: ""
        }

    val usernameFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_USERNAME] ?: ""
        }

    val emailFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_EMAIL] ?: ""
        }

    val phoneFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_PHONE] ?: ""
        }

    val fullNameFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_FULL_NAME] ?: ""
        }

    val nickNameFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_NICK_NAME] ?: ""
        }

    val lastNameFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_LAST_NAME] ?: ""
        }

    val dobFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_DOB] ?: ""
        }

    val bioFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_BIO] ?: ""
        }

    val genderFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_GENDER] ?: ""
        }

    val profileImageUriFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_PROFILE_IMAGE_URI] ?: ""
        }

    val coverPhotoUrlFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_COVER_PHOTO_URL] ?: ""
        }

    val locationFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_LOCATION] ?: ""
        }

    val audioIntroUrlFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_AUDIO_INTRO_URL] ?: ""
        }

    val audioIntroDurationFlow: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_AUDIO_INTRO_DURATION] ?: 0
        }

    val isVerifiedFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_IS_VERIFIED] ?: false
        }

    val isProfileCompleteFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_IS_PROFILE_COMPLETE] ?: false
        }

    // Synchronous snapshot reads when required in coroutine scopes
    suspend fun getToken(): String = tokenFlow.first()

    suspend fun getUserId(): String = userIdFlow.first()

    suspend fun getPageRedirect(): String = pageRedirectFlow.first()

    suspend fun isIntroComplete(): Boolean = isIntroCompleteFlow.first()

    suspend fun getFirstName(): String = firstNameFlow.first()

    suspend fun getLastName(): String = lastNameFlow.first()

    suspend fun getNickName(): String = nickNameFlow.first()

    suspend fun getDob(): String = dobFlow.first()

    suspend fun getBio(): String = bioFlow.first()

    suspend fun getGender(): String = genderFlow.first()

    suspend fun getUsername(): String = usernameFlow.first()

    suspend fun getFullName(): String = fullNameFlow.first()

    suspend fun getEmail(): String = emailFlow.first()

    suspend fun setEmail(email: String) {
        dataStore.edit { preferences ->
            preferences[KEY_EMAIL] = email
        }
    }

    suspend fun getPhone(): String = phoneFlow.first()

    suspend fun setPhone(phone: String) {
        dataStore.edit { preferences ->
            preferences[KEY_PHONE] = phone
        }
    }

    suspend fun setUsername(username: String) {
        dataStore.edit { preferences ->
            preferences[KEY_USERNAME] = username
        }
    }

    suspend fun setFullName(fullName: String) {
        dataStore.edit { preferences ->
            preferences[KEY_FULL_NAME] = fullName
        }
    }

    suspend fun setGender(gender: String) {
        dataStore.edit { preferences ->
            preferences[KEY_GENDER] = gender
        }
    }

    suspend fun setFirstName(firstName: String) {
        dataStore.edit { preferences ->
            preferences[KEY_FIRST_NAME] = firstName
        }
    }

    suspend fun setLastName(lastName: String) {
        dataStore.edit { preferences ->
            preferences[KEY_LAST_NAME] = lastName
        }
    }

    suspend fun setDob(dob: String) {
        dataStore.edit { preferences ->
            preferences[KEY_DOB] = dob
        }
    }

    suspend fun setBio(bio: String) {
        dataStore.edit { preferences ->
            preferences[KEY_BIO] = bio
        }
    }

    suspend fun getProfileImageUri(): String = profileImageUriFlow.first()

    suspend fun getCoverPhotoUrl(): String = coverPhotoUrlFlow.first()

    suspend fun setCoverPhotoUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[KEY_COVER_PHOTO_URL] = url
        }
    }

    suspend fun getLocation(): String = locationFlow.first()

    suspend fun setLocation(location: String) {
        dataStore.edit { preferences ->
            preferences[KEY_LOCATION] = location
        }
    }

    suspend fun getAudioIntroUrl(): String = audioIntroUrlFlow.first()

    suspend fun setAudioIntroUrl(url: String) {
        fastCache.edit().putString("cached_audio_intro_url", url).apply()
        dataStore.edit { preferences ->
            preferences[KEY_AUDIO_INTRO_URL] = url
        }
    }

    suspend fun getAudioIntroDuration(): Int = audioIntroDurationFlow.first()

    suspend fun setAudioIntroDuration(duration: Int) {
        fastCache.edit().putInt("cached_audio_intro_duration", duration).apply()
        dataStore.edit { preferences ->
            preferences[KEY_AUDIO_INTRO_DURATION] = duration
        }
    }

    suspend fun isVerified(): Boolean = isVerifiedFlow.first()

    suspend fun isProfileComplete(): Boolean = isProfileCompleteFlow.first()

    suspend fun isLoggedIn(): Boolean = getToken().isNotBlank()

    // Write operations
    suspend fun setToken(token: String) {
        cachedToken = token
        fastCache.edit().putString("cached_jwt_token", token).apply()
        dataStore.edit { preferences ->
            preferences[KEY_TOKEN] = token
        }
    }

    suspend fun setUserId(userId: String) {
        cachedUserId = userId
        fastCache.edit().putString("cached_user_id", userId).apply()
        dataStore.edit { preferences ->
            preferences[KEY_USER_ID] = userId
        }
    }

    suspend fun setPageRedirect(pageRedirect: String) {
        dataStore.edit { preferences ->
            preferences[KEY_PAGE_REDIRECT] = pageRedirect
        }
    }

    suspend fun setIntroComplete(isComplete: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_IS_INTRO_COMPLETE] = isComplete
        }
    }

    suspend fun saveUserProfile(
        firstName: String,
        lastName: String,
        nickName: String,
        dob: String,
        gender: String,
        bio: String
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_FIRST_NAME] = firstName
            preferences[KEY_LAST_NAME] = lastName
            preferences[KEY_NICK_NAME] = nickName
            preferences[KEY_DOB] = dob
            preferences[KEY_GENDER] = gender
            preferences[KEY_BIO] = bio
        }
    }

    suspend fun setProfileImageUri(uri: String) {
        dataStore.edit { preferences ->
            preferences[KEY_PROFILE_IMAGE_URI] = uri
        }
    }

    suspend fun setVerified(isVerified: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_IS_VERIFIED] = isVerified
        }
    }

    suspend fun setProfileComplete(isComplete: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_IS_PROFILE_COMPLETE] = isComplete
        }
    }

    suspend fun getOrCreateDeviceToken(): String {
        val existingToken = dataStore.data.first()[KEY_DEVICE_TOKEN]
        if (!existingToken.isNullOrBlank()) {
            return existingToken
        }
        val generatedToken = java.util.UUID.randomUUID().toString()
        setDeviceToken(generatedToken)
        return generatedToken
    }

    suspend fun setDeviceToken(token: String) {
        dataStore.edit { preferences ->
            preferences[KEY_DEVICE_TOKEN] = token
        }
    }

    suspend fun clearSession() {
        cachedToken = ""
        cachedUserId = ""
        fastCache.edit().clear().apply()
        dataStore.edit { preferences ->
            val introStatus = preferences[KEY_IS_INTRO_COMPLETE] ?: false
            preferences.clear()
            // Preserve intro completed state after clearing user auth session
            preferences[KEY_IS_INTRO_COMPLETE] = introStatus
        }
    }

    companion object {
        val KEY_TOKEN = stringPreferencesKey("jwt_token")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_PAGE_REDIRECT = stringPreferencesKey("page_redirect")
        val KEY_IS_INTRO_COMPLETE = booleanPreferencesKey("is_intro_complete")
        val KEY_FIRST_NAME = stringPreferencesKey("first_name")
        val KEY_LAST_NAME = stringPreferencesKey("last_name")
        val KEY_NICK_NAME = stringPreferencesKey("nick_name")
        val KEY_FULL_NAME = stringPreferencesKey("full_name")
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_EMAIL = stringPreferencesKey("email")
        val KEY_PHONE = stringPreferencesKey("phone")
        val KEY_DOB = stringPreferencesKey("dob")
        val KEY_GENDER = stringPreferencesKey("gender")
        val KEY_BIO = stringPreferencesKey("bio")
        val KEY_LOCATION = stringPreferencesKey("location")
        val KEY_PROFILE_IMAGE_URI = stringPreferencesKey("profile_image_uri")
        val KEY_COVER_PHOTO_URL = stringPreferencesKey("cover_photo_url")
        val KEY_AUDIO_INTRO_URL = stringPreferencesKey("audio_intro_url")
        val KEY_AUDIO_INTRO_DURATION = intPreferencesKey("audio_intro_duration")
        val KEY_IS_VERIFIED = booleanPreferencesKey("is_verified")
        val KEY_IS_PROFILE_COMPLETE = booleanPreferencesKey("is_profile_complete")
        val KEY_DEVICE_TOKEN = stringPreferencesKey("device_token")
    }
}
