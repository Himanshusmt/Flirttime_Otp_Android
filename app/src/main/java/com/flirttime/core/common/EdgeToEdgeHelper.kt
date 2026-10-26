package com.flirttime.core.common

import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat

/**
 * Standard edge-to-edge initializer across all Activities.
 * Ensures status bars and navigation bars are completely transparent,
 * disables contrast enforcement (black scrims) on Android Q+,
 * and sets light/dark appearance for system icons against warm background (#FAF6F6).
 */
object EdgeToEdgeHelper {

    @Suppress("DEPRECATION")
    fun setupEdgeToEdge(
        activity: ComponentActivity,
        isLightStatusBars: Boolean = true,
        isLightNavigationBars: Boolean = true
    ) {
        val statusStyle = if (isLightStatusBars) {
            androidx.activity.SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        } else {
            androidx.activity.SystemBarStyle.dark(Color.TRANSPARENT)
        }
        val navStyle = if (isLightNavigationBars) {
            androidx.activity.SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        } else {
            androidx.activity.SystemBarStyle.dark(Color.TRANSPARENT)
        }
        activity.enableEdgeToEdge(
            statusBarStyle = statusStyle,
            navigationBarStyle = navStyle
        )
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        val window = activity.window
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = Color.TRANSPARENT
        }

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = isLightStatusBars
        insetsController.isAppearanceLightNavigationBars = isLightNavigationBars
    }
}
