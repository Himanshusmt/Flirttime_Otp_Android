package com.flirttime.core.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import androidx.core.view.isVisible
import com.flirttime.databinding.DialogCommonConfirmationBinding

class CommonConfirmationDialog(
    context: Context,
    private val title: String,
    private val message: String? = null,
    private val positiveText: String = "Yes",
    private val negativeText: String = "No",
    private val onPositiveClick: () -> Unit,
    private val onNegativeClick: (() -> Unit)? = null
) : Dialog(context) {

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogCommonConfirmationBinding.inflate(LayoutInflater.from(context))
        setContentView(binding.root)

        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (context.resources.displayMetrics.widthPixels * 0.86).toInt()
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        binding.tvDialogTitle.text = title

        if (!message.isNullOrBlank()) {
            binding.tvDialogMessage.text = message
            binding.tvDialogMessage.isVisible = true
        } else {
            binding.tvDialogMessage.isVisible = false
        }

        binding.btnPositive.text = positiveText
        binding.btnNegative.text = negativeText

        binding.btnPositive.setOnClickListener {
            dismiss()
            onPositiveClick()
        }

        binding.btnNegative.setOnClickListener {
            dismiss()
            onNegativeClick?.invoke()
        }
    }

    companion object {
        fun show(
            context: Context,
            title: String,
            message: String? = null,
            positiveText: String = "Yes",
            negativeText: String = "No",
            onNegativeClick: (() -> Unit)? = null,
            onPositiveClick: () -> Unit
        ): CommonConfirmationDialog {
            val dialog = CommonConfirmationDialog(
                context = context,
                title = title,
                message = message,
                positiveText = positiveText,
                negativeText = negativeText,
                onPositiveClick = onPositiveClick,
                onNegativeClick = onNegativeClick
            )
            dialog.show()
            return dialog
        }
    }
}
