package com.flirttime.feature.quickmoments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.flirttime.R
import com.flirttime.core.common.MediaUrlHelper
import com.flirttime.databinding.DialogQuickMomentViewerBinding
import com.flirttime.feature.main.matches.MatchesRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class QuickMomentsViewerDialogFragment : DialogFragment() {

    private var _binding: DialogQuickMomentViewerBinding? = null
    private val binding get() = _binding!!

    private val matchesRepository: MatchesRepository by inject()

    var storyCard: QuickMomentCardItem.StoryCard? = null
    var onDeleteClick: ((String) -> Unit)? = null
    var onReactClick: ((storyId: String, emoji: String) -> Unit)? = null
    var onReplyClick: ((storyId: String, message: String) -> Unit)? = null

    private var storyAnimator: ValueAnimator? = null
    private var isStoryPaused: Boolean = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.let { window ->
            window.requestFeature(Window.FEATURE_NO_TITLE)
            window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogQuickMomentViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val item = storyCard ?: run {
            dismiss()
            return
        }

        // Apply Window Insets so top bar/avatar are not cut off by status bar/notch
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootViewer) { _, windowInsets ->
            val statusBarInset = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarInset = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val density = resources.displayMetrics.density
            val extraTopPad = (8 * density).toInt()
            val extraBottomPad = (16 * density).toInt()

            binding.layoutViewerTopContainer.updatePadding(top = statusBarInset + extraTopPad)
            binding.layoutViewerBottomContainer.updatePadding(bottom = navBarInset + extraBottomPad)
            windowInsets
        }

        binding.btnViewerClose.setOnClickListener {
            stopStoryTimeline()
            dismiss()
        }

        // Author Header
        binding.tvViewerAuthorName.text = item.userName
        binding.tvViewerStoryTime.text = "${item.relativeAge ?: "1m"} ago"
        val resolvedAvatar = MediaUrlHelper.resolveMediaUrl(item.userAvatar)
        if (!resolvedAvatar.isNullOrBlank()) {
            binding.ivViewerAuthorAvatar.setImageURI(resolvedAvatar)
        } else {
            binding.ivViewerAuthorAvatar.setActualImageResource(R.drawable.ic_settings_person)
        }

        // Story Content
        val resolvedMedia = MediaUrlHelper.resolveMediaUrl(item.mediaUrl)
        if (!resolvedMedia.isNullOrBlank()) {
            binding.ivViewerMedia.visibility = View.VISIBLE
            binding.layoutViewerTextBackground.visibility = View.GONE
            binding.ivViewerMedia.setImageURI(resolvedMedia)
        } else {
            binding.ivViewerMedia.visibility = View.GONE
            binding.layoutViewerTextBackground.visibility = View.VISIBLE
            binding.tvViewerTextContent.text = item.textContent.orEmpty()

            if (!item.backgroundColor.isNullOrBlank()) {
                try {
                    val color = Color.parseColor(item.backgroundColor)
                    val drawable = GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(color, color)
                    )
                    binding.layoutViewerTextBackground.background = drawable
                } catch (_: Exception) {
                    binding.layoutViewerTextBackground.setBackgroundResource(R.drawable.bg_gradient_story_red)
                }
            } else {
                binding.layoutViewerTextBackground.setBackgroundResource(R.drawable.bg_gradient_story_red)
            }
        }

        // Top 3-Dots Menu (...)
        binding.btnViewerMenu.setOnClickListener {
            pauseStoryTimeline()
            val menuSheet = StoryMenuBottomSheetDialogFragment.newInstance(
                isOwnStory = item.isOwnStory,
                storyId = item.storyId,
                userName = item.userName,
                userId = item.userId.orEmpty()
            ).apply {
                onDeleteConfirmed = { sId ->
                    onDeleteClick?.invoke(sId)
                    this@QuickMomentsViewerDialogFragment.dismiss()
                }
            }
            menuSheet.dialog?.setOnDismissListener {
                resumeStoryTimeline()
            }
            menuSheet.show(childFragmentManager, "story_menu")
        }

        // Bottom Actions based on Own Story vs Other User Story
        if (item.isOwnStory) {
            binding.layoutOwnStoryActions.visibility = View.VISIBLE
            binding.layoutOtherStoryActions.visibility = View.GONE

            val views = item.viewCount ?: item.originalSlide?.viewsCount ?: item.originalSlide?.viewsCount ?: 0
            binding.tvStoryViewersCount.text = "$views views"

            binding.btnStoryViewers.setOnClickListener {
                pauseStoryTimeline()
                val viewersSheet = StoryViewersBottomSheetDialogFragment.newInstance(item.storyId)
                viewersSheet.dialog?.setOnDismissListener {
                    resumeStoryTimeline()
                }
                viewersSheet.show(childFragmentManager, "story_viewers")
            }
        } else {
            binding.layoutOwnStoryActions.visibility = View.GONE
            binding.layoutOtherStoryActions.visibility = View.VISIBLE

            binding.etReplyMessage.hint = "Reply to ${item.userName}..."

            fun sendReaction(emoji: String) {
                onReactClick?.invoke(item.storyId, emoji)
                viewLifecycleOwner.lifecycleScope.launch {
                    matchesRepository.reactToQuickMoment(item.storyId, emoji).collectLatest { }
                }
                Toast.makeText(requireContext(), "Reacted $emoji", Toast.LENGTH_SHORT).show()
            }

            fun sendReply() {
                val msg = binding.etReplyMessage.text?.toString()?.trim().orEmpty()
                if (msg.isNotBlank()) {
                    onReplyClick?.invoke(item.storyId, msg)
                    viewLifecycleOwner.lifecycleScope.launch {
                        matchesRepository.replyToQuickMoment(item.storyId, msg).collectLatest { }
                    }
                    binding.etReplyMessage.text?.clear()
                    Toast.makeText(requireContext(), "Reply sent!", Toast.LENGTH_SHORT).show()
                }
            }

            binding.btnReactLove.setOnClickListener { sendReaction("\uD83D\uDE0D") }
            binding.btnReactFire.setOnClickListener { sendReaction("\uD83D\uDD25") }
            binding.btnReactLaugh.setOnClickListener { sendReaction("\uD83D\uDE02") }
            binding.btnReactWow.setOnClickListener { sendReaction("\uD83D\uDE2E") }
            binding.btnReactClap.setOnClickListener { sendReaction("\uD83D\uDC4F") }
            binding.btnReactHeart.setOnClickListener { sendReaction("\u2764\uFE0F") }

            binding.btnSendReply.setOnClickListener { sendReply() }
            binding.etReplyMessage.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendReply()
                    true
                } else false
            }

            // Pause while user touches or types in reply box
            binding.etReplyMessage.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) pauseStoryTimeline() else resumeStoryTimeline()
            }
        }

        // Pause/Resume on screen touch and hold
        binding.rootViewer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    pauseStoryTimeline()
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    resumeStoryTimeline()
                    false
                }
                else -> false
            }
        }

        // Start 5-second story timeline bar
        startStoryTimeline()
    }

    private fun startStoryTimeline() {
        storyAnimator?.cancel()
        binding.progressBarStory.max = 1000
        binding.progressBarStory.progress = 0

        storyAnimator = ValueAnimator.ofInt(0, 1000).apply {
            duration = STORY_DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                _binding?.progressBarStory?.progress = anim.animatedValue as Int
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (isAdded && !isStateSaved) {
                        dismissAllowingStateLoss()
                    }
                }
            })
            start()
        }
        isStoryPaused = false
    }

    private fun pauseStoryTimeline() {
        if (!isStoryPaused) {
            storyAnimator?.pause()
            isStoryPaused = true
        }
    }

    private fun resumeStoryTimeline() {
        if (isStoryPaused) {
            storyAnimator?.resume()
            isStoryPaused = false
        }
    }

    private fun stopStoryTimeline() {
        storyAnimator?.cancel()
        storyAnimator = null
    }

    override fun onPause() {
        super.onPause()
        pauseStoryTimeline()
    }

    override fun onResume() {
        super.onResume()
        resumeStoryTimeline()
    }

    override fun onDestroyView() {
        stopStoryTimeline()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val STORY_DURATION_MS = 5000L // 5 seconds per story

        fun newInstance(card: QuickMomentCardItem.StoryCard): QuickMomentsViewerDialogFragment {
            return QuickMomentsViewerDialogFragment().apply {
                this.storyCard = card
            }
        }
    }
}
