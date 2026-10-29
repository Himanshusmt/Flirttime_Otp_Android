package com.flirttime.feature.main.profile.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Custom audio waveform view rendering vertical rounded amplitude bars
 * representing recorded voice notes.
 */
class AudioWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F4AAB6")
        style = Paint.Style.FILL
    }

    private val activeBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935")
        style = Paint.Style.FILL
    }

    private var progress: Float = 0f

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        invalidate()
    }

    // Relative amplitude levels matching audio visual pattern
    private val amplitudes = floatArrayOf(
        0.35f, 0.70f, 0.45f, 0.90f, 0.55f, 0.30f, 0.85f, 0.60f,
        0.40f, 0.75f, 0.95f, 0.50f, 0.35f, 0.80f, 0.65f, 0.45f,
        0.90f, 0.70f, 0.50f, 0.85f, 0.60f, 0.40f, 0.75f, 0.30f
    )

    private val rectF = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val availableWidth = width.toFloat() - paddingLeft - paddingRight
        val availableHeight = height.toFloat() - paddingTop - paddingBottom
        if (availableWidth <= 0 || availableHeight <= 0) return

        val count = amplitudes.size
        val barWidth = 3f * resources.displayMetrics.density
        val totalBarsWidth = count * barWidth
        val spacing = if (count > 1) (availableWidth - totalBarsWidth) / (count - 1) else 0f

        var startX = paddingLeft.toFloat()
        val centerY = paddingTop + availableHeight / 2f

        for (i in 0 until count) {
            val amp = amplitudes[i]
            val barHeight = (availableHeight * amp).coerceAtLeast(barWidth)
            val top = centerY - barHeight / 2f
            val bottom = centerY + barHeight / 2f
            val radius = barWidth / 2f

            rectF.set(startX, top, startX + barWidth, bottom)
            val currentPaint = if ((i.toFloat() / (count - 1).coerceAtLeast(1).toFloat()) <= progress) {
                activeBarPaint
            } else {
                barPaint
            }
            canvas.drawRoundRect(rectF, radius, radius, currentPaint)

            startX += barWidth + spacing
        }
    }
}
