package com.flirttime.feature.chat.detail

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.databinding.ItemChatSearchResultBinding
import com.flirttime.feature.chat.model.MessageKind
import com.flirttime.feature.chat.model.MessageUi
import com.flirttime.feature.chat.util.ChatTimeFormatter

/** One in-thread search hit; the query travels with it so highlights survive diffing. */
data class SearchHit(
    val message: MessageUi,
    val query: String
)

/**
 * Result rows for the in-thread message search panel. The matched keyword is highlighted
 * with the brand colour, the rest of the snippet stays in the muted body style.
 */
class ChatSearchResultAdapter(
    private val onResultClick: (MessageUi) -> Unit
) : ListAdapter<SearchHit, ChatSearchResultAdapter.SearchResultViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val binding = ItemChatSearchResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SearchResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SearchResultViewHolder(
        private val binding: ItemChatSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(hit: SearchHit) {
            val message = hit.message
            binding.tvSender.text = senderLabel(message)
            binding.tvTime.text = ChatTimeFormatter.messageTimeLabel(message.createdAtRaw)
            binding.tvSnippet.text = highlight(snippet(message), hit.query)
            binding.root.setOnClickListener { onResultClick(message) }
        }

        /** `You` for outgoing bubbles, otherwise the participant name. */
        private fun senderLabel(message: MessageUi): String {
            if (message.isMine) return "You"
            return message.senderName?.takeIf { it.isNotBlank() } ?: "Message"
        }

        /** Media bubbles have no text body, so fall back to the same labels as the inbox. */
        private fun snippet(message: MessageUi): String {
            val body = message.body.trim()
            if (body.isNotEmpty()) return body
            return when (message.kind) {
                MessageKind.IMAGE -> "Photo"
                MessageKind.VIDEO -> "Video"
                MessageKind.AUDIO -> "Voice message"
                else -> "Message"
            }
        }

        private fun highlight(text: String, query: String): CharSequence {
            if (query.isBlank()) return text
            val lowerText = text.lowercase()
            val lowerQuery = query.lowercase()
            val spannable = SpannableString(text)
            var index = lowerText.indexOf(lowerQuery)
            if (index == -1) return text

            val accent = ContextCompat.getColor(binding.root.context, R.color.brand_primary)
            while (index >= 0) {
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    index,
                    index + lowerQuery.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    ForegroundColorSpan(accent),
                    index,
                    index + lowerQuery.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                index = lowerText.indexOf(lowerQuery, index + lowerQuery.length)
            }
            return spannable
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<SearchHit>() {
            override fun areItemsTheSame(oldItem: SearchHit, newItem: SearchHit): Boolean =
                oldItem.message.id == newItem.message.id

            override fun areContentsTheSame(oldItem: SearchHit, newItem: SearchHit): Boolean =
                oldItem == newItem
        }
    }
}
