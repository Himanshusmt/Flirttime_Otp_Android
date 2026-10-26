package com.flirttime.feature.auth.presentation.email

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.ActivityEmailVerifyBinding
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class EmailVerifyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmailVerifyBinding
    private val viewModel: EmailVerifyViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEmailVerifyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        setupEdgeToEdge()
        setupInputFocus()
        setupEmailWatcher()
        setupLearnMoreSpannable()
        setupPrivacyPolicySpannable()
        setupListeners()
        observeViewModel()
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )
            val imeInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.ime()
            )

            val isKeyboardVisible = imeInsets.bottom > 0

            if (isKeyboardVisible) {
                binding.viewCollapseHeader.ivBg.visibility = View.GONE
                binding.viewCollapseHeader.viewCurve.visibility = View.GONE
                binding.viewCollapseHeader.viewSpacer.visibility = View.VISIBLE

                val spacerHeight = maxOf(statusBarInsets.top, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._10sdp))
                binding.viewCollapseHeader.viewSpacer.updateLayoutParams {
                    height = spacerHeight
                }
                binding.linearBottom.gravity = Gravity.TOP
            } else {
                binding.viewCollapseHeader.ivBg.visibility = View.VISIBLE
                binding.viewCollapseHeader.viewCurve.visibility = View.VISIBLE
                binding.viewCollapseHeader.viewSpacer.visibility = View.GONE
                binding.linearBottom.gravity = Gravity.BOTTOM
            }

            val bottomPadding = if (isKeyboardVisible) imeInsets.bottom else navBarInsets.bottom
            binding.scrollView.updatePadding(bottom = bottomPadding)

            windowInsets
        }
    }

    private fun setupInputFocus() {
        binding.etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.linearEmail.setBackgroundResource(R.drawable.loginnumber_bg_redstroke)
            } else {
                binding.linearEmail.setBackgroundResource(R.drawable.loginnumber_bg)
            }
        }
    }

    private fun setupEmailWatcher() {
        binding.etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (binding.viewError.root.visibility == View.VISIBLE) {
                    binding.viewError.root.visibility = View.GONE
                    binding.linearEmailHeading.visibility = View.VISIBLE
                }
            }

            override fun afterTextChanged(s: Editable?) {
                val email = s?.toString()?.trim().orEmpty()
                val isValid = email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
                binding.tvContinue.isEnabled = isValid
                binding.tvContinue.alpha = if (isValid) 1.0f else 0.5f
            }
        })
    }

    private fun setupLearnMoreSpannable() {
        val text = "Email verification helps us keep your account secure. Learn more."
        val target = "Learn more."
        val spannable = SpannableStringBuilder(text)
        val startIndex = text.indexOf(target)

        if (startIndex != -1) {
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    AppNavigator.navigateTo(
                        context = this@EmailVerifyActivity,
                        destination = Destination.Webview(
                            title = "Why Email Verification Matters",
                            url = "https://flirttime.in/page/privacy-policy"
                        )
                    )
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = ContextCompat.getColor(this@EmailVerifyActivity, R.color.text_gry)
                    ds.isFakeBoldText = true
                    ds.isUnderlineText = false
                }
            }
            spannable.setSpan(
                clickableSpan,
                startIndex,
                startIndex + target.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        binding.tvSecureContent.text = spannable
        binding.tvSecureContent.movementMethod = LinkMovementMethod.getInstance()
        binding.tvSecureContent.highlightColor = Color.TRANSPARENT
    }

    private fun setupPrivacyPolicySpannable() {
        val fullText = getString(R.string.by_continuing_i_accept_flirt_time_s_privacy_policy)
        val targetWord = "Privacy Policy"
        val startIndex = fullText.indexOf(targetWord, ignoreCase = true)

        val spannable = SpannableStringBuilder(fullText).apply {
            if (startIndex != -1) {
                setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(this@EmailVerifyActivity, R.color.btn_border)),
                    0,
                    startIndex,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(this@EmailVerifyActivity, R.color.text_gry)),
                    startIndex,
                    startIndex + targetWord.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            AppNavigator.navigateTo(
                                context = this@EmailVerifyActivity,
                                destination = Destination.Webview(
                                    url = "https://flirttime.in/page/privacy-policy",
                                    title = "Privacy policy"
                                )
                            )
                        }

                        override fun updateDrawState(ds: TextPaint) {
                            super.updateDrawState(ds)
                            ds.isUnderlineText = false
                        }
                    },
                    startIndex,
                    startIndex + targetWord.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        binding.tvPrivacyPolicy.text = spannable
        binding.tvPrivacyPolicy.movementMethod = LinkMovementMethod.getInstance()
        binding.tvPrivacyPolicy.highlightColor = Color.TRANSPARENT
        binding.tvPrivacyPolicy.setOnClickListener {
            AppNavigator.navigateTo(
                context = this@EmailVerifyActivity,
                destination = Destination.Webview(
                    url = "https://flirttime.in/page/privacy-policy",
                    title = "Privacy policy"
                )
            )
        }
    }

    private fun showErrorView(errorMsg: String) {
        binding.linearEmailHeading.visibility = View.GONE
        binding.viewError.root.visibility = View.VISIBLE
        binding.viewError.tvTitle.text = getString(R.string.got_some_trouble)
        binding.viewError.tvErrorMsg.text = errorMsg
    }

    private fun setupListeners() {
        binding.tvContinue.setOnClickListener {
            val email = binding.etEmail.text?.toString()?.trim().orEmpty()
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showErrorView(getString(R.string.please_enter_valid_email_so_your_match_can_text_you))
            } else {
                binding.linearEmailHeading.visibility = View.VISIBLE
                binding.viewError.root.visibility = View.GONE

                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(binding.etEmail.windowToken, 0)

                // Request Email OTP from backend
                viewModel.requestEmailOtp(email)
            }
        }

        binding.linearPhone.setOnClickListener {
            finish()
        }

        binding.linearGoogle.setOnClickListener {
            Toast.makeText(this, "Google Sign-In integration ready", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.requestOtpState.collect { resource ->
                    when (resource) {
                        is Resource.Loading -> {
                            binding.tvContinue.isEnabled = false
                            binding.tvContinue.alpha = 0.5f
                        }
                        is Resource.Success -> {
                            binding.tvContinue.isEnabled = true
                            binding.tvContinue.alpha = 1.0f
                            val email = binding.etEmail.text?.toString()?.trim().orEmpty()
                            val otpData = resource.data
                            viewModel.resetRequestOtpState()

                            AppNavigator.navigateTo(
                                context = this@EmailVerifyActivity,
                                destination = Destination.Otp(
                                    otpFrom = "email",
                                    phoneCode = "",
                                    identifier = email,
                                    verificationId = otpData.verificationId
                                )
                            )
                        }
                        is Resource.Error -> {
                            binding.tvContinue.isEnabled = true
                            binding.tvContinue.alpha = 1.0f
                            showErrorView(resource.message)
                        }
                        null -> Unit
                    }
                }
            }
        }
    }
}
