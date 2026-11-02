package com.flirttime.feature.chat.socket

import android.util.Log
import com.flirttime.core.common.NetworkConnectivityObserver
import com.flirttime.core.database.SessionManager
import com.flirttime.feature.chat.model.ChatMessageDto
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

data class ReadReceiptEvent(
    val conversationId: String,
    val messageId: String?,
    val userId: String?,
    val status: String?
)

data class TypingEvent(
    val conversationId: String,
    val userId: String?,
    val senderName: String?,
    val isTyping: Boolean
)

data class ConversationChangeEvent(
    val conversationId: String,
    val reason: String
)

/** Thread-level preferences pushed by `chat:conversation:settings:updated` / `conversation:pin-updated`. */
data class ConversationSettingsEvent(
    val conversationId: String,
    val isMuted: Boolean?,
    val isPinned: Boolean?,
    val isArchived: Boolean?,
    val isBlocked: Boolean?
)

/** `conversation:deleted` / `conversation:delete:ack` — the thread disappeared from the inbox. */
data class ConversationRemovedEvent(
    val conversationId: String,
    val forEveryone: Boolean
)

/**
 * `chat:delivery_receipt` / `chat:message_delivered` fan-out: the peer device accepted
 * one or more messages, so the sender upgrades from a single to a double grey tick.
 */
data class DeliveryReceiptEvent(
    val conversationId: String,
    val messageIds: List<String>
)

/** `conversation:unread_updated` — per-thread unread badge shared with every device. */
data class ConversationUnreadEvent(
    val conversationId: String,
    val unreadCount: Int
)

/** `chat:message_forwarded` — threads that received the forwarded copies. */
data class MessagesForwardedEvent(
    val conversationIds: List<String>,
    val messageIds: List<String>
)

/** `chat:member:added|removed|role:updated` and `chat:group:updated` — roster/metadata changes. */
data class GroupChangeEvent(
    val event: String,
    val conversationId: String?,
    val userIds: List<String>
)

/** `presence:update` — one peer's online flag plus the last-seen timestamp when shared. */
data class UserPresenceEvent(
    val userId: String,
    val isOnline: Boolean,
    val lastSeenAt: String?
)

/** `chat:message_edited` fan-out (also arrives as `chat:message:edited`). */
data class MessageEditedEvent(
    val conversationId: String?,
    val messageId: String,
    val content: String,
    val isEdited: Boolean,
    val message: ChatMessageDto?
)

/** `chat:message_deleted` fan-out — `deleteType` is `me` or `everyone`. */
data class MessageDeletedEvent(
    val conversationId: String?,
    val messageIds: List<String>,
    val deleteType: String?
)

/** `message:reaction:updated` fan-out. */
data class MessageReactionEvent(
    val conversationId: String?,
    val messageId: String,
    val reactions: Any?,
    val emoji: String?
)

/** `chat:message:pin:updated` / `chat:message:pin:expired` fan-out. */
data class MessagePinEvent(
    val conversationId: String?,
    val messageId: String,
    val isPinned: Boolean
)

/**
 * Realtime transport state shown in the inbox / thread banners.
 *
 * [OFFLINE] means the device has no validated network; [CONNECTING] means the network is up
 * but the socket handshake has not completed (first connect, reconnect or after an error).
 */
enum class ChatConnectionState { CONNECTED, CONNECTING, OFFLINE }

/**
 * Single Socket.IO connection shared by the whole chat feature.
 *
 * The FlirtTime backend exposes Socket.IO on the API host at `/socket.io` and expects the
 * JWT in the handshake `auth` payload (`{ token }`), exactly like the iOS client. Event
 * names and payload shapes mirror `ChatSocketManager.swift`.
 */
