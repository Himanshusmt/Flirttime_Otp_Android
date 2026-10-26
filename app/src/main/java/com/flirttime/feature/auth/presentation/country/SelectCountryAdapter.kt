package com.flirttime.feature.auth.presentation.country

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.databinding.RvSearchcountryBinding

data class CountryUiModel(
    val name: String,
    val phoneCode: String,
    val countryCode: String,
    val flagUrl: String? = null,
    val flagResId: Int? = null,
    var isSelected: Boolean = false
)

class SelectCountryAdapter(
    private var list: List<CountryUiModel>,
    private val onItemClick: (CountryUiModel) -> Unit
) : RecyclerView.Adapter<SelectCountryAdapter.ViewHolder>() {

    fun updateList(newList: List<CountryUiModel>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RvSearchcountryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.binding.tvCountry.text = "${item.name} (${item.phoneCode})"

        if (item.flagResId != null) {
            holder.binding.civ.setImageResource(item.flagResId)
        } else {
            holder.binding.civ.setImageResource(R.drawable.img_flag_placeholder)
        }

        if (item.isSelected) {
            holder.binding.imgFlag.setImageResource(R.drawable.img_country_selected)
        } else {
            holder.binding.imgFlag.setImageResource(R.drawable.img_notselect_country)
        }

        holder.binding.root.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = list.size

    class ViewHolder(val binding: RvSearchcountryBinding) : RecyclerView.ViewHolder(binding.root)
}
