package com.flirttime.feature.auth.phone

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import com.flirttime.databinding.ActivityPhoneInputBinding
import com.flirttime.feature.auth.selectCountry.SelectCountryActivity
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class PhoneInputActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhoneInputBinding
    private val viewModel: PhoneInputViewModel by viewModel()

    private var currentPhoneCode: String = "+91"

    private val countryPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data ?: return@registerForActivityResult
                val code = data.getStringExtra("countryCode") ?: "+91"
                val flagResId = data.getIntExtra("countryFlagResId", -1)

                currentPhoneCode = code
                binding.tvMobileNo.text = code

                if (flagResId != -1) {
                    binding.imgFlag.setImageResource(flagResId)
                } else {
                    binding.imgFlag.setImageResource(R.drawable.img_flag_placeholder)
                }

                setupPhoneInput()
                val input = binding.etMobile.text?.toString()?.trim().orEmpty()
                val isValid = input.length == getRequiredLength()
                binding.tvContinue.isEnabled = isValid
                binding.tvContinue.alpha = if (isValid) 1.0f else 0.5f
            }
        }

    private val maxLengths = mapOf(
        "+1" to 10,     // USA, Canada
        "+7" to 10,     // Russia
        "+20" to 9,     // Egypt
        "+27" to 9,     // South Africa
        "+30" to 10,    // Greece
        "+31" to 9,     // Netherlands
        "+32" to 9,     // Belgium
        "+33" to 9,     // France
        "+34" to 9,     // Spain
        "+36" to 9,     // Hungary
        "+39" to 10,    // Italy
        "+40" to 10,    // Romania
        "+41" to 9,     // Switzerland
        "+43" to 10,    // Austria
        "+44" to 11,    // United Kingdom
        "+45" to 8,     // Denmark
        "+46" to 10,    // Sweden
        "+47" to 8,     // Norway
        "+48" to 9,     // Poland
        "+49" to 11,    // Germany
        "+51" to 9,     // Peru
        "+52" to 10,    // Mexico
        "+53" to 8,     // Cuba
        "+54" to 10,    // Argentina
        "+55" to 11,    // Brazil
        "+56" to 9,     // Chile
        "+57" to 10,    // Colombia
        "+58" to 11,    // Venezuela
        "+60" to 10,    // Malaysia
        "+61" to 9,     // Australia
        "+62" to 10,    // Indonesia
        "+63" to 10,    // Philippines
        "+64" to 9,     // New Zealand
        "+65" to 8,     // Singapore
        "+66" to 9,     // Thailand
        "+81" to 10,    // Japan
        "+82" to 11,    // South Korea
        "+84" to 10,    // Vietnam
        "+86" to 11,    // China
        "+90" to 10,    // Turkey
        "+91" to 10,    // India
        "+92" to 10,    // Pakistan
        "+93" to 9,     // Afghanistan
        "+94" to 10,    // Sri Lanka
        "+95" to 8,     // Myanmar
        "+98" to 10     // Iran
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPhoneInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        setupScreenSpecificEdgeToEdge()
        setupPhoneInput()
        setupPrivacyPolicySpannable()
        setupListeners()
        observeViewModel()
    }

    /**
     * Screen-specific edge-to-edge handling:
     * - Keyboard closed: Background artwork draws edge-to-edge behind status bars,
     *   bottom inset applied for system navigation bars.
     * - Keyboard open: Banner collapses, spacer accounts for status bar / display cutout,
     *   bottom inset adjusts for soft keyboard (IME).
     */
    private fun setupScreenSpecificEdgeToEdge() {
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

    private fun getRequiredLength(): Int {
        return maxLengths[currentPhoneCode] ?: 10
    }

    private fun setupPhoneInput() {
        binding.etMobile.filters = arrayOf(InputFilter.LengthFilter(getRequiredLength()))

        binding.etMobile.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.linearMobile.setBackgroundResource(R.drawable.loginnumber_bg_redstroke)
            } else {
                binding.linearMobile.setBackgroundResource(R.drawable.loginnumber_bg)
            }
        }

        binding.etMobile.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (binding.viewError.root.visibility == View.VISIBLE) {
                    binding.viewError.root.visibility = View.GONE
                    binding.linearWelcome.visibility = View.VISIBLE
                }
            }

            override fun afterTextChanged(s: Editable?) {
                val input = s?.toString()?.trim().orEmpty()
                val isValid = input.length == getRequiredLength()
                binding.tvContinue.isEnabled = isValid
                binding.tvContinue.alpha = if (isValid) 1.0f else 0.5f
            }
        })
    }

    private fun isInvalidDummyNumber(phoneNumber: String): Boolean {
        return when (phoneNumber) {
            "0000000000", "1111111111", "2222222222", "1234567890",
            "9999999999", "0123456789", "1231231231", "5555555555",
            "1010101010" -> true
            else -> false
        }
    }

    private fun showErrorMessageView(errorMsg: String) {
        binding.linearWelcome.visibility = View.GONE
        binding.viewError.root.visibility = View.VISIBLE
        binding.viewError.tvTitle.text = getString(R.string.got_some_trouble)
        binding.viewError.tvErrorMsg.text = errorMsg
    }

    private fun setupPrivacyPolicySpannable() {
        val fullText = getString(R.string.by_continuing_i_accept_flirt_time_s_privacy_policy)
        val targetWord = "Privacy Policy"
        val startIndex = fullText.indexOf(targetWord, ignoreCase = true)

        val spannable = SpannableStringBuilder(fullText).apply {
            if (startIndex != -1) {
                setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(
                            this@PhoneInputActivity,
                            R.color.btn_border
                        )
                    ),
                    0,
                    startIndex,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(
                            this@PhoneInputActivity,
                            R.color.text_gry
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
                                context = this@PhoneInputActivity,
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
                context = this@PhoneInputActivity,
                destination = Destination.Webview(
                    url = "https://flirttime.in/page/privacy-policy",
                    title = "Privacy policy"
                )
            )
        }
    }

    private fun setupListeners() {
        binding.tvContinue.setOnClickListener {
            val phone = binding.etMobile.text.toString().trim()
            if (phone.length != getRequiredLength() || isInvalidDummyNumber(phone)) {
                showErrorMessageView(getString(R.string.please_enter_valid_contact_number_so_your_match_can_text_you))
            } else {
                binding.linearWelcome.visibility = View.VISIBLE
                binding.viewError.root.visibility = View.GONE

                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(binding.etMobile.windowToken, 0)

                // Request OTP from backend API
                viewModel.requestPhoneOtp(currentPhoneCode, phone)
            }
        }

        binding.llSelectcountry.setOnClickListener {
            val intent = Intent(this, SelectCountryActivity::class.java).apply {
                putExtra("countryCode", currentPhoneCode)
            }
            countryPickerLauncher.launch(intent)
        }

        binding.ivMail.setOnClickListener {
            AppNavigator.navigateTo(this, Destination.EmailInput)
        }

        binding.linearGoogleLogin.setOnClickListener {
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
                            val phone = binding.etMobile.text.toString().trim()
                            val otpData = resource.data
                            viewModel.resetRequestOtpState()

                            AppNavigator.navigateTo(
                                context = this@PhoneInputActivity,
                                destination = Destination.Otp(
                                    otpFrom = "mobile",
                                    phoneCode = currentPhoneCode,
                                    identifier = phone,
                                    verificationId = otpData.verificationId
                                )
                            )
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
        }
    }
}