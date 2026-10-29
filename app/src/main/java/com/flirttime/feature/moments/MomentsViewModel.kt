package com.flirttime.feature.moments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.feature.moments.MomentsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MomentsViewModel(
    private val momentsRepository: MomentsRepository
) : ViewModel() {

    companion object {
        const val PAGE_SIZE = 10
    }

    private val accumulatedMoments = mutableListOf<MomentPost>()

    private val _momentsState = MutableStateFlow<Resource<List<MomentPost>>>(Resource.Loading)
    val momentsState: StateFlow<Resource<List<MomentPost>>> = _momentsState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private var currentPage: Int = 1
    private var totalPages: Int = 1
    private var isFetchingPage: Boolean = false
    private var lastLoadedBatchSize: Int = 0

    val canLoadMore: Boolean
        get() = (currentPage < totalPages) || (lastLoadedBatchSize >= PAGE_SIZE)

    init {
        loadMoments(isRefresh = false)
    }

    fun loadMoments(isRefresh: Boolean = false) {
        if (isFetchingPage) {
            if (isRefresh) {
                _isRefreshing.value = false
            }
            return
        }
        isFetchingPage = true

        if (isRefresh) {
            _isRefreshing.value = true
            currentPage = 1
            totalPages = 1
            lastLoadedBatchSize = 0
        } else if (accumulatedMoments.isEmpty()) {
            currentPage = 1
            totalPages = 1
            lastLoadedBatchSize = 0
            _momentsState.value = Resource.Loading
        }

        viewModelScope.launch {
            try {
                momentsRepository.getMomentsFeed(page = 1, limit = PAGE_SIZE).collect { resource ->
                    when (resource) {
                        is Resource.Success -> {
                            val result = resource.data
                            currentPage = result.page
                            totalPages = result.totalPages
                            lastLoadedBatchSize = result.moments.size
                            accumulatedMoments.clear()
                            accumulatedMoments.addAll(result.moments)
                            _momentsState.value = Resource.Success(accumulatedMoments.toList())
                            isFetchingPage = false
                            _isRefreshing.value = false
                        }
                        is Resource.Error -> {
                            if (accumulatedMoments.isEmpty()) {
                                _momentsState.value = Resource.Error(
                                    message = resource.message,
                                    code = resource.code,
                                    cause = resource.cause,
                                    errorBody = resource.errorBody
                                )
                            } else {
                                // Re-emit existing list so observers are notified
                                _momentsState.value = Resource.Success(accumulatedMoments.toList())
                                _errorMessage.emit(resource.message)
                            }
                            isFetchingPage = false
                            _isRefreshing.value = false
                        }
                        is Resource.Loading -> Unit
                    }
                }
            } catch (e: Exception) {
                isFetchingPage = false
                _isRefreshing.value = false
                _errorMessage.emit(e.localizedMessage ?: "Failed to refresh moments")
            } finally {
                isFetchingPage = false
                _isRefreshing.value = false
            }
        }
    }

    fun loadNextPage() {
        if (isFetchingPage || _isLoadingMore.value || !canLoadMore) return

        isFetchingPage = true
        _isLoadingMore.value = true
        val nextPage = currentPage + 1

        viewModelScope.launch {
            try {
                momentsRepository.getMomentsFeed(page = nextPage, limit = PAGE_SIZE).collect { resource ->
                    when (resource) {
                        is Resource.Success -> {
                            val result = resource.data
                            currentPage = result.page
                            if (result.totalPages > 0) {
                                totalPages = result.totalPages
                            }
                            lastLoadedBatchSize = result.moments.size

                            if (result.moments.isEmpty()) {
                                // Backend has no more data
                                totalPages = currentPage
                                lastLoadedBatchSize = 0
                            }

                            // Append distinct moments to avoid duplicate items
                            val existingIds = accumulatedMoments.map { it.id }.toSet()
                            val newItems = result.moments.filter { it.id !in existingIds }
                            accumulatedMoments.addAll(newItems)

                            _momentsState.value = Resource.Success(accumulatedMoments.toList())
                            _isLoadingMore.value = false
                            isFetchingPage = false
                        }
                        is Resource.Error -> {
                            _isLoadingMore.value = false
                            isFetchingPage = false
                            _errorMessage.emit(resource.message)
                        }
                        is Resource.Loading -> Unit
                    }
                }
            } catch (e: Exception) {
                _isLoadingMore.value = false
                isFetchingPage = false
                _errorMessage.emit(e.localizedMessage ?: "Failed to load more moments")
            } finally {
                _isLoadingMore.value = false
                isFetchingPage = false
            }
        }
    }

    fun toggleLike(momentId: String, isLiked: Boolean) {
        viewModelScope.launch {
            momentsRepository.toggleLike(momentId = momentId, isLiked = isLiked).collect {
                // Background optimistic sync with server
            }
        }
    }

    fun deleteMoment(momentId: String, onResult: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            momentsRepository.deleteMoment(momentId).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        removeMomentFromList(momentId)
                        onResult?.invoke(true, null)
                    }
                    is Resource.Error -> {
                        onResult?.invoke(false, resource.message)
                    }
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    fun removeMomentFromList(momentId: String) {
        accumulatedMoments.removeAll { it.id == momentId }
        _momentsState.value = Resource.Success(accumulatedMoments.toList())
    }
}
