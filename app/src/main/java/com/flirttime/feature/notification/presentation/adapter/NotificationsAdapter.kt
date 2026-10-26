package com.flirttime.feature.notification.presentation.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.core.common.ShimmerHelper
import com.flirttime.databinding.ItemNotificationCardBinding
import com.flirttime.databinding.ItemNotificationHeaderBinding
import com.flirttime.feature.notification.data.model.NotificationItem
import com.flirttime.feature.notification.data.model.NotificationListItem
import com.flirttime.feature.notification.data.model.NotificationType

class NotificationsAdapter(
    private val onItemClick: (NotificationItem) -> Unit
) : ListAdapter<NotificationListItem, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is NotificationListItem.Header -> VIEW_TYPE_HEADER
            is NotificationListItem.Item -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemNotificationHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            else -> {
                val binding = ItemNotificationCardBinding.inflate(inflater, parent, false)
                NotificationViewHolder(binding, onItemClick)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is NotificationListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is NotificationListItem.Item -> (holder as NotificationViewHolder).bind(item.notification)
        }
    }

    class HeaderViewHolder(
        private val binding: ItemNotificationHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: NotificationListItem.Header) {
            binding.tvHeader.text = header.title
        }
    }

    class NotificationViewHolder(
        private val binding: ItemNotificationCardBinding,
        private val onItemClick: (NotificationItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NotificationItem) {
            // Read vs Unread card styling & indicator dot
            if (item.isRead) {
                binding.cardContainer.setBackgroundResource(R.drawable.bg_notification_card_read)
                binding.viewUnreadDot.visibility = View.GONE
            } else {
                binding.cardContainer.setBackgroundResource(R.drawable.bg_notification_card_unread)
                binding.viewUnreadDot.visibility = View.VISIBLE
            }

            binding.tvTitle.text = item.title
            binding.tvDescription.text = item.description
            binding.tvTimestamp.text = item.timestamp

            // Avatar rendering
            if (item.avatarUrl != null) {
                binding.ivAvatar.visibility = View.VISIBLE
                binding.systemAvatarContainer.visibility = View.GONE
                ShimmerHelper.loadWithShimmer(binding.ivAvatar, item.avatarUrl)
            } else {
                binding.ivAvatar.visibility = View.GONE
                binding.systemAvatarContainer.visibility = View.VISIBLE
            }

            // Corner Badge styling based on NotificationType
            when (item.type) {
                NotificationType.MATCH -> {
                    binding.badgeContainer.setBackgroundResource(R.drawable.bg_notif_badge_pink)
                    binding.ivBadgeIcon.setImageResource(R.drawable.ic_notif_badge_match)
                }
                NotificationType.LIKE -> {
                    binding.badgeContainer.setBackgroundResource(R.drawable.bg_notif_badge_red)
                    binding.ivBadgeIcon.setImageResource(R.drawable.ic_notif_badge_like)
                }
                NotificationType.COMPLIMENT -> {
                    binding.badgeContainer.setBackgroundResource(R.drawable.bg_notif_badge_pink)
                    binding.ivBadgeIcon.setImageResource(R.drawable.ic_notif_badge_compliment)
                }
                NotificationType.SUPER_LIKE -> {
                    binding.badgeContainer.setBackgroundResource(R.drawable.bg_notif_badge_blue)
                    binding.ivBadgeIcon.setImageResource(R.drawable.ic_notif_badge_star)
                }
                NotificationType.MESSAGE -> {
                    binding.badgeContainer.setBackgroundResource(R.drawable.bg_notif_badge_blue)
                    binding.ivBadgeIcon.setImageResource(R.drawable.ic_notif_badge_message)
                }
                NotificationType.PROFILE_VERIFIED -> {
                    binding.badgeContainer.setBackgroundResource(R.drawable.bg_notif_badge_green)
                    binding.ivBadgeIcon.setImageResource(R.drawable.ic_notif_badge_verified)
                }
                NotificationType.PHOTO_NOT_APPROVED -> {
                    binding.badgeContainer.setBackgroundResource(R.drawable.bg_notif_badge_orange)
                    binding.ivBadgeIcon.setImageResource(R.drawable.ic_notif_badge_warning)
                }
            }

            binding.cardContainer.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1

        private val DiffCallback = object : DiffUtil.ItemCallback<NotificationListItem>() {
            override fun areItemsTheSame(
                oldItem: NotificationListItem,
                newItem: NotificationListItem
            ): Boolean {
                return when {
                    oldItem is NotificationListItem.Header && newItem is NotificationListItem.Header ->
                        oldItem.title == newItem.title
                    oldItem is NotificationListItem.Item && newItem is NotificationListItem.Item ->
                        oldItem.notification.id == newItem.notification.id
                    else -> false
                }
            }

            override fun areContentsTheSame(
                oldItem: NotificationListItem,
                newItem: NotificationListItem
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
