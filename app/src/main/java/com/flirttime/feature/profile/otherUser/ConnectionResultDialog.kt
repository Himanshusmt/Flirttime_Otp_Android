package com.flirttime.feature.profile.otherUser

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.flirttime.core.common.FastImageHelper
import com.flirttime.databinding.DialogMatchCelebrationBinding

object ConnectionResultDialog {

    fun showMatch(
        context: Context,
        name: String,
        avatarUrl: String?,
        hasSharedInterests: Boolean,
        onStartConversation: () -> Unit,
        onKeepExploring: () -> Unit = {},
        subtitleOverride: String? = null
    ) {
        val subtitle = subtitleOverride ?: if (hasSharedInterests) {
            "You and $name have connected based on shared interests."
        } else {
            "You and $name are into each other."
        }
        show(
            context = context,
            title = "It's a Match!",
            subtitle = subtitle,
            avatarUrl = avatarUrl,
            primaryText = "Start Conversation",
            secondaryText = "Keep Exploring",
            showPrimary = true,
            onPrimary = onStartConversation,
            onSecondary = onKeepExploring
        )
    }

    fun showLikeSent(
        context: Context,
        name: String,
        avatarUrl: String?,
        onKeepExploring: () -> Unit = {},
        subtitleOverride: String? = null
    ) {
        show(
            context = context,
            title = "Like Sent",
            subtitle = subtitleOverride ?: "If $name feels it too, you'll connect. We'll let you know.",
            avatarUrl = avatarUrl,
            primaryText = "",
            secondaryText = "Keep Exploring",
            showPrimary = false,
            onPrimary = {},
            onSecondary = onKeepExploring
        )
    }

    private fun show(
        context: Context,
        title: String,
        subtitle: String,
        avatarUrl: String?,
        primaryText: String,
        secondaryText: String,
        showPrimary: Boolean,
        onPrimary: () -> Unit,
        onSecondary: () -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogMatchCelebrationBinding.inflate(android.view.LayoutInflater.from(context))
        dialog.setContentView(binding.root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (context.resources.displayMetrics.widthPixels * 0.88).toInt()
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        binding.tvMatchTitle.text = title
        binding.tvMatchSubtitle.text = subtitle
        FastImageHelper.loadDirect(binding.ivMatchedAvatar, avatarUrl)
        binding.btnSendMessage.text = primaryText
        binding.btnSendMessage.visibility = if (showPrimary) View.VISIBLE else View.GONE
        binding.btnKeepSwiping.text = secondaryText

        binding.btnSendMessage.setOnClickListener {
            dialog.dismiss()
            onPrimary()
        }
        binding.btnKeepSwiping.setOnClickListener {
            dialog.dismiss()
            onSecondary()
        }
        dialog.show()
    }
}
