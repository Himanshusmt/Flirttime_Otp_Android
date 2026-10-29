package com.flirttime.feature.main.profile.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Custom circular progress ring displaying profile completion percentage
 * around the user's avatar.
 */
class ProfileProgressRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var progress: Int = 90
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    var strokeWidthPx: Float = 4.5f * resources.displayMetrics.density
        set(value) {
            field = value
            trackPaint.strokeWidth = value
            progressPaint.strokeWidth = value
            invalidate()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        color = Color.parseColor("#EAEAEA")
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#EA3355")
    }

    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val halfStroke = strokeWidthPx / 2f
        oval.set(
            halfStroke + paddingLeft,
            halfStroke + paddingTop,
            width - halfStroke - paddingRight,
            height - halfStroke - paddingBottom
        )

        // 1. Draw 360-degree background track
        canvas.drawArc(oval, 0f, 360f, false, trackPaint)

        // 2. Draw active progress arc starting at top (-90 degrees)
        if (progress > 0) {
            val sweepAngle = (progress / 100f) * 360f
            canvas.drawArc(oval, -90f, sweepAngle, false, progressPaint)
        }
    }
}
