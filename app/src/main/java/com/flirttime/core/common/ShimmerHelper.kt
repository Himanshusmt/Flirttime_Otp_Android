package com.flirttime.core.common

import android.graphics.Color
import android.graphics.drawable.Animatable
import android.net.Uri
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.controller.BaseControllerListener
import com.facebook.drawee.drawable.ScalingUtils
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.image.ImageInfo
import com.facebook.shimmer.Shimmer
import com.facebook.shimmer.ShimmerDrawable

object ShimmerHelper {

    /**
     * Creates a configured ShimmerDrawable with smooth, subtle tones.
     */
    fun createShimmerDrawable(): ShimmerDrawable {
        val shimmer = Shimmer.ColorHighlightBuilder()
            .setBaseColor(Color.parseColor("#EAEAEA"))
            .setHighlightColor(Color.parseColor("#F8F8F8"))
            .setDuration(1200L)
            .setDirection(Shimmer.Direction.LEFT_TO_RIGHT)
            .setAutoStart(true)
            .build()

        return ShimmerDrawable().apply {
            setShimmer(shimmer)
            startShimmer()
        }
    }

    /**
     * Loads an image URL into a SimpleDraweeView with an active Shimmer placeholder.
     */
    fun loadWithShimmer(
        draweeView: SimpleDraweeView,
        url: String?,
        fallbackRes: Int? = null,
        scaleType: ScalingUtils.ScaleType = ScalingUtils.ScaleType.CENTER_CROP
    ) {
        val resolvedUrl = MediaUrlHelper.resolveMediaUrl(url)
        if (resolvedUrl.isNullOrBlank()) {
            draweeView.tag = null
            if (fallbackRes != null && fallbackRes != 0) {
                draweeView.setActualImageResource(fallbackRes)
            } else {
                draweeView.setImageURI(null as String?)
            }
            return
        }

        // Avoid re-triggering shimmer if this exact URL is already loaded and active on this view!
        val activeUrl = draweeView.tag as? String
        if (activeUrl == resolvedUrl && draweeView.controller != null) {
            return
        }
        draweeView.tag = resolvedUrl

        val shimmerDrawable = createShimmerDrawable()
        draweeView.hierarchy.setPlaceholderImage(shimmerDrawable, scaleType)

        val controller = Fresco.newDraweeControllerBuilder()
            .setUri(Uri.parse(resolvedUrl))
            .setOldController(draweeView.controller)
            .setControllerListener(object : BaseControllerListener<ImageInfo>() {
                override fun onFinalImageSet(id: String?, imageInfo: ImageInfo?, animatable: Animatable?) {
                    shimmerDrawable.stopShimmer()
                }

                override fun onFailure(id: String?, throwable: Throwable?) {
                    shimmerDrawable.stopShimmer()
                    if (fallbackRes != null && fallbackRes != 0) {
                        draweeView.setActualImageResource(fallbackRes)
                    }
                }
            })
            .build()

        draweeView.controller = controller
    }
}
