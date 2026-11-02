package com.flirttime.feature.moments

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.facebook.drawee.drawable.ScalingUtils
import com.facebook.drawee.view.SimpleDraweeView
import com.flirttime.R
import com.flirttime.core.common.MediaUrlHelper

/** Full-screen swipeable photo viewer opened from a moment photo (iOS `MediaViewerController`). */
class MomentMediaViewerActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_URLS = "extra_urls"
        private const val EXTRA_START = "extra_start"

        fun start(context: Context, urls: List<String>, startIndex: Int) {
            if (urls.isEmpty()) return
            context.startActivity(
                Intent(context, MomentMediaViewerActivity::class.java)
                    .putStringArrayListExtra(EXTRA_URLS, ArrayList(urls))
                    .putExtra(EXTRA_START, startIndex)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        val urls = intent.getStringArrayListExtra(EXTRA_URLS).orEmpty()
        val start = intent.getIntExtra(EXTRA_START, 0).coerceIn(0, (urls.size - 1).coerceAtLeast(0))
        val density = resources.displayMetrics.density

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val pager = ViewPager2(this).apply {
            adapter = PhotoAdapter(urls)
            setCurrentItem(start, false)
        }
        root.addView(pager, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val close = ImageView(this).apply {
            setImageResource(R.drawable.ic_close_white)
            setPadding((10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
            contentDescription = "Close"
            setOnClickListener { finish() }
        }
        val closeSize = (44 * density).toInt()
        root.addView(close, FrameLayout.LayoutParams(closeSize, closeSize, Gravity.TOP or Gravity.START))

        val counter = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setBackgroundResource(R.drawable.bg_moment_media_counter_ios)
            setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
            visibility = if (urls.size > 1) android.view.View.VISIBLE else android.view.View.GONE
        }
        root.addView(counter, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ))

        fun updateCounter(position: Int) { counter.text = "${position + 1}/${urls.size}" }
        updateCounter(start)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateCounter(position)
        })

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()).top
            (close.layoutParams as FrameLayout.LayoutParams).topMargin = top + (4 * density).toInt()
            (counter.layoutParams as FrameLayout.LayoutParams).topMargin = top + (12 * density).toInt()
            close.requestLayout(); counter.requestLayout()
            insets
        }
        setContentView(root)
    }

    private class PhotoAdapter(private val urls: List<String>) : RecyclerView.Adapter<PhotoAdapter.Holder>() {
        class Holder(val view: SimpleDraweeView) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = SimpleDraweeView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                hierarchy.actualImageScaleType = ScalingUtils.ScaleType.FIT_CENTER
            }
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.view.setImageURI(MediaUrlHelper.resolveMediaUrl(urls[position]))
        }

        override fun getItemCount() = urls.size
    }
}
