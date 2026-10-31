package com.flirttime.feature.main.explore

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import com.flirttime.R
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** iOS `ExploreTab.iconName` – the exact SF Symbols (`heart.fill`, `arrow.up.heart.fill`, `flame.fill`, `sparkles`). */
val ExploreTab.iconRes: Int
    get() = when (this) {
        ExploreTab.LIKES -> R.drawable.ic_sf_heart_fill
        ExploreTab.YOU_LIKED -> R.drawable.ic_sf_arrow_up_heart_fill
        ExploreTab.MATCHES -> R.drawable.ic_sf_flame_fill
        ExploreTab.COMPLIMENTS -> R.drawable.ic_sf_sparkles
    }

val ExploreTab.title: String
    get() = when (this) {
        ExploreTab.LIKES -> "Likes"
        ExploreTab.YOU_LIKED -> "You Liked"
        ExploreTab.MATCHES -> "Matches"
        ExploreTab.COMPLIMENTS -> "Compliments"
    }

val ExploreTab.emptyTitle: String
    get() = when (this) {
        ExploreTab.LIKES -> "No likes yet"
        ExploreTab.YOU_LIKED -> "You haven't liked anyone yet"
        ExploreTab.MATCHES -> "No matches yet"
        ExploreTab.COMPLIMENTS -> "No compliments yet"
    }

val ExploreTab.emptyMessage: String
    get() = when (this) {
        ExploreTab.LIKES -> "Keep your profile fresh. People who like you will show up here."
        ExploreTab.YOU_LIKED -> "Start swiping on Discover and the people you like will appear here."
        ExploreTab.MATCHES -> "When you and someone like each other, it's a match. They'll appear here."
        ExploreTab.COMPLIMENTS -> "Sweet words from your admirers will land here."
    }

/**
 * Android port of iOS `ExploreSegmentHeaderView`: four equal items (icon + title + count badge), a 1dp
 * track and a 3dp red indicator that is `title width + 18dp` wide and slides between tabs.
 */
class ExploreSegmentHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var onSelect: ((ExploreTab) -> Unit)? = null

    private val items = ExploreTab.entries.map { ExploreSegmentItemView(context).apply { bind(it) } }
    private val indicator = View(context)
    private var progress = 0f
    private var animator: ValueAnimator? = null

    private val indicatorHeight = dp(3f)
    private val indicatorExtraWidth = dp(18f)

    init {
        clipChildren = false
        clipToPadding = false

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        items.forEach { item ->
            row.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            item.setOnClickListener { onSelect?.invoke(item.tab) }
        }
        // iOS: items end 4dp above the track; the track sits 1dp above the bottom edge.
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            bottomMargin = dp(6f)
        })

        val track = View(context).apply { setBackgroundColor(Color.parseColor("#F2F3F5")) }
        addView(track, LayoutParams(LayoutParams.MATCH_PARENT, dp(1f)).apply {
            gravity = android.view.Gravity.BOTTOM
            bottomMargin = dp(1f)
        })

        indicator.setBackgroundResource(R.drawable.bg_explore_indicator)
        addView(indicator, LayoutParams(1, indicatorHeight).apply {
            gravity = android.view.Gravity.BOTTOM
        })
        applyProgress()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        layoutIndicator()
    }

    /** `progress` is the fractional page index (0 = first tab, 3 = last tab). */
    fun setProgress(value: Float) {
        progress = min(max(value, 0f), (items.size - 1).toFloat())
        applyProgress()
        layoutIndicator()
    }

    fun select(tab: ExploreTab, animated: Boolean) {
        animator?.cancel()
        val target = tab.ordinal.toFloat()
        if (!animated || !isLaidOut) {
            setProgress(target)
            return
        }
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 280
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { setProgress(it.animatedValue as Float) }
            start()
        }
    }

    fun setCount(tab: ExploreTab, count: Int) {
        items[tab.ordinal].setCount(count)
    }

    private fun applyProgress() {
        items.forEachIndexed { index, item ->
            item.setHighlight(max(0f, 1f - abs(progress - index)))
        }
    }

    private fun layoutIndicator() {
        if (items.isEmpty() || width <= 0) return
        val lower = floor(progress).toInt().coerceIn(0, items.size - 1)
        val upper = min(lower + 1, items.size - 1)
        val fraction = progress - lower

        val (fromLeft, fromWidth) = indicatorFrame(items[lower])
        val (toLeft, toWidth) = indicatorFrame(items[upper])

        val left = fromLeft + (toLeft - fromLeft) * fraction
        val w = fromWidth + (toWidth - fromWidth) * fraction
        val bottom = height
        indicator.layout(left.toInt(), bottom - indicatorHeight, (left + w).toInt(), bottom)
    }

    private fun indicatorFrame(item: ExploreSegmentItemView): Pair<Float, Float> {
        val itemLeft = (item.parent as View).left + item.left
        val itemWidth = item.width.toFloat()
        val width = min(item.titleWidth() + indicatorExtraWidth, itemWidth - dp(8f))
        return (itemLeft + itemWidth / 2f - width / 2f) to width
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
}

