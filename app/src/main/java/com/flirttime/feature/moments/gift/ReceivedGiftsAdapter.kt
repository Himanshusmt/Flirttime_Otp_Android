package com.flirttime.feature.moments.gift

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.databinding.ItemReceivedGiftBinding
import com.flirttime.feature.moments.ReceivedGiftDto

class ReceivedGiftsAdapter(
    private val onItemClick: ((ReceivedGiftDto) -> Unit)? = null
) : ListAdapter<ReceivedGiftDto, ReceivedGiftsAdapter.ReceivedGiftViewHolder>(GiftDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReceivedGiftViewHolder {
        val binding = ItemReceivedGiftBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ReceivedGiftViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReceivedGiftViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ReceivedGiftViewHolder(
        private val binding: ItemReceivedGiftBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(gift: ReceivedGiftDto) {
            // Sender Avatar
            if (!gift.senderAvatar.isNullOrBlank()) {
                binding.ivSenderAvatar.setImageURI(gift.senderAvatar)
            } else {
                binding.ivSenderAvatar.setActualImageResource(R.drawable.img_login)
            }

            // Sender Name & Time
            binding.tvSenderName.text = gift.senderName
            binding.tvTimeAgo.text = gift.timeAgo ?: "1d"

            // Description: Sent you a Rose · 1 coins
            val giftName = gift.displayName
            val coins = gift.effectiveCoins
            binding.tvGiftDescription.text = "Sent you a $giftName · $coins coins"

            // Gift Icon / Emoji
            if (!gift.imageUrl.isNullOrBlank()) {
                binding.ivGiftImage.visibility = View.VISIBLE
                binding.tvGiftEmoji.visibility = View.GONE
                binding.ivGiftImage.setImageURI(gift.imageUrl)
            } else {
                binding.ivGiftImage.visibility = View.GONE
                binding.tvGiftEmoji.visibility = View.VISIBLE
                binding.tvGiftEmoji.text = gift.icon?.takeIf { it.isNotBlank() } ?: resolveGiftEmoji(gift.giftCode, giftName)
            }

            binding.root.setOnClickListener {
                onItemClick?.invoke(gift)
            }
        }

        private fun resolveGiftEmoji(code: String?, name: String?): String {
            val key = (code ?: name).orEmpty().lowercase()
            return when {
                key.contains("rose") -> "🌹"
                key.contains("heart") -> "💖"
                key.contains("chocolate") -> "🍫"
                key.contains("teddy") -> "🧸"
                key.contains("bouquet") -> "💐"
                key.contains("ring") -> "💍"
                key.contains("crown") -> "👑"
                key.contains("car") -> "🏎️"
                else -> "🌹"
            }
        }
    }

    class GiftDiffCallback : DiffUtil.ItemCallback<ReceivedGiftDto>() {
        override fun areItemsTheSame(oldItem: ReceivedGiftDto, newItem: ReceivedGiftDto): Boolean {
            val oldId = oldItem.id ?: oldItem.giftId
            val newId = newItem.id ?: newItem.giftId
            return if (oldId != null && newId != null) oldId == newId else oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: ReceivedGiftDto, newItem: ReceivedGiftDto): Boolean {
            return oldItem == newItem
        }
    }
}
