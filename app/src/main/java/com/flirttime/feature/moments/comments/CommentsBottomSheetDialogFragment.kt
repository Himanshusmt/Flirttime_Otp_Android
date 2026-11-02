package com.flirttime.feature.moments.comments

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.core.common.FastImageHelper
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.BottomSheetCommentsBinding
import com.flirttime.feature.moments.MomentsRepository
import com.flirttime.feature.moments.socket.MomentSocketKind
import com.flirttime.feature.moments.socket.MomentSocketUpdate
import com.flirttime.feature.moments.socket.MomentsSocketService
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class CommentsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCommentsBinding? = null
    private val binding get() = _binding!!

    private val momentsRepository: MomentsRepository by inject()
    private val sessionManager: SessionManager by inject()
    private val momentsSocket: MomentsSocketService by inject()
    private lateinit var commentsAdapter: CommentsAdapter
    private val likeInFlight = mutableSetOf<String>()

    private val allComments = mutableListOf<CommentItem>()
    private val expandedIds = mutableSetOf<String>()
    private val loadingRepliesIds = mutableSetOf<String>()
    private var momentId: String = ""
    private var currentUserId: String = ""
    private var currentUserName: String = "You"
    private var currentUserAvatar: String? = null
    private var replyTarget: CommentItem? = null
    private var replyPrefix: String? = null
    private var isSending = false
    private var currentPage = 1
    private var hasMore = false
    private var isLoadingPage = false

    var onCommentPosted: (() -> Unit)? = null
    var onCommentsCountChanged: ((Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        momentId = arguments?.getString("ARG_POST_ID").orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCommentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupKeyboardAndHeightObserver()
        setupRecyclerView()
        setupEmojiBar()
        setupInputActions()
        loadCurrentUser()
        loadComments(page = 1)
        momentsSocket.setup()
        viewLifecycleOwner.lifecycleScope.launch {
            momentsSocket.updates.collect { applySocket(it) }
        }
    }

    fun isForMoment(id: String): Boolean = momentId == id

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        @Suppress("DEPRECATION")
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        bottomSheet.background = ColorDrawable(Color.TRANSPARENT)
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
        val defaultHeight = (resources.displayMetrics.heightPixels * 0.75).toInt()
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply { height = defaultHeight }
    }

    private fun setupKeyboardAndHeightObserver() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val isImeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime())
            val dialog = dialog as? BottomSheetDialog
            val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val defaultHeight = (resources.displayMetrics.heightPixels * 0.75).toInt()
                bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                    height = if (isImeVisible && imeInsets.bottom > 0) {
                        ViewGroup.LayoutParams.MATCH_PARENT
                    } else {
                        defaultHeight
                    }
                }
            }
            windowInsets
        }
    }

    private fun setupRecyclerView() {
        commentsAdapter = CommentsAdapter(
            onReplyClick = { startReply(it) },
            onLikeClick = { toggleLike(it) },
            onUserClick = { openUser(it.userId, it.username, it.avatarUrl) },
            onDeleteClick = { confirmDelete(it) },
            onToggleReplies = { toggleReplies(it) },
            onMentionClick = { openMention(it) }
        )
        binding.rvComments.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = commentsAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    if (commentsAdapter.itemCount == 0) return
                    if (lm.findLastVisibleItemPosition() >= commentsAdapter.itemCount - 3) {
                        loadComments(page = currentPage + 1)
                    }
                }
            })
        }
    }

    private fun setupEmojiBar() {
        val emojis = listOf("❤️", "🔥", "😍", "❤️‍🔥", "😎", "🙌", "😮")
        emojis.forEach { emoji ->
            val tv = TextView(requireContext()).apply {
                text = emoji
                textSize = 22f
                gravity = Gravity.CENTER
                layoutParams = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener { insertEmoji(emoji) }
            }
            binding.layoutEmojiBar.addView(tv)
        }
    }

    private fun setupInputActions() {
        updateSendButtonState(hasText = false)
        binding.etCommentInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty()
                if (text.length > MAX_COMMENT_LENGTH) {
                    binding.etCommentInput.setText(text.take(MAX_COMMENT_LENGTH))
                    binding.etCommentInput.setSelection(MAX_COMMENT_LENGTH)
                }
                updateSendButtonState(hasText = text.isNotBlank())
            }
        })
        binding.btnSendComment.setOnClickListener {
            if (binding.btnSendComment.isEnabled) postComment()
        }
        binding.etCommentInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND && binding.btnSendComment.isEnabled) {
                postComment()
                true
            } else {
                false
            }
        }
        binding.btnCancelReply.setOnClickListener { cancelReply() }
    }

    private fun loadCurrentUser() {
        lifecycleScope.launch {
            currentUserId = sessionManager.getUserId()
            currentUserName = sessionManager.getNickName().ifBlank { sessionManager.getFirstName() }.ifBlank { "You" }
            currentUserAvatar = sessionManager.getProfileImageUri().takeIf { it.isNotBlank() }
            FastImageHelper.loadDirect(
                draweeView = binding.ivCurrentUserAvatar,
                url = currentUserAvatar,
                fallbackRes = R.drawable.img_login
            )
        }
    }

    private fun updateSendButtonState(hasText: Boolean) {
        binding.btnSendComment.isEnabled = hasText && !isSending
        binding.ivSendIcon.alpha = if (hasText && !isSending) 1.0f else 0.5f
    }

    private fun insertEmoji(emoji: String) {
        val field = binding.etCommentInput
        val current = field.text?.toString().orEmpty()
        if (current.length + emoji.length > MAX_COMMENT_LENGTH) return
        val start = field.selectionStart.coerceAtLeast(0)
        val end = field.selectionEnd.coerceAtLeast(0)
        field.text?.replace(minOf(start, end), maxOf(start, end), emoji)
        updateSendButtonState(hasText = !field.text.isNullOrBlank())
    }

    private fun startReply(comment: CommentItem) {
        val name = comment.username
        replyTarget = comment
        binding.tvReplyBanner.text = if (name.isBlank()) "Replying to comment" else "Replying to @$name"
        val mention = if (name.isBlank()) null else "@${name.replace(" ", "")} "
        if (mention != null && binding.etCommentInput.text?.toString()?.startsWith(mention) != true) {
            binding.etCommentInput.setText(mention + binding.etCommentInput.text?.toString().orEmpty())
            binding.etCommentInput.setSelection(binding.etCommentInput.text?.length ?: 0)
        }
        replyPrefix = mention
        binding.layoutReplyBanner.visibility = View.VISIBLE
        binding.etCommentInput.requestFocus()
        updateSendButtonState(hasText = !binding.etCommentInput.text.isNullOrBlank())
    }

    private fun endReply() {
        replyTarget = null
        replyPrefix = null
        binding.layoutReplyBanner.visibility = View.GONE
    }

    private fun cancelReply() {
        val prefix = replyPrefix
        val text = binding.etCommentInput.text?.toString().orEmpty()
        if (prefix != null && text.startsWith(prefix)) {
            binding.etCommentInput.setText(text.removePrefix(prefix))
            binding.etCommentInput.setSelection(binding.etCommentInput.text?.length ?: 0)
        }
        endReply()
        updateSendButtonState(hasText = !binding.etCommentInput.text.isNullOrBlank())
    }

    private fun postComment() {
        val text = binding.etCommentInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty() || momentId.isEmpty() || isSending) return
        val target = replyTarget
        val prefix = replyPrefix
        isSending = true
        updateSendButtonState(hasText = true)
        binding.etCommentInput.text?.clear()
        endReply()

        lifecycleScope.launch {
            val flow = if (target == null) {
                momentsRepository.postMomentComment(momentId, text)
            } else {
                val rootId = rootIdOf(target)
                val parentReplyId = if (target.id == rootId) null else target.id
                momentsRepository.postMomentCommentReply(momentId, rootId, text, parentReplyId)
            }
            flow.collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        isSending = false
                        val created = CommentItem(
                            id = System.currentTimeMillis().toString(),
                            userId = currentUserId,
                            username = currentUserName,
                            handle = currentUserName.replace(" ", ""),
                            timeAgo = "now",
                            text = text,
                            avatarUrl = currentUserAvatar,
                            parentId = target?.let { rootIdOf(it) },
                            isMine = true,
                            isReply = target != null
                        )
                        allComments.add(created)
                        if (target != null) {
                            val rootId = rootIdOf(target)
                            expandedIds.add(rootId)
                            bumpReplyCount(rootId, 1)
                        }
                        refreshDisplay()
                        onCommentPosted?.invoke()
                        onCommentsCountChanged?.invoke(allComments.size)
                    }
                    is Resource.Error -> {
                        isSending = false
                        binding.etCommentInput.setText(text)
                        if (target != null && replyTarget == null) startReply(target, prefix)
                        Toast.makeText(
                            requireContext(),
                            resource.message ?: if (target == null) "Unable to send comment" else "Unable to send reply",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is Resource.Loading -> Unit
                }
                updateSendButtonState(hasText = !binding.etCommentInput.text.isNullOrBlank())
            }
        }
    }

    private fun startReply(comment: CommentItem, prefix: String?) {
        startReply(comment)
        if (prefix != null) replyPrefix = prefix
    }

    private fun toggleLike(comment: CommentItem) {
        val index = allComments.indexOfFirst { it.id == comment.id }
        if (index < 0) return
        val liked = !allComments[index].isLiked
        allComments[index] = allComments[index].copy(
            isLiked = liked,
            likeCount = maxOf(0, allComments[index].likeCount + if (liked) 1 else -1)
        )
        refreshDisplay()
        likeInFlight.add(comment.id)
        lifecycleScope.launch {
            momentsRepository.likeMomentComment(momentId, comment.id, liked).collect { resource ->
                if (resource !is Resource.Loading) likeInFlight.remove(comment.id)
                when (resource) {
                    is Resource.Success -> {
                        val pos = allComments.indexOfFirst { it.id == comment.id }
                        if (pos >= 0) {
                            val serverLiked = resource.data?.isLiked
                            val serverCount = resource.data?.likesCount ?: resource.data?.likeCount
                            allComments[pos] = allComments[pos].copy(
                                isLiked = serverLiked ?: allComments[pos].isLiked,
                                likeCount = serverCount ?: allComments[pos].likeCount
                            )
                            refreshDisplay()
                        }
                    }
                    is Resource.Error -> {
                        val pos = allComments.indexOfFirst { it.id == comment.id }
                        if (pos >= 0) {
                            allComments[pos] = allComments[pos].copy(
                                isLiked = !liked,
                                likeCount = maxOf(0, allComments[pos].likeCount + if (liked) -1 else 1)
                            )
                            refreshDisplay()
                        }
                        Toast.makeText(requireContext(), resource.message ?: "Unable to update like", Toast.LENGTH_SHORT).show()
                    }
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    private fun toggleReplies(comment: CommentItem) {
        val rootId = comment.id
        if (comment.replyCount <= 0 || loadingRepliesIds.contains(rootId)) return
        if (expandedIds.contains(rootId)) {
            expandedIds.remove(rootId)
            refreshDisplay()
            return
        }
        val loadedReplies = allComments.count { rootIdOf(it) == rootId && it.id != rootId }
        if (loadedReplies >= comment.replyCount) {
            expandedIds.add(rootId)
            refreshDisplay()
            return
        }
        loadingRepliesIds.add(rootId)
        refreshDisplay()
        lifecycleScope.launch {
            momentsRepository.getMomentCommentReplies(momentId, rootId).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        loadingRepliesIds.remove(rootId)
                        mergeComments(resource.data)
                        expandedIds.add(rootId)
                        refreshDisplay()
                    }
                    is Resource.Error -> {
                        loadingRepliesIds.remove(rootId)
                        if (loadedReplies > 0) expandedIds.add(rootId)
                        refreshDisplay()
                        Toast.makeText(requireContext(), resource.message ?: "Unable to load replies", Toast.LENGTH_SHORT).show()
                    }
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    private fun confirmDelete(comment: CommentItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete comment?")
            .setMessage("This can't be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> deleteComment(comment) }
            .show()
    }

    private fun deleteComment(comment: CommentItem) {
        lifecycleScope.launch {
            momentsRepository.deleteMomentComment(momentId, comment.id).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        val removedThread = comment.parentId.isNullOrBlank()
                        if (removedThread) {
                            val replyIds = allComments.filter { rootIdOf(it) == comment.id && it.id != comment.id }.map { it.id }.toSet()
                            allComments.removeAll { it.id == comment.id || it.id in replyIds }
                            expandedIds.remove(comment.id)
                        } else {
                            allComments.removeAll { it.id == comment.id }
                            bumpReplyCount(rootIdOf(comment), -1)
                        }
                        if (replyTarget?.id == comment.id) cancelReply()
                        refreshDisplay()
                        onCommentsCountChanged?.invoke(allComments.size)
                    }
                    is Resource.Error -> {
                        Toast.makeText(requireContext(), resource.message ?: "Unable to delete comment", Toast.LENGTH_SHORT).show()
                    }
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    private fun applySocket(update: MomentSocketUpdate) {
        if (update.momentId != momentId) return
        when (update.kind) {
            MomentSocketKind.Deleted -> dismiss()
            MomentSocketKind.CommentCreated -> {
                val comment = update.comment ?: return
                if (allComments.any { it.id == comment.id }) return
                allComments.add(comment)
                val parentId = comment.parentId
                if (!parentId.isNullOrBlank()) {
                    val rootId = allComments.firstOrNull { it.id == parentId }?.let { rootIdOf(it) } ?: parentId
                    expandedIds.add(rootId)
                    bumpReplyCount(rootId, 1)
                }
                refreshDisplay()
                onCommentsCountChanged?.invoke(allComments.size)
            }
            MomentSocketKind.CommentDeleted -> {
                val id = update.commentId ?: update.comment?.id ?: return
                val comment = allComments.firstOrNull { it.id == id } ?: return
                val removedThread = comment.parentId.isNullOrBlank()
                if (removedThread) {
                    val replyIds = allComments.filter { rootIdOf(it) == comment.id && it.id != comment.id }.map { it.id }.toSet()
                    allComments.removeAll { it.id == comment.id || it.id in replyIds }
                    expandedIds.remove(comment.id)
                } else {
                    allComments.removeAll { it.id == comment.id }
                    bumpReplyCount(rootIdOf(comment), -1)
                }
                if (replyTarget?.id == comment.id) cancelReply()
                refreshDisplay()
                onCommentsCountChanged?.invoke(allComments.size)
            }
            MomentSocketKind.CommentLikeUpdated -> {
                val id = update.commentId ?: update.comment?.id ?: return
                if (likeInFlight.contains(id) && update.commentIsLiked == null) return
                val index = allComments.indexOfFirst { it.id == id }
                if (index < 0) return
                val current = allComments[index]
                allComments[index] = current.copy(
                    isLiked = update.commentIsLiked ?: update.comment?.isLiked ?: current.isLiked,
                    likeCount = update.commentLikesCount ?: update.comment?.likeCount ?: current.likeCount
                )
                refreshDisplay()
            }
            else -> Unit
        }
    }

    private fun loadComments(page: Int) {
        if (momentId.isEmpty() || isLoadingPage) return
        if (page > 1 && !hasMore) return
        isLoadingPage = true
        if (page == 1 && allComments.isEmpty()) {
            binding.progressBarComments.visibility = View.VISIBLE
        }
        lifecycleScope.launch {
            momentsRepository.getMomentComments(momentId = momentId, page = page, limit = PAGE_SIZE).collect { resource ->
                when (resource) {
                    is Resource.Loading -> Unit
                    is Resource.Success -> {
                        binding.progressBarComments.visibility = View.GONE
                        if (page == 1) allComments.clear()
                        mergeComments(resource.data)
                        currentPage = page
                        hasMore = resource.data.size >= PAGE_SIZE
                        isLoadingPage = false
                        refreshDisplay()
                        onCommentsCountChanged?.invoke(allComments.size)
                    }
                    is Resource.Error -> {
                        binding.progressBarComments.visibility = View.GONE
                        isLoadingPage = false
                        if (allComments.isEmpty()) {
                            binding.tvEmptyComments.text = resource.message ?: "Unable to load comments"
                            binding.tvEmptyComments.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun mergeComments(incoming: List<CommentItem>) {
        incoming.forEach { item ->
            val marked = item.copy(
                isMine = item.isMine || (!currentUserId.isBlank() && item.userId == currentUserId)
            )
            val existing = allComments.indexOfFirst { it.id == marked.id }
            if (existing >= 0) {
                allComments[existing] = marked.copy(
                    replyCount = maxOf(allComments[existing].replyCount, marked.replyCount)
                )
            } else {
                allComments.add(marked)
            }
        }
    }

    private fun bumpReplyCount(rootId: String, delta: Int) {
        val index = allComments.indexOfFirst { it.id == rootId }
        if (index >= 0) {
            allComments[index] = allComments[index].copy(replyCount = maxOf(0, allComments[index].replyCount + delta))
        }
    }

    private fun rootIdOf(comment: CommentItem): String {
        var current = comment
        val visited = mutableSetOf(comment.id)
        while (!current.parentId.isNullOrBlank()) {
            val parent = allComments.firstOrNull { it.id == current.parentId } ?: return current.parentId.orEmpty().ifBlank { current.id }
            if (!visited.add(parent.id)) break
            current = parent
        }
        return current.parentId?.takeIf { it.isNotBlank() } ?: current.id
    }

    private fun refreshDisplay() {
        val roots = mutableListOf<CommentItem>()
        val repliesByRoot = mutableMapOf<String, MutableList<CommentItem>>()
        val rootIds = allComments.filter { it.parentId.isNullOrBlank() }.map { it.id }.toSet()
        allComments.forEach { comment ->
            val root = rootIdOf(comment)
            if (root == comment.id) {
                roots.add(comment)
            } else if (rootIds.contains(root)) {
                repliesByRoot.getOrPut(root) { mutableListOf() }.add(comment)
            } else {
                roots.add(comment)
            }
        }
        val display = mutableListOf<CommentItem>()
        roots.sortedByDescending { it.createdAt.orEmpty() }.forEach { root ->
            val replies = (repliesByRoot[root.id] ?: emptyList()).sortedBy { it.createdAt.orEmpty() }
            val expanded = expandedIds.contains(root.id)
            display.add(
                root.copy(
                    isReply = false,
                    isExpanded = expanded,
                    isLoadingReplies = loadingRepliesIds.contains(root.id),
                    replyCount = maxOf(root.replyCount, replies.size)
                )
            )
            if (expanded) {
                replies.forEach { display.add(it.copy(isReply = true, parentId = root.id)) }
            }
        }
        commentsAdapter.submitList(display)
        binding.tvEmptyComments.text = "No comments yet.\nStart the conversation."
        binding.tvEmptyComments.visibility = if (display.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openMention(username: String) {
        val handle = username.trim().trimStart('@')
        val match = allComments.firstOrNull { comment ->
            comment.handle.equals(handle, ignoreCase = true) ||
                comment.username.replace(" ", "").equals(handle, ignoreCase = true)
        }
        if (match?.userId.isNullOrBlank()) {
            Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
            return
        }
        openUser(match?.userId, match?.username, match?.avatarUrl)
    }

    private fun openUser(userId: String?, username: String?, avatarUrl: String?) {
        if (userId.isNullOrBlank()) return
        dismiss()
        AppNavigator.navigateTo(
            context = requireContext(),
            destination = Destination.UserProfile(
                userId = userId,
                username = username,
                profilePhotoUrl = avatarUrl
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CommentsBottomSheet"
        private const val PAGE_SIZE = 50
        private const val MAX_COMMENT_LENGTH = 500

        fun newInstance(postId: String = ""): CommentsBottomSheetDialogFragment {
            return CommentsBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString("ARG_POST_ID", postId)
                }
            }
        }
    }
}
