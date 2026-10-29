package com.flirttime.feature.profile.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.databinding.ItemReportedUserBinding
import com.flirttime.feature.profile.ReportUserDto

class ReportedUsersAdapter : ListAdapter<ReportUserDto, ReportedUsersAdapter.ReportViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val binding = ItemReportedUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ReportViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ReportViewHolder(
        private val binding: ItemReportedUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ReportUserDto) {
            val name = item.targetName ?: if (item.targetType == "moment") "Reported Moment" else "Reported Profile"
            binding.tvReportName.text = name

            val reason = item.description ?: item.reason ?: "Report submitted"
            binding.tvReportReason.text = reason

            val photoUrl = item.targetPhoto ?: item.momentThumbnail
            if (!photoUrl.isNullOrBlank()) {
                binding.ivReportAvatar.setImageURI(photoUrl)
            } else {
                binding.ivReportAvatar.setActualImageResource(R.drawable.img_login)
            }

            val statusText = when (item.status?.lowercase()) {
                "reviewed" -> "Reviewed"
                "resolved" -> "Resolved"
                "dismissed" -> "Dismissed"
                else -> "Pending"
            }
            binding.tvReportStatus.text = statusText
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ReportUserDto>() {
            override fun areItemsTheSame(oldItem: ReportUserDto, newItem: ReportUserDto): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ReportUserDto, newItem: ReportUserDto): Boolean {
                return oldItem == newItem
            }
        }
    }
}
