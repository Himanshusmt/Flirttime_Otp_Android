package com.flirttime.feature.moments.comments

import androidx.annotation.DrawableRes
import com.flirttime.R

data class CommentItem(
    val id: String,
    val userId: String? = null,
    val username: String,
    val handle: String? = null,
    val timeAgo: String,
    val text: String,
    @field:DrawableRes val avatarResId: Int = R.drawable.img_gallery_demo_girl,
    val avatarUrl: String? = null,
    val likeCount: Int = 0,
    val isLiked: Boolean = false,
    val replyCount: Int = 0,
    val replies: List<CommentItem> = emptyList(),
    val parentId: String? = null,
    val isMine: Boolean = false,
    val isReply: Boolean = false,
    val isExpanded: Boolean = false,
    val isLoadingReplies: Boolean = false,
    val createdAt: String? = null
)
