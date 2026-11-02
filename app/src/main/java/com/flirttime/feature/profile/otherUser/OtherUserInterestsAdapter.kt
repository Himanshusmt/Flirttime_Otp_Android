package com.flirttime.feature.profile.otherUser

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.databinding.ItemOtherUserInterestBinding
import com.flirttime.feature.profile.UserInterestDto

class OtherUserInterestsAdapter(
    private var interests: List<UserInterestDto> = emptyList()
) : RecyclerView.Adapter<OtherUserInterestsAdapter.InterestViewHolder>() {

    fun submitList(newInterests: List<UserInterestDto>) {
        interests = newInterests
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InterestViewHolder {
        val binding = ItemOtherUserInterestBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return InterestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: InterestViewHolder, position: Int) {
        holder.bind(interests[position])
    }

    override fun getItemCount(): Int = interests.size

    class InterestViewHolder(private val binding: ItemOtherUserInterestBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: UserInterestDto) {
            val name = item.name.orEmpty()
            binding.tvInterestName.text = name

            val icon = resolveInterestEmoji(name, item.emoji ?: item.icon)
            binding.tvInterestIcon.text = icon

            val isCommon = item.isCommon == true
            val context = binding.root.context
            if (isCommon) {
                binding.rootInterestChip.setBackgroundResource(R.drawable.bg_interest_chip_pill_common)
                binding.tvInterestName.setTextColor(ContextCompat.getColor(context, R.color.brand_red))
            } else {
                binding.rootInterestChip.setBackgroundResource(R.drawable.bg_interest_chip_pill)
                binding.tvInterestName.setTextColor(0xFF333333.toInt())
            }
        }

        private fun resolveInterestEmoji(name: String, provided: String?): String {
            if (!provided.isNullOrBlank()) return provided
            val lower = name.lowercase().trim()
            return when {
                lower.contains("exercise") -> "🏋️"
                lower.contains("shop") -> "🛍️"
                lower.contains("game") || lower.contains("gaming") -> "🎮"
                lower.contains("fitness") || lower.contains("gym") -> "💪"
                lower.contains("sport") -> "⚽"
                lower.contains("music") -> "🎵"
                lower.contains("travel") -> "✈️"
                lower.contains("read") || lower.contains("book") -> "📚"
                lower.contains("cook") || lower.contains("food") -> "🍳"
                lower.contains("photo") -> "📸"
                lower.contains("coffee") -> "☕"
                lower.contains("movie") || lower.contains("cinema") -> "🎬"
                lower.contains("art") || lower.contains("draw") -> "🎨"
                lower.contains("dance") -> "💃"
                lower.contains("nature") || lower.contains("hike") -> "🌿"
                lower.contains("pet") || lower.contains("dog") || lower.contains("cat") -> "🐾"
                else -> "✨"
            }
        }
    }
}
