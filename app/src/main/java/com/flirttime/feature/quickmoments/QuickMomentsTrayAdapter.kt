package com.flirttime.feature.quickmoments

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.core.common.IsoDate
import com.flirttime.core.common.ShimmerHelper
import com.flirttime.databinding.ItemProfileQuickMomentBinding

/**
 * Own Quick Moments row on the Profile tab. Port of iOS `ProfileQuickMomentsView.momentItem`:
 * gradient ring bubble, text moments rendered on their background colour, a Punch activity badge
 * (top reaction emoji + reactions/replies count) and a short "2h" age label.
 */
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
            val imageUrl = item.displayImageUrl
            if (imageUrl.isNullOrBlank()) {
                // iOS `isText`: no media path -> render the text on its background colour.
                binding.ivQuickMomentThumb.visibility = View.GONE
                binding.tvQuickMomentText.visibility = View.VISIBLE
                binding.tvQuickMomentText.text = item.effectiveText.orEmpty()
                binding.tvQuickMomentText.background = textBackground(item.backgroundColor)
            } else {
                binding.tvQuickMomentText.visibility = View.GONE
                binding.ivQuickMomentThumb.visibility = View.VISIBLE
                ShimmerHelper.loadWithShimmer(binding.ivQuickMomentThumb, imageUrl)
            }

            binding.tvQuickMomentLabel.text = IsoDate.timeAgo(item.createdAt)
                .ifBlank { item.relativeAge.orEmpty() }

            val activity = item.activityCount
            if (activity > 0) {
                val emoji = item.topReactionEmojis.firstOrNull() ?: "💬"
                binding.tvBadgeText.visibility = View.VISIBLE
                binding.tvBadgeText.text = "$emoji ${IsoDate.compactCount(activity)}"
            } else {
                binding.tvBadgeText.visibility = View.GONE
            }

            binding.root.contentDescription = buildString {
                append("Your quick moment")
                if (binding.tvQuickMomentLabel.text.isNotBlank()) append(", ").append(binding.tvQuickMomentLabel.text)
                item.effectiveText?.let { append(". ").append(it) }
                if (activity > 0) append(". ").append(activity).append(" reactions and replies")
            }
            binding.root.setOnClickListener { onStoryClick(item) }
        }

        /** The stored hex colour fading to a darker shade, like iOS `QuickMomentColors.gradient(for:)`. */
        private fun textBackground(hex: String?): GradientDrawable {
            val base = runCatching { Color.parseColor(hex ?: "") }.getOrDefault(Color.parseColor("#D92B2B"))
            val hsv = FloatArray(3)
            Color.colorToHSV(base, hsv)
            hsv[2] = hsv[2] * 0.7f
            val dark = Color.HSVToColor(hsv)
            return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(base, dark)).apply {
                shape = GradientDrawable.OVAL
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
