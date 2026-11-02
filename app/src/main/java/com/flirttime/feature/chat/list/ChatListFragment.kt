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
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.FragmentChatBinding
import com.flirttime.feature.chat.model.ConversationUi
import com.flirttime.feature.chat.settings.ChatSettingsSheetFragment
import com.flirttime.feature.chat.socket.ChatConnectionState
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Inbox tab: pinned + regular conversation sections backed by `GET /api/v1/chats`
 * and kept fresh through [com.flirttime.feature.chat.socket.ChatSocketManager].
 */
class ChatListFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatListViewModel by viewModel()

    private lateinit var pinnedAdapter: ConversationAdapter
    private lateinit var regularAdapter: ConversationAdapter
    private lateinit var archivedAdapter: ConversationAdapter

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
        setupAdapters()
        setupSearch()
        setupSwipeActions()
        setupInboxSelection()
        setupSwipeRefresh()
        setupPagination()
        setupConnectionBanner()
        observeViewModel()
    }

    private fun setupAdapters() {
        val onOpen: (ConversationUi) -> Unit = { conversation -> openConversation(conversation) }
        val onLongPress: (ConversationUi) -> Unit = { conversation -> showChatSettings(conversation) }

        pinnedAdapter = ConversationAdapter(onOpen, onLongPress, ::toggleInboxSelection)
        regularAdapter = ConversationAdapter(onOpen, onLongPress, ::toggleInboxSelection)
        archivedAdapter = ConversationAdapter(onOpen, onLongPress, ::toggleInboxSelection)

        binding.rvPinnedChats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = pinnedAdapter
            setHasFixedSize(true)
        }

        binding.rvRegularChats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = regularAdapter
            setHasFixedSize(true)
        }

        binding.rvArchivedChats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = archivedAdapter
            setHasFixedSize(true)
        }

        binding.layoutArchivedHeader.setOnClickListener {
            viewModel.toggleArchivedExpanded()
        }
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
                render(viewModel.visibleConversations())
                renderArchived()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
    }

    /** iOS inbox swipe actions: right = read / unread, left = archive / unarchive. */
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
        // One helper per list: attachToRecyclerView moves the helper rather than cloning it.
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
        binding.btnInboxSelect.isVisible = !active
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

    /** Loads the next inbox page once the user is roughly one screen from the bottom. */
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

    /**
     * Renders the socket state like the iOS connect flow: a hidden banner while live, a
     * soft reminder while reconnecting and an explicit offline notice with a retry action.
     */
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
                    viewModel.conversations.collect {
                        render(viewModel.visibleConversations())
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
                        binding.progressBar.isVisible = isLoading
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

    private fun render(conversations: List<ConversationUi>) {
        if (_binding == null) return

        val pinned = conversations.filter { it.isPinned }
        val regular = conversations.filterNot { it.isPinned }

        pinnedAdapter.submitList(pinned)
        regularAdapter.submitList(regular)

        binding.layoutPinnedSection.isVisible = pinned.isNotEmpty()
        binding.layoutChatsSection.isVisible = regular.isNotEmpty()

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

    /** Collapsible archived bucket; the rows only bind while the group is expanded. */
    private fun renderArchived() {
        if (_binding == null) return

        val archived = viewModel.visibleArchivedConversations()
        val expanded = viewModel.isArchivedExpanded.value

        binding.layoutArchivedSection.isVisible = archived.isNotEmpty()
        binding.tvArchivedCount.text = archived.size.toString()
        binding.ivArchivedChevron.rotation = if (expanded) 0f else -90f
        binding.rvArchivedChats.isVisible = expanded && archived.isNotEmpty()
        archivedAdapter.submitList(if (expanded) archived else emptyList())
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
                ChatSettingsSheetFragment.Action.TOGGLE_ARCHIVE -> viewModel.toggleArchive(conversation)
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
        _binding = null
    }

    private companion object {
        const val PAGINATION_THRESHOLD_DP = 320
    }
}
