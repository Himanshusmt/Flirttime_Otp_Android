package com.flirttime.feature.splash.presentation

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.ActivitySplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import kotlin.time.Duration.Companion.milliseconds

import android.graphics.Color
import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.core.view.WindowCompat

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val sessionManager: SessionManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSplashWindow()
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startSplashSequence()
    }

    /**
     * Isolated window styling strictly for SplashActivity:
     * - Disables AndroidX default light scrims via explicit transparent SystemBarStyle
     * - Disables contrast enforcement on Android Q+
     * - Ensures navigation bar and status bar are 100% transparent with white system icons
     *   so the splash background is completely uniform across the entire display.
     */
    @Suppress("DEPRECATION")
    private fun setupSplashWindow() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false
    }

    private fun startSplashSequence() {
        lifecycleScope.launch {
            // Phase 2: Transition directly to animated Lottie (Phase 1 was app logo on system splash)
            binding.imgSplashLogo.visibility = View.GONE
            binding.animationView.visibility = View.VISIBLE
            binding.animationView.playAnimation()

            // Allow animation to play for 1400ms
            delay(1400.milliseconds)

            // 4. Determine destination and route
            val token = sessionManager.getToken()
            val isIntroCompleted = sessionManager.isIntroComplete()

            val targetDestination = when {
                token.isNotBlank() -> {
                    val pageRedirect = sessionManager.getPageRedirect()
                    when (pageRedirect) {
                        "home" -> Destination.Main(targetTab = 2)
                        "gesture" -> Destination.VerificationProcess
                        "avatar" -> Destination.GestureVerification
                        "user_info" -> Destination.ProfileVerification
                        else -> Destination.IntroduceYourself
                    }
                }
                isIntroCompleted -> {
                    Destination.Login
                }
                else -> {
                    Destination.Intro1
                }
            }

            AppNavigator.navigateTo(
                context = this@SplashActivity,
                destination = targetDestination,
                clearBackStack = true,
                finishCurrent = true
            )
        }
    }
}
