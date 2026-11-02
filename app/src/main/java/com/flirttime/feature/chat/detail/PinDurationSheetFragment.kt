package com.flirttime.feature.chat.detail

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.FragmentManager
import com.flirttime.R
import com.flirttime.databinding.DialogPinDurationBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Pin lifetime chooser shown before a message is pinned.
 *
 * Values match the iOS payload (`24h` / `7d` / `30d`) so the backend expires the pin the
 * same way on both platforms.
 */
class PinDurationSheetFragment : BottomSheetDialogFragment() {

    /** Receives `"24h"`, `"7d"` or `"30d"`. */
    var onDurationSelected: ((String) -> Unit)? = null

    private var _binding: DialogPinDurationBinding? = null
    private val binding get() = _binding!!

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
        _binding = DialogPinDurationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.row24h.setOnClickListener { pick(DURATION_24_HOURS) }
        binding.row7d.setOnClickListener { pick(DURATION_7_DAYS) }
        binding.row30d.setOnClickListener { pick(DURATION_30_DAYS) }
        binding.tvCancel.setOnClickListener { dismissAllowingStateLoss() }
    }

    private fun pick(duration: String) {
        val callback = onDurationSelected
        dismissAllowingStateLoss()
        callback?.invoke(duration)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "PinDurationSheet"
        const val DURATION_24_HOURS = "24h"
        const val DURATION_7_DAYS = "7d"
        const val DURATION_30_DAYS = "30d"
    }
}
