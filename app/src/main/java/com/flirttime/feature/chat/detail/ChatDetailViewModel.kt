package com.flirttime.feature.chat.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.core.database.SessionManager
import com.flirttime.feature.main.matches.FreeTierLimitsStore
import com.flirttime.core.navigation.ChatMember
import com.flirttime.feature.chat.ChatRepository
import com.flirttime.feature.chat.cache.ChatCacheStore
import com.flirttime.feature.chat.cache.OutboxEntry
import com.flirttime.feature.chat.media.PreparedMedia
import com.flirttime.feature.chat.model.ChatMessageDto
import com.flirttime.feature.chat.model.DeliveryState
import com.flirttime.feature.chat.model.MessageKind
import com.flirttime.feature.chat.model.MessageRow
import com.flirttime.feature.chat.model.MessageUi
import com.flirttime.feature.chat.model.ReactionUi
import com.flirttime.feature.chat.model.ReplyPreviewUi
import com.flirttime.feature.chat.model.parseReactions
import com.flirttime.feature.chat.model.toUi
import com.flirttime.feature.chat.socket.ChatConnectionState
import com.flirttime.feature.chat.socket.ChatSocketManager
import com.flirttime.feature.chat.socket.MessageDeletedEvent
import com.flirttime.feature.chat.socket.MessageEditedEvent
import com.flirttime.feature.chat.socket.MessagePinEvent
import com.flirttime.feature.chat.socket.MessageReactionEvent
import com.flirttime.feature.chat.util.ChatTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

/** Thread-level flags for the current conversation (mute / pin / archive / block). */
data class ThreadSettings(
    val isMuted: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isBlocked: Boolean = false
)

/** Live presence of the direct-chat peer, rendered as the header subtitle. */
data class PeerPresenceUi(
    val userId: String,
    val isOnline: Boolean,
    val lastSeenAt: String? = null
)

/**
 * Conversation thread state: paginated history, optimistic sends, read receipts and
 * realtime fan-out for a single conversation.
 */
