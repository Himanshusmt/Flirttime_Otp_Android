package com.flirttime.feature.main.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.core.common.ShimmerHelper
import com.flirttime.databinding.ItemProfileMomentBinding
import com.flirttime.feature.moments.MomentDto

class ProfileMomentsAdapter(
    private val onMomentClick: ((MomentDto) -> Unit)? = null
) : ListAdapter<MomentDto, ProfileMomentsAdapter.ProfileMomentViewHolder>(MomentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileMomentViewHolder {
        val binding = ItemProfileMomentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProfileMomentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProfileMomentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProfileMomentViewHolder(
        private val binding: ItemProfileMomentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(moment: MomentDto) {
            // Media Image with Shimmer placeholder
            ShimmerHelper.loadWithShimmer(
                draweeView = binding.ivMomentMedia,
                url = moment.displayImageUrl
            )

            // Multiple media indicator icon (top-right)
            binding.ivMultiMedia.visibility = if (moment.isMultiMedia) View.VISIBLE else View.GONE

            // Caption (bottom)
            if (!moment.caption.isNullOrBlank()) {
                binding.tvCaption.visibility = View.VISIBLE
                binding.tvCaption.text = moment.caption
            } else {
                binding.tvCaption.visibility = View.GONE
            }

            // Stats
            binding.tvLikeCount.text = moment.effectiveLikes.toString()
            binding.tvCommentCount.text = moment.effectiveComments.toString()

            // Click listener
            binding.root.setOnClickListener {
                onMomentClick?.invoke(moment)
            }
        }
    }

    class MomentDiffCallback : DiffUtil.ItemCallback<MomentDto>() {
        override fun areItemsTheSame(oldItem: MomentDto, newItem: MomentDto): Boolean {
            val oldId = oldItem.id ?: oldItem.momentId
            val newId = newItem.id ?: newItem.momentId
            return if (oldId != null && newId != null) oldId == newId else oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: MomentDto, newItem: MomentDto): Boolean {
            return oldItem == newItem
        }
    }
}
