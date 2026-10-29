package com.flirttime.feature.profile.settings

import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.core.common.EdgeToEdgeHelper
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.ActivityPersonalDetailsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.SimpleDateFormat
import java.util.Locale

class PersonalDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonalDetailsBinding
    private val viewModel: SettingsViewModel by viewModel()
    private val sessionManager: SessionManager by inject()

    private var pendingEmail: String? = null
    private var pendingPhone: String? = null
    private var activeOtpDialog: androidx.appcompat.app.AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = true, isLightNavigationBars = true)
        binding = ActivityPersonalDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        loadUserProfile()
        setupListeners()
        observeViewModel()
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
                // Full name
                launch {
                    sessionManager.fullNameFlow.collect { fullName ->
                        val effectiveName = when {
                            fullName.isNotBlank() -> fullName
                            sessionManager.getFirstName().isNotBlank() -> sessionManager.getFirstName()
                            else -> "Not set"
                        }
                        binding.tvFullName.text = effectiveName
                    }
                }

                // Username
                launch {
                    sessionManager.usernameFlow.collect { username ->
                        val effectiveUsername = when {
                            username.isNotBlank() -> username
                            sessionManager.getNickName().isNotBlank() -> sessionManager.getNickName()
                            else -> "Not set"
                        }
                        binding.tvUsername.text = effectiveUsername
                    }
                }

                // Email (Add once only - once set, hide pencil)
                launch {
                    sessionManager.emailFlow.collect { email ->
                        if (email.isNotBlank()) {
                            binding.tvEmail.text = email
                            binding.btnEditEmail.isVisible = false
                            binding.rowEmail.isClickable = false
                        } else {
                            binding.tvEmail.text = "Not added"
                            binding.btnEditEmail.isVisible = true
                            binding.rowEmail.isClickable = true
                        }
                    }
                }

                // Phone number (Add once only - once set, hide pencil)
                launch {
                    sessionManager.phoneFlow.collect { phone ->
                        if (phone.isNotBlank()) {
                            binding.tvPhoneNumber.text = phone
                            binding.btnEditPhone.isVisible = false
                            binding.rowPhoneNumber.isClickable = false
                        } else {
                            binding.tvPhoneNumber.text = "Not added"
                            binding.btnEditPhone.isVisible = true
                            binding.rowPhoneNumber.isClickable = true
                        }
                    }
                }

                // Date of birth
                launch {
                    sessionManager.dobFlow.collect { dob ->
                        binding.tvDob.text = formatDisplayDate(dob)
                    }
                }

                // Location
                launch {
                    sessionManager.locationFlow.collect { location ->
                        binding.tvLocation.text = if (location.isNotBlank()) location else "Not added"
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Edit Full Name
        val openFullNameEditor = { showEditFullNameDialog() }
        binding.rowFullName.setOnClickListener { openFullNameEditor() }
        binding.btnEditFullName.setOnClickListener { openFullNameEditor() }

        // Edit Username
        val openUsernameEditor = { showEditUsernameDialog() }
        binding.rowUsername.setOnClickListener { openUsernameEditor() }
        binding.btnEditUsername.setOnClickListener { openUsernameEditor() }

        // Add Email
        val openEmailAdder = {
            if (binding.btnEditEmail.isVisible) showAddEmailDialog()
        }
        binding.rowEmail.setOnClickListener { openEmailAdder() }
        binding.btnEditEmail.setOnClickListener { openEmailAdder() }

        // Add Phone
        val openPhoneAdder = {
            if (binding.btnEditPhone.isVisible) showAddPhoneDialog()
        }
        binding.rowPhoneNumber.setOnClickListener { openPhoneAdder() }
        binding.btnEditPhone.setOnClickListener { openPhoneAdder() }

        // Edit Location
        val openLocationEditor = { showEditLocationDialog() }
        binding.rowLocation.setOnClickListener { openLocationEditor() }
        binding.btnEditLocation.setOnClickListener { openLocationEditor() }

        // Log out
        binding.btnLogOut.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        // Delete account
        binding.btnDeleteAccount.setOnClickListener {
            showDeleteAccountConfirmationDialog()
        }
    }

    // Full Name Dialog
    private fun showEditFullNameDialog() {
        val currentName = binding.tvFullName.text.toString().let {
            if (it == "Not set") "" else it
        }

        val container = FrameLayout(this)
        val input = EditText(this).apply {
            setText(currentName)
            setSelection(length())
            hint = "Enter full name"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins((20 * resources.displayMetrics.density).toInt(), 0, (20 * resources.displayMetrics.density).toInt(), 0)
        }
        container.addView(input, params)

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Full Name")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.length < 2 || newName.length > 50) {
                    Toast.makeText(this, "Full name must be between 2 and 50 characters", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.updatePersonalDetails(fullName = newName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Username Dialog
    private fun showEditUsernameDialog() {
        val currentUsername = binding.tvUsername.text.toString().let {
            if (it == "Not set") "" else it
        }

        val container = FrameLayout(this)
        val input = EditText(this).apply {
            setText(currentUsername)
            setSelection(length())
            hint = "Enter username"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins((20 * resources.displayMetrics.density).toInt(), 0, (20 * resources.displayMetrics.density).toInt(), 0)
        }
        container.addView(input, params)

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Username")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newUsername = input.text.toString().trim()
                val regex = Regex("^[a-zA-Z0-9._]{3,30}$")
                if (!regex.matches(newUsername)) {
                    Toast.makeText(this, "Username must be 3-30 characters (letters, numbers, '.', '_')", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.updatePersonalDetails(username = newUsername)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Add Email Dialog
    private fun showAddEmailDialog() {
        val container = FrameLayout(this)
        val input = EditText(this).apply {
            hint = "Enter email address"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins((20 * resources.displayMetrics.density).toInt(), 0, (20 * resources.displayMetrics.density).toInt(), 0)
        }
        container.addView(input, params)

        MaterialAlertDialogBuilder(this)
            .setTitle("Add Email")
            .setMessage("We'll send an OTP verification code to this email.")
            .setView(container)
            .setPositiveButton("Send OTP") { _, _ ->
                val email = input.text.toString().trim().lowercase()
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                pendingEmail = email
                viewModel.requestEmailOtp(email)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Add Phone Dialog
    private fun showAddPhoneDialog() {
        val container = FrameLayout(this)
        val input = EditText(this).apply {
            hint = "10-digit mobile number"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_PHONE
        }
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins((20 * resources.displayMetrics.density).toInt(), 0, (20 * resources.displayMetrics.density).toInt(), 0)
        }
        container.addView(input, params)

        MaterialAlertDialogBuilder(this)
            .setTitle("Add Phone Number")
            .setMessage("We'll send a 6-digit OTP code to verify your phone (+91).")
            .setView(container)
            .setPositiveButton("Send OTP") { _, _ ->
                val phone = input.text.toString().trim()
                if (phone.length < 10) {
                    Toast.makeText(this, "Please enter a valid 10-digit phone number", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                pendingPhone = "+91 $phone"
                viewModel.requestPhoneOtp("+91", phone)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // OTP verification dialog
    private fun showOtpVerificationDialog(verificationId: String, isEmail: Boolean) {
        val container = FrameLayout(this)
        val input = EditText(this).apply {
            hint = "Enter 6-digit OTP"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins((20 * resources.displayMetrics.density).toInt(), 0, (20 * resources.displayMetrics.density).toInt(), 0)
        }
        container.addView(input, params)

        activeOtpDialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (isEmail) "Verify Email OTP" else "Verify Phone OTP")
            .setMessage("Please enter the 6-digit verification code sent to your ${if (isEmail) "email" else "phone"}.")
            .setView(container)
            .setPositiveButton("Verify") { _, _ ->
                val otp = input.text.toString().trim()
                if (otp.length != 6) {
                    Toast.makeText(this, "Please enter a 6-digit OTP", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (isEmail) {
                    pendingEmail?.let { viewModel.verifyEmailOtp(verificationId, otp, it) }
                } else {
                    pendingPhone?.let { viewModel.verifyPhoneOtp(verificationId, otp, it) }
                }
            }
            .setNeutralButton("Resend OTP") { _, _ ->
                if (isEmail) {
                    viewModel.resendEmailOtp(verificationId)
                } else {
                    viewModel.resendPhoneOtp(verificationId)
                }
                Toast.makeText(this, "OTP resent", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Edit Location Dialog
    private fun showEditLocationDialog() {
        val currentLocation = binding.tvLocation.text.toString().let {
            if (it == "Not added") "" else it
        }

        val container = FrameLayout(this)
        val input = EditText(this).apply {
            setText(currentLocation)
            setSelection(length())
            hint = "e.g. Indore, Madhya Pradesh, India"
            setSingleLine(true)
        }
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins((20 * resources.displayMetrics.density).toInt(), 0, (20 * resources.displayMetrics.density).toInt(), 0)
        }
        container.addView(input, params)

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Location")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newLoc = input.text.toString().trim()
                if (newLoc.isBlank()) {
                    Toast.makeText(this, "Location cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.updatePersonalDetails(location = newLoc)
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    private fun showDeleteAccountConfirmationDialog() {
        com.flirttime.core.ui.dialog.CommonConfirmationDialog.show(
            context = this,
            title = "Are you sure you want to delete your account?",
            message = "All your profile data, matches, and chats will be permanently removed.",
            positiveText = "Yes",
            negativeText = "No"
        ) {
            viewModel.deleteAccount()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Profile update state
                launch {
                    viewModel.updateProfileState.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                binding.layoutLoading.isVisible = true
                            }
                            is Resource.Success -> {
                                binding.layoutLoading.isVisible = false
                                viewModel.resetUpdateProfileState()
                                Toast.makeText(this@PersonalDetailsActivity, "Details updated successfully", Toast.LENGTH_SHORT).show()
                            }
                            is Resource.Error -> {
                                binding.layoutLoading.isVisible = false
                                Toast.makeText(this@PersonalDetailsActivity, resource.message, Toast.LENGTH_SHORT).show()
                            }
                            null -> Unit
                        }
                    }
                }

                // OTP request state
                launch {
                    viewModel.otpRequestState.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                binding.layoutLoading.isVisible = true
                            }
                            is Resource.Success -> {
                                binding.layoutLoading.isVisible = false
                                val verificationId = resource.data
                                val isEmail = pendingEmail != null
                                showOtpVerificationDialog(verificationId, isEmail)
                            }
                            is Resource.Error -> {
                                binding.layoutLoading.isVisible = false
                                Toast.makeText(this@PersonalDetailsActivity, resource.message, Toast.LENGTH_SHORT).show()
                                pendingEmail = null
                                pendingPhone = null
                            }
                            null -> Unit
                        }
                    }
                }

                // OTP verify state
                launch {
                    viewModel.otpVerifyState.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                binding.layoutLoading.isVisible = true
                            }
                            is Resource.Success -> {
                                binding.layoutLoading.isVisible = false
                                activeOtpDialog?.dismiss()
                                activeOtpDialog = null
                                viewModel.resetOtpStates()
                                val message = if (pendingEmail != null) "Email verified and saved!" else "Phone verified and saved!"
                                pendingEmail = null
                                pendingPhone = null
                                Toast.makeText(this@PersonalDetailsActivity, message, Toast.LENGTH_SHORT).show()
                            }
                            is Resource.Error -> {
                                binding.layoutLoading.isVisible = false
                                Toast.makeText(this@PersonalDetailsActivity, resource.message, Toast.LENGTH_SHORT).show()
                            }
                            null -> Unit
                        }
                    }
                }

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
                                Toast.makeText(this@PersonalDetailsActivity, "Logged out successfully", Toast.LENGTH_SHORT).show()
                                AppNavigator.navigateTo(
                                    context = this@PersonalDetailsActivity,
                                    destination = Destination.Login,
                                    clearBackStack = true,
                                    finishCurrent = true
                                )
                            }
                            is Resource.Error -> {
                                binding.layoutLoading.isVisible = false
                                Toast.makeText(this@PersonalDetailsActivity, resource.message, Toast.LENGTH_SHORT).show()
                            }
                            null -> Unit
                        }
                    }
                }

                // Delete account state
                launch {
                    viewModel.deleteAccountState.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                binding.layoutLoading.isVisible = true
                            }
                            is Resource.Success -> {
                                binding.layoutLoading.isVisible = false
                                viewModel.resetDeleteState()
                                Toast.makeText(this@PersonalDetailsActivity, "Account deleted successfully", Toast.LENGTH_SHORT).show()
                                AppNavigator.navigateTo(
                                    context = this@PersonalDetailsActivity,
                                    destination = Destination.Login,
                                    clearBackStack = true,
                                    finishCurrent = true
                                )
                            }
                            is Resource.Error -> {
                                binding.layoutLoading.isVisible = false
                                Toast.makeText(this@PersonalDetailsActivity, resource.message, Toast.LENGTH_SHORT).show()
                            }
                            null -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun formatDisplayDate(rawDob: String): String {
        if (rawDob.isBlank()) return "Not set"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = parser.parse(rawDob)
            if (date != null) {
                val formatter = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
                formatter.format(date)
            } else rawDob
        } catch (_: Exception) {
            rawDob
        }
    }
}
