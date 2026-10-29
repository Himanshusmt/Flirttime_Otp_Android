package com.flirttime.core.image

import android.net.Uri

/**
 * Supported crop shapes.
 */
enum class CropShape {
    RECTANGLE,
    OVAL
}

/**
 * Standard aspect ratio specifications for cropping across screens.
 */
sealed interface CropAspectRatio {
    data object Freeform : CropAspectRatio
    data object Square : CropAspectRatio              // 1:1 (Avatar, Profile Picture)
    data object Portrait4_5 : CropAspectRatio         // 4:5 (Moments post, Instagram portrait)
    data object Portrait3_4 : CropAspectRatio         // 3:4 (Profile card stack)
    data object Landscape16_9 : CropAspectRatio       // 16:9 (Cover photo)
    data class Custom(val ratioX: Int, val ratioY: Int) : CropAspectRatio
}

/**
 * Configuration options for image cropping operations.
 */
data class CropConfig(
    val shape: CropShape = CropShape.RECTANGLE,
    val aspectRatio: CropAspectRatio = CropAspectRatio.Square,
    val title: String? = null,
    val guidelines: Boolean = true,
    val maxOutputWidth: Int = 1080,
    val maxOutputHeight: Int = 1080,
    val compressionQuality: Int = 85,
    val includeCamera: Boolean = true,
    val includeGallery: Boolean = true,
    val allowRotation: Boolean = true,
    val allowFlipping: Boolean = true,
    val customOutputUri: Uri? = null
) {
    companion object {
        /**
         * Preset for user profile avatars (1:1 square with oval/circle overlay).
         */
        fun profileAvatar(circular: Boolean = true): CropConfig = CropConfig(
            shape = if (circular) CropShape.OVAL else CropShape.RECTANGLE,
            aspectRatio = CropAspectRatio.Square,
            title = "Crop Profile Photo",
            maxOutputWidth = 1024,
            maxOutputHeight = 1024,
            compressionQuality = 90
        )

        /**
         * Preset for Moments feed posts (as displayed in customize image crop screen).
         */
        fun momentPost(): CropConfig = CropConfig(
            shape = CropShape.RECTANGLE,
            aspectRatio = CropAspectRatio.Custom(4, 3),
            title = "Crop photo",
            maxOutputWidth = 1200,
            maxOutputHeight = 1200,
            compressionQuality = 90
        )

        /**
         * Preset for ID / Profile gesture verification photo.
         */
        fun verificationPhoto(): CropConfig = CropConfig(
            shape = CropShape.RECTANGLE,
            aspectRatio = CropAspectRatio.Portrait3_4,
            title = "Crop Verification Photo",
            maxOutputWidth = 1200,
            maxOutputHeight = 1600,
            compressionQuality = 90
        )

        /**
         * Preset for Cover Photo (16:9 landscape aspect ratio).
         */
        fun coverPhoto(title: String = "Crop Cover Photo"): CropConfig = CropConfig(
            shape = CropShape.RECTANGLE,
            aspectRatio = CropAspectRatio.Landscape16_9,
            title = title,
            maxOutputWidth = 1920,
            maxOutputHeight = 1080,
            compressionQuality = 90
        )

        /**
         * Freeform unconstrained crop.
         */
        fun freeform(title: String = "Crop Image"): CropConfig = CropConfig(
            shape = CropShape.RECTANGLE,
            aspectRatio = CropAspectRatio.Freeform,
            title = title,
            compressionQuality = 85
        )
    }
}

/**
 * Request object for the Crop ActivityResultContract.
 */
data class CropRequest(
    val sourceUri: Uri? = null,
    val sourceUris: List<Uri> = emptyList(),
    val config: CropConfig = CropConfig()
)

/**
 * Result of a crop operation.
 */
sealed class CropResult {
    data class Success(
        val originalUri: Uri?,
        val croppedUri: Uri,
        val croppedUris: List<Uri> = listOf(croppedUri),
        val filePath: String? = null
    ) : CropResult()

    data object Cancelled : CropResult()

    data class Failure(
        val error: Throwable?
    ) : CropResult()
}
