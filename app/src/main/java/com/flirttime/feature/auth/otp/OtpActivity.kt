package com.flirttime.feature.auth.otp

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
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
import androidx.core.view.isGone
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.flirttime.Resource
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.core.ui.otpview.OTPListener
import com.flirttime.databinding.ActivityOtpactivityBinding
import com.intuit.sdp.R
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class OtpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtpactivityBinding
    private val sessionManager: SessionManager by inject()
    private val viewModel: OtpViewModel by viewModel()

    private var otpFrom: String = "mobile"
    private var phoneCode: String = "+91"
    private var identifier: String = ""
    private var verificationId: String = ""
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOtpactivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        readIntentData()
        setupEdgeToEdge()
        setupDisplayHeader()
        setupEmailLearnMore()
        setupOtpView()
        setupTimer()
        setupPrivacyPolicySpannable()
        setupListeners()
        observeViewModel()
    }

    private fun readIntentData() {
        otpFrom = intent.getStringExtra(AppNavigator.EXTRA_OTP_FROM) ?: "mobile"
        phoneCode = intent.getStringExtra(AppNavigator.EXTRA_PHONE_CODE) ?: ""
        identifier = intent.getStringExtra(AppNavigator.EXTRA_PHONE_NUMBER) ?: ""
        verificationId = intent.getStringExtra(AppNavigator.EXTRA_VERIFICATION_ID) ?: ""
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

                val spacerHeight = maxOf(statusBarInsets.top, resources.getDimensionPixelSize(R.dimen._10sdp))
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

    private fun setupDisplayHeader() {
        val textFrom: String
        val fullDisplayNumber: String

        if (otpFrom.equals("email", ignoreCase = true)) {
            textFrom = "mail <br> on"
            fullDisplayNumber = identifier
            binding.tvEmailVerify.visibility = View.VISIBLE
        } else {
            textFrom = "text <br> message on"
            fullDisplayNumber = "$phoneCode $identifier"
            binding.tvEmailVerify.visibility = View.GONE
        }

        val html = "<font color=\"#B4B6B8\">Please enter the code we’ve sent to you via $textFrom </font>" +
                "<font color=\"#D92B2B\"> <b>$fullDisplayNumber</b><b>&nbsp;&nbsp; •&nbsp; &nbsp;</b><u><b>Edit</b></u> </font>"

        val htmlContent = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        val ssEdit = SpannableString(htmlContent)

        val clickableSpanEdit = object : ClickableSpan() {
            override fun onClick(widget: View) {
                finish()
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = ContextCompat.getColor(this@OtpActivity, com.flirttime.R.color.btn_border)
                ds.isUnderlineText = false
            }
        }

        val editIndex = htmlContent.lastIndexOf("Edit")
        if (editIndex != -1) {
            ssEdit.setSpan(
                clickableSpanEdit,
                editIndex,
                editIndex + 4,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            ssEdit.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this, com.flirttime.R.color.btn_border)),
                editIndex,
                editIndex + 4,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        binding.tvCode.text = ssEdit
        binding.tvCode.movementMethod = LinkMovementMethod.getInstance()
        binding.tvCode.highlightColor = Color.TRANSPARENT
    }

    private fun setupEmailLearnMore() {
        val text = "<font color=\"#B4B6B8\">Email verification helps us keep your account secure.<br></font>" +
                "<font color=\"#252525\"><b>Learn more.</b></font>"
        val htmlContent = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY)
        val ss = SpannableString(htmlContent)

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                AppNavigator.navigateTo(
                    context = this@OtpActivity,
                    destination = Destination.Webview(
                        title = "Why Email Verification Matters",
                        url = "https://flirttime.in/page/privacy-policy"
                    )
                )
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = ContextCompat.getColor(this@OtpActivity, com.flirttime.R.color.black)
                ds.isUnderlineText = false
            }
        }

        val targetIndex = htmlContent.indexOf("Learn more.")
        if (targetIndex != -1) {
            ss.setSpan(
                clickableSpan,
                targetIndex,
                targetIndex + "Learn more.".length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        binding.tvEmailVerify.text = ss
        binding.tvEmailVerify.movementMethod = LinkMovementMethod.getInstance()
        binding.tvEmailVerify.highlightColor = Color.TRANSPARENT
    }

    private fun setupOtpView() {
        binding.otpView.otpListener = object : OTPListener {
            override fun onInteractionListener() {
                val len = binding.otpView.otp?.trim()?.length ?: 0
                if (len == 6) {
                    binding.tvContinue.alpha = 1.0f
                    binding.tvContinue.isEnabled = true
                } else {
                    binding.tvContinue.alpha = 0.5f
                    binding.tvContinue.isEnabled = false
                }

                if (binding.viewError.root.visibility == View.VISIBLE) {
                    binding.viewError.root.visibility = View.GONE
                    binding.linearOtpMsg.visibility = View.VISIBLE
                }
            }

            override fun onOTPComplete(otp: String) {
                binding.tvContinue.alpha = 1.0f
                binding.tvContinue.isEnabled = true
            }
        }
    }

    private fun setupTimer() {
        countDownTimer?.cancel()
        binding.tvResentOtp.isEnabled = false
        binding.tvResentOtp.isClickable = false
        binding.tvResentOtp.alpha = 0.5f

        countDownTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = (millisUntilFinished / 1000) % 60
                val min = (millisUntilFinished / (1000 * 60)) % 60
                binding.tvTime.text = String.format("%02d:%02d", min, sec)
            }

            override fun onFinish() {
                binding.tvTime.text = "00:00"
                binding.tvResentOtp.isEnabled = true
                binding.tvResentOtp.isClickable = true
                binding.tvResentOtp.alpha = 1.0f

                binding.otpView.setOTP("")
                binding.tvContinue.alpha = 0.5f
                binding.tvContinue.isEnabled = false
            }
        }.start()
    }

    private fun setupPrivacyPolicySpannable() {
        val fullText = getString(com.flirttime.R.string.by_continuing_i_accept_flirt_time_s_privacy_policy)
        val targetWord = "Privacy Policy"
        val startIndex = fullText.indexOf(targetWord, ignoreCase = true)

        val spannable = SpannableStringBuilder(fullText).apply {
            if (startIndex != -1) {
                setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(
                            this@OtpActivity,
                            com.flirttime.R.color.btn_border
                        )
                    ),
                    0,
                    startIndex,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(
                            this@OtpActivity,
                            com.flirttime.R.color.text_gry
                        )
                    ),
                    startIndex,
                    startIndex + targetWord.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            AppNavigator.navigateTo(
                                context = this@OtpActivity,
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
                context = this@OtpActivity,
                destination = Destination.Webview(
                    url = "https://flirttime.in/page/privacy-policy",
                    title = "Privacy policy"
                )
            )
        }
    }

    private fun showErrorMessageView(message: String) {
        binding.linearOtpMsg.visibility = View.GONE
        binding.viewError.root.visibility = View.VISIBLE
        binding.viewError.tvTitle.text = getString(com.flirttime.R.string.wrong_otp)
        binding.viewError.tvErrorMsg.text = message
        binding.otpView.setOTP("")
        binding.tvContinue.alpha = 0.5f
        binding.tvContinue.isEnabled = false
    }

    private fun setupListeners() {
        binding.tvContinue.setOnClickListener {
            val enteredOtp = binding.otpView.otp?.trim().orEmpty()

            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(binding.root.windowToken, 0)

            if (enteredOtp.length == 6) {
                binding.linearOtpMsg.visibility = View.VISIBLE
                binding.viewError.root.visibility = View.GONE
                // Verify OTP with backend (phone or email based on otpFrom)
                viewModel.verifyOtp(otpFrom, verificationId, enteredOtp)
            } else {
                showErrorMessageView(getString(com.flirttime.R.string.no_worries_your_match_awaits_try_again))
            }
        }

        binding.tvResentOtp.setOnClickListener {
            if (binding.linearOtpMsg.isGone) {
                binding.linearOtpMsg.visibility = View.VISIBLE
                binding.viewError.root.visibility = View.GONE
            }
            binding.otpView.setOTP("")
            viewModel.resendOtp(otpFrom, verificationId)
        }

        binding.viewError.root.setOnClickListener {
            binding.viewError.root.visibility = View.GONE
            binding.linearOtpMsg.visibility = View.VISIBLE
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.verifyOtpState.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                binding.tvContinue.isEnabled = false
                                binding.tvContinue.alpha = 0.5f
                            }
                            is Resource.Success -> {
                                binding.tvContinue.isEnabled = true
                                binding.tvContinue.alpha = 1.0f
                                Toast.makeText(this@OtpActivity, "OTP Verified Successfully", Toast.LENGTH_SHORT).show()
                                val isProfileComplete = resource.data.user?.isProfileComplete ?: false
                                viewModel.resetVerifyState()

                                if (isProfileComplete) {
                                    AppNavigator.navigateTo(
                                        context = this@OtpActivity,
                                        destination = Destination.Main(),
                                        clearBackStack = true
                                    )
                                } else {
                                    AppNavigator.navigateTo(
                                        context = this@OtpActivity,
                                        destination = Destination.IntroduceYourself,
                                        finishCurrent = true
                                    )
                                }
                            }
                            is Resource.Error -> {
                                binding.tvContinue.isEnabled = true
                                binding.tvContinue.alpha = 1.0f
                                showErrorMessageView(resource.message)
                            }
                            null -> Unit
                        }
                    }
                }

                launch {
                    viewModel.resendOtpState.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                binding.tvResentOtp.isEnabled = false
                                binding.tvResentOtp.alpha = 0.5f
                            }
                            is Resource.Success -> {
                                binding.tvResentOtp.isEnabled = false
                                resource.data.verificationId.let { verificationId = it }
                                Toast.makeText(this@OtpActivity, "Verification code resent", Toast.LENGTH_SHORT).show()
                                viewModel.resetResendState()
                                setupTimer()
                            }
                            is Resource.Error -> {
                                binding.tvResentOtp.isEnabled = true
                                binding.tvResentOtp.alpha = 1.0f
                                showErrorMessageView(resource.message)
                            }
                            null -> Unit
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}