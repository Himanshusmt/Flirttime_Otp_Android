package com.flirttime.feature.chat.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.core.common.MediaUrlHelper
import com.flirttime.databinding.ItemChatRowBinding
import com.flirttime.feature.chat.model.ConversationUi

class ConversationAdapter(
    private val onConversationClick: (ConversationUi) -> Unit,
    private val onConversationLongClick: ((ConversationUi) -> Unit)? = null,
    private val onSelectionToggle: (ConversationUi) -> Unit = {}
) : ListAdapter<ConversationUi, ConversationAdapter.ConversationViewHolder>(DIFF) {

    /** Bulk-action state (iOS `MultiSelectActionBar`): taps toggle a row instead of opening. */
    private var selectionMode = false
    private var selectedIds: Set<String> = emptySet()

    fun setSelectionMode(enabled: Boolean) {
        if (selectionMode == enabled) return
        selectionMode = enabled
        if (!enabled) selectedIds = emptySet()
        notifyItemRangeChanged(0, itemCount)
    }

    fun setSelectedIds(ids: Set<String>) {
        selectedIds = ids
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val binding = ItemChatRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ConversationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ConversationViewHolder(
        private val binding: ItemChatRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(conversation: ConversationUi) {
            val context = binding.root.context
            val avatarUrl = MediaUrlHelper.resolveMediaUrl(conversation.avatarUrl)

            if (!avatarUrl.isNullOrBlank()) {
                binding.ivAvatar.setImageURI(avatarUrl)
            } else {
                binding.ivAvatar.setActualImageResource(R.drawable.img_login)
            }

            // Online presence reuses the ring used for story highlights in the inbox design.
            binding.viewStoryRing.visibility = if (conversation.isOnline) View.VISIBLE else View.GONE
            binding.ivVerifiedBadge.visibility = if (conversation.isVerified) View.VISIBLE else View.GONE

            binding.tvContactName.text = conversation.title
            binding.tvTimestamp.text = conversation.timestampLabel
            // Muted threads advertise it in the timestamp column, matching iOS.
            binding.ivMuted.visibility = if (conversation.isMuted) View.VISIBLE else View.GONE

            if (conversation.lastMessagePreview.isBlank()) {
                binding.layoutMessagePreview.visibility = View.GONE
            } else {
                binding.layoutMessagePreview.visibility = View.VISIBLE
                binding.tvLastMessage.text = conversation.lastMessagePreview

                binding.ivDoubleCheck.visibility = if (conversation.isLastMessageFromMe) View.VISIBLE else View.GONE
                binding.ivDoubleCheck.alpha = if (conversation.isLastMessageRead) 1f else 0.45f

                // Unread threads keep the bold/dark treatment, read ones drop back to grey.
                val isUnread = conversation.unreadCount > 0
                binding.tvLastMessage.setTextColor(
                    if (isUnread) UNREAD_PREVIEW_COLOR else READ_PREVIEW_COLOR
                )
                runCatching {
                    binding.tvLastMessage.typeface = ResourcesCompat.getFont(
                        context,
                        if (isUnread) R.font.fredokasemibold else R.font.fredokaregular
                    )
                }
            }

            if (conversation.unreadCount > 0) {
                binding.tvUnreadBadge.visibility = View.VISIBLE
                binding.tvUnreadBadge.text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString()
                binding.tvUnreadBadge.isEnabled = !conversation.isMuted
                binding.tvUnreadBadge.alpha = if (conversation.isMuted) 0.5f else 1f
            } else {
                binding.tvUnreadBadge.visibility = View.GONE
            }

            renderSelection(conversation)

            binding.root.setOnClickListener {
                if (selectionMode) onSelectionToggle(conversation) else onConversationClick(conversation)
            }
            // Long-press opens thread settings; while selecting it toggles the row instead.
            binding.root.setOnLongClickListener {
                if (selectionMode) {
                    onSelectionToggle(conversation)
                    true
                } else {
                    onConversationLongClick?.invoke(conversation)
                    onConversationLongClick != null
                }
            }
        }

        private fun renderSelection(conversation: ConversationUi) {
            if (!selectionMode) {
                binding.ivSelected.visibility = View.GONE
                return
            }
            val selected = selectedIds.contains(conversation.id)
            binding.ivSelected.visibility = View.VISIBLE
            binding.ivSelected.setImageResource(
                if (selected) R.drawable.ic_action_check_circle else R.drawable.ic_action_circle_outline
            )
        }
    }

    private companion object {
        val UNREAD_PREVIEW_COLOR = 0xFF18181B.toInt()
        val READ_PREVIEW_COLOR = 0xFF71717A.toInt()

        val DIFF = object : DiffUtil.ItemCallback<ConversationUi>() {
            override fun areItemsTheSame(oldItem: ConversationUi, newItem: ConversationUi): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ConversationUi, newItem: ConversationUi): Boolean =
                oldItem == newItem
        }
    }
}
