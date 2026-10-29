package com.flirttime.feature.main.chat

/**
 * Represents a conversation row in the Chats / Messages screen.
 */
data class ChatItem(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val avatarRes: Int? = null,
    val isPinned: Boolean = false,
    val hasStoryRing: Boolean = false,
    val isVerified: Boolean = false,
    val lastMessage: String? = null,
    val timestamp: String,
    val unreadCount: Int = 0,
    val isSentByMe: Boolean = false,
    val isDeliveredOrRead: Boolean = false
)
