package com.flirttime.feature.moments.comments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.databinding.ItemCommentBinding

class CommentsAdapter(
    private val onReplyClick: ((CommentItem) -> Unit)? = null,
    private val onLikeClick: ((CommentItem) -> Unit)? = null,
    private val onUserClick: ((CommentItem) -> Unit)? = null
) : ListAdapter<CommentItem, CommentsAdapter.CommentViewHolder>(CommentDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CommentViewHolder(
        private val binding: ItemCommentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: CommentItem) {
            binding.tvCommentUsername.text = comment.username
            binding.tvCommentTimeAgo.text = comment.timeAgo
            binding.tvCommentContent.text = comment.text

            binding.ivCommentAvatar.setOnClickListener {
                onUserClick?.invoke(comment)
            }
            binding.tvCommentUsername.setOnClickListener {
                onUserClick?.invoke(comment)
            }
            if (!comment.avatarUrl.isNullOrBlank()) {
                binding.ivCommentAvatar.setImageURI(comment.avatarUrl)
            } else {
                binding.ivCommentAvatar.setActualImageResource(comment.avatarResId)
            }

            // Future reply hook
            if (comment.replyCount > 0) {
                binding.tvReplyAction.visibility = View.VISIBLE
                binding.tvReplyAction.text = "View ${comment.replyCount} replies"
            } else {
                binding.tvReplyAction.visibility = View.GONE
            }

            binding.tvReplyAction.setOnClickListener {
                onReplyClick?.invoke(comment)
            }
        }
    }

    companion object {
        private val CommentDiffCallback = object : DiffUtil.ItemCallback<CommentItem>() {
            override fun areItemsTheSame(oldItem: CommentItem, newItem: CommentItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: CommentItem, newItem: CommentItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
