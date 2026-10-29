package com.flirttime.feature.main.matches

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import com.flirttime.R
import com.flirttime.core.common.ShimmerHelper
import com.flirttime.databinding.ItemMatchCardBinding
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Modern, high-performance card stack component supporting 4-direction swipes:
 * - RIGHT (+X): Interested
 * - LEFT (-X): Not Interested
 * - UP (-Y): Super Like
 * - DOWN (+Y): Nope
 *
 * Touch detection spans across the entire container area, allowing gestures
 * to start on or outside card boundaries.
 */
class SwipeCardStackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class SwipeDirection {
        LEFT,
        RIGHT,
        UP,
        DOWN
    }

    interface OnCardSwipeListener {
        fun onCardSwiped(direction: SwipeDirection, profile: MatchProfile)
        fun onCardDragging(dx: Float, dy: Float, progress: Float) {}
        fun onStackEmpty() {}
        fun onNearStackEnd() {}
    }

    private var profiles: List<MatchProfile> = emptyList()
    private var currentIndex: Int = 0

    var listener: OnCardSwipeListener? = null
    var onCardClickListener: ((MatchProfile) -> Unit)? = null

    // Stack Views Pooling (3 cards max visible: top, middle, bottom)
    private val cardBindings = ArrayList<ItemMatchCardBinding>(3)
    private var topBindingIndex = 0
    private var middleBindingIndex = 1
    private var bottomBindingIndex = 2

    // Gesture tracking
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var activePointerId: Int = MotionEvent.INVALID_POINTER_ID
    private var isDragging: Boolean = false
    private var isAnimating: Boolean = false
    private var velocityTracker: VelocityTracker? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity
    private val minFlingVelocity = 1000f

    // Visual Stack Offset dimensions (1 clean card stack peeking at bottom)
    private val density = resources.displayMetrics.density
    private val cardMarginH = (16 * density).toInt()
    private val cardMarginT = (8 * density).toInt()
    private val stackTranslationY = 10f * density
    private val stackScale = 0.95f
    private val maxCardWidth = (420 * density).toInt()

    init {
        clipChildren = false
        clipToPadding = false

        val inflater = LayoutInflater.from(context)
        for (i in 0 until 3) {
            val binding = ItemMatchCardBinding.inflate(inflater, this, false)
            cardBindings.add(binding)
            // Add to layout (bottom card will be at child 0, middle child 1, top child 2)
            addView(binding.root)
        }
        applyStackVisualOrder()
        bindAllCards()
    }

    fun submitList(newProfiles: List<MatchProfile>) {
        profiles = newProfiles
        currentIndex = 0
        isAnimating = false
        isDragging = false
        topBindingIndex = 0
        middleBindingIndex = 1
        bottomBindingIndex = 2
        applyStackVisualOrder()
        bindAllCards()
    }

    fun appendList(moreProfiles: List<MatchProfile>) {
        if (moreProfiles.isEmpty()) return
        val wasEmpty = profiles.isEmpty() || currentIndex >= profiles.size
        profiles = profiles + moreProfiles
        if (wasEmpty) {
            bindAllCards()
        } else {
            bindCard(getMiddleBinding(), profiles.getOrNull(currentIndex + 1))
            bindCard(getBottomBinding(), profiles.getOrNull(currentIndex + 2))
        }
    }

    /**
     * Restores a card back onto the top of the deck when an action fails
     * (optimistic swipe rollback as per API documentation).
     */
    fun restoreCard(profile: MatchProfile) {
        if (currentIndex > 0) {
            currentIndex--
            val recycledTop = bottomBindingIndex
            bottomBindingIndex = middleBindingIndex
            middleBindingIndex = topBindingIndex
            topBindingIndex = recycledTop

            applyStackVisualOrder()
            bindAllCards()
        } else {
            profiles = listOf(profile) + profiles
            currentIndex = 0
            applyStackVisualOrder()
            bindAllCards()
        }
    }

    fun hasCards(): Boolean = currentIndex < profiles.size

    fun getCurrentProfile(): MatchProfile? = profiles.getOrNull(currentIndex)

    fun getTopPosition(): Int = currentIndex

    private fun getTopBinding(): ItemMatchCardBinding = cardBindings[topBindingIndex]
    private fun getMiddleBinding(): ItemMatchCardBinding = cardBindings[middleBindingIndex]
    private fun getBottomBinding(): ItemMatchCardBinding = cardBindings[bottomBindingIndex]

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        // 1. Initial card width (capped for tablets/foldables)
        var cardWidth = (parentWidth - (cardMarginH * 2)).coerceAtLeast(0)
        if (cardWidth > maxCardWidth) {
            cardWidth = maxCardWidth
        }

        // 2. Ideal card height based on standard 1.30 aspect ratio
        var cardHeight = (cardWidth * 1.30f).toInt()

        // 3. Adaptive check: if vertical space is constrained (e.g., short phones or split screen),
        // scale down cardHeight & cardWidth proportionally to guarantee bottom buttons are never pushed off-screen
        val bottomPeekingOffset = (10 * density).toInt()
        val availableCardHeight = if (heightMode != MeasureSpec.UNSPECIFIED && heightSize > 0) {
            heightSize - cardMarginT - bottomPeekingOffset
        } else {
            cardHeight
        }

        if (availableCardHeight in 1 until cardHeight) {
            cardHeight = availableCardHeight
            cardWidth = (cardHeight / 1.30f).toInt()
        }

        val requiredHeight = cardMarginT + cardHeight + bottomPeekingOffset
        val measuredH = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> requiredHeight.coerceAtMost(heightSize)
            else -> requiredHeight
        }

        setMeasuredDimension(parentWidth, measuredH)

        val childWidthSpec = MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(cardHeight, MeasureSpec.EXACTLY)

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != View.GONE) {
                child.measure(childWidthSpec, childHeightSpec)
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val parentWidth = right - left
        val sampleChild = if (childCount > 0) getChildAt(0) else null
        val cardWidth = sampleChild?.measuredWidth ?: (parentWidth - (cardMarginH * 2)).coerceAtLeast(0)
        val cardHeight = sampleChild?.measuredHeight ?: (cardWidth * 1.30f).toInt()

        // Horizontally center card (essential on tablets and wide screens)
        val cardLeft = ((parentWidth - cardWidth) / 2).coerceAtLeast(cardMarginH)
        val cardTop = cardMarginT
        val cardRight = cardLeft + cardWidth
        val cardBottom = cardTop + cardHeight

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != View.GONE) {
                child.layout(cardLeft, cardTop, cardRight, cardBottom)
                child.pivotX = cardWidth / 2f
                child.pivotY = cardHeight.toFloat()
            }
        }
    }

    private fun applyStackVisualOrder() {
        val topRoot = getTopBinding().root
        val middleRoot = getMiddleBinding().root
        val bottomRoot = getBottomBinding().root

        // Z-order: bottom at 0, middle at 1, top at 2
        bottomRoot.bringToFront()
        middleRoot.bringToFront()
        topRoot.bringToFront()

        // 1st card: elevated slightly above
        topRoot.translationX = 0f
        topRoot.translationY = 0f
        topRoot.scaleX = 1f
        topRoot.scaleY = 1f
        topRoot.rotation = 0f
        topRoot.alpha = 1f
        topRoot.cardElevation = 8f * density
        topRoot.translationZ = 8f * density

        // Subsequent cards: perfectly aligned with each other as 1 bottom stack
        middleRoot.translationX = 0f
        middleRoot.translationY = stackTranslationY
        middleRoot.scaleX = stackScale
        middleRoot.scaleY = stackScale
        middleRoot.rotation = 0f
        middleRoot.alpha = 1f
        middleRoot.cardElevation = 4f * density
        middleRoot.translationZ = 4f * density

        bottomRoot.translationX = 0f
        bottomRoot.translationY = stackTranslationY
        bottomRoot.scaleX = stackScale
        bottomRoot.scaleY = stackScale
        bottomRoot.rotation = 0f
        bottomRoot.alpha = 1f
        bottomRoot.cardElevation = 2f * density
        bottomRoot.translationZ = 2f * density

        // Reset all swipe stickers
        resetStickerRotations(getTopBinding())
        resetStickerRotations(getMiddleBinding())
        resetStickerRotations(getBottomBinding())
        updateStickersAlpha(getTopBinding())
        updateStickersAlpha(getMiddleBinding())
        updateStickersAlpha(getBottomBinding())
    }

    private fun resetStickerRotations(binding: ItemMatchCardBinding) {
        binding.layoutStickerInterested.rotation = -14f
        binding.layoutStickerNotInterested.rotation = 14f
        binding.layoutStickerSuperLike.rotation = -6f
        binding.layoutStickerNope.rotation = -8f
    }

    private fun updateStickersAlpha(
        binding: ItemMatchCardBinding,
        interestedAlpha: Float = 0f,
        notInterestedAlpha: Float = 0f,
        superLikeAlpha: Float = 0f,
        nopeAlpha: Float = 0f
    ) {
        binding.layoutStickerInterested.alpha = interestedAlpha
        binding.layoutStickerNotInterested.alpha = notInterestedAlpha
        binding.layoutStickerSuperLike.alpha = superLikeAlpha
        binding.layoutStickerNope.alpha = nopeAlpha
    }

    private fun bindAllCards() {
        bindCard(getTopBinding(), profiles.getOrNull(currentIndex))
        bindCard(getMiddleBinding(), profiles.getOrNull(currentIndex + 1))
        bindCard(getBottomBinding(), profiles.getOrNull(currentIndex + 2))
    }

    private fun bindCard(binding: ItemMatchCardBinding, profile: MatchProfile?) {
        if (profile == null) {
            binding.root.visibility = View.GONE
            return
        }
        binding.root.visibility = View.VISIBLE

        // Load Photo using Fresco with Shimmer
        ShimmerHelper.loadWithShimmer(
            draweeView = binding.ivUserPhoto,
            url = profile.imageUrl,
            fallbackRes = profile.imageResId
        )

        // Top Badges
        if (profile.momentsCount > 0) {
            binding.badgeMoments.visibility = View.VISIBLE
            binding.tvMomentsCount.text = "${profile.momentsCount} Moments"
        } else {
            binding.badgeMoments.visibility = View.GONE
        }

        if (profile.matchPercentage > 0) {
            binding.badgeMatchPercentage.visibility = View.VISIBLE
            binding.tvMatchPercentage.text = "${profile.matchPercentage}% Match"
        } else {
            binding.badgeMatchPercentage.visibility = View.GONE
        }

        // Name, Age and Verification Checkmark
        binding.tvNameAge.text = "${profile.name}, ${profile.age}"
        binding.ivVerifiedBadge.visibility = if (profile.isVerified) View.VISIBLE else View.GONE

        // Location
        if (profile.location.isNotBlank()) {
            binding.tvLocation.visibility = View.VISIBLE
            binding.tvLocation.text = profile.location
        } else {
            binding.tvLocation.visibility = View.GONE
        }

        // Relationship Goal & Shared Interests
        if (!profile.relationshipGoal.isNullOrBlank()) {
            binding.badgeGoal.visibility = View.VISIBLE
            binding.tvRelationshipGoal.text = profile.relationshipGoal
        } else {
            binding.badgeGoal.visibility = View.GONE
        }

        if (profile.sharedInterestsCount > 0) {
            binding.badgeSharedInterests.visibility = View.VISIBLE
            binding.tvSharedInterests.text = "${profile.sharedInterestsCount} Shared Interests"
        } else {
            binding.badgeSharedInterests.visibility = View.GONE
        }

        val colorShared = Color.parseColor("#DE2D2D")
        val colorNormal = Color.parseColor("#333333")

        // Row 2 Interest Chips
        val interests = profile.interests
        if (interests.isNotEmpty()) {
            binding.chip1.visibility = View.VISIBLE
            binding.tvChipEmoji1.text = interests[0].emoji
            binding.tvChipText1.text = interests[0].label
            binding.tvChipText1.setTextColor(if (interests[0].isShared) colorShared else colorNormal)
        } else {
            binding.chip1.visibility = View.GONE
        }

        if (interests.size > 1) {
            binding.chip2.visibility = View.VISIBLE
            binding.tvChipEmoji2.text = interests[1].emoji
            binding.tvChipText2.text = interests[1].label
            binding.tvChipText2.setTextColor(if (interests[1].isShared) colorShared else colorNormal)
        } else {
            binding.chip2.visibility = View.GONE
        }

        if (interests.size > 2) {
            // Auto-hide chip3 on narrow widths (< 360dp) to prevent card edge clipping
            val screenWidthDp = resources.configuration.screenWidthDp
            if (screenWidthDp >= 360) {
                binding.chip3.visibility = View.VISIBLE
                binding.tvChipEmoji3.text = interests[2].emoji
                binding.tvChipText3.text = interests[2].label
                binding.tvChipText3.setTextColor(if (interests[2].isShared) colorShared else colorNormal)
            } else {
                binding.chip3.visibility = View.GONE
            }
        } else {
            binding.chip3.visibility = View.GONE
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!hasCards() || isAnimating) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                activePointerId = ev.getPointerId(0)
                isDragging = false
                velocityTracker?.clear()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = ev.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return false
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                if (hypot(dx, dy) > touchSlop) {
                    startDragging()
                    velocityTracker?.addMovement(ev)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!hasCards() || isAnimating) return false
        velocityTracker?.addMovement(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                activePointerId = ev.getPointerId(0)
                isDragging = false
                velocityTracker?.clear()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(ev)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                if (!isDragging && hypot(dx, dy) > touchSlop) {
                    startDragging()
                }
                if (isDragging) {
                    handleDrag(dx, dy)
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = ev.actionIndex
                val pointerId = ev.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newIndex = if (pointerIndex == 0) 1 else 0
                    activePointerId = ev.getPointerId(newIndex)
                    downX = ev.getRawX(newIndex) - (getTopBinding().root.translationX)
                    downY = ev.getRawY(newIndex) - (getTopBinding().root.translationY)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    finishDrag(dx, dy)
                } else {
                    getCurrentProfile()?.let { onCardClickListener?.invoke(it) }
                }
                isDragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    snapBack()
                }
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    private fun MotionEvent.getRawX(pointerIndex: Int): Float {
        return if (pointerIndex == 0) rawX else x + (rawX - x)
    }

    private fun MotionEvent.getRawY(pointerIndex: Int): Float {
        return if (pointerIndex == 0) rawY else y + (rawY - y)
    }

    private fun startDragging() {
        if (!isDragging) {
            isDragging = true
            parent?.requestDisallowInterceptTouchEvent(true)
            bringToFront()
            translationZ = 32f * density
            val topRoot = getTopBinding().root
            topRoot.bringToFront()
            topRoot.translationZ = 32f * density
        }
    }

    private fun handleDrag(dx: Float, dy: Float) {
        val topRoot = getTopBinding().root
        topRoot.translationX = dx
        topRoot.translationY = dy

        val screenW = width.toFloat().coerceAtLeast(1f)
        val screenH = height.toFloat().coerceAtLeast(1f)
        val maxRotation = 18f
        topRoot.rotation = (dx / screenW) * maxRotation

        val progress = (hypot(dx, dy) / (screenW * 0.40f)).coerceIn(0f, 1f)
        updateStackBackgroundProgress(progress)

        // Update swipe sticker alpha based on dominant direction
        val isHorizontal = abs(dx) >= abs(dy)
        val progressH = (abs(dx) / (screenW * 0.22f)).coerceIn(0f, 1f)
        val progressV = (abs(dy) / (screenH * 0.18f)).coerceIn(0f, 1f)
        val topBinding = getTopBinding()

        if (isHorizontal) {
            if (dx > 0) {
                // Right swipe -> Interested (Top-Left corner)
                updateStickersAlpha(topBinding, interestedAlpha = progressH)
                topBinding.layoutStickerInterested.rotation = -14f + (dx / screenW) * 4f
            } else {
                // Left swipe -> Not Interested (Top-Right corner)
                updateStickersAlpha(topBinding, notInterestedAlpha = progressH)
                topBinding.layoutStickerNotInterested.rotation = 14f + (dx / screenW) * 4f
            }
        } else {
            if (dy > 0) {
                // Down swipe -> Nope (Top-Center)
                updateStickersAlpha(topBinding, nopeAlpha = progressV)
                topBinding.layoutStickerNope.rotation = -8f + (dy / screenH) * 4f
            } else {
                // Up swipe -> Super Like (Center)
                updateStickersAlpha(topBinding, superLikeAlpha = progressV)
                topBinding.layoutStickerSuperLike.rotation = -6f + (dy / screenH) * 4f
            }
        }

        listener?.onCardDragging(dx, dy, progress)
    }

    private fun updateStackBackgroundProgress(progress: Float) {
        val middleRoot = getMiddleBinding().root
        val bottomRoot = getBottomBinding().root

        // Middle card expands to full scale and moves up
        middleRoot.scaleX = stackScale + ((1f - stackScale) * progress)
        middleRoot.scaleY = stackScale + ((1f - stackScale) * progress)
        middleRoot.translationY = stackTranslationY * (1f - progress)

        // Bottom card stays perfectly aligned as the 1 bottom stack
        bottomRoot.scaleX = stackScale
        bottomRoot.scaleY = stackScale
        bottomRoot.translationY = stackTranslationY
    }

    private fun finishDrag(dx: Float, dy: Float) {
        velocityTracker?.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
        val vx = velocityTracker?.xVelocity ?: 0f
        val vy = velocityTracker?.yVelocity ?: 0f

        val screenW = width.toFloat().coerceAtLeast(1f)
        val screenH = height.toFloat().coerceAtLeast(1f)

        val horizontalThreshold = screenW * 0.22f
        val verticalThreshold = screenH * 0.18f

        val hasFlingX = abs(vx) > minFlingVelocity
        val hasFlingY = abs(vy) > minFlingVelocity
        val isFarX = abs(dx) > horizontalThreshold
        val isFarY = abs(dy) > verticalThreshold

        val shouldSwipe = hasFlingX || hasFlingY || isFarX || isFarY

        if (shouldSwipe) {
            // Dominant direction classification for diagonal swipes
            val isHorizontal = if (hasFlingX || hasFlingY) {
                abs(vx) >= abs(vy)
            } else {
                abs(dx) >= abs(dy)
            }

            val direction = if (isHorizontal) {
                val effectiveX = if (hasFlingX && abs(vx) > abs(dx)) vx else dx
                if (effectiveX > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
            } else {
                val effectiveY = if (hasFlingY && abs(vy) > abs(dy)) vy else dy
                // In Android screen coordinates:
                // finger moving UP -> Y decreases -> effectiveY < 0 -> Super Like
                // finger moving DOWN -> Y increases -> effectiveY > 0 -> Nope
                if (effectiveY > 0) SwipeDirection.DOWN else SwipeDirection.UP
            }

            animateSwipeOut(direction)
        } else {
            snapBack()
        }
    }

    fun swipe(direction: SwipeDirection) {
        if (!hasCards() || isAnimating) return
        animateSwipeOut(direction)
    }

    private fun animateSwipeOut(direction: SwipeDirection) {
        isAnimating = true
        bringToFront()
        translationZ = 32f * density
        val topRoot = getTopBinding().root
        topRoot.bringToFront()
        topRoot.translationZ = 32f * density

        val screenW = width.toFloat().coerceAtLeast(1f)
        val screenH = height.toFloat().coerceAtLeast(1f)

        val targetX: Float
        val targetY: Float
        val targetRot: Float

        when (direction) {
            SwipeDirection.RIGHT -> {
                targetX = screenW * 1.5f
                targetY = topRoot.translationY
                targetRot = 25f
            }
            SwipeDirection.LEFT -> {
                targetX = -screenW * 1.5f
                targetY = topRoot.translationY
                targetRot = -25f
            }
            SwipeDirection.UP -> {
                targetX = topRoot.translationX
                targetY = -screenH * 1.5f
                targetRot = topRoot.rotation * 0.5f
            }
            SwipeDirection.DOWN -> {
                targetX = topRoot.translationX
                targetY = screenH * 1.5f
                targetRot = topRoot.rotation * 0.5f
            }
        }

        // Show active sticker on the swiping card
        val topBinding = getTopBinding()
        when (direction) {
            SwipeDirection.RIGHT -> {
                updateStickersAlpha(topBinding, interestedAlpha = 1f)
                topBinding.layoutStickerInterested.rotation = -14f
            }
            SwipeDirection.LEFT -> {
                updateStickersAlpha(topBinding, notInterestedAlpha = 1f)
                topBinding.layoutStickerNotInterested.rotation = 14f
            }
            SwipeDirection.UP -> {
                updateStickersAlpha(topBinding, superLikeAlpha = 1f)
                topBinding.layoutStickerSuperLike.rotation = -6f
            }
            SwipeDirection.DOWN -> {
                updateStickersAlpha(topBinding, nopeAlpha = 1f)
                topBinding.layoutStickerNope.rotation = -8f
            }
        }

        // Animate middle & bottom cards to next positions
        val middleRoot = getMiddleBinding().root
        val bottomRoot = getBottomBinding().root

        middleRoot.animate()
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(240)
            .start()

        bottomRoot.animate()
            .scaleX(stackScale)
            .scaleY(stackScale)
            .translationY(stackTranslationY)
            .setDuration(240)
            .start()

        topRoot.animate()
            .translationX(targetX)
            .translationY(targetY)
            .rotation(targetRot)
            .alpha(0f)
            .setDuration(240)
            .setInterpolator(AccelerateInterpolator(1.2f))
            .withEndAction {
                onSwipeAnimationFinished(direction)
            }
            .start()
    }

    private fun onSwipeAnimationFinished(direction: SwipeDirection) {
        val swipedProfile = getCurrentProfile()
        currentIndex++

        // Recycle views: top becomes bottom
        val recycledBindingIndex = topBindingIndex
        topBindingIndex = middleBindingIndex
        middleBindingIndex = bottomBindingIndex
        bottomBindingIndex = recycledBindingIndex

        // Bind new bottom card
        bindCard(getBottomBinding(), profiles.getOrNull(currentIndex + 2))

        // Reset visual ordering and transforms
        translationZ = 0f
        applyStackVisualOrder()
        isAnimating = false

        if (swipedProfile != null) {
            listener?.onCardSwiped(direction, swipedProfile)
        }

        val remaining = profiles.size - currentIndex
        if (remaining in 1..3) {
            listener?.onNearStackEnd()
        }

        if (currentIndex >= profiles.size) {
            listener?.onStackEmpty()
        }
    }

    private fun snapBack() {
        val topRoot = getTopBinding().root
        val middleRoot = getMiddleBinding().root
        val bottomRoot = getBottomBinding().root

        val topBinding = getTopBinding()
        topBinding.layoutStickerInterested.animate().alpha(0f).rotation(-14f).setDuration(200).start()
        topBinding.layoutStickerNotInterested.animate().alpha(0f).rotation(14f).setDuration(200).start()
        topBinding.layoutStickerSuperLike.animate().alpha(0f).rotation(-6f).setDuration(200).start()
        topBinding.layoutStickerNope.animate().alpha(0f).rotation(-8f).setDuration(200).start()

        topRoot.animate()
            .translationX(0f)
            .translationY(0f)
            .rotation(0f)
            .alpha(1f)
            .setDuration(220)
            .setInterpolator(OvershootInterpolator(1.15f))
            .withEndAction {
                translationZ = 0f
                applyStackVisualOrder()
            }
            .start()

        middleRoot.animate()
            .scaleX(stackScale)
            .scaleY(stackScale)
            .translationY(stackTranslationY)
            .setDuration(220)
            .start()

        bottomRoot.animate()
            .scaleX(stackScale)
            .scaleY(stackScale)
            .translationY(stackTranslationY)
            .setDuration(220)
            .start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        velocityTracker?.recycle()
        velocityTracker = null
    }
}
