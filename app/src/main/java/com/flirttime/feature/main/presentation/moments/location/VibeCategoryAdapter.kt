package com.flirttime.feature.main.presentation.moments.location

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.databinding.ItemVibeCategoryPillBinding

data class VibeCategory(
    val id: String,
    val emoji: String,
    val label: String,
    val category: SpotCategory
)

class VibeCategoryAdapter(
    private val categories: List<VibeCategory>,
    private val onCategorySelected: (SpotCategory) -> Unit
) : RecyclerView.Adapter<VibeCategoryAdapter.CategoryViewHolder>() {

    private var selectedCategory: SpotCategory = SpotCategory.ALL

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemVibeCategoryPillBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size

    fun setSelectedCategory(category: SpotCategory) {
        selectedCategory = category
        notifyDataSetChanged()
    }

    inner class CategoryViewHolder(
        private val binding: ItemVibeCategoryPillBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VibeCategory) {
            binding.tvCategoryEmoji.text = item.emoji
            binding.tvCategoryLabel.text = item.label

            val isSelected = (item.category == selectedCategory)
            binding.layoutPillRoot.isSelected = isSelected

            val context = binding.root.context
            if (isSelected) {
                binding.tvCategoryLabel.setTextColor(Color.WHITE)
            } else {
                binding.tvCategoryLabel.setTextColor(Color.parseColor("#2A2A2A"))
            }

            binding.root.setOnClickListener {
                val newSelection = if (selectedCategory == item.category) SpotCategory.ALL else item.category
                selectedCategory = newSelection
                notifyDataSetChanged()
                onCategorySelected(selectedCategory)
            }
        }
    }
}
