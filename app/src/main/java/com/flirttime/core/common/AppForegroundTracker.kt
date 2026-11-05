package com.flirttime.core.common

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Tracks whether any screen is in front. Chat notifications are only posted while the app
 * is in the background, so the user is never notified about a thread they are reading.
 */
object AppForegroundTracker : Application.ActivityLifecycleCallbacks {

    @Volatile
    private var startedActivities = 0

    @Volatile
    private var currentActivityRef: WeakReference<Activity>? = null

    val isForeground: Boolean get() = startedActivities > 0

    val currentActivity: Activity? get() = currentActivityRef?.get()

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivities += 1
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivityRef?.get() === activity) {
            currentActivityRef = null
        }
    }
}
