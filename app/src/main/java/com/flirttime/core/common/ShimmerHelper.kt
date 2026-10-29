package com.flirttime.core.common

import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.Build
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.controller.BaseControllerListener
import com.facebook.drawee.drawable.ScalingUtils
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.image.ImageInfo
import com.facebook.imagepipeline.postprocessors.IterativeBoxBlurPostProcessor
import com.facebook.imagepipeline.request.ImageRequest
import com.facebook.imagepipeline.request.ImageRequestBuilder
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
            .setDuration(1000L)
            .setDirection(Shimmer.Direction.LEFT_TO_RIGHT)
            .setAutoStart(true)
            .build()

        return ShimmerDrawable().apply {
            setShimmer(shimmer)
            startShimmer()
        }
    }

    /**
     * Pre-fetches an image to disk/memory cache in the background for instant display.
     */
    fun prefetchImage(url: String?) {
        val urls = MediaUrlHelper.getAlternativeUrls(url)
        val pipeline = Fresco.getImagePipeline()
        urls.forEach { resolved ->
            try {
                val request = ImageRequestBuilder.newBuilderWithSource(Uri.parse(resolved))
                    .setProgressiveRenderingEnabled(true)
                    .build()
                pipeline.prefetchToDiskCache(request, null)
            } catch (_: Exception) {}
        }
    }

    /**
     * Loads an image URL into a SimpleDraweeView with fast caching and smooth shimmer.
     * Uses Fresco's first-available-request mechanism so if an S3 thumbnail returns 403,
     * it automatically fails over to the full-size image without visual disruption.
     * Optionally applies a high-performance blur effect (matching iOS UIVisualEffectView).
     */
    fun loadWithShimmer(
        draweeView: SimpleDraweeView,
        url: String?,
        fallbackRes: Int? = null,
        scaleType: ScalingUtils.ScaleType = ScalingUtils.ScaleType.CENTER_CROP,
        isBlurred: Boolean = false
    ) {
        // Hardware blur on Android 12+ (matches iOS UIVisualEffectView)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isBlurred) {
                draweeView.setRenderEffect(RenderEffect.createBlurEffect(35f, 35f, Shader.TileMode.CLAMP))
            } else {
                draweeView.setRenderEffect(null)
            }
        }

        val altUrls = MediaUrlHelper.getAlternativeUrls(url)
        if (altUrls.isEmpty()) {
            draweeView.tag = null
            if (fallbackRes != null && fallbackRes != 0) {
                draweeView.setActualImageResource(fallbackRes)
            } else {
                draweeView.setImageURI(null as String?)
            }
            return
        }

        val primaryUrl = altUrls.first()
        val activeTag = draweeView.tag as? String
        val expectedTag = "$primaryUrl:blurred=$isBlurred"
        if (activeTag == expectedTag && draweeView.controller != null) {
            return
        }
        draweeView.tag = expectedTag

        val shimmerDrawable = createShimmerDrawable()
        draweeView.hierarchy.setPlaceholderImage(shimmerDrawable, scaleType)
        if (fallbackRes != null && fallbackRes != 0) {
            draweeView.hierarchy.setFailureImage(fallbackRes, scaleType)
        }

        val imageRequests = altUrls.map { resolved ->
            val builder = ImageRequestBuilder.newBuilderWithSource(Uri.parse(resolved))
                .setProgressiveRenderingEnabled(true)
                .setLowestPermittedRequestLevel(ImageRequest.RequestLevel.FULL_FETCH)
            if (isBlurred) {
                builder.postprocessor = IterativeBoxBlurPostProcessor(25, 3)
            }
            builder.build()
        }.toTypedArray()

        val controllerBuilder = Fresco.newDraweeControllerBuilder()
            .setOldController(draweeView.controller)
            .setAutoPlayAnimations(true)
            .setTapToRetryEnabled(true)
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

        if (imageRequests.size > 1) {
            controllerBuilder.setFirstAvailableImageRequests(imageRequests)
        } else {
            controllerBuilder.imageRequest = imageRequests[0]
        }

        draweeView.controller = controllerBuilder.build()
    }
}
