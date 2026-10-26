package com.flirttime.feature.notification.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.feature.notification.data.model.NotificationItem
import com.flirttime.feature.notification.data.model.NotificationListItem
import com.flirttime.feature.notification.data.repository.NotificationRepository
import com.flirttime.feature.notification.data.repository.NotificationRepositoryImpl
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class NotificationViewModel(
    private val repository: NotificationRepository = NotificationRepositoryImpl()
) : ViewModel() {

    val notificationsState: StateFlow<List<NotificationListItem>> = repository.notificationsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onNotificationClicked(item: NotificationItem) {
        repository.markAsRead(item.id)
    }

    fun onMarkAllAsRead() {
        repository.markAllAsRead()
    }
}
