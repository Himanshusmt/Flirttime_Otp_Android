package com.flirttime.feature.main.presentation.moments.location

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.databinding.ItemVibeSpotBinding

class VibeSpotsAdapter(
    private val onSpotClick: (VibeSpotItem) -> Unit
) : ListAdapter<VibeSpotItem, VibeSpotsAdapter.SpotViewHolder>(SpotDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpotViewHolder {
        val binding = ItemVibeSpotBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SpotViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SpotViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SpotViewHolder(
        private val binding: ItemVibeSpotBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(spot: VibeSpotItem) {
            binding.tvSpotName.text = spot.name
            binding.tvSpotAddress.text = spot.address
            binding.tvSpotDistance.text = spot.distance

            binding.layoutSpotIconContainer.setBackgroundResource(spot.backgroundRes)
            binding.ivSpotIcon.setImageResource(spot.iconRes)

            binding.root.setOnClickListener {
                onSpotClick(spot)
            }
        }
    }

    companion object {
        private val SpotDiffCallback = object : DiffUtil.ItemCallback<VibeSpotItem>() {
            override fun areItemsTheSame(oldItem: VibeSpotItem, newItem: VibeSpotItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: VibeSpotItem, newItem: VibeSpotItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
