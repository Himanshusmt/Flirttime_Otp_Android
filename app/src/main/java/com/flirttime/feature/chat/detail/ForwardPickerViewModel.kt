package com.flirttime.feature.chat.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.feature.chat.ChatRepository
import com.flirttime.feature.chat.model.ForwardRecipientUi
import com.flirttime.feature.chat.model.toUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Recipient search + multi-select state for the forward picker.
 *
 * Mirrors the iOS `ForwardRecipientPickerViewModel`: debounced search, paged loading and a
 * selection set that survives additional result pages.
 */
class ForwardPickerViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    private val _recipients = MutableStateFlow<List<ForwardRecipientUi>>(emptyList())
    val recipients: StateFlow<List<ForwardRecipientUi>> = _recipients.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _loadFailed = MutableStateFlow(false)
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    private var page = 1
    private var hasMore = true
    private var query = ""
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        load(reset = true)
    }

    /** Debounced so typing does not flood the endpoint. */
    fun onSearchChanged(raw: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val trimmed = raw.trim()
            if (trimmed == query) return@launch
            query = trimmed
            load(reset = true)
        }
    }

    fun loadMore() {
        if (!hasMore || _isLoading.value || _isLoadingMore.value) return
        load(reset = false)
    }

    fun toggle(userId: String) {
        _selectedIds.update { current ->
            if (current.contains(userId)) current - userId else current + userId
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    private fun load(reset: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (reset) {
                page = 1
                hasMore = true
                _isLoading.value = true
            } else {
                _isLoadingMore.value = true
            }

            when (val result = repository.getForwardRecipients(
                page = page,
                limit = PAGE_SIZE,
                search = query.ifBlank { null }
            )) {
                is Resource.Success -> {
                    val rows = result.data.recipients.mapNotNull { it.toUi() }
                    _recipients.value = if (reset) {
                        rows.distinctBy { it.userId }
                    } else {
                        (_recipients.value + rows).distinctBy { it.userId }
                    }
                    hasMore = result.data.hasMore && rows.isNotEmpty()
                    if (rows.isNotEmpty()) page += 1
                    _loadFailed.value = false
                }
                is Resource.Error -> {
                    if (reset) _recipients.value = emptyList()
                    _loadFailed.value = true
                }
                Resource.Loading -> Unit
            }

            _isLoading.value = false
            _isLoadingMore.value = false
        }
    }

    companion object {
        private const val PAGE_SIZE = 30
        private const val SEARCH_DEBOUNCE_MS = 320L
    }
}
