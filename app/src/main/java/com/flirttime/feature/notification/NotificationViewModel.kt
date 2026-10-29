package com.flirttime.feature.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.feature.notification.NotificationItem
import com.flirttime.feature.notification.NotificationListItem
import com.flirttime.feature.notification.NotificationSection
import com.flirttime.feature.notification.NotificationSectionDto
import com.flirttime.feature.notification.NotificationRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val repository: NotificationRepository
) : ViewModel() {

    private val _notificationsState = MutableStateFlow<List<NotificationListItem>>(emptyList())
    val notificationsState: StateFlow<List<NotificationListItem>> = _notificationsState.asStateFlow()

    private val _unreadCountState = MutableStateFlow(0)
    val unreadCountState: StateFlow<Int> = _unreadCountState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableSharedFlow<String>(replay = 0)
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    // Raw in-memory items mapped by id for optimistic updates
    private val currentRawItems = mutableListOf<NotificationItem>()
    private var nextCursor: String? = null
    var hasMore: Boolean = false
        private set

    init {
        loadNotifications(isRefresh = true)
        // iOS parity: screen open pe mark-all-read call karta hai
        markAllAsRead()
    }

    fun loadNotifications(isRefresh: Boolean = false) {
        if (_isLoading.value) return

        viewModelScope.launch {
            if (isRefresh) {
                _isRefreshing.value = true
                nextCursor = null
                hasMore = false
            } else {
                _isLoading.value = true
            }

            val cursorToUse = if (isRefresh) null else nextCursor

            repository.getNotifications(limit = 20, cursor = cursorToUse).collect { resource ->
                when (resource) {
                    is Resource.Loading -> Unit
                    is Resource.Success -> {
                        val response = resource.data
                        nextCursor = response.nextCursor
                        hasMore = response.hasMore

                        val parsedItems = parseSectionsToItems(response.sections)

                        if (isRefresh) {
                            currentRawItems.clear()
                            currentRawItems.addAll(parsedItems)
                        } else {
                            // Deduplicate when appending paginated items
                            val existingIds = currentRawItems.map { it.id }.toSet()
                            val newUniqueItems = parsedItems.filterNot { it.id in existingIds }
                            currentRawItems.addAll(newUniqueItems)
                        }

                        updateUiList(currentRawItems)
                        _isLoading.value = false
                        _isRefreshing.value = false
                    }
                    is Resource.Error -> {
                        _isLoading.value = false
                        _isRefreshing.value = false
                        _errorMessage.emit(resource.message)
                    }
                }
            }
        }
    }

    fun loadNextPage() {
        if (hasMore && !_isLoading.value && !nextCursor.isNullOrBlank()) {
            loadNotifications(isRefresh = false)
        }
    }

    fun onNotificationClicked(item: NotificationItem) {
        if (!item.isRead) {
            // Optimistic update
            val index = currentRawItems.indexOfFirst { it.id == item.id }
            if (index != -1) {
                currentRawItems[index] = item.copy(isRead = true)
                updateUiList(currentRawItems)
            }
            // Sync with backend
            repository.markAsRead(item.id).launchIn(viewModelScope)
            _unreadCountState.update { maxOf(0, it - 1) }
        }
    }

    fun markAllAsRead() {
        // Optimistic update
        if (currentRawItems.isNotEmpty()) {
            val updated = currentRawItems.map { it.copy(isRead = true) }
            currentRawItems.clear()
            currentRawItems.addAll(updated)
            updateUiList(currentRawItems)
        }
        _unreadCountState.value = 0

        // Sync with backend
        repository.markAllAsRead().launchIn(viewModelScope)
    }

    fun deleteNotification(item: NotificationItem) {
        // Optimistic delete
        val backupList = ArrayList(currentRawItems)
        currentRawItems.removeAll { it.id == item.id }
        updateUiList(currentRawItems)

        viewModelScope.launch {
            repository.deleteNotification(item.id).collect { resource ->
                if (resource is Resource.Error) {
                    // Revert on error
                    currentRawItems.clear()
                    currentRawItems.addAll(backupList)
                    updateUiList(currentRawItems)
                    _errorMessage.emit("Failed to delete: ${resource.message}")
                }
            }
        }
    }

    private fun parseSectionsToItems(sections: List<NotificationSectionDto>): List<NotificationItem> {
        val items = mutableListOf<NotificationItem>()
        for (sec in sections) {
            val sectionEnum = NotificationSection.fromString(sec.title)
            for (dto in sec.items) {
                items.add(dto.toDomain(sectionEnum))
            }
        }
        return items
    }

    private fun updateUiList(rawItems: List<NotificationItem>) {
        val grouped = rawItems.groupBy { it.section }
        val result = mutableListOf<NotificationListItem>()

        listOf(NotificationSection.TODAY, NotificationSection.YESTERDAY, NotificationSection.EARLIER).forEach { section ->
            val sectionItems = grouped[section]
            if (!sectionItems.isNullOrEmpty()) {
                result.add(NotificationListItem.Header(section.displayTitle))
                sectionItems.forEach { item ->
                    result.add(NotificationListItem.Item(item))
                }
            }
        }

        _notificationsState.value = result
    }
}
