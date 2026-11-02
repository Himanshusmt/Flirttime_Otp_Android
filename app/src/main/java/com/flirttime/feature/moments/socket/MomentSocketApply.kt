package com.flirttime.feature.moments.socket

import com.flirttime.feature.moments.MomentPost

/** Shared 3s echo-guard + count patch used by the feed and My Moments. */
class LocalEditGuard(private val windowMs: Long = 3_000) {
    private val recent = mutableMapOf<String, Long>()

    fun mark(id: String) {
        if (id.isNotBlank()) recent[id] = System.currentTimeMillis()
    }

    fun recentlyEdited(id: String): Boolean {
        val at = recent[id] ?: return false
        if (System.currentTimeMillis() - at > windowMs) {
            recent.remove(id)
            return false
        }
        return true
    }
}

fun MomentSocketUpdate.applyCountsTo(post: MomentPost, skipDelta: Boolean): MomentPost {
    if (likesCount != null) {
        post.likeCount = likesCount
    } else if (!skipDelta && kind == MomentSocketKind.LikeUpdated && viewerHasLiked != null) {
        if (viewerHasLiked != post.isLiked) {
            post.likeCount = maxOf(0, post.likeCount + if (viewerHasLiked) 1 else -1)
        }
    }
    if (commentsCount != null) {
        post.commentCount = commentsCount
    } else if (!skipDelta) {
        when (kind) {
            MomentSocketKind.CommentCreated -> post.commentCount += 1
            MomentSocketKind.CommentDeleted -> post.commentCount = maxOf(0, post.commentCount - 1)
            else -> Unit
        }
    }
    if (giftsCount != null) {
        post.giftCount = giftsCount
    } else if (!skipDelta && kind == MomentSocketKind.GiftSent) {
        post.giftCount += 1
    }
    if (viewerHasLiked != null) {
        post.isLiked = viewerHasLiked
    }
    return post
}
