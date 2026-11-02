package com.flirttime.feature.main.explore

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.core.common.ShimmerHelper
import com.flirttime.databinding.ItemExploreProfileCardBinding

class ExploreProfileAdapter(
    private val onProfileClick: (ExploreProfileItem) -> Unit
) : ListAdapter<ExploreProfileItem, ExploreProfileAdapter.ProfileViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val binding = ItemExploreProfileCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProfileViewHolder(binding, onProfileClick)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ProfileViewHolder(
        private val binding: ItemExploreProfileCardBinding,
        private val onProfileClick: (ExploreProfileItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ExploreProfileItem) {
            // 1. Photo / Placeholder / Blur Binding
            if (item.imageUrl.isNullOrBlank()) {
                binding.ivPlaceholder.visibility = View.VISIBLE
                binding.ivProfilePhoto.visibility = View.GONE
            } else {
                binding.ivPlaceholder.visibility = View.GONE
                binding.ivProfilePhoto.visibility = View.VISIBLE

                // Load with Shimmer and blur if locked
                ShimmerHelper.loadWithShimmer(
                    draweeView = binding.ivProfilePhoto,
                    url = item.imageUrl,
                    fallbackRes = R.drawable.explore_placeholder,
                    isBlurred = item.isLocked
                )
            }

            // 2. Lock / Blur Overlay
            if (item.isLocked) {
                binding.layoutBlurOverlay.visibility = View.VISIBLE
            } else {
                binding.layoutBlurOverlay.visibility = View.GONE
            }

            // 3. Name & Age
            val nameAgeText = if (item.age != null && item.age > 0) {
                "${item.username}, ${item.age}"
            } else {
                item.username
            }
            binding.tvNameAge.text = nameAgeText

            // 4. Achievement / Verified Rosette Badge
            binding.ivRibbonBadge.visibility = if (item.isVerified) View.VISIBLE else View.GONE

            // 5. Online Status (Badge Pill & Dot next to name)
            val showOnline = item.isOnline && !item.isLocked
            binding.layoutOnlineBadge.visibility = if (showOnline) View.VISIBLE else View.GONE
            binding.dotOnlineNextToName.visibility = if (showOnline) View.VISIBLE else View.GONE

            // 6. SuperLike Badge
            binding.ivSuperLikeBadge.visibility = if (item.isSuperLike && !item.isLocked) View.VISIBLE else View.GONE

            // 7. Walk Distance (e.g. "7 km away", "Nearby")
            if (!item.distance.isNullOrBlank()) {
                binding.layoutDistance.visibility = View.VISIBLE
                binding.tvDistance.text = item.distance
            } else {
                binding.layoutDistance.visibility = View.GONE
            }

            // 8. Subtitle / Location
            val subtitleText = item.subtitle?.takeIf { it.isNotBlank() } ?: item.location
            binding.tvSubtitle.text = subtitleText
            binding.tvSubtitle.visibility = if (subtitleText.isNotBlank()) View.VISIBLE else View.GONE

            // 9. Click Handler
            binding.cardProfile.setOnClickListener {
                onProfileClick(item)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ExploreProfileItem>() {
            override fun areItemsTheSame(oldItem: ExploreProfileItem, newItem: ExploreProfileItem): Boolean {
                return oldItem.id == newItem.id && oldItem.isLocked == newItem.isLocked
            }

            override fun areContentsTheSame(oldItem: ExploreProfileItem, newItem: ExploreProfileItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