class ChatSocketManager(
    private val sessionManager: SessionManager,
    private val connectivityObserver: NetworkConnectivityObserver
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    private var socket: Socket? = null
    private var isConnecting = false
    private val joinedRooms: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Threads currently on screen — local mirror of `EVENT_VIEWING` for notifications. */
    private val viewingConversations: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /** Network + socket state, exactly one source of truth for the reconnect banner. */
    private val _connectionState = MutableStateFlow(ChatConnectionState.CONNECTING)
    val connectionState: StateFlow<ChatConnectionState> = _connectionState.asStateFlow()

    /** Emits once every time the socket (re)connects so callers can refresh / retry. */
    private val _reconnected = MutableSharedFlow<Unit>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val reconnected: SharedFlow<Unit> = _reconnected.asSharedFlow()

    private var isNetworkAvailable = true

    private val _incomingMessages = MutableSharedFlow<ChatMessageDto>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incomingMessages: SharedFlow<ChatMessageDto> = _incomingMessages.asSharedFlow()

    private val _readReceipts = MutableSharedFlow<ReadReceiptEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val readReceipts: SharedFlow<ReadReceiptEvent> = _readReceipts.asSharedFlow()

    private val _typingEvents = MutableSharedFlow<TypingEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val typingEvents: SharedFlow<TypingEvent> = _typingEvents.asSharedFlow()

    private val _conversationChanges = MutableSharedFlow<ConversationChangeEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val conversationChanges: SharedFlow<ConversationChangeEvent> =
        _conversationChanges.asSharedFlow()

    private val _editedMessages = MutableSharedFlow<MessageEditedEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val editedMessages: SharedFlow<MessageEditedEvent> = _editedMessages.asSharedFlow()

    private val _deletedMessages = MutableSharedFlow<MessageDeletedEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val deletedMessages: SharedFlow<MessageDeletedEvent> = _deletedMessages.asSharedFlow()

    private val _reactionUpdates = MutableSharedFlow<MessageReactionEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val reactionUpdates: SharedFlow<MessageReactionEvent> = _reactionUpdates.asSharedFlow()

    private val _pinUpdates = MutableSharedFlow<MessagePinEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val pinUpdates: SharedFlow<MessagePinEvent> = _pinUpdates.asSharedFlow()

    private val _conversationSettings = MutableSharedFlow<ConversationSettingsEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val conversationSettings: SharedFlow<ConversationSettingsEvent> =
        _conversationSettings.asSharedFlow()

    private val _conversationRemoved = MutableSharedFlow<ConversationRemovedEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val conversationRemoved: SharedFlow<ConversationRemovedEvent> =
        _conversationRemoved.asSharedFlow()

    private val _deliveryReceipts = MutableSharedFlow<DeliveryReceiptEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val deliveryReceipts: SharedFlow<DeliveryReceiptEvent> = _deliveryReceipts.asSharedFlow()

    private val _unreadUpdates = MutableSharedFlow<ConversationUnreadEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val unreadUpdates: SharedFlow<ConversationUnreadEvent> = _unreadUpdates.asSharedFlow()

    private val _forwardedMessages = MutableSharedFlow<MessagesForwardedEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val forwardedMessages: SharedFlow<MessagesForwardedEvent> =
        _forwardedMessages.asSharedFlow()

    private val _groupChanges = MutableSharedFlow<GroupChangeEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val groupChanges: SharedFlow<GroupChangeEvent> = _groupChanges.asSharedFlow()

    private val _presenceUpdates = MutableSharedFlow<UserPresenceEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val presenceUpdates: SharedFlow<UserPresenceEvent> = _presenceUpdates.asSharedFlow()

    init {
        // iOS `pathMonitor`: as soon as the network is back, rebuild a dead socket.
        scope.launch {
            connectivityObserver.isConnected.collect { online ->
                isNetworkAvailable = online
                if (!online) {
                    _connectionState.value = ChatConnectionState.OFFLINE
                } else {
                    publishConnectionState()
                    val current = socket
                    when {
                        current == null -> connect()
                        !current.connected() -> forceReconnect()
                    }
                }
            }
        }
    }

    /** Idempotent — safe to call from every screen that needs realtime updates. */
    fun connect() {
        if (socket != null || isConnecting) return
        isConnecting = true
        scope.launch {
            try {
                val token = sessionManager.getToken()
                if (token.isBlank()) {
                    isConnecting = false
                    return@launch
                }
                openSocket(token)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to open chat socket", e)
                isConnecting = false
            }
        }
    }

    private fun openSocket(token: String) {
        val options = IO.Options().apply {
            auth = mapOf("token" to token)
            path = SOCKET_PATH
            reconnection = true
            forceNew = true
            // Polling first, then upgrade — the production host sits behind Apache/nginx.
            transports = arrayOf("polling", "websocket")
        }
        val created = IO.socket(URI.create(SOCKET_URL), options)
        socket = created
        registerListeners(created)
        created.connect()
        isConnecting = false
    }

    private fun registerListeners(socket: Socket) {
        socket.on(Socket.EVENT_CONNECT) { _ ->
            _isConnected.value = true
            publishConnectionState()
            // Re-join rooms after a reconnect so no fan-out is lost.
            joinedRooms.toList().forEach { emitConversationEvent(EVENT_JOIN_CONVERSATION, it) }
            emitDeliveredSync()
            _reconnected.tryEmit(Unit)
        }
        socket.on(Socket.EVENT_DISCONNECT) { _ ->
            _isConnected.value = false
            publishConnectionState()
        }
        socket.on(Socket.EVENT_CONNECT_ERROR) { args ->
            _isConnected.value = false
            publishConnectionState()
            Log.w(TAG, "Chat socket connect error: ${args.firstOrNull()}")
        }

        socket.on(EVENT_MESSAGE_RECEIVED) { args ->
            parseMessage(args)?.let { dto ->
                _incomingMessages.tryEmit(dto)
                acknowledgeDelivery(dto)
            }
        }
        socket.on(EVENT_SYSTEM_MESSAGE) { args ->
            // Screenshot notices and group notices arrive as a system-typed message.
            parseMessage(args)?.let { _incomingMessages.tryEmit(it) }
        }
        socket.on(EVENT_MESSAGE_SENT) { args ->
            parseMessage(args)?.let { _incomingMessages.tryEmit(it) }
        }

        EVENT_READ_RECEIPTS.forEach { event ->
            socket.on(event) { args ->
                parseReadReceipt(args.firstOrNull())?.let { _readReceipts.tryEmit(it) }
            }
        }

        socket.on(EVENT_TYPING) { args ->
            parseTyping(args.firstOrNull())?.let { _typingEvents.tryEmit(it) }
        }

        EVENT_CONVERSATION_CHANGES.forEach { event ->
            socket.on(event) { args ->
                parseConversationChange(args.firstOrNull())?.let {
                    _conversationChanges.tryEmit(it)
                }
            }
        }

        EVENT_MESSAGE_EDITS.forEach { event ->
            socket.on(event) { args ->
                parseEdit(args.firstOrNull())?.let { _editedMessages.tryEmit(it) }
            }
        }
        EVENT_MESSAGE_DELETES.forEach { event ->
            socket.on(event) { args ->
                parseDelete(args.firstOrNull())?.let { _deletedMessages.tryEmit(it) }
            }
        }
        EVENT_MESSAGE_REACTIONS.forEach { event ->
            socket.on(event) { args ->
                parseReaction(args.firstOrNull())?.let { _reactionUpdates.tryEmit(it) }
            }
        }
        EVENT_MESSAGE_PINS.forEach { event ->
            socket.on(event) { args ->
                parsePin(args.firstOrNull(), forceUnpin = event == EVENT_PIN_EXPIRED)?.let {
                    _pinUpdates.tryEmit(it)
                }
            }
        }

        EVENT_CONVERSATION_SETTINGS.forEach { event ->
            socket.on(event) { args ->
                val pinnedOnly = event == EVENT_PIN_CONVERSATION_UPDATED
                parseConversationSettings(args.firstOrNull(), pinnedOnly)?.let {
                    _conversationSettings.tryEmit(it)
                }
            }
        }

        EVENT_CONVERSATION_REMOVED.forEach { event ->
            socket.on(event) { args ->
                parseConversationRemoved(args.firstOrNull())?.let {
                    _conversationRemoved.tryEmit(it)
                }
            }
        }

        EVENT_DELIVERY_RECEIPTS.forEach { event ->
            socket.on(event) { args ->
                parseDeliveryReceipt(args.firstOrNull())?.let { _deliveryReceipts.tryEmit(it) }
            }
        }

        socket.on(EVENT_UNREAD_UPDATED) { args ->
            parseUnread(args.firstOrNull())?.let { _unreadUpdates.tryEmit(it) }
        }

        socket.on(EVENT_FORWARDED_MESSAGE) { args ->
            parseForwarded(args.firstOrNull())?.let { _forwardedMessages.tryEmit(it) }
        }

        EVENT_GROUP_CHANGES.forEach { event ->
            socket.on(event) { args ->
                parseGroupChange(args.firstOrNull(), event)?.let { _groupChanges.tryEmit(it) }
            }
        }

        socket.on(EVENT_PRESENCE_UPDATE) { args ->
            parsePresence(args.firstOrNull()).forEach { _presenceUpdates.tryEmit(it) }
        }
    }

    /**
     * iOS `ChatDetailViewModel+Loading`: every peer message reaching this device is acked
     * with `chat:message_delivered`, which is what turns the sender's ticks double-grey.
     */
    private fun acknowledgeDelivery(dto: ChatMessageDto) {
        val conversationId = dto.conversationId
        val messageId = dto.id
        if (conversationId.isNullOrBlank() || messageId.isNullOrBlank()) return
        if ((dto.messageType ?: dto.type)?.equals(TYPE_SYSTEM, ignoreCase = true) == true) return
        scope.launch {
            val senderId = dto.sender?.id ?: dto.senderId
            if (senderId.isNullOrBlank()) return@launch
            if (senderId == sessionManager.getUserId()) return@launch
            emitMessageDelivered(conversationId, messageId)
        }
    }

    fun joinConversation(conversationId: String) {
        if (conversationId.isBlank()) return
        if (!joinedRooms.add(conversationId)) return
        emitConversationEvent(EVENT_JOIN_CONVERSATION, conversationId)
    }

    fun leaveConversation(conversationId: String) {
        if (conversationId.isBlank()) return
        joinedRooms.remove(conversationId)
        emitConversationEvent(EVENT_LEAVE_CONVERSATION, conversationId)
    }

    /** Tells the backend the thread is open so it can suppress push notifications. */
    fun setConversationViewing(conversationId: String, active: Boolean) {
        if (conversationId.isBlank()) return
        if (active) viewingConversations.add(conversationId) else viewingConversations.remove(conversationId)
        val payload = JSONObject()
            .put("conversationId", conversationId)
            .put("active", active)
        socket?.emit(EVENT_VIEWING, payload)
    }

    /** True while a thread screen owns this conversation — suppresses its notifications. */
    fun isViewingConversation(conversationId: String): Boolean =
        viewingConversations.contains(conversationId)

    fun emitMarkRead(conversationId: String, lastReadMessageId: String?) {
        if (conversationId.isBlank()) return
        val payload = JSONObject().put("conversationId", conversationId)
        if (!lastReadMessageId.isNullOrBlank()) {
            payload.put("lastReadMessageId", lastReadMessageId)
            payload.put("messageId", lastReadMessageId)
        }
        socket?.emit(EVENT_MARK_READ, payload)
    }

    fun emitTyping(conversationId: String, userId: String) {
        emitTypingState(conversationId, userId, isTyping = true)
    }

    fun emitStoppedTyping(conversationId: String, userId: String) {
        emitTypingState(conversationId, userId, isTyping = false)
    }

    private fun emitTypingState(conversationId: String, userId: String, isTyping: Boolean) {
        if (conversationId.isBlank() || userId.isBlank()) return
        val payload = JSONObject()
            .put("conversationId", conversationId)
            .put("userId", userId)
            .put("isTyping", isTyping)
        socket?.emit(EVENT_TYPING, payload)
    }

    fun emitEditMessage(conversationId: String, messageId: String, content: String) {
        if (conversationId.isBlank() || messageId.isBlank()) return
        socket?.emit(
            EVENT_EDIT_MESSAGE,
            JSONObject()
                .put("conversationId", conversationId)
                .put("messageId", messageId)
                .put("content", content)
        )
    }

    fun emitDeleteMessages(conversationId: String, messageIds: List<String>, deleteType: String) {
        if (conversationId.isBlank() || messageIds.isEmpty()) return
        socket?.emit(
            EVENT_DELETE_MESSAGE,
            JSONObject()
                .put("conversationId", conversationId)
                .put("messageIds", org.json.JSONArray(messageIds))
                .put("deleteType", deleteType)
        )
    }

    fun emitReaction(conversationId: String, messageId: String, emoji: String) {
        if (conversationId.isBlank() || messageId.isBlank() || emoji.isBlank()) return
        socket?.emit(
            EVENT_REACT_MESSAGE,
            JSONObject()
                .put("conversationId", conversationId)
                .put("messageId", messageId)
                .put("emoji", emoji)
        )
    }

    fun emitPinMessage(
        conversationId: String,
        messageId: String,
        isPinned: Boolean,
        pinDuration: String?
    ) {
        if (conversationId.isBlank() || messageId.isBlank()) return
        val payload = JSONObject()
            .put("conversationId", conversationId)
            .put("messageId", messageId)
            .put("isPinned", isPinned)
        if (isPinned && !pinDuration.isNullOrBlank()) payload.put("pinDuration", pinDuration)
        socket?.emit(EVENT_PIN_MESSAGE, payload)
    }

    fun emitForwardMessage(conversationId: String, messageIds: List<String>, recipientIds: List<String>) {
        if (messageIds.isEmpty() || recipientIds.isEmpty()) return
        socket?.emit(
            EVENT_FORWARD_MESSAGE,
            JSONObject()
                .put("conversationId", conversationId)
                .put("messageIds", org.json.JSONArray(messageIds))
                .put("recipientIds", org.json.JSONArray(recipientIds))
        )
    }

    /** Fan-out for mute / pin / archive / block so other devices update instantly. */
    fun emitConversationSettings(
        conversationId: String,
        isMuted: Boolean? = null,
        isPinned: Boolean? = null,
        isArchived: Boolean? = null,
        isBlocked: Boolean? = null
    ) {
        if (conversationId.isBlank()) return
        val payload = JSONObject().put("conversationId", conversationId)
        isMuted?.let { payload.put("isMuted", it) }
        isPinned?.let { payload.put("isPinned", it) }
        isArchived?.let { payload.put("isArchived", it) }
        isBlocked?.let { payload.put("isBlocked", it) }
        socket?.emit(EVENT_SETTINGS_UPDATE, payload)
    }

    fun emitArchiveConversation(conversationId: String, isArchived: Boolean) {
        if (conversationId.isBlank()) return
        socket?.emit(
            EVENT_ARCHIVE_CONVERSATION,
            JSONObject()
                .put("conversationId", conversationId)
                .put("isArchived", isArchived)
        )
    }

    fun emitDeleteConversation(conversationId: String, deleteType: String = "me") {
        if (conversationId.isBlank()) return
        socket?.emit(
            EVENT_DELETE_CONVERSATION,
            JSONObject()
                .put("conversationId", conversationId)
                .put("deleteType", deleteType)
        )
    }

    /** Delivered tick — `chat:message_delivered` `{ conversationId, messageIds, messageId }`. */
    fun emitMessageDelivered(conversationId: String, messageId: String) {
        if (conversationId.isBlank() || messageId.isBlank()) return
        socket?.emit(
            EVENT_MESSAGE_DELIVERED,
            JSONObject()
                .put("conversationId", conversationId)
                .put("messageIds", org.json.JSONArray(listOf(messageId)))
                .put("messageId", messageId)
        )
    }

    /** iOS `syncInboxDelivered`: bulk ack of undelivered inbox messages after a (re)connect. */
    fun emitDeliveredSync() {
        socket?.emit(EVENT_DELIVERED_SYNC, JSONObject())
    }

    /** `presence:set` — this device's own online/away state for one thread. */
    fun emitPresence(status: String, conversationId: String, userId: String) {
        if (userId.isBlank()) return
        socket?.emit(
            EVENT_PRESENCE_SET,
            JSONObject()
                .put("status", status)
                .put("conversationId", conversationId)
                .put("conversation_id", conversationId)
                .put("userId", userId)
                .put("user_id", userId)
                .put("user", userId)
        )
    }

    /** `presence:get` — subscribe to the online state of the peers on screen. */
    fun emitSubscribePresence(userIds: List<String>) {
        val ids = userIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return
        socket?.emit(EVENT_PRESENCE_GET, JSONObject().put("userIds", org.json.JSONArray(ids)))
    }

    /** `chat:screenshot` — tells the peer (via a server system message) that a capture happened. */
    fun emitScreenshot(conversationId: String) {
        if (conversationId.isBlank()) return
        socket?.emit(EVENT_SCREENSHOT, JSONObject().put("conversationId", conversationId))
    }

    private fun emitConversationEvent(event: String, conversationId: String) {
        val live = socket ?: return
        if (!live.connected()) return
        live.emit(event, conversationId)
    }

    fun disconnect() {
        socket?.off()
        socket?.disconnect()
        socket = null
        joinedRooms.clear()
        isConnecting = false
        _isConnected.value = false
        publishConnectionState()
    }

    /**
     * Rebuilds a half-open session (iOS `forceReconnect`): the old socket is torn down so
     * the next handshake is fresh, then [connect] runs again.
     */
    fun forceReconnect() {
        scope.launch {
            runCatching {
                socket?.off()
                socket?.disconnect()
            }
            socket = null
            isConnecting = false
            _isConnected.value = false
            publishConnectionState()
            connect()
        }
    }

    private fun publishConnectionState() {
        _connectionState.value = when {
            _isConnected.value -> ChatConnectionState.CONNECTED
            !isNetworkAvailable -> ChatConnectionState.OFFLINE
            else -> ChatConnectionState.CONNECTING
        }
    }

    private fun parseMessage(args: Array<out Any?>): ChatMessageDto? {
        val payload = toJsonObject(args.firstOrNull()) ?: return null
        val messageObject = extractMessageObject(payload) ?: return null
        return try {
            gson.fromJson(messageObject.toString(), ChatMessageDto::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to parse socket message payload", e)
            null
        }
    }

    /** Socket payloads arrive as the message itself or wrapped in `message` / `data`. */
    private fun extractMessageObject(payload: JSONObject): JSONObject? {
        for (key in listOf("message", "data", "result")) {
            val nested = payload.optJSONObject(key)
            if (nested != null && (nested.has("id") || nested.has("conversationId"))) {
                return nested
            }
            val nestedArray = payload.optJSONArray(key)
            if (nestedArray != null && nestedArray.length() > 0) {
                nestedArray.optJSONObject(0)?.let { return it }
            }
        }
        return payload.takeIf { it.has("id") || it.has("conversationId") }
    }

    private fun parseReadReceipt(raw: Any?): ReadReceiptEvent? {
        val payload = toJsonObject(raw) ?: return null
        val body = payload.optJSONObject("data") ?: payload
        val conversationId = body.optString("conversationId")
        if (conversationId.isBlank()) return null
        return ReadReceiptEvent(
            conversationId = conversationId,
            messageId = body.optString("messageId").takeIf { it.isNotBlank() }
                ?: body.optString("lastReadMessageId").takeIf { it.isNotBlank() },
            userId = body.optString("userId").takeIf { it.isNotBlank() },
            status = body.optString("status").takeIf { it.isNotBlank() } ?: "read"
        )
    }

    private fun parseTyping(raw: Any?): TypingEvent? {
        val payload = toJsonObject(raw) ?: return null
        val body = payload.optJSONObject("data") ?: payload
        val conversationId = body.optString("conversationId")
        if (conversationId.isBlank()) return null
        return TypingEvent(
            conversationId = conversationId,
            userId = body.optString("userId").takeIf { it.isNotBlank() },
            senderName = body.optString("senderName").takeIf { it.isNotBlank() }
                ?: body.optString("userName").takeIf { it.isNotBlank() },
            isTyping = body.optBoolean("isTyping", true)
        )
    }

    private fun parseConversationChange(raw: Any?): ConversationChangeEvent? {
        val payload = toJsonObject(raw) ?: return null
        val body = payload.optJSONObject("data") ?: payload
        val conversationId = body.optString("conversationId")
            .takeIf { it.isNotBlank() }
            ?: body.optJSONObject("conversation")?.optString("id").orEmpty()
        if (conversationId.isBlank()) return null
        return ConversationChangeEvent(conversationId = conversationId, reason = "update")
    }

    /**
     * Reads the thread preferences from any of the shapes the backend ships:
     * `{conversationId, isMuted}`, `{data:{settings}}` or `{conversation:{id, settings}}`.
     */
    private fun parseConversationSettings(
        raw: Any?,
        pinnedOnly: Boolean
    ): ConversationSettingsEvent? {
        val payload = toJsonObject(raw) ?: return null
        val body = payload.optJSONObject("data") ?: payload
        val conversation = body.optJSONObject("conversation")
        val settings = body.optJSONObject("settings") ?: conversation?.optJSONObject("settings")
        val conversationId = body.optString("conversationId")
            .takeIf { it.isNotBlank() }
            ?: conversation?.optString("id")?.takeIf { it.isNotBlank() }
            ?: body.optString("id").takeIf { it.isNotBlank() }
            ?: return null

        fun read(vararg aliases: String): Boolean? {
            aliases.forEach { key ->
                if (settings?.has(key) == true) return settings.optBoolean(key)
                if (body.has(key)) return body.optBoolean(key)
            }
            return null
        }

        val pinned = read("isPinned", "pinned")
        if (pinnedOnly && pinned == null) return null
        return ConversationSettingsEvent(
            conversationId = conversationId,
            isMuted = if (pinnedOnly) null else read("isMuted", "muted"),
            isPinned = pinned,
            isArchived = if (pinnedOnly) null else read("isArchived", "archived"),
            isBlocked = if (pinnedOnly) null else read("isBlocked", "blocked")
        )
    }

    private fun parseConversationRemoved(raw: Any?): ConversationRemovedEvent? {
        val payload = toJsonObject(raw) ?: return null
        val body = payload.optJSONObject("data") ?: payload
        val conversationId = body.optString("conversationId")
            .takeIf { it.isNotBlank() }
            ?: body.optJSONObject("conversation")?.optString("id")?.takeIf { it.isNotBlank() }
            ?: return null
        val scope = body.optString("deleteType").takeIf { it.isNotBlank() }
            ?: body.optString("scope").takeIf { it.isNotBlank() }
        return ConversationRemovedEvent(
            conversationId = conversationId,
            forEveryone = scope.equals("everyone", ignoreCase = true)
        )
    }

    private fun parseDeliveryReceipt(raw: Any?): DeliveryReceiptEvent? {
        val payload = toJsonObject(raw) ?: return null
        val body = payload.optJSONObject("data") ?: payload
        val conversationId = body.optString("conversationId").takeIf { it.isNotBlank() }
            ?: body.optString("conversation_id").takeIf { it.isNotBlank() }
            ?: return null
        val ids = mutableListOf<String>()
        body.optJSONArray("messageIds")?.let { array ->
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(ids::add)
            }
        }
        body.optString("messageId").takeIf { it.isNotBlank() }?.let(ids::add)
        body.optString("lastReadMessageId").takeIf { it.isNotBlank() }?.let(ids::add)
        if (ids.isEmpty()) return null
        return DeliveryReceiptEvent(conversationId, ids.distinct())
    }

    /** `conversation:unread_updated` — the count arrives as a number or a numeric string. */
    private fun parseUnread(raw: Any?): ConversationUnreadEvent? {
        val payload = toJsonObject(raw) ?: return null
        val body = payload.optJSONObject("data") ?: payload
        val conversationId = body.optString("conversationId").takeIf { it.isNotBlank() }
            ?: return null
        val count = when (val value = body.opt("unreadCount") ?: body.opt("unread")) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        } ?: return null
        return ConversationUnreadEvent(conversationId, count.coerceAtLeast(0))
    }

    /** `chat:message_forwarded` — the server ships the forwarded message objects. */
    private fun parseForwarded(raw: Any?): MessagesForwardedEvent? {
        val payload = toJsonObject(raw) ?: return null
        val body = payload.optJSONObject("data") ?: payload
        val messages = payload.optJSONArray("messages")
            ?: body.optJSONArray("messages")
            ?: body.optJSONObject("data")?.optJSONArray("messages")
        val conversationIds = linkedSetOf<String>()
        val messageIds = linkedSetOf<String>()
        body.optString("conversationId").takeIf { it.isNotBlank() }
            ?.let(conversationIds::add)
        if (messages != null) {
            for (index in 0 until messages.length()) {
                val item = messages.optJSONObject(index) ?: continue
                item.optString("id").takeIf { it.isNotBlank() }?.let(messageIds::add)
                val target = item.optString("conversationId").takeIf { it.isNotBlank() }
                    ?: item.optJSONObject("conversation")?.optString("id")
                        ?.takeIf { it.isNotBlank() }
                target?.let(conversationIds::add)
            }
        }
        if (conversationIds.isEmpty() && messageIds.isEmpty()) return null
        return MessagesForwardedEvent(conversationIds.toList(), messageIds.toList())
    }

    /** `chat:member:*` / `chat:group:updated` — roster and group-metadata fan-out. */
    private fun parseGroupChange(raw: Any?, event: String): GroupChangeEvent? {
        val payload = toJsonObject(raw) ?: return null
        val body = payload.optJSONObject("data") ?: payload
        val conversationId = body.optString("conversationId").takeIf { it.isNotBlank() }
            ?: body.optString("id").takeIf { it.isNotBlank() }
            ?: body.optJSONObject("conversation")?.optString("id")?.takeIf { it.isNotBlank() }
        val userIds = mutableListOf<String>()
        body.optJSONArray("userIds")?.let { array ->
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(userIds::add)
            }
        }
        body.optString("userId").takeIf { it.isNotBlank() }?.let(userIds::add)
        if (conversationId == null && userIds.isEmpty()) return null
        return GroupChangeEvent(event, conversationId, userIds.distinct())
    }

    /** `presence:update` — a single snapshot, a `snapshots`/`users` array, or a wrapped `data`. */
    private fun parsePresence(raw: Any?): List<UserPresenceEvent> {
        val payload = toJsonObject(raw) ?: return emptyList()
        val snapshots = payload.optJSONArray("snapshots")
            ?: payload.optJSONArray("users")
            ?: payload.optJSONArray("data")
        val objects = mutableListOf<JSONObject>()
        if (snapshots != null) {
            for (index in 0 until snapshots.length()) {
                snapshots.optJSONObject(index)?.let(objects::add)
            }
        } else {
            val data = payload.optJSONObject("data") ?: payload
            objects.add(data)
            val nested = data.optJSONArray("snapshots") ?: data.optJSONArray("users")
            if (nested != null) {
                objects.clear()
                for (index in 0 until nested.length()) {
                    nested.optJSONObject(index)?.let(objects::add)
                }
            }
        }
        return objects.mapNotNull { snapshot ->
            val userId = snapshot.optString("userId").takeIf { it.isNotBlank() }
                ?: snapshot.optString("id").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val status = snapshot.optString("status")
            val isOnline = when {
                snapshot.has("online") -> snapshot.optBoolean("online")
                snapshot.has("isOnline") -> snapshot.optBoolean("isOnline")
                else -> status.equals("online", ignoreCase = true)
            }
            val lastSeen = listOf("lastSeenAt", "lastOnline", "last_online", "lastSeen")
                .firstNotNullOfOrNull { key ->
                    snapshot.optString(key).takeIf { it.isNotBlank() }
                }
            UserPresenceEvent(userId, isOnline, lastSeen)
        }
    }

    private fun parseEdit(raw: Any?): MessageEditedEvent? {
        val payload = toJsonObject(raw) ?: return null
        val body = payload.optJSONObject("data") ?: payload
        val messageObject = body.optJSONObject("message") ?: body.optJSONObject("updatedMessage")
        val messageId = body.optString("messageId").takeIf { it.isNotBlank() }
            ?: messageObject?.optString("id")?.takeIf { it.isNotBlank() }
            ?: return null
        val content = body.optString("content").takeIf { it.isNotBlank() }
            ?: messageObject?.optString("content").orEmpty()
        val dto = messageObject?.let {
            runCatching { gson.fromJson(it.toString(), ChatMessageDto::class.java) }.getOrNull()
        }
        return MessageEditedEvent(
            conversationId = body.optString("conversationId").takeIf { it.isNotBlank() },
            messageId = messageId,
            content = content,
            isEdited = !body.has("isEdited") || body.optBoolean("isEdited", true),
            message = dto
        )
    }

    private fun parseDelete(raw: Any?): MessageDeletedEvent? {
        val payload = toJsonObject(raw) ?: return null
        val body = payload.optJSONObject("data") ?: payload
        val ids = mutableListOf<String>()
        body.optJSONArray("messageIds")?.let { array ->
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(ids::add)
            }
        }
        body.optString("messageId").takeIf { it.isNotBlank() }?.let(ids::add)
        body.optJSONObject("message")?.optString("id")?.takeIf { it.isNotBlank() }?.let(ids::add)
        if (ids.isEmpty()) return null
        return MessageDeletedEvent(
            conversationId = body.optString("conversationId").takeIf { it.isNotBlank() },
            messageIds = ids.distinct(),
            deleteType = body.optString("deleteType").takeIf { it.isNotBlank() }
        )
    }

    private fun parseReaction(raw: Any?): MessageReactionEvent? {
        val payload = toJsonObject(raw) ?: return null
        val body = payload.optJSONObject("data") ?: payload
        val messageObject = body.optJSONObject("message")
        val messageId = body.optString("messageId").takeIf { it.isNotBlank() }
            ?: messageObject?.optString("id")?.takeIf { it.isNotBlank() }
            ?: return null
        val reactions = body.opt("reactions")
            ?: body.opt("reaction")
            ?: messageObject?.opt("reactions")
        return MessageReactionEvent(
            conversationId = body.optString("conversationId").takeIf { it.isNotBlank() },
            messageId = messageId,
            reactions = reactions?.let { toPlainValue(it) },
            emoji = body.optString("emoji").takeIf { it.isNotBlank() }
        )
    }

    private fun parsePin(raw: Any?, forceUnpin: Boolean): MessagePinEvent? {
        val payload = toJsonObject(raw) ?: return null
        val body = payload.optJSONObject("data") ?: payload
        val messageObject = body.optJSONObject("message")
        val messageId = body.optString("messageId").takeIf { it.isNotBlank() }
            ?: messageObject?.optString("id")?.takeIf { it.isNotBlank() }
            ?: return null
        val isPinned = if (forceUnpin) {
            false
        } else {
            when {
                body.has("isPinned") -> body.optBoolean("isPinned")
                body.has("pinned") -> body.optBoolean("pinned")
                messageObject != null -> messageObject.optBoolean("isPinned", true)
                else -> true
            }
        }
        return MessagePinEvent(
            conversationId = body.optString("conversationId").takeIf { it.isNotBlank() },
            messageId = messageId,
            isPinned = isPinned
        )
    }

    /** Unwraps an `org.json` value into maps / lists so the UI layer can parse it. */
    private fun toPlainValue(value: Any): Any = when (value) {
        is org.json.JSONArray -> (0 until value.length()).mapNotNull { index ->
            value.opt(index)?.let { toPlainValue(it) }
        }
        is JSONObject -> value.keys().asSequence().associateWith { key ->
            value.opt(key)?.let { toPlainValue(it) }
        }
        else -> value
    }

    /** Payloads arrive wrapped (`data.first` is the object) or nested inside arrays. */
    private fun toJsonObject(raw: Any?): JSONObject? = when (raw) {
        is JSONObject -> raw
        is org.json.JSONArray -> unwrapArray(raw)
        is String -> runCatching { JSONObject(raw) }.getOrNull()
        is Map<*, *> -> runCatching { JSONObject(raw) }.getOrNull()
        else -> null
    }

    private fun unwrapArray(array: org.json.JSONArray): JSONObject? {
        var current: Any? = array
        while (current is org.json.JSONArray && current.length() > 0) {
            current = current.opt(0)
        }
        return current as? JSONObject
    }

    companion object {
        private const val TAG = "ChatSocketManager"

        /** Same host as the REST API, which is also where Socket.IO is mounted. */
        private const val SOCKET_URL = "https://flirttimeapi.walit.in"
        private const val SOCKET_PATH = "/socket.io"

        private const val EVENT_JOIN_CONVERSATION = "chat:conversation:join"
        private const val EVENT_LEAVE_CONVERSATION = "chat:conversation:leave"
        private const val EVENT_VIEWING = "chat:conversation:viewing"
        private const val EVENT_MESSAGE_RECEIVED = "chat:message_received"
        private const val EVENT_MESSAGE_SENT = "chat:message_sent"
        private const val EVENT_TYPING = "chat:typing"
        private const val EVENT_MARK_READ = "chat:mark_read"
        private const val EVENT_EDIT_MESSAGE = "chat:edit_message"
        private const val EVENT_DELETE_MESSAGE = "chat:delete_message"
        private const val EVENT_REACT_MESSAGE = "chat:message:react"
        private const val EVENT_PIN_MESSAGE = "chat:pin_message"
        private const val EVENT_FORWARD_MESSAGE = "chat:forward_message"
        private const val EVENT_PIN_EXPIRED = "chat:message:pin:expired"

        private const val EVENT_SETTINGS_UPDATE = "chat:conversation:settings:update"
        private const val EVENT_ARCHIVE_CONVERSATION = "conversation:archive"
        private const val EVENT_DELETE_CONVERSATION = "conversation:delete"
        private const val EVENT_PIN_CONVERSATION_UPDATED = "conversation:pin-updated"

        private const val EVENT_MESSAGE_DELIVERED = "chat:message_delivered"
        private const val EVENT_DELIVERED_SYNC = "chat:delivered_sync"
        private const val EVENT_FORWARDED_MESSAGE = "chat:message_forwarded"
        private const val EVENT_UNREAD_UPDATED = "conversation:unread_updated"
        private const val EVENT_SYSTEM_MESSAGE = "chat:system:message"
        private const val EVENT_SCREENSHOT = "chat:screenshot"
        private const val EVENT_PRESENCE_SET = "presence:set"
        private const val EVENT_PRESENCE_UPDATE = "presence:update"
        private const val EVENT_PRESENCE_GET = "presence:get"

        /** Same tick upgrade, four backend spellings. */
        private val EVENT_DELIVERY_RECEIPTS = listOf(
            "chat:delivery_receipt",
            "chat:message_delivered",
            "chat:message:delivered",
            "message:delivery_receipt"
        )

        private val EVENT_GROUP_CHANGES = listOf(
            "chat:member:added",
            "chat:member:removed",
            "chat:member:role:updated",
            "chat:group:updated"
        )

        /** Message types that never carry a delivery receipt. */
        private const val TYPE_SYSTEM = "system"

        private val EVENT_MESSAGE_EDITS = listOf(
            "chat:message_edited",
            "chat:message:edited",
            "chat:message:edit:ack"
        )

        private val EVENT_MESSAGE_DELETES = listOf(
            "chat:message_deleted",
            "message:delete",
            "message:deleted",
            "chat:message:delete:ack"
        )

        private val EVENT_MESSAGE_REACTIONS = listOf(
            "message:reaction:updated",
            "chat:message:reacted",
            "chat:message:react:ack"
        )

        private val EVENT_MESSAGE_PINS = listOf(
            "chat:message:pin:updated",
            "chat:message:pin",
            "chat:message:pin:ack",
            EVENT_PIN_EXPIRED
        )

        private val EVENT_READ_RECEIPTS = listOf(
            "chat:read_receipt",
            "chat:message:seen",
            "message:read_receipt",
            "chat:message:status",
            "message:status"
        )

        private val EVENT_CONVERSATION_CHANGES = listOf(
            "conversation:updated",
            "chat:conversation_updated",
            "chat:inbox:refresh"
        )

        /** Thread preference fan-out: mute / pin / archive / block, plus the emit ack. */
        private val EVENT_CONVERSATION_SETTINGS = listOf(
            "chat:conversation:settings:updated",
            "chat:conversation:settings:update:ack",
            EVENT_PIN_CONVERSATION_UPDATED,
            "conversation:archived",
            "conversation:mute-updated",
            "user:blocked",
            "user:unblocked"
        )

        private val EVENT_CONVERSATION_REMOVED = listOf(
            "conversation:deleted",
            "chat:conversation:deleted",
            "conversation:delete:ack"
        )
    }
}
