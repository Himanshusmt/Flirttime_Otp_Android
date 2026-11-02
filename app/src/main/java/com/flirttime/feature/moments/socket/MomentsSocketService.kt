package com.flirttime.feature.moments.socket

import android.util.Log
import com.flirttime.core.common.IsoDate
import com.flirttime.core.database.SessionManager
import com.flirttime.feature.chat.socket.ChatSocketManager
import com.flirttime.feature.moments.MomentDto
import com.flirttime.feature.moments.MomentPost
import com.flirttime.feature.moments.comments.CommentItem
import com.flirttime.feature.moments.toUiItem
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject

enum class MomentSocketKind {
    Updated,
    LikeUpdated,
    CommentCreated,
    CommentDeleted,
    CommentLikeUpdated,
    GiftSent,
    Deleted
}

data class MomentSocketUpdate(
    val kind: MomentSocketKind,
    val momentId: String,
    val likesCount: Int? = null,
    val commentsCount: Int? = null,
    val giftsCount: Int? = null,
    val sharesCount: Int? = null,
    val viewerHasLiked: Boolean? = null,
    val actorId: String? = null,
    val commentId: String? = null,
    val comment: CommentItem? = null,
    val commentLikesCount: Int? = null,
    val commentIsLiked: Boolean? = null,
    val moment: MomentPost? = null
)

