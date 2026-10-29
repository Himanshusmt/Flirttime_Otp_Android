package com.flirttime.feature.moments

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.core.common.ShimmerHelper
import com.flirttime.databinding.ItemMomentMediaPageBinding

class MomentMediaPagerAdapter(
    private var mediaList: List<String> = emptyList(),
    private var fallbackRes: Int? = null
) : RecyclerView.Adapter<MomentMediaPagerAdapter.MediaViewHolder>() {

    fun setMedia(items: List<String>, fallback: Int? = null) {
        mediaList = items
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
        ShimmerHelper.loadWithShimmer(
            draweeView = holder.binding.ivMediaPage,
            url = url,
            fallbackRes = fallbackRes
        )
    }

    override fun getItemCount(): Int = mediaList.size

    inner class MediaViewHolder(val binding: ItemMomentMediaPageBinding) :
        RecyclerView.ViewHolder(binding.root)
}
