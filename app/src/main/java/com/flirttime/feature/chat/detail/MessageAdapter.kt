package com.flirttime.feature.chat.detail

import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.facebook.drawee.view.SimpleDraweeView
import com.flirttime.R
import com.flirttime.core.common.MediaUrlHelper
import com.flirttime.databinding.ItemChatDateSeparatorBinding
import com.flirttime.databinding.ItemMessageBinding
import com.flirttime.feature.chat.model.DeliveryState
import com.flirttime.feature.chat.model.MessageKind
import com.flirttime.feature.chat.model.MessageRow
import com.flirttime.feature.chat.model.MessageUi
import com.flirttime.feature.chat.model.ReactionUi
import com.flirttime.feature.chat.model.ReplyPreviewUi
import com.flirttime.feature.main.profile.widget.AudioWaveformView
import java.io.File
import java.util.Locale

/**
 * Renders a conversation thread: day separators, incoming/outgoing bubbles, quoted replies,
 * emoji reactions, image / video thumbnails, voice notes, delivery ticks and system notices.
 */
class MessageAdapter(
    private val onRetry: (MessageUi) -> Unit,
    private val isGroupChat: Boolean = false,
    private val onAudioClick: (MessageUi) -> Unit = {},
    private val onVideoClick: (MessageUi) -> Unit = {},
    private val onImageClick: (MessageUi) -> Unit = {},
    private val onLongPress: (MessageUi) -> Unit = {},
    private val onSelectToggle: (MessageUi) -> Unit = {},
    private val onReplyPreviewClick: (MessageUi, String) -> Unit = { _, _ -> },
    private val onReactionClick: (MessageUi, ReactionUi) -> Unit = { _, _ -> }
) : ListAdapter<MessageRow, RecyclerView.ViewHolder>(DIFF) {

    private var playingMessageId: String? = null
    private var playingProgress: Float = 0f

    /** Bulk-action state: rows show a check control and taps toggle instead of opening. */
    private var selectionMode = false
    private var selectedIds: Set<String> = emptySet()

    fun setSelectionMode(enabled: Boolean) {
        if (selectionMode == enabled) return
        selectionMode = enabled
        if (!enabled) selectedIds = emptySet()
        notifyItemRangeChanged(0, itemCount)
    }

    fun setSelectedIds(ids: Set<String>) {
        selectedIds = ids
        notifyItemRangeChanged(0, itemCount)
    }

    /** Called by the player: swaps the play icon and scrubs the waveform in place. */
    fun setPlaybackState(messageId: String?, progress: Float = 0f) {
        val previousId = playingMessageId
        playingMessageId = messageId
        playingProgress = progress.coerceIn(0f, 1f)

        listOf(previousId, messageId)
            .filterNotNull()
            .distinct()
            .forEach { id ->
                val index = currentList.indexOfFirst { it is MessageRow.Message && it.data.id == id }
                if (index != -1) notifyItemChanged(index, PAYLOAD_PLAYBACK)
            }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is MessageRow.Separator -> TYPE_SEPARATOR
        is MessageRow.Message -> TYPE_MESSAGE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SEPARATOR) {
            SeparatorViewHolder(ItemChatDateSeparatorBinding.inflate(inflater, parent, false))
        } else {
            MessageViewHolder(ItemMessageBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SeparatorViewHolder -> holder.bind(getItem(position) as MessageRow.Separator)
            is MessageViewHolder -> holder.bind((getItem(position) as MessageRow.Message).data)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        val message = (getItem(position) as? MessageRow.Message)?.data
        if (payloads.isNotEmpty() && holder is MessageViewHolder && message != null) {
            if (message.kind == MessageKind.AUDIO) {
                holder.bindAudioPlayback(message)
                return
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    private class SeparatorViewHolder(
        private val binding: ItemChatDateSeparatorBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(separator: MessageRow.Separator) {
            binding.tvDateLabel.text = separator.label
        }
    }

    private inner class MessageViewHolder(
        private val binding: ItemMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: MessageUi) {
            binding.layoutIncoming.visibility = View.GONE
            binding.layoutOutgoing.visibility = View.GONE
            binding.tvSystemMessage.visibility = View.GONE
            binding.frameSelection.visibility = View.GONE
            binding.frameSelection.setOnClickListener(null)

            if (message.isSystem) {
                binding.tvSystemMessage.visibility = View.VISIBLE
                binding.tvSystemMessage.text = message.body.ifBlank { FALLBACK_SYSTEM }
                return
            }

            if (message.isMine) bindOutgoing(message) else bindIncoming(message)
            bindSelection(message)
        }

        /** Dims the row and shows the check bubble while multi-select is active. */
        private fun bindSelection(message: MessageUi) {
            if (!selectionMode) return
            val selected = selectedIds.contains(message.id)
            binding.frameSelection.visibility = View.VISIBLE
            binding.ivSelectionCheck.setImageResource(
                if (selected) R.drawable.ic_action_check_circle else R.drawable.ic_action_circle_outline
            )
            binding.frameSelection.setOnClickListener { onSelectToggle(message) }
        }

        private fun bindIncoming(message: MessageUi) {
            binding.layoutIncoming.visibility = View.VISIBLE
            attachLongPress(binding.layoutIncoming, message)
            attachLongPress(binding.frameIncomingMedia, message)
            attachLongPress(binding.layoutIncomingAudio, message)
            attachLongPress(binding.layoutIncomingReply, message)
            binding.tvIncomingTime.text = timeLabel(message)
            binding.ivIncomingPinned.isVisible = message.isPinned

            val senderName = message.senderName
            if (!senderName.isNullOrBlank()) {
                binding.tvSenderName.visibility = View.VISIBLE
                binding.tvSenderName.text = senderName
            } else {
                binding.tvSenderName.visibility = View.GONE
            }

            // Group threads show who wrote the bubble, so avatars lead the sender row.
            val avatarUrl = MediaUrlHelper.resolveMediaUrl(message.senderAvatarUrl)
            val showAvatar = isGroupChat && !message.isMine && !avatarUrl.isNullOrBlank()
            binding.ivSenderAvatar.isVisible = showAvatar
            if (showAvatar) {
                binding.ivSenderAvatar.setImageURI(avatarUrl)
            } else {
                binding.ivSenderAvatar.setImageURI(null as String?)
            }
            binding.layoutSenderHeader.isVisible =
                !message.isMine && (isGroupChat || !senderName.isNullOrBlank())

            bindReplyPreview(
                message = message,
                container = binding.layoutIncomingReply,
                sender = binding.tvIncomingReplySender,
                body = binding.tvIncomingReplyBody
            )
            bindIncomingMedia(message)
            bindCaption(binding.tvIncomingBody, message.body, message.isDeleted)
            bindReactions(binding.layoutIncomingReactions, message)
        }

        private fun bindOutgoing(message: MessageUi) {
            val context = binding.root.context
            val failed = message.isFailed || message.deliveryState == DeliveryState.FAILED

            binding.layoutOutgoing.visibility = View.VISIBLE
            attachLongPress(binding.layoutOutgoing, message)
            attachLongPress(binding.frameOutgoingMedia, message)
            attachLongPress(binding.layoutOutgoingAudio, message)
            attachLongPress(binding.layoutOutgoingReply, message)

            bindReplyPreview(
                message = message,
                container = binding.layoutOutgoingReply,
                sender = binding.tvOutgoingReplySender,
                body = binding.tvOutgoingReplyBody
            )
            bindOutgoingMedia(message)
            bindCaption(binding.tvOutgoingBody, message.body, message.isDeleted)
            bindReactions(binding.layoutOutgoingReactions, message)

            binding.ivOutgoingPinned.isVisible = message.isPinned
            binding.tvOutgoingTime.text = timeLabel(message)
            binding.tvStatus.text = statusLabel(message, failed)
            binding.tvStatus.setTextColor(
                ContextCompat.getColor(
                    context,
                    when {
                        failed -> R.color.brand_primary
                        // Seen wins over the delivered double tick, exactly like iOS.
                        message.deliveryState == DeliveryState.READ -> R.color.brand_primary
                        else -> R.color.text_tertiary
                    }
                )
            )
            binding.tvStatus.isClickable = failed
            binding.tvStatus.isFocusable = failed
            binding.tvStatus.setOnClickListener(
                if (failed) View.OnClickListener { onRetry(message) } else null
            )
        }

        /** Long-press opens the action sheet; while selecting it toggles the row instead. */
        private fun attachLongPress(view: View, message: MessageUi) {
            view.setOnLongClickListener {
                if (selectionMode) onSelectToggle(message) else onLongPress(message)
                true
            }
        }

        private fun bindCaption(view: TextView, body: String, deleted: Boolean) {
            val caption = body.trim()
            view.isVisible = caption.isNotEmpty()
            view.text = caption
            view.typeface = Typeface.create(view.typeface, if (deleted) Typeface.ITALIC else Typeface.NORMAL)
        }

        private fun bindReplyPreview(
            message: MessageUi,
            container: View,
            sender: TextView,
            body: TextView
        ) {
            val reply = message.reply
            if (reply == null) {
                container.isVisible = false
                container.setOnClickListener(null)
                return
            }

            container.isVisible = true
            sender.text = reply.senderName?.takeIf { it.isNotBlank() }
                ?: if (reply.isMine) "You" else "Message"
            body.text = replyLabel(reply)
            container.setOnClickListener { onReplyPreviewClick(message, reply.id) }
        }

        /** Builds up to four reaction pills; the count is hidden for single reactions. */
        private fun bindReactions(container: LinearLayout, message: MessageUi) {
            container.removeAllViews()
            val reactions = message.visibleReactions()
            container.isVisible = reactions.isNotEmpty()
            if (reactions.isEmpty()) return

            reactions.forEachIndexed { index, reaction ->
                val pill = TextView(container.context).apply {
                    text = if (reaction.count > 1) "${reaction.emoji} ${reaction.count}" else reaction.emoji
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    background = ContextCompat.getDrawable(
                        context,
                        if (reaction.reactedByMe) R.drawable.bg_reaction_pill_mine
                        else R.drawable.bg_reaction_pill
                    )
                    val horizontal = (5 * resources.displayMetrics.density).toInt()
                    val vertical = (2 * resources.displayMetrics.density).toInt()
                    setPadding(horizontal, vertical, horizontal, vertical)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onReactionClick(message, reaction) }
                }
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                if (index > 0) params.marginStart = (4 * container.resources.displayMetrics.density).toInt()
                container.addView(pill, params)
            }
        }

        private fun timeLabel(message: MessageUi): String =
            if (message.isEdited) "${message.timeLabel} · Edited" else message.timeLabel

        private fun bindIncomingMedia(message: MessageUi) {
            val deleted = message.isDeleted
            val isVisual = !deleted && (message.kind == MessageKind.IMAGE || message.kind == MessageKind.VIDEO)
            val isAudio = !deleted && message.kind == MessageKind.AUDIO

            binding.frameIncomingMedia.isVisible = isVisual
            binding.layoutIncomingAudio.isVisible = isAudio

            if (isVisual) {
                renderVisual(
                    message = message,
                    frame = binding.frameIncomingMedia,
                    image = binding.ivIncomingImage,
                    playOverlay = binding.ivIncomingPlay,
                    durationChip = binding.tvIncomingMediaDuration
                )
            }
            if (isAudio) {
                binding.tvIncomingAudioDuration.text = formatDuration(message.durationMs)
                binding.layoutIncomingAudio.setOnClickListener { onAudioClick(message) }
                binding.ivIncomingAudioPlay.setOnClickListener { onAudioClick(message) }
                binding.waveIncoming.setOnClickListener { onAudioClick(message) }
                bindAudioPlayback(message)
            }
        }

        private fun bindOutgoingMedia(message: MessageUi) {
            val deleted = message.isDeleted
            val isVisual = !deleted && (message.kind == MessageKind.IMAGE || message.kind == MessageKind.VIDEO)
            val isAudio = !deleted && message.kind == MessageKind.AUDIO

            binding.frameOutgoingMedia.isVisible = isVisual
            binding.layoutOutgoingAudio.isVisible = isAudio

            if (isVisual) {
                renderVisual(
                    message = message,
                    frame = binding.frameOutgoingMedia,
                    image = binding.ivOutgoingImage,
                    playOverlay = binding.ivOutgoingPlay,
                    durationChip = binding.tvOutgoingMediaDuration
                )
            }
            if (isAudio) {
                binding.tvOutgoingAudioDuration.text = formatDuration(message.durationMs)
                binding.layoutOutgoingAudio.setOnClickListener { onAudioClick(message) }
                binding.ivOutgoingAudioPlay.setOnClickListener { onAudioClick(message) }
                binding.waveOutgoing.setOnClickListener { onAudioClick(message) }
                bindAudioPlayback(message)
            }
        }

        private fun renderVisual(
            message: MessageUi,
            frame: View,
            image: SimpleDraweeView,
            playOverlay: ImageView,
            durationChip: android.widget.TextView
        ) {
            applyAspectSize(frame, image, message)

            val uri = resolveMediaUri(message)
            image.setImageURI(uri)

            val isVideo = message.kind == MessageKind.VIDEO
            playOverlay.isVisible = isVideo
            playOverlay.setOnClickListener { if (isVideo) onVideoClick(message) }

            val duration = if (isVideo) formatDuration(message.durationMs) else null
            durationChip.isVisible = !duration.isNullOrBlank()
            durationChip.text = duration.orEmpty()

            image.setOnClickListener { onImageClick(message) }
        }

        /** Swaps only the play icon + waveform for audio rows (used for progress ticks). */
        fun bindAudioPlayback(message: MessageUi) {
            val isMine = message.isMine
            val icon = if (isMine) binding.ivOutgoingAudioPlay else binding.ivIncomingAudioPlay
            val wave = if (isMine) binding.waveOutgoing else binding.waveIncoming

            val isPlaying = playingMessageId != null && playingMessageId == message.id
            wave.setWaveColors(
                if (isMine) WAVE_INACTIVE_ON_BRAND else WAVE_INACTIVE_DEFAULT,
                if (isMine) WAVE_ACTIVE_ON_BRAND else WAVE_ACTIVE_DEFAULT
            )
            wave.setProgress(if (isPlaying) playingProgress else 0f)
            icon.setImageResource(if (isPlaying) R.drawable.ic_pause_white else R.drawable.ic_play_white)
        }

        private fun applyAspectSize(frame: View, image: SimpleDraweeView, message: MessageUi) {
            val widthPx = frame.resources.getDimensionPixelSize(R.dimen.chat_media_width)
            val ratio = if (message.mediaWidth > 0 && message.mediaHeight > 0) {
                message.mediaWidth.toFloat() / message.mediaHeight.toFloat()
            } else {
                DEFAULT_MEDIA_RATIO
            }
            val minHeight = frame.resources.getDimensionPixelSize(R.dimen.chat_media_min_height)
            val maxHeight = frame.resources.getDimensionPixelSize(R.dimen.chat_media_max_height)
            val heightPx = (widthPx / ratio).toInt().coerceIn(minHeight, maxHeight)

            frame.layoutParams = frame.layoutParams.apply {
                width = widthPx
                height = heightPx
            }
            image.layoutParams = image.layoutParams.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
        }
    }

    private companion object {
        const val TYPE_SEPARATOR = 0
        const val TYPE_MESSAGE = 1
        const val FALLBACK_SYSTEM = "Conversation updated"
        const val PAYLOAD_PLAYBACK = "playback"
        const val DEFAULT_MEDIA_RATIO = 3f / 4f

        // Waveform colours: pink/red on the pale incoming bubble, white on the brand bubble.
        val WAVE_INACTIVE_DEFAULT = Color.parseColor("#F4AAB6")
        val WAVE_ACTIVE_DEFAULT = Color.parseColor("#E53935")
        val WAVE_INACTIVE_ON_BRAND = Color.parseColor("#80FFFFFF")
        val WAVE_ACTIVE_ON_BRAND = Color.WHITE

        fun statusLabel(message: MessageUi, failed: Boolean): String = when {
            failed -> "Not sent · Tap to retry"
            message.deliveryState == DeliveryState.SENDING -> "Sending…"
            message.deliveryState == DeliveryState.READ -> "✓✓"
            message.deliveryState == DeliveryState.DELIVERED -> "✓✓"
            else -> "✓"
        }

        fun formatDuration(durationMs: Long): String {
            if (durationMs <= 0L) return ""
            val totalSeconds = (durationMs / 1000L).coerceAtLeast(1L)
            return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
        }

        /** One-line label for a quoted bubble (media types fall back to a type name). */
        fun replyLabel(reply: ReplyPreviewUi): String = when {
            reply.body.isNotBlank() -> reply.body
            reply.kind == MessageKind.IMAGE -> "Photo"
            reply.kind == MessageKind.VIDEO -> "Video"
            reply.kind == MessageKind.AUDIO -> "Voice message"
            else -> "Message"
        }

        fun resolveMediaUri(message: MessageUi): Uri? {
            val local = message.localUri?.takeIf { it.isNotBlank() }
            if (local != null) {
                return when {
                    local.startsWith("content://") || local.startsWith("file://") -> Uri.parse(local)
                    local.startsWith("http://") || local.startsWith("https://") -> Uri.parse(local)
                    else -> Uri.fromFile(File(local))
                }
            }
            val remote = MediaUrlHelper.resolveMediaUrl(message.thumbnailUrl ?: message.mediaUrl)
            return remote?.let { Uri.parse(it) }
        }

        val DIFF = object : DiffUtil.ItemCallback<MessageRow>() {
            override fun areItemsTheSame(oldItem: MessageRow, newItem: MessageRow): Boolean =
                when {
                    oldItem is MessageRow.Separator && newItem is MessageRow.Separator ->
                        oldItem.label == newItem.label
                    oldItem is MessageRow.Message && newItem is MessageRow.Message ->
                        oldItem.data.id == newItem.data.id
                    else -> false
                }

            override fun areContentsTheSame(oldItem: MessageRow, newItem: MessageRow): Boolean =
                oldItem == newItem
        }
    }
}
