package com.flirttime.feature.profile.otherUser

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.core.common.ShimmerHelper
import com.flirttime.databinding.ItemOtherUserMomentBinding
import com.flirttime.feature.moments.MomentDto

class OtherUserMomentsAdapter(
    private var moments: List<MomentDto> = emptyList(),
    private val onMomentClick: ((MomentDto) -> Unit)? = null
) : RecyclerView.Adapter<OtherUserMomentsAdapter.MomentViewHolder>() {

    fun submitList(newMoments: List<MomentDto>) {
        moments = newMoments
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MomentViewHolder {
        val binding = ItemOtherUserMomentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MomentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MomentViewHolder, position: Int) {
        holder.bind(moments[position])
    }

    override fun getItemCount(): Int = moments.size

    inner class MomentViewHolder(private val binding: ItemOtherUserMomentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MomentDto) {
            val thumbUrl = item.thumbnailUrl
                ?: item.firstImageUrl
                ?: item.media?.firstOrNull()?.thumbnailUrl
                ?: item.media?.firstOrNull()?.mediaUrl
                ?: ""

            ShimmerHelper.loadWithShimmer(
                draweeView = binding.ivMomentThumb,
                url = thumbUrl,
                fallbackRes = R.drawable.img_login
            )

            binding.tvMomentLikes.text = "${item.effectiveLikes}"

            binding.root.setOnClickListener {
                onMomentClick?.invoke(item)
            }
        }
    }
}
