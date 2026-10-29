package com.flirttime.feature.moments.comments

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.databinding.BottomSheetCommentsBinding
import com.flirttime.feature.moments.MomentsRepository
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class CommentsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCommentsBinding? = null
    private val binding get() = _binding!!

    private val momentsRepository: MomentsRepository by inject()
    private lateinit var commentsAdapter: CommentsAdapter
    private val commentsList = mutableListOf<CommentItem>()
    private var momentId: String = ""

    var onCommentPosted: (() -> Unit)? = null

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
        setupInputActions()
        loadComments()
    }

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

        val displayHeight = resources.displayMetrics.heightPixels
        val defaultHeight = (displayHeight * 0.75).toInt()
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = defaultHeight
        }
    }

    private fun setupKeyboardAndHeightObserver() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val isImeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime())

            val dialog = dialog as? BottomSheetDialog
            val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val displayHeight = resources.displayMetrics.heightPixels
                val defaultHeight = (displayHeight * 0.75).toInt()

                if (isImeVisible && imeInsets.bottom > 0) {
                    bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                        height = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                } else {
                    bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                        height = defaultHeight
                    }
                }
            }
            windowInsets
        }
    }

    private fun setupRecyclerView() {
        commentsAdapter = CommentsAdapter(
            onReplyClick = { _ -> },
            onLikeClick = { _ -> },
            onUserClick = { comment ->
                val targetId = comment.userId ?: comment.id
                if (targetId.isNotBlank()) {
                    dismiss()
                    com.flirttime.core.navigation.AppNavigator.navigateTo(
                        context = requireContext(),
                        destination = com.flirttime.core.navigation.Destination.UserProfile(
                            userId = targetId,
                            username = comment.username,
                            profilePhotoUrl = comment.avatarUrl
                        )
                    )
                }
            }
        )

        binding.rvComments.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = commentsAdapter
        }
    }

    private fun setupInputActions() {
        updateSendButtonState(hasText = false)

        binding.etCommentInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSendButtonState(hasText = !s.isNullOrBlank())
            }
        })

        binding.btnSendComment.setOnClickListener {
            if (binding.btnSendComment.isEnabled) {
                postComment()
            }
        }

        binding.etCommentInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND && binding.btnSendComment.isEnabled) {
                postComment()
                true
            } else {
                false
            }
        }
    }

    private fun updateSendButtonState(hasText: Boolean) {
        binding.btnSendComment.isEnabled = hasText
        binding.ivSendIcon.alpha = if (hasText) 1.0f else 0.5f
    }

    private fun postComment() {
        val text = binding.etCommentInput.text?.toString()?.trim() ?: ""
        if (text.isEmpty() || momentId.isEmpty()) return

        // Optimistic UI insert
        val optimisticComment = CommentItem(
            id = System.currentTimeMillis().toString(),
            username = "You",
            timeAgo = "Just now",
            text = text,
            avatarResId = R.drawable.img_gallery_demo_girl
        )

        commentsList.add(optimisticComment)
        commentsAdapter.submitList(commentsList.toList()) {
            binding.rvComments.smoothScrollToPosition(commentsList.size - 1)
        }

        binding.etCommentInput.text?.clear()
        updateEmptyState()
        onCommentPosted?.invoke()

        // Background API sync
        lifecycleScope.launch {
            momentsRepository.postMomentComment(momentId, text).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        // Reload freshly confirmed comments from server
                        loadComments()
                    }
                    is Resource.Error -> {
                        Toast.makeText(requireContext(), "Failed to post comment: ${resource.message}", Toast.LENGTH_SHORT).show()
                    }
                    is Resource.Loading -> Unit
                }
            }
        }
    }

    private fun loadComments() {
        if (momentId.isEmpty()) {
            updateEmptyState()
            return
        }

        lifecycleScope.launch {
            momentsRepository.getMomentComments(momentId = momentId, page = 1, limit = 50).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        if (commentsList.isEmpty()) {
                            binding.progressBarComments.visibility = View.VISIBLE
                        }
                    }
                    is Resource.Success -> {
                        binding.progressBarComments.visibility = View.GONE
                        commentsList.clear()
                        commentsList.addAll(resource.data)
                        commentsAdapter.submitList(commentsList.toList())
                        updateEmptyState()
                    }
                    is Resource.Error -> {
                        binding.progressBarComments.visibility = View.GONE
                        updateEmptyState()
                    }
                }
            }
        }
    }

    private fun updateEmptyState() {
        binding.tvEmptyComments.visibility = if (commentsList.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CommentsBottomSheet"

        fun newInstance(postId: String = ""): CommentsBottomSheetDialogFragment {
            return CommentsBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString("ARG_POST_ID", postId)
                }
            }
        }
    }
}
