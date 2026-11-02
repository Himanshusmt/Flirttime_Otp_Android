package com.flirttime.feature.chat.list

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flirttime.Resource
import com.flirttime.core.common.AppForegroundTracker
import com.flirttime.core.database.SessionManager
import com.flirttime.feature.chat.ChatRepository
import com.flirttime.feature.chat.cache.ChatCacheStore
import com.flirttime.feature.chat.model.ChatMessageDto
import com.flirttime.feature.chat.model.ConversationUi
import com.flirttime.feature.chat.model.toUi
import com.flirttime.feature.chat.notification.ChatNotificationCenter
import com.flirttime.feature.chat.socket.ChatConnectionState
import com.flirttime.feature.chat.socket.ChatSocketManager
import com.flirttime.feature.chat.util.ChatTimeFormatter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatListViewModel(
    private val repository: ChatRepository,
    private val socketManager: ChatSocketManager,
    private val sessionManager: SessionManager,
    private val cache: ChatCacheStore,
    private val appContext: Context
) : ViewModel() {

    private val _conversations = MutableStateFlow<List<ConversationUi>>(emptyList())
    val conversations: StateFlow<List<ConversationUi>> = _conversations.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _unreadTotal = MutableStateFlow(0)
    val unreadTotal: StateFlow<Int> = _unreadTotal.asStateFlow()

    /** Threads hidden behind the "Archived" group; loaded with `?archived=true`. */
    private val _archivedConversations = MutableStateFlow<List<ConversationUi>>(emptyList())
    val archivedConversations: StateFlow<List<ConversationUi>> =
        _archivedConversations.asStateFlow()

    private val _isArchivedExpanded = MutableStateFlow(false)
    val isArchivedExpanded: StateFlow<Boolean> = _isArchivedExpanded.asStateFlow()

    /** True while an extra inbox page is being fetched for scroll-to-bottom pagination. */
    private val _isLoadingMoreConversations = MutableStateFlow(false)
    val isLoadingMoreConversations: StateFlow<Boolean> =
        _isLoadingMoreConversations.asStateFlow()

    /** One-shot confirmations such as "Chat archived". */
    private val _notice = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notice: SharedFlow<String> = _notice.asSharedFlow()

    private val _errorMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    /** Populated before the first load so socket mapping always knows "me". */
    var currentUserId: String = ""
        private set

    /** Socket / network state used by the reconnect banner. */
    val connectionState: StateFlow<ChatConnectionState> = socketManager.connectionState

    /** Last inbox page handed to the list; page 1 is the refresh payload. */
    private var inboxPage = 1
    private var hasMoreInbox = false

    init {
        socketManager.connect()
        observeIncomingMessages()
        observeConversationChanges()
        observeConversationSettings()
        observeConversationRemoved()
        observeUnreadUpdates()
        observeForwardedMessages()
        observeGroupChanges()
        observeReconnects()
        loadConversations(isRefresh = true)
    }

    fun visibleConversations(): List<ConversationUi> {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) return _conversations.value
        return _conversations.value.filter { conversation ->
            conversation.title.contains(query, ignoreCase = true) ||
                conversation.lastMessagePreview.contains(query, ignoreCase = true)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    /** Freshest copy of a thread, inbox first then the archived bucket. */
    fun conversationById(id: String): ConversationUi? =
        _conversations.value.firstOrNull { it.id == id }
            ?: _archivedConversations.value.firstOrNull { it.id == id }

    fun refresh() = loadConversations(isRefresh = true)

    /** Banner action: rebuild a dead socket and pull the inbox again. */
    fun forceReconnect() {
        socketManager.forceReconnect()
        refresh()
    }

    /** Mirrors iOS `ChatSyncService.loadNextPage`: appends the next page to the inbox. */
    fun loadMoreConversations() {
        if (!hasMoreInbox || _isLoadingMoreConversations.value || _isLoading.value) return
        // A filtered list does not map onto server pages, so paging pauses while searching.
        if (_searchQuery.value.isNotBlank()) return

        viewModelScope.launch {
            _isLoadingMoreConversations.value = true
            if (currentUserId.isEmpty()) currentUserId = sessionManager.getUserId()

            val pageBefore = inboxPage
            val nextPage = pageBefore + 1
            when (val result = repository.getConversations(page = nextPage, limit = INBOX_PAGE_SIZE)) {
                is Resource.Success -> {
                    // A refresh landed while this page was in flight; its rows are stale.
                    if (inboxPage != pageBefore) {
                        _isLoadingMoreConversations.value = false
                        return@launch
                    }
                    val rawRows = result.data
                    val mapped = rawRows.mapNotNull { it.toUi(currentUserId) }
                        .sortedByDescending {
                            ChatTimeFormatter.parse(it.lastMessageAtRaw)?.time ?: 0L
                        }
                    appendArchived(mapped.filter { it.isArchived })

                    val archivedIds = _archivedConversations.value.map { it.id }.toSet()
                    val knownIds = _conversations.value.map { it.id }.toSet()
                    val appended = mapped.filterNot {
                        it.isArchived || it.id in archivedIds || it.id in knownIds
                    }
                    if (appended.isNotEmpty()) {
                        _conversations.value = _conversations.value + appended
                        appended.forEach { socketManager.joinConversation(it.id) }
                        recalcUnread()
                    }

                    inboxPage = nextPage
                    // Same rule as iOS: a short page means the feed is exhausted.
                    hasMoreInbox = rawRows.size >= INBOX_PAGE_SIZE
                }
                is Resource.Error -> _errorMessage.tryEmit(result.message)
                is Resource.Loading -> Unit
            }

            _isLoadingMoreConversations.value = false
        }
    }

    /** Merges archived rows pulled by a later page into the archived bucket. */
    private fun appendArchived(rows: List<ConversationUi>) {
        if (rows.isEmpty()) return
        val merged = (_archivedConversations.value + rows)
            .distinctBy { it.id }
            .sortedByDescending { ChatTimeFormatter.parse(it.lastMessageAtRaw)?.time ?: 0L }
        _archivedConversations.value = merged
    }

    fun toggleArchivedExpanded() {
        _isArchivedExpanded.value = !_isArchivedExpanded.value
    }

    /** Archived rows matching the current search query. */
    fun visibleArchivedConversations(): List<ConversationUi> {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) return _archivedConversations.value
        return _archivedConversations.value.filter { conversation ->
            conversation.title.contains(query, ignoreCase = true) ||
                conversation.lastMessagePreview.contains(query, ignoreCase = true)
        }
    }

    private fun loadConversations(isRefresh: Boolean) {
        if (_isLoading.value) return
        viewModelScope.launch {
            if (isRefresh) {
                _isRefreshing.value = true
            } else {
                _isLoading.value = true
            }

            if (currentUserId.isEmpty()) {
                currentUserId = sessionManager.getUserId()
            }

            when (val result = repository.getConversations(page = 1, limit = INBOX_PAGE_SIZE)) {
                is Resource.Success -> {
                    val mapped = result.data.mapNotNull { it.toUi(currentUserId) }
                        .sortedByDescending { ChatTimeFormatter.parse(it.lastMessageAtRaw)?.time ?: 0L }
                    // Rows flagged as archived never belong in the inbox sections.
                    val archivedIds = mapped.filter { it.isArchived }
                    if (archivedIds.isNotEmpty()) {
                        _archivedConversations.value = archivedIds
                    }
                    val hiddenIds = _archivedConversations.value.map { it.id }.toSet()
                    val visible = mapped.filterNot { it.isArchived || it.id in hiddenIds }
                    _conversations.value = visible
                    _unreadTotal.value = visible.sumOf { it.unreadCount }
                    // Pagination restarts with every refresh, matching iOS `startApiSync`.
                    inboxPage = 1
                    hasMoreInbox = result.data.size >= INBOX_PAGE_SIZE
                    // Subscribe to every visible thread so the inbox updates without opening it.
                    visible.forEach { socketManager.joinConversation(it.id) }
                    cache.saveConversations(visible, archived = false)
                }
                is Resource.Error -> {
                    if (_conversations.value.isEmpty()) {
                        // Offline cold start: show the last successfully loaded inbox.
                        loadCachedConversations()
                        if (_conversations.value.isEmpty()) _errorMessage.tryEmit(result.message)
                    }
                }
                is Resource.Loading -> Unit
            }

            if (isRefresh) loadArchivedConversations()

            _isLoading.value = false
            _isRefreshing.value = false
        }
    }

    /** Fetches the archived bucket separately; servers that ignore the flag return nothing extra. */
    private fun loadArchivedConversations() {
        viewModelScope.launch {
            when (val result = repository.getConversations(page = 1, limit = INBOX_PAGE_SIZE, archived = true)) {
                is Resource.Success -> {
                    val mapped = result.data.mapNotNull { it.toUi(currentUserId) }
                        .sortedByDescending { ChatTimeFormatter.parse(it.lastMessageAtRaw)?.time ?: 0L }
                    // `?archived=true` is authoritative, so every returned row belongs to the
                    // bucket even when the payload does not repeat the flag.
                    val flagged = mapped.filter { it.isArchived }
                    val archived = (if (flagged.isNotEmpty()) flagged else mapped)
                        .map { it.copy(isArchived = true, isPinned = false) }
                    _archivedConversations.value = archived
                    if (archived.isEmpty()) _isArchivedExpanded.value = false
                    cache.saveConversations(archived, archived = true)
                }
                is Resource.Error, is Resource.Loading -> Unit
            }
        }
    }

    /** Restores the cached inbox page when the first network call fails (offline start). */
    private suspend fun loadCachedConversations() {
        val cached = cache.loadConversations(archived = false)
        if (cached.isNotEmpty() && _conversations.value.isEmpty()) {
            _conversations.value = cached
            _unreadTotal.value = cached.sumOf { it.unreadCount }
            cached.forEach { socketManager.joinConversation(it.id) }
        }
        val cachedArchived = cache.loadConversations(archived = true)
        if (cachedArchived.isNotEmpty() && _archivedConversations.value.isEmpty()) {
            _archivedConversations.value = cachedArchived
        }
    }

    // region Thread actions

    fun togglePin(conversation: ConversationUi) {
        applySettings(conversation, isPinned = !conversation.isPinned)
    }

    fun toggleMute(conversation: ConversationUi) {
        applySettings(conversation, isMuted = !conversation.isMuted)
    }

    fun toggleArchive(conversation: ConversationUi) {
        val wasArchived = conversation.isArchived
        applySettings(conversation, isArchived = !wasArchived)
        _notice.tryEmit(if (wasArchived) "Chat moved back to your inbox" else "Chat archived")
    }

    fun markRead(conversation: ConversationUi) {
        if (conversation.unreadCount == 0) return
        patchRow(conversation.id) { it.copy(unreadCount = 0) }
        recalcUnread()
        socketManager.emitMarkRead(conversation.id, null)
        viewModelScope.launch {
            when (val result = repository.markConversationRead(conversation.id, null)) {
                is Resource.Error -> {
                    replaceRow(conversation)
                    _errorMessage.tryEmit(result.message)
                }
                else -> Unit
            }
        }
    }

    fun markUnread(conversation: ConversationUi) {
        if (conversation.unreadCount > 0) return
        patchRow(conversation.id) { it.copy(unreadCount = 1) }
        recalcUnread()
        viewModelScope.launch {
            when (val result = repository.markConversationUnread(conversation.id)) {
                is Resource.Error -> {
                    replaceRow(conversation)
                    _errorMessage.tryEmit(result.message)
                }
                else -> Unit
            }
        }
    }

    fun deleteConversation(conversation: ConversationUi, forEveryone: Boolean = false) {
        removeRow(conversation.id)
        socketManager.emitDeleteConversation(
            conversationId = conversation.id,
            deleteType = if (forEveryone) "everyone" else "me"
        )
        viewModelScope.launch {
            when (val result = repository.deleteConversation(conversation.id, forEveryone)) {
                is Resource.Error -> {
                    _errorMessage.tryEmit(result.message)
                    loadConversations(isRefresh = true)
                }
                else -> Unit
            }
        }
    }

    /** “Clear chat” from the inbox sheet: drops the cached transcript and row preview. */
    fun clearChat(conversation: ConversationUi) {
        patchRow(conversation.id) {
            it.copy(
                lastMessagePreview = "",
                lastMessageAtRaw = null,
                timestampLabel = "",
                unreadCount = 0,
                isLastMessageFromMe = false,
                isLastMessageRead = false
            )
        }
        viewModelScope.launch {
            cache.clearMessages(conversation.id)
            cache.clearOutbox(conversation.id)
        }
        _notice.tryEmit("Chat cleared")
    }

    fun toggleBlock(conversation: ConversationUi) {
        val userId = conversation.peerUserId
        if (userId.isNullOrBlank()) {
            _errorMessage.tryEmit("Blocking is only available in direct chats")
            return
        }
        val blocking = !conversation.isBlocked
        patchRow(conversation.id) { it.copy(isBlocked = blocking) }
        socketManager.emitConversationSettings(conversation.id, isBlocked = blocking)
        viewModelScope.launch {
            val result = if (blocking) {
                repository.blockUser(userId, conversation.id)
            } else {
                repository.unblockUser(userId)
            }
            if (result is Resource.Error) {
                replaceRow(conversation)
                _errorMessage.tryEmit(result.message)
            }
        }
    }

    /**
     * Optimistic settings update mirroring iOS `applyConversationSettings`: patch the row,
     * emit the socket event, then persist over REST and roll back on failure.
     */
    private fun applySettings(
        conversation: ConversationUi,
        isMuted: Boolean? = null,
        isPinned: Boolean? = null,
        isArchived: Boolean? = null
    ) {
        patchRow(conversation.id) { row ->
            row.copy(
                isMuted = isMuted ?: row.isMuted,
                isPinned = isPinned ?: row.isPinned,
                isArchived = isArchived ?: row.isArchived
            )
        }
        rebalance(conversation.id)
        if (isArchived == true) _isArchivedExpanded.value = true
        recalcUnread()

        socketManager.emitConversationSettings(
            conversationId = conversation.id,
            isMuted = isMuted,
            isPinned = isPinned,
            isArchived = isArchived
        )
        if (isArchived != null) {
            socketManager.emitArchiveConversation(conversation.id, isArchived)
        }

        viewModelScope.launch {
            val result = repository.updateConversationSettings(
                conversationId = conversation.id,
                isMuted = isMuted,
                isPinned = isPinned,
                isArchived = isArchived
            )
            if (result is Resource.Error) {
                replaceRow(conversation)
                _errorMessage.tryEmit(result.message)
            }
        }
    }

    private fun patchRow(id: String, transform: (ConversationUi) -> ConversationUi) {
        _conversations.update { list -> list.map { if (it.id == id) transform(it) else it } }
        _archivedConversations.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun replaceRow(conversation: ConversationUi) {
        patchRow(conversation.id) { conversation }
        rebalance(conversation.id)
        recalcUnread()
    }

    private fun removeRow(id: String) {
        _conversations.update { list -> list.filterNot { it.id == id } }
        _archivedConversations.update { list -> list.filterNot { it.id == id } }
        recalcUnread()
    }

    /** Moves a row between the inbox and the archived bucket after its flags changed. */
    private fun rebalance(id: String) {
        val inInbox = _conversations.value.firstOrNull { it.id == id }
        val inArchived = _archivedConversations.value.firstOrNull { it.id == id }
        val row = inInbox ?: inArchived ?: return
        if (row.isArchived) {
            if (inInbox != null) {
                _conversations.update { list -> list.filterNot { it.id == id } }
            }
            if (inArchived == null) {
                _archivedConversations.update { list -> listOf(row) + list }
            }
        } else {
            if (inArchived != null) {
                _archivedConversations.update { list -> list.filterNot { it.id == id } }
            }
            if (inInbox == null) {
                _conversations.update { list -> listOf(row) + list }
            }
        }
    }

    private fun recalcUnread() {
        _unreadTotal.value = _conversations.value.sumOf { it.unreadCount }
    }

    // endregion

    private fun observeIncomingMessages() {
        viewModelScope.launch {
            socketManager.incomingMessages.collect { dto ->
                if (currentUserId.isEmpty()) return@collect
                val conversationId = dto.conversationId ?: return@collect
                val senderId = dto.sender?.id ?: dto.senderId
                val isMine = !senderId.isNullOrEmpty() && senderId == currentUserId
                val timestamp = dto.createdAt ?: dto.sentAt

                _conversations.update { current ->
                    val index = current.indexOfFirst { it.id == conversationId }
                    if (index == -1) return@update current

                    val existing = current[index]
                    val updated = existing.copy(
                        lastMessagePreview = dto.content?.trim()?.takeIf { it.isNotEmpty() }
                            ?: existing.lastMessagePreview,
                        lastMessageAtRaw = timestamp ?: existing.lastMessageAtRaw,
                        timestampLabel = ChatTimeFormatter.inboxLabel(timestamp),
                        unreadCount = if (isMine) existing.unreadCount else existing.unreadCount + 1,
                        isLastMessageFromMe = isMine,
                        isLastMessageRead = false
                    )
                    // Bump the thread to the top of its section, matching iOS behaviour.
                    val without = current.toMutableList().apply { removeAt(index) }
                    without.add(0, updated)
                    without
                }
                _unreadTotal.value = _conversations.value.sumOf { it.unreadCount }

                // Background arrivals become a deep-linked notification (iOS push parity).
                if (!isMine &&
                    !AppForegroundTracker.isForeground &&
                    !socketManager.isViewingConversation(conversationId)
                ) {
                    notifyBackgroundMessage(conversationId, dto)
                }
            }
        }
    }

    private fun notifyBackgroundMessage(conversationId: String, dto: ChatMessageDto) {
        val row = _conversations.value.firstOrNull { it.id == conversationId }
        val preview = dto.content?.trim()
            ?.takeIf { it.isNotEmpty() && !it.startsWith("http", ignoreCase = true) }
            ?: if (dto.media.isNullOrEmpty()) "Sent you a message" else "Sent you an attachment"
        ChatNotificationCenter.notifyMessage(
            context = appContext,
            conversationId = conversationId,
            title = row?.title?.takeIf { it.isNotBlank() } ?: "New message",
            body = preview,
            avatarUrl = row?.avatarUrl,
            isGroup = row?.isGroup == true,
            isVerified = row?.isVerified == true,
            unreadCount = row?.unreadCount ?: 1
        )
    }

    private fun observeConversationChanges() {
        viewModelScope.launch {
            socketManager.conversationChanges.collect {
                loadConversations(isRefresh = true)
            }
        }
    }

    /** Mute / pin / archive / block fan-out from other devices (and from this one). */
    private fun observeConversationSettings() {
        viewModelScope.launch {
            socketManager.conversationSettings.collect { event ->
                val known = _conversations.value.firstOrNull { it.id == event.conversationId }
                    ?: _archivedConversations.value.firstOrNull { it.id == event.conversationId }
                if (known == null) {
                    loadConversations(isRefresh = true)
                    return@collect
                }
                patchRow(event.conversationId) { row ->
                    row.copy(
                        isMuted = event.isMuted ?: row.isMuted,
                        isPinned = event.isPinned ?: row.isPinned,
                        isArchived = event.isArchived ?: row.isArchived,
                        isBlocked = event.isBlocked ?: row.isBlocked
                    )
                }
                rebalance(event.conversationId)
                recalcUnread()
            }
        }
    }

    private fun observeConversationRemoved() {
        viewModelScope.launch {
            socketManager.conversationRemoved.collect { event ->
                removeRow(event.conversationId)
            }
        }
    }

    /**
     * `conversation:unread_updated` — the server is authoritative, so a device that already
     * read the thread never shows a badge pushed to the others.
     */
    private fun observeUnreadUpdates() {
        viewModelScope.launch {
            socketManager.unreadUpdates.collect { event ->
                val known = _conversations.value.any { it.id == event.conversationId } ||
                    _archivedConversations.value.any { it.id == event.conversationId }
                if (!known) {
                    loadConversations(isRefresh = true)
                    return@collect
                }
                val unread = if (socketManager.isViewingConversation(event.conversationId)) {
                    0
                } else {
                    event.unreadCount
                }
                patchRow(event.conversationId) { it.copy(unreadCount = unread) }
                recalcUnread()
            }
        }
    }

    /** `chat:message_forwarded` — the forwarded copy changes a row's preview and order. */
    private fun observeForwardedMessages() {
        viewModelScope.launch {
            socketManager.forwardedMessages.collect {
                loadConversations(isRefresh = true)
            }
        }
    }

    /** `chat:member:*` / `chat:group:updated` — new groups appear without a pull-to-refresh. */
    private fun observeGroupChanges() {
        viewModelScope.launch {
            socketManager.groupChanges.collect {
                loadConversations(isRefresh = true)
            }
        }
    }

    /** iOS refreshes the inbox once the socket handshake completes after a drop. */
    private fun observeReconnects() {
        viewModelScope.launch {
            socketManager.reconnected.collect {
                loadConversations(isRefresh = true)
            }
        }
    }

    private companion object {
        /** Matches the page size already used for the inbox and the archived bucket. */
        const val INBOX_PAGE_SIZE = 40
    }
}
