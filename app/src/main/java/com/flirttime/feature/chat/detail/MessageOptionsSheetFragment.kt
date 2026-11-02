package com.flirttime.feature.chat.detail

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import com.flirttime.R
import com.flirttime.databinding.DialogMessageOptionsBinding
import com.flirttime.feature.chat.model.MessageKind
import com.flirttime.feature.chat.model.MessageUi
import com.flirttime.feature.chat.model.ReactionUi
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Long-press action sheet — the Android twin of the iOS `MessageOptionsSheet`.
 *
 * Shows a quoted preview, a horizontal reaction bar (recent emoji first, then a “+” that
 * opens the emoji grid) and the action list: Reply, Forward, Copy, Edit, Pin / Unpin, Select,
 * Message Info, Retry, Delete for Me, Delete for Everyone.
 */
class MessageOptionsSheetFragment : BottomSheetDialogFragment() {

    interface Callbacks {
        fun onReplyClicked(message: MessageUi)
        fun onForwardClicked(message: MessageUi)
        fun onCopyClicked(message: MessageUi)
        fun onEditClicked(message: MessageUi)
        fun onPinClicked(message: MessageUi, isPinned: Boolean)
        fun onSelectClicked(message: MessageUi)
        fun onMessageInfoClicked(message: MessageUi)
        fun onRetryClicked(message: MessageUi)
        fun onDeleteForMeClicked(message: MessageUi)
        fun onDeleteForEveryoneClicked(message: MessageUi)
        fun onReactClicked(message: MessageUi, emoji: String)
        fun onMoreReactionsClicked(message: MessageUi)
        fun onReactionUsersClicked(message: MessageUi, reaction: ReactionUi)
    }

    private var _binding: DialogMessageOptionsBinding? = null
    private val binding get() = _binding!!

    private var message: MessageUi? = null
    private var callbacks: Callbacks? = null
    private var canEdit = false
    private var canDeleteForEveryone = false
    private var canPin = true
    private var canReply = true
    private var canForward = true
    private var canCopy = true
    private var canSelect = true
    private var canShowInfo = true
    private var canRetry = false
    private var isPinned = false

    fun bind(
        message: MessageUi,
        callbacks: Callbacks,
        canEdit: Boolean,
        canDeleteForEveryone: Boolean,
        isPinned: Boolean,
        canRetry: Boolean,
        isTemporary: Boolean = false
    ): MessageOptionsSheetFragment = apply {
        this.message = message
        this.callbacks = callbacks
        this.canEdit = canEdit && !isTemporary
        this.canDeleteForEveryone = canDeleteForEveryone && !isTemporary
        this.isPinned = isPinned
        this.canRetry = canRetry
        // Local temps only expose Delete / Retry, exactly like iOS.
        this.canPin = !isTemporary
        this.canReply = !isTemporary
        this.canForward = !isTemporary && message.kind != MessageKind.UNSUPPORTED
        // Copy needs a text body; info only makes sense for messages the server knows about.
        this.canCopy = !isTemporary && message.body.isNotBlank() && !message.isDeleted
        this.canShowInfo = !isTemporary && !message.isSystem
        this.canSelect = !isTemporary && !message.isSystem
    }

    fun show(manager: FragmentManager) {
        show(manager, TAG)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            sheet.setBackgroundResource(R.drawable.bg_bottom_sheet_top)
            val behavior = BottomSheetBehavior.from(sheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogMessageOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val target = message
        if (target == null) {
            dismissAllowingStateLoss()
            return
        }

        binding.tvMessagePreview.text = previewText(target)
        bindReactions(target)
        bindRows(target)
    }

    private fun previewText(message: MessageUi): String {
        val body = message.body.trim()
        if (body.isNotEmpty()) return body
        return when (message.kind) {
            MessageKind.IMAGE -> "Photo"
            MessageKind.VIDEO -> "Video"
            MessageKind.AUDIO -> "Voice message"
            else -> "Message"
        }
    }

    private fun bindReactions(message: MessageUi) {
        val container = binding.layoutReactions
        container.removeAllViews()
        val density = resources.displayMetrics.density

        RecentReactions.visible(requireContext()).forEach { emoji ->
            container.addView(
                emojiButton(emoji, message),
                LinearLayout.LayoutParams(dp(44, density), dp(44, density)).apply {
                    marginEnd = dp(10, density)
                }
            )
        }

        // "+" opens the full emoji grid.
        val more = TextView(requireContext()).apply {
            text = "+"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_primary))
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_reaction_add)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dismissAllowingStateLoss()
                callbacks?.onMoreReactionsClicked(message)
            }
        }
        container.addView(more, LinearLayout.LayoutParams(dp(44, density), dp(44, density)))
    }

    private fun emojiButton(emoji: String, message: MessageUi): TextView =
        TextView(requireContext()).apply {
            text = emoji
            textSize = 26f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener {
                RecentReactions.remember(requireContext(), emoji)
                dismissAllowingStateLoss()
                callbacks?.onReactClicked(message, emoji)
            }
        }

    private fun bindRows(message: MessageUi) {
        binding.rowReply.isVisible = canReply
        binding.rowForward.isVisible = canForward
        binding.rowCopy.isVisible = canCopy
        binding.rowEdit.isVisible = canEdit
        binding.rowPin.isVisible = canPin
        binding.rowSelect.isVisible = canSelect
        binding.rowMessageInfo.isVisible = canShowInfo
        binding.rowRetry.isVisible = canRetry
        binding.rowDeleteMe.isVisible = true
        binding.rowDeleteEveryone.isVisible = canDeleteForEveryone

        binding.tvPinLabel.text = if (isPinned) "Unpin" else "Pin"
        binding.ivPinIcon.setImageResource(
            if (isPinned) R.drawable.ic_action_unpin else R.drawable.ic_action_pin
        )

        binding.rowReply.setOnClickListener {
            dismissAllowingStateLoss()
            callbacks?.onReplyClicked(message)
        }
        binding.rowForward.setOnClickListener {
            dismissAllowingStateLoss()
            callbacks?.onForwardClicked(message)
        }
        binding.rowCopy.setOnClickListener {
            dismissAllowingStateLoss()
            callbacks?.onCopyClicked(message)
        }
        binding.rowEdit.setOnClickListener {
            dismissAllowingStateLoss()
            callbacks?.onEditClicked(message)
        }
        binding.rowPin.setOnClickListener {
            dismissAllowingStateLoss()
            callbacks?.onPinClicked(message, isPinned)
        }
        binding.rowSelect.setOnClickListener {
            dismissAllowingStateLoss()
            callbacks?.onSelectClicked(message)
        }
        binding.rowMessageInfo.setOnClickListener {
            dismissAllowingStateLoss()
            callbacks?.onMessageInfoClicked(message)
        }
        binding.rowRetry.setOnClickListener {
            dismissAllowingStateLoss()
            callbacks?.onRetryClicked(message)
        }
        binding.rowDeleteMe.setOnClickListener {
            dismissAllowingStateLoss()
            callbacks?.onDeleteForMeClicked(message)
        }
        binding.rowDeleteEveryone.setOnClickListener {
            dismissAllowingStateLoss()
            callbacks?.onDeleteForEveryoneClicked(message)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun dp(value: Int, density: Float): Int = (value * density).toInt()

    companion object {
        const val TAG = "MessageOptionsSheet"
    }
}
