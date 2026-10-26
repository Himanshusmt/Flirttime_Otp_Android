package com.flirttime.feature.profile.presentation.gesture

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.feature.auth.data.model.AuthUserDto
import com.flirttime.feature.profile.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GestureVerificationViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uploadState = MutableStateFlow<Resource<AuthUserDto>?>(null)
    val uploadState: StateFlow<Resource<AuthUserDto>?> = _uploadState.asStateFlow()

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
