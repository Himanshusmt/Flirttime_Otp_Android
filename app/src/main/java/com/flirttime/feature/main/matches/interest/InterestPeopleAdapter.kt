package com.flirttime.feature.main.matches.interest

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.core.common.ShimmerHelper
import com.flirttime.databinding.ItemInterestPersonBinding

class InterestPeopleAdapter(
    private var people: List<InterestPersonItem>,
    private val onPersonClick: (InterestPersonItem) -> Unit = {}
) : RecyclerView.Adapter<InterestPeopleAdapter.PersonViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonViewHolder {
        val binding = ItemInterestPersonBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PersonViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PersonViewHolder, position: Int) {
        holder.bind(people[position])
    }

    override fun getItemCount(): Int = people.size

    fun updateList(newPeople: List<InterestPersonItem>) {
        people = newPeople
        notifyDataSetChanged()
    }

    inner class PersonViewHolder(
        private val binding: ItemInterestPersonBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: InterestPersonItem) {
            binding.tvPersonName.text = item.name
            binding.tvPersonCity.text = item.city

            // Load profile photo with shimmer as per project pattern
            ShimmerHelper.loadWithShimmer(binding.ivPersonPhoto, item.imageUrl)

            binding.root.setOnClickListener {
                onPersonClick(item)
            }
        }
    }
}
