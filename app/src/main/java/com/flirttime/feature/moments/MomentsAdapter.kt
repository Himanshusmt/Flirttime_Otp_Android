package com.flirttime.feature.moments

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.flirttime.R
import com.flirttime.core.common.IsoDate
import com.flirttime.databinding.ItemMomentLoadingBinding
import com.flirttime.databinding.ItemMomentPostBinding

class MomentsAdapter(
    private var posts: MutableList<MomentPost> = mutableListOf(),
    private val onLikeClick: ((MomentPost, Int) -> Unit)? = null,
    private val onCommentClick: ((MomentPost) -> Unit)? = null,
    private val onGiftClick: ((MomentPost) -> Unit)? = null,
    private val onMoreOptionsClick: ((MomentPost) -> Unit)? = null,
    private val onUserClick: ((MomentPost) -> Unit)? = null,
    /** Long press on the heart: show who liked the moment. */
    private val onLikeLongClick: ((MomentPost) -> Unit)? = null,
    /** Tap on a photo: open the full-screen viewer at that index. */
    private val onMediaClick: ((MomentPost, Int) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_LOADING = 1
        const val PAYLOAD_LIKE_UPDATE = "payload_like_update"
        const val PAYLOAD_CAPTION = "payload_caption"

        /** Longer captions are cut here with a "more" toggle (iOS `captionPreviewLength`). */
        const val CAPTION_PREVIEW_LENGTH = 250
    }

    /** Moments whose long caption the user expanded with "more". */
    private val expandedCaptionIds = mutableSetOf<String>()

    private var isLoadingFooterVisible: Boolean = false

    val currentList: List<MomentPost>
        get() = posts.toList()

    fun submitList(newPosts: List<MomentPost>) {
        posts.clear()
        posts.addAll(newPosts)
        notifyDataSetChanged()
    }

    fun removePost(postId: String) {
        val index = posts.indexOfFirst { it.id == postId }
        if (index != -1) {
            posts.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun removePostsByAuthor(authorId: String) {
        val iterator = posts.iterator()
        var changed = false
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.userId == authorId) {
                iterator.remove()
                changed = true
            }
        }
        if (changed) {
            notifyDataSetChanged()
        }
    }

    fun notifyLikeChanged(position: Int) {
        if (position in posts.indices) {
            notifyItemChanged(position, PAYLOAD_LIKE_UPDATE)
        }
    }

    fun findPostPosition(postId: String): Int {
        return posts.indexOfFirst { it.id == postId }
    }

    fun setLoadingFooter(visible: Boolean) {
        if (isLoadingFooterVisible == visible) return
        isLoadingFooterVisible = visible
        if (visible) notifyItemInserted(posts.size) else notifyItemRemoved(posts.size)
    }

    /** Rebinds one row after its like / comment / gift counts changed. */
    fun notifyPostChanged(postId: String) {
        val index = findPostPosition(postId)
        if (index != -1) notifyItemChanged(index)
    }

    fun appendPosts(more: List<MomentPost>) {
        val existing = posts.map { it.id }.toHashSet()
        val fresh = more.filter { it.id !in existing }
        if (fresh.isEmpty()) return
        val start = posts.size
        posts.addAll(fresh)
        notifyItemRangeInserted(start, fresh.size)
    }

    override fun getItemViewType(position: Int): Int {
        return if (isLoadingFooterVisible && position == posts.size) {
            VIEW_TYPE_LOADING
        } else {
            VIEW_TYPE_ITEM
        }
    }

    override fun getItemCount(): Int = posts.size + if (isLoadingFooterVisible) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_LOADING) {
            val binding = ItemMomentLoadingBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            LoadingFooterViewHolder(binding)
        } else {
            val binding = ItemMomentPostBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            MomentViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is MomentViewHolder && position < posts.size) {
            holder.bind(posts[position])
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty() && holder is MomentViewHolder && position < posts.size &&
            (payloads.contains(PAYLOAD_LIKE_UPDATE) || payloads.contains(PAYLOAD_CAPTION))
        ) {
            if (payloads.contains(PAYLOAD_LIKE_UPDATE)) holder.updateLikeUi(posts[position])
            if (payloads.contains(PAYLOAD_CAPTION)) holder.bindCaption(posts[position])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    class LoadingFooterViewHolder(binding: ItemMomentLoadingBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class MomentViewHolder(private val binding: ItemMomentPostBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val mediaAdapter = MomentMediaPagerAdapter()
        private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null
        private var boundPost: MomentPost? = null
        private var overPhoto = true

        private val ctx get() = binding.root.context
        private val density get() = ctx.resources.displayMetrics.density
        private fun dp(value: Int) = (value * density).toInt()

        private val gestureDetector = GestureDetector(
            binding.root.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    val post = boundPost ?: return false
                    onMediaClick?.invoke(post, binding.vpPostMedia.currentItem)
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val post = boundPost ?: return false
                    showHeartBurst(e.x, e.y)
                    // iOS: a double tap only likes, it never un-likes.
                    if (!post.isLiked) performLike(post)
                    return true
                }
            }
        )

        init {
            binding.vpPostMedia.adapter = mediaAdapter
            setupTouchHandling(binding.vpPostMedia)
        }

        private fun setupTouchHandling(viewPager: ViewPager2) {
            var startX = 0f
            var startY = 0f
            val innerRv = viewPager.getChildAt(0) as? RecyclerView ?: return
            innerRv.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    gestureDetector.onTouchEvent(e)
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = e.x
                            startY = e.y
                            rv.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = Math.abs(e.x - startX)
                            val dy = Math.abs(e.y - startY)
                            if (dx > dy && dx > 8f) {
                                rv.parent?.requestDisallowInterceptTouchEvent(true)
                            } else if (dy > dx && dy > 8f) {
                                rv.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            rv.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    return false
                }
            })
        }

        fun bind(post: MomentPost) {
            boundPost = post

            // User avatar
            if (!post.avatarUrl.isNullOrBlank()) {
                binding.ivAvatar.setImageURI(post.avatarUrl)
            } else if (post.avatarRes != null && post.avatarRes != 0) {
                binding.ivAvatar.setActualImageResource(post.avatarRes)
            } else {
                binding.ivAvatar.setActualImageResource(R.drawable.img_login)
            }
            binding.ivAvatar.setOnClickListener { onUserClick?.invoke(post) }
            binding.tvUsername.setOnClickListener { onUserClick?.invoke(post) }

            // Username & verified badge
            binding.tvUsername.text = post.username
            binding.ivVerified.visibility = if (post.isVerified) View.VISIBLE else View.GONE

            // Location · time (hidden when empty)
            binding.tvLocationTime.text = post.locationAndTime
            binding.tvLocationTime.visibility = if (post.locationAndTime.isBlank()) View.GONE else View.VISIBLE

            bindCaption(post)

            // Photos: the whole card disappears for text-only moments
            val allMedia = post.getAllMediaUrls()
            val totalCount = allMedia.size
            val hasMedia = totalCount > 0
            binding.cardMediaContainer.visibility = if (hasMedia) View.VISIBLE else View.GONE
            placeActions(overPhoto = hasMedia)

            mediaAdapter.setMedia(allMedia, post.mediaRes)

            pageChangeCallback?.let { binding.vpPostMedia.unregisterOnPageChangeCallback(it) }
            val newCallback = object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    post.currentMediaIndex = position
                    updateIndicators(position, totalCount)
                }
            }
            pageChangeCallback = newCallback
            binding.vpPostMedia.registerOnPageChangeCallback(newCallback)

            val initialIndex = post.currentMediaIndex.coerceIn(0, (totalCount - 1).coerceAtLeast(0))
            binding.vpPostMedia.setCurrentItem(initialIndex, false)
            binding.vpPostMedia.isUserInputEnabled = (totalCount > 1)
            updateIndicators(initialIndex, totalCount)

            // Action counts
            updateLikeUi(post)
            binding.tvCommentCount.text = IsoDate.compactCount(post.commentCount)
            binding.tvGiftCount.text = IsoDate.compactCount(post.giftCount)
            binding.btnComment.contentDescription = "Comments, ${post.commentCount}"
            binding.btnGift.contentDescription = "Gifts, ${post.giftCount}"

            binding.btnLike.setOnClickListener { performLike(post) }
            binding.btnLike.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onLikeLongClick?.invoke(post)
                onLikeLongClick != null
            }
            binding.btnComment.setOnClickListener { bounce(binding.flCommentCircle); onCommentClick?.invoke(post) }
            binding.btnGift.setOnClickListener { bounce(binding.flGiftCircle); onGiftClick?.invoke(post) }
            binding.btnMoreOptions.setOnClickListener { onMoreOptionsClick?.invoke(post) }
        }

        /** Optimistic like toggle, then the host reconciles with the server (iOS `toggleLike`). */
        private fun performLike(post: MomentPost) {
            post.isLiked = !post.isLiked
            post.likeCount += if (post.isLiked) 1 else -1
            if (post.likeCount < 0) post.likeCount = 0
            bounce(binding.flLikeCircle)
            updateLikeUi(post)
            onLikeClick?.invoke(post, adapterPosition)
        }

        private fun bounce(view: View) {
            view.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100).withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }.start()
        }

        private fun showHeartBurst(x: Float, y: Float) {
            // Touch coordinates are relative to the inner RecyclerView, which fills the media frame.
            val size = dp(80)
            val heart = ImageView(ctx).apply {
                setImageResource(R.drawable.ic_moment_heart_filled)
                imageTintList = ColorStateList.valueOf(Color.parseColor("#D92B2B"))
                layoutParams = FrameLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.TOP or Gravity.START
                    leftMargin = (x - size / 2).toInt()
                    topMargin = (y - size / 2).toInt()
                }
                scaleX = 0.3f; scaleY = 0.3f; alpha = 0f
            }
            val frame = binding.frameMedia
            frame.addView(heart)
            heart.animate().alpha(1f).scaleX(1.2f).scaleY(1.2f).setDuration(180).withEndAction {
                heart.animate().alpha(0f).scaleX(1.5f).scaleY(1.5f).setStartDelay(220).setDuration(220)
                    .withEndAction { frame.removeView(heart) }.start()
            }.start()
        }

        /** Moves the like/comment/gift column between the photo (white on frosted) and a plain row below the caption. */
        private fun placeActions(overPhoto: Boolean) {
            val actions = binding.layoutActions
            val targetParent: ViewGroup = if (overPhoto) binding.frameMedia else binding.layoutRowActions
            if (actions.parent !== targetParent) {
                (actions.parent as? ViewGroup)?.removeView(actions)
                if (overPhoto) {
                    actions.orientation = LinearLayout.VERTICAL
                    targetParent.addView(
                        actions,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM or Gravity.END
                        ).apply {
                            marginEnd = dp(8)
                            bottomMargin = dp(10)
                        }
                    )
                } else {
                    actions.orientation = LinearLayout.HORIZONTAL
                    targetParent.addView(
                        actions,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                }
            }
            binding.layoutRowActions.visibility = if (overPhoto) View.GONE else View.VISIBLE
            this.overPhoto = overPhoto

            // Spacing between buttons: 10dp in the column, 20dp in the row.
            listOf(binding.btnComment, binding.btnGift).forEach { button ->
                (button.layoutParams as LinearLayout.LayoutParams).apply {
                    topMargin = if (overPhoto) dp(10) else 0
                    marginStart = if (overPhoto) 0 else dp(20)
                    button.layoutParams = this
                }
            }
            applyActionStyle()
        }

        private fun applyActionStyle() {
            val circleBg = if (overPhoto) R.drawable.bg_moment_action_over_photo else R.drawable.bg_moment_action_plain
            val iconColor = Color.parseColor(if (overPhoto) "#FFFFFF" else "#252525")
            val countColor = Color.parseColor(if (overPhoto) "#FFFFFF" else "#646464")
            val countFont = ResourcesCompat.getFont(
                ctx, if (overPhoto) R.font.fredokamedium else R.font.fredokaregular
            )
            listOf(binding.flLikeCircle, binding.flCommentCircle, binding.flGiftCircle).forEach {
                it.setBackgroundResource(circleBg)
            }
            listOf(binding.ivCommentIcon, binding.ivGiftIcon).forEach {
                it.imageTintList = ColorStateList.valueOf(iconColor)
            }
            listOf(binding.tvLikeCount, binding.tvCommentCount, binding.tvGiftCount).forEach {
                it.setTextColor(countColor)
                it.typeface = countFont
                if (overPhoto) it.setShadowLayer(2f * density, 0f, 1f * density, Color.parseColor("#99000000"))
                else it.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            }
        }

        /** Caption with "… more" / "less" past [CAPTION_PREVIEW_LENGTH] characters (iOS `setCaptionExpanded`). */
        fun bindCaption(post: MomentPost) {
            val caption = post.caption.trim()
            if (caption.isEmpty()) {
                binding.tvCaption.visibility = View.GONE
                binding.tvCaption.setOnClickListener(null)
                return
            }
            binding.tvCaption.visibility = View.VISIBLE
            if (caption.length <= CAPTION_PREVIEW_LENGTH) {
                binding.tvCaption.text = caption
                binding.tvCaption.setOnClickListener(null)
                binding.tvCaption.isClickable = false
                return
            }
            val expanded = post.id in expandedCaptionIds
            val visible = if (expanded) "$caption " else caption.take(CAPTION_PREVIEW_LENGTH).trim() + "… "
            val toggleLabel = if (expanded) "less" else "more"
            val text = SpannableStringBuilder(visible)
            val start = text.length
            text.append(toggleLabel)
            text.setSpan(ForegroundColorSpan(Color.parseColor("#D92B2B")), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            binding.tvCaption.text = text
            binding.tvCaption.setOnClickListener {
                if (expanded) expandedCaptionIds.remove(post.id) else expandedCaptionIds.add(post.id)
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos, PAYLOAD_CAPTION)
            }
        }

        internal fun updateLikeUi(post: MomentPost) {
            binding.tvLikeCount.text = IsoDate.compactCount(post.likeCount)
            binding.btnLike.contentDescription =
                (if (post.isLiked) "Unlike" else "Like") + ", ${post.likeCount} likes"
            if (post.isLiked) {
                binding.ivLikeIcon.setImageResource(R.drawable.ic_moment_heart_filled)
                binding.ivLikeIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#D92B2B"))
            } else {
                binding.ivLikeIcon.setImageResource(R.drawable.ic_moment_heart)
                binding.ivLikeIcon.imageTintList = ColorStateList.valueOf(
                    Color.parseColor(if (overPhoto) "#FFFFFF" else "#252525")
                )
            }
        }

        private fun updateIndicators(selectedIndex: Int, totalCount: Int) {
            if (totalCount <= 1) {
                binding.layoutMediaCountBadge.visibility = View.GONE
                binding.layoutMediaDotsIndicator.visibility = View.GONE
                return
            }

            // Top-right "current/total"
            binding.layoutMediaCountBadge.visibility = View.VISIBLE
            binding.tvMediaCountBadge.text = "${selectedIndex + 1}/$totalCount"

            // Bottom-left: 28dp white pill for the current photo, 6dp dots for the rest
            binding.layoutMediaDotsIndicator.visibility = View.VISIBLE
            val container = binding.layoutMediaDotsIndicator
            val dotSize = dp(6)
            val activeWidth = dp(28)

            if (container.childCount != totalCount) {
                container.removeAllViews()
                for (i in 0 until totalCount) {
                    val dot = View(container.context)
                    dot.layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                        if (i > 0) marginStart = dp(5)
                    }
                    container.addView(dot)
                }
            }
            for (i in 0 until totalCount) {
                val dot = container.getChildAt(i)
                val isActive = i == selectedIndex
                val lp = dot.layoutParams as LinearLayout.LayoutParams
                val targetWidth = if (isActive) activeWidth else dotSize
                if (lp.width != targetWidth) {
                    lp.width = targetWidth
                    dot.layoutParams = lp
                }
                dot.setBackgroundResource(
                    if (isActive) R.drawable.bg_moment_dot_active else R.drawable.bg_moment_dot_inactive
                )
            }
        }
    }
}
