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
            binding.tvInterestIcon.text = item.icon ?: "✨"
            binding.tvInterestName.text = item.name.orEmpty()

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
    }
}
