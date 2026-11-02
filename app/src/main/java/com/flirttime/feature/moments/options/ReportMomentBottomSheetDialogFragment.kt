package com.flirttime.feature.moments.options

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.flirttime.databinding.BottomSheetReportMomentBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ReportMomentBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetReportMomentBinding? = null
    private val binding get() = _binding!!

    var onReasonSelected: ((reasonTitle: String, reasonCode: String) -> Unit)? = null

    private var momentId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        momentId = arguments?.getString(ARG_MOMENT_ID).orEmpty()
    }

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
        _binding = BottomSheetReportMomentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnReasonNudity.setOnClickListener {
            selectReason("Nudity or sexual content", "nudity")
        }

        binding.btnReasonViolence.setOnClickListener {
            selectReason("Violence or graphic content", "violence")
        }

        binding.btnReasonHarassment.setOnClickListener {
            selectReason("Harassment or bullying", "harassment")
        }

        binding.btnReasonInappropriate.setOnClickListener {
            selectReason("Inappropriate content", "inappropriate_content")
        }

        binding.btnReasonSpam.setOnClickListener {
            selectReason("Spam or advertising", "spam")
        }

        binding.btnReasonFake.setOnClickListener {
            selectReason("Fake or misleading content", "fake_content")
        }

        binding.btnReasonOther.setOnClickListener {
            selectReason("Something else", "other")
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun selectReason(title: String, code: String) {
        dismiss()
        onReasonSelected?.invoke(title, code)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ReportMomentBottomSheet"
        private const val ARG_MOMENT_ID = "arg_moment_id"

        fun newInstance(momentId: String): ReportMomentBottomSheetDialogFragment {
            return ReportMomentBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MOMENT_ID, momentId)
                }
            }
        }
    }
}

