package com.flirttime.feature.splash

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.ActivitySplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

import android.graphics.Color
import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.core.view.WindowCompat
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import org.koin.androidx.viewmodel.ext.android.viewModel

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val viewModel: SplashViewModel by viewModel()

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

            // Concurrently start auth verification and minimum animation delay
            val minDelayDeferred = async {
                delay(1400.milliseconds)
            }

            viewModel.determineStartDestination()

            val targetDestination = viewModel.navigationEvent.first()

            // Wait until minimum animation time has elapsed
            minDelayDeferred.await()

            AppNavigator.navigateTo(
                context = this@SplashActivity,
                destination = targetDestination,
                clearBackStack = true,
                finishCurrent = true
            )
        }
    }
}
