package com.flirttime.feature.onboarding

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.flirttime.R
import com.flirttime.Resource
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.ActivityPicturePreviewBinding
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class PicturePreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPicturePreviewBinding
    private val viewModel: PicturePreviewViewModel by viewModel()
    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.flirttime.core.common.EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = true, isLightNavigationBars = true)
        binding = ActivityPicturePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initHeader()
        setupEdgeToEdge()
        displayImage()
        setupMulticolorTitle()
        setupPrivacyPolicyLink()
        setupClickListeners()
        observeViewModel()
    }

    private fun initHeader() {
        binding.iHeader.tvHeading.text = getString(R.string.profile_verification)
        binding.iHeader.backArrow.setOnClickListener {
            finish()
        }
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )

            // Top status bar insets applied to header padding
            val baseHeaderPadding = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._10sdp)
            binding.iHeader.root.updatePadding(
                top = statusBarInsets.top + baseHeaderPadding
            )

            // Bottom navigation bar insets applied to bottom button container
            val baseBottomPadding = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._15sdp)
            binding.bottomButtonContainer.updatePadding(
                bottom = navBarInsets.bottom + baseBottomPadding
            )

            windowInsets
        }
    }

    private fun displayImage() {
        val imageUriString = intent.getStringExtra(AppNavigator.EXTRA_IMAGE_URI)
        if (!imageUriString.isNullOrEmpty()) {
            try {
                val parsedUri = Uri.parse(imageUriString)
                imageUri = parsedUri
                binding.ivGesture.setImageURI(parsedUri)
            } catch (e: Exception) {
                binding.ivGesture.setImageResource(R.drawable.img_app_logo)
            }
        } else {
            binding.ivGesture.setImageResource(R.drawable.img_app_logo)
        }
    }

    private fun setupMulticolorTitle() {
        val html = "<font color='#252525'>Picture perfect! <br/> Your</font>  <font color='#D92B2B'> profile shines</font>"
        binding.tvTitle.text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
    }

    private fun setupPrivacyPolicyLink() {
        val html = "<font color='#B4B6B8'>For more info on how we use, retain and protect your personal data please read our</font> <font color='#D92B2B'>Privacy Policy</font>"
        val spannedHtml = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        val spannableString = SpannableString(spannedHtml)

        val privacyPolicyText = "Privacy Policy"
        val startIndex = spannedHtml.indexOf(privacyPolicyText)
        if (startIndex != -1) {
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    AppNavigator.navigateTo(
                        context = this@PicturePreviewActivity,
                        destination = Destination.Webview(
                            title = "Privacy Policy",
                            url = "https://staging.flirttime.in/privacy-policy"
                        )
                    )
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = ContextCompat.getColor(this@PicturePreviewActivity, R.color.btn_border)
                    ds.isUnderlineText = false
                }
            }

            spannableString.setSpan(
                clickableSpan,
                startIndex,
                startIndex + privacyPolicyText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            binding.tvContent.text = spannableString
            binding.tvContent.movementMethod = LinkMovementMethod.getInstance()
            binding.tvContent.highlightColor = Color.TRANSPARENT
        } else {
            binding.tvContent.text = spannedHtml
        }
    }

    private fun setupClickListeners() {
        binding.tvRetake.setOnClickListener {
            finish()
        }

        binding.tvContinue.setOnClickListener {
            val uri = imageUri
            if (uri != null) {
                viewModel.uploadProfilePhoto(this, uri)
            } else {
                AppNavigator.navigateTo(
                    context = this,
                    destination = Destination.GestureVerification,
                    finishCurrent = false
                )
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uploadState.collect { resource ->
                    when (resource) {
                        is Resource.Loading -> {
                            binding.tvContinue.isEnabled = false
                            binding.tvContinue.alpha = 0.5f
                        }
                        is Resource.Success -> {
                            binding.tvContinue.isEnabled = true
                            binding.tvContinue.alpha = 1.0f
                            viewModel.resetUploadState()
                            Toast.makeText(this@PicturePreviewActivity, "Profile photo uploaded successfully", Toast.LENGTH_SHORT).show()

                            AppNavigator.navigateTo(
                                context = this@PicturePreviewActivity,
                                destination = Destination.GestureVerification,
                                finishCurrent = false
                            )
                        }
                        is Resource.Error -> {
                            binding.tvContinue.isEnabled = true
                            binding.tvContinue.alpha = 1.0f
                            Toast.makeText(this@PicturePreviewActivity, resource.message, Toast.LENGTH_LONG).show()
                        }
                        null -> Unit
                    }
                }
            }
        }
    }
}
