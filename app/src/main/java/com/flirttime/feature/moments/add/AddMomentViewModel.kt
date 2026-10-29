package com.flirttime.feature.moments.add

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.core.common.MultipartHelper
import com.flirttime.core.database.SessionManager
import com.flirttime.feature.moments.MomentsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddMomentViewModel(
    private val momentsRepository: MomentsRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _publishState = MutableStateFlow<Resource<Unit>?>(null)
    val publishState: StateFlow<Resource<Unit>?> = _publishState.asStateFlow()

    val nickNameFlow = sessionManager.nickNameFlow
    val firstNameFlow = sessionManager.firstNameFlow
    val profileImageUriFlow = sessionManager.profileImageUriFlow

    fun publishMoment(
        context: Context,
        caption: String?,
        audience: String,
        location: String?,
        imageUris: List<Uri>
    ) {
        if (imageUris.isEmpty()) {
            _publishState.value = Resource.Error("Please select at least 1 image")
            return
        }

        viewModelScope.launch {
            _publishState.value = Resource.Loading

            val parts = withContext(Dispatchers.IO) {
                imageUris.mapNotNull { uri ->
                    MultipartHelper.createPartFromUri(context, uri, "images")
                }
            }

            if (parts.isEmpty()) {
                _publishState.value = Resource.Error("Failed to process selected images. Please try again.")
                return@launch
            }

            momentsRepository.createMoment(
                caption = caption,
                audience = audience,
                location = location,
                imageParts = parts
            ).collect { resource ->
                _publishState.value = resource
            }
        }
    }

    fun resetState() {
        _publishState.value = null
    }
}
