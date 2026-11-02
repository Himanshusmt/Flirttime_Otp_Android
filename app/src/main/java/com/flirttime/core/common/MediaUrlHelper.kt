package com.flirttime.core.common

object MediaUrlHelper {

    const val S3_MEDIA_BASE_URL = "https://flirttime.s3.ap-south-1.amazonaws.com/"

    /**
     * Resolves all alternative URLs for an image path to guarantee loading:
     * 1. If it's a presigned URL with query params (X-Amz-Signature), the signed URL is preferred,
     *    with the clean unsigned full-size URL as a fallback if the S3 bucket allows public access.
     * 2. If it contains '/thumbnails/', the full-size URL (stripping '/thumbnails/') is preferred
     *    because S3 thumbnail endpoints respond with 403 Forbidden (matching iOS ImageLoader),
     *    with the original thumbnail as a fallback.
     * 3. If relative, prepends the S3 base URL.
     */
    fun getAlternativeUrls(pathOrUrl: String?): List<String> {
        if (pathOrUrl.isNullOrBlank()) return emptyList()
        val trimmed = pathOrUrl.trim()
        val fullUrl = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else {
            S3_MEDIA_BASE_URL + trimmed.removePrefix("/")
        }

        val results = mutableListOf<String>()
        val isPresigned = fullUrl.contains("X-Amz-Signature", ignoreCase = true) ||
                fullUrl.contains("AWSAccessKeyId", ignoreCase = true)

        if (isPresigned) {
            // 1. Presigned URL as-is (path must match HMAC signature)
            results.add(fullUrl)
            // 2. Public full-size fallback (in case thumbnail 403s and full-size is public)
            val cleanFullSize = fullUrl.substringBefore("?").replace("/thumbnails/", "/")
            if (cleanFullSize != fullUrl) {
                results.add(cleanFullSize)
            }
        } else if (fullUrl.contains("/thumbnails/")) {
            // 1. Full-size photo first (bypasses S3 403 Forbidden on thumbnail endpoints)
            results.add(fullUrl.replace("/thumbnails/", "/"))
            // 2. Original thumbnail URL as fallback
            results.add(fullUrl)
        } else {
            results.add(fullUrl)
        }

        return results.distinct()
    }

    /**
     * Resolves the primary URL for an image path or URL.
     */
    fun resolveMediaUrl(pathOrUrl: String?): String? {
        return getAlternativeUrls(pathOrUrl).firstOrNull()
    }
}
