package com.flirttime.feature.profile.presentation.gesture

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.ActivityGestureVerificationBinding
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class GestureVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGestureVerificationBinding
    private val sessionManager: SessionManager by inject()
    private val viewModel: GestureVerificationViewModel by viewModel()
    private var tempGestureUri: Uri? = null

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            showCameraPermissionDeniedDialog()
        }
    }

    private val takeSelfieLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val uri = tempGestureUri
            if (uri != null) {
                viewModel.uploadGesturePhoto(this, uri)
            } else {
                proceedToVerificationProcess()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.flirttime.core.common.EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = true, isLightNavigationBars = true)
        binding = ActivityGestureVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initHeader()
        setupEdgeToEdge()
        setupPrivacyPolicyLink()
        setupMulticolorTitle()
        setupListeners()
        observeViewModel()
    }

    private fun initHeader() {
        binding.iHeader.tvHeading.text = "Gesture verification"
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

            // Bottom navigation bar insets applied to pinned bottom CTA container
            val baseBottomPadding = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._15sdp)
            binding.bottomCtaContainer.updatePadding(
                bottom = navBarInsets.bottom + baseBottomPadding
            )

            windowInsets
        }
    }

    private fun setupMulticolorTitle() {
        val html = "<font color='#252525'>Copy this pose and <br/>take a</font> <font color='#D92B2B'> photo</font>"
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
                        context = this@GestureVerificationActivity,
                        destination = Destination.Webview(
                            title = "Privacy Policy",
                            url = "https://staging.flirttime.in/privacy-policy"
                        )
                    )
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = ContextCompat.getColor(this@GestureVerificationActivity, R.color.btn_border)
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
        }
    }

    private fun setupListeners() {
        binding.tvTakephoto.setOnClickListener {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.CAMERA
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                launchCamera()
            } else {
                requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchCamera() {
        try {
            val imagesFolder = java.io.File(cacheDir, "camera_photos").apply { mkdirs() }
            val photoFile = java.io.File(imagesFolder, "gesture_capture_${System.currentTimeMillis()}.jpg")
            if (!photoFile.exists()) {
                photoFile.createNewFile()
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                photoFile
            )
            tempGestureUri = uri
            takeSelfieLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Unable to launch camera: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCameraPermissionDeniedDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Alert !")
            .setMessage("Camera permission is important in order to take picture.")
            .setCancelable(false)
            .setPositiveButton("Allow") { dialog, _ ->
                dialog.dismiss()
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Deny") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uploadState.collect { resource ->
                    when (resource) {
                        is Resource.Loading -> {
                            binding.tvTakephoto.isEnabled = false
                            binding.tvTakephoto.alpha = 0.5f
                        }
                        is Resource.Success -> {
                            binding.tvTakephoto.isEnabled = true
                            binding.tvTakephoto.alpha = 1.0f
                            viewModel.resetUploadState()
                            proceedToVerificationProcess()
                        }
                        is Resource.Error -> {
                            binding.tvTakephoto.isEnabled = true
                            binding.tvTakephoto.alpha = 1.0f
                            Toast.makeText(this@GestureVerificationActivity, resource.message, Toast.LENGTH_LONG).show()
                        }
                        null -> Unit
                    }
                }
            }
        }
    }

    private fun proceedToVerificationProcess() {
        lifecycleScope.launch {
            sessionManager.setPageRedirect("gesture")
            Toast.makeText(this@GestureVerificationActivity, "Gesture uploaded successfully!", Toast.LENGTH_SHORT).show()
            AppNavigator.navigateTo(
                context = this@GestureVerificationActivity,
                destination = Destination.VerificationProcess,
                finishCurrent = true
            )
        }
    }
}
