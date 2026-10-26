package com.flirttime.feature.profile.presentation.dialog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.databinding.RvYearsBinding

class DobDialogYearAdapter(
    private val years: List<String>,
    private val onYearClick: (year: String, position: Int) -> Unit
) : RecyclerView.Adapter<DobDialogYearAdapter.DobDialogYearViewHolder>() {

    class DobDialogYearViewHolder(val binding: RvYearsBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DobDialogYearViewHolder {
        val binding = RvYearsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DobDialogYearViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DobDialogYearViewHolder, position: Int) {
        val year = years[position]
        holder.binding.yearTv.text = year
        holder.itemView.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onYearClick(years[pos], pos)
            }
        }
    }

    override fun getItemCount(): Int = years.size
}
