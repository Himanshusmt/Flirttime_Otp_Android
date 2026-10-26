package com.flirttime.core.image

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.canhub.cropper.CropImageView
import com.flirttime.databinding.ActivityCropBinding
import java.io.File

/**
 * Dedicated, brand-aligned Image Cropping Activity.
 * Implements the customized crop experience matching the reference design:
 * - Pure black background
 * - "Cancel" on the left
 * - "Crop photo X of Y" centered title
 * - Pinch-to-zoom and drag to frame with 3x3 grid
 * - "Pinch and drag to frame your photo" hint
 * - "Next" red pill CTA button
 * - Supports sequential cropping when multiple photos are picked
 */
class CropActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SOURCE_URI = "extra_source_uri"
        const val EXTRA_SOURCE_URIS = "extra_source_uris"
        const val EXTRA_CROPPED_URI = "extra_cropped_uri"
        const val EXTRA_CROPPED_URIS = "extra_cropped_uris"
        const val EXTRA_CROP_SHAPE = "extra_crop_shape"
        const val EXTRA_ASPECT_RATIO_FIXED = "extra_aspect_ratio_fixed"
        const val EXTRA_ASPECT_RATIO_X = "extra_aspect_ratio_x"
        const val EXTRA_ASPECT_RATIO_Y = "extra_aspect_ratio_y"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MAX_WIDTH = "extra_max_width"
        const val EXTRA_MAX_HEIGHT = "extra_max_height"
        const val EXTRA_QUALITY = "extra_quality"
        const val EXTRA_CUSTOM_OUTPUT_URI = "extra_custom_output_uri"

        fun createIntent(
            context: Context,
            sourceUri: Uri,
            config: CropConfig = CropConfig()
        ): Intent {
            return createMultipleIntent(context, listOf(sourceUri), config)
        }

        fun createMultipleIntent(
            context: Context,
            sourceUris: List<Uri>,
            config: CropConfig = CropConfig()
        ): Intent {
            val (fixedRatio, ratioX, ratioY) = when (val ratio = config.aspectRatio) {
                is CropAspectRatio.Freeform -> Triple(false, 1, 1)
                is CropAspectRatio.Square -> Triple(true, 1, 1)
                is CropAspectRatio.Portrait4_5 -> Triple(true, 4, 5)
                is CropAspectRatio.Portrait3_4 -> Triple(true, 3, 4)
                is CropAspectRatio.Landscape16_9 -> Triple(true, 16, 9)
                is CropAspectRatio.Custom -> Triple(true, ratio.ratioX, ratio.ratioY)
            }

            return Intent(context, CropActivity::class.java).apply {
                if (sourceUris.isNotEmpty()) {
                    putExtra(EXTRA_SOURCE_URI, sourceUris.first())
                    putParcelableArrayListExtra(EXTRA_SOURCE_URIS, ArrayList(sourceUris))
                }
                putExtra(EXTRA_CROP_SHAPE, config.shape.name)
                putExtra(EXTRA_ASPECT_RATIO_FIXED, fixedRatio)
                putExtra(EXTRA_ASPECT_RATIO_X, ratioX)
                putExtra(EXTRA_ASPECT_RATIO_Y, ratioY)
                putExtra(EXTRA_TITLE, config.title)
                putExtra(EXTRA_MAX_WIDTH, config.maxOutputWidth)
                putExtra(EXTRA_MAX_HEIGHT, config.maxOutputHeight)
                putExtra(EXTRA_QUALITY, config.compressionQuality)
                if (config.customOutputUri != null) {
                    putExtra(EXTRA_CUSTOM_OUTPUT_URI, config.customOutputUri)
                }
            }
        }
    }

    private lateinit var binding: ActivityCropBinding
    private val pendingUris = ArrayList<Uri>()
    private val croppedResults = ArrayList<Uri>()
    private var currentIndex = 0

    private var customOutputUri: Uri? = null
    private var maxOutputWidth: Int = 1200
    private var maxOutputHeight: Int = 1200
    private var compressionQuality: Int = 90

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.flirttime.core.common.EdgeToEdgeHelper.setupEdgeToEdge(this, isLightStatusBars = false, isLightNavigationBars = false)

        binding = ActivityCropBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val urisExtra = androidx.core.content.IntentCompat.getParcelableArrayListExtra(
            intent,
            EXTRA_SOURCE_URIS,
            Uri::class.java
        )
        if (!urisExtra.isNullOrEmpty()) {
            pendingUris.addAll(urisExtra)
        } else {
            val single = androidx.core.content.IntentCompat.getParcelableExtra(
                intent,
                EXTRA_SOURCE_URI,
                Uri::class.java
            )
            if (single != null) {
                pendingUris.add(single)
            }
        }

        customOutputUri = androidx.core.content.IntentCompat.getParcelableExtra(
            intent,
            EXTRA_CUSTOM_OUTPUT_URI,
            Uri::class.java
        )

        if (pendingUris.isEmpty()) {
            Toast.makeText(this, "No image provided to crop", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        setupInsets()
        setupCropView()
        setupListeners()
        loadCurrentPhoto()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutHeader) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBars.top + 8)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutBottomActions) { view, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBars.bottom + 16)
            insets
        }
    }

    private fun setupCropView() {
        val shapeName = intent.getStringExtra(EXTRA_CROP_SHAPE) ?: CropShape.RECTANGLE.name
        val shape = if (shapeName == CropShape.OVAL.name) {
            CropImageView.CropShape.OVAL
        } else {
            CropImageView.CropShape.RECTANGLE
        }
        binding.cropImageView.cropShape = shape

        val fixedRatio = intent.getBooleanExtra(EXTRA_ASPECT_RATIO_FIXED, true)
        val ratioX = intent.getIntExtra(EXTRA_ASPECT_RATIO_X, 4)
        val ratioY = intent.getIntExtra(EXTRA_ASPECT_RATIO_Y, 3)

        binding.cropImageView.setFixedAspectRatio(fixedRatio)
        if (fixedRatio) {
            binding.cropImageView.setAspectRatio(ratioX, ratioY)
        }

        binding.cropImageView.guidelines = CropImageView.Guidelines.ON

        maxOutputWidth = intent.getIntExtra(EXTRA_MAX_WIDTH, 1200)
        maxOutputHeight = intent.getIntExtra(EXTRA_MAX_HEIGHT, 1200)
        compressionQuality = intent.getIntExtra(EXTRA_QUALITY, 90)

        binding.cropImageView.setOnCropImageCompleteListener { _, result ->
            binding.layoutLoading.visibility = View.GONE
            binding.btnNext.isEnabled = true

            if (result.isSuccessful && result.uriContent != null) {
                croppedResults.add(result.uriContent!!)
                advanceToNextOrFinish()
            } else {
                val errorMsg = result.error?.localizedMessage ?: "Failed to crop image"
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCurrentPhoto() {
        val total = pendingUris.size
        val customTitle = intent.getStringExtra(EXTRA_TITLE)

        binding.tvTitle.text = when {
            total > 1 -> "Crop photo ${currentIndex + 1} of $total"
            customTitle != null -> customTitle
            else -> "Crop photo"
        }

        binding.btnNext.text = "Next"

        binding.cropImageView.setImageUriAsync(pendingUris[currentIndex])
    }

    private fun advanceToNextOrFinish() {
        val total = pendingUris.size
        if (currentIndex < total - 1) {
            currentIndex++
            loadCurrentPhoto()
        } else {
            // All photos cropped successfully
            val data = Intent().apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(EXTRA_CROPPED_URI, croppedResults.firstOrNull())
                putParcelableArrayListExtra(EXTRA_CROPPED_URIS, croppedResults)
            }
            setResult(Activity.RESULT_OK, data)
            finish()
        }
    }

    private fun setupListeners() {
        binding.btnCancel.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        binding.btnNext.setOnClickListener {
            applyCrop()
        }
    }

    private fun applyCrop() {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.btnNext.isEnabled = false

        val targetUri = if (pendingUris.size == 1 && customOutputUri != null) {
            if (customOutputUri!!.scheme == "file") {
                val file = File(customOutputUri!!.path ?: "")
                androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    file
                )
            } else {
                customOutputUri
            }
        } else {
            generateOutputCacheUri()
        }

        if (targetUri != null) {
            grantUriPermission(
                packageName,
                targetUri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        binding.cropImageView.croppedImageAsync(
            saveCompressFormat = Bitmap.CompressFormat.JPEG,
            saveCompressQuality = compressionQuality,
            reqWidth = maxOutputWidth,
            reqHeight = maxOutputHeight,
            options = CropImageView.RequestSizeOptions.RESIZE_INSIDE,
            customOutputUri = targetUri
        )
    }

    private fun generateOutputCacheUri(): Uri? {
        return try {
            val cropsDir = File(cacheDir, "cropped_images").apply { mkdirs() }
            val file = File(cropsDir, "crop_${System.currentTimeMillis()}_$currentIndex.jpg")
            if (!file.exists()) {
                file.createNewFile()
            }
            androidx.core.content.FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
