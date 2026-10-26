package com.flirttime.feature.main.presentation.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.core.common.ShimmerHelper
import com.flirttime.databinding.ItemChatRowBinding

class ChatsAdapter(
    private var chats: List<ChatItem> = emptyList(),
    private val onChatClick: ((ChatItem) -> Unit)? = null
) : RecyclerView.Adapter<ChatsAdapter.ChatViewHolder>() {

    fun submitList(newChats: List<ChatItem>) {
        chats = newChats
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(chats[position])
    }

    override fun getItemCount(): Int = chats.size

    inner class ChatViewHolder(private val binding: ItemChatRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: ChatItem) {
            val context = binding.root.context

            // 1. Avatar with Shimmer
            ShimmerHelper.loadWithShimmer(
                draweeView = binding.ivAvatar,
                url = chat.avatarUrl,
                fallbackRes = chat.avatarRes
            )

            // 2. Active Story/Status Green Ring
            binding.viewStoryRing.visibility = if (chat.hasStoryRing) View.VISIBLE else View.GONE

            // 3. Verified Blue Badge
            binding.ivVerifiedBadge.visibility = if (chat.isVerified) View.VISIBLE else View.GONE

            // 4. Contact Name
            binding.tvContactName.text = chat.name

            // 5. Last Message Preview
            if (chat.lastMessage.isNullOrBlank()) {
                binding.layoutMessagePreview.visibility = View.GONE
            } else {
                binding.layoutMessagePreview.visibility = View.VISIBLE
                binding.tvLastMessage.text = chat.lastMessage

                // Double check icon (if sent by user)
                binding.ivDoubleCheck.visibility = if (chat.isSentByMe) View.VISIBLE else View.GONE

                // Unread message styling (Bold black vs regular gray)
                if (chat.unreadCount > 0) {
                    binding.tvLastMessage.setTextColor(0xFF18181B.toInt())
                    try {
                        binding.tvLastMessage.typeface = ResourcesCompat.getFont(context, R.font.fredokasemibold)
                    } catch (_: Exception) {}
                } else {
                    binding.tvLastMessage.setTextColor(0xFF71717A.toInt())
                    try {
                        binding.tvLastMessage.typeface = ResourcesCompat.getFont(context, R.font.fredokaregular)
                    } catch (_: Exception) {}
                }
            }

            // 6. Timestamp
            binding.tvTimestamp.text = chat.timestamp

            // 7. Unread Badge
            if (chat.unreadCount > 0) {
                binding.tvUnreadBadge.visibility = View.VISIBLE
                binding.tvUnreadBadge.text = chat.unreadCount.toString()
            } else {
                binding.tvUnreadBadge.visibility = View.GONE
            }

            // 8. Row Click
            binding.root.setOnClickListener {
                onChatClick?.invoke(chat)
            }
        }
    }
}
