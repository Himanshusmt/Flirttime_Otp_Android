package com.flirttime.feature.profile.otherUser

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.flirttime.databinding.BottomSheetReportUserBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ReportUserBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetReportUserBinding? = null
    private val binding get() = _binding!!

    var onReasonSelected: ((reasonTitle: String, reasonCode: String) -> Unit)? = null

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
        _binding = BottomSheetReportUserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val name = arguments?.getString(ARG_NAME).orEmpty().ifBlank { "this profile" }
        binding.tvReportTitle.text = "Report $name"
        binding.tvReportSubtitle.text = "Why are you reporting this profile?"

        REASONS.forEachIndexed { index, (title, code) ->
            if (index > 0) {
                binding.layoutReasons.addView(View(requireContext()).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (0.5f * resources.displayMetrics.density).toInt().coerceAtLeast(1)
                    )
                    setBackgroundColor(Color.parseColor("#E5E5EA"))
                })
            }
            val row = TextView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (50 * resources.displayMetrics.density).toInt()
                )
                text = title
                gravity = android.view.Gravity.CENTER
                setTextColor(Color.parseColor("#1A1A1A"))
                textSize = 16f
                typeface = androidx.core.content.res.ResourcesCompat.getFont(
                    requireContext(),
                    com.flirttime.R.font.fredokamedium
                )
                isClickable = true
                isFocusable = true
                setBackgroundResource(android.R.attr.selectableItemBackground.let {
                    val out = android.util.TypedValue()
                    context.theme.resolveAttribute(it, out, true)
                    out.resourceId
                })
                setOnClickListener {
                    dismiss()
                    onReasonSelected?.invoke(title, code)
                }
            }
            binding.layoutReasons.addView(row)
        }

        binding.btnCancel.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ReportUserBottomSheet"
        private const val ARG_NAME = "arg_name"

        val REASONS = listOf(
            "Harassment" to "harassment",
            "Inappropriate content" to "inappropriate_content",
            "Spam" to "spam",
            "Scam" to "scam",
            "Fake profile" to "fake_profile",
            "Underage" to "underage",
            "Other" to "other"
        )

        fun newInstance(name: String): ReportUserBottomSheetDialogFragment {
            return ReportUserBottomSheetDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_NAME, name) }
            }
        }
    }
}
