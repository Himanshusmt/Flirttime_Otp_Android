package com.flirttime.feature.chat.detail

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.core.common.EdgeToEdgeHelper
import com.flirttime.core.common.MediaUrlHelper
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.ChatMember
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.ActivityChatDetailBinding
import com.flirttime.feature.chat.cache.ChatDraftStore
import com.flirttime.feature.chat.group.GroupMembersSheetFragment
import com.flirttime.feature.chat.media.ChatAudioPlayer
import com.flirttime.feature.chat.media.ChatImageCompressor
import com.flirttime.feature.chat.media.PreparedMedia
import com.flirttime.feature.chat.media.VoiceNoteRecorder
import com.flirttime.feature.chat.model.MessageKind
import com.flirttime.feature.chat.model.MessageRow
import com.flirttime.feature.chat.model.MessageUi
import com.flirttime.feature.chat.model.ReactionUi
import com.flirttime.feature.chat.notification.ChatDeepLink
import com.flirttime.feature.chat.notification.ChatNotificationCenter
import com.flirttime.feature.chat.notification.ChatTarget
import com.flirttime.feature.chat.settings.ChatSettingsSheetFragment
import com.flirttime.feature.chat.socket.ChatConnectionState
import com.flirttime.feature.chat.util.ChatTimeFormatter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

/**
 * Single conversation thread: paginated history, optimistic sends, delivery ticks,
 * typing indicator, photo / voice-note attachments and realtime updates.
 */
class ChatDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatDetailBinding
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var searchAdapter: ChatSearchResultAdapter

    private val conversationId: String by lazy {
        intent.getStringExtra(AppNavigator.EXTRA_CONVERSATION_ID)?.takeIf { it.isNotBlank() }
            ?: ChatDeepLink.conversationIdFromUri(intent.data).orEmpty()
    }

    /** Push payload behind this launch (`flirttime://chat/<id>` or a data push). */
    private val deepLinkTarget: ChatTarget? by lazy { ChatDeepLink.parse(intent) }

    private val peerUserId: String by lazy {
        intent.getStringExtra(AppNavigator.EXTRA_CHAT_PEER_USER_ID).orEmpty()
    }

    private val sessionManager: SessionManager by inject()

    /** Unsent composer text per conversation (iOS `ConversationDraftStore`). */
    private val draftStore: ChatDraftStore by inject()

    private val isGroupChat: Boolean by lazy {
        intent.getBooleanExtra(AppNavigator.EXTRA_CHAT_IS_GROUP, false) ||
            deepLinkTarget?.isGroup == true
    }

    /** Verified flag handed over by the inbox row (the thread itself refines it). */
    private val isVerifiedHint: Boolean by lazy {
        intent.getBooleanExtra(AppNavigator.EXTRA_CHAT_IS_VERIFIED, false) ||
            deepLinkTarget?.isVerified == true
    }

    /** Participants known at navigation time; realtime senders are merged on top. */
    private val intentMembers: List<ChatMember> by lazy {
        runCatching {
            val raw = intent.getStringExtra(AppNavigator.EXTRA_CHAT_MEMBERS)
                ?: return@runCatching emptyList()
            val type = object : TypeToken<List<ChatMember>>() {}.type
            Gson().fromJson<List<ChatMember>>(raw, type)
        }.getOrNull().orEmpty()
    }

    private val initialThreadSettings: ThreadSettings by lazy {
        ThreadSettings(
            isMuted = intent.getBooleanExtra(AppNavigator.EXTRA_CHAT_IS_MUTED, false),
            isPinned = intent.getBooleanExtra(AppNavigator.EXTRA_CHAT_IS_PINNED, false),
            isArchived = intent.getBooleanExtra(AppNavigator.EXTRA_CHAT_IS_ARCHIVED, false),
            isBlocked = intent.getBooleanExtra(AppNavigator.EXTRA_CHAT_IS_BLOCKED, false)
        )
    }

    private val viewModel: ChatDetailViewModel by viewModel {
        parametersOf(conversationId, peerUserId, initialThreadSettings)
    }

    private var subtitleDefault: String? = null
    private var isTypingIndicator = false
    private var presenceText: String? = null
    private var presenceIsOnline = false
    private var screenCaptureCallback: ScreenCaptureCallback? = null
    private var isLoadingNow = false
    private var pendingScrollToBottom = true
    private var isSearchOpen = false

    private val searchBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = closeMessageSearch()
    }

    private var isSelectionMode = false
    private val selectedMessageIds = linkedSetOf<String>()

    private val selectionBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = exitSelectionMode()
    }

    private val voiceRecorder by lazy { VoiceNoteRecorder(applicationContext) }
    private var recordingStartedAt = 0L
    private var recordingTimerJob: Job? = null

    private val audioPlayer by lazy {
        ChatAudioPlayer(
            onStateChanged = { messageId, isPlaying ->
                messageAdapter.setPlaybackState(if (isPlaying) messageId else null, 0f)
            },
            onProgress = { messageId, fraction, _ ->
                messageAdapter.setPlaybackState(messageId, fraction)
            }
        )
    }

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_GALLERY_IMAGES)
    ) { uris ->
        if (uris.isNotEmpty()) sendPickedImages(uris)
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecording()
        } else {
            Toast.makeText(
                this,
                "Microphone permission is required to record a voice note.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = true, isLightNavigationBars = true)

        binding = ActivityChatDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (conversationId.isBlank()) {
            finish()
            return
        }

        // Tapping the conversation's notification opens this thread; clear its tray entry.
        ChatNotificationCenter.cancel(this, conversationId)

        setupInsets()
        setupHeader()
        setupRecyclerView()
        setupComposer()
        setupMessageActions()
        setupSelectionBar()
        setupMessageSearch()
        setupConnectionBanner()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        registerScreenCaptureWatcher()
    }

    override fun onPause() {
        persistDraft()
        super.onPause()
    }

    override fun onStop() {
        unregisterScreenCaptureWatcher()
        super.onStop()
        audioPlayer.stop()
        if (voiceRecorder.isRecording) cancelRecording()
    }

    override fun onDestroy() {
        recordingTimerJob?.cancel()
        audioPlayer.release()
        super.onDestroy()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBar = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

            binding.layoutHeader.updatePadding(top = systemBars.top + 8)
            binding.layoutSelectionBar.updatePadding(top = systemBars.top + 8)
            binding.layoutComposer.updatePadding(bottom = maxOf(navBar.bottom, ime.bottom) + 8)

            windowInsets
        }
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnMore.setOnClickListener { showChatSettings() }
        binding.btnSearch.setOnClickListener { openMessageSearch() }

        binding.tvTitle.text = intent.getStringExtra(AppNavigator.EXTRA_CHAT_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: deepLinkTarget?.title
            ?: "Chat"

        val avatarUrl = MediaUrlHelper.resolveMediaUrl(
            intent.getStringExtra(AppNavigator.EXTRA_CHAT_AVATAR) ?: deepLinkTarget?.avatarUrl
        )
        if (!avatarUrl.isNullOrBlank()) {
            binding.ivAvatar.setImageURI(avatarUrl)
        } else {
            binding.ivAvatar.setActualImageResource(R.drawable.img_login)
        }

        if (isGroupChat) {
            subtitleDefault = groupMemberLabel(intentMembers.size)
        }
        renderSubtitle(isTyping = false)

        // Header opens the peer profile (direct) or the member list (group).
        binding.ivHeaderVerified.isVisible = isVerifiedHint
        val headerClick = View.OnClickListener { openHeaderInfo() }
        binding.ivAvatar.setOnClickListener(headerClick)
        binding.tvTitle.setOnClickListener(headerClick)
        binding.tvSubtitle.setOnClickListener(headerClick)
        binding.ivHeaderVerified.setOnClickListener(headerClick)
    }

    /** iOS nav-bar tap: profile for a direct chat, group detail for a group thread. */
    private fun openHeaderInfo() {
        if (isGroupChat) {
            openGroupMembers()
        } else {
            openPeerProfile()
        }
    }

    private fun openGroupMembers() {
        val merged = (intentMembers + viewModel.members.value)
            .filter { it.userId.isNotBlank() }
            .associateBy { it.userId }
            .values
            .sortedBy { it.name.lowercase(Locale.US) }
        if (merged.isEmpty()) return
        lifecycleScope.launch {
            val currentUserId = sessionManager.getUserId()
            GroupMembersSheetFragment.create(
                title = binding.tvTitle.text?.toString().orEmpty(),
                members = merged,
                currentUserId = currentUserId
            ).show(supportFragmentManager, GroupMembersSheetFragment.TAG)
        }
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(
            onRetry = viewModel::retry,
            isGroupChat = isGroupChat,
            onAudioClick = ::onVoiceNoteClicked,
            onVideoClick = { message -> openAttachment(message, "video/*") },
            onImageClick = { message -> openAttachment(message, "image/*") },
            onLongPress = ::showMessageOptions,
            onSelectToggle = ::toggleMessageSelection,
            onReplyPreviewClick = { _, replyId -> jumpToMessage(replyId) },
            onReactionClick = { _, reaction -> showReactionUsers(reaction) }
        )

        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatDetailActivity).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    viewModel.onThreadScrolled(isNearBottom())
                    renderJumpToLatest(viewModel.newMessagesWhileScrolled.value)
                    if (dy >= 0) return
                    val firstVisible = (recyclerView.layoutManager as? LinearLayoutManager)
                        ?.findFirstVisibleItemPosition() ?: return
                    if (firstVisible <= 2) viewModel.loadOlderMessages()
                }
            })
        }

        binding.btnJumpToLatest.setOnClickListener {
            viewModel.clearNewMessagesBadge()
            scrollToLatest()
        }
    }

    /**
     * Floating "jump to latest" pill: visible whenever the tail is off screen, with the
     * iOS-style unread badge once new messages arrived meanwhile.
     */
    private fun renderJumpToLatest(newCount: Int) {
        val scrolledUp = !isNearBottom()
        binding.layoutJumpToLatest.isVisible = scrolledUp
        binding.tvNewMessagesCount.isVisible = scrolledUp && newCount > 0
        binding.tvNewMessagesCount.text = if (newCount > 9) "9+" else newCount.toString()
    }

    // region In-thread search

    private fun setupMessageSearch() {
        searchAdapter = ChatSearchResultAdapter { message -> onSearchResultSelected(message) }
        binding.rvSearchResults.layoutManager = LinearLayoutManager(this)
        binding.rvSearchResults.adapter = searchAdapter

        binding.etSearchMessages.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                binding.btnClearMessageSearch.isVisible = query.isNotEmpty()
                viewModel.onMessageSearchQueryChanged(query)
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.btnClearMessageSearch.setOnClickListener {
            binding.etSearchMessages.text?.clear()
        }

        onBackPressedDispatcher.addCallback(this, searchBackCallback)
        renderSearchState(viewModel.searchResults.value, viewModel.isSearching.value)
    }

    /** Opens the search panel over the message list and focuses the keyword field. */
    private fun openMessageSearch() {
        if (isSearchOpen) return
        isSearchOpen = true
        searchBackCallback.isEnabled = true
        binding.layoutSearchPanel.isVisible = true
        binding.etSearchMessages.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(binding.etSearchMessages, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun closeMessageSearch() {
        if (!isSearchOpen) return
        isSearchOpen = false
        searchBackCallback.isEnabled = false
        binding.layoutSearchPanel.isVisible = false
        binding.etSearchMessages.text?.clear()
        hideKeyboard()
        viewModel.clearMessageSearch()
    }

    private fun renderSearchState(results: List<MessageUi>, searching: Boolean) {
        val query = viewModel.messageSearchQuery.value
        searchAdapter.submitList(results.map { SearchHit(it, query) })

        binding.progressSearch.isVisible = searching
        binding.rvSearchResults.isVisible = results.isNotEmpty()
        binding.layoutSearchEmpty.isVisible = query.isNotBlank() && results.isEmpty() && !searching
        binding.tvSearchSummary.text = when {
            query.isBlank() -> "Search this conversation"
            searching -> "Searching…"
            results.isEmpty() -> "No messages match \"$query\""
            results.size >= SEARCH_RESULT_CAP -> "$SEARCH_RESULT_CAP+ results"
            results.size == 1 -> "1 result"
            else -> "${results.size} results"
        }
    }

    /** Closes the panel, then scrolls the thread to the tapped hit. */
    private fun onSearchResultSelected(message: MessageUi) {
        closeMessageSearch()
        binding.rvMessages.post { jumpToMessage(message.id) }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    // endregion

    private fun setupComposer() {
        binding.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.onComposerTextChanged(s?.toString().orEmpty())
                updateComposerButtons()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.btnSend.setOnClickListener { sendCurrentMessage() }
        binding.btnAttach.setOnClickListener { launchImagePicker() }
        binding.btnMic.setOnClickListener { onMicClicked() }
        binding.btnCancelRecording.setOnClickListener { cancelRecording() }
        binding.btnSendRecording.setOnClickListener { finishRecording(send = true) }

        updateComposerButtons()
        restoreDraft()
    }

    /** Restores an unsent draft unless the composer already holds text (edit / reply). */
    private fun restoreDraft() {
        lifecycleScope.launch {
            val draft = draftStore.load(conversationId) ?: return@launch
            if (draft.text.isBlank() || !binding.etMessage.text.isNullOrBlank()) return@launch
            binding.etMessage.setText(draft.text)
            binding.etMessage.setSelection(binding.etMessage.text.length)
            updateComposerButtons()
        }
    }

    private fun persistDraft() {
        if (conversationId.isBlank()) return
        val text = binding.etMessage.text?.toString().orEmpty()
        lifecycleScope.launch { draftStore.save(conversationId, text) }
    }

    private fun updateComposerButtons() {
        if (voiceRecorder.isRecording) return
        val hasText = !binding.etMessage.text.isNullOrBlank()
        binding.btnSend.isVisible = hasText
        binding.btnSend.isEnabled = hasText
        binding.btnSend.alpha = if (hasText) 1f else 0.4f
        binding.btnMic.isVisible = !hasText
    }

    private fun sendCurrentMessage() {
        val text = binding.etMessage.text?.toString().orEmpty()
        if (text.isBlank()) return
        binding.etMessage.setText("")
        lifecycleScope.launch { draftStore.clear(conversationId) }
        viewModel.sendMessage(text)
    }

    // region Photos

    private fun launchImagePicker() {
        pickImagesLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    /**
     * Compresses every picked photo like iOS (<= 1600px, JPEG 80) and hands them to the VM
     * one by one, so a multi-image pick becomes a burst of bubbles.
     */
    private fun sendPickedImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        setMediaBusy(true)
        lifecycleScope.launch {
            val prepared = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> prepareImage(uri) }
            }
            setMediaBusy(false)
            if (prepared.isEmpty()) {
                Toast.makeText(
                    this@ChatDetailActivity,
                    "Could not attach those photos",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            prepared.forEach { viewModel.sendMedia(it) }
        }
    }

    private fun prepareImage(uri: Uri): PreparedMedia? {
        val target = File(cacheDir, "chat_image_${System.currentTimeMillis()}_${uri.hashCode()}.jpg")
        val compressed = ChatImageCompressor.compress(this, uri, target) ?: return null
        val bytes = runCatching { compressed.file.readBytes() }.getOrNull()
        if (bytes == null || bytes.isEmpty()) {
            compressed.file.delete()
            return null
        }
        return PreparedMedia(
            kind = KIND_IMAGE,
            fileName = compressed.file.name,
            contentType = CONTENT_TYPE_IMAGE,
            bytes = bytes,
            localPath = compressed.file.absolutePath,
            width = compressed.width,
            height = compressed.height
        )
    }

    private fun setMediaBusy(busy: Boolean) {
        binding.progressBar.isVisible = busy
        binding.btnAttach.isEnabled = !busy
        binding.btnMic.isEnabled = !busy
    }

    // endregion

    // region Voice notes

    private fun onMicClicked() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) startRecording() else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startRecording() {
        if (voiceRecorder.isRecording) return
        audioPlayer.stop()

        if (!voiceRecorder.start()) {
            Toast.makeText(this, "Could not start recording", Toast.LENGTH_SHORT).show()
            return
        }

        recordingStartedAt = SystemClock.elapsedRealtime()
        binding.layoutComposerRow.isVisible = false
        binding.layoutRecording.isVisible = true
        binding.tvRecordingTimer.text = formatClock(0L)
        startRecordingTimer()
    }

    private fun startRecordingTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = lifecycleScope.launch {
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - recordingStartedAt
                binding.tvRecordingTimer.text = formatClock(elapsed)
                if (elapsed >= VoiceNoteRecorder.MAX_DURATION_MS) {
                    finishRecording(send = true)
                    break
                }
                delay(TIMER_TICK_MS)
            }
        }
    }

    private fun finishRecording(send: Boolean) {
        if (!voiceRecorder.isRecording) return
        recordingTimerJob?.cancel()
        recordingTimerJob = null

        val recording = voiceRecorder.stop()
        showComposerRow()

        if (recording == null) {
            if (send) {
                Toast.makeText(this, "Hold a little longer to record", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (!send) {
            recording.file.delete()
            return
        }

        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { recording.file.readBytes() }.getOrNull()
            }
            if (bytes == null || bytes.isEmpty()) {
                Toast.makeText(this@ChatDetailActivity, "Could not read the recording", Toast.LENGTH_SHORT).show()
                return@launch
            }
            viewModel.sendMedia(
                PreparedMedia(
                    kind = KIND_AUDIO,
                    fileName = recording.file.name,
                    contentType = CONTENT_TYPE_AUDIO,
                    bytes = bytes,
                    localPath = recording.file.absolutePath,
                    durationMs = recording.durationMs
                )
            )
        }
    }

    private fun cancelRecording() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        voiceRecorder.cancel()
        showComposerRow()
    }

    private fun showComposerRow() {
        binding.layoutRecording.isVisible = false
        binding.layoutComposerRow.isVisible = true
        updateComposerButtons()
    }

    // endregion

    // region Attachment playback

    private fun onVoiceNoteClicked(message: MessageUi) {
        val source = resolveLocalOrRemote(message)
        if (source.isNullOrBlank()) {
            Toast.makeText(this, "Voice note is still uploading", Toast.LENGTH_SHORT).show()
            return
        }
        audioPlayer.toggle(message.id, source)
    }

    private fun openAttachment(message: MessageUi, mimeType: String) {
        val target = resolveLocalOrRemote(message)
        if (target.isNullOrBlank()) {
            Toast.makeText(this, "Attachment is still uploading", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = if (target.startsWith("http://") || target.startsWith("https://")) {
            Uri.parse(target)
        } else {
            runCatching {
                FileProvider.getUriForFile(this, "$packageName.fileprovider", File(target))
            }.getOrNull()
        }

        if (uri == null) return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, null)) }
            .onFailure { Toast.makeText(this, "No app can open this attachment", Toast.LENGTH_SHORT).show() }
    }

    private fun resolveLocalOrRemote(message: MessageUi): String? =
        message.localUri?.takeIf { it.isNotBlank() }
            ?: MediaUrlHelper.resolveMediaUrl(message.mediaUrl)

    // endregion

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.rows.collect { rows ->
                        messageAdapter.submitList(rows) {
                            if (pendingScrollToBottom) {
                                pendingScrollToBottom = false
                                scrollToLatest()
                            }
                        }
                        updateEmptyState()
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        isLoadingNow = loading
                        binding.progressBar.isVisible = loading
                        updateEmptyState()
                    }
                }
                launch {
                    viewModel.isLoadingMore.collect { loadingMore ->
                        binding.progressBarTop.isVisible = loadingMore
                    }
                }
                launch {
                    viewModel.isOtherTyping.collect { typing -> renderSubtitle(typing) }
                }
                launch {
                    viewModel.scrollToBottom.collect {
                        if (isNearBottom()) {
                            pendingScrollToBottom = true
                            scrollToLatest()
                        }
                    }
                }
                launch {
                    viewModel.errorMessage.collect { message ->
                        Toast.makeText(this@ChatDetailActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
                launch {
                    viewModel.notice.collect { message ->
                        Toast.makeText(this@ChatDetailActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
                launch {
                    viewModel.matchRequiredToChat.collect {
                        com.flirttime.feature.subscription.SubscriptionPopUp.show(
                            this@ChatDetailActivity,
                            com.flirttime.feature.subscription.SubscriptionPopUpType.CHAT
                        )
                    }
                }
                launch {
                    viewModel.replyTarget.collect { target ->
                        renderComposerBanner(viewModel.editTarget.value, target)
                    }
                }
                launch {
                    viewModel.editTarget.collect { target ->
                        renderComposerBanner(target, viewModel.replyTarget.value)
                    }
                }
                launch {
                    viewModel.pinnedMessage.collect { pinned -> renderPinnedBanner(pinned) }
                }
                launch {
                    viewModel.scrollToMessage.collect { messageId -> jumpToMessage(messageId) }
                }
                launch {
                    viewModel.threadSettings.collect { settings -> renderBlocked(settings) }
                }
                launch {
                    viewModel.conversationClosed.collect { finish() }
                }
                launch {
                    viewModel.searchResults.collect { results ->
                        renderSearchState(results, viewModel.isSearching.value)
                    }
                }
                launch {
                    viewModel.isSearching.collect { searching ->
                        renderSearchState(viewModel.searchResults.value, searching)
                    }
                }
                launch {
                    viewModel.messageSearchQuery.collect {
                        renderSearchState(viewModel.searchResults.value, viewModel.isSearching.value)
                    }
                }
                launch {
                    viewModel.connectionState.collect { state -> renderConnectionBanner(state) }
                }
                launch {
                    viewModel.newMessagesWhileScrolled.collect { count ->
                        renderJumpToLatest(count)
                    }
                }
                launch {
                    viewModel.peerIsVerified.collect { verified ->
                        binding.ivHeaderVerified.isVisible = isVerifiedHint || verified
                    }
                }
                launch {
                    viewModel.peerPresence.collect { presence -> renderPresence(presence) }
                }
                launch {
                    // Participants keep arriving with messages; refresh the group count.
                    viewModel.members.collect { members ->
                        if (!isGroupChat) return@collect
                        val count = (intentMembers + members)
                            .mapNotNull { it.userId.takeIf { id -> id.isNotBlank() } }
                            .distinct()
                            .size
                        if (count == 0) return@collect
                        val label = groupMemberLabel(count)
                        if (label != subtitleDefault) {
                            subtitleDefault = label
                            renderSubtitle(isTypingIndicator)
                        }
                    }
                }
            }
        }
    }

    private fun setupConnectionBanner() {
        binding.btnConnectionRetry.setOnClickListener { viewModel.forceReconnect() }
    }

    /**
     * Banner above the thread: hidden while live, "Connecting…" while the socket comes
     * back, and an offline notice (with a retry) once the network is gone.
     */
    private fun renderConnectionBanner(state: ChatConnectionState) {
        binding.layoutConnectionBanner.isVisible = state != ChatConnectionState.CONNECTED
        binding.btnConnectionRetry.isVisible = state == ChatConnectionState.OFFLINE
        binding.tvConnectionText.text = when (state) {
            ChatConnectionState.CONNECTED -> ""
            ChatConnectionState.CONNECTING -> "Reconnecting…"
            ChatConnectionState.OFFLINE -> "You're offline — messages will send when you reconnect"
        }
    }

    /** Blocked threads hide the composer behind an unblock prompt, exactly like iOS. */
    private fun renderBlocked(settings: ThreadSettings) {
        binding.layoutBlockedBanner.isVisible = settings.isBlocked
        binding.layoutComposerRow.isVisible = !settings.isBlocked
        if (settings.isBlocked) {
            binding.layoutComposerBanner.isVisible = false
            if (voiceRecorder.isRecording) cancelRecording()
        } else {
            renderComposerBanner(viewModel.editTarget.value, viewModel.replyTarget.value)
        }
    }

    // region Thread actions

    private fun showChatSettings() {
        val settings = viewModel.threadSettings.value
        val options = ChatSettingsSheetFragment.Options(
            conversationId = conversationId,
            name = binding.tvTitle.text?.toString().orEmpty(),
            avatarUrl = intent.getStringExtra(AppNavigator.EXTRA_CHAT_AVATAR),
            username = intent.getStringExtra(AppNavigator.EXTRA_CHAT_PEER_USERNAME),
            peerUserId = viewModel.peerUserId.takeIf { it.isNotBlank() },
            isGroup = intent.getBooleanExtra(AppNavigator.EXTRA_CHAT_IS_GROUP, false),
            isMuted = settings.isMuted,
            isPinned = settings.isPinned,
            isArchived = settings.isArchived,
            isBlocked = settings.isBlocked
        )
        ChatSettingsSheetFragment
            .create(options, chatSettingsCallbacks)
            .show(supportFragmentManager, ChatSettingsSheetFragment.TAG)
    }

    private val chatSettingsCallbacks = object : ChatSettingsSheetFragment.Callbacks {
        override fun onChatAction(
            options: ChatSettingsSheetFragment.Options,
            action: ChatSettingsSheetFragment.Action
        ) {
            when (action) {
                ChatSettingsSheetFragment.Action.OPEN_PROFILE -> {
                    dismissSettingsSheet()
                    openPeerProfile()
                }
                ChatSettingsSheetFragment.Action.TOGGLE_MUTE -> viewModel.toggleMute()
                ChatSettingsSheetFragment.Action.TOGGLE_PIN -> viewModel.togglePin()
                ChatSettingsSheetFragment.Action.TOGGLE_ARCHIVE -> viewModel.toggleArchive()
                ChatSettingsSheetFragment.Action.MARK_READ -> viewModel.markConversationRead()
                ChatSettingsSheetFragment.Action.MARK_UNREAD -> viewModel.markConversationUnread()
                ChatSettingsSheetFragment.Action.TOGGLE_BLOCK -> {
                    dismissSettingsSheet()
                    if (options.isBlocked) confirmBlockUser() else viewModel.toggleBlock()
                }
                ChatSettingsSheetFragment.Action.CLEAR_CHAT -> {
                    confirmClearChat()
                }
                ChatSettingsSheetFragment.Action.DELETE -> {
                    confirmDeleteConversation()
                }
            }
        }
    }

    private fun confirmClearChat() {
        binding.root.post {
            AlertDialog.Builder(this)
                .setTitle("Clear this chat?")
                .setMessage(
                    "Messages will be removed from this device. This does not delete them " +
                        "for the other person."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ ->
                    binding.etMessage.setText("")
                    binding.layoutComposer.isVisible = true
                    updateComposerButtons()
                    lifecycleScope.launch { draftStore.clear(conversationId) }
                    viewModel.clearChat()
                }
                .show()
        }
    }

    private fun dismissSettingsSheet() {
        val sheet = supportFragmentManager.findFragmentByTag(ChatSettingsSheetFragment.TAG)
        (sheet as? ChatSettingsSheetFragment)?.dismissAllowingStateLoss()
    }

    private fun openPeerProfile() {
        val userId = viewModel.peerUserId
        if (userId.isBlank()) return
        AppNavigator.navigateTo(
            this,
            Destination.UserProfile(
                userId = userId,
                username = intent.getStringExtra(AppNavigator.EXTRA_CHAT_PEER_USERNAME),
                name = binding.tvTitle.text?.toString(),
                profilePhotoUrl = intent.getStringExtra(AppNavigator.EXTRA_CHAT_AVATAR)
            )
        )
    }

    private fun confirmBlockUser() {
        val name = binding.tvTitle.text?.toString().orEmpty()
        binding.root.post {
            AlertDialog.Builder(this)
                .setTitle("Block User?")
                .setMessage("Are you sure you want to block $name?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Block") { _, _ -> viewModel.toggleBlock() }
                .show()
        }
    }

    private fun confirmDeleteConversation() {
        val name = binding.tvTitle.text?.toString().orEmpty()
        binding.root.post {
            AlertDialog.Builder(this)
                .setTitle("Delete Chat?")
                .setMessage(
                    "Are you sure you want to delete the chat with $name? " +
                        "This action cannot be undone."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ -> viewModel.deleteConversation() }
                .show()
        }
    }

    // endregion

    // region Message actions

    private fun setupMessageActions() {
        binding.btnBannerClose.setOnClickListener {
            viewModel.cancelReply()
            viewModel.cancelEdit()
        }
        binding.btnUnpin.setOnClickListener {
            val pinned = viewModel.pinnedMessage.value ?: return@setOnClickListener
            viewModel.setPinned(pinned, pinned = false)
        }
        binding.layoutPinnedBanner.setOnClickListener {
            viewModel.pinnedMessage.value?.let { jumpToMessage(it.id) }
        }
        binding.btnUnblock.setOnClickListener { viewModel.toggleBlock() }
    }

    private fun showMessageOptions(message: MessageUi) {
        val current = viewModel.messageById(message.id) ?: message
        MessageOptionsSheetFragment()
            .bind(
                message = current,
                callbacks = messageOptionsCallbacks,
                canEdit = viewModel.canEdit(current),
                canDeleteForEveryone = viewModel.canDeleteForEveryone(current),
                isPinned = current.isPinned,
                canRetry = current.isFailed,
                isTemporary = current.isOptimisticTemporary
            )
            .show(supportFragmentManager)
    }

    private val messageOptionsCallbacks = object : MessageOptionsSheetFragment.Callbacks {

        override fun onReplyClicked(message: MessageUi) {
            viewModel.startReply(message)
            binding.etMessage.requestFocus()
        }

        override fun onForwardClicked(message: MessageUi) {
            binding.root.post { showForwardPicker(message) }
        }

        override fun onCopyClicked(message: MessageUi) {
            val text = viewModel.copyText(viewModel.messageById(message.id) ?: message)
            if (text.isBlank()) return
            copyToClipboard(text)
        }

        override fun onEditClicked(message: MessageUi) {
            viewModel.startEdit(message)
            binding.etMessage.setText(message.body)
            binding.etMessage.setSelection(binding.etMessage.text.length)
            binding.etMessage.requestFocus()
            updateComposerButtons()
        }

        override fun onPinClicked(message: MessageUi, isPinned: Boolean) {
            if (isPinned) {
                viewModel.setPinned(message, pinned = false)
            } else {
                binding.root.post { showPinDurationPicker(message) }
            }
        }

        override fun onSelectClicked(message: MessageUi) {
            startSelectionMode(message)
        }

        override fun onMessageInfoClicked(message: MessageUi) {
            binding.root.post { showMessageInfo(message) }
        }

        override fun onRetryClicked(message: MessageUi) = viewModel.retry(message)

        override fun onDeleteForMeClicked(message: MessageUi) =
            confirmDelete(message, forEveryone = false)

        override fun onDeleteForEveryoneClicked(message: MessageUi) =
            confirmDelete(message, forEveryone = true)

        override fun onReactClicked(message: MessageUi, emoji: String) {
            viewModel.toggleReaction(viewModel.messageById(message.id) ?: message, emoji)
        }

        override fun onMoreReactionsClicked(message: MessageUi) {
            binding.root.post { showEmojiPicker(message) }
        }

        override fun onReactionUsersClicked(message: MessageUi, reaction: ReactionUi) {
            binding.root.post { showReactionUsers(reaction) }
        }
    }

    // region Copy / message info

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Message", text))
        Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show()
    }

    /** iOS “Message Info”: who read / received this message, from `statuses[]`. */
    private fun showMessageInfo(message: MessageUi) {
        val current = viewModel.messageById(message.id) ?: message
        val me = viewModel.signedInUserId()
        val people = (intentMembers + viewModel.members.value)
            .filter { it.userId.isNotBlank() && it.userId != me }
            .associateBy { it.userId }
            .values
            .sortedBy { it.name.lowercase(Locale.US) }

        val rows = people.map { member ->
            MessageInfoSheetFragment.Row(
                name = member.name.takeIf { it.isNotBlank() } ?: "Member",
                avatarUrl = member.avatarUrl,
                status = viewModel.receiptStatusFor(current, member.userId)
            )
        }.ifEmpty {
            // Direct chats launched without a member payload still report the peer.
            val peerId = viewModel.peerUserId
            if (peerId.isBlank()) {
                emptyList()
            } else {
                listOf(
                    MessageInfoSheetFragment.Row(
                        name = binding.tvTitle.text?.toString()?.takeIf { it.isNotBlank() } ?: "Chat",
                        status = viewModel.receiptStatusFor(current, peerId)
                    )
                )
            }
        }

        MessageInfoSheetFragment.create(messagePreview(current), rows)
            .show(supportFragmentManager, MessageInfoSheetFragment.TAG)
    }

    // endregion

    // region Multi-select

    private fun setupSelectionBar() {
        onBackPressedDispatcher.addCallback(this, selectionBackCallback)
        binding.layoutSelectionBar.isVisible = false
        binding.btnCancelSelection.setOnClickListener { exitSelectionMode() }
        binding.btnSelectionCopy.setOnClickListener { copySelectedMessages() }
        binding.btnSelectionForward.setOnClickListener { forwardSelectedMessages() }
        binding.btnSelectionDelete.setOnClickListener { confirmDeleteSelected() }
    }

    private fun startSelectionMode(message: MessageUi) {
        if (message.isSystem) return
        isSelectionMode = true
        selectedMessageIds.clear()
        selectedMessageIds.add(message.id)
        selectionBackCallback.isEnabled = true
        hideKeyboard()
        binding.etMessage.clearFocus()
        messageAdapter.setSelectionMode(true)
        messageAdapter.setSelectedIds(selectedMessageIds)
        renderSelectionBar()
    }

    private fun toggleMessageSelection(message: MessageUi) {
        if (!isSelectionMode) {
            startSelectionMode(message)
            return
        }
        if (!selectedMessageIds.remove(message.id)) selectedMessageIds.add(message.id)
        if (selectedMessageIds.isEmpty()) {
            exitSelectionMode()
        } else {
            messageAdapter.setSelectedIds(selectedMessageIds)
            renderSelectionBar()
        }
    }

    private fun exitSelectionMode() {
        if (!isSelectionMode) return
        isSelectionMode = false
        selectedMessageIds.clear()
        selectionBackCallback.isEnabled = false
        messageAdapter.setSelectionMode(false)
        renderSelectionBar()
    }

    private fun renderSelectionBar() {
        val active = isSelectionMode
        binding.layoutHeader.isVisible = !active
        binding.layoutSelectionBar.isVisible = active
        binding.layoutComposer.isVisible = !active
        if (!active) return

        binding.tvSelectionCount.text = when (selectedMessageIds.size) {
            1 -> "1 selected"
            else -> "${selectedMessageIds.size} selected"
        }
        val copyable = selectedMessages().any { it.body.isNotBlank() && !it.isDeleted }
        binding.btnSelectionCopy.isEnabled = copyable
        binding.btnSelectionCopy.alpha = if (copyable) 1f else 0.4f
    }

    private fun selectedMessages(): List<MessageUi> =
        selectedMessageIds.mapNotNull { viewModel.messageById(it) }

    private fun copySelectedMessages() {
        val text = selectedMessages()
            .filter { it.body.isNotBlank() && !it.isDeleted }
            .joinToString("\n") { viewModel.copyText(it) }
        if (text.isBlank()) return
        copyToClipboard(text)
        exitSelectionMode()
    }

    private fun forwardSelectedMessages() {
        val targets = selectedMessages()
        if (targets.isEmpty()) return
        ForwardPickerFragment.create { recipientIds ->
            binding.root.post {
                viewModel.forwardMessages(targets, recipientIds)
                exitSelectionMode()
            }
        }.show(supportFragmentManager)
    }

    private fun confirmDeleteSelected() {
        val targets = selectedMessages()
        if (targets.isEmpty()) return
        val countLabel = if (targets.size == 1) "1 message" else "${targets.size} messages"
        val canDeleteForEveryone = targets.all { it.isMine && viewModel.canDeleteForEveryone(it) }

        val builder = AlertDialog.Builder(this)
            .setTitle("Delete $countLabel?")
            .setMessage("This action cannot be undone.")
            .setNegativeButton("Cancel", null)

        if (canDeleteForEveryone) {
            builder.setNeutralButton("Delete for Me") { _, _ ->
                viewModel.deleteMessages(targets, forEveryone = false)
                exitSelectionMode()
            }
            builder.setPositiveButton("Delete for Everyone") { _, _ ->
                viewModel.deleteMessages(targets, forEveryone = true)
                exitSelectionMode()
            }
        } else {
            builder.setPositiveButton("Delete") { _, _ ->
                viewModel.deleteMessages(targets, forEveryone = false)
                exitSelectionMode()
            }
        }
        builder.show()
    }

    // endregion

    private fun confirmDelete(message: MessageUi, forEveryone: Boolean) {
        val detail = if (forEveryone) {
            "This message will be deleted for everyone in this chat."
        } else {
            "This message will be deleted for you only. Other chat members will still be able to see it."
        }

        AlertDialog.Builder(this)
            .setTitle("Delete Message?")
            .setMessage("Are you sure you want to delete this message? This action cannot be undone.\n\n$detail")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                val target = viewModel.messageById(message.id) ?: message
                viewModel.deleteMessage(target, forEveryone)
            }
            .show()
    }

    private fun showPinDurationPicker(message: MessageUi) {
        val sheet = PinDurationSheetFragment()
        sheet.onDurationSelected = { duration ->
            val target = viewModel.messageById(message.id) ?: message
            binding.root.post { viewModel.setPinned(target, pinned = true, pinDuration = duration) }
        }
        sheet.show(supportFragmentManager)
    }

    private fun showEmojiPicker(message: MessageUi) {
        val sheet = EmojiPickerSheetFragment()
        sheet.onEmojiSelected = { emoji ->
            val target = viewModel.messageById(message.id) ?: message
            binding.root.post { viewModel.toggleReaction(target, emoji) }
        }
        sheet.show(supportFragmentManager)
    }

    private fun showForwardPicker(message: MessageUi) {
        val sheet = ForwardPickerFragment.create { recipientIds ->
            val target = viewModel.messageById(message.id) ?: message
            binding.root.post { viewModel.forwardMessage(target, recipientIds) }
        }
        sheet.show(supportFragmentManager)
    }

    private fun showReactionUsers(reaction: ReactionUi) {
        val names = reaction.names.filter { it.isNotBlank() }
        val detail = if (names.isEmpty()) {
            "${reaction.count} ${reaction.emoji}"
        } else {
            "${reaction.emoji}  ${names.joinToString(", ")}"
        }

        AlertDialog.Builder(this)
            .setTitle("Reactions")
            .setMessage(detail)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun renderComposerBanner(editTarget: MessageUi?, replyTarget: MessageUi?) {
        when {
            editTarget != null -> {
                binding.layoutComposerBanner.isVisible = true
                binding.tvComposerBannerTitle.text = "Editing message"
                binding.tvComposerBannerBody.text = messagePreview(editTarget)
            }
            replyTarget != null -> {
                val who = replyTarget.senderName?.takeIf { it.isNotBlank() }
                    ?: if (replyTarget.isMine) "yourself" else "message"
                binding.layoutComposerBanner.isVisible = true
                binding.tvComposerBannerTitle.text = "Replying to $who"
                binding.tvComposerBannerBody.text = messagePreview(replyTarget)
            }
            else -> binding.layoutComposerBanner.isVisible = false
        }
    }

    private fun renderPinnedBanner(pinned: MessageUi?) {
        binding.layoutPinnedBanner.isVisible = pinned != null
        binding.tvPinnedText.text = pinned?.let { messagePreview(it) }.orEmpty()
    }

    private fun messagePreview(message: MessageUi): String {
        val body = message.body.trim()
        if (body.isNotEmpty()) return body
        return when (message.kind) {
            MessageKind.IMAGE -> "Photo"
            MessageKind.VIDEO -> "Video"
            MessageKind.AUDIO -> "Voice message"
            else -> "Message"
        }
    }

    private fun jumpToMessage(messageId: String) {
        val index = messageAdapter.currentList.indexOfFirst {
            it is MessageRow.Message && it.data.id == messageId
        }
        if (index == -1) {
            Toast.makeText(this, "Message is no longer in this chat", Toast.LENGTH_SHORT).show()
            return
        }
        val manager = binding.rvMessages.layoutManager as? LinearLayoutManager
        manager?.scrollToPositionWithOffset(index, binding.rvMessages.height / 3)
    }

    // endregion

    private fun renderSubtitle(isTyping: Boolean) {
        isTypingIndicator = isTyping
        when {
            isTyping -> {
                binding.tvSubtitle.isVisible = true
                binding.tvSubtitle.setTextColor(ContextCompat.getColor(this, R.color.brand_primary))
                binding.tvSubtitle.text = "typing…"
            }
            presenceText != null -> {
                binding.tvSubtitle.isVisible = true
                binding.tvSubtitle.setTextColor(
                    ContextCompat.getColor(
                        this,
                        if (presenceIsOnline) R.color.brand_primary else R.color.text_tertiary
                    )
                )
                binding.tvSubtitle.text = presenceText
            }
            else -> {
                binding.tvSubtitle.isVisible = subtitleDefault != null
                binding.tvSubtitle.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
                binding.tvSubtitle.text = subtitleDefault
            }
        }
    }

    /** iOS header status: "Online" while the peer is present, else the last-seen label. */
    private fun renderPresence(presence: PeerPresenceUi?) {
        if (isGroupChat || presence == null) return
        presenceIsOnline = presence.isOnline
        presenceText = if (presence.isOnline) {
            "Online"
        } else {
            presence.lastSeenAt
                ?.let { ChatTimeFormatter.inboxLabel(it).takeIf(String::isNotBlank) }
                ?.let { "Last seen $it" }
        }
        renderSubtitle(isTypingIndicator)
    }

    /** Android 14+ equivalent of iOS `userDidTakeScreenshotNotification`. */
    private fun registerScreenCaptureWatcher() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (screenCaptureCallback != null) return
        val callback = ScreenCaptureCallback { viewModel.notifyScreenshot() }
        screenCaptureCallback = callback
        registerScreenCaptureCallback(mainExecutor, callback)
    }

    private fun unregisterScreenCaptureWatcher() {
        val callback = screenCaptureCallback ?: return
        screenCaptureCallback = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            unregisterScreenCaptureCallback(callback)
        }
    }

    /** "1 member", "N members", or the generic fallback while nobody is known yet. */
    private fun groupMemberLabel(count: Int): String = when (count) {
        0 -> "Group chat"
        1 -> "1 member"
        else -> "$count members"
    }

    private fun updateEmptyState() {
        binding.layoutEmpty.isVisible = messageAdapter.itemCount == 0 && !isLoadingNow
    }

    private fun scrollToLatest() {
        val lastIndex = messageAdapter.itemCount - 1
        if (lastIndex < 0) return
        binding.rvMessages.scrollToPosition(lastIndex)
        viewModel.clearNewMessagesBadge()
        viewModel.onThreadScrolled(true)
        renderJumpToLatest(0)
    }

    private fun isNearBottom(): Boolean {
        val manager = binding.rvMessages.layoutManager as? LinearLayoutManager ?: return true
        if (messageAdapter.itemCount == 0) return true
        return manager.findLastVisibleItemPosition() >= messageAdapter.itemCount - 3
    }

    private fun formatClock(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    private companion object {
        const val TIMER_TICK_MS = 200L
        const val KIND_IMAGE = "image"
        const val KIND_AUDIO = "audio"
        const val CONTENT_TYPE_IMAGE = "image/jpeg"
        const val CONTENT_TYPE_AUDIO = "audio/mp4"

        /** Gallery multi-pick cap (iOS sends up to 10 picked photos in one go). */
        const val MAX_GALLERY_IMAGES = 10

        /** Must stay in sync with `ChatDetailViewModel.SEARCH_LIMIT`. */
        const val SEARCH_RESULT_CAP = 50
    }
}
