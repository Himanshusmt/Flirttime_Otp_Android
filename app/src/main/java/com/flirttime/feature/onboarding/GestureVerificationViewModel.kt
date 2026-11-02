package com.flirttime.feature.onboarding

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.feature.profile.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GestureVerificationViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uploadState = MutableStateFlow<Resource<GestureVerificationDto>?>(null)
    val uploadState: StateFlow<Resource<GestureVerificationDto>?> = _uploadState.asStateFlow()

    fun uploadGesturePhoto(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            userRepository.uploadGesturePhoto(context, imageUri).collect { result ->
                _uploadState.value = result
            }
        }
    }

    fun resetUploadState() {
        _uploadState.value = null
    }
}
