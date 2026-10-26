package com.flirttime.feature.main.presentation.matches

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.core.common.ShimmerHelper
import com.flirttime.databinding.ItemMatchCardBinding

class MatchCardAdapter(
    private var profiles: List<MatchProfile> = emptyList(),
    private val onCardClick: ((MatchProfile) -> Unit)? = null
) : RecyclerView.Adapter<MatchCardAdapter.MatchCardViewHolder>() {

    fun submitList(newProfiles: List<MatchProfile>) {
        profiles = newProfiles
        notifyDataSetChanged()
    }

    fun getProfileAt(position: Int): MatchProfile? {
        return profiles.getOrNull(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MatchCardViewHolder {
        val binding = ItemMatchCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MatchCardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MatchCardViewHolder, position: Int) {
        holder.bind(profiles[position])
    }

    override fun getItemCount(): Int = profiles.size

    inner class MatchCardViewHolder(
        private val binding: ItemMatchCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(profile: MatchProfile) {
            // Load Photo using Fresco with Shimmer
            ShimmerHelper.loadWithShimmer(
                draweeView = binding.ivUserPhoto,
                url = profile.imageUrl,
                fallbackRes = profile.imageResId
            )

            // Top Badges
            binding.tvMomentsCount.text = "${profile.momentsCount} Moments"
            binding.tvMatchPercentage.text = "${profile.matchPercentage}% Match"

            // Name, Age and Verification
            binding.tvNameAge.text = "${profile.name}, ${profile.age}"
            binding.ivVerifiedBadge.visibility = if (profile.isVerified) View.VISIBLE else View.GONE

            // Location
            binding.tvLocation.text = profile.location

            // Relationship Goal & Shared Interests
            binding.tvRelationshipGoal.text = profile.relationshipGoal
            binding.tvSharedInterests.text = "${profile.sharedInterestsCount} Shared Interests"

            val colorShared = android.graphics.Color.parseColor("#DE2D2D")
            val colorNormal = android.graphics.Color.parseColor("#333333")

            // Interest Chips
            val interests = profile.interests
            if (interests.isNotEmpty()) {
                binding.chip1.visibility = View.VISIBLE
                binding.tvChipEmoji1.text = interests[0].emoji
                binding.tvChipText1.text = interests[0].label
                binding.tvChipText1.setTextColor(if (interests[0].isShared) colorShared else colorNormal)
            } else {
                binding.chip1.visibility = View.GONE
            }

            if (interests.size > 1) {
                binding.chip2.visibility = View.VISIBLE
                binding.tvChipEmoji2.text = interests[1].emoji
                binding.tvChipText2.text = interests[1].label
                binding.tvChipText2.setTextColor(if (interests[1].isShared) colorShared else colorNormal)
            } else {
                binding.chip2.visibility = View.GONE
            }

            if (interests.size > 2) {
                // Auto-hide chip3 on narrow widths (< 360dp) to prevent card edge clipping
                val screenWidthDp = binding.root.resources.configuration.screenWidthDp
                if (screenWidthDp >= 360) {
                    binding.chip3.visibility = View.VISIBLE
                    binding.tvChipEmoji3.text = interests[2].emoji
                    binding.tvChipText3.text = interests[2].label
                    binding.tvChipText3.setTextColor(if (interests[2].isShared) colorShared else colorNormal)
                } else {
                    binding.chip3.visibility = View.GONE
                }
            } else {
                binding.chip3.visibility = View.GONE
            }

            binding.cardViewRoot.setOnClickListener {
                onCardClick?.invoke(profile)
            }
        }
    }
}
