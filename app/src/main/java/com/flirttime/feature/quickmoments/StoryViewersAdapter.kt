package com.flirttime.feature.quickmoments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.core.common.MediaUrlHelper
import com.flirttime.databinding.ItemStoryViewerBinding

class StoryViewersAdapter(
    private var viewers: List<StoryViewerItemDto> = emptyList(),
    private var reactionsMap: Map<String, String> = emptyMap(),
    private val onViewerClick: ((String) -> Unit)? = null
) : RecyclerView.Adapter<StoryViewersAdapter.ViewerViewHolder>() {

    fun submitData(
        newViewers: List<StoryViewerItemDto>,
        reactions: Map<String, String> = emptyMap()
    ) {
        this.viewers = newViewers
        this.reactionsMap = reactions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewerViewHolder {
        val binding = ItemStoryViewerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewerViewHolder, position: Int) {
        holder.bind(viewers[position])
    }

    override fun getItemCount(): Int = viewers.size

    inner class ViewerViewHolder(
        private val binding: ItemStoryViewerBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StoryViewerItemDto) {
            val user = item.viewer
            val name = user?.effectiveName ?: "Viewer"
            val photo = user?.profilePhoto
            val time = item.timeAgo ?: "recently"
            val userId = user?.userId

            binding.tvViewerName.text = name
            binding.tvViewerTimeAgo.text = time

            val resolvedPhoto = MediaUrlHelper.resolveMediaUrl(photo)
            if (!resolvedPhoto.isNullOrBlank()) {
                binding.ivViewerAvatar.setImageURI(resolvedPhoto)
            } else {
                binding.ivViewerAvatar.setActualImageResource(R.drawable.ic_settings_person)
            }

            // If this user reacted, show their emoji badge
            val userReaction = if (!userId.isNullOrBlank()) reactionsMap[userId] else null
            if (!userReaction.isNullOrBlank()) {
                binding.tvViewerReactionEmoji.text = userReaction
                binding.tvViewerReactionEmoji.visibility = View.VISIBLE
            } else {
                binding.tvViewerReactionEmoji.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                if (!userId.isNullOrBlank()) {
                    onViewerClick?.invoke(userId)
                }
            }
        }
    }
}
