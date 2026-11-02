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

data class ProfileMomentUiItem(
    val moment: MomentDto,
    val isOverflow: Boolean = false,
    val overflowCount: Int = 0
)

class ProfileMomentsAdapter(
    private val onMomentClick: ((MomentDto) -> Unit)? = null,
    private val onSeeAllClick: (() -> Unit)? = null
) : ListAdapter<ProfileMomentUiItem, ProfileMomentsAdapter.ProfileMomentViewHolder>(MomentDiffCallback()) {

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

        fun bind(item: ProfileMomentUiItem) {
            val moment = item.moment

            // Media Image with Shimmer placeholder
            ShimmerHelper.loadWithShimmer(
                draweeView = binding.ivMomentMedia,
                url = moment.displayImageUrl
            )

            if (item.isOverflow) {
                binding.layoutOverflowSeeAll.visibility = View.VISIBLE
                binding.tvOverflowCount.text = "+${item.overflowCount}"
                binding.layoutBottomDetails.visibility = View.GONE
                binding.viewBottomScrim.visibility = View.GONE
                binding.ivMultiMedia.visibility = View.GONE

                binding.root.setOnClickListener {
                    onSeeAllClick?.invoke()
                }
            } else {
                binding.layoutOverflowSeeAll.visibility = View.GONE
                binding.layoutBottomDetails.visibility = View.VISIBLE
                binding.viewBottomScrim.visibility = View.VISIBLE

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
    }

    class MomentDiffCallback : DiffUtil.ItemCallback<ProfileMomentUiItem>() {
        override fun areItemsTheSame(oldItem: ProfileMomentUiItem, newItem: ProfileMomentUiItem): Boolean {
            if (oldItem.isOverflow != newItem.isOverflow) return false
            val oldId = oldItem.moment.id ?: oldItem.moment.momentId
            val newId = newItem.moment.id ?: newItem.moment.momentId
            return if (oldId != null && newId != null) oldId == newId else oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: ProfileMomentUiItem, newItem: ProfileMomentUiItem): Boolean {
            return oldItem == newItem
        }
    }
}
