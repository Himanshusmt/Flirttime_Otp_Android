package com.flirttime

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView

/**
 * Launcher activity for testing individual XML layouts.
 * Change the layout resource in setContentView() to test any screen.
 * Handles Edge-to-Edge dynamically so UI elements never overlap system bars.
 */
class TestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Enable Edge-to-Edge at Window level
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        // 👉 CHANGE THIS LAYOUT TO TEST ANY XML:
        val layoutRes = R.layout.activity_intro3
        setContentView(layoutRes)

        // 2. Apply dynamic system insets to prevent UI overlap while preserving full-bleed backgrounds
        applyDynamicEdgeToEdgeInsets(layoutRes)
    }

    private fun applyDynamicEdgeToEdgeInsets(layoutRes: Int) {
        val rootView = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) ?: return

        when (layoutRes) {
            R.layout.activity_intro1,
            R.layout.activity_intro2,
            R.layout.activity_intro3 -> {
                // Onboarding Intro Screens:
                // - Status bar & Nav bar: Dark icons on white background
                // - Top ivAppName gets status bar insets
                // - Bottom llBottom gets navigation bar insets so buttons float safely above system keys
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true

                val topLogo = rootView.findViewById<View>(R.id.ivAppName)
                val bottomContainer = rootView.findViewById<View>(R.id.llBottom)

                ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, windowInsets ->
                    val navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
                    val statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())

                    topLogo?.updatePadding(top = statusBars.top)
                    bottomContainer?.updatePadding(
                        left = navBars.left,
                        right = navBars.right,
                        bottom = navBars.bottom
                    )

                    windowInsets
                }
            }
            R.layout.activity_phone_input -> {

                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true

                val scrollView = rootView as? NestedScrollView ?: rootView.findViewById(R.id.scrollView)
                scrollView.clipToPadding = false

                ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, windowInsets ->
                    val navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
                    val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
                    val bottomInset = maxOf(navBars.bottom, ime.bottom)

                    // Pad the bottom of the scroll container dynamically so content (Privacy Policy, etc.)
                    // is never overlapped by the 3-button nav bar (< O |||) or soft keyboard.
                    scrollView?.updatePadding(bottom = bottomInset)

                    windowInsets
                }
            }

            R.layout.activity_login -> {
                // Login Screen:
                // - Status bar & Nav bar: Light icons on dark/gradient background.
                // - Fullscreen background photo/gradient bleeds 100% full screen.
                // - contentContainer receives top (status bar) and bottom (nav bar) dynamic padding.
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false

                val contentContainer = rootView.findViewById<View>(R.id.contentContainer)

                ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, windowInsets ->
                    val navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
                    val statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())

                    contentContainer?.updatePadding(
                        left = navBars.left,
                        top = statusBars.top,
                        right = navBars.right,
                        bottom = navBars.bottom
                    )

                    windowInsets
                }
            }

            else -> {
                // Fallback for any other test screen:
                ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, windowInsets ->
                    val navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
                    val statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
                    v.updatePadding(
                        left = navBars.left,
                        top = statusBars.top,
                        right = navBars.right,
                        bottom = navBars.bottom
                    )
                    windowInsets
                }
            }
        }

        ViewCompat.requestApplyInsets(rootView)
    }
}
