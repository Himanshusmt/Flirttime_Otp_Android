package com.flirttime.core.image

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import java.io.File

/**
 * Centralized, lifecycle-safe helper for cropping images in Activities and Fragments.
 * Provides unified entrypoints for:
 * - Direct URI cropping: [launchCrop]
 * - Gallery selection + crop: [launchGallery]
 * - Camera capture + crop: [launchCamera]
 */
class ImageCropperHelper private constructor(
    private val cropLauncher: ActivityResultLauncher<CropRequest>,
    private val pickGalleryLauncher: ActivityResultLauncher<PickVisualMediaRequest>,
    private val takeCameraLauncher: ActivityResultLauncher<Uri>,
    private val contextProvider: () -> Context
) {

    private var pendingConfig: CropConfig = CropConfig()
    private var pendingCameraUri: Uri? = null

    companion object {
        /**
         * Register ImageCropperHelper in a ComponentActivity.
         * MUST be called during Activity initialization or in onCreate().
         */
        fun register(
            activity: ComponentActivity,
            onResult: (CropResult) -> Unit
        ): ImageCropperHelper {
            var helper: ImageCropperHelper? = null

            val cropLauncher = activity.registerForActivityResult(FlirtTimeCropContract()) { result ->
                onResult(result)
            }

            val pickGalleryLauncher = activity.registerForActivityResult(
                ActivityResultContracts.PickVisualMedia()
            ) { uri ->
                if (uri != null) {
                    val config = helper?.pendingConfig ?: CropConfig()
                    cropLauncher.launch(CropRequest(sourceUri = uri, config = config))
                } else {
                    onResult(CropResult.Cancelled)
                }
            }

            val takeCameraLauncher = activity.registerForActivityResult(
                ActivityResultContracts.TakePicture()
            ) { success ->
                val cameraUri = helper?.pendingCameraUri
                if (success && cameraUri != null) {
                    val config = helper?.pendingConfig ?: CropConfig()
                    cropLauncher.launch(CropRequest(sourceUri = cameraUri, config = config))
                } else {
                    onResult(CropResult.Cancelled)
                }
            }

            val instance = ImageCropperHelper(
                cropLauncher = cropLauncher,
                pickGalleryLauncher = pickGalleryLauncher,
                takeCameraLauncher = takeCameraLauncher,
                contextProvider = { activity }
            )
            helper = instance
            return instance
        }

        /**
         * Register ImageCropperHelper in a Fragment.
         * MUST be called during Fragment initialization or in onAttach().
         */
        fun register(
            fragment: Fragment,
            onResult: (CropResult) -> Unit
        ): ImageCropperHelper {
            var helper: ImageCropperHelper? = null

            val cropLauncher = fragment.registerForActivityResult(FlirtTimeCropContract()) { result ->
                onResult(result)
            }

            val pickGalleryLauncher = fragment.registerForActivityResult(
                ActivityResultContracts.PickVisualMedia()
            ) { uri ->
                if (uri != null) {
                    val config = helper?.pendingConfig ?: CropConfig()
                    cropLauncher.launch(CropRequest(sourceUri = uri, config = config))
                } else {
                    onResult(CropResult.Cancelled)
                }
            }

            val takeCameraLauncher = fragment.registerForActivityResult(
                ActivityResultContracts.TakePicture()
            ) { success ->
                val cameraUri = helper?.pendingCameraUri
                if (success && cameraUri != null) {
                    val config = helper?.pendingConfig ?: CropConfig()
                    cropLauncher.launch(CropRequest(sourceUri = cameraUri, config = config))
                } else {
                    onResult(CropResult.Cancelled)
                }
            }

            val instance = ImageCropperHelper(
                cropLauncher = cropLauncher,
                pickGalleryLauncher = pickGalleryLauncher,
                takeCameraLauncher = takeCameraLauncher,
                contextProvider = { fragment.requireContext() }
            )
            helper = instance
            return instance
        }
    }

    /**
     * Launch custom cropper directly for a pre-selected image URI.
     */
    fun launchCrop(
        sourceUri: Uri,
        config: CropConfig = CropConfig()
    ) {
        pendingConfig = config
        cropLauncher.launch(CropRequest(sourceUri = sourceUri, config = config))
    }

    /**
     * Open system Photo Picker and smoothly route the selected photo into [CropActivity].
     */
    fun launchGallery(config: CropConfig = CropConfig()) {
        pendingConfig = config
        pickGalleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    /**
     * Launch system Camera to capture a photo, then immediately route into [CropActivity].
     */
    fun launchCamera(config: CropConfig = CropConfig()) {
        pendingConfig = config
        val context = contextProvider()
        try {
            val cameraFolder = File(context.cacheDir, "camera_captures").apply { mkdirs() }
            val photoFile = File(cameraFolder, "capture_${System.currentTimeMillis()}.jpg")
            if (!photoFile.exists()) {
                photoFile.createNewFile()
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            pendingCameraUri = uri
            takeCameraLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Unable to launch camera: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
