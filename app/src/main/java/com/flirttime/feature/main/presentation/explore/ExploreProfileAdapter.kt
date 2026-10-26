package com.flirttime.feature.main.presentation.explore

import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.core.common.ShimmerHelper
import com.flirttime.databinding.ItemExploreProfileCardBinding
import com.flirttime.feature.main.presentation.explore.model.ExploreProfileItem

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
            // 1. Photo / Placeholder Binding
            if (item.isPlaceholder || item.imageUrl.isNullOrBlank()) {
                binding.viewPlaceholderBg.visibility = View.VISIBLE
                binding.ivProfilePhoto.visibility = View.GONE
                // Semi-transparent scrim on light card
                binding.viewScrim.visibility = View.GONE
                // Use subtle gray text on placeholder card
                binding.tvNameAge.setTextColor(Color.parseColor("#999999"))
                binding.tvDistance.setTextColor(Color.parseColor("#AAAAAA"))
                binding.tvLocation.setTextColor(Color.parseColor("#AAAAAA"))
            } else {
                binding.viewPlaceholderBg.visibility = View.GONE
                binding.ivProfilePhoto.visibility = View.VISIBLE
                binding.viewScrim.visibility = View.VISIBLE
                binding.tvNameAge.setTextColor(Color.WHITE)
                binding.tvDistance.setTextColor(Color.parseColor("#F0F0F0"))
                binding.tvLocation.setTextColor(Color.parseColor("#E0E0E0"))

                ShimmerHelper.loadWithShimmer(binding.ivProfilePhoto, item.imageUrl)
            }

            // 2. Name & Age
            val nameAgeText = if (item.age != null && item.age > 0) {
                "${item.username}, ${item.age}"
            } else {
                item.username
            }
            binding.tvNameAge.text = nameAgeText

            // 3. Ribbon Badge
            binding.ivRibbonBadge.visibility = if (item.hasRibbonBadge) View.VISIBLE else View.GONE

            // 4. Distance
            if (!item.distance.isNullOrBlank()) {
                binding.layoutDistance.visibility = View.VISIBLE
                binding.tvDistance.text = item.distance
            } else {
                binding.layoutDistance.visibility = View.GONE
            }

            // 5. Location
            binding.tvLocation.text = item.location

            // 6. Click Handler
            binding.cardProfile.setOnClickListener {
                onProfileClick(item)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ExploreProfileItem>() {
            override fun areItemsTheSame(oldItem: ExploreProfileItem, newItem: ExploreProfileItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ExploreProfileItem, newItem: ExploreProfileItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
