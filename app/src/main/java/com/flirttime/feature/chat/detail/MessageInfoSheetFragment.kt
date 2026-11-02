package com.flirttime.feature.chat.detail

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.facebook.drawee.view.SimpleDraweeView
import com.flirttime.R
import com.flirttime.core.common.MediaUrlHelper
import com.flirttime.databinding.DialogMessageInfoBinding
import com.flirttime.databinding.ItemMessageInfoRowBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * “Message Info” sheet — the Android twin of the iOS `MessageInfoSheetViewController`.
 *
 * Lists every other chat participant and the furthest receipt the message reached for them
 * (Read / Delivered / Sent), read from `statuses[]` on the message payload.
 */
class MessageInfoSheetFragment : BottomSheetDialogFragment() {

    /** One participant row: who they are and how far the message got for them. */
    data class Row(
        val name: String,
        val avatarUrl: String? = null,
        val status: String = STATUS_PENDING,
        val isGroup: Boolean = false
    )

    private var _binding: DialogMessageInfoBinding? = null
    private val binding get() = _binding!!

    private var preview: String = ""
    private var rows: List<Row> = emptyList()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            sheet.setBackgroundResource(R.drawable.bg_bottom_sheet_top)
            val behavior = BottomSheetBehavior.from(sheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogMessageInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = arguments ?: return
        preview = args.getString(ARG_PREVIEW).orEmpty()
        rows = runCatching {
            val raw = args.getString(ARG_ROWS).orEmpty()
            val type = object : TypeToken<List<Row>>() {}.type
            Gson().fromJson<List<Row>>(raw, type)
        }.getOrNull().orEmpty()

        val sorted = rows.sortedBy { statusRank(it.status) }
        val readCount = sorted.count { it.status == STATUS_READ }
        val deliveredCount = sorted.count { it.status == STATUS_DELIVERED }

        binding.tvInfoPreview.isVisible = preview.isNotBlank()
        binding.tvInfoPreview.text = preview
        binding.tvInfoSummary.text = summaryText(sorted.size, readCount, deliveredCount)
        binding.tvInfoEmpty.isVisible = sorted.isEmpty()
        binding.rvMessageInfo.isVisible = sorted.isNotEmpty()

        binding.rvMessageInfo.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMessageInfo.adapter = InfoAdapter()
        (binding.rvMessageInfo.adapter as InfoAdapter).submitList(sorted)
    }

    private fun summaryText(total: Int, readCount: Int, deliveredCount: Int): String = when {
        total == 0 -> "No delivery details yet"
        readCount == total -> "Read by everyone"
        readCount > 0 -> "Read by $readCount of $total"
        deliveredCount == total -> "Delivered to everyone"
        deliveredCount > 0 -> "Delivered to $deliveredCount of $total"
        else -> "Sent"
    }

    private fun statusRank(status: String): Int = when (status) {
        STATUS_READ -> 0
        STATUS_DELIVERED -> 1
        STATUS_SENT -> 2
        else -> 3
    }

    private fun statusLabel(status: String): String = when (status) {
        STATUS_READ -> "Read"
        STATUS_DELIVERED -> "Delivered"
        STATUS_SENT -> "Sent"
        else -> "Pending"
    }

    private inner class InfoAdapter :
        RecyclerView.Adapter<InfoAdapter.Holder>() {

        private val items = mutableListOf<Row>()

        fun submitList(next: List<Row>) {
            items.clear()
            items.addAll(next)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val inflater = LayoutInflater.from(parent.context)
            return Holder(ItemMessageInfoRowBinding.inflate(inflater, parent, false))
        }

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

        override fun getItemCount(): Int = items.size

        inner class Holder(private val itemBinding: ItemMessageInfoRowBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(row: Row) {
                itemBinding.tvInfoName.text = row.name.takeIf { it.isNotBlank() } ?: "Member"
                itemBinding.tvInfoStatus.text = statusLabel(row.status)
                itemBinding.ivInfoStatusIcon.setImageResource(
                    when (row.status) {
                        STATUS_READ -> R.drawable.ic_action_check_circle
                        STATUS_DELIVERED -> R.drawable.ic_double_check_red
                        else -> R.drawable.ic_action_circle_outline
                    }
                )
                val avatar: SimpleDraweeView = itemBinding.ivInfoAvatar
                val avatarUrl = MediaUrlHelper.resolveMediaUrl(row.avatarUrl)
                if (!avatarUrl.isNullOrBlank()) {
                    avatar.setImageURI(avatarUrl)
                } else {
                    avatar.setActualImageResource(R.drawable.img_login)
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "MessageInfoSheet"

        const val STATUS_READ = "read"
        const val STATUS_DELIVERED = "delivered"
        const val STATUS_SENT = "sent"
        const val STATUS_PENDING = "pending"

        private const val ARG_PREVIEW = "arg_preview"
        private const val ARG_ROWS = "arg_rows"

        fun create(preview: String, rows: List<Row>): MessageInfoSheetFragment =
            MessageInfoSheetFragment().apply {
                arguments = bundleOf(
                    ARG_PREVIEW to preview,
                    ARG_ROWS to Gson().toJson(rows)
                )
            }
    }
}
