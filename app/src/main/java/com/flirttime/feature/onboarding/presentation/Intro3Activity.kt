package com.flirttime.feature.onboarding.presentation

import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.flirttime.R
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.ActivityIntro3Binding

class Intro3Activity : AppCompatActivity() {

    private lateinit var binding: ActivityIntro3Binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.flirttime.core.common.EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = true, isLightNavigationBars = true)
        binding = ActivityIntro3Binding.inflate(layoutInflater)
        setContentView(binding.root)

        setupScreenSpecificEdgeToEdge()
        setupHeadlineStyling()
        setupPrivacyPolicySpannable()
        setupListeners()
    }

    /**
     * Screen-specific edge-to-edge handling:
     * Applies status bar & display cutout insets to the content container's top,
     * ensuring [ivAppName] sits cleanly below camera cutouts and status bars.
     * Applies navigation bar insets + standard spacing to [llBottom]'s bottom padding.
     */
    private fun setupScreenSpecificEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val topInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val bottomInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )

            binding.contentContainer.updatePadding(
                top = topInsets.top,
                bottom = 0
            )

            val standardBottomSpace = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._15sdp)
            binding.llBottom.updatePadding(
                bottom = bottomInsets.bottom + standardBottomSpace
            )

            windowInsets
        }
    }

    private fun setupHeadlineStyling() {
        val darkColor = ContextCompat.getColor(this, R.color.text_gry)
        val redColor = ContextCompat.getColor(this, R.color.btn_border)

        binding.tvContent.text = buildSpannedString {
            color(darkColor) { append("Discover individuals you\n") }
            color(redColor) { append("meet along the way") }
        }
    }

    private fun setupPrivacyPolicySpannable() {
        val redColor = ContextCompat.getColor(this, R.color.btn_border)
        val blackColor = ContextCompat.getColor(this, R.color.black)

        val prefixText = "By continuing, I accept FlirtTime’s "
        val policyText = "Privacy Policy"
        val fullText = prefixText + policyText

        val spannable = SpannableString(fullText)
        spannable.setSpan(ForegroundColorSpan(redColor), 0, prefixText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                AppNavigator.navigateTo(
                    context = this@Intro3Activity,
                    destination = Destination.Webview(
                        title = "Privacy policy",
                        url = "https://flirttime.in/privacy-policy"
                    )
                )
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = blackColor
                ds.isUnderlineText = false
            }
        }
        spannable.setSpan(
            clickableSpan,
            prefixText.length,
            fullText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        binding.tvPrivacyPolicy.text = spannable
        binding.tvPrivacyPolicy.movementMethod = LinkMovementMethod.getInstance()
        binding.tvPrivacyPolicy.highlightColor = Color.TRANSPARENT
    }

    private fun setupListeners() {
        binding.tvContinue.setOnClickListener {
            AppNavigator.navigateTo(this, Destination.Permission)
        }
    }
}
