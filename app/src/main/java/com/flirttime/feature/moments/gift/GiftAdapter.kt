package com.flirttime.feature.moments.gift

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.databinding.ItemGiftCardBinding

class GiftAdapter(
    private val gifts: List<GiftItem>,
    private val onGiftSelected: (GiftItem?) -> Unit
) : RecyclerView.Adapter<GiftAdapter.GiftViewHolder>() {

    private var selectedPosition: Int = -1

    inner class GiftViewHolder(
        val binding: ItemGiftCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(gift: GiftItem, position: Int) {
            val context = binding.root.context

            binding.tvGiftEmoji.text = gift.emoji
            binding.tvGiftName.text = gift.name
            binding.tvGiftPrice.text = gift.coinPrice.toString()

            val isSelected = position == selectedPosition
            gift.isSelected = isSelected

            if (isSelected) {
                binding.cardContainer.setBackgroundResource(R.drawable.bg_gift_card_selected)
                binding.tvGiftName.setTextColor(ContextCompat.getColor(context, R.color.brand_primary))
            } else {
                binding.cardContainer.setBackgroundResource(R.drawable.bg_gift_card_unselected)
                binding.tvGiftName.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }

            binding.cardContainer.setOnClickListener {
                val previousPos = selectedPosition
                if (selectedPosition == position) {
                    // Deselect if already selected
                    selectedPosition = -1
                    notifyItemChanged(position)
                    onGiftSelected(null)
                } else {
                    selectedPosition = position
                    if (previousPos != -1) {
                        notifyItemChanged(previousPos)
                    }
                    notifyItemChanged(selectedPosition)
                    onGiftSelected(gift)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GiftViewHolder {
        val binding = ItemGiftCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GiftViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GiftViewHolder, position: Int) {
        holder.bind(gifts[position], position)
    }

    override fun getItemCount(): Int = gifts.size

    fun getSelectedGift(): GiftItem? {
        return if (selectedPosition in gifts.indices) gifts[selectedPosition] else null
    }

    fun clearSelection() {
        val prev = selectedPosition
        selectedPosition = -1
        if (prev != -1) {
            notifyItemChanged(prev)
        }
        onGiftSelected(null)
    }
}
