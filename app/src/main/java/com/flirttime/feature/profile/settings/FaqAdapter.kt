package com.flirttime.feature.profile.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.databinding.ItemFaqRowBinding

class FaqAdapter(
    private val items: List<FAQItem>,
    private val onToggle: (Int) -> Unit
) : RecyclerView.Adapter<FaqAdapter.FaqViewHolder>() {

    var expandedIndex: Int? = null
        set(value) {
            val previous = field
            field = value
            previous?.let { notifyItemChanged(it) }
            value?.let { if (it != previous) notifyItemChanged(it) }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FaqViewHolder {
        val binding = ItemFaqRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FaqViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FaqViewHolder, position: Int) {
        holder.bind(items[position], expandedIndex == position)
    }

    override fun getItemCount(): Int = items.size

    inner class FaqViewHolder(
        private val binding: ItemFaqRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FAQItem, isExpanded: Boolean) {
            binding.tvQuestion.text = item.question
            binding.tvAnswer.text = item.answer
            binding.tvAnswer.isVisible = isExpanded
            val targetRotation = if (isExpanded) 180f else 0f
            binding.ivChevron.animate().cancel()
            binding.ivChevron.animate().rotation(targetRotation).setDuration(200).start()
            binding.root.contentDescription = item.question
            binding.root.setOnClickListener {
                onToggle(adapterPosition)
            }
        }
    }
}
