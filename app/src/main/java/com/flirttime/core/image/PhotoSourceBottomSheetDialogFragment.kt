package com.flirttime.core.image

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.flirttime.databinding.DialogPhotoSourceBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * BottomSheet dialog for selecting the image source (Camera vs Gallery)
 * before routing into ImageCropperHelper.
 */
class PhotoSourceBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogPhotoSourceBottomSheetBinding? = null
    private val binding get() = _binding!!

    var onCameraSelected: (() -> Unit)? = null
    var onGallerySelected: (() -> Unit)? = null

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
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnTakePhoto.setOnClickListener {
            dismiss()
            onCameraSelected?.invoke()
        }

        binding.btnChooseLibrary.setOnClickListener {
            dismiss()
            onGallerySelected?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PhotoSourceBottomSheetDialogFragment"

        fun newInstance(): PhotoSourceBottomSheetDialogFragment =
            PhotoSourceBottomSheetDialogFragment()
    }
}
