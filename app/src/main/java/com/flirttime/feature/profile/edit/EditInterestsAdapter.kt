package com.flirttime.feature.profile.edit

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.databinding.ItemEditInterestBinding

data class EditInterestUiItem(
    val id: String = "",
    val name: String,
    val emoji: String,
    val isAddAction: Boolean = false
)

class EditInterestsAdapter(
    private val onItemClick: (EditInterestUiItem) -> Unit,
    private val onAddClick: () -> Unit
) : RecyclerView.Adapter<EditInterestsAdapter.InterestViewHolder>() {

    private val items = mutableListOf<EditInterestUiItem>()

    fun submitList(newItems: List<EditInterestUiItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InterestViewHolder {
        val binding = ItemEditInterestBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return InterestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: InterestViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class InterestViewHolder(
        private val binding: ItemEditInterestBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: EditInterestUiItem) {
            if (item.isAddAction) {
                binding.tvInterestEmoji.text = "➕"
                binding.tvInterestName.text = "Add"
                binding.tvInterestName.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.text_tertiary)
                )
                binding.cardInterest.setBackgroundResource(R.drawable.bg_dashed_photo_slot)
                binding.cardInterest.setOnClickListener { onAddClick() }
            } else {
                binding.tvInterestEmoji.text = item.emoji
                binding.tvInterestName.text = item.name
                binding.tvInterestName.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.text_primary)
                )
                binding.cardInterest.setCardBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.bg_surface_card)
                )
                binding.cardInterest.strokeColor = ContextCompat.getColor(itemView.context, R.color.border_subtle)
                binding.cardInterest.setOnClickListener { onItemClick(item) }
            }
        }
    }
}
