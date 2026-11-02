package com.flirttime.feature.moments.comments

import com.flirttime.core.common.IsoDate
import com.flirttime.feature.moments.MomentCommentDto

object CommentMapper {

    fun fromDto(
        dto: MomentCommentDto,
        currentUserId: String?,
        fallbackParentId: String? = null
    ): CommentItem {
        val userObj = dto.user ?: dto.commenter ?: dto.author
        val username = userObj?.username?.takeIf { it.isNotBlank() }
            ?: userObj?.fullName?.takeIf { it.isNotBlank() }
            ?: userObj?.name?.takeIf { it.isNotBlank() }
            ?: "User"
        val handle = userObj?.username?.trim()?.takeIf { it.isNotBlank() }
            ?: username.replace(" ", "")
        val avatarUrl = userObj?.profilePicture?.takeIf { it.isNotBlank() }
            ?: userObj?.profilePhoto?.takeIf { it.isNotBlank() }
        val content = dto.comment?.takeIf { it.isNotBlank() } ?: dto.text.orEmpty()
        val createdAt = dto.createdAt
        val time = dto.timeAgo?.takeIf { it.isNotBlank() }
            ?: IsoDate.timeAgo(createdAt).ifBlank { "now" }
        val authorId = userObj?.id ?: userObj?.userId
        val parentId = dto.parentId?.takeIf { it.isNotBlank() }
            ?: dto.parentCommentId?.takeIf { it.isNotBlank() }
            ?: dto.parentReplyId?.takeIf { it.isNotBlank() }
            ?: fallbackParentId
        val isMine = dto.isMyComment == true ||
            (!currentUserId.isNullOrBlank() && authorId == currentUserId)
        val id = dto.id ?: dto.commentId ?: dto.replyId ?: System.currentTimeMillis().toString()
        val resolvedParent = parentId?.takeIf { it != id }
        return CommentItem(
            id = id,
            userId = authorId,
            username = username,
            handle = handle,
            timeAgo = time,
            text = content,
            avatarUrl = avatarUrl,
            likeCount = dto.likesCount ?: dto.likeCount ?: 0,
            isLiked = dto.isLiked ?: dto.hasLiked ?: false,
            replyCount = dto.repliesCount ?: dto.replyCount ?: 0,
            parentId = resolvedParent,
            isMine = isMine,
            isReply = !resolvedParent.isNullOrBlank(),
            createdAt = createdAt
        )
    }
}
