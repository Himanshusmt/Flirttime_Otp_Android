package com.flirttime.feature.notification

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.flirttime.R
import kotlinx.coroutines.launch

object NotificationBadgeBinder {

    private const val BADGE_TAG = "notification_count_badge"

    fun bind(anchor: View, lifecycleOwner: LifecycleOwner, store: NotificationBadgeStore) {
        store.setup()
        val badge = ensureBadge(anchor)
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                store.unreadCount.collect { count ->
                    if (count <= 0) {
                        badge.visibility = View.GONE
                    } else {
                        badge.visibility = View.VISIBLE
                        badge.text = if (count > 9) "9+" else count.toString()
                    }
                }
            }
        }
    }

    private fun ensureBadge(anchor: View): TextView {
        val existingHost = (anchor as? ViewGroup)?.findViewWithTag<TextView>(BADGE_TAG)
            ?: (anchor.parent as? ViewGroup)?.findViewWithTag<TextView>(BADGE_TAG)
        if (existingHost != null) return existingHost

        val host: FrameLayout
        if (anchor is FrameLayout) {
            host = anchor
        } else {
            val parent = anchor.parent as? ViewGroup ?: return TextView(anchor.context)
            val index = parent.indexOfChild(anchor)
            val originalLp = anchor.layoutParams
            parent.removeView(anchor)
            host = FrameLayout(anchor.context).apply { layoutParams = originalLp }
            anchor.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            host.addView(anchor)
            parent.addView(host, index)
        }

        val density = anchor.resources.displayMetrics.density
        val badge = TextView(anchor.context).apply {
            tag = BADGE_TAG
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = ResourcesCompat.getFont(anchor.context, R.font.fredokasemibold)
            gravity = Gravity.CENTER
            minWidth = (16 * density).toInt()
            minHeight = (16 * density).toInt()
            setPadding((4 * density).toInt(), 0, (4 * density).toInt(), 0)
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(Color.parseColor("#D92B2B"))
            }
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = (-2 * density).toInt()
                marginEnd = (-2 * density).toInt()
            }
        }
        host.addView(badge)
        return badge
    }
}
