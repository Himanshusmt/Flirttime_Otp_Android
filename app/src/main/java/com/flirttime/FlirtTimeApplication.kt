package com.flirttime

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import com.facebook.cache.disk.DiskCacheConfig
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.cache.DefaultCacheKeyFactory
import com.facebook.imagepipeline.core.ImagePipelineConfig
import com.flirttime.core.common.AppForegroundTracker
import com.flirttime.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class FlirtTimeApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@FlirtTimeApplication)
            modules(appModule)
        }

        // High-performance Fresco pipeline configured for instant image display, fast downsampling, and low memory
        val diskCacheConfig = DiskCacheConfig.newBuilder(this)
            .setMaxCacheSize(100L * 1024 * 1024)
            .setMaxCacheSizeOnLowDiskSpace(20L * 1024 * 1024)
            .build()

        val pipelineConfig = ImagePipelineConfig.newBuilder(this)
            .setDownsampleMode(com.facebook.imagepipeline.core.DownsampleMode.ALWAYS)
            .setResizeAndRotateEnabledForNetwork(true)
            .setBitmapsConfig(Bitmap.Config.RGB_565)
            .setMainDiskCacheConfig(diskCacheConfig)
            .setCacheKeyFactory(PresignedPathCacheKeyFactory)
            .build()

        Fresco.initialize(this, pipelineConfig)

        // Chat notifications are suppressed while a screen is in front.
        AppForegroundTracker.register(this)
    }
}

/** Presigned S3 URLs rotate the query; cache by path so a new signature still hits disk. */
private object PresignedPathCacheKeyFactory : DefaultCacheKeyFactory() {
    override fun getCacheKeySourceUri(sourceUri: Uri): Uri {
        val raw = sourceUri.toString()
        if (raw.contains("X-Amz-Signature", ignoreCase = true) ||
            raw.contains("AWSAccessKeyId", ignoreCase = true)
        ) {
            return sourceUri.buildUpon().clearQuery().build()
        }
        return sourceUri
    }
}
