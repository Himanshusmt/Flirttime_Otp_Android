package com.flirttime.feature.profile.presentation.process

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.ActivityVerificationProcessBinding
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class VerificationProcessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerificationProcessBinding
    private val sessionManager: SessionManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityVerificationProcessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Dark background -> White/Light system icons
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setupEdgeToEdge()
        setupListeners()
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )

            // Top status bar insets applied to content container
            val baseTopPadding = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._30sdp)
            binding.llContent.updatePadding(
                top = statusBarInsets.top + baseTopPadding
            )

            // Bottom navigation bar insets applied to bottom container
            val baseBottomPadding = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._20sdp)
            binding.bottomContainer.updatePadding(
                bottom = navBarInsets.bottom + baseBottomPadding
            )

            windowInsets
        }
    }

    private fun setupListeners() {
        binding.tvContinue.setOnClickListener {
            lifecycleScope.launch {
                sessionManager.setPageRedirect("home")
                sessionManager.setVerified(true)

                AppNavigator.navigateTo(
                    context = this@VerificationProcessActivity,
                    destination = Destination.Main(),
                    clearBackStack = true,
                    finishCurrent = true
                )
            }
        }
    }
}
