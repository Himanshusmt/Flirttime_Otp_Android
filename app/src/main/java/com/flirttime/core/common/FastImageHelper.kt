package com.flirttime.core.common

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.controller.BaseControllerListener
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.common.ResizeOptions
import com.facebook.imagepipeline.image.ImageInfo
import com.facebook.imagepipeline.request.ImageRequest
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.flirttime.R

/**
 * Moments / profile loader matching iOS `ImageLoader`:
 * memory then disk, thumbnail then full, no shimmer, no fade (unless asked),
 * skip S3 `/thumbnails/` after the first 403, and skip a reload of the same URL.
 */
object FastImageHelper {

    @Volatile
    var thumbnailsUnavailable: Boolean = false
        private set

    fun loadDirect(
        draweeView: SimpleDraweeView,
        url: String?,
        fallbackRes: Int = R.drawable.img_login,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
        thumbnailUrl: String? = null,
        fadeDurationMs: Int = 0,
        grayPlaceholder: Boolean = false
    ) {
        draweeView.hierarchy.fadeDuration = fadeDurationMs.coerceAtLeast(0)
        if (grayPlaceholder) {
            draweeView.hierarchy.setPlaceholderImage(ColorDrawable(Color.parseColor("#E8E8E8")))
        } else if (fallbackRes != 0) {
            draweeView.hierarchy.setPlaceholderImage(fallbackRes)
        }

        if (url.isNullOrBlank() && thumbnailUrl.isNullOrBlank()) {
            if (!grayPlaceholder) draweeView.setActualImageResource(fallbackRes)
            draweeView.setTag(R.id.fast_image_loaded_url, null)
            return
        }

        val fullResolved = resolvePrimary(url ?: thumbnailUrl)
        val thumbResolved = if (thumbnailsUnavailable) {
            null
        } else {
            resolvePrimary(thumbnailUrl)?.takeIf { it != fullResolved }
        }
        val cacheKey = listOfNotNull(fullResolved, thumbResolved, targetWidth, targetHeight).joinToString("|")
        if (draweeView.getTag(R.id.fast_image_loaded_url) == cacheKey) return
        draweeView.setTag(R.id.fast_image_loaded_url, cacheKey)

        val fullRequest = buildRequest(fullResolved, targetWidth, targetHeight)
        val lowResRequest = thumbResolved?.let { buildRequest(it, targetWidth, targetHeight) }

        val controllerBuilder = Fresco.newDraweeControllerBuilder()
            .setImageRequest(fullRequest)
            .setOldController(draweeView.controller)
            .setAutoPlayAnimations(true)
            .setControllerListener(object : BaseControllerListener<ImageInfo>() {
                override fun onFailure(id: String?, throwable: Throwable?) {
                    if (isThumbnailPath(fullResolved) || isThumbnailPath(thumbResolved)) {
                        if (isForbidden(throwable)) {
                            thumbnailsUnavailable = true
                            val fallback = stripThumbnail(fullResolved) ?: stripThumbnail(thumbResolved)
                            if (!fallback.isNullOrBlank() && fallback != fullResolved) {
                                draweeView.setTag(R.id.fast_image_loaded_url, null)
                                loadDirect(
                                    draweeView = draweeView,
                                    url = fallback,
                                    fallbackRes = fallbackRes,
                                    targetWidth = targetWidth,
                                    targetHeight = targetHeight,
                                    thumbnailUrl = null,
                                    fadeDurationMs = fadeDurationMs,
                                    grayPlaceholder = grayPlaceholder
                                )
                            }
                        }
                    }
                }
            })
        if (lowResRequest != null) {
            controllerBuilder.setLowResImageRequest(lowResRequest)
        }
        draweeView.controller = controllerBuilder.build()
    }

    private fun resolvePrimary(pathOrUrl: String?): String? {
        if (pathOrUrl.isNullOrBlank()) return null
        val trimmed = pathOrUrl.trim()
        if (trimmed.startsWith("content://") || trimmed.startsWith("file://") || trimmed.startsWith("android.resource://")) {
            return trimmed
        }
        val alternatives = MediaUrlHelper.getAlternativeUrls(trimmed)
        if (thumbnailsUnavailable) {
            return alternatives.firstOrNull { !it.contains("/thumbnails/") } ?: alternatives.firstOrNull()
        }
        return alternatives.firstOrNull()
    }

    private fun buildRequest(url: String?, targetWidth: Int, targetHeight: Int): ImageRequest? {
        if (url.isNullOrBlank()) return null
        val builder = ImageRequestBuilder.newBuilderWithSource(Uri.parse(url))
            .setLowestPermittedRequestLevel(ImageRequest.RequestLevel.FULL_FETCH)
            .setProgressiveRenderingEnabled(true)
        if (targetWidth > 0 && targetHeight > 0) {
            builder.resizeOptions = ResizeOptions(targetWidth, targetHeight)
        }
        return builder.build()
    }

    private fun isThumbnailPath(url: String?): Boolean {
        return url?.contains("/thumbnails/") == true
    }

    private fun stripThumbnail(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url.replace("/thumbnails/", "/")
    }

    private fun isForbidden(throwable: Throwable?): Boolean {
        var current = throwable
        while (current != null) {
            val message = current.message.orEmpty()
            if (message.contains("403") || message.contains("Forbidden", ignoreCase = true)) return true
            current = current.cause
        }
        return false
    }
}
