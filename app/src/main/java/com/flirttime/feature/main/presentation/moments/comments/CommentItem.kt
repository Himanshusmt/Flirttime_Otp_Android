package com.flirttime.feature.main.presentation.moments.comments

import androidx.annotation.DrawableRes
import com.flirttime.R

/**
 * Data model representing a comment in the Moments feed comment bottom sheet.
 * Designed with extensibility for future nested replies, likes count, and rich media.
 */
data class CommentItem(
    val id: String,
    val username: String,
    val timeAgo: String,
    val text: String,
    @field:DrawableRes val avatarResId: Int = R.drawable.img_gallery_demo_girl,
    val avatarUrl: String? = null,
    val likeCount: Int = 0,
    val isLiked: Boolean = false,
    val replyCount: Int = 0,
    val replies: List<CommentItem> = emptyList()
)