/** iOS `ExploreSegmentItemView`. */
class ExploreSegmentItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    lateinit var tab: ExploreTab
        private set

    private val contentStack: View
    private val icon: ImageView
    private val title: TextView
    private val badge: TextView

    private val activeIconColor = Color.parseColor("#D92B2B")
    private val activeTitleColor = Color.parseColor("#252525")
    private val inactiveColor = Color.parseColor("#8C8C8C")

    init {
        clipChildren = false
        clipToPadding = false
        isClickable = true
        isFocusable = true
        LayoutInflater.from(context).inflate(R.layout.view_explore_segment_item, this, true)
        contentStack = findViewById(R.id.contentStack)
        icon = findViewById(R.id.ivSegmentIcon)
        title = findViewById(R.id.tvSegmentTitle)
        badge = findViewById(R.id.tvSegmentBadge)
        // iOS `adjustsFontSizeToFit` with a 0.8 minimum scale.
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            title, 10, 13, 1, TypedValue.COMPLEX_UNIT_SP
        )
    }

    fun bind(tab: ExploreTab) {
        this.tab = tab
        icon.setImageResource(tab.iconRes)
        title.text = tab.title
        contentDescription = tab.title
        setHighlight(0f)
    }

    fun titleWidth(): Float = title.paint.measureText(title.text.toString())

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        contentStack.animate().alpha(if (pressed) 0.6f else 1f).setDuration(150).start()
    }

    fun setCount(count: Int) {
        badge.text = if (count > 99) "99+" else count.toString()
        badge.visibility = if (count > 0) View.VISIBLE else View.GONE
        stateDescription = if (count > 0) "$count" else null
        requestLayout()
    }

    /** `weight` is 1 for the fully selected tab and 0 for a fully unselected tab. */
    fun setHighlight(weight: Float) {
        val iconColor = ColorUtils.blendARGB(inactiveColor, activeIconColor, weight)
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(iconColor))
        title.setTextColor(ColorUtils.blendARGB(inactiveColor, activeTitleColor, weight))
        val scale = 1f + 0.12f * weight
        icon.scaleX = scale
        icon.scaleY = scale
        isSelected = weight >= 0.5f
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        positionBadge()
    }

    /** iOS: badge centred on the icon's trailing edge, 2pt below the icon top, never leaving the item. */
    private fun positionBadge() {
        if (badge.visibility != View.VISIBLE || badge.width == 0) return
        val rect = Rect(0, 0, icon.width, icon.height)
        offsetDescendantRectToMyCoords(icon, rect)
        var x = rect.right - badge.width / 2f
        x = min(x, width - 1f * resources.displayMetrics.density - badge.width)
        x = max(x, 0f)
        var y = rect.top + 2f * resources.displayMetrics.density - badge.height / 2f
        y = max(y, 0f)
        badge.translationX = x - badge.left
        badge.translationY = y - badge.top
    }
}
