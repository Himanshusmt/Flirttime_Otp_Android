package com.flirttime.core.ui.widget

import android.content.Context
import android.graphics.Outline
import android.net.Uri
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.facebook.drawee.view.SimpleDraweeView
import com.flirttime.R

/**
 * Reusable dynamic multi-image gallery view supporting 1 to 5 images.
 *
 * Implements strict layout rules:
 * - 1 Image: 100% width, 100% height
 * - 2 Images: 2 equal side-by-side columns (50% each)
 * - 3 Images: 1 large top image (~58% height), 2 equal bottom images (50% width each)
 * - 4 Images: Asymmetric: 1 large left image (~58% width, full height), 3 stacked right images (1/3 height each)
 * - 5 Images: Top row: 2 images (50% each, ~58% height); Bottom row: 3 images (1/3 each, ~42% height)
 *
 * Supports Replace & Remove action buttons in editable mode, with rounded corner clipping
 * and centerCrop image scaling.
 */
class MultiImageGalleryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    private val mediaItems = mutableListOf<Any>()

    var gapPx: Int = dpToPx(2.5f)
        set(value) {
            field = value
            requestLayout()
        }

    var cornerRadiusPx: Float = 0f
        set(value) {
            field = value
            invalidateOutline()
            invalidate()
        }

    var aspectRatio: Float = 1.08f
        set(value) {
            field = value
            requestLayout()
        }

    var isEditable: Boolean = true
        set(value) {
            field = value
            updateActionButtonsVisibility()
        }

    var onReplaceClickListener: ((index: Int, item: Any) -> Unit)? = null
    var onRemoveClickListener: ((index: Int, item: Any) -> Unit)? = null
    var onPhotoClickListener: ((index: Int, item: Any) -> Unit)? = null

    // Pre-allocated cell layouts
    private val childRects = Array(5) { IntArray(4) } // left, top, right, bottom

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (cornerRadiusPx > 0f) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                } else {
                    outline.setRect(0, 0, view.width, view.height)
                }
            }
        }
        clipToOutline = true
    }

    fun setImages(items: List<Any>) {
        mediaItems.clear()
        mediaItems.addAll(items.take(5))
        rebuildViews()
    }

    fun getImages(): List<Any> = mediaItems.toList()

    private fun rebuildViews() {
        removeAllViews()

        for (i in mediaItems.indices) {
            val item = mediaItems[i]
            val cellView = createCellView(i, item)
            addView(cellView)
        }
        updateActionButtonsVisibility()

        requestLayout()
        invalidate()
    }

    private fun createCellView(index: Int, item: Any): View {
        val container = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        // 1. Fresco DraweeView with CenterCrop
        val drawee = SimpleDraweeView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            hierarchy.actualImageScaleType = com.facebook.drawee.drawable.ScalingUtils.ScaleType.CENTER_CROP

            when (item) {
                is Uri -> setImageURI(item)
                is String -> setImageURI(Uri.parse(item))
                else -> setImageURI(Uri.parse(item.toString()))
            }

            setOnClickListener {
                onPhotoClickListener?.invoke(index, item)
            }
        }
        container.addView(drawee)

        // 2. Replace Button Pill (Top-Left of first photo)
        if (index == 0) {
            val btnReplace = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_pill_translucent_dark)
                val padH = dpToPx(10f)
                val padV = dpToPx(5f)
                setPadding(padH, padV, padH, padV)

                val lp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    val margin = dpToPx(12f)
                    setMargins(margin, margin, margin, margin)
                }
                layoutParams = lp

                val icon = ImageView(context).apply {
                    val size = dpToPx(14f)
                    layoutParams = LinearLayout.LayoutParams(size, size)
                    setImageResource(R.drawable.ic_replace_media)
                }
                addView(icon)

                val text = TextView(context).apply {
                    val textLp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = dpToPx(4f)
                    }
                    layoutParams = textLp
                    this.text = "Replace"
                    setTextColor(0xFFFFFFFF.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    includeFontPadding = false
                    try {
                        typeface = ResourcesCompat.getFont(context, R.font.fredokamedium)
                    } catch (_: Exception) {
                    }
                }
                addView(text)

                setOnClickListener {
                    onReplaceClickListener?.invoke(index, item)
                }
            }
            container.addView(btnReplace)
        }

        return container
    }

    private fun updateActionButtonsVisibility() {
        for (i in 0 until childCount) {
            val cell = getChildAt(i) as? ViewGroup ?: continue
            val replaceBtn = if (cell.childCount > 1) cell.getChildAt(1) else null
            val visibility = if (isEditable && i == 0) View.VISIBLE else View.GONE
            replaceBtn?.visibility = visibility
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val height = if (heightMode == MeasureSpec.EXACTLY && heightSize > 0) {
            heightSize
        } else {
            (width * aspectRatio).toInt()
        }

        setMeasuredDimension(width, height)

        val count = childCount.coerceAtMost(5)
        if (count == 0) return

        calculateLayout(width, height, count)

        for (i in 0 until count) {
            val rect = childRects[i]
            val childW = rect[2] - rect[0]
            val childH = rect[3] - rect[1]
            val child = getChildAt(i)
            child.measure(
                MeasureSpec.makeMeasureSpec(childW.coerceAtLeast(0), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(childH.coerceAtLeast(0), MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val count = childCount.coerceAtMost(5)
        for (i in 0 until count) {
            val rect = childRects[i]
            val child = getChildAt(i)
            child.layout(rect[0], rect[1], rect[2], rect[3])
        }
    }

    private fun calculateLayout(totalWidth: Int, totalHeight: Int, count: Int) {
        val w = totalWidth
        val h = totalHeight
        val g = gapPx

        when (count) {
            1 -> {
                // Rule 1: Full gallery width & height
                setRect(0, 0, 0, w, h)
            }
            2 -> {
                // Rule 2: Side by side, equal widths (50% each), full height
                val cellW = (w - g) / 2
                setRect(0, 0, 0, cellW, h)
                setRect(1, cellW + g, 0, w, h)
            }
            3 -> {
                // Rule 3: 1 large left image (50% width, full height), 2 stacked vertically on right (50% each)
                val leftW = (w - g) / 2
                setRect(0, 0, 0, leftW, h)

                val rightX = leftW + g
                val rh = (h - g) / 2
                setRect(1, rightX, 0, w, rh)
                setRect(2, rightX, rh + g, w, h)
            }
            4 -> {
                // Rule 4: 1 large left image (50% width, full height), 3 stacked vertically on right (1/3 each)
                val leftW = (w - g) / 2
                setRect(0, 0, 0, leftW, h)

                val rightX = leftW + g
                val rightAvailableH = h - (2 * g)
                val rh0 = rightAvailableH / 3
                val rh1 = rightAvailableH / 3

                setRect(1, rightX, 0, w, rh0)
                setRect(2, rightX, rh0 + g, w, rh0 + g + rh1)
                setRect(3, rightX, rh0 + g + rh1 + g, w, h)
            }
            5 -> {
                // Rule 5: Top row: 2 images side-by-side (50% each, ~58% height)
                // Bottom row: 3 images side-by-side (1/3 each, ~42% height)
                val availableH = h - g
                val topH = (availableH * 0.58f).toInt()

                val topCellW = (w - g) / 2
                setRect(0, 0, 0, topCellW, topH)
                setRect(1, topCellW + g, 0, w, topH)

                val bottomTop = topH + g
                val availableBW = w - (2 * g)
                val bw0 = availableBW / 3
                val bw1 = availableBW / 3

                setRect(2, 0, bottomTop, bw0, h)
                setRect(3, bw0 + g, bottomTop, bw0 + g + bw1, h)
                setRect(4, bw0 + g + bw1 + g, bottomTop, w, h)
            }
        }
    }

    private fun setRect(index: Int, left: Int, top: Int, right: Int, bottom: Int) {
        childRects[index][0] = left
        childRects[index][1] = top
        childRects[index][2] = right
        childRects[index][3] = bottom
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
