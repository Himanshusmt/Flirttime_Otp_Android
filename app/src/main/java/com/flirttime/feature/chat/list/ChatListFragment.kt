package com.flirttime.feature.chat.list

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.FragmentChatBinding
import com.flirttime.feature.chat.model.CallHistoryUi
import com.flirttime.feature.chat.model.ConversationUi
import com.flirttime.feature.chat.settings.ChatSettingsSheetFragment
import com.flirttime.feature.chat.socket.ChatConnectionState
import com.flirttime.feature.notification.NotificationBadgeBinder
import com.flirttime.feature.notification.NotificationBadgeStore
import com.flirttime.feature.quickmoments.AddNewMomentBottomSheetDialogFragment
import com.flirttime.feature.quickmoments.QuickMomentCardItem
import com.flirttime.feature.quickmoments.QuickMomentsCardAdapter
import com.flirttime.feature.quickmoments.QuickMomentsViewerDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * Inbox tab: Chats | Calls shell matching iOS Messages.
 * Chats keep socket / swipe / multi-select; Calls are REST history with stubbed dial buttons.
 */
class ChatListFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatListViewModel by activityViewModel()
    private val badgeStore: NotificationBadgeStore by inject()

    private lateinit var pinnedAdapter: ConversationAdapter
    private lateinit var regularAdapter: ConversationAdapter
    private lateinit var archivedAdapter: ConversationAdapter
    private lateinit var callHistoryAdapter: CallHistoryAdapter
    private lateinit var quickMomentsAdapter: QuickMomentsCardAdapter

    private var currentQuery: String = ""
    private var isSelectionMode = false
    private val selectedConversationIds = linkedSetOf<String>()

    private val selectionBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = exitSelectionMode()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHeader()
        setupSegmentControl()
        setupAdapters()
        setupQuickMoments()
        setupCallHistory()
        setupSearch()
        setupSwipeActions()
        setupInboxSelection()
        setupSwipeRefresh()
        setupPagination()
        setupConnectionBanner()
        observeViewModel()
        applyInboxSection(viewModel.inboxSection.value)
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadHeaderInfo()
        // Re-bind inbox rows after tab switches: NestedScrollView + wrap_content RVs can
        // miss the first submitList measure pass while the ViewModel already has data.
        if (_binding != null && viewModel.inboxSection.value == ChatInboxSection.CHATS) {
            render(viewModel.visibleConversations())
            renderArchived()
        }
    }

    private fun setupHeader() {
        NotificationBadgeBinder.bind(binding.btnNotification, viewLifecycleOwner, badgeStore)
        binding.btnCoins.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.CoinShop)
        }
        binding.btnNotification.setOnClickListener {
            AppNavigator.navigateTo(requireContext(), Destination.Notification)
        }
    }

    private fun setupSegmentControl() {
        binding.inboxSegment.btnSegmentChats.setOnClickListener {
            viewModel.selectInboxSection(ChatInboxSection.CHATS)
        }
        binding.inboxSegment.btnSegmentCalls.setOnClickListener {
            viewModel.selectInboxSection(ChatInboxSection.CALLS)
        }
        binding.btnDeleteAllCalls.setOnClickListener { confirmDeleteAllCalls() }
    }

    private fun setupAdapters() {
        val onOpen: (ConversationUi) -> Unit = { conversation -> openConversation(conversation) }
        val onLongPress: (ConversationUi) -> Unit = { conversation -> showChatSettings(conversation) }

        pinnedAdapter = ConversationAdapter(onOpen, onLongPress, ::toggleInboxSelection)
        regularAdapter = ConversationAdapter(onOpen, onLongPress, ::toggleInboxSelection)
        archivedAdapter = ConversationAdapter(onOpen, onLongPress, ::toggleInboxSelection)

        // Do not setHasFixedSize(true): these RVs are wrap_content inside NestedScrollView,
        // so height must grow with item count or the first inbox paint stays empty.
        binding.rvPinnedChats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = pinnedAdapter
            isNestedScrollingEnabled = false
        }
        binding.rvRegularChats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = regularAdapter
            isNestedScrollingEnabled = false
        }
        binding.rvArchivedChats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = archivedAdapter
            isNestedScrollingEnabled = false
        }
        binding.layoutArchivedHeader.setOnClickListener {
            viewModel.toggleArchivedExpanded()
        }
    }

    private fun setupQuickMoments() {
        quickMomentsAdapter = QuickMomentsCardAdapter(
            onAddNewClick = { showAddQuickMomentBottomSheet() },
            onStoryClick = { storyCard ->
                val allStories = quickMomentsAdapter.currentList
                    .filterIsInstance<QuickMomentCardItem.StoryCard>()
                val initialIndex = allStories.indexOf(storyCard).coerceAtLeast(0)
                val viewer = QuickMomentsViewerDialogFragment.newInstance(
                    storyList = if (allStories.isNotEmpty()) allStories else listOf(storyCard),
                    initialIndex = initialIndex
                )
                viewer.onDeleteClick = { storyId ->
                    viewModel.deleteQuickMoment(storyId) {
                        Toast.makeText(requireContext(), "Story deleted", Toast.LENGTH_SHORT).show()
                    }
                }
                viewer.onReactClick = { storyId, emoji ->
                    viewModel.reactToQuickMoment(storyId, emoji)
                }
                viewer.onReplyClick = { storyId, message ->
                    viewModel.replyToQuickMoment(storyId, message)
                }
                viewer.show(childFragmentManager, "StoryViewer")
            }
        )
        binding.rvQuickMoments.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = quickMomentsAdapter
            setHasFixedSize(true)
            addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    val density = resources.displayMetrics.density
                    val lp = (view.layoutParams as? RecyclerView.LayoutParams)
                        ?: RecyclerView.LayoutParams(
                            (108 * density).toInt(),
                            (140 * density).toInt()
                        )
                    lp.width = (108 * density).toInt()
                    lp.height = (140 * density).toInt()
                    lp.marginEnd = (8 * density).toInt()
                    view.layoutParams = lp
                }

                override fun onChildViewDetachedFromWindow(view: View) = Unit
            })
        }
    }

    private fun showAddQuickMomentBottomSheet() {
        val sheet = AddNewMomentBottomSheetDialogFragment.newInstance()
        sheet.onMomentCreated = { viewModel.loadQuickMomentsCards(forceRefresh = true) }
        sheet.show(childFragmentManager, AddNewMomentBottomSheetDialogFragment.TAG)
    }

    private fun setupCallHistory() {
        callHistoryAdapter = CallHistoryAdapter(
            onCallActionClick = {
                Toast.makeText(requireContext(), "Coming soon", Toast.LENGTH_SHORT).show()
            },
            onItemLongClick = { call -> confirmDeleteCall(call) }
        )
        binding.rvCallHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = callHistoryAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0) return
                    val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val last = lm.findLastVisibleItemPosition()
                    if (last >= callHistoryAdapter.itemCount - 3) {
                        viewModel.loadMoreCallHistoryIfNeeded()
                    }
                }
            })
        }
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                @Suppress("DEPRECATION")
                val position = viewHolder.adapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val item = callHistoryAdapter.currentList.getOrNull(position) ?: return
                viewModel.deleteCallHistoryItem(item.id)
            }
        }).attachToRecyclerView(binding.rvCallHistory)
    }

    private fun setupSearch() {
        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s?.toString()?.trim().orEmpty()
                binding.btnClearSearch.isVisible = currentQuery.isNotEmpty()
                viewModel.onSearchQueryChanged(currentQuery)
                if (viewModel.inboxSection.value == ChatInboxSection.CHATS) {
                    render(viewModel.visibleConversations())
                    renderArchived()
                } else {
                    renderCalls(viewModel.visibleCallHistory())
                }
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
            viewModel.loadQuickMomentsCards(forceRefresh = true)
        }
        binding.swipeRefreshCalls.setOnRefreshListener {
            viewModel.refreshCallHistory()
        }
    }

    private fun setupSwipeActions() {
        val callback = ChatRowSwipeCallback(
            context = requireContext(),
            onArchiveToggled = { conversation -> viewModel.toggleArchive(conversation) },
            onReadToggled = { conversation ->
                if (conversation.unreadCount > 0) {
                    viewModel.markRead(conversation)
                } else {
                    viewModel.markUnread(conversation)
                }
            }
        )
        ItemTouchHelper(callback).attachToRecyclerView(binding.rvPinnedChats)
        ItemTouchHelper(callback).attachToRecyclerView(binding.rvRegularChats)
        ItemTouchHelper(callback).attachToRecyclerView(binding.rvArchivedChats)
    }

    // region Inbox multi-select

    private fun setupInboxSelection() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, selectionBackCallback)
        binding.layoutInboxSelectionBar.isVisible = false
        binding.btnInboxSelect.setOnClickListener { enterSelectionMode() }
        binding.btnInboxSelectCancel.setOnClickListener { exitSelectionMode() }
        binding.btnInboxSelectAll.setOnClickListener { toggleSelectAll() }
        binding.btnInboxMarkRead.setOnClickListener { markSelectedRead() }
        binding.btnInboxArchive.setOnClickListener { archiveSelected() }
        binding.btnInboxMute.setOnClickListener { muteSelected() }
        binding.btnInboxPin.setOnClickListener { pinSelected() }
        binding.btnInboxDelete.setOnClickListener { confirmDeleteSelected() }
    }

    private fun enterSelectionMode() {
        if (isSelectionMode) return
        if (viewModel.inboxSection.value != ChatInboxSection.CHATS) return
        isSelectionMode = true
        selectedConversationIds.clear()
        selectionBackCallback.isEnabled = true
        adapters().forEach { it.setSelectionMode(true) }
        renderInboxSelection()
    }

    private fun exitSelectionMode() {
        if (!isSelectionMode) return
        isSelectionMode = false
        selectedConversationIds.clear()
        selectionBackCallback.isEnabled = false
        adapters().forEach { it.setSelectionMode(false) }
        renderInboxSelection()
    }

    private fun toggleInboxSelection(conversation: ConversationUi) {
        if (!isSelectionMode) return
        if (!selectedConversationIds.remove(conversation.id)) {
            selectedConversationIds.add(conversation.id)
        }
        adapters().forEach { it.setSelectedIds(selectedConversationIds) }
        renderInboxSelection()
    }

    private fun adapters(): List<ConversationAdapter> =
        listOf(pinnedAdapter, regularAdapter, archivedAdapter)

    private fun toggleSelectAll() {
        val visible = viewModel.visibleConversations() + viewModel.visibleArchivedConversations()
        if (visible.isNotEmpty() && selectedConversationIds.size >= visible.size) {
            selectedConversationIds.clear()
        } else {
            selectedConversationIds.addAll(visible.map { it.id })
        }
        adapters().forEach { it.setSelectedIds(selectedConversationIds) }
        renderInboxSelection()
    }

    private fun renderInboxSelection() {
        val active = isSelectionMode
        binding.layoutInboxSelectionBar.isVisible = active
        binding.layoutSearchBar.isVisible = !active
        binding.btnInboxSelect.isVisible =
            !active && viewModel.inboxSection.value == ChatInboxSection.CHATS
        if (!active) return

        binding.tvInboxSelectionCount.text = when (selectedConversationIds.size) {
            0 -> "Select chats"
            1 -> "1 selected"
            else -> "${selectedConversationIds.size} selected"
        }
        val hasSelection = selectedConversationIds.isNotEmpty()
        listOf(
            binding.btnInboxMarkRead,
            binding.btnInboxArchive,
            binding.btnInboxMute,
            binding.btnInboxPin,
            binding.btnInboxDelete
        ).forEach {
            it.isEnabled = hasSelection
            it.alpha = if (hasSelection) 1f else 0.4f
        }
    }

    private fun selectedConversations(): List<ConversationUi> =
        selectedConversationIds.mapNotNull { viewModel.conversationById(it) }

    private fun markSelectedRead() {
        selectedConversations().forEach { viewModel.markRead(it) }
        exitSelectionMode()
    }

    private fun archiveSelected() {
        selectedConversations().filterNot { it.isArchived }.forEach { viewModel.toggleArchive(it) }
        exitSelectionMode()
    }

    private fun muteSelected() {
        selectedConversations().filterNot { it.isMuted }.forEach { viewModel.toggleMute(it) }
        exitSelectionMode()
    }

    private fun pinSelected() {
        selectedConversations().filterNot { it.isPinned }.forEach { viewModel.togglePin(it) }
        exitSelectionMode()
    }

    private fun confirmDeleteSelected() {
        val targets = selectedConversations()
        if (targets.isEmpty()) return
        val countLabel = if (targets.size == 1) "1 chat" else "${targets.size} chats"
        AlertDialog.Builder(requireContext())
            .setTitle("Delete $countLabel?")
            .setMessage("These chats will be removed from your inbox. This cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                targets.forEach { viewModel.deleteConversation(it) }
                exitSelectionMode()
            }
            .show()
    }

    // endregion

    private fun setupPagination() {
        binding.scrollChats.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { view, _, scrollY, _, _ ->
                val content = view.getChildAt(0) ?: return@OnScrollChangeListener
                val remaining = content.height - (scrollY + view.height)
                if (remaining <= paginationThresholdPx()) viewModel.loadMoreConversations()
            }
        )
    }

    private fun paginationThresholdPx(): Int =
        (PAGINATION_THRESHOLD_DP * resources.displayMetrics.density).toInt()

    private fun setupConnectionBanner() {
        binding.btnConnectionRetry.setOnClickListener { viewModel.forceReconnect() }
    }

    private fun renderConnectionBanner(state: ChatConnectionState) {
        if (_binding == null) return
        binding.layoutConnectionBanner.isVisible = state != ChatConnectionState.CONNECTED
        binding.btnConnectionRetry.isVisible = state == ChatConnectionState.OFFLINE
        binding.tvConnectionText.text = when (state) {
            ChatConnectionState.CONNECTED -> ""
            ChatConnectionState.CONNECTING -> "Connecting…"
            ChatConnectionState.OFFLINE -> "You're offline — showing saved chats"
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.inboxSection.collect { section -> applyInboxSection(section) }
                }
                launch {
                    viewModel.conversations.collect {
                        if (viewModel.inboxSection.value == ChatInboxSection.CHATS) {
                            render(viewModel.visibleConversations())
                        }
                    }
                }
                launch {
                    viewModel.archivedConversations.collect { renderArchived() }
                }
                launch {
                    viewModel.isArchivedExpanded.collect { renderArchived() }
                }
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.progressBar.isVisible =
                            isLoading && viewModel.inboxSection.value == ChatInboxSection.CHATS
                    }
                }
                launch {
                    viewModel.isRefreshing.collect { isRefreshing ->
                        binding.swipeRefresh.isRefreshing = isRefreshing
                    }
                }
                launch {
                    viewModel.isLoadingMoreConversations.collect { isLoadingMore ->
                        binding.progressLoadMore.isVisible = isLoadingMore
                    }
                }
                launch {
                    viewModel.connectionState.collect { state -> renderConnectionBanner(state) }
                }
                launch {
                    viewModel.coinBalance.collectLatest { coins ->
                        binding.tvCoinCount.text = coins.toString()
                    }
                }
                launch {
                    viewModel.quickMomentsCards.collectLatest { resource ->
                        when (resource) {
                            is Resource.Success -> quickMomentsAdapter.submitList(resource.data)
                            is Resource.Error -> {
                                Toast.makeText(
                                    requireContext(),
                                    resource.message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            is Resource.Loading -> Unit
                        }
                    }
                }
                launch {
                    viewModel.callHistory.collect {
                        if (viewModel.inboxSection.value == ChatInboxSection.CALLS) {
                            renderCalls(viewModel.visibleCallHistory())
                        }
                    }
                }
                launch {
                    viewModel.isLoadingCallHistory.collect { loading ->
                        binding.progressCalls.isVisible =
                            loading && callHistoryAdapter.itemCount == 0
                    }
                }
                launch {
                    viewModel.isRefreshingCallHistory.collect { refreshing ->
                        binding.swipeRefreshCalls.isRefreshing = refreshing
                    }
                }
                launch {
                    viewModel.errorMessage.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
                launch {
                    viewModel.notice.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun applyInboxSection(section: ChatInboxSection) {
        if (_binding == null) return
        val chatsSelected = section == ChatInboxSection.CHATS
        if (!chatsSelected && isSelectionMode) exitSelectionMode()

        binding.swipeRefresh.isVisible = chatsSelected
        binding.layoutCallsContent.isVisible = !chatsSelected
        binding.btnInboxSelect.isVisible = chatsSelected && !isSelectionMode
        binding.btnDeleteAllCalls.isVisible =
            !chatsSelected && viewModel.callHistory.value.isNotEmpty()

        styleSegment(chatsSelected)

        if (chatsSelected) {
            render(viewModel.visibleConversations())
            renderArchived()
        } else {
            renderCalls(viewModel.visibleCallHistory())
        }
    }

    private fun styleSegment(chatsSelected: Boolean) {
        val segment = binding.inboxSegment
        val selectedBg = R.drawable.bg_inbox_segment_selected
        val white = ContextCompat.getColor(requireContext(), R.color.white)
        val dark = ContextCompat.getColor(requireContext(), android.R.color.black)

        if (chatsSelected) {
            segment.btnSegmentChats.setBackgroundResource(selectedBg)
            segment.tvSegmentChats.setTextColor(white)
            segment.ivSegmentChats.setColorFilter(white)
            segment.btnSegmentCalls.background = null
            segment.tvSegmentCalls.setTextColor(dark)
            segment.ivSegmentCalls.setColorFilter(dark)
        } else {
            segment.btnSegmentCalls.setBackgroundResource(selectedBg)
            segment.tvSegmentCalls.setTextColor(white)
            segment.ivSegmentCalls.setColorFilter(white)
            segment.btnSegmentChats.background = null
            segment.tvSegmentChats.setTextColor(dark)
            segment.ivSegmentChats.setColorFilter(dark)
        }
    }

    private fun render(conversations: List<ConversationUi>) {
        if (_binding == null) return

        val pinned = conversations.filter { it.isPinned }
        val regular = conversations.filterNot { it.isPinned }

        binding.layoutPinnedSection.isVisible = pinned.isNotEmpty()
        binding.layoutChatsSection.isVisible = regular.isNotEmpty()

        pinnedAdapter.submitList(pinned) { requestInboxLayout() }
        regularAdapter.submitList(regular) { requestInboxLayout() }

        val isEmpty = pinned.isEmpty() && regular.isEmpty() &&
            viewModel.archivedConversations.value.isEmpty()
        binding.layoutEmptySearch.isVisible = isEmpty
        if (isEmpty) {
            val isSearching = currentQuery.isNotEmpty()
            binding.tvEmptyTitle.text =
                if (isSearching) "No chats found" else "No conversations yet"
            binding.tvEmptyMessage.text = if (isSearching) {
                "Check your spelling or search for another name"
            } else {
                "Matches you talk to will show up here"
            }
        }
    }

    /** Force NestedScrollView to remeasure wrap_content chat RVs after async DiffUtil commits. */
    private fun requestInboxLayout() {
        val b = _binding ?: return
        b.rvPinnedChats.requestLayout()
        b.rvRegularChats.requestLayout()
        b.rvArchivedChats.requestLayout()
        b.scrollChats.requestLayout()
    }

    private fun renderArchived() {
        if (_binding == null) return
        if (viewModel.inboxSection.value != ChatInboxSection.CHATS) return

        val archived = viewModel.visibleArchivedConversations()
        val expanded = viewModel.isArchivedExpanded.value

        binding.layoutArchivedSection.isVisible = archived.isNotEmpty()
        binding.tvArchivedCount.text = archived.size.toString()
        binding.ivArchivedChevron.rotation = if (expanded) 0f else -90f
        binding.rvArchivedChats.isVisible = expanded && archived.isNotEmpty()
        archivedAdapter.submitList(if (expanded) archived else emptyList()) {
            requestInboxLayout()
        }
    }

    private fun renderCalls(calls: List<CallHistoryUi>) {
        if (_binding == null) return
        callHistoryAdapter.submitList(calls)
        val empty = calls.isEmpty() && !viewModel.isLoadingCallHistory.value
        binding.layoutEmptyCalls.isVisible = empty
        binding.rvCallHistory.isVisible = !empty
        binding.btnDeleteAllCalls.isVisible =
            viewModel.inboxSection.value == ChatInboxSection.CALLS && calls.isNotEmpty()
    }

    private fun confirmDeleteCall(call: CallHistoryUi) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete call?")
            .setMessage("Remove this call from your history?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteCallHistoryItem(call.id)
            }
            .show()
    }

    private fun confirmDeleteAllCalls() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete all calls?")
            .setMessage("This will clear your entire call history. This cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete All") { _, _ ->
                viewModel.deleteAllCallHistory()
            }
            .show()
    }

    private fun openConversation(conversation: ConversationUi) {
        AppNavigator.navigateTo(
            requireContext(),
            Destination.ChatDetail(
                conversationId = conversation.id,
                title = conversation.title,
                avatarUrl = conversation.avatarUrl,
                isGroup = conversation.isGroup,
                isVerified = conversation.isVerified,
                members = conversation.members,
                peerUserId = conversation.peerUserId,
                peerUsername = conversation.peerUserName,
                isPinned = conversation.isPinned,
                isMuted = conversation.isMuted,
                isArchived = conversation.isArchived,
                isBlocked = conversation.isBlocked
            )
        )
    }

    // region Thread actions

    private fun showChatSettings(conversation: ConversationUi) {
        ChatSettingsSheetFragment
            .create(conversation.toSheetOptions(), settingsCallbacks)
            .show(childFragmentManager, ChatSettingsSheetFragment.TAG)
    }

    private val settingsCallbacks = object : ChatSettingsSheetFragment.Callbacks {
        override fun onChatAction(
            options: ChatSettingsSheetFragment.Options,
            action: ChatSettingsSheetFragment.Action
        ) {
            val conversation = viewModel.conversationById(options.conversationId) ?: return
            when (action) {
                ChatSettingsSheetFragment.Action.OPEN_PROFILE -> {
                    dismissChatSettings()
                    openProfile(conversation)
                }
                ChatSettingsSheetFragment.Action.TOGGLE_MUTE -> viewModel.toggleMute(conversation)
                ChatSettingsSheetFragment.Action.TOGGLE_PIN -> viewModel.togglePin(conversation)
                ChatSettingsSheetFragment.Action.TOGGLE_ARCHIVE ->
                    viewModel.toggleArchive(conversation)
                ChatSettingsSheetFragment.Action.MARK_READ -> viewModel.markRead(conversation)
                ChatSettingsSheetFragment.Action.MARK_UNREAD -> viewModel.markUnread(conversation)
                ChatSettingsSheetFragment.Action.TOGGLE_BLOCK -> {
                    if (options.isBlocked) {
                        dismissChatSettings()
                        binding.root.post { confirmBlock(conversation) }
                    } else {
                        viewModel.toggleBlock(conversation)
                    }
                }
                ChatSettingsSheetFragment.Action.CLEAR_CHAT -> {
                    binding.root.post { confirmClearChat(conversation) }
                }
                ChatSettingsSheetFragment.Action.DELETE -> {
                    binding.root.post { confirmDelete(conversation) }
                }
            }
        }
    }

    private fun dismissChatSettings() {
        val sheet = childFragmentManager.findFragmentByTag(ChatSettingsSheetFragment.TAG)
        (sheet as? ChatSettingsSheetFragment)?.dismissAllowingStateLoss()
    }

    private fun openProfile(conversation: ConversationUi) {
        val userId = conversation.peerUserId ?: return
        AppNavigator.navigateTo(
            requireContext(),
            Destination.UserProfile(
                userId = userId,
                username = conversation.peerUserName,
                name = conversation.title,
                profilePhotoUrl = conversation.avatarUrl
            )
        )
    }

    private fun confirmBlock(conversation: ConversationUi) {
        AlertDialog.Builder(requireContext())
            .setTitle("Block User?")
            .setMessage("Are you sure you want to block ${conversation.title}?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Block") { _, _ -> viewModel.toggleBlock(conversation) }
            .show()
    }

    private fun confirmClearChat(conversation: ConversationUi) {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear this chat?")
            .setMessage(
                "Messages will be removed from this device. This does not delete them for " +
                    "${conversation.title}."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ -> viewModel.clearChat(conversation) }
            .show()
    }

    private fun confirmDelete(conversation: ConversationUi) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Chat?")
            .setMessage(
                "Are you sure you want to delete the chat with ${conversation.title}? " +
                    "This action cannot be undone."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteConversation(conversation) }
            .show()
    }

    private fun ConversationUi.toSheetOptions(): ChatSettingsSheetFragment.Options =
        ChatSettingsSheetFragment.Options(
            conversationId = id,
            name = title,
            avatarUrl = avatarUrl,
            username = peerUserName,
            peerUserId = peerUserId,
            isGroup = isGroup,
            isMuted = isMuted,
            isPinned = isPinned,
            isArchived = isArchived,
            isBlocked = isBlocked,
            unreadCount = unreadCount
        )

    // endregion

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvPinnedChats.adapter = null
        binding.rvRegularChats.adapter = null
        binding.rvArchivedChats.adapter = null
        binding.rvCallHistory.adapter = null
        binding.rvQuickMoments.adapter = null
        _binding = null
    }

    private companion object {
        const val PAGINATION_THRESHOLD_DP = 320
    }
}
