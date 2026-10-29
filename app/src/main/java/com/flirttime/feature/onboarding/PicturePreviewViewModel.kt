package com.flirttime.feature.onboarding

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.feature.auth.AuthUserDto
import com.flirttime.feature.main.MainRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PicturePreviewViewModel(
    private val mainRepository: MainRepository
) : ViewModel() {

    private val _uploadState = MutableStateFlow<Resource<AuthUserDto>?>(null)
    val uploadState: StateFlow<Resource<AuthUserDto>?> = _uploadState.asStateFlow()

    fun uploadProfilePhoto(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            mainRepository.uploadProfilePhoto(context, imageUri).collect { result ->
                _uploadState.value = result
            }
        }
    }

    fun resetUploadState() {
        _uploadState.value = null
    }
}
