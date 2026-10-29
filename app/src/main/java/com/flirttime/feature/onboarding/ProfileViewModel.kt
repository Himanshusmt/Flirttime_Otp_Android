package com.flirttime.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.feature.auth.AuthUserDto
import com.flirttime.feature.profile.UpdateProfileRequest
import com.flirttime.feature.main.MainRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class ProfileViewModel(
    private val mainRepository: MainRepository
) : ViewModel() {

    private val _updateProfileState = MutableStateFlow<Resource<AuthUserDto>?>(null)
    val updateProfileState: StateFlow<Resource<AuthUserDto>?> = _updateProfileState.asStateFlow()

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
        val fullName = if (fName.isNotBlank() && lName.isNotBlank()) "$fName $lName" else fName.ifBlank { nName }
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
        // 1. Already in YYYY-MM-DD format
        if (trimmed.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) {
            return trimmed
        }
        // 2. Format: dd/MM/yyyy or d/M/yyyy
        if (trimmed.contains("/")) {
            val parts = trimmed.split("/")
            if (parts.size == 3) {
                val day = parts[0].padStart(2, '0')
                val month = parts[1].padStart(2, '0')
                val year = parts[2]
                return "$year-$month-$day"
            }
        }
        // 3. Fallback: Parse via SimpleDateFormat
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
