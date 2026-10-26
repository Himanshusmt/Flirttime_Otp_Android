package com.flirttime.feature.notification.data.model

/**
 * Supported Notification Types for FlirtTime.
 */
enum class NotificationType {
    MATCH,
    LIKE,
    COMPLIMENT,
    SUPER_LIKE,
    MESSAGE,
    PROFILE_VERIFIED,
    PHOTO_NOT_APPROVED
}

/**
 * Temporal Grouping Sections.
 */
enum class NotificationSection(val displayTitle: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    EARLIER("Earlier")
}

/**
 * Individual Notification Entity.
 */
data class NotificationItem(
    val id: String,
    val type: NotificationType,
    val title: String,
    val description: String,
    val timestamp: String,
    val avatarUrl: String? = null,
    val isRead: Boolean = false,
    val section: NotificationSection
)

/**
 * Sealed UI Model for Sectioned List Rendering with DiffUtil.
 */
sealed class NotificationListItem {
    data class Header(val title: String) : NotificationListItem()
    data class Item(val notification: NotificationItem) : NotificationListItem()
}
