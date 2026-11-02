package com.flirttime.feature.chat.detail

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.databinding.DialogEmojiPickerBinding
import com.flirttime.databinding.ItemEmojiBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Full emoji grid opened from the “+” button of the reaction bar.
 *
 * Mirrors the iOS `EmojiPickerSheet`: a 7 column grid, recents hoisted to the front and
 * every pick recorded so the reaction bar keeps the last used emoji first.
 */
class EmojiPickerSheetFragment : BottomSheetDialogFragment() {

    /** Invoked right after the sheet dismisses, so the caller can react immediately. */
    var onEmojiSelected: ((String) -> Unit)? = null

    private var _binding: DialogEmojiPickerBinding? = null
    private val binding get() = _binding!!

    private val adapter = EmojiAdapter { emoji ->
        RecentReactions.remember(requireContext(), emoji)
        val callback = onEmojiSelected
        dismissAllowingStateLoss()
        callback?.invoke(emoji)
    }

    fun show(manager: FragmentManager) {
        show(manager, TAG)
    }

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
        _binding = DialogEmojiPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvEmoji.layoutManager = GridLayoutManager(requireContext(), COLUMNS)
        binding.rvEmoji.adapter = adapter
        adapter.submit(RecentReactions.visible(requireContext()) + EMOJI_PALETTE)
    }

    override fun onDestroyView() {
        binding.rvEmoji.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private class EmojiAdapter(
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<EmojiAdapter.Holder>() {

        private val items = mutableListOf<String>()

        fun submit(values: List<String>) {
            items.clear()
            items.addAll(values.distinct())
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemEmojiBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return Holder(binding)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position], onClick)
        }

        override fun getItemCount(): Int = items.size

        class Holder(private val binding: ItemEmojiBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(emoji: String, onClick: (String) -> Unit) {
                (binding.root as TextView).text = emoji
                binding.root.setOnClickListener { onClick(emoji) }
            }
        }
    }

    companion object {
        const val TAG = "EmojiPickerSheet"
        private const val COLUMNS = 7

        /** A compact, iOS-like palette; recents are prepended at runtime. */
        private val EMOJI_PALETTE = listOf(
            "❤️", "😂", "😮", "😢", "😡", "🥰", "😘", "😎",
            "🥳", "🤩", "😴", "🤔", "🙌", "👏", "👌", "🤝",
            "💯", "✨", "🎉", "🔥", "💥", "💫", "⭐️", "🌹",
            "💔", "💖", "💘", "🍀", "🌈", "☀️", "🌙", "☕️",
            "🍕", "🍰", "🍻", "🎁", "🏆", "⚡️", "👍", "👎",
            "🙏", "😊", "🤗", "😅", "😭", "😱", "🙈", "🤪"
        )
    }
}
