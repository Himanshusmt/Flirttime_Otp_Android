package com.flirttime.feature.profile.otherUser

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.flirttime.databinding.BottomSheetOtherUserActionsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class OtherUserActionsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetOtherUserActionsBinding? = null
    private val binding get() = _binding!!

    var onReportClick: (() -> Unit)? = null
    var onBlockClick: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetOtherUserActionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
            sheet.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val username = arguments?.getString(ARG_USERNAME) ?: "User"

        binding.btnReport.text = "Report $username"
        binding.btnBlock.text = "Block $username"

        binding.btnReport.setOnClickListener {
            dismiss()
            onReportClick?.invoke()
        }

        binding.btnBlock.setOnClickListener {
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
        const val TAG = "OtherUserActionsBottomSheet"
        private const val ARG_USERNAME = "arg_username"

        fun newInstance(username: String): OtherUserActionsBottomSheetDialogFragment {
            return OtherUserActionsBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_USERNAME, username)
                }
            }
        }
    }
}

