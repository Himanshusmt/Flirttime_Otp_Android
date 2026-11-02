package com.flirttime.feature.chat.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.flirttime.R
import com.flirttime.core.common.MediaUrlHelper
import com.flirttime.databinding.DialogChatSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Thread-level action sheet — the Android twin of the iOS conversation actions
 * (`ChatActionsHandler` + the swipe / multi-select rows in `ChatRowView`).
 *
 * Mutations are applied to the local [Options] copy so the row labels flip in place
 * (Mute → Unmute, Pin → Unpin, …) without re-opening the sheet.
 */
class ChatSettingsSheetFragment : BottomSheetDialogFragment() {

    enum class Action {
        OPEN_PROFILE,
        TOGGLE_MUTE,
        TOGGLE_PIN,
        TOGGLE_ARCHIVE,
        MARK_READ,
        MARK_UNREAD,
        TOGGLE_BLOCK,
        CLEAR_CHAT,
        DELETE
    }

    data class Options(
        val conversationId: String,
        val name: String,
        val avatarUrl: String? = null,
        val username: String? = null,
        val peerUserId: String? = null,
        val isGroup: Boolean = false,
        val isMuted: Boolean = false,
        val isPinned: Boolean = false,
        val isArchived: Boolean = false,
        val isBlocked: Boolean = false,
        val unreadCount: Int = 0
    )

    interface Callbacks {
        /** [options] already carries the toggled flags for the tapped [action]. */
        fun onChatAction(options: Options, action: Action)
    }

    private var _binding: DialogChatSettingsBinding? = null
    private val binding get() = _binding!!

    private var options: Options? = null
    private var callbacks: Callbacks? = null

    fun bind(options: Options, callbacks: Callbacks): ChatSettingsSheetFragment = apply {
        this.options = options
        this.callbacks = callbacks
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
        _binding = DialogChatSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val current = options
        if (current == null) {
            dismissAllowingStateLoss()
            return
        }

        bindHeader(current)
        bindRows(current)
    }

    private fun bindHeader(options: Options) {
        binding.tvName.text = options.name
        val subtitle = options.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
            ?: if (options.isGroup) "Group chat" else "View profile"
        binding.tvSubtitle.text = subtitle

        val avatarUrl = MediaUrlHelper.resolveMediaUrl(options.avatarUrl)
        if (!avatarUrl.isNullOrBlank()) {
            binding.ivAvatar.setImageURI(avatarUrl)
        } else {
            binding.ivAvatar.setActualImageResource(R.drawable.img_login)
        }

        // Profiles only exist for real users, so groups get a static header.
        val canOpenProfile = !options.isGroup && !options.peerUserId.isNullOrBlank()
        binding.layoutProfile.isClickable = canOpenProfile
        binding.layoutProfile.setOnClickListener {
            if (canOpenProfile) emit(Action.OPEN_PROFILE)
        }
    }

    private fun bindRows(options: Options) {
        binding.tvMute.text = if (options.isMuted) "Unmute" else "Mute"
        binding.tvPin.text = if (options.isPinned) "Unpin" else "Pin"
        binding.tvArchive.text = if (options.isArchived) "Unarchive" else "Archive"
        binding.tvReadState.text = if (options.unreadCount > 0) "Mark as read" else "Mark as unread"

        // Blocking is a direct-chat-only action on iOS as well.
        val canBlock = !options.isGroup && !options.peerUserId.isNullOrBlank()
        binding.tvBlock.isVisible = canBlock
        binding.tvBlock.text = if (options.isBlocked) "Unblock" else "Block"

        binding.tvMute.setOnClickListener {
            val next = current().copy(isMuted = !current().isMuted)
            apply(next, Action.TOGGLE_MUTE)
        }
        binding.tvPin.setOnClickListener {
            val next = current().copy(isPinned = !current().isPinned)
            apply(next, Action.TOGGLE_PIN)
        }
        binding.tvArchive.setOnClickListener {
            val next = current().copy(isArchived = !current().isArchived, isPinned = false)
            apply(next, Action.TOGGLE_ARCHIVE)
        }
        binding.tvReadState.setOnClickListener {
            val unread = current().unreadCount > 0
            val next = current().copy(unreadCount = if (unread) 0 else 1)
            apply(next, if (unread) Action.MARK_READ else Action.MARK_UNREAD)
        }
        binding.tvBlock.setOnClickListener {
            val next = current().copy(isBlocked = !current().isBlocked)
            apply(next, Action.TOGGLE_BLOCK)
        }
        binding.tvClearChat.setOnClickListener {
            val current = current()
            dismissAllowingStateLoss()
            callbacks?.onChatAction(current, Action.CLEAR_CHAT)
        }
        binding.tvDelete.setOnClickListener {
            val current = current()
            dismissAllowingStateLoss()
            callbacks?.onChatAction(current, Action.DELETE)
        }
        binding.tvCancel.setOnClickListener { dismissAllowingStateLoss() }
    }

    /** Keeps the sheet open so several toggles can be applied in one visit. */
    private fun apply(next: Options, action: Action) {
        options = next
        bindRows(next)
        callbacks?.onChatAction(next, action)
    }

    private fun emit(action: Action) {
        callbacks?.onChatAction(current(), action)
    }

    private fun current(): Options = options ?: Options(conversationId = "", name = "")

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ChatSettingsSheet"

        fun create(options: Options, callbacks: Callbacks): ChatSettingsSheetFragment =
            ChatSettingsSheetFragment().bind(options, callbacks)
    }
}
