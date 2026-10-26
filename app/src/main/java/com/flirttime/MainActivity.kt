package com.flirttime

import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.ImageViewCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.activity.enableEdgeToEdge
import androidx.navigation.navOptions
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var lastStatusBarTop: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.flirttime.core.common.EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = true, isLightNavigationBars = true)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Centralized WindowInsets: Status bar to navHostFragment (edge-to-edge aware), Navigation bar to bottom nav bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )

            lastStatusBarTop = statusBarInsets.top
            applyStatusBarInsetsForDestination(navController.currentDestination?.id ?: R.id.navigation_matches)
            binding.relativeBottom.updatePadding(bottom = navBarInsets.bottom)

            windowInsets
        }

        setupNavigation()
        handleIncomingIntent()
        val initialDest = navController.currentDestination?.id ?: R.id.navigation_matches
        updateBottomBarUi(initialDest)
        applyStatusBarInsetsForDestination(initialDest)
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.linearMoments.setOnClickListener {
            selectTab(R.id.navigation_moments)
        }

        binding.linearExplore.setOnClickListener {
            selectTab(R.id.navigation_explore)
        }

        binding.card.setOnClickListener {
            selectTab(R.id.navigation_matches)
        }

        binding.imgMidNormal.setOnClickListener {
            selectTab(R.id.navigation_matches)
        }

        binding.linearMessage.setOnClickListener {
            selectTab(R.id.navigation_chat)
        }

        binding.linearProfile.setOnClickListener {
            selectTab(R.id.navigation_profile)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateBottomBarUi(destination.id)
            applyStatusBarInsetsForDestination(destination.id)
        }
    }

    private fun applyStatusBarInsetsForDestination(destinationId: Int) {
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (destinationId == R.id.navigation_profile) {
            // Profile tab: Cover photo extends edge-to-edge behind the status bar
            binding.navHostFragment.updatePadding(top = 0)
            insetsController.isAppearanceLightStatusBars = false // White status bar text/icons over cover photo
        } else {
            // Standard tabs: Start safely below status bar
            binding.navHostFragment.updatePadding(top = lastStatusBarTop)
            insetsController.isAppearanceLightStatusBars = true // Dark status bar text/icons over light background
        }
    }

    private fun selectTab(destinationId: Int) {
        if (navController.currentDestination?.id == destinationId) return

        val navOptions = navOptions {
            launchSingleTop = true
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            restoreState = true
        }
        navController.navigate(destinationId, null, navOptions)
    }

    private fun updateBottomBarUi(destinationId: Int) {
        val brandRed = getColor(R.color.brand_red)

        // Moments
        val isMoments = destinationId == R.id.navigation_moments
        binding.imgMoment.setImageResource(if (isMoments) R.drawable.img_moment_select else R.drawable.img_moment_unselect)
        binding.txtMoment.setTextColor(brandRed)
        binding.linearMoments.alpha = if (isMoments) 1.0f else 0.85f

        // Explore
        val isExplore = destinationId == R.id.navigation_explore
        binding.imgExplore.setImageResource(if (isExplore) R.drawable.img_explore_select else R.drawable.img_explore_unselect)
        binding.txtExplore.setTextColor(brandRed)
        binding.linearExplore.alpha = if (isExplore) 1.0f else 0.85f

        // Center Matches Button
        val isMatches = destinationId == R.id.navigation_matches
        binding.card.scaleX = 1.0f
        binding.card.scaleY = 1.0f

        if (isMatches) {
            // Selected: Brand red card background with white logo & red glow shadow
            binding.card.setBackgroundResource(R.drawable.bg_floating_button_selected)
            ImageViewCompat.setImageTintList(
                binding.imgMidNormal,
                ColorStateList.valueOf(getColor(R.color.white))
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                binding.card.outlineAmbientShadowColor = getColor(R.color.shadow_connect_ambient)
                binding.card.outlineSpotShadowColor = getColor(R.color.shadow_connect_red)
            }
        } else {
            // Unselected: Clean pure white background with red logo & soft neutral shadow
            binding.card.setBackgroundResource(R.drawable.bg_floating_button_unselected)
            ImageViewCompat.setImageTintList(
                binding.imgMidNormal,
                ColorStateList.valueOf(brandRed)
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                binding.card.outlineAmbientShadowColor = getColor(R.color.shadow_soft_black)
                binding.card.outlineSpotShadowColor = getColor(R.color.shadow_soft_black)
            }
        }

        // Messages
        val isChat = destinationId == R.id.navigation_chat
        binding.imgMessage.setImageResource(if (isChat) R.drawable.img_message_select else R.drawable.img_message_unselect)
        binding.txtMessage.setTextColor(brandRed)
        binding.linearMessage.alpha = if (isChat) 1.0f else 0.85f

        // Profile
        val isProfile = destinationId == R.id.navigation_profile
        binding.imgProfile.setImageResource(if (isProfile) R.drawable.img_profile_select else R.drawable.img_profile_unselect_)
        binding.txtProfile.setTextColor(brandRed)
        binding.linearProfile.alpha = if (isProfile) 1.0f else 0.85f
    }

    private fun handleIncomingIntent() {
        if (intent.hasExtra(AppNavigator.EXTRA_TARGET_TAB)) {
            val targetTab = intent.getIntExtra(AppNavigator.EXTRA_TARGET_TAB, 2)
            when (targetTab) {
                0 -> selectTab(R.id.navigation_moments)
                1 -> selectTab(R.id.navigation_explore)
                2 -> selectTab(R.id.navigation_matches)
                3 -> selectTab(R.id.navigation_chat)
                4 -> selectTab(R.id.navigation_profile)
                else -> selectTab(R.id.navigation_matches)
            }
        }
    }
}