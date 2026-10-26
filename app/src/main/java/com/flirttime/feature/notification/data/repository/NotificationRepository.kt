package com.flirttime.feature.notification.data.repository

import com.flirttime.feature.notification.data.model.NotificationItem
import com.flirttime.feature.notification.data.model.NotificationListItem
import com.flirttime.feature.notification.data.model.NotificationSection
import com.flirttime.feature.notification.data.model.NotificationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface NotificationRepository {
    val notificationsFlow: StateFlow<List<NotificationListItem>>
    fun markAsRead(id: String)
    fun markAllAsRead()
}

class NotificationRepositoryImpl : NotificationRepository {

    private val _notifications = MutableStateFlow(getInitialMockNotifications())
    private val _uiItemsFlow = MutableStateFlow<List<NotificationListItem>>(emptyList())
    override val notificationsFlow: StateFlow<List<NotificationListItem>> = _uiItemsFlow.asStateFlow()

    init {
        updateUiList(_notifications.value)
    }

    override fun markAsRead(id: String) {
        _notifications.update { currentList ->
            currentList.map { item ->
                if (item.id == id) item.copy(isRead = true) else item
            }
        }
        updateUiList(_notifications.value)
    }

    override fun markAllAsRead() {
        _notifications.update { currentList ->
            currentList.map { it.copy(isRead = true) }
        }
        updateUiList(_notifications.value)
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

        _uiItemsFlow.value = result
    }

    companion object {
        fun getInitialMockNotifications(): List<NotificationItem> {
            return listOf(
                NotificationItem(
                    id = "notif_1",
                    type = NotificationType.MATCH,
                    title = "It's a match!",
                    description = "You and Cindy liked each other. Say hi!",
                    timestamp = "12m",
                    avatarUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=500&fit=crop",
                    isRead = false,
                    section = NotificationSection.TODAY
                ),
                NotificationItem(
                    id = "notif_2",
                    type = NotificationType.LIKE,
                    title = "Sara liked you",
                    description = "Check out her profile and like back to match.",
                    timestamp = "1h",
                    avatarUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=500&fit=crop",
                    isRead = false,
                    section = NotificationSection.TODAY
                ),
                NotificationItem(
                    id = "notif_3",
                    type = NotificationType.COMPLIMENT,
                    title = "Meera sent a compliment",
                    description = "\"Your smile in the second picture is everything!\"",
                    timestamp = "5h",
                    avatarUrl = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=500&fit=crop",
                    isRead = false,
                    section = NotificationSection.TODAY
                ),
                NotificationItem(
                    id = "notif_4",
                    type = NotificationType.SUPER_LIKE,
                    title = "Nisha super liked you",
                    description = "You stood out! See who's into you.",
                    timestamp = "Yesterday",
                    avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500&fit=crop",
                    isRead = false,
                    section = NotificationSection.YESTERDAY
                ),
                NotificationItem(
                    id = "notif_5",
                    type = NotificationType.MESSAGE,
                    title = "Riya",
                    description = "Sent you a new message.",
                    timestamp = "Yesterday",
                    avatarUrl = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=500&fit=crop",
                    isRead = false,
                    section = NotificationSection.YESTERDAY
                ),
                NotificationItem(
                    id = "notif_6",
                    type = NotificationType.PROFILE_VERIFIED,
                    title = "Profile verified",
                    description = "Your profile photo has been verified. You now have the blue tick!",
                    timestamp = "3d",
                    avatarUrl = null,
                    isRead = true,
                    section = NotificationSection.EARLIER
                ),
                NotificationItem(
                    id = "notif_7",
                    type = NotificationType.PHOTO_NOT_APPROVED,
                    title = "Photo not approved",
                    description = "One of your photos didn't meet our guidelines. Tap to replace it.",
                    timestamp = "4d",
                    avatarUrl = null,
                    isRead = false,
                    section = NotificationSection.EARLIER
                )
            )
        }
    }
}
