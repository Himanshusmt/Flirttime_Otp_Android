package com.flirttime.feature.subscription

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import com.flirttime.R
import com.flirttime.databinding.DialogSubscriptionPopupBinding

/**
 * Android port of iOS `SubscriptionPopUp`: the gold "Unlock the full power of Premium" sheet shown when a
 * free user taps a locked feature. Tapping the backdrop dismisses it; the CTA runs [onUpgrade]
 * (iOS `subsUpdateAction` → `openPremium(source:)`).
 */
object SubscriptionPopUp {

    fun show(
        context: Context,
        type: SubscriptionPopUpType,
        onUpgrade: () -> Unit = { PremiumActivity.start(context, type) }
    ): Dialog {
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogSubscriptionPopupBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }

        configure(binding, type, context)

        binding.popupRoot.setOnClickListener { dialog.dismiss() }
        binding.btnPopupCta.setOnClickListener {
            dialog.dismiss()
            onUpgrade()
        }
        dialog.show()
        return dialog
    }

    /** iOS `configurePopupView(subcriptionType:)`. */
    private fun configure(binding: DialogSubscriptionPopupBinding, type: SubscriptionPopUpType, context: Context) {
        val density = context.resources.displayMetrics.density
        fun sizeIcon(widthDp: Int) {
            binding.ivPopupIcon.layoutParams = (binding.ivPopupIcon.layoutParams as FrameLayout.LayoutParams).apply {
                width = (widthDp * density).toInt()
                height = (128 * density).toInt()
            }
        }
        when (type) {
            SubscriptionPopUpType.CHAT -> {
                binding.tvPopupTitle.text = "Someone wants to chat with you"
                binding.ivPopupIcon.setImageResource(R.drawable.img_sub_popup_chat)
                sizeIcon(298)
            }
            SubscriptionPopUpType.COMPLIMENT -> {
                binding.tvPopupTitle.text = "Someone has sent you a compliment!"
                binding.ivPopupIcon.setImageResource(R.drawable.img_sub_popup_compliment)
                sizeIcon(298)
            }
            SubscriptionPopUpType.SUPER_LIKE -> {
                binding.tvPopupTitle.text = "Someone has sent you super like"
                binding.ivPopupIcon.setImageResource(R.drawable.img_sub_popup_superlike)
                sizeIcon(128)
            }
            SubscriptionPopUpType.UNLIMITED_SWIPES -> {
                // iOS reuses the compliment copy for this variant.
                binding.tvPopupTitle.text = "Someone has sent you a compliment!"
                binding.ivPopupIcon.setImageResource(R.drawable.img_sub_popup_unlimited)
                sizeIcon(128)
            }
        }
    }
}