class ChatDetailViewModel(
    private val conversationId: String,
    peerUserIdHint: String,
    initialSettings: ThreadSettings,
    private val repository: ChatRepository,
    private val socketManager: ChatSocketManager,
    private val sessionManager: SessionManager,
    private val cache: ChatCacheStore,
    private val limitsStore: FreeTierLimitsStore
) : ViewModel() {

    private val _rows = MutableStateFlow<List<MessageRow>>(emptyList())
    val rows: StateFlow<List<MessageRow>> = _rows.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isOtherTyping = MutableStateFlow(false)
    val isOtherTyping: StateFlow<Boolean> = _isOtherTyping.asStateFlow()

    /** Bubble currently quoted by the composer (`null` when not replying). */
    private val _replyTarget = MutableStateFlow<MessageUi?>(null)
    val replyTarget: StateFlow<MessageUi?> = _replyTarget.asStateFlow()

    /** Message being edited inline in the composer. */
    private val _editTarget = MutableStateFlow<MessageUi?>(null)
    val editTarget: StateFlow<MessageUi?> = _editTarget.asStateFlow()

    /** Only one message can be pinned at a time, so the banner is a single item. */
    private val _pinnedMessage = MutableStateFlow<MessageUi?>(null)
    val pinnedMessage: StateFlow<MessageUi?> = _pinnedMessage.asStateFlow()

    /** One-shot "scroll to bubble" request (reply preview / pinned banner taps). */
    private val _scrollToMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val scrollToMessage: SharedFlow<String> = _scrollToMessage.asSharedFlow()

    /** Informational toasts such as "Message forwarded". */
    private val _notice = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notice: SharedFlow<String> = _notice.asSharedFlow()

    private val _errorMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private val _matchRequiredToChat = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val matchRequiredToChat: SharedFlow<Unit> = _matchRequiredToChat.asSharedFlow()

    /** Emitted whenever a new message should scroll the list to the bottom. */
    private val _scrollToBottom = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val scrollToBottom: SharedFlow<Unit> = _scrollToBottom.asSharedFlow()

    private val _threadSettings = MutableStateFlow(initialSettings)
    val threadSettings: StateFlow<ThreadSettings> = _threadSettings.asStateFlow()

    /** Screen closes itself after the thread is deleted from the sheet. */
    private val _conversationClosed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val conversationClosed: SharedFlow<Unit> = _conversationClosed.asSharedFlow()

    /** In-thread search query, newest-first hits and the in-flight flag. */
    private val _messageSearchQuery = MutableStateFlow("")
    val messageSearchQuery: StateFlow<String> = _messageSearchQuery.asStateFlow()

    /** Unread messages that arrived while the reader is scrolled up (iOS badge count). */
    private val _newMessagesWhileScrolled = MutableStateFlow(0)
    val newMessagesWhileScrolled: StateFlow<Int> = _newMessagesWhileScrolled.asStateFlow()

    /** True while the newest bubble is on screen, so arrivals do not bump the counter. */
    private var isReaderAtBottom = true

    /** Direct-chat peer verification, resolved from the thread when the intent had no flag. */
    private val _peerIsVerified = MutableStateFlow(false)
    val peerIsVerified: StateFlow<Boolean> = _peerIsVerified.asStateFlow()

    /** Distinct senders seen in this thread — the group member sheet fallback. */
    private val _members = MutableStateFlow<List<ChatMember>>(emptyList())
    val members: StateFlow<List<ChatMember>> = _members.asStateFlow()

    /** Live `presence:update` state of the direct-chat peer (header subtitle). */
    private val _peerPresence = MutableStateFlow<PeerPresenceUi?>(null)
    val peerPresence: StateFlow<PeerPresenceUi?> = _peerPresence.asStateFlow()

    /** Peers already subscribed through `presence:get`, so repeated publishes stay quiet. */
    private var presenceSubscribedFor: Set<String> = emptySet()

    private val _searchResults = MutableStateFlow<List<MessageUi>>(emptyList())
    val searchResults: StateFlow<List<MessageUi>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    /** Peer of a direct chat; resolved from the first incoming message when unknown. */
    var peerUserId: String = peerUserIdHint.trim()
        private set

    private val messages = mutableListOf<MessageUi>()

    /** Attachments kept in memory so a failed upload can be retried without re-picking. */
    private val pendingMedia = mutableMapOf<String, PreparedMedia>()
    private var currentPage = 1
    private var hasMorePages = true
    private var oldestLoadedRaw: String? = null

    private var currentUserId: String = ""
    private var lastReadMessageId: String? = null
    private var typingStopJob: Job? = null
    private var typingHideJob: Job? = null
    private var isFetchingOlder = false
    private var searchJob: Job? = null

    /** Temp ids currently owned by the send loop, so a replay cannot double-send. */
    private val inFlightSends: MutableSet<String> = mutableSetOf()

    /** Debounce identical composer sends (double-tap / race before `_isSending` flips). */
    private var lastQueuedText: String = ""
    private var lastQueuedAtMs: Long = 0L

    /** True when the last history fetch failed and only the cache (or nothing) is shown. */
    private var loadFailed = false

    /** Epoch millis of the last local “Clear chat”; older messages stay hidden. */
    private var clearedBeforeMillis: Long = 0L

    /** The first connect is the normal startup handshake, not a reconnect. */
    private var sawInitialConnect = false

    /** Socket / network state used by the reconnect banner and the send queue. */
    val connectionState: StateFlow<ChatConnectionState> = socketManager.connectionState

    init {
        socketManager.connect()
        socketManager.joinConversation(conversationId)
        socketManager.setConversationViewing(conversationId, true)
        announcePresence()
        observeSocket()
        loadInitial()
    }

    /** `presence:set` — this device is online for the open thread. */
    private fun announcePresence() {
        viewModelScope.launch {
            val me = sessionManager.getUserId()
            if (me.isNotBlank()) socketManager.emitPresence("online", conversationId, me)
        }
    }

    /** `presence:get` — subscribes to the peer once the roster is known. */
    private suspend fun subscribePeerPresence() {
        val ids = buildList {
            if (peerUserId.isNotBlank()) add(peerUserId)
            addAll(_members.value.map { it.userId })
        }
            .filter { it.isNotBlank() && it != currentUserId }
            .distinct()
            .toSet()
        if (ids.isEmpty() || ids == presenceSubscribedFor) return
        presenceSubscribedFor = ids
        socketManager.emitSubscribePresence(ids.toList())
    }

    /** iOS `UIApplication.userDidTakeScreenshotNotification` — warns the peer. */
    fun notifyScreenshot() {
        socketManager.emitScreenshot(conversationId)
    }

    fun loadInitial() {
        viewModelScope.launch {
            _isLoading.value = true
            if (currentUserId.isEmpty()) currentUserId = sessionManager.getUserId()
            clearedBeforeMillis = cache.clearedAt(conversationId)

            when (val result = repository.getMessages(conversationId, page = 1, limit = PAGE_SIZE)) {
                is Resource.Success -> {
                    val page = result.data
                    val incoming = page.messages.orEmpty()
                        .map { it.toUi(currentUserId, conversationId) }
                        .filterNot { isCleared(it) }
                    if (peerUserId.isBlank()) {
                        peerUserId = page.messages.orEmpty()
                            .mapNotNull { it.sender?.id ?: it.senderId }
                            .firstOrNull { it.isNotBlank() && it != currentUserId }
                            .orEmpty()
                    }
                    messages.clear()
                    messages.addAll(incoming.sortedBy { sortKey(it) })
                    currentPage = 1
                    oldestLoadedRaw = messages.firstOrNull()?.createdAtRaw
                    hasMorePages = page.pagination?.resolvedHasMore(
                        page = 1,
                        received = incoming.size,
                        requested = PAGE_SIZE
                    ) ?: (incoming.size >= PAGE_SIZE)
                    // Only one pin is active at a time; the page payload is authoritative.
                    _pinnedMessage.value = page.pinnedMessages?.firstOrNull()
                        ?.toUi(currentUserId, conversationId)
                        ?: messages.firstOrNull { it.isPinned }
                    publish()
                    _scrollToBottom.tryEmit(Unit)
                    markRead()
                    cache.saveMessages(conversationId, messages)
                    loadFailed = false
                }
                is Resource.Error -> {
                    // Offline cold start: show the cached tail of this conversation.
                    val restored = restoreCachedMessages()
                    loadFailed = !restored
                    if (loadFailed) _errorMessage.tryEmit(result.message)
                }
                is Resource.Loading -> Unit
            }

            _isLoading.value = false
            replayOutbox()
        }
    }

    /** Loads the cached tail so the thread is readable without a connection. */
    private suspend fun restoreCachedMessages(): Boolean {
        if (messages.isNotEmpty()) return true
        val cached = cache.loadMessages(conversationId).filterNot { isCleared(it) }
        if (cached.isEmpty()) return false
        messages.addAll(cached.sortedBy { sortKey(it) })
        oldestLoadedRaw = messages.firstOrNull()?.createdAtRaw
        hasMorePages = false
        publish()
        _scrollToBottom.tryEmit(Unit)
        return true
    }

    fun loadOlderMessages() {
        if (_isLoadingMore.value || !hasMorePages) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            fetchOlderPage()
            _isLoadingMore.value = false
        }
    }

    /** Banner action: rebuild a dead socket; a successful reconnect reloads the thread. */
    fun forceReconnect() = socketManager.forceReconnect()

    /**
     * Pulls the next older page into [messages]. Returns how many rows were appended, so
     * the search loop can stop as soon as the history runs out.
     */
    private suspend fun fetchOlderPage(): Int {
        if (isFetchingOlder || !hasMorePages) return 0
        val before = oldestLoadedRaw ?: return 0

        isFetchingOlder = true
        var appended = 0
        try {
            val nextPage = currentPage + 1
            when (val result = repository.getMessages(
                conversationId = conversationId,
                page = nextPage,
                limit = PAGE_SIZE,
                before = before
            )) {
                is Resource.Success -> {
                    val page = result.data
                    val older = page.messages.orEmpty()
                        .map { it.toUi(currentUserId, conversationId) }
                        .filterNot { isCleared(it) }
                        .filterNot { candidate -> messages.any { it.id == candidate.id } }

                    if (older.isEmpty()) {
                        hasMorePages = false
                    } else {
                        messages.addAll(older)
                        messages.sortBy { sortKey(it) }
                        currentPage = nextPage
                        oldestLoadedRaw = messages.firstOrNull()?.createdAtRaw ?: before
                        hasMorePages = page.pagination?.resolvedHasMore(
                            page = nextPage,
                            received = older.size,
                            requested = PAGE_SIZE
                        ) ?: (older.size >= PAGE_SIZE)
                        publish()
                        appended = older.size
                    }
                }
                is Resource.Error -> _errorMessage.tryEmit(result.message)
                is Resource.Loading -> Unit
            }
        } finally {
            isFetchingOlder = false
        }
        return appended
    }

    // region In-thread search

    /** Debounced search entry point; an empty query resets the result list. */
    fun onMessageSearchQueryChanged(rawQuery: String) {
        _messageSearchQuery.value = rawQuery
        searchJob?.cancel()

        val query = rawQuery.trim()
        if (query.isEmpty()) {
            _isSearching.value = false
            _searchResults.value = emptyList()
            return
        }

        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(SEARCH_DEBOUNCE_MS)
            val hits = scanForMatches(query)
            if (_messageSearchQuery.value.trim() == query) {
                _searchResults.value = hits
                _isSearching.value = false
            }
        }
    }

    fun clearMessageSearch() {
        searchJob?.cancel()
        _messageSearchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    /**
     * Android twin of iOS `MessageRepository.searchMessages`: case-insensitive "contains",
     * deleted/system rows skipped, newest first, capped at [SEARCH_LIMIT].
     *
     * The Android thread only keeps the pages it has loaded, so older pages are pulled in
     * (up to [SEARCH_MAX_BACKFILL_PAGES]) until the cap or the end of the history is hit.
     */
    private suspend fun scanForMatches(query: String): List<MessageUi> {
        var hits = matchMessages(query)
        var backfilled = 0
        while (hits.size < SEARCH_LIMIT && hasMorePages && backfilled < SEARCH_MAX_BACKFILL_PAGES) {
            if (fetchOlderPage() == 0) break
            backfilled++
            hits = matchMessages(query)
        }
        return hits.take(SEARCH_LIMIT)
    }

    private fun matchMessages(query: String): List<MessageUi> =
        messages.asReversed().filter { message ->
            !message.isDeleted &&
                !message.isSystem &&
                !message.isOptimisticTemporary &&
                message.body.contains(query, ignoreCase = true)
        }

    // endregion

    fun sendMessage(rawText: String) {
        enqueueOutgoing(
            rawText = rawText,
            messageType = "text",
            metadata = null
        )
    }

    /** iOS contact share: `messageType = contact` + contactName/contactPhone metadata. */
    fun sendContact(name: String, phone: String) {
        val safeName = name.trim().ifBlank { "Contact" }
        val safePhone = phone.trim()
        val body = if (safePhone.isBlank()) {
            "Contact: $safeName"
        } else {
            "Contact: $safeName\nPhone: $safePhone"
        }
        enqueueOutgoing(
            rawText = body,
            messageType = "contact",
            metadata = buildMap {
                put("contactName", safeName)
                if (safePhone.isNotBlank()) put("contactPhone", safePhone)
            }
        )
    }

    private fun enqueueOutgoing(
        rawText: String,
        messageType: String,
        metadata: Map<String, Any>?
    ) {
        val text = rawText.trim()
        if (text.isEmpty()) return

        // Guard against double-tap / IME+button races before async dispatch flips `_isSending`.
        val now = android.os.SystemClock.elapsedRealtime()
        if (text == lastQueuedText && now - lastQueuedAtMs < SEND_DEBOUNCE_MS) return
        if (_isSending.value && text == lastQueuedText) return

        if (_threadSettings.value.isBlocked) {
            _notice.tryEmit("Unblock this user to send messages")
            return
        }

        val editing = _editTarget.value
        if (editing != null && messageType == "text") {
            submitEdit(editing, text)
            return
        }

        lastQueuedText = text
        lastQueuedAtMs = now

        val replyTargetSnapshot = _replyTarget.value
        val replyToId = replyTargetSnapshot
            ?.takeUnless { it.isOptimisticTemporary }
            ?.id
            ?.takeIf { it.isNotBlank() }

        val tempId = "local-${UUID.randomUUID()}"
        val optimistic = MessageUi(
            id = tempId,
            conversationId = conversationId,
            body = text,
            kind = com.flirttime.feature.chat.model.MessageKind.TEXT,
            mediaUrl = null,
            isMine = true,
            isSystem = false,
            senderName = null,
            createdAtRaw = ChatTimeFormatter.formatIsoUtc(Date()),
            timeLabel = ChatTimeFormatter.messageTimeLabel(ChatTimeFormatter.formatIsoUtc(Date())),
            deliveryState = DeliveryState.SENDING,
            pendingId = tempId,
            reply = replyPreviewFor(replyTargetSnapshot)
        )
        messages.add(optimistic)
        _replyTarget.value = null
        publish()
        _scrollToBottom.tryEmit(Unit)
        stopTyping()

        // Queue first, then send: if the process dies the entry is replayed on next open.
        val entry = OutboxEntry(
            tempId = tempId,
            conversationId = conversationId,
            text = text,
            replyToId = replyToId,
            messageType = messageType,
            metadata = metadata
        )
        viewModelScope.launch {
            cache.enqueue(entry)
            cache.saveMessages(conversationId, messages)
        }
        dispatchSend(entry)
    }

    fun retry(message: MessageUi) {
        val pendingId = message.pendingId ?: message.id
        val index = messages.indexOfFirst { it.id == pendingId || it.pendingId == pendingId }
        if (index == -1) return

        val target = messages[index]
        val prepared = pendingMedia.remove(pendingId) ?: pendingMedia.remove(target.id)
        if (prepared != null) {
            messages.removeAt(index)
            publish()
            sendMedia(prepared)
            return
        }

        // Text: reuse the queued entry so the temp id (and its reply link) survive.
        viewModelScope.launch {
            val entry = cache.pendingFor(conversationId).firstOrNull { it.tempId == pendingId }
                ?: OutboxEntry(
                    tempId = pendingId,
                    conversationId = conversationId,
                    text = target.body,
                    replyToId = target.reply?.id
                )
            cache.enqueue(entry)
            markSending(pendingId)
            dispatchSend(entry)
        }
    }

    // region Send queue

    /**
     * Sends one queued message, mirroring the iOS retry loop: up to [MAX_AUTO_RETRIES]
     * attempts with back-off while the socket is up; once it is down the bubble parks as
     * failed and [replayOutbox] picks it up as soon as the connection returns.
     */
    private fun dispatchSend(entry: OutboxEntry) {
        if (inFlightSends.contains(entry.tempId)) return
        if (_threadSettings.value.isBlocked) {
            markFailed(entry.tempId)
            return
        }
        inFlightSends.add(entry.tempId)

        viewModelScope.launch {
            var attempt = 0
            try {
                while (true) {
                    _isSending.value = true
                    val result = repository.sendMessage(
                        conversationId = conversationId,
                        text = entry.text,
                        clientMessageId = entry.tempId,
                        replyToId = entry.replyToId,
                        messageType = entry.messageType,
                        metadata = entry.metadata
                    )
                    _isSending.value = false

                    when (result) {
                        is Resource.Success -> {
                            cache.removeFromOutbox(entry.tempId)
                            replacePending(
                                entry.tempId,
                                result.data.toUi(currentUserId, conversationId)
                            )
                            cache.saveMessages(conversationId, messages)
                            return@launch
                        }
                        is Resource.Error -> {
                            if (isMatchRequired(result)) {
                                cache.removeFromOutbox(entry.tempId)
                                markFailed(entry.tempId)
                                if (!limitsStore.isPremium) {
                                    _matchRequiredToChat.tryEmit(Unit)
                                }
                                return@launch
                            }
                            attempt++
                            cache.markAttempt(entry.tempId)
                            val canRetry = attempt <= MAX_AUTO_RETRIES &&
                                socketManager.isConnected.value
                            if (!canRetry) {
                                markFailed(entry.tempId)
                                return@launch
                            }
                            // Stay in the "sending" state while the retry backs off.
                            markSending(entry.tempId)
                            delay(retryDelayMs(attempt))
                        }
                        is Resource.Loading -> Unit
                    }
                }
            } finally {
                _isSending.value = false
                cache.saveMessages(conversationId, messages)
                inFlightSends.remove(entry.tempId)
            }
        }
    }

    /**
     * Merges the persisted queue back into the thread (failed bubbles) and fires the
     * deferred sends when the connection is available.
     */
    private suspend fun replayOutbox() {
        val entries = cache.pendingFor(conversationId)
        if (entries.isEmpty()) return

        var restored = false
        for (entry in entries) {
            val known = messages.any { it.id == entry.tempId || it.pendingId == entry.tempId }
            if (known) continue
            val createdAt = ChatTimeFormatter.formatIsoUtc(Date(entry.createdAt))
            messages.add(
                MessageUi(
                    id = entry.tempId,
                    conversationId = conversationId,
                    body = entry.text,
                    kind = MessageKind.TEXT,
                    mediaUrl = null,
                    isMine = true,
                    isSystem = false,
                    senderName = null,
                    createdAtRaw = createdAt,
                    timeLabel = ChatTimeFormatter.messageTimeLabel(createdAt),
                    deliveryState = DeliveryState.FAILED,
                    pendingId = entry.tempId,
                    isFailed = true
                )
            )
            restored = true
        }
        if (restored) {
            messages.sortBy { sortKey(it) }
            publish()
            _scrollToBottom.tryEmit(Unit)
        }

        if (socketManager.isConnected.value) {
            entries.forEach { entry ->
                markSending(entry.tempId)
                dispatchSend(entry)
            }
        }
    }

    private fun retryDelayMs(attempt: Int): Long =
        (RETRY_BASE_DELAY_MS * attempt).coerceAtMost(RETRY_MAX_DELAY_MS)

    // endregion

    // region Thread actions

    fun toggleMute() {
        updateThreadSettings(isMuted = !_threadSettings.value.isMuted)
    }

    fun togglePin() {
        updateThreadSettings(isPinned = !_threadSettings.value.isPinned)
    }

    fun toggleArchive() {
        val next = !_threadSettings.value.isArchived
        updateThreadSettings(isArchived = next)
        _notice.tryEmit(if (next) "Chat archived" else "Chat moved back to your inbox")
    }

    fun markConversationRead() = markRead()

    /**
     * “Clear chat”: wipes the locally cached transcript (and any queued sends) without
     * touching the server copy — the same scope as the iOS confirmation.
     */
    fun clearChat() {
        val clearedAt = System.currentTimeMillis()
        clearedBeforeMillis = clearedAt
        messages.clear()
        pendingMedia.clear()
        lastReadMessageId = null
        oldestLoadedRaw = null
        currentPage = 1
        hasMorePages = false
        _pinnedMessage.value = null
        _replyTarget.value = null
        _editTarget.value = null
        publish()
        viewModelScope.launch {
            cache.clearMessages(conversationId)
            cache.clearOutbox(conversationId)
            cache.markCleared(conversationId, clearedAt)
        }
        _notice.tryEmit("Chat cleared")
    }

    fun markConversationUnread() {
        viewModelScope.launch {
            when (val result = repository.markConversationUnread(conversationId)) {
                is Resource.Error -> _errorMessage.tryEmit(result.message)
                else -> _notice.tryEmit("Marked as unread")
            }
        }
    }

    fun toggleBlock() {
        val userId = peerUserId
        if (userId.isBlank()) {
            _errorMessage.tryEmit("Blocking is only available in direct chats")
            return
        }
        val snapshot = _threadSettings.value
        val blocking = !snapshot.isBlocked
        _threadSettings.value = snapshot.copy(isBlocked = blocking)
        socketManager.emitConversationSettings(conversationId, isBlocked = blocking)
        viewModelScope.launch {
            val result = if (blocking) {
                repository.blockUser(userId, conversationId)
            } else {
                repository.unblockUser(userId)
            }
            if (result is Resource.Error) {
                _threadSettings.value = snapshot
                _errorMessage.tryEmit(result.message)
            }
        }
    }

    fun deleteConversation() {
        socketManager.emitDeleteConversation(conversationId, "me")
        viewModelScope.launch {
            when (val result = repository.deleteConversation(conversationId)) {
                is Resource.Error -> _errorMessage.tryEmit(result.message)
                else -> _conversationClosed.tryEmit(Unit)
            }
        }
    }

    /** Optimistic mute / pin / archive, mirroring iOS `applyConversationSettings`. */
    private fun updateThreadSettings(
        isMuted: Boolean? = null,
        isPinned: Boolean? = null,
        isArchived: Boolean? = null
    ) {
        val snapshot = _threadSettings.value
        _threadSettings.value = snapshot.copy(
            isMuted = isMuted ?: snapshot.isMuted,
            isPinned = isPinned ?: snapshot.isPinned,
            isArchived = isArchived ?: snapshot.isArchived
        )

        socketManager.emitConversationSettings(
            conversationId = conversationId,
            isMuted = isMuted,
            isPinned = isPinned,
            isArchived = isArchived
        )
        if (isArchived != null) {
            socketManager.emitArchiveConversation(conversationId, isArchived)
        }

        viewModelScope.launch {
            val result = repository.updateConversationSettings(
                conversationId = conversationId,
                isMuted = isMuted,
                isPinned = isPinned,
                isArchived = isArchived
            )
            if (result is Resource.Error) {
                _threadSettings.value = snapshot
                _errorMessage.tryEmit(result.message)
            }
        }
    }

    // endregion

    // region Message actions

    fun messageById(id: String): MessageUi? =
        messages.firstOrNull { it.id == id }
            ?: messages.firstOrNull { it.pendingId == id }

    /** Starts a reply; edit mode is mutually exclusive with it (iOS behaviour). */
    fun startReply(message: MessageUi) {
        val target = messageById(message.id) ?: message
        if (_replyTarget.value?.id == target.id) return
        _editTarget.value = null
        _replyTarget.value = target
    }

    fun cancelReply() {
        _replyTarget.value = null
    }

    /** Text-only, outgoing, non-deleted messages can be edited. */
    fun canEdit(message: MessageUi): Boolean =
        message.isMine &&
            !message.isDeleted &&
            !message.isOptimisticTemporary &&
            message.kind == MessageKind.TEXT &&
            !message.isFailed

    /** Only my own messages can be deleted for everyone. */
    fun canDeleteForEveryone(message: MessageUi): Boolean =
        message.isMine && !message.isOptimisticTemporary

    fun startEdit(message: MessageUi) {
        if (!canEdit(message)) return
        _replyTarget.value = null
        _editTarget.value = messageById(message.id) ?: message
    }

    fun cancelEdit() {
        _editTarget.value = null
    }

    private fun submitEdit(target: MessageUi, text: String) {
        val index = messages.indexOfFirst { it.id == target.id }
        _editTarget.value = null
        if (index == -1) return

        val snapshot = messages[index]
        messages[index] = snapshot.copy(body = text, isEdited = true)
        updateReplyPreviews(snapshot.id, text)
        publish()

        viewModelScope.launch {
            when (val result = repository.editMessage(conversationId, target.id, text)) {
                is Resource.Success -> Unit
                is Resource.Error -> {
                    _errorMessage.tryEmit(result.message)
                    restore(snapshot)
                    // Realtime fallback so the peer still sees the edit.
                    socketManager.emitEditMessage(conversationId, target.id, text)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun deleteMessage(message: MessageUi, forEveryone: Boolean) {
        val index = messages.indexOfFirst { it.id == message.id }
        if (index == -1) return

        val snapshot = messages[index]
        if (forEveryone) {
            messages[index] = snapshot.copy(
                isDeleted = true,
                body = "You deleted this message",
                reactions = emptyList()
            )
        } else {
            messages.removeAt(index)
        }
        if (_pinnedMessage.value?.id == snapshot.id) _pinnedMessage.value = null
        _replyTarget.value = _replyTarget.value?.takeIf { it.id != snapshot.id }
        _editTarget.value = _editTarget.value?.takeIf { it.id != snapshot.id }
        publish()

        viewModelScope.launch {
            when (val result = repository.deleteMessage(conversationId, snapshot.id, forEveryone)) {
                is Resource.Success -> Unit
                is Resource.Error -> {
                    _errorMessage.tryEmit(result.message)
                    if (messages.none { it.id == snapshot.id }) {
                        messages.add(snapshot)
                        messages.sortBy { sortKey(it) }
                        publish()
                    }
                    socketManager.emitDeleteMessages(
                        conversationId,
                        listOf(snapshot.id),
                        if (forEveryone) "everyone" else "me"
                    )
                }
                is Resource.Loading -> Unit
            }
        }
    }

    /** Optimistic emoji toggle: one reaction per user per message. */
    fun toggleReaction(message: MessageUi, emoji: String) {
        val index = messages.indexOfFirst { it.id == message.id }
        if (index == -1 || emoji.isBlank()) return

        val snapshot = messages[index]
        messages[index] = applyOptimisticReaction(snapshot, emoji)
        publish()

        viewModelScope.launch {
            when (val result = repository.reactToMessage(conversationId, message.id, emoji)) {
                is Resource.Success -> {
                    val serverReactions = result.data.parseReactions(currentUserId)
                    if (serverReactions.isNotEmpty()) {
                        val current = messages.indexOfFirst { it.id == message.id }
                        if (current != -1) {
                            messages[current] = messages[current].copy(reactions = serverReactions)
                            publish()
                        }
                    }
                }
                is Resource.Error -> {
                    _errorMessage.tryEmit(result.message)
                    val current = messages.indexOfFirst { it.id == message.id }
                    if (current != -1) {
                        messages[current] = messages[current].copy(reactions = snapshot.reactions)
                        publish()
                    }
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun applyOptimisticReaction(source: MessageUi, emoji: String): MessageUi {
        val groups = source.reactions.toMutableList()
        val targetIndex = groups.indexOfFirst { it.emoji == emoji }

        if (targetIndex != -1 && groups[targetIndex].reactedByMe) {
            val toggled = groups[targetIndex].toggleMine(currentUserId, null)
            if (toggled == null) groups.removeAt(targetIndex) else groups[targetIndex] = toggled
            return source.copy(reactions = groups)
        }

        // Drop my reaction from every other group before adding the new one.
        for (i in groups.indices.reversed()) {
            if (!groups[i].reactedByMe) continue
            val toggled = groups[i].toggleMine(currentUserId, null)
            if (toggled == null) groups.removeAt(i) else groups[i] = toggled
        }

        val index = groups.indexOfFirst { it.emoji == emoji }
        if (index == -1) {
            groups.add(ReactionUi(emoji = emoji, count = 1, reactedByMe = true, userIds = listOf(currentUserId)))
        } else {
            groups[index] = groups[index].copy(
                count = groups[index].count + 1,
                reactedByMe = true,
                userIds = (groups[index].userIds + currentUserId).distinct()
            )
        }
        return source.copy(reactions = groups)
    }

    /** Pin / unpin; pinning clears every other pin (the API keeps a single pin). */
    fun setPinned(message: MessageUi, pinned: Boolean, pinDuration: String? = null) {
        val target = messageById(message.id) ?: return
        if (target.isOptimisticTemporary) return

        val previousPins = messages.map { it.id to it.isPinned }
        val previousPinned = _pinnedMessage.value

        for (index in messages.indices) {
            val shouldPin = pinned && messages[index].id == target.id
            if (messages[index].isPinned != shouldPin) {
                messages[index] = messages[index].copy(isPinned = shouldPin)
            }
        }
        _pinnedMessage.value = if (pinned) messageById(target.id) else null
        publish()

        viewModelScope.launch {
            when (val result = repository.pinMessage(conversationId, target.id, pinned, pinDuration)) {
                is Resource.Success -> Unit
                is Resource.Error -> {
                    _errorMessage.tryEmit(result.message)
                    for ((id, wasPinned) in previousPins) {
                        val index = messages.indexOfFirst { it.id == id }
                        if (index != -1) messages[index] = messages[index].copy(isPinned = wasPinned)
                    }
                    _pinnedMessage.value = previousPinned
                    publish()
                    socketManager.emitPinMessage(conversationId, target.id, pinned, pinDuration)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun forwardMessage(message: MessageUi, recipientIds: List<String>) {
        val id = message.id.takeIf { it.isNotBlank() && !message.isOptimisticTemporary } ?: return
        if (recipientIds.isEmpty()) return

        viewModelScope.launch {
            val recipients = recipientIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            when (val result = repository.forwardMessages(listOf(id), recipients)) {
                is Resource.Success -> _notice.tryEmit("Message forwarded")
                is Resource.Error -> {
                    _errorMessage.tryEmit(result.message)
                    socketManager.emitForwardMessage(conversationId, listOf(id), recipients)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    /** Jump to a quoted message when its preview is tapped. */
    fun requestScrollToMessage(messageId: String) {
        if (messageId.isBlank()) return
        _scrollToMessage.tryEmit(messageId)
    }

    /** Forwards a multi-select batch; ids are validated before the request. */
    fun forwardMessages(targets: List<MessageUi>, recipientIds: List<String>) {
        val ids = targets
            .filterNot { it.isOptimisticTemporary || it.isSystem }
            .mapNotNull { it.id.takeIf { id -> id.isNotBlank() } }
            .distinct()
        val recipients = recipientIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (ids.isEmpty() || recipients.isEmpty()) return

        viewModelScope.launch {
            when (val result = repository.forwardMessages(ids, recipients)) {
                is Resource.Success -> _notice.tryEmit(
                    if (ids.size == 1) "Message forwarded" else "${ids.size} messages forwarded"
                )
                is Resource.Error -> {
                    _errorMessage.tryEmit(result.message)
                    socketManager.emitForwardMessage(conversationId, ids, recipients)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    /** Multi-select delete: each bubble runs its own optimistic update + request. */
    fun deleteMessages(targets: List<MessageUi>, forEveryone: Boolean) {
        targets
            .mapNotNull { candidate -> messageById(candidate.id) }
            .forEach { deleteMessage(it, forEveryone) }
    }

    /** Messages that can be copied / forwarded / deleted as a batch. */
    fun selectableMessages(): List<MessageUi> =
        messages.filterNot { it.isOptimisticTemporary || it.isSystem || it.isDeleted }

    /** Clipboard payload for a bubble; media bubbles fall back to their caption. */
    fun copyText(message: MessageUi): String = message.body.trim()

    /** Who has seen the bubble, for the Message Info sheet. */
    fun receiptStatusFor(message: MessageUi, userId: String): String =
        message.receipts[userId] ?: when (message.deliveryState) {
            DeliveryState.READ -> "read"
            DeliveryState.DELIVERED -> "delivered"
            DeliveryState.SENT -> "sent"
            else -> "pending"
        }

    /** Current user id, resolved on the first load. */
    fun signedInUserId(): String = currentUserId

    private fun replyPreviewFor(target: MessageUi?): ReplyPreviewUi? {
        if (target == null || target.id.isBlank() || target.isOptimisticTemporary) return null
        return ReplyPreviewUi(
            id = target.id,
            senderName = if (target.isMine) "You" else target.senderName,
            body = target.body,
            kind = target.kind,
            isMine = target.isMine
        )
    }

    /** Keeps quoted previews in sync when the quoted message is edited. */
    private fun updateReplyPreviews(messageId: String, newBody: String) {
        for (index in messages.indices) {
            val reply = messages[index].reply ?: continue
            if (reply.id == messageId && reply.body != newBody) {
                messages[index] = messages[index].copy(reply = reply.copy(body = newBody))
            }
        }
        _pinnedMessage.value?.let { pinned ->
            if (pinned.id == messageId) {
                _pinnedMessage.value = pinned.copy(body = newBody, isEdited = true)
            }
        }
    }

    private fun restore(snapshot: MessageUi) {
        val index = messages.indexOfFirst { it.id == snapshot.id }
        if (index == -1) return
        messages[index] = snapshot
        publish()
    }

    // endregion

    /**
     * Sends an attachment the same way the iOS client does: open an upload session,
     * PUT the bytes, then post the message with the storage key as `mediaIds[0]`.
     */
    fun sendMedia(prepared: PreparedMedia) {
        val replyTargetSnapshot = _replyTarget.value
        val replyToId = replyTargetSnapshot
            ?.takeUnless { it.isOptimisticTemporary }
            ?.id
            ?.takeIf { it.isNotBlank() }

        val tempId = "local-${UUID.randomUUID()}"
        val createdAt = ChatTimeFormatter.formatIsoUtc(Date())
        val optimistic = MessageUi(
            id = tempId,
            conversationId = conversationId,
            body = "",
            kind = prepared.messageKind(),
            mediaUrl = null,
            isMine = true,
            isSystem = false,
            senderName = null,
            createdAtRaw = createdAt,
            timeLabel = ChatTimeFormatter.messageTimeLabel(createdAt),
            deliveryState = DeliveryState.SENDING,
            pendingId = tempId,
            localUri = prepared.localPath,
            durationMs = prepared.durationMs,
            mediaWidth = prepared.width,
            mediaHeight = prepared.height,
            reply = replyPreviewFor(replyTargetSnapshot)
        )

        pendingMedia[tempId] = prepared
        messages.add(optimistic)
        _replyTarget.value = null
        publish()
        _scrollToBottom.tryEmit(Unit)
        stopTyping()

        uploadAndSendMedia(tempId, prepared, replyToId)
    }

    private fun uploadAndSendMedia(tempId: String, prepared: PreparedMedia, replyToId: String? = null) {
        viewModelScope.launch {
            val upload = when (
                val result = repository.uploadChatMedia(
                    kind = prepared.kind,
                    contentType = prepared.contentType,
                    fileName = prepared.fileName,
                    bytes = prepared.bytes
                )
            ) {
                is Resource.Success -> result.data
                is Resource.Error -> {
                    _errorMessage.tryEmit(result.message)
                    markFailed(tempId)
                    return@launch
                }
                is Resource.Loading -> return@launch
            }

            val sent = repository.sendMediaMessage(
                conversationId = conversationId,
                kind = prepared.kind,
                upload = upload,
                clientMessageId = tempId,
                fileName = prepared.fileName,
                fileSizeBytes = prepared.bytes.size.toLong(),
                durationSeconds = prepared.durationMs.takeIf { it > 0 }?.div(1000.0),
                width = prepared.width.takeIf { it > 0 },
                height = prepared.height.takeIf { it > 0 },
                replyToId = replyToId
            )

            when (sent) {
                is Resource.Success -> {
                    pendingMedia.remove(tempId)
                    val local = messages.firstOrNull { it.id == tempId }
                    val serverUi = sent.data.toUi(currentUserId, conversationId)
                    replacePending(
                        tempId,
                        if (local != null) serverUi.keepingLocalPreview(local) else serverUi
                    )
                }
                is Resource.Error -> {
                    _errorMessage.tryEmit(sent.message)
                    markFailed(tempId)
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun PreparedMedia.messageKind(): MessageKind = when (kind.lowercase()) {
        "image" -> MessageKind.IMAGE
        "video" -> MessageKind.VIDEO
        "audio" -> MessageKind.AUDIO
        else -> MessageKind.TEXT
    }

    fun sendLocation(latitude: Double, longitude: Double, address: String, name: String? = null) {
        if (_isSending.value) return
        if (_threadSettings.value.isBlocked) {
            _notice.tryEmit("Unblock this user to send messages")
            return
        }

        val replyTargetSnapshot = _replyTarget.value
        val replyToId = replyTargetSnapshot
            ?.takeUnless { it.isOptimisticTemporary }
            ?.id
            ?.takeIf { it.isNotBlank() }

        val tempId = "local-${UUID.randomUUID()}"
        val createdAt = ChatTimeFormatter.formatIsoUtc(Date())
        val label = address.ifBlank { "Current location" }
        val optimistic = MessageUi(
            id = tempId,
            conversationId = conversationId,
            body = label,
            kind = MessageKind.LOCATION,
            mediaUrl = null,
            isMine = true,
            isSystem = false,
            senderName = null,
            createdAtRaw = createdAt,
            timeLabel = ChatTimeFormatter.messageTimeLabel(createdAt),
            deliveryState = DeliveryState.SENDING,
            pendingId = tempId,
            reply = replyPreviewFor(replyTargetSnapshot),
            locationLatitude = latitude,
            locationLongitude = longitude,
            locationLabel = label
        )
        messages.add(optimistic)
        _replyTarget.value = null
        publish()
        _scrollToBottom.tryEmit(Unit)
        stopTyping()

        viewModelScope.launch {
            _isSending.value = true
            when (
                val result = repository.sendLocationMessage(
                    conversationId = conversationId,
                    latitude = latitude,
                    longitude = longitude,
                    name = name,
                    address = label,
                    clientMessageId = tempId,
                    replyToId = replyToId
                )
            ) {
                is Resource.Success -> {
                    replacePending(tempId, result.data.toUi(currentUserId, conversationId))
                    cache.saveMessages(conversationId, messages)
                }
                is Resource.Error -> {
                    if (isMatchRequired(result)) {
                        markFailed(tempId)
                        if (!limitsStore.isPremium) {
                            _matchRequiredToChat.tryEmit(Unit)
                        }
                    } else {
                        _errorMessage.tryEmit(result.message)
                        markFailed(tempId)
                    }
                }
                is Resource.Loading -> Unit
            }
            _isSending.value = false
        }
    }

    /** Keeps the picked-file preview on the bubble until the server URL is usable. */
    private fun MessageUi.keepingLocalPreview(local: MessageUi): MessageUi = copy(
        localUri = localUri ?: local.localUri,
        thumbnailUrl = thumbnailUrl ?: local.thumbnailUrl,
        durationMs = if (durationMs > 0) durationMs else local.durationMs,
        mediaWidth = if (mediaWidth > 0) mediaWidth else local.mediaWidth,
        mediaHeight = if (mediaHeight > 0) mediaHeight else local.mediaHeight
    )

    fun onComposerTextChanged(text: String) {
        if (text.isBlank()) {
            stopTyping()
            return
        }
        socketManager.emitTyping(conversationId, currentUserId)
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch {
            delay(TYPING_KEEPALIVE_MS)
            stopTyping()
        }
    }

    private fun stopTyping() {
        typingStopJob?.cancel()
        typingStopJob = null
        if (currentUserId.isNotEmpty()) {
            socketManager.emitStoppedTyping(conversationId, currentUserId)
        }
    }

    private fun markRead() {
        val lastIncoming = messages.lastOrNull { !it.isMine && !it.id.startsWith("local-") } ?: return
        if (lastIncoming.id == lastReadMessageId) return
        lastReadMessageId = lastIncoming.id

        socketManager.emitMarkRead(conversationId, lastIncoming.id)
        viewModelScope.launch { repository.markConversationRead(conversationId, lastIncoming.id) }
    }

    private fun replacePending(tempId: String, serverMessage: MessageUi) {
        val index = messages.indexOfFirst { it.id == tempId || it.pendingId == tempId }
        if (index == -1) {
            // Socket may have already promoted the optimistic bubble.
            if (serverMessage.id.isNotBlank() && messages.any { it.id == serverMessage.id }) {
                absorbMatchingOptimistic(serverMessage)
                publish()
                return
            }
            messages.add(serverMessage)
            absorbMatchingOptimistic(serverMessage)
            messages.sortBy { sortKey(it) }
            publish()
            _scrollToBottom.tryEmit(Unit)
            return
        }
        messages[index] = serverMessage
        dedupeByServerId(serverMessage.id, preferredIndex = index)
        absorbMatchingOptimistic(serverMessage)
        messages.sortBy { sortKey(it) }
        publish()
    }

    /** Drops leftover optimistic rows keyed by the same clientMessageId. */
    private fun absorbMatchingOptimistic(serverMessage: MessageUi) {
        if (!serverMessage.isMine || serverMessage.isOptimisticTemporary) return
        val clientId = serverMessage.pendingId?.takeIf { it.isNotBlank() } ?: return
        for (i in messages.lastIndex downTo 0) {
            val row = messages[i]
            if (row.id == serverMessage.id) continue
            if (row.isOptimisticTemporary && (row.id == clientId || row.pendingId == clientId)) {
                messages.removeAt(i)
            }
        }
    }

    private fun dedupeByServerId(serverId: String, preferredIndex: Int = -1) {
        if (serverId.isBlank()) return
        var keep = preferredIndex.takeIf { it in messages.indices && messages[it].id == serverId }
            ?: messages.indexOfFirst { it.id == serverId }
        if (keep < 0) return
        for (i in messages.lastIndex downTo 0) {
            if (i == keep) continue
            if (messages[i].id != serverId) continue
            messages.removeAt(i)
            if (i < keep) keep--
        }
    }

    private fun markFailed(tempId: String) {
        val index = messages.indexOfFirst { it.id == tempId || it.pendingId == tempId }
        if (index == -1) return
        messages[index] = messages[index].copy(
            deliveryState = DeliveryState.FAILED,
            isFailed = true
        )
        publish()
    }

    /** Puts a parked bubble back into the sending state while a retry is in flight. */
    private fun markSending(tempId: String) {
        val index = messages.indexOfFirst { it.id == tempId || it.pendingId == tempId }
        if (index == -1) return
        messages[index] = messages[index].copy(
            deliveryState = DeliveryState.SENDING,
            isFailed = false
        )
        publish()
    }

    private fun observeSocket() {
        viewModelScope.launch {
            // iOS `.chatSocketDidConnect`: resume the queue and refill a stale thread.
            socketManager.reconnected.collect { onSocketReconnected() }
        }
        viewModelScope.launch {
            socketManager.incomingMessages.collect { dto ->
                if (dto.conversationId != conversationId) return@collect
                if (currentUserId.isEmpty()) currentUserId = sessionManager.getUserId()
                onRealtimeMessage(dto)
            }
        }
        viewModelScope.launch {
            socketManager.conversationSettings.collect { event ->
                if (event.conversationId != conversationId) return@collect
                val current = _threadSettings.value
                _threadSettings.value = current.copy(
                    isMuted = event.isMuted ?: current.isMuted,
                    isPinned = event.isPinned ?: current.isPinned,
                    isArchived = event.isArchived ?: current.isArchived,
                    isBlocked = event.isBlocked ?: current.isBlocked
                )
            }
        }
        viewModelScope.launch {
            socketManager.conversationRemoved.collect { event ->
                if (event.conversationId == conversationId) _conversationClosed.tryEmit(Unit)
            }
        }
        viewModelScope.launch {
            socketManager.readReceipts.collect { receipt ->
                if (receipt.conversationId != conversationId) return@collect
                if (receipt.status.equals("delivered", ignoreCase = true)) {
                    applyDeliveryReceipt(listOfNotNull(receipt.messageId))
                } else {
                    applyReadReceipt(receipt.messageId)
                }
            }
        }
        viewModelScope.launch {
            socketManager.deliveryReceipts.collect { event ->
                if (event.conversationId != conversationId) return@collect
                applyDeliveryReceipt(event.messageIds)
            }
        }
        viewModelScope.launch {
            // A forward landed in this thread: pull the new copies in.
            socketManager.forwardedMessages.collect { event ->
                if (conversationId in event.conversationIds) loadInitial()
            }
        }
        viewModelScope.launch {
            // Members added/removed or group metadata changed: re-derive the roster.
            socketManager.groupChanges.collect { event ->
                if (event.conversationId == conversationId) loadInitial()
            }
        }
        viewModelScope.launch {
            socketManager.presenceUpdates.collect { event ->
                if (event.userId == currentUserId) return@collect
                val peer = peerUserId.ifBlank {
                    _members.value.singleOrNull()?.userId.orEmpty()
                }
                if (peer.isNotBlank() && event.userId != peer) return@collect
                _peerPresence.value = PeerPresenceUi(
                    userId = event.userId,
                    isOnline = event.isOnline,
                    lastSeenAt = event.lastSeenAt
                )
            }
        }
        viewModelScope.launch {
            // New senders extend the roster; subscribe their presence once.
            members.collect { subscribePeerPresence() }
        }
        viewModelScope.launch {
            socketManager.typingEvents.collect { event ->
                if (event.conversationId != conversationId) return@collect
                if (event.userId != null && event.userId == currentUserId) return@collect
                _isOtherTyping.value = event.isTyping
                if (event.isTyping) {
                    typingHideJob?.cancel()
                    typingHideJob = viewModelScope.launch {
                        delay(TYPING_HIDE_MS)
                        _isOtherTyping.value = false
                    }
                }
            }
        }
        viewModelScope.launch {
            socketManager.editedMessages.collect { event ->
                if (event.conversationId != null && event.conversationId != conversationId) return@collect
                applyRemoteEdit(event)
            }
        }
        viewModelScope.launch {
            socketManager.deletedMessages.collect { event ->
                if (event.conversationId != null && event.conversationId != conversationId) return@collect
                applyRemoteDelete(event)
            }
        }
        viewModelScope.launch {
            socketManager.reactionUpdates.collect { event ->
                if (event.conversationId != null && event.conversationId != conversationId) return@collect
                applyRemoteReactions(event)
            }
        }
        viewModelScope.launch {
            socketManager.pinUpdates.collect { event ->
                if (event.conversationId != null && event.conversationId != conversationId) return@collect
                applyRemotePin(event)
            }
        }
    }

    /**
     * Runs after a drop/reconnect: queued sends resume, and a thread whose first load
     * failed (offline start) is fetched again now that the socket is back.
     */
    private suspend fun onSocketReconnected() {
        // Presence has to be re-announced on the first handshake as well as on reconnects:
        // the initial `presence:set` can race the socket and be dropped.
        announcePresence()
        subscribePeerPresence()
        if (!sawInitialConnect) {
            sawInitialConnect = true
            return
        }
        if (loadFailed || messages.isEmpty()) {
            loadInitial()
        } else {
            replayOutbox()
        }
    }

    private fun applyRemoteEdit(event: MessageEditedEvent) {
        val index = messages.indexOfFirst { it.id == event.messageId }
        if (index == -1) return
        val body = event.content.takeIf { it.isNotBlank() }
            ?: event.message?.content?.trim().orEmpty()
        messages[index] = messages[index].copy(body = body, isEdited = true)
        updateReplyPreviews(event.messageId, body)
        publish()
    }

    private fun applyRemoteDelete(event: MessageDeletedEvent) {
        val forEveryone = event.deleteType?.equals("me", ignoreCase = true) != true
        var changed = false
        for (id in event.messageIds) {
            val index = messages.indexOfFirst { it.id == id }
            if (index == -1) continue
            if (forEveryone) {
                messages[index] = messages[index].copy(
                    isDeleted = true,
                    body = if (messages[index].isMine) "You deleted this message" else "This message was deleted",
                    reactions = emptyList()
                )
            } else {
                messages.removeAt(index)
            }
            if (_pinnedMessage.value?.id == id) _pinnedMessage.value = null
            changed = true
        }
        if (changed) publish()
    }

    private fun applyRemoteReactions(event: MessageReactionEvent) {
        // Sparse payloads (emoji only) carry no full state — keep the optimistic groups.
        val raw = event.reactions ?: return
        val index = messages.indexOfFirst { it.id == event.messageId }
        if (index == -1) return
        messages[index] = messages[index].copy(reactions = raw.parseReactions(currentUserId))
        publish()
    }

    private fun applyRemotePin(event: MessagePinEvent) {
        if (event.isPinned) {
            for (index in messages.indices) {
                val shouldPin = messages[index].id == event.messageId
                if (messages[index].isPinned != shouldPin) {
                    messages[index] = messages[index].copy(isPinned = shouldPin)
                }
            }
            _pinnedMessage.value = messageById(event.messageId)
        } else {
            val index = messages.indexOfFirst { it.id == event.messageId }
            if (index != -1) messages[index] = messages[index].copy(isPinned = false)
            if (_pinnedMessage.value?.id == event.messageId) _pinnedMessage.value = null
        }
        publish()
    }

    private fun onRealtimeMessage(dto: ChatMessageDto) {
        val incoming = dto.toUi(currentUserId, conversationId)

        // Prefer matching our optimistic bubble — including when server omits clientMessageId.
        val pendingIndex = messages.indexOfFirst { row ->
            val byClientId = !dto.clientMessageId.isNullOrBlank() &&
                (row.pendingId == dto.clientMessageId || row.id == dto.clientMessageId)
            val byOptimisticBody = incoming.isMine &&
                row.isOptimisticTemporary &&
                row.isMine &&
                row.kind == incoming.kind &&
                row.body == incoming.body
            byClientId || byOptimisticBody
        }
        if (pendingIndex != -1) {
            messages[pendingIndex] = incoming
            dedupeByServerId(incoming.id, preferredIndex = pendingIndex)
            absorbMatchingOptimistic(incoming)
            messages.sortBy { sortKey(it) }
            publish()
            return
        }

        if (incoming.id.isNotBlank() && messages.any { it.id == incoming.id }) {
            dedupeByServerId(incoming.id)
            publish()
            return
        }

        messages.add(incoming)
        absorbMatchingOptimistic(incoming)
        messages.sortBy { sortKey(it) }
        oldestLoadedRaw = messages.firstOrNull()?.createdAtRaw ?: oldestLoadedRaw
        publish()
        _scrollToBottom.tryEmit(Unit)

        if (!incoming.isMine) {
            // Scrolled up: count it instead of yanking the list (iOS scrollDownBadge).
            if (!isReaderAtBottom) _newMessagesWhileScrolled.value += 1
            _isOtherTyping.value = false
            markRead()
        }
    }

    private fun applyReadReceipt(messageId: String?) {
        var changed = false
        for (index in messages.indices) {
            val message = messages[index]
            if (!message.isMine || message.deliveryState == DeliveryState.FAILED) continue
            // A receipt covers the referenced message and everything sent before it.
            val upToReceipt = messageId.isNullOrBlank() || message.id == messageId ||
                messages.doesNotContainAfter(message.id, messageId)
            if (upToReceipt && message.deliveryState != DeliveryState.READ) {
                messages[index] = message.copy(deliveryState = DeliveryState.READ)
                changed = true
            }
        }
        if (changed) publish()
    }

    /** Double grey tick: covers the referenced message and every earlier outgoing bubble. */
    private fun applyDeliveryReceipt(messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        var changed = false
        for (index in messages.indices) {
            val message = messages[index]
            if (!message.isMine || message.deliveryState == DeliveryState.FAILED) continue
            if (message.deliveryState == DeliveryState.READ) continue
            val covered = messageIds.any { id ->
                message.id == id || messages.doesNotContainAfter(message.id, id)
            }
            if (covered) {
                messages[index] = message.copy(deliveryState = DeliveryState.DELIVERED)
                changed = true
            }
        }
        if (changed) publish()
    }

    /** True when [messageId] is not present locally, so the receipt applies to every bubble. */
    private fun List<MessageUi>.doesNotContainAfter(messageIdToFind: String, receiptMessageId: String): Boolean {
        val receiptIndex = indexOfFirst { it.id == receiptMessageId }
        if (receiptIndex == -1) return true
        val messageIndex = indexOfFirst { it.id == messageIdToFind }
        return messageIndex != -1 && messageIndex <= receiptIndex
    }

    private fun publish() {
        _rows.value = buildRows(messages)
        publishParticipants()
    }

    /** Rebuilds the sender directory used by the verified badge and the member sheet. */
    private fun publishParticipants() {
        val byId = LinkedHashMap<String, ChatMember>()
        var peerVerified: Boolean? = null
        for (message in messages) {
            val senderId = message.senderId?.takeIf { it.isNotBlank() } ?: continue
            if (senderId == currentUserId) continue
            val existing = byId[senderId]
            val name = message.senderName?.takeIf { it.isNotBlank() }
            val avatar = message.senderAvatarUrl?.takeIf { it.isNotBlank() }
            byId[senderId] = ChatMember(
                userId = senderId,
                name = name ?: existing?.name.orEmpty(),
                avatarUrl = avatar ?: existing?.avatarUrl,
                isVerified = message.senderIsVerified || existing?.isVerified == true
            )
            if (senderId == peerUserId) {
                peerVerified = message.senderIsVerified || peerVerified == true
            }
        }
        _members.value = byId.values.toList()
        // A push deep link may not know the peer id: a single other participant is the peer.
        val resolvedPeer = peerVerified
            ?: byId.values.singleOrNull()
                ?.takeIf { peerUserId.isBlank() || it.userId == peerUserId }
                ?.isVerified
        resolvedPeer?.let { _peerIsVerified.value = it }
    }

    /**
     * Feed from the list scroll listener. Near the bottom the "new messages" badge clears,
     * exactly like iOS `scrollViewDidScroll` → `newMessageCount = 0`.
     */
    fun onThreadScrolled(nearBottom: Boolean) {
        if (isReaderAtBottom == nearBottom) return
        isReaderAtBottom = nearBottom
        if (nearBottom && _newMessagesWhileScrolled.value != 0) {
            _newMessagesWhileScrolled.value = 0
        }
    }

    /** "N new messages" pill tapped (or the list jumped to the tail). */
    fun clearNewMessagesBadge() {
        if (_newMessagesWhileScrolled.value != 0) _newMessagesWhileScrolled.value = 0
    }

    private fun buildRows(source: List<MessageUi>): List<MessageRow> {
        val rows = mutableListOf<MessageRow>()
        var previousDayKey: String? = null
        source.forEach { message ->
            val dayKey = ChatTimeFormatter.dayKey(message.createdAtRaw)
            if (dayKey.isNotEmpty() && dayKey != previousDayKey) {
                rows.add(MessageRow.Separator(ChatTimeFormatter.dateSeparatorLabel(message.createdAtRaw)))
                previousDayKey = dayKey
            }
            rows.add(MessageRow.Message(message))
        }
        return rows
    }

    private fun sortKey(message: MessageUi): Long =
        ChatTimeFormatter.parse(message.createdAtRaw)?.time ?: 0L

    /** Messages sent before the last local “Clear chat” stay hidden on this device. */
    private fun isCleared(message: MessageUi): Boolean =
        clearedBeforeMillis > 0 && sortKey(message) <= clearedBeforeMillis

    override fun onCleared() {
        socketManager.setConversationViewing(conversationId, false)
        socketManager.leaveConversation(conversationId)
        if (currentUserId.isNotBlank()) {
            socketManager.emitPresence("offline", conversationId, currentUserId)
        }
        pendingMedia.clear()
        stopTyping()
        super.onCleared()
    }

    private fun isMatchRequired(error: Resource.Error): Boolean {
        val haystack = listOf(error.message, error.errorBody, error.cause?.message)
            .filterNotNull()
            .joinToString(" ")
            .uppercase()
            .replace(" ", "_")
        return haystack.contains("MATCH_REQUIRED_TO_CHAT")
    }

    private companion object {
        const val PAGE_SIZE = 30
        const val TYPING_KEEPALIVE_MS = 2_000L
        const val TYPING_HIDE_MS = 3_000L
        const val SEARCH_LIMIT = 50
        const val SEARCH_DEBOUNCE_MS = 300L
        const val SEARCH_MAX_BACKFILL_PAGES = 6

        /** Automatic retries before a message parks as "tap to retry" (iOS send queue). */
        const val MAX_AUTO_RETRIES = 3
        const val RETRY_BASE_DELAY_MS = 2_000L
        const val RETRY_MAX_DELAY_MS = 8_000L
        const val SEND_DEBOUNCE_MS = 900L
    }
}
