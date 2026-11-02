package com.flirttime.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.feature.auth.AuthRepository
import com.flirttime.feature.auth.AuthUserDto
import com.flirttime.feature.main.MainRepository
import com.flirttime.feature.profile.UpdateProfileRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class ProfileViewModel(
    private val mainRepository: MainRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _updateProfileState = MutableStateFlow<Resource<AuthUserDto>?>(null)
    val updateProfileState: StateFlow<Resource<AuthUserDto>?> = _updateProfileState.asStateFlow()

    private val _usernameValid = MutableStateFlow(false)
    val usernameValid: StateFlow<Boolean> = _usernameValid.asStateFlow()

    private val _usernameError = MutableStateFlow<String?>(null)
    val usernameError: StateFlow<String?> = _usernameError.asStateFlow()

    private var usernameCheckJob: Job? = null
    private var lastRequestedUsername: String = ""

    fun reportScreenOpened() {
        viewModelScope.launch {
            mainRepository.reportOnboardingScreen(OnboardingStep.BASIC_INFO).collect { }
        }
    }

    fun onDisplayNameChanged(raw: String) {
        usernameCheckJob?.cancel()
        val nickName = raw.trim()
        if (nickName.isEmpty()) {
            lastRequestedUsername = ""
            _usernameValid.value = false
            _usernameError.value = null
            return
        }
        if (nickName.length < 3) {
            lastRequestedUsername = ""
            _usernameValid.value = false
            _usernameError.value = "Display name must be at least 3 characters"
            return
        }
        _usernameValid.value = false
        _usernameError.value = null
        lastRequestedUsername = nickName
        usernameCheckJob = viewModelScope.launch {
            delay(500)
            authRepository.checkUsernameAvailability(nickName).collect { resource ->
                if (nickName != lastRequestedUsername) return@collect
                when (resource) {
                    is Resource.Success -> {
                        if (resource.data) {
                            _usernameValid.value = true
                            _usernameError.value = null
                        } else {
                            _usernameValid.value = false
                            _usernameError.value = "This display name is already taken"
                        }
                    }
                    is Resource.Error -> {
                        _usernameValid.value = false
                        _usernameError.value = resource.message?.takeIf { it.isNotBlank() }
                            ?: "Unable to check display name"
                    }
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    fun updateProfile(
        firstName: String,
        lastName: String,
        nickName: String,
        dob: String,
        gender: String,
        location: String? = null,
        about: String
    ) {
        val formattedDob = formatDobForApi(dob)
        val normalizedGender = gender.trim().lowercase()
        val effectiveLocation = location?.trim()?.ifBlank { null }

        val fName = firstName.trim()
        val lName = lastName.trim()
        val nName = nickName.trim()
        val fullName = listOf(fName, lName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { nName }
        val username = if (nName.isNotBlank()) nName else fName.lowercase().replace(" ", "")

        val request = UpdateProfileRequest(
            fullName = fullName,
            username = username,
            dateOfBirth = formattedDob,
            gender = normalizedGender,
            location = effectiveLocation,
            about = about.trim()
        )

        viewModelScope.launch {
            mainRepository.updateProfile(request).collect { result ->
                _updateProfileState.value = result
            }
        }
    }

    /**
     * Converts UI date (dd/MM/yyyy) into strict backend required format (YYYY-MM-DD).
     */
    private fun formatDobForApi(inputDob: String): String {
        val trimmed = inputDob.trim()
        if (trimmed.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) {
            return trimmed
        }
        if (trimmed.contains("/")) {
            val parts = trimmed.split("/")
            if (parts.size == 3) {
                val day = parts[0].padStart(2, '0')
                val month = parts[1].padStart(2, '0')
                val year = parts[2]
                return "$year-$month-$day"
            }
        }
        return try {
            val sdfIn = SimpleDateFormat("dd/MM/yyyy", Locale.US)
            val date = sdfIn.parse(trimmed)
            if (date != null) {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
            } else {
                trimmed
            }
        } catch (_: Exception) {
            trimmed
        }
    }

    fun resetUpdateProfileState() {
        _updateProfileState.value = null
    }
}
