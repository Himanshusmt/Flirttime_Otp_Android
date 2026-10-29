package com.flirttime.feature.quickmoments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.core.common.ShimmerHelper
import com.flirttime.databinding.ItemProfileQuickMomentBinding

class QuickMomentsTrayAdapter(
    private val onStoryClick: (QuickMomentSlideDto) -> Unit
) : ListAdapter<QuickMomentSlideDto, QuickMomentsTrayAdapter.QuickMomentViewHolder>(QuickMomentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickMomentViewHolder {
        val binding = ItemProfileQuickMomentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QuickMomentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuickMomentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class QuickMomentViewHolder(
        private val binding: ItemProfileQuickMomentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: QuickMomentSlideDto) {
            val url = item.displayImageUrl
            if (!url.isNullOrBlank()) {
                ShimmerHelper.loadWithShimmer(binding.ivQuickMomentThumb, url)
            } else {
                binding.ivQuickMomentThumb.setImageURI(null as String?)
            }

            val label = item.relativeAge ?: item.caption?.take(10) ?: "Story"
            binding.tvQuickMomentLabel.text = label

            val reactionCount = item.reactionsCount ?: 0
            val viewCount = item.viewsCount ?: 0
            if (reactionCount > 0) {
                binding.layoutBadge.visibility = View.VISIBLE
                binding.tvBadgeText.text = "❤️ $reactionCount"
            } else if (viewCount > 0) {
                binding.layoutBadge.visibility = View.VISIBLE
                binding.tvBadgeText.text = "👁 $viewCount"
            } else {
                binding.layoutBadge.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onStoryClick(item)
            }
        }
    }

    class QuickMomentDiffCallback : DiffUtil.ItemCallback<QuickMomentSlideDto>() {
        override fun areItemsTheSame(oldItem: QuickMomentSlideDto, newItem: QuickMomentSlideDto): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: QuickMomentSlideDto, newItem: QuickMomentSlideDto): Boolean {
            return oldItem == newItem
        }
    }
}
