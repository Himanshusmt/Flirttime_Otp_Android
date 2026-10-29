package com.flirttime.feature.profile.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.flirttime.BuildConfig
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.core.common.EdgeToEdgeHelper
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.ActivitySettingsBinding
import com.flirttime.databinding.DialogBlockedUsersBinding
import com.flirttime.databinding.DialogNotificationsSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModel()
    private val sessionManager: SessionManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = true, isLightNavigationBars = true)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        loadUserProfile()
        setupListeners()
        observeViewModel()
        displayAppVersion()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )

            view.updatePadding(
                top = statusBarInsets.top,
                bottom = navBarInsets.bottom
            )

            windowInsets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun loadUserProfile() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Avatar
                launch {
                    sessionManager.profileImageUriFlow.collect { uri ->
                        if (uri.isNotBlank()) {
                            binding.ivAvatar.setImageURI(uri)
                        } else {
                            binding.ivAvatar.setActualImageResource(R.drawable.img_login)
                        }
                    }
                }

                // Verified Badge
                launch {
                    sessionManager.isVerifiedFlow.collect { isVerified ->
                        binding.ivVerifiedBadge.isVisible = isVerified
                    }
                }

                // Username & Fallbacks
                launch {
                    sessionManager.usernameFlow.collect { username ->
                        val effectiveUsername = when {
                            username.isNotBlank() -> username
                            sessionManager.getFullName().isNotBlank() -> sessionManager.getFullName()
                            sessionManager.getFirstName().isNotBlank() -> sessionManager.getFirstName()
                            sessionManager.getNickName().isNotBlank() -> sessionManager.getNickName()
                            else -> "User"
                        }
                        binding.tvUsername.text = effectiveUsername
                    }
                }

                // Email
                launch {
                    sessionManager.emailFlow.collect { email ->
                        if (email.isNotBlank()) {
                            binding.tvEmail.text = email
                        } else {
                            val username = sessionManager.getUsername()
                            val fallbackEmail = if (username.isNotBlank()) {
                                "${username.lowercase().replace(" ", "")}@flirttime.in"
                            } else "user@flirttime.in"
                            binding.tvEmail.text = fallbackEmail
                        }
                    }
                }
            }
        }
    }

    private fun displayAppVersion() {
        binding.tvAppVersion.text = "Flirt Time v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Profile Overview Card / Edit Profile
        val openEditProfile = {
            AppNavigator.navigateTo(this, Destination.EditProfile)
        }
        binding.cardProfileOverview.setOnClickListener { openEditProfile() }
        binding.btnEditProfile.setOnClickListener { openEditProfile() }

        // Section: ACCOUNT
        binding.rowPersonalDetails.setOnClickListener {
            AppNavigator.navigateTo(this, Destination.PersonalDetails)
        }

        binding.rowNotifications.setOnClickListener {
            AppNavigator.navigateTo(this, Destination.NotificationSettings)
        }

        binding.rowBlockedReported.setOnClickListener {
            AppNavigator.navigateTo(this, Destination.BlockedReported)
        }

        // Section: SUPPORT
        binding.rowHelpSupport.setOnClickListener {
            openHelpSupport()
        }

        binding.rowShareApp.setOnClickListener {
            shareFlirtTime()
        }

        // Section: LEGAL
        binding.rowTerms.setOnClickListener {
            AppNavigator.navigateTo(
                context = this,
                destination = Destination.Webview(
                    title = "Terms & Conditions",
                    url = "https://flirttime.in/page/terms"
                )
            )
        }

        binding.rowPrivacyPolicy.setOnClickListener {
            AppNavigator.navigateTo(
                context = this,
                destination = Destination.Webview(
                    title = "Privacy Policy",
                    url = "https://flirttime.in/page/privacy-policy"
                )
            )
        }

        // Log out button
        binding.btnLogOut.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun openHelpSupport() {
        try {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:support@flirttime.in")
                putExtra(Intent.EXTRA_SUBJECT, "Flirt Time Support Request")
            }
            startActivity(emailIntent)
        } catch (_: Exception) {
            AppNavigator.navigateTo(
                context = this,
                destination = Destination.Webview(
                    title = "Help & Support",
                    url = "https://flirttime.in/help"
                )
            )
        }
    }

    private fun shareFlirtTime() {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TEXT,
                "I'm on Flirt Time, come find me! https://flirttime.in"
            )
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Flirt Time")
        startActivity(shareIntent)
    }

    private fun showNotificationsBottomSheet() {
        val bottomSheet = BottomSheetDialog(this)
        val sheetBinding = DialogNotificationsSettingsBinding.inflate(layoutInflater)
        bottomSheet.setContentView(sheetBinding.root)

        var isBinding = true
        val settings = viewModel.notificationSettingsState.value?.getOrNull()
        if (settings != null) {
            sheetBinding.switchPush.isChecked = settings.pushNotifications ?: true
            sheetBinding.switchNewMessages.isChecked = settings.newMessages ?: true
            sheetBinding.switchNewMatches.isChecked = settings.newMatches ?: true
            sheetBinding.switchLikeComments.isChecked = settings.likeComments ?: true
            sheetBinding.switchProfileVisits.isChecked = settings.profileVisits ?: true
            sheetBinding.switchDatingTips.isChecked = settings.datingTips ?: false
        } else {
            sheetBinding.switchPush.isChecked = true
            sheetBinding.switchNewMessages.isChecked = true
            sheetBinding.switchNewMatches.isChecked = true
            sheetBinding.switchLikeComments.isChecked = true
            sheetBinding.switchProfileVisits.isChecked = true
            sheetBinding.switchDatingTips.isChecked = false
        }
        isBinding = false

        sheetBinding.switchPush.setOnCheckedChangeListener { _, isChecked ->
            if (!isBinding) viewModel.togglePushNotifications(isChecked)
        }
        sheetBinding.switchNewMessages.setOnCheckedChangeListener { _, isChecked ->
            if (!isBinding) viewModel.toggleNewMessages(isChecked)
        }
        sheetBinding.switchNewMatches.setOnCheckedChangeListener { _, isChecked ->
            if (!isBinding) viewModel.toggleNewMatches(isChecked)
        }
        sheetBinding.switchLikeComments.setOnCheckedChangeListener { _, isChecked ->
            if (!isBinding) viewModel.toggleLikeComments(isChecked)
        }
        sheetBinding.switchProfileVisits.setOnCheckedChangeListener { _, isChecked ->
            if (!isBinding) viewModel.toggleProfileVisits(isChecked)
        }
        sheetBinding.switchDatingTips.setOnCheckedChangeListener { _, isChecked ->
            if (!isBinding) viewModel.toggleDatingTips(isChecked)
        }

        bottomSheet.show()
    }

    private fun showBlockedUsersBottomSheet() {
        val bottomSheet = BottomSheetDialog(this)
        val sheetBinding = DialogBlockedUsersBinding.inflate(layoutInflater)
        bottomSheet.setContentView(sheetBinding.root)

        val adapter = BlockedUsersAdapter { blockedUser ->
            MaterialAlertDialogBuilder(this)
                .setTitle("Unblock User")
                .setMessage("Are you sure you want to unblock ${blockedUser.name ?: blockedUser.username ?: "this user"}?")
                .setPositiveButton("Unblock") { _, _ ->
                    viewModel.unblockUser(blockedUser.userId)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        sheetBinding.rvBlockedUsers.layoutManager = LinearLayoutManager(this)
        sheetBinding.rvBlockedUsers.adapter = adapter

        viewModel.loadBlockedUsers()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.blockedUsersState.collectLatest { resource ->
                    when (resource) {
                        is Resource.Loading -> {
                            sheetBinding.pbBlockedLoading.isVisible = true
                            sheetBinding.tvEmptyBlocked.isVisible = false
                        }
                        is Resource.Success -> {
                            sheetBinding.pbBlockedLoading.isVisible = false
                            val list = resource.data.blockedUsers
                            adapter.submitList(list)
                            sheetBinding.tvEmptyBlocked.isVisible = list.isEmpty()
                            sheetBinding.rvBlockedUsers.isVisible = list.isNotEmpty()
                        }
                        is Resource.Error -> {
                            sheetBinding.pbBlockedLoading.isVisible = false
                            sheetBinding.tvEmptyBlocked.text = resource.message
                            sheetBinding.tvEmptyBlocked.isVisible = true
                        }
                        null -> Unit
                    }
                }
            }
        }

        bottomSheet.show()
    }

    private fun showLogoutConfirmationDialog() {
        com.flirttime.core.ui.dialog.CommonConfirmationDialog.show(
            context = this,
            title = "Are you sure you want to logout?",
            positiveText = "Yes",
            negativeText = "No"
        ) {
            viewModel.logout()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Logout state
                launch {
                    viewModel.logoutState.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                binding.layoutLoading.isVisible = true
                            }
                            is Resource.Success -> {
                                binding.layoutLoading.isVisible = false
                                viewModel.resetLogoutState()
                                Toast.makeText(this@SettingsActivity, "Logged out successfully", Toast.LENGTH_SHORT).show()
                                AppNavigator.navigateTo(
                                    context = this@SettingsActivity,
                                    destination = Destination.Login,
                                    clearBackStack = true,
                                    finishCurrent = true
                                )
                            }
                            is Resource.Error -> {
                                binding.layoutLoading.isVisible = false
                                Toast.makeText(this@SettingsActivity, resource.message, Toast.LENGTH_SHORT).show()
                            }
                            null -> Unit
                        }
                    }
                }

                // Unblock feedback
                launch {
                    viewModel.unblockState.collect { resource ->
                        when (resource) {
                            is Resource.Success -> {
                                Toast.makeText(this@SettingsActivity, "User unblocked successfully", Toast.LENGTH_SHORT).show()
                            }
                            is Resource.Error -> {
                                Toast.makeText(this@SettingsActivity, resource.message, Toast.LENGTH_SHORT).show()
                            }
                            else -> Unit
                        }
                    }
                }

                // Update settings failure toast
                launch {
                    viewModel.updateSettingsState.collect { resource ->
                        if (resource is Resource.Error) {
                            Toast.makeText(this@SettingsActivity, "Failed to update setting: ${resource.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}
