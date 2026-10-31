package com.flirttime.feature.moments.options

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.flirttime.databinding.BottomSheetMomentMoreOptionsBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class MomentMoreOptionsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetMomentMoreOptionsBinding? = null
    private val binding get() = _binding!!

    var onDeleteClick: (() -> Unit)? = null
    var onReportClick: (() -> Unit)? = null
    var onBlockClick: (() -> Unit)? = null

    private var momentId: String = ""
    private var authorId: String? = null
    private var authorName: String = ""
    private var isOwnMoment: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        momentId = arguments?.getString(ARG_MOMENT_ID).orEmpty()
        authorId = arguments?.getString(ARG_AUTHOR_ID)
        authorName = arguments?.getString(ARG_AUTHOR_NAME).orEmpty()
        isOwnMoment = arguments?.getBoolean(ARG_IS_OWN_MOMENT, false) ?: false
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
        _binding = BottomSheetMomentMoreOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (isOwnMoment) {
            binding.btnDeleteMoment.visibility = View.VISIBLE
            binding.btnReportMoment.visibility = View.GONE
            binding.dividerReportBlock.visibility = View.GONE
            binding.btnBlockUser.visibility = View.GONE
        } else {
            binding.btnDeleteMoment.visibility = View.GONE
            binding.btnReportMoment.visibility = View.VISIBLE
            binding.dividerReportBlock.visibility = View.VISIBLE
            binding.btnBlockUser.visibility = View.VISIBLE
        }

        binding.btnDeleteMoment.setOnClickListener {
            dismiss()
            onDeleteClick?.invoke()
        }

        binding.btnReportMoment.setOnClickListener {
            dismiss()
            onReportClick?.invoke()
        }

        binding.btnBlockUser.setOnClickListener {
            dismiss()
            onBlockClick?.invoke()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "MomentMoreOptionsBottomSheet"
        private const val ARG_MOMENT_ID = "arg_moment_id"
        private const val ARG_AUTHOR_ID = "arg_author_id"
        private const val ARG_AUTHOR_NAME = "arg_author_name"
        private const val ARG_IS_OWN_MOMENT = "arg_is_own_moment"

        fun newInstance(
            momentId: String,
            authorId: String?,
            authorName: String,
            isOwnMoment: Boolean
        ): MomentMoreOptionsBottomSheetDialogFragment {
            return MomentMoreOptionsBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MOMENT_ID, momentId)
                    putString(ARG_AUTHOR_ID, authorId)
                    putString(ARG_AUTHOR_NAME, authorName)
                    putBoolean(ARG_IS_OWN_MOMENT, isOwnMoment)
                }
            }
        }
    }
}

