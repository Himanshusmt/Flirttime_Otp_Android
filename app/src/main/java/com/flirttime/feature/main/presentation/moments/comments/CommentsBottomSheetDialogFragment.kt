package com.flirttime.feature.main.presentation.moments.comments

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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.flirttime.R
import com.flirttime.databinding.BottomSheetCommentsBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class CommentsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCommentsBinding? = null
    private val binding get() = _binding!!

    private lateinit var commentsAdapter: CommentsAdapter
    private val commentsList = mutableListOf<CommentItem>()

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
        loadInitialComments()
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

        // Make default sheet container background transparent so our custom rounded shape renders cleanly
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

    /**
     * Dynamically adjusts the bottom sheet height when the software keyboard opens/closes.
     * Prevents the sheet from overflowing off-screen while keeping the input bar directly
     * on top of the keyboard without artificial padding gaps.
     */
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
                    // When keyboard is visible, allow the sheet to fit the visible dialog window
                    bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                        height = ViewGroup.LayoutParams.MATCH_PARENT
                    }
                } else {
                    // When keyboard is closed, keep design height (~75% screen height)
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
            onReplyClick = { comment ->
                // Future nested reply expansion hook
            },
            onLikeClick = { comment ->
                // Future like comment hook
            }
        )

        binding.rvComments.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = commentsAdapter
        }
    }

    private fun setupInputActions() {
        // Initial state: Send button disabled until text entered
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
        if (text.isEmpty()) return

        val newComment = CommentItem(
            id = System.currentTimeMillis().toString(),
            username = "You",
            timeAgo = "Just now",
            text = text,
            avatarResId = R.drawable.img_gallery_demo_girl
        )

        commentsList.add(newComment)
        commentsAdapter.submitList(commentsList.toList()) {
            binding.rvComments.smoothScrollToPosition(commentsList.size - 1)
        }

        binding.etCommentInput.text?.clear()
        updateEmptyState()
    }

    private fun loadInitialComments() {
        // Mock data matching the design screenshot
        commentsList.clear()
        commentsList.add(
            CommentItem(
                id = "c1",
                username = "pawan123",
                timeAgo = "31m",
                text = "Test hpp",
                avatarResId = R.drawable.img_gallery_demo_girl
            )
        )

        commentsAdapter.submitList(commentsList.toList())
        updateEmptyState()
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
