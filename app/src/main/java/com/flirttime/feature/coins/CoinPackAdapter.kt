package com.flirttime.feature.coins

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.databinding.ItemCoinPackBinding

class CoinPackAdapter(
    private val onBuy: (CoinPackOption) -> Unit
) : RecyclerView.Adapter<CoinPackAdapter.Holder>() {

    private var items: List<CoinPackOption> = emptyList()
    private var purchasingId: Int? = null

    fun submit(options: List<CoinPackOption>, purchasingPackId: Int?) {
        items = options
        purchasingId = purchasingPackId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemCoinPackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class Holder(private val binding: ItemCoinPackBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(option: CoinPackOption) {
            val pack = option.pack
            binding.tvCoins.text = pack.totalCoins.toString()
            binding.tvPrice.text = option.displayPrice
            val bonus = pack.bonusCoins ?: 0
            binding.tvBonus.visibility = if (bonus > 0) View.VISIBLE else View.GONE
            binding.tvBonus.text = "+$bonus bonus"
            when {
                option.isPopular -> {
                    binding.tvTag.visibility = View.VISIBLE
                    binding.tvTag.text = "Popular"
                }
                option.isBestValue -> {
                    binding.tvTag.visibility = View.VISIBLE
                    binding.tvTag.text = "Best value"
                }
                else -> binding.tvTag.visibility = View.GONE
            }
            val buying = purchasingId != null && purchasingId == pack.id
            binding.progressBuy.visibility = if (buying) View.VISIBLE else View.GONE
            binding.root.isEnabled = purchasingId == null
            binding.root.alpha = if (purchasingId == null || buying) 1f else 0.5f
            binding.root.setOnClickListener { onBuy(option) }
        }
    }
}
