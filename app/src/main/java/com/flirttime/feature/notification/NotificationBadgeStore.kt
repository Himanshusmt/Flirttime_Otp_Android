package com.flirttime.feature.notification

import android.util.Log
import com.flirttime.Resource
import com.flirttime.core.database.SessionManager
import com.flirttime.feature.chat.socket.ChatSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class NotificationBadgeStore(
    private val socketManager: ChatSocketManager,
    private val repository: NotificationRepository,
    private val sessionManager: SessionManager
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    @Volatile
    private var listenersConfigured = false

    fun setup() {
        if (listenersConfigured) return
        listenersConfigured = true
        socketManager.listenRaw(EVENT_COUNT) { args -> applySocketPayload(args) }
        socketManager.connect()
        refresh()
    }

    fun set(unread: Int) {
        _unreadCount.value = maxOf(0, unread)
    }

    fun refresh() {
        if (sessionManager.getCachedToken().isBlank()) {
            set(0)
            return
        }
        scope.launch {
            repository.getUnreadCount().collect { resource ->
                if (resource is Resource.Success) {
                    set(resource.data)
                }
            }
        }
    }

    private fun applySocketPayload(args: Array<Any>) {
        val parsed = parseCount(args)
        if (parsed != null) {
            Log.d(TAG, "notification:count unread=$parsed")
            set(parsed)
        } else {
            Log.d(TAG, "notification:count missing total — bump + REST confirm")
            set(_unreadCount.value + 1)
            refresh()
        }
    }

    private fun parseCount(args: Array<Any>): Int? {
        val first = args.firstOrNull() ?: return null
        when (first) {
            is Number -> return first.toInt()
            is String -> first.toIntOrNull()?.let { return it }
        }
        val root = when (first) {
            is JSONObject -> first
            is org.json.JSONArray -> first.optJSONObject(0)
            is Map<*, *> -> runCatching { JSONObject(first) }.getOrNull()
            else -> null
        } ?: return null
        val body = root.optJSONObject("data") ?: root.optJSONObject("counts") ?: root
        listOf("unreadCount", "unread", "count", "total", "notificationCount", "unread_count").forEach { key ->
            when (val value = body.opt(key) ?: root.opt(key)) {
                is Number -> return value.toInt()
                is String -> value.toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    companion object {
        private const val TAG = "NotificationBadge"
        private const val EVENT_COUNT = "notification:count"
    }
}
