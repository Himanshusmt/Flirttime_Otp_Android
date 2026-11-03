package com.flirttime.feature.profile.otherUser

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.flirttime.R
import com.flirttime.databinding.BottomSheetConnectBecauseBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ConnectBecauseBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetConnectBecauseBinding? = null
    private val binding get() = _binding!!

    var onSend: ((ConnectReason?) -> Unit)? = null

    private var selected: ConnectReason? = null
    private val reasonButtons = mutableListOf<Pair<ConnectReason, TextView>>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let { sheet ->
                sheet.setBackgroundColor(Color.TRANSPARENT)
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetConnectBecauseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val displayName = arguments?.getString(ARG_DISPLAY_NAME).orEmpty().ifBlank { "them" }
        binding.tvConnectSubtitle.text = "Optional: let $displayName know what caught your eye."

        val hasSharedInterests = arguments?.getBoolean(ARG_HAS_SHARED_INTERESTS) == true
        val hasMoments = arguments?.getBoolean(ARG_HAS_MOMENTS) == true
        val reasons = ConnectReason.available(hasSharedInterests, hasMoments)

        val density = resources.displayMetrics.density
        reasons.forEach { reason ->
            val row = TextView(requireContext()).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (10 * density).toInt()
                }
                text = reason.apiValue
                textSize = 16f
                typeface = androidx.core.content.res.ResourcesCompat.getFont(
                    requireContext(),
                    R.font.fredokamedium
                )
                setPadding(
                    (14 * density).toInt(),
                    (12 * density).toInt(),
                    (14 * density).toInt(),
                    (12 * density).toInt()
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { toggle(reason) }
            }
            applyReasonStyle(row, reason, selected = false)
            reasonButtons += reason to row
            binding.layoutReasons.addView(row)
        }

        binding.btnSendConnection.setOnClickListener {
            dismiss()
            onSend?.invoke(selected)
        }
        binding.btnCancel.setOnClickListener { dismiss() }
    }

    private fun toggle(reason: ConnectReason) {
        selected = if (selected == reason) null else reason
        reasonButtons.forEach { (entry, view) ->
            applyReasonStyle(view, entry, selected = selected == entry)
        }
    }

    private fun applyReasonStyle(view: TextView, reason: ConnectReason, selected: Boolean) {
        val context = requireContext()
        view.setBackgroundResource(
            if (selected) R.drawable.bg_interest_chip_pill_common else R.drawable.bg_interest_chip_pill
        )
        view.setTextColor(
            ContextCompat.getColor(
                context,
                if (selected) R.color.brand_primary else R.color.text_primary
            )
        )
        view.contentDescription = reason.apiValue
    }

    override fun onDestroyView() {
        super.onDestroyView()
        reasonButtons.clear()
        _binding = null
    }

    companion object {
        const val TAG = "ConnectBecauseBottomSheet"

        private const val ARG_DISPLAY_NAME = "arg_display_name"
        private const val ARG_HAS_SHARED_INTERESTS = "arg_has_shared_interests"
        private const val ARG_HAS_MOMENTS = "arg_has_moments"

        fun newInstance(
            displayName: String,
            hasSharedInterests: Boolean,
            hasMoments: Boolean
        ): ConnectBecauseBottomSheetDialogFragment {
            return ConnectBecauseBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DISPLAY_NAME, displayName)
                    putBoolean(ARG_HAS_SHARED_INTERESTS, hasSharedInterests)
                    putBoolean(ARG_HAS_MOMENTS, hasMoments)
                }
            }
        }
    }
}
