package com.flirttime.core.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import com.flirttime.databinding.DialogSessionExpiredBinding

class SessionExpiredDialog(
    context: Context,
    private val title: String = "Session Expired",
    private val message: String = "Your session has expired. Redirecting to login...",
    private val buttonText: String = "Log In Now",
    private val autoDismissDelayMs: Long = 1800L,
    private val onConfirm: () -> Unit
) : Dialog(context) {

    private val autoHandler = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable {
        if (isShowing) {
            dismiss()
            onConfirm()
        }
    }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogSessionExpiredBinding.inflate(LayoutInflater.from(context))
        setContentView(binding.root)

        setCancelable(false)
        setCanceledOnTouchOutside(false)

        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (context.resources.displayMetrics.widthPixels * 0.86).toInt()
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        binding.tvDialogTitle.text = title
        binding.tvDialogMessage.text = message
        binding.btnPositive.text = buttonText

        binding.btnPositive.setOnClickListener {
            autoHandler.removeCallbacks(autoDismissRunnable)
            dismiss()
            onConfirm()
        }

        setOnCancelListener {
            autoHandler.removeCallbacks(autoDismissRunnable)
            onConfirm()
        }

        if (autoDismissDelayMs > 0) {
            autoHandler.postDelayed(autoDismissRunnable, autoDismissDelayMs)
        }
    }

    override fun onStop() {
        super.onStop()
        autoHandler.removeCallbacks(autoDismissRunnable)
    }

    companion object {
        fun show(
            context: Context,
            title: String = "Session Expired",
            message: String = "Your session has expired. Redirecting to login...",
            buttonText: String = "Log In Now",
            autoDismissDelayMs: Long = 1800L,
            onConfirm: () -> Unit
        ): SessionExpiredDialog {
            val dialog = SessionExpiredDialog(
                context = context,
                title = title,
                message = message,
                buttonText = buttonText,
                autoDismissDelayMs = autoDismissDelayMs,
                onConfirm = onConfirm
            )
            dialog.show()
            return dialog
        }
    }
}
