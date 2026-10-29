package com.flirttime.feature.profile.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.databinding.ItemBlockedUserBinding
import com.flirttime.feature.profile.BlockedUserDto

class BlockedUsersAdapter(
    private val onUnblockClick: (BlockedUserDto) -> Unit
) : ListAdapter<BlockedUserDto, BlockedUsersAdapter.BlockedUserViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockedUserViewHolder {
        val binding = ItemBlockedUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BlockedUserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BlockedUserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BlockedUserViewHolder(
        private val binding: ItemBlockedUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BlockedUserDto) {
            val displayName = item.name ?: item.username ?: "User"
            binding.tvBlockedName.text = displayName
            binding.tvBlockedUsername.text = if (!item.username.isNullOrBlank()) "@${item.username}" else ""

            if (!item.profilePhoto.isNullOrBlank()) {
                binding.ivBlockedAvatar.setImageURI(item.profilePhoto)
            } else {
                binding.ivBlockedAvatar.setActualImageResource(R.drawable.img_login)
            }

            binding.btnUnblock.setOnClickListener {
                onUnblockClick(item)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<BlockedUserDto>() {
            override fun areItemsTheSame(oldItem: BlockedUserDto, newItem: BlockedUserDto): Boolean {
                return oldItem.userId == newItem.userId
            }

            override fun areContentsTheSame(oldItem: BlockedUserDto, newItem: BlockedUserDto): Boolean {
                return oldItem == newItem
            }
        }
    }
}
