package com.flirttime.feature.main.presentation.moments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.feature.moments.domain.repository.MomentsRepository
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

    private val accumulatedMoments = mutableListOf<MomentPost>()

    private val _momentsState = MutableStateFlow<Resource<List<MomentPost>>>(Resource.Loading)
    val momentsState: StateFlow<Resource<List<MomentPost>>> = _momentsState.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private var currentPage: Int = 1
    private var totalPages: Int = 1
    private var isFetchingPage: Boolean = false

    val canLoadMore: Boolean
        get() = currentPage < totalPages

    init {
        loadMoments(isRefresh = false)
    }

    fun loadMoments(isRefresh: Boolean = false) {
        if (isFetchingPage) return
        isFetchingPage = true

        if (isRefresh || accumulatedMoments.isEmpty()) {
            currentPage = 1
            totalPages = 1
            _momentsState.value = Resource.Loading
        }

        viewModelScope.launch {
            momentsRepository.getMomentsFeed(page = 1, limit = 20).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val result = resource.data
                        currentPage = result.page
                        totalPages = result.totalPages
                        accumulatedMoments.clear()
                        accumulatedMoments.addAll(result.moments)
                        _momentsState.value = Resource.Success(accumulatedMoments.toList())
                        isFetchingPage = false
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
                            _errorMessage.emit(resource.message)
                        }
                        isFetchingPage = false
                    }
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    fun loadNextPage() {
        if (isFetchingPage || _isLoadingMore.value || !canLoadMore) return

        isFetchingPage = true
        _isLoadingMore.value = true
        val nextPage = currentPage + 1

        viewModelScope.launch {
            momentsRepository.getMomentsFeed(page = nextPage, limit = 20).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val result = resource.data
                        currentPage = result.page
                        totalPages = result.totalPages

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
        }
    }

    fun toggleLike(momentId: String, isLiked: Boolean) {
        viewModelScope.launch {
            momentsRepository.toggleLike(momentId = momentId, isLiked = isLiked).collect {
                // Background optimistic sync with server
            }
        }
    }
}
