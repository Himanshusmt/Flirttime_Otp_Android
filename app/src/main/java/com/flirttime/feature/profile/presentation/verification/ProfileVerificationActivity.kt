package com.flirttime.feature.profile.presentation.verification

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.flirttime.R
import com.flirttime.core.database.SessionManager
import com.flirttime.core.navigation.AppNavigator
import com.flirttime.core.navigation.Destination
import com.flirttime.databinding.ActivityProfileVerificationBinding
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class ProfileVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileVerificationBinding
    private val sessionManager: SessionManager by inject()
    private var selectedImageUri: Uri? = null
    private var tempCameraUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            binding.ivProfileVerificationArt.setImageURI(uri)
            proceedToPicturePreview(uri.toString())
        }
    }

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            selectedImageUri = tempCameraUri
            proceedToPicturePreview(tempCameraUri.toString())
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCameraDirect()
        } else {
            showCameraPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.flirttime.core.common.EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = true, isLightNavigationBars = true)
        binding = ActivityProfileVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initHeader()
        setupEdgeToEdge()
        setupListeners()
    }

    private fun initHeader() {
        binding.icHeader.tvHeading.text = getString(R.string.profile_verification)
        binding.icHeader.backArrow.visibility = View.GONE
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val navBarInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )

            // 1. Top status bar insets applied to header padding
            val baseHeaderPadding = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._10sdp)
            binding.icHeader.root.updatePadding(
                top = statusBarInsets.top + baseHeaderPadding
            )

            // 2. Bottom navigation bar insets applied to pinned bottom CTA container
            val baseBottomPadding = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._15sdp)
            binding.bottomCtaContainer.updatePadding(
                bottom = navBarInsets.bottom + baseBottomPadding
            )

            windowInsets
        }
    }

    private fun setupListeners() {
        binding.lnTakePhoto.setOnClickListener {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.CAMERA
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                launchCameraDirect()
            } else {
                requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }

        binding.linearLibrary.setOnClickListener {
            pickImageLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }
    }

    private fun launchCameraDirect() {
        try {
            val imagesFolder = java.io.File(cacheDir, "camera_photos").apply { mkdirs() }
            val photoFile = java.io.File(imagesFolder, "profile_capture_${System.currentTimeMillis()}.jpg")
            if (!photoFile.exists()) {
                photoFile.createNewFile()
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                photoFile
            )
            tempCameraUri = uri
            takePhotoLauncher.launch(uri)
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

    private fun saveBitmapToCache(bitmap: Bitmap): Uri? {
        return try {
            val file = java.io.File(cacheDir, "selected_profile_pic.jpg")
            val outputStream = java.io.FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()
            Uri.fromFile(file)
        } catch (e: Exception) {
            null
        }
    }

    private fun proceedToPicturePreview(imageUri: String) {
        lifecycleScope.launch {
            if (imageUri.isNotEmpty()) {
                sessionManager.setProfileImageUri(imageUri)
            }
            sessionManager.setPageRedirect("avatar")

            AppNavigator.navigateTo(
                context = this@ProfileVerificationActivity,
                destination = Destination.PicturePreview(imageUri),
                finishCurrent = false
            )
        }
    }
}
