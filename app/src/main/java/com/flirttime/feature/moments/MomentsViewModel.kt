package com.flirttime.feature.moments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.feature.chat.socket.ChatSocketManager
import com.flirttime.feature.moments.socket.LocalEditGuard
import com.flirttime.feature.moments.socket.MomentSocketKind
import com.flirttime.feature.moments.socket.MomentSocketUpdate
import com.flirttime.feature.moments.socket.MomentsSocketService
import com.flirttime.feature.moments.socket.applyCountsTo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MomentsViewModel(
    private val momentsRepository: MomentsRepository,
    private val socketManager: ChatSocketManager,
    private val momentsSocket: MomentsSocketService
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

    private val _socketChange = MutableSharedFlow<SocketListChange>(extraBufferCapacity = 16)
    val socketChange: SharedFlow<SocketListChange> = _socketChange.asSharedFlow()

    private val localEdits = LocalEditGuard()

    private var currentPage: Int = 1
    private var totalPages: Int = 1
    private var isFetchingPage: Boolean = false
    private var lastLoadedBatchSize: Int = 0

    val canLoadMore: Boolean
        get() = (currentPage < totalPages) || (lastLoadedBatchSize >= PAGE_SIZE)

    init {
        socketManager.connect()
        momentsSocket.setup()
        loadMoments(isRefresh = false)
        viewModelScope.launch {
            momentsSocket.updates.collect { applySocket(it) }
        }
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
        markLocalEdit(momentId)
        viewModelScope.launch {
            momentsRepository.toggleLike(momentId = momentId, isLiked = isLiked).collect {
                // Background optimistic sync with server
            }
        }
    }

    fun markLocalEdit(momentId: String) {
        localEdits.mark(momentId)
    }

    private fun applySocket(update: MomentSocketUpdate) {
        val change = when {
            update.kind == MomentSocketKind.Deleted -> {
                val existed = accumulatedMoments.any { it.id == update.momentId }
                if (existed) {
                    accumulatedMoments.removeAll { it.id == update.momentId }
                    _momentsState.value = Resource.Success(accumulatedMoments.toList())
                    SocketListChange.Removed(update.momentId)
                } else {
                    SocketListChange.None
                }
            }
            accumulatedMoments.none { it.id == update.momentId } -> {
                val incoming = update.moment?.takeIf { shouldAccept(it) } ?: return
                val patched = update.applyCountsTo(incoming, localEdits.recentlyEdited(update.momentId))
                accumulatedMoments.add(0, patched)
                _momentsState.value = Resource.Success(accumulatedMoments.toList())
                SocketListChange.Inserted(0)
            }
            else -> {
                val index = accumulatedMoments.indexOfFirst { it.id == update.momentId }
                if (index < 0) return
                accumulatedMoments[index] = update.applyCountsTo(
                    accumulatedMoments[index],
                    localEdits.recentlyEdited(update.momentId)
                )
                SocketListChange.Counts(update.momentId)
            }
        }
        viewModelScope.launch { _socketChange.emit(change) }
    }

    private fun shouldAccept(post: MomentPost): Boolean {
        return post.id.isNotBlank()
    }

    fun deleteMoment(momentId: String, onResult: ((Boolean, String?) -> Unit)? = null) {
        markLocalEdit(momentId)
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

    fun reportMoment(
        momentId: String,
        reason: String,
        description: String? = null,
        onResult: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch {
            momentsRepository.reportMoment(momentId = momentId, reason = reason, description = description).collect { resource ->
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

    fun blockUser(
        userId: String,
        reason: String = "Blocked from Moments",
        onResult: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch {
            momentsRepository.blockUser(userId = userId, reason = reason).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        removeMomentsByAuthor(userId)
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

    fun removeMomentsByAuthor(authorId: String) {
        accumulatedMoments.removeAll { it.userId == authorId }
        _momentsState.value = Resource.Success(accumulatedMoments.toList())
    }

    sealed class SocketListChange {
        data class Counts(val momentId: String) : SocketListChange()
        data class Inserted(val index: Int) : SocketListChange()
        data class Removed(val momentId: String) : SocketListChange()
        data object None : SocketListChange()
    }
}
