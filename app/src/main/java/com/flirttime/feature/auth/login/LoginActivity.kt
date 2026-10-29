package com.flirttime.feature.auth.login

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.ActivityLoginBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModel()
    private val sessionManager: SessionManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLoginWindow()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupScreenSpecificEdgeToEdge()
        setupPrivacyPolicySpannable()
        setupListeners()
        observeState()
    }

    /**
     * Isolated window styling strictly for LoginActivity:
     * - Disables AndroidX default light scrims via explicit transparent SystemBarStyle
     * - Disables contrast enforcement on Android Q+
     * - Removes navigation bar divider on Android P+
     * - Ensures navigation bar and status bar are 100% transparent with white system icons
     *   so the screen's red background flows continuously to the bottom edge.
     */
    @Suppress("DEPRECATION")
    private fun setupLoginWindow() {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = Color.TRANSPARENT
        }

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false
    }

    /**
     * Screen-specific edge-to-edge handling:
     * Fullscreen background extends behind all system bars.
     * Content stays vertically anchored while [tvPrivacyPolicy] receives bottom navigation bar insets
     * so it cleanly floats above the navigation bar/pill.
     */
    private fun setupScreenSpecificEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val bottomInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )

            // Safely offset the bottom privacy text above the system navigation bar
            binding.tvPrivacyPolicy.updatePadding(
                bottom = bottomInsets.bottom
            )

            windowInsets
        }
    }

    private fun setupPrivacyPolicySpannable() {
        val fullText = getString(R.string.by_continuing_i_accept_flirt_time_s_privacy_policy)
        val privacyTarget = "Privacy Policy"
        val spannable = SpannableString(fullText)

        val startIndex = fullText.indexOf(privacyTarget)
        if (startIndex != -1) {
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    AppNavigator.navigateTo(
                        context = this@LoginActivity,
                        destination = Destination.Webview(
                            title = "Privacy policy",
                            url = "https://flirttime.in/page/privacy-policy"
                        )
                    )
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = ContextCompat.getColor(this@LoginActivity, R.color.white)
                    ds.isUnderlineText = false
                }
            }
            spannable.setSpan(
                clickableSpan,
                startIndex,
                startIndex + privacyTarget.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        binding.tvPrivacyPolicy.text = spannable
        binding.tvPrivacyPolicy.movementMethod = LinkMovementMethod.getInstance()
        binding.tvPrivacyPolicy.highlightColor = Color.TRANSPARENT
        binding.tvPrivacyPolicy.setOnClickListener {
            AppNavigator.navigateTo(
                context = this@LoginActivity,
                destination = Destination.Webview(
                    title = "Privacy policy",
                    url = "https://flirttime.in/page/privacy-policy"
                )
            )
        }
    }

    private fun setupListeners() {
        binding.llPhone.setOnClickListener {
            AppNavigator.navigateTo(this, Destination.PhoneInput())
        }

        binding.llEmail.setOnClickListener {
            AppNavigator.navigateTo(this, Destination.EmailInput)
        }

        binding.linearGoogleLogin.setOnClickListener {
            Toast.makeText(this, "Google Sign-In integration ready", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.loginState.collectLatest { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        setButtonsEnabled(false)
                    }
                    is Resource.Success -> {
                        setButtonsEnabled(true)
                        Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_SHORT).show()
                    }
                    is Resource.Error -> {
                        setButtonsEnabled(true)
                        Toast.makeText(this@LoginActivity, resource.message, Toast.LENGTH_LONG).show()
                    }
                    null -> {
                        setButtonsEnabled(true)
                    }
                }
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.llPhone.isEnabled = enabled
        binding.llEmail.isEnabled = enabled
        binding.linearGoogleLogin.isEnabled = enabled
    }
}