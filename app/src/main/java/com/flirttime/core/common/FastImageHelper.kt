package com.flirttime.core.common

import android.net.Uri
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.common.ResizeOptions
import com.facebook.imagepipeline.request.ImageRequest
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.flirttime.R

/**
 * High-performance, direct Fresco image loader without shimmer animations or artificial delay,
 * matching iOS's instantaneous image rendering and aggressive disk/memory cache utilization.
 */
object FastImageHelper {

    fun loadDirect(
        draweeView: SimpleDraweeView,
        url: String?,
        fallbackRes: Int = R.drawable.img_login,
        targetWidth: Int = 0,
        targetHeight: Int = 0
    ) {
        if (url.isNullOrBlank()) {
            draweeView.setActualImageResource(fallbackRes)
            return
        }

        val cleanUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "${MediaUrlHelper.S3_MEDIA_BASE_URL}${url.removePrefix("/")}"
        }

        val uri = Uri.parse(cleanUrl)
        val requestBuilder = ImageRequestBuilder.newBuilderWithSource(uri)
            .setLowestPermittedRequestLevel(ImageRequest.RequestLevel.FULL_FETCH)
            .setProgressiveRenderingEnabled(true)

        if (targetWidth > 0 && targetHeight > 0) {
            requestBuilder.resizeOptions = ResizeOptions(targetWidth, targetHeight)
        }

        val controller = Fresco.newDraweeControllerBuilder()
            .setImageRequest(requestBuilder.build())
            .setOldController(draweeView.controller)
            .setAutoPlayAnimations(true)
            .build()

        draweeView.controller = controller
    }
}

