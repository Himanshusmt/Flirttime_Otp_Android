package com.flirttime.feature.quickmoments

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.core.common.MediaUrlHelper
import com.flirttime.databinding.ItemQuickMomentAddCardBinding
import com.flirttime.databinding.ItemQuickMomentStoryCardBinding

class QuickMomentsCardAdapter(
    private val onAddNewClick: () -> Unit,
    private val onStoryClick: (QuickMomentCardItem.StoryCard) -> Unit
) : ListAdapter<QuickMomentCardItem, RecyclerView.ViewHolder>(QuickMomentCardDiffCallback()) {

    companion object {
        private const val TYPE_ADD_NEW = 0
        private const val TYPE_STORY = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is QuickMomentCardItem.AddNewCard -> TYPE_ADD_NEW
            is QuickMomentCardItem.StoryCard -> TYPE_STORY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_ADD_NEW -> {
                val binding = ItemQuickMomentAddCardBinding.inflate(inflater, parent, false)
                AddNewViewHolder(binding)
            }
            else -> {
                val binding = ItemQuickMomentStoryCardBinding.inflate(inflater, parent, false)
                StoryViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is QuickMomentCardItem.AddNewCard -> (holder as AddNewViewHolder).bind(item)
            is QuickMomentCardItem.StoryCard -> (holder as StoryViewHolder).bind(item)
        }
    }

    inner class AddNewViewHolder(
        private val binding: ItemQuickMomentAddCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: QuickMomentCardItem.AddNewCard) {
            val resolvedUrl = MediaUrlHelper.resolveMediaUrl(item.userProfilePhoto)
            if (!resolvedUrl.isNullOrBlank()) {
                binding.ivAddCardBackground.setImageURI(resolvedUrl)
            } else {
                binding.ivAddCardBackground.setActualImageResource(R.drawable.ic_settings_person)
            }

            binding.root.setOnClickListener { onAddNewClick() }
            binding.btnCenterAdd.setOnClickListener { onAddNewClick() }
        }
    }

    inner class StoryViewHolder(
        private val binding: ItemQuickMomentStoryCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: QuickMomentCardItem.StoryCard) {
            // Background & Content
            val resolvedMedia = MediaUrlHelper.resolveMediaUrl(item.mediaUrl)
            if (!resolvedMedia.isNullOrBlank()) {
                binding.ivStoryMedia.visibility = View.VISIBLE
                binding.viewScrim.visibility = View.VISIBLE
                binding.ivStoryMedia.setImageURI(resolvedMedia)
                binding.tvStoryContent.visibility = if (!item.textContent.isNullOrBlank()) View.VISIBLE else View.GONE
                binding.tvStoryContent.text = item.textContent.orEmpty()
            } else {
                binding.ivStoryMedia.visibility = View.GONE
                binding.viewScrim.visibility = View.GONE
                if (!item.textContent.isNullOrBlank()) {
                    binding.tvStoryContent.visibility = View.VISIBLE
                    binding.tvStoryContent.text = item.textContent
                } else {
                    binding.tvStoryContent.visibility = View.GONE
                }

                if (!item.backgroundColor.isNullOrBlank()) {
                    try {
                        val parsedColor = Color.parseColor(item.backgroundColor)
                        val drawable = GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(parsedColor, parsedColor)
                        ).apply {
                            cornerRadius = 16f * binding.root.context.resources.displayMetrics.density
                        }
                        binding.layoutCardBackground.background = drawable
                    } catch (_: Exception) {
                        binding.layoutCardBackground.setBackgroundResource(R.drawable.bg_gradient_story_red)
                    }
                } else {
                    binding.layoutCardBackground.setBackgroundResource(R.drawable.bg_gradient_story_red)
                }
            }

            // Bottom Info: Avatar, Name, Time
            val resolvedAvatar = MediaUrlHelper.resolveMediaUrl(item.userAvatar)
            if (!resolvedAvatar.isNullOrBlank()) {
                binding.ivAuthorAvatar.setImageURI(resolvedAvatar)
            } else {
                binding.ivAuthorAvatar.setActualImageResource(R.drawable.ic_settings_person)
            }

            binding.tvAuthorName.text = item.userName
            binding.tvStoryTime.text = item.relativeAge ?: "1m"

            binding.root.setOnClickListener {
                onStoryClick(item)
            }
        }
    }

    class QuickMomentCardDiffCallback : DiffUtil.ItemCallback<QuickMomentCardItem>() {
        override fun areItemsTheSame(oldItem: QuickMomentCardItem, newItem: QuickMomentCardItem): Boolean {
            return when {
                oldItem is QuickMomentCardItem.AddNewCard && newItem is QuickMomentCardItem.AddNewCard -> true
                oldItem is QuickMomentCardItem.StoryCard && newItem is QuickMomentCardItem.StoryCard -> oldItem.storyId == newItem.storyId
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: QuickMomentCardItem, newItem: QuickMomentCardItem): Boolean {
            return oldItem == newItem
        }
    }
}
