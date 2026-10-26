package com.flirttime.feature.main.presentation.moments.add

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.flirttime.databinding.DialogPhotoSourceBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PhotoSourceBottomSheetDialogFragment(
    private val onTakePhoto: () -> Unit,
    private val onChooseLibrary: () -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogPhotoSourceBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPhotoSourceBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
        }
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnTakePhoto.setOnClickListener {
            dismiss()
            onTakePhoto()
        }

        binding.btnChooseLibrary.setOnClickListener {
            dismiss()
            onChooseLibrary()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PhotoSourceBottomSheetDialogFragment"

        fun newInstance(
            onTakePhoto: () -> Unit,
            onChooseLibrary: () -> Unit
        ): PhotoSourceBottomSheetDialogFragment {
            return PhotoSourceBottomSheetDialogFragment(onTakePhoto, onChooseLibrary)
        }
    }
}
