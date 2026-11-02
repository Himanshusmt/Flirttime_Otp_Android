package com.flirttime.feature.chat.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.core.common.MediaUrlHelper
import com.flirttime.databinding.ItemForwardRecipientBinding
import com.flirttime.feature.chat.model.ForwardRecipientUi

/**
 * Multi-select list for the forward picker.
 *
 * Each row keeps its own selected flag so DiffUtil repaints the checkmark without a full
 * `notifyDataSetChanged`.
 */
class ForwardRecipientAdapter(
    private val onToggle: (ForwardRecipientUi) -> Unit
) : ListAdapter<ForwardRecipientAdapter.Row, ForwardRecipientAdapter.Holder>(DIFF) {

    data class Row(val recipient: ForwardRecipientUi, val selected: Boolean)

    fun submit(recipients: List<ForwardRecipientUi>, selectedIds: Set<String>) {
        submitList(recipients.map { Row(it, it.userId in selectedIds) })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemForwardRecipientBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return Holder(binding, onToggle)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    class Holder(
        private val binding: ItemForwardRecipientBinding,
        private val onToggle: (ForwardRecipientUi) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: Row) {
            val avatarUrl = MediaUrlHelper.resolveMediaUrl(row.recipient.avatarUrl)
            if (!avatarUrl.isNullOrBlank()) {
                binding.ivAvatar.setImageURI(avatarUrl)
            } else {
                binding.ivAvatar.setActualImageResource(R.drawable.img_login)
            }

            binding.tvName.text = row.recipient.name
            val userName = row.recipient.userName
            if (userName.isNullOrBlank()) {
                binding.tvUserName.visibility = View.GONE
            } else {
                binding.tvUserName.visibility = View.VISIBLE
                binding.tvUserName.text = if (userName.startsWith("@")) userName else "@$userName"
            }

            binding.ivCheck.setImageResource(
                if (row.selected) R.drawable.ic_action_check_circle
                else R.drawable.ic_action_circle_outline
            )
            binding.root.setOnClickListener { onToggle(row.recipient) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(oldItem: Row, newItem: Row): Boolean =
                oldItem.recipient.userId == newItem.recipient.userId

            override fun areContentsTheSame(oldItem: Row, newItem: Row): Boolean =
                oldItem == newItem
        }
    }
}
