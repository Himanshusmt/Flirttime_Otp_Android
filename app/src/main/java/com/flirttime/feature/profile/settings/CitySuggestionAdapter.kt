package com.flirttime.feature.profile.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.core.location.CitySuggestion
import com.flirttime.databinding.ItemCitySuggestionBinding

class CitySuggestionAdapter(
    private val onSuggestionClick: (CitySuggestion) -> Unit
) : RecyclerView.Adapter<CitySuggestionAdapter.SuggestionViewHolder>() {

    private val items = mutableListOf<CitySuggestion>()

    fun submitList(suggestions: List<CitySuggestion>) {
        items.clear()
        items.addAll(suggestions)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val binding = ItemCitySuggestionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SuggestionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class SuggestionViewHolder(
        private val binding: ItemCitySuggestionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CitySuggestion) {
            binding.tvSuggestionTitle.text = item.title
            binding.tvSuggestionSubtitle.text = item.subtitle
            binding.root.setOnClickListener { onSuggestionClick(item) }
        }
    }
}
