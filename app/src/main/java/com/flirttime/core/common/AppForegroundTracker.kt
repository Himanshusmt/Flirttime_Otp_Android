package com.flirttime.core.common

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Tracks whether any screen is in front. Chat notifications are only posted while the app
 * is in the background, so the user is never notified about a thread they are reading.
 */
object AppForegroundTracker : Application.ActivityLifecycleCallbacks {

    @Volatile
    private var startedActivities = 0

    val isForeground: Boolean get() = startedActivities > 0

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivities += 1
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
