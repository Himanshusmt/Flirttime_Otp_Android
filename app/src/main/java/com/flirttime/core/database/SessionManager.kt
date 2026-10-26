package com.flirttime.core.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "flirttime_preferences")

class SessionManager(private val context: Context) {

    private val dataStore = context.dataStore

    @Volatile
    private var cachedToken: String = ""

    init {
        CoroutineScope(Dispatchers.IO).launch {
            tokenFlow.collect { token ->
                cachedToken = token
            }
        }
    }

    /**
     * Non-blocking hot-cached token retrieval for network interceptors.
     */
    fun getCachedToken(): String = cachedToken

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

    val nickNameFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_NICK_NAME] ?: ""
        }

    val profileImageUriFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[KEY_PROFILE_IMAGE_URI] ?: ""
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

    suspend fun getNickName(): String = nickNameFlow.first()

    suspend fun getProfileImageUri(): String = profileImageUriFlow.first()

    suspend fun isVerified(): Boolean = isVerifiedFlow.first()

    suspend fun isProfileComplete(): Boolean = isProfileCompleteFlow.first()

    suspend fun isLoggedIn(): Boolean = getToken().isNotBlank()

    // Write operations
    suspend fun setToken(token: String) {
        cachedToken = token
        dataStore.edit { preferences ->
            preferences[KEY_TOKEN] = token
        }
    }

    suspend fun setUserId(userId: String) {
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

    suspend fun clearSession() {
        cachedToken = ""
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
        val KEY_DOB = stringPreferencesKey("dob")
        val KEY_GENDER = stringPreferencesKey("gender")
        val KEY_BIO = stringPreferencesKey("bio")
        val KEY_PROFILE_IMAGE_URI = stringPreferencesKey("profile_image_uri")
        val KEY_IS_VERIFIED = booleanPreferencesKey("is_verified")
        val KEY_IS_PROFILE_COMPLETE = booleanPreferencesKey("is_profile_complete")
    }
}
