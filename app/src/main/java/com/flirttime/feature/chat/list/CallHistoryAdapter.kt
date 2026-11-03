package com.flirttime.feature.chat.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.core.common.MediaUrlHelper
import com.flirttime.databinding.ItemCallHistoryRowBinding
import com.flirttime.feature.chat.model.CallHistoryMedia
import com.flirttime.feature.chat.model.CallHistoryUi

class CallHistoryAdapter(
    private val onCallActionClick: (CallHistoryUi) -> Unit,
    private val onItemLongClick: (CallHistoryUi) -> Unit = {}
) : ListAdapter<CallHistoryUi, CallHistoryAdapter.CallViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallViewHolder {
        val binding = ItemCallHistoryRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CallViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CallViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CallViewHolder(
        private val binding: ItemCallHistoryRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CallHistoryUi) {
            val context = binding.root.context
            val missedColor = ContextCompat.getColor(context, R.color.brand_primary)
            val nameColor = if (item.isMissed) {
                missedColor
            } else {
                ContextCompat.getColor(context, android.R.color.black)
            }
            val subtitleColor = if (item.isMissed) missedColor else 0xFF8E8E93.toInt()

            binding.tvName.text = item.peerName
            binding.tvName.setTextColor(nameColor)
            binding.tvSubtitle.text = item.subtitle()
            binding.tvSubtitle.setTextColor(subtitleColor)
            binding.tvTime.text = item.timestampLabel

            val avatarUrl = MediaUrlHelper.resolveMediaUrl(item.peerAvatar)
            if (!avatarUrl.isNullOrBlank()) {
                binding.ivAvatar.setImageURI(avatarUrl)
            } else {
                binding.ivAvatar.setActualImageResource(R.drawable.ic_settings_person)
            }

            val iconRes = if (item.media == CallHistoryMedia.VIDEO) {
                R.drawable.ic_sf_video_fill
            } else {
                R.drawable.ic_phone_receiver
            }
            binding.ivCallAction.setImageResource(iconRes)

            binding.btnCallAction.setOnClickListener { onCallActionClick(item) }
            binding.root.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CallHistoryUi>() {
            override fun areItemsTheSame(oldItem: CallHistoryUi, newItem: CallHistoryUi): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: CallHistoryUi, newItem: CallHistoryUi): Boolean =
                oldItem == newItem
        }
    }
}
