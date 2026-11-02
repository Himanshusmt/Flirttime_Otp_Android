package com.flirttime.feature.moments

import android.content.Context
import android.net.Uri
import com.flirttime.Resource
import com.flirttime.core.common.MultipartHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MomentsUploadStore(
    private val appContext: Context,
    private val momentsRepository: MomentsRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var uploadJob: Job? = null
    private var tickJob: Job? = null
    private var uploadGeneration = 0
    private var displayProgress = 0f
    private var targetProgress = 0f
    private var pendingSuccess = false

    private val _progress = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = _progress.asStateFlow()

    private val _finished = MutableSharedFlow<Resource<Unit>>(extraBufferCapacity = 1)
    val finished: SharedFlow<Resource<Unit>> = _finished.asSharedFlow()

    val isUploading: Boolean
        get() = _progress.value != null

    fun startUpload(
        caption: String?,
        audience: String,
        location: String?,
        imageUris: List<Uri>
    ): Boolean {
        if (isUploading || imageUris.isEmpty()) return false
        val generation = ++uploadGeneration
        displayProgress = 0f
        targetProgress = 0f
        pendingSuccess = false
        _progress.value = 0f
        animateTo(0.05f)

        uploadJob = scope.launch {
            val parts = withContext(Dispatchers.IO) {
                imageUris.take(5).mapNotNull { uri ->
                    MultipartHelper.createPartFromUri(appContext, uri, "images")
                }
            }
            if (generation != uploadGeneration) return@launch
            if (parts.isEmpty()) {
                fail("Couldn't prepare your photos.")
                return@launch
            }
            animateTo(0.75f)
            momentsRepository.createMoment(
                caption = caption,
                audience = audience,
                location = location,
                imageParts = parts
            ).collect { resource ->
                if (generation != uploadGeneration) return@collect
                when (resource) {
                    is Resource.Success -> {
                        pendingSuccess = true
                        animateTo(1f)
                    }
                    is Resource.Error -> fail(resource.message ?: "Unable to post your moment")
                    is Resource.Loading -> Unit
                }
            }
        }
        return true
    }

    private fun animateTo(target: Float) {
        targetProgress = maxOf(targetProgress, target.coerceIn(0f, 1f))
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (true) {
                if (displayProgress < targetProgress) {
                    displayProgress = minOf(displayProgress + 0.01f, targetProgress)
                    _progress.value = displayProgress
                } else if (displayProgress >= 1f && pendingSuccess) {
                    finishSuccess()
                    return@launch
                }
                delay(20)
            }
        }
    }

    private fun finishSuccess() {
        tickJob?.cancel()
        pendingSuccess = false
        _progress.value = null
        _finished.tryEmit(Resource.Success(Unit))
    }

    private fun fail(message: String) {
        tickJob?.cancel()
        uploadJob?.cancel()
        pendingSuccess = false
        _progress.value = null
        _finished.tryEmit(Resource.Error(message))
    }
}
