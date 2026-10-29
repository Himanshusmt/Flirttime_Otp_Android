package com.flirttime.core.common

object MediaUrlHelper {

    const val S3_MEDIA_BASE_URL = "https://flirttime.s3.ap-south-1.amazonaws.com/"

    /**
     * Resolves a media/image path or URL:
     * - If null or blank, returns null.
     * - If starts with "http://" or "https://", returns as-is.
     * - Otherwise prepends the S3 base URL.
     */
    fun resolveMediaUrl(pathOrUrl: String?): String? {
        if (pathOrUrl.isNullOrBlank()) return null
        val trimmed = pathOrUrl.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        val cleanPath = trimmed.removePrefix("/")
        return S3_MEDIA_BASE_URL + cleanPath
    }
}
