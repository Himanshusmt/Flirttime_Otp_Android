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
import com.facebook.imagepipeline.common.ResizeOptions
import com.facebook.imagepipeline.image.ImageInfo
import com.facebook.imagepipeline.postprocessors.IterativeBoxBlurPostProcessor
import com.facebook.imagepipeline.request.ImageRequest
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.facebook.shimmer.Shimmer
import com.facebook.shimmer.ShimmerDrawable

object ShimmerHelper {

    // Target dimensions for explore grid cards (360x500 px downsample for ultra-fast decode)
    private val cardResizeOptions = ResizeOptions.forSquareSize(500)

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
     * Pre-fetches an image to BOTH disk and bitmap memory cache ahead of time,
     * ensuring 0ms instantaneous rendering when cards scroll into view.
     */
    fun prefetchImage(url: String?) {
        val urls = MediaUrlHelper.getAlternativeUrls(url)
        val pipeline = Fresco.getImagePipeline()
        urls.forEach { resolved ->
            try {
                val uri = Uri.parse(resolved)
                val request = ImageRequestBuilder.newBuilderWithSource(uri)
                    .setResizeOptions(cardResizeOptions)
                    .setProgressiveRenderingEnabled(true)
                    .build()
                pipeline.prefetchToDiskCache(request, null)
                pipeline.prefetchToBitmapCache(request, null)
            } catch (_: Exception) {}
        }
    }

    /**
     * Loads an image URL into a SimpleDraweeView with maximum performance:
     * - Uses downsampled ResizeOptions to eliminate decoding lag.
     * - Checks in-memory bitmap cache to skip placeholder flicker if already available.
     * - Uses Fresco's first-available-request failover (full-size first, thumbnail fallback).
     * - Applies hardware-accelerated blur on Android 12+ (matching iOS UIVisualEffectView).
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

        // Check if already in bitmap memory cache
        val primaryUri = Uri.parse(primaryUrl)
        val isInMemory = Fresco.getImagePipeline().isInBitmapMemoryCache(primaryUri)

        val shimmerDrawable = if (!isInMemory) createShimmerDrawable() else null
        if (shimmerDrawable != null) {
            draweeView.hierarchy.setPlaceholderImage(shimmerDrawable, scaleType)
        }
        if (fallbackRes != null && fallbackRes != 0) {
            draweeView.hierarchy.setFailureImage(fallbackRes, scaleType)
        }

        val imageRequests = altUrls.map { resolved ->
            val builder = ImageRequestBuilder.newBuilderWithSource(Uri.parse(resolved))
                .setResizeOptions(cardResizeOptions)
                .setProgressiveRenderingEnabled(true)
                .setLocalThumbnailPreviewsEnabled(true)
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
                    shimmerDrawable?.stopShimmer()
                }

                override fun onFailure(id: String?, throwable: Throwable?) {
                    shimmerDrawable?.stopShimmer()
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
