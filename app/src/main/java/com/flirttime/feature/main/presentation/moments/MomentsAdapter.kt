package com.flirttime.feature.main.presentation.moments

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.widget.LinearLayout
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import com.flirttime.R
import com.flirttime.core.common.ShimmerHelper
import com.flirttime.databinding.ItemMomentLoadingBinding
import com.flirttime.databinding.ItemMomentPostBinding

class MomentsAdapter(
    private var posts: MutableList<MomentPost> = mutableListOf(),
    private val onLikeClick: ((MomentPost, Int) -> Unit)? = null,
    private val onCommentClick: ((MomentPost) -> Unit)? = null,
    private val onGiftClick: ((MomentPost) -> Unit)? = null,
    private val onMoreOptionsClick: ((MomentPost) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_ITEM = 0
        private const val VIEW_TYPE_LOADING = 1
    }

    private var isLoadingFooterVisible: Boolean = false

    fun submitList(newPosts: List<MomentPost>) {
        posts.clear()
        posts.addAll(newPosts)
        notifyDataSetChanged()
    }

    fun setLoadingFooter(visible: Boolean) {
        if (isLoadingFooterVisible == visible) return
        isLoadingFooterVisible = visible
        if (visible) {
            notifyItemInserted(posts.size)
        } else {
            notifyItemRemoved(posts.size)
        }
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

    class LoadingFooterViewHolder(binding: ItemMomentLoadingBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class MomentViewHolder(private val binding: ItemMomentPostBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val mediaAdapter = MomentMediaPagerAdapter()
        private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null

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
            // User Avatar with Shimmer
            ShimmerHelper.loadWithShimmer(
                draweeView = binding.ivAvatar,
                url = post.avatarUrl,
                fallbackRes = post.avatarRes
            )

            // Username & Verified Badge
            binding.tvUsername.text = post.username
            binding.ivVerified.visibility = if (post.isVerified) View.VISIBLE else View.GONE

            // Location and Time
            binding.tvLocationTime.text = post.locationAndTime

            // Caption (Hide when empty to avoid taking space)
            if (post.caption.isNullOrBlank()) {
                binding.tvCaption.visibility = View.GONE
            } else {
                binding.tvCaption.visibility = View.VISIBLE
                binding.tvCaption.text = post.caption
            }

            // Setup Media ViewPager2 & Indicators
            val allMedia = post.getAllMediaUrls()
            val totalCount = allMedia.size

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

            // Action Counts
            updateLikeUi(post)
            binding.tvCommentCount.text = post.commentCount.toString()
            binding.tvGiftCount.text = post.giftCount.toString()

            // Like Action with Bounce Animation
            binding.btnLike.setOnClickListener {
                post.isLiked = !post.isLiked
                post.likeCount += if (post.isLiked) 1 else -1
                if (post.likeCount < 0) post.likeCount = 0

                // Animate like button bounce
                binding.ivLikeIcon.animate()
                    .scaleX(1.3f)
                    .scaleY(1.3f)
                    .setDuration(120)
                    .withEndAction {
                        binding.ivLikeIcon.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(120)
                            .start()
                    }
                    .start()

                updateLikeUi(post)
                onLikeClick?.invoke(post, adapterPosition)
            }

            // Comment Action
            binding.btnComment.setOnClickListener {
                onCommentClick?.invoke(post)
            }

            // Gift Action
            binding.btnGift.setOnClickListener {
                onGiftClick?.invoke(post)
            }

            // More Options Action
            binding.btnMoreOptions.setOnClickListener {
                onMoreOptionsClick?.invoke(post)
            }
        }

        private fun updateLikeUi(post: MomentPost) {
            binding.tvLikeCount.text = post.likeCount.toString()
            if (post.isLiked) {
                binding.ivLikeIcon.setImageResource(R.drawable.ic_thread_heart_filled)
                binding.ivLikeIcon.imageTintList = null
            } else {
                binding.ivLikeIcon.setImageResource(R.drawable.ic_thread_heart)
                binding.ivLikeIcon.imageTintList = null
            }
        }

        private fun updateIndicators(selectedIndex: Int, totalCount: Int) {
            if (totalCount <= 1) {
                binding.layoutMediaCountBadge.visibility = View.GONE
                binding.layoutMediaDotsIndicator.visibility = View.GONE
                return
            }

            // 1. Top-Right Count Badge (e.g. 1/5)
            binding.layoutMediaCountBadge.visibility = View.VISIBLE
            binding.tvMediaCountBadge.text = "${selectedIndex + 1}/$totalCount"

            // 2. Bottom-Left Dots Indicator (Active capsule + Inactive dots)
            binding.layoutMediaDotsIndicator.visibility = View.VISIBLE
            val dotsContainer = binding.layoutMediaDotsIndicator
            val density = dotsContainer.context.resources.displayMetrics.density

            if (dotsContainer.childCount != totalCount) {
                dotsContainer.removeAllViews()
                for (i in 0 until totalCount) {
                    val dot = View(dotsContainer.context)
                    val isActive = (i == selectedIndex)
                    val w = if (isActive) (20f * density).toInt() else (5f * density).toInt()
                    val h = (5f * density).toInt()
                    val lp = LinearLayout.LayoutParams(w, h).apply {
                        if (i > 0) marginStart = (4f * density).toInt()
                    }
                    dot.layoutParams = lp
                    dot.setBackgroundResource(
                        if (isActive) R.drawable.bg_moment_dot_active
                        else R.drawable.bg_moment_dot_inactive
                    )
                    dotsContainer.addView(dot)
                }
            } else {
                for (i in 0 until totalCount) {
                    val dot = dotsContainer.getChildAt(i)
                    val isActive = (i == selectedIndex)
                    val targetWidth = if (isActive) (20f * density).toInt() else (5f * density).toInt()
                    val lp = dot.layoutParams as LinearLayout.LayoutParams
                    if (lp.width != targetWidth) {
                        lp.width = targetWidth
                        dot.layoutParams = lp
                    }
                    dot.setBackgroundResource(
                        if (isActive) R.drawable.bg_moment_dot_active
                        else R.drawable.bg_moment_dot_inactive
                    )
                }
            }
        }
    }
}
