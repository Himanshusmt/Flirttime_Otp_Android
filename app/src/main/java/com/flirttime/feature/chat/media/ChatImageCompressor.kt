package com.flirttime.feature.chat.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

data class CompressedImage(
    val file: File,
    val width: Int,
    val height: Int
)

/**
 * Chat photos are normalised the way the iOS client does it: longest edge <= 1600px,
 * JPEG quality 80. Nothing is cropped — chat bubbles keep the original framing.
 */
object ChatImageCompressor {

    private const val MAX_DIMENSION = 1600
    private const val JPEG_QUALITY = 80

    fun compress(context: Context, uri: Uri, targetFile: File): CompressedImage? {
        return try {
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val sourceWidth = bounds.outWidth
            val sourceHeight = bounds.outHeight
            if (sourceWidth <= 0 || sourceHeight <= 0) return null

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(sourceWidth, sourceHeight, MAX_DIMENSION)
            }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            val scaled = scaleDown(decoded, MAX_DIMENSION)
            if (scaled !== decoded) decoded.recycle()

            FileOutputStream(targetFile).use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }

            CompressedImage(
                file = targetFile,
                width = scaled.width,
                height = scaled.height
            ).also { scaled.recycle() }
        } catch (e: Exception) {
            null
        }
    }

    private fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= maxDimension) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleDown(source: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxDimension) return source

        val ratio = maxDimension.toFloat() / longest
        val targetWidth = (source.width * ratio).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }
}
