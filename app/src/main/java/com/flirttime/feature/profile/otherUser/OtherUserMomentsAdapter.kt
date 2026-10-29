package com.flirttime.feature.profile.otherUser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.core.common.FastImageHelper
import com.flirttime.databinding.ItemOtherUserMomentBinding
import com.flirttime.feature.moments.MomentDto

class OtherUserMomentsAdapter(
    private var moments: List<MomentDto> = emptyList(),
    private val onMomentClick: ((MomentDto) -> Unit)? = null,
    private val onSeeAllClick: (() -> Unit)? = null
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
        val isSeeAllSlot = position == 8 && moments.size > 9
        val remainingCount = if (moments.size > 8) moments.size - 8 else 0
        holder.bind(moments[position], isSeeAllSlot, remainingCount)
    }

    override fun getItemCount(): Int = minOf(moments.size, 9)

    inner class MomentViewHolder(private val binding: ItemOtherUserMomentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MomentDto, isSeeAllSlot: Boolean, remainingCount: Int) {
            val thumbUrl = item.thumbnailUrl
                ?: item.firstImageUrl
                ?: item.imageUrl
                ?: item.mediaUrl
                ?: item.media?.firstOrNull()?.thumbnailUrl
                ?: item.media?.firstOrNull()?.mediaUrl
                ?: ""

            FastImageHelper.loadDirect(
                draweeView = binding.ivMomentThumb,
                url = thumbUrl,
                fallbackRes = R.drawable.explore_placeholder
            )

            if (isSeeAllSlot) {
                binding.layoutSeeAllOverlay.visibility = View.VISIBLE
                binding.layoutCounters.visibility = View.GONE
                binding.viewScrim.visibility = View.GONE
                binding.tvSeeAllCount.text = "+$remainingCount"

                binding.root.setOnClickListener {
                    onSeeAllClick?.invoke()
                }
            } else {
                binding.layoutSeeAllOverlay.visibility = View.GONE
                binding.layoutCounters.visibility = View.VISIBLE
                binding.viewScrim.visibility = View.VISIBLE

                binding.tvMomentLikes.text = "${item.effectiveLikes}"
                binding.tvMomentComments.text = "${item.effectiveComments}"

                binding.root.setOnClickListener {
                    onMomentClick?.invoke(item)
                }
            }
        }
    }
}
