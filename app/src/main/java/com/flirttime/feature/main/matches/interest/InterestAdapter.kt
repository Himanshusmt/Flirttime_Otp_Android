package com.flirttime.feature.main.matches.interest

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.databinding.ItemInterestCardBinding

class InterestAdapter(
    private var items: List<InterestItem>,
    private val onInterestSelected: (InterestItem) -> Unit
) : RecyclerView.Adapter<InterestAdapter.InterestViewHolder>() {

    private var selectedIndex = items.indexOfFirst { it.isSelected }.takeIf { it != -1 } ?: 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InterestViewHolder {
        val binding = ItemInterestCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return InterestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: InterestViewHolder, position: Int) {
        holder.bind(items[position], position == selectedIndex)
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<InterestItem>) {
        items = newItems
        selectedIndex = items.indexOfFirst { it.isSelected }.takeIf { it != -1 } ?: 0
        notifyDataSetChanged()
    }

    fun getSelectedItem(): InterestItem? = items.getOrNull(selectedIndex)

    inner class InterestViewHolder(
        private val binding: ItemInterestCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: InterestItem, isSelected: Boolean) {
            val context = binding.root.context
            val density = context.resources.displayMetrics.density

            binding.tvInterestEmoji.text = item.emoji
            binding.tvInterestName.text = item.name
            binding.tvInterestCategory.text = item.category

            // Pastel Icon Squircle Background
            val iconDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14f * density
                setColor(item.iconBgColor)
            }
            binding.layoutIconBg.background = iconDrawable

            // Card Selection State Styling (Pure White unselected / Warm Peach outline selected)
            if (isSelected) {
                binding.cardInterest.setBackgroundResource(R.drawable.bg_interest_card_selected)
            } else {
                binding.cardInterest.setBackgroundResource(R.drawable.bg_interest_card_unselected)
            }

            binding.root.setOnClickListener {
                val previousIndex = selectedIndex
                val currentIndex = adapterPosition
                if (currentIndex != RecyclerView.NO_POSITION && currentIndex != selectedIndex) {
                    items[previousIndex].isSelected = false
                    items[currentIndex].isSelected = true
                    selectedIndex = currentIndex
                    notifyItemChanged(previousIndex)
                    notifyItemChanged(currentIndex)
                    onInterestSelected(items[currentIndex])
                }
            }
        }
    }
}
