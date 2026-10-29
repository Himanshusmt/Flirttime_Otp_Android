package com.flirttime.feature.moments.delete

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.flirttime.Resource
import com.flirttime.databinding.BottomSheetDeleteMomentBinding
import com.flirttime.feature.moments.MomentsRepository
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Bottom sheet dialog allowing the author of a moment to permanently delete it.
 */
class DeleteMomentBottomSheetDialogFragment : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "DeleteMomentBottomSheet"
        private const val ARG_MOMENT_ID = "arg_moment_id"

        fun newInstance(momentId: String): DeleteMomentBottomSheetDialogFragment {
            return DeleteMomentBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MOMENT_ID, momentId)
                }
            }
        }
    }

    private var _binding: BottomSheetDeleteMomentBinding? = null
    private val binding get() = _binding!!

    private val momentsRepository: MomentsRepository by inject()

    private var momentId: String = ""
    var onMomentDeleted: ((String) -> Unit)? = null

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
                sheet.setBackgroundResource(android.R.color.transparent)
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
        _binding = BottomSheetDeleteMomentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnDeleteMoment.setOnClickListener {
            performDelete()
        }
    }

    private fun performDelete() {
        if (momentId.isBlank()) {
            Toast.makeText(context, "Invalid moment ID", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            momentsRepository.deleteMoment(momentId).collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        setLoading(false)
                        Toast.makeText(context, "Moment deleted successfully", Toast.LENGTH_SHORT).show()
                        onMomentDeleted?.invoke(momentId)
                        dismissAllowingStateLoss()
                    }
                    is Resource.Error -> {
                        setLoading(false)
                        val error = resource.message.takeIf { it.isNotBlank() }
                            ?: "Failed to delete moment. Please try again."
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                    is Resource.Loading -> {
                        setLoading(true)
                    }
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        isCancelable = !isLoading
        binding.btnDeleteMoment.isEnabled = !isLoading
        binding.btnCancel.isEnabled = !isLoading
        binding.progressDelete.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.tvDeleteText.visibility = if (isLoading) View.GONE else View.VISIBLE
        binding.ivDeleteIcon.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
