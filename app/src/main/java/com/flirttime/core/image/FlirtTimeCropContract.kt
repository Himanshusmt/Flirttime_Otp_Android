package com.flirttime.core.image

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/**
 * Custom ActivityResultContract for FlirtTime's dedicated [CropActivity].
 * Passes [CropConfig] parameters and retrieves the cropped [Uri] or list of [Uri]s safely.
 */
class FlirtTimeCropContract : ActivityResultContract<CropRequest, CropResult>() {

    override fun createIntent(context: Context, input: CropRequest): Intent {
        val uris = if (input.sourceUris.isNotEmpty()) {
            input.sourceUris
        } else if (input.sourceUri != null) {
            listOf(input.sourceUri)
        } else {
            throw IllegalArgumentException("Source URI(s) must not be empty for CropActivity")
        }
        return CropActivity.createMultipleIntent(context, uris, input.config)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): CropResult {
        if (resultCode == Activity.RESULT_CANCELED || intent == null) {
            return CropResult.Cancelled
        }

        val croppedUris = androidx.core.content.IntentCompat.getParcelableArrayListExtra(
            intent,
            CropActivity.EXTRA_CROPPED_URIS,
            Uri::class.java
        )

        val singleUri = androidx.core.content.IntentCompat.getParcelableExtra(
            intent,
            CropActivity.EXTRA_CROPPED_URI,
            Uri::class.java
        ) ?: croppedUris?.firstOrNull()

        return if (singleUri != null) {
            CropResult.Success(
                originalUri = null,
                croppedUri = singleUri,
                croppedUris = croppedUris ?: listOf(singleUri),
                filePath = null
            )
        } else {
            CropResult.Failure(IllegalStateException("Cropped image URI was null"))
        }
    }
}
