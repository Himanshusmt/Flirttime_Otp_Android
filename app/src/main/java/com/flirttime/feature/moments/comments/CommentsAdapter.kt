package com.flirttime.feature.moments.comments

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.core.common.FastImageHelper
import com.flirttime.core.common.IsoDate
import com.flirttime.databinding.ItemCommentBinding

class CommentsAdapter(
    private val onReplyClick: ((CommentItem) -> Unit)? = null,
    private val onLikeClick: ((CommentItem) -> Unit)? = null,
    private val onUserClick: ((CommentItem) -> Unit)? = null,
    private val onDeleteClick: ((CommentItem) -> Unit)? = null,
    private val onToggleReplies: ((CommentItem) -> Unit)? = null,
    private val onMentionClick: ((String) -> Unit)? = null
) : ListAdapter<CommentItem, CommentsAdapter.CommentViewHolder>(CommentDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CommentViewHolder(
        private val binding: ItemCommentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: CommentItem) {
            val density = binding.root.resources.displayMetrics.density
            val avatarSize = if (comment.isReply) (28 * density).toInt() else (36 * density).toInt()
            val indent = if (comment.isReply) (46 * density).toInt() else 0
            binding.root.setPadding(
                (16 * density).toInt() + indent,
                binding.root.paddingTop,
                binding.root.paddingEnd,
                binding.root.paddingBottom
            )
            binding.ivCommentAvatar.layoutParams = binding.ivCommentAvatar.layoutParams.apply {
                width = avatarSize
                height = avatarSize
            }

            binding.tvCommentUsername.text = comment.username
            binding.tvCommentTimeAgo.text = comment.timeAgo
            bindMentions(comment.text)

            FastImageHelper.loadDirect(
                draweeView = binding.ivCommentAvatar,
                url = comment.avatarUrl,
                fallbackRes = comment.avatarResId
            )

            binding.ivCommentAvatar.setOnClickListener { onUserClick?.invoke(comment) }
            binding.tvCommentUsername.setOnClickListener { onUserClick?.invoke(comment) }
            binding.tvReplyAction.setOnClickListener { onReplyClick?.invoke(comment) }
            binding.layoutLike.setOnClickListener { onLikeClick?.invoke(comment) }

            binding.tvDeleteAction.visibility = if (comment.isMine) View.VISIBLE else View.GONE
            binding.tvDeleteAction.setOnClickListener { onDeleteClick?.invoke(comment) }

            val liked = comment.isLiked
            binding.ivCommentLike.setImageResource(
                if (liked) R.drawable.ic_moment_heart_filled else R.drawable.ic_moment_heart
            )
            binding.ivCommentLike.imageTintList = android.content.res.ColorStateList.valueOf(
                if (liked) Color.parseColor("#DE2D2D") else Color.parseColor("#8E8E93")
            )
            if (comment.likeCount > 0) {
                binding.tvCommentLikeCount.visibility = View.VISIBLE
                binding.tvCommentLikeCount.text = IsoDate.compactCount(comment.likeCount)
            } else {
                binding.tvCommentLikeCount.visibility = View.GONE
            }

            val showToggle = !comment.isReply && comment.replyCount > 0
            binding.layoutRepliesToggle.visibility = if (showToggle) View.VISIBLE else View.GONE
            if (showToggle) {
                binding.tvRepliesToggle.text = if (comment.isExpanded) {
                    "Hide replies"
                } else {
                    val count = comment.replyCount
                    if (count == 1) "View 1 reply" else "View $count replies"
                }
                binding.progressReplies.visibility = if (comment.isLoadingReplies) View.VISIBLE else View.GONE
                binding.layoutRepliesToggle.setOnClickListener { onToggleReplies?.invoke(comment) }
            }

            binding.root.setOnLongClickListener {
                onReplyClick?.invoke(comment)
                true
            }
        }

        private fun bindMentions(text: String) {
            val spannable = SpannableString(text)
            val matcher = MENTION_REGEX.matcher(text)
            var hasMention = false
            while (matcher.find()) {
                hasMention = true
                val start = matcher.start()
                val end = matcher.end()
                val username = text.substring(start + 1, end)
                spannable.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        onMentionClick?.invoke(username)
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        ds.isUnderlineText = false
                        ds.color = Color.parseColor("#DE2D2D")
                        ds.isFakeBoldText = true
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            binding.tvCommentContent.text = spannable
            binding.tvCommentContent.movementMethod = if (hasMention) LinkMovementMethod.getInstance() else null
            binding.tvCommentContent.highlightColor = Color.TRANSPARENT
        }
    }

    companion object {
        private val MENTION_REGEX = Regex("(?<![\\w])@[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*").toPattern()

        private val CommentDiffCallback = object : DiffUtil.ItemCallback<CommentItem>() {
            override fun areItemsTheSame(oldItem: CommentItem, newItem: CommentItem): Boolean {
                return oldItem.id == newItem.id && oldItem.isReply == newItem.isReply
            }

            override fun areContentsTheSame(oldItem: CommentItem, newItem: CommentItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
