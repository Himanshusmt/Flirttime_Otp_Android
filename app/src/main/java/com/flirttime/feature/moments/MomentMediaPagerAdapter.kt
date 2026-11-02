package com.flirttime.feature.moments

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.core.common.FastImageHelper
import com.flirttime.databinding.ItemMomentMediaPageBinding

class MomentMediaPagerAdapter(
    private var mediaList: List<String> = emptyList(),
    private var thumbnailList: List<String> = emptyList(),
    private var fallbackRes: Int? = null
) : RecyclerView.Adapter<MomentMediaPagerAdapter.MediaViewHolder>() {

    fun setMedia(items: List<String>, thumbs: List<String> = emptyList(), fallback: Int? = null) {
        mediaList = items
        thumbnailList = thumbs
        fallbackRes = fallback
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMomentMediaPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val url = mediaList.getOrNull(position)
        FastImageHelper.loadDirect(
            draweeView = holder.binding.ivMediaPage,
            url = url,
            thumbnailUrl = thumbnailList.getOrNull(position),
            fallbackRes = fallbackRes ?: 0,
            grayPlaceholder = true
        )
    }

    override fun getItemCount(): Int = mediaList.size

    inner class MediaViewHolder(val binding: ItemMomentMediaPageBinding) :
        RecyclerView.ViewHolder(binding.root)
}
