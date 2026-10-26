package com.flirttime

import android.app.Application
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

        com.facebook.drawee.backends.pipeline.Fresco.initialize(this)
    }
}