class MomentsSocketService(
    private val socketManager: ChatSocketManager,
    private val sessionManager: SessionManager
) {

    private val gson = Gson()
    private val _updates = MutableSharedFlow<MomentSocketUpdate>(extraBufferCapacity = 64)
    val updates: SharedFlow<MomentSocketUpdate> = _updates.asSharedFlow()

    @Volatile
    private var listenersConfigured = false

    fun setup() {
        if (listenersConfigured) return
        listenersConfigured = true
        listen("moment:created", MomentSocketKind.Updated)
        listen("moment:updated", MomentSocketKind.Updated)
        listen("moment:like_updated", MomentSocketKind.LikeUpdated)
        listen("moment:comment_created", MomentSocketKind.CommentCreated)
        listen("moment:comment_deleted", MomentSocketKind.CommentDeleted)
        listen("moment:comment_like_updated", MomentSocketKind.CommentLikeUpdated)
        listen("moment:gift_sent", MomentSocketKind.GiftSent)
        listen("moment:deleted", MomentSocketKind.Deleted)
        socketManager.connect()
    }

    private fun listen(event: String, kind: MomentSocketKind) {
        socketManager.listenRaw(event) { args ->
            val update = parse(args, kind) ?: return@listenRaw
            Log.d(TAG, "$event momentId=${update.momentId}")
            _updates.tryEmit(update)
        }
    }

    private fun parse(args: Array<Any>, kind: MomentSocketKind): MomentSocketUpdate? {
        val body = dictionaryFrom(args.firstOrNull()) ?: return null
        val momentDict = body.optJSONObject("moment") ?: body.optJSONObject("post")
        val commentDict = body.optJSONObject("comment")
            ?: body.optJSONObject("reply")
            ?: body.optJSONObject("data")?.optJSONObject("comment")
            ?: body.optJSONObject("data")?.optJSONObject("reply")

        val momentId = string(body, "momentId")
            ?: string(body, "vibeId")
            ?: string(momentDict, "momentId")
            ?: string(momentDict, "id")
            ?: string(commentDict, "momentId")
            ?: string(commentDict, "vibeId")
            ?: if (kind == MomentSocketKind.Deleted) string(body, "id") else null
        if (momentId.isNullOrBlank()) return null

        val actor = body.optJSONObject("actor")
            ?: body.optJSONObject("user")
            ?: body.optJSONObject("sender")
            ?: body.optJSONObject("liker")
        val actorId = string(body, "actorId")
            ?: string(body, "likerId")
            ?: string(body, "senderId")
            ?: string(body, "fromUserId")
            ?: string(actor, "id")
            ?: string(actor, "userId")

        val commentId = string(body, "commentId")
            ?: string(body, "replyId")
            ?: string(commentDict, "replyId")
            ?: string(commentDict, "commentId")
            ?: string(commentDict, "id")

        val currentUserId = sessionManager.getCachedUserId()
        val isActorCurrentUser = !actorId.isNullOrBlank() && actorId == currentUserId

        val explicitViewerLike = bool(body, "isLikedByMe")
            ?: bool(body, "hasLikedByMe")
            ?: bool(body, "likedByMe")
            ?: bool(body, "viewerHasLiked")
            ?: bool(momentDict, "isLikedByMe")
            ?: bool(momentDict, "hasLikedByMe")
        val viewerReaction = string(body, "viewerReaction") ?: string(momentDict, "viewerReaction")
        val rawLiked = bool(body, "isLiked") ?: bool(body, "hasLiked")
            ?: bool(momentDict, "isLiked") ?: bool(momentDict, "hasLiked")
        val viewerHasLiked = explicitViewerLike
            ?: if (viewerReaction != null) true else null
            ?: if (isActorCurrentUser) rawLiked else null

        val commentLikesCount = int(body, "commentLikesCount")
            ?: int(body, "commentLikeCount")
            ?: int(commentDict, "likesCount")
            ?: int(commentDict, "likeCount")
        val commentIsLiked = bool(body, "commentIsLiked")
            ?: bool(commentDict, "isLikedByMe")
            ?: bool(commentDict, "likedByMe")
            ?: if (isActorCurrentUser) (bool(commentDict, "isLiked") ?: bool(body, "isLiked")) else null

        val comment = if (commentDict != null) {
            decodeComment(commentDict, momentId, commentId, isActorCurrentUser, commentLikesCount, commentIsLiked)
        } else if (kind == MomentSocketKind.CommentCreated && !commentId.isNullOrBlank()) {
            CommentItem(
                id = commentId,
                userId = actorId,
                username = string(actor, "name") ?: string(actor, "username") ?: string(actor, "fullName") ?: "User",
                handle = string(actor, "username"),
                timeAgo = IsoDate.timeAgo(string(body, "createdAt")).ifBlank { "now" },
                text = string(body, "comment") ?: string(body, "reply") ?: string(body, "body") ?: string(body, "text").orEmpty(),
                avatarUrl = string(actor, "profilePicture") ?: string(actor, "profilePhoto") ?: string(actor, "avatar"),
                likeCount = commentLikesCount ?: 0,
                isLiked = commentIsLiked ?: false,
                parentId = string(body, "parentId") ?: string(body, "parentCommentId") ?: string(body, "parentReplyId"),
                isMine = isActorCurrentUser,
                isReply = !string(body, "parentId").isNullOrBlank(),
                createdAt = string(body, "createdAt")
            )
        } else {
            null
        }

        val moment = decodeMoment(momentDict ?: body)

        return MomentSocketUpdate(
            kind = kind,
            momentId = momentId,
            likesCount = int(body, "likesCount") ?: int(body, "likeCount")
                ?: int(momentDict, "likesCount") ?: int(momentDict, "likeCount"),
            commentsCount = int(body, "commentsCount") ?: int(body, "commentCount") ?: int(body, "totalComments")
                ?: int(momentDict, "commentsCount") ?: int(momentDict, "commentCount"),
            giftsCount = int(body, "totalMomentGifts") ?: int(body, "giftsCount") ?: int(body, "giftCount")
                ?: int(momentDict, "totalMomentGifts") ?: int(momentDict, "giftsCount") ?: int(momentDict, "giftCount"),
            sharesCount = int(body, "sharesCount") ?: int(body, "shareCount")
                ?: int(momentDict, "sharesCount") ?: int(momentDict, "shareCount"),
            viewerHasLiked = viewerHasLiked,
            actorId = actorId,
            commentId = commentId,
            comment = comment,
            commentLikesCount = commentLikesCount,
            commentIsLiked = commentIsLiked,
            moment = moment
        )
    }

    private fun dictionaryFrom(raw: Any?): JSONObject? {
        val root = when (raw) {
            is JSONObject -> raw
            is org.json.JSONArray -> raw.optJSONObject(0)
            is String -> runCatching { JSONObject(raw) }.getOrNull()
            is Map<*, *> -> runCatching { JSONObject(raw) }.getOrNull()
            else -> null
        } ?: return null
        val body = root.optJSONObject("data") ?: root
        val moment = body.optJSONObject("moment") ?: return body
        val merged = JSONObject()
        moment.keys().forEach { merged.put(it, moment.opt(it)) }
        body.keys().forEach { key ->
            if (!merged.has(key)) merged.put(key, body.opt(key))
        }
        merged.put("moment", moment)
        return merged
    }

    private fun decodeMoment(dict: JSONObject?): MomentPost? {
        if (dict == null) return null
        val hasContent = dict.has("media") || dict.has("imageUrl") || dict.has("photo")
            || dict.has("thumbnailUrl") || dict.has("caption") || dict.has("creator")
        if (!hasContent) return null
        return runCatching {
            gson.fromJson(dict.toString(), MomentDto::class.java).toUiItem()
        }.getOrNull()?.takeIf { it.id.isNotBlank() }
    }

    private fun decodeComment(
        dict: JSONObject,
        momentId: String,
        fallbackId: String?,
        isActorCurrentUser: Boolean,
        likes: Int?,
        liked: Boolean?
    ): CommentItem? {
        val id = string(dict, "replyId") ?: string(dict, "commentId") ?: string(dict, "id") ?: fallbackId
        if (id.isNullOrBlank()) return null
        val author = dict.optJSONObject("user") ?: dict.optJSONObject("author") ?: dict.optJSONObject("commenter")
        val parentId = string(dict, "parentId") ?: string(dict, "parentCommentId") ?: string(dict, "parentReplyId")
        val username = string(author, "username") ?: string(author, "fullName") ?: string(author, "name") ?: "User"
        return CommentItem(
            id = id,
            userId = string(author, "id") ?: string(author, "userId"),
            username = username,
            handle = string(author, "username") ?: username.replace(" ", ""),
            timeAgo = IsoDate.timeAgo(string(dict, "createdAt")).ifBlank { string(dict, "timeAgo") ?: "now" },
            text = string(dict, "comment") ?: string(dict, "reply") ?: string(dict, "body") ?: string(dict, "text").orEmpty(),
            avatarUrl = string(author, "profilePicture") ?: string(author, "profilePhoto") ?: string(author, "avatar"),
            likeCount = likes ?: int(dict, "likesCount") ?: int(dict, "likeCount") ?: 0,
            isLiked = liked ?: bool(dict, "isLiked") ?: bool(dict, "hasLiked") ?: false,
            replyCount = int(dict, "repliesCount") ?: int(dict, "replyCount") ?: 0,
            parentId = parentId,
            isMine = bool(dict, "isMyComment") ?: bool(dict, "isMine") ?: isActorCurrentUser,
            isReply = !parentId.isNullOrBlank(),
            createdAt = string(dict, "createdAt")
        )
    }

    private fun string(obj: JSONObject?, key: String): String? {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
        return obj.opt(key)?.toString()?.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun int(obj: JSONObject?, key: String): Int? {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
        return when (val value = obj.opt(key)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun bool(obj: JSONObject?, key: String): Boolean? {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
        return when (val value = obj.opt(key)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            else -> null
        }
    }

    companion object {
        private const val TAG = "MomentsSocket"
    }
}
