package com.flirttime.feature.moments

import android.content.Context
import android.content.Intent

object MomentShareHelper {

    fun shareText(post: MomentPost): String {
        val name = post.username.ifBlank { "Someone" }
        val caption = post.caption.trim()
        return if (caption.isEmpty()) {
            "Check out $name's moment on FlirtTime"
        } else {
            "$name on FlirtTime: $caption"
        }
    }

    fun share(context: Context, post: MomentPost) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText(post))
        }
        context.startActivity(Intent.createChooser(intent, "Share moment"))
    }
}
