package com.flirttime.feature.onboarding.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.ActivityPermissionBinding
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class PermissionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionBinding
    private val sessionManager: SessionManager by inject()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Proceed to Login regardless of allow/deny outcome matching legacy behavior
        navigateToLogin()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.flirttime.core.common.EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = true, isLightNavigationBars = true)
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupScreenSpecificEdgeToEdge()
        binding.tvproceed.setOnClickListener {
            requestAppPermissions()
        }
    }

    /**
     * Screen-specific edge-to-edge handling:
     * Applies status bar & display cutout insets to the top of the content container ([binding.main]),
     * ensuring [ivPermissionHeader] sits cleanly below camera cutouts and status bars.
     * Applies navigation bar insets + standard spacing to [llBottom]'s bottom padding so the proceed button
     * sits cleanly above the system navigation bar without cumulative XML padding.
     */
    private fun setupScreenSpecificEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val topInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val bottomInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )

            // Push top content safely below status bar and camera cutouts
            binding.main.updatePadding(
                top = topInsets.top,
                bottom = 0
            )

            // Push proceed button safely above system navigation bar with standard space
            val standardBottomSpace = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._15sdp)
            binding.llBottom.updatePadding(
                bottom = bottomInsets.bottom + standardBottomSpace
            )

            windowInsets
        }
    }

    private fun requestAppPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // 1. Camera (Matching legacy requirement)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        // 2. Location
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // 3. Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        lifecycleScope.launch {
            // Save intro completed state matching legacy logic
            sessionManager.setIntroComplete(true)

            AppNavigator.navigateTo(
                context = this@PermissionActivity,
                destination = Destination.Login,
                finishCurrent = true
            )
        }
    }
}
