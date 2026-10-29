package com.flirttime

import android.app.Application
import android.graphics.Bitmap
import com.facebook.cache.disk.DiskCacheConfig
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.core.ImagePipelineConfig
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
            .build()

        Fresco.initialize(this, pipelineConfig)
    }
}
