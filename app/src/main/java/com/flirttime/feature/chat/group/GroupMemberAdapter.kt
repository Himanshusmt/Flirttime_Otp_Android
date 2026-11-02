package com.flirttime.feature.chat.group

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.core.common.MediaUrlHelper
import com.flirttime.core.navigation.ChatMember
import com.flirttime.databinding.ItemGroupMemberBinding

/** Rows of the group member sheet: avatar, name, verified badge and a "You" pill. */
class GroupMemberAdapter(
    private val currentUserId: String
) : ListAdapter<ChatMember, GroupMemberAdapter.MemberViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder =
        MemberViewHolder(
            ItemGroupMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MemberViewHolder(
        private val binding: ItemGroupMemberBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(member: ChatMember) {
            val isMe = member.userId == currentUserId
            binding.tvMemberName.text = member.name.takeIf { it.isNotBlank() }
                ?: if (isMe) "You" else "Member"
            binding.tvMemberRole.text = if (isMe) "You" else "Member"
            binding.ivMemberVerified.isVisible = member.isVerified
            binding.tvMemberYou.isVisible = isMe

            val avatarUrl = MediaUrlHelper.resolveMediaUrl(member.avatarUrl)
            if (!avatarUrl.isNullOrBlank()) {
                binding.ivMemberAvatar.setImageURI(avatarUrl)
            } else {
                binding.ivMemberAvatar.setActualImageResource(com.flirttime.R.drawable.img_login)
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ChatMember>() {
            override fun areItemsTheSame(oldItem: ChatMember, newItem: ChatMember): Boolean =
                oldItem.userId == newItem.userId

            override fun areContentsTheSame(oldItem: ChatMember, newItem: ChatMember): Boolean =
                oldItem == newItem
        }
    }
}
