package com.flirttime.feature.main.presentation.chat

object MockChatProvider {

    fun getChats(): List<ChatItem> = listOf(
        ChatItem(
            id = "chat_1",
            name = "Sophia Carter",
            avatarUrl = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=200&h=200&fit=crop",
            isPinned = true,
            hasStoryRing = true,
            isVerified = true,
            lastMessage = "There's a live music night near my place...",
            timestamp = "23 hr ago",
            unreadCount = 0
        ),
        ChatItem(
            id = "chat_2",
            name = "Cindy M",
            avatarUrl = "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=200&h=200&fit=crop",
            isPinned = false,
            hasStoryRing = true,
            isVerified = false,
            lastMessage = null,
            timestamp = "23 hr ago",
            unreadCount = 0
        ),
        ChatItem(
            id = "chat_3",
            name = "Aarav Mehta",
            avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200&h=200&fit=crop",
            isPinned = false,
            hasStoryRing = false,
            isVerified = false,
            lastMessage = "We should plan one together sometime",
            timestamp = "1 day ago",
            unreadCount = 1
        ),
        ChatItem(
            id = "chat_4",
            name = "Emma Wilson",
            avatarUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=200&h=200&fit=crop",
            isPinned = false,
            hasStoryRing = true,
            isVerified = true,
            lastMessage = "Promise 🤞",
            timestamp = "1 day ago",
            unreadCount = 0,
            isSentByMe = true,
            isDeliveredOrRead = true
        ),
        ChatItem(
            id = "chat_5",
            name = "Riya Sharma",
            avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&h=200&fit=crop",
            isPinned = false,
            hasStoryRing = false,
            isVerified = false,
            lastMessage = "Challenge accepted",
            timestamp = "2 days ago",
            unreadCount = 0,
            isSentByMe = true,
            isDeliveredOrRead = true
        ),
        ChatItem(
            id = "chat_6",
            name = "Kabir Singh",
            avatarUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=200&h=200&fit=crop",
            isPinned = false,
            hasStoryRing = false,
            isVerified = false,
            lastMessage = "Count me in 🙌",
            timestamp = "3 days ago",
            unreadCount = 0,
            isSentByMe = true,
            isDeliveredOrRead = true
        )
    )
}
