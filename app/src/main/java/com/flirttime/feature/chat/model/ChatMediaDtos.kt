package com.flirttime.feature.chat.model

import com.google.gson.annotations.SerializedName

/**
 * Wire models for `POST /api/v1/media/uploads` (chat attachment uploads).
 *
 * The endpoint hands back a presigned PUT target; the storage `key` is what the chat
 * API expects in `mediaIds` — exactly what the iOS client does (`uploaded.key`).
 */
data class MediaUploadRequest(
    @SerializedName("purpose") val purpose: String = "chat",
    @SerializedName("kind") val kind: String,
    @SerializedName("contentType") val contentType: String,
    @SerializedName("mimeType") val mimeType: String,
    @SerializedName("fileName") val fileName: String,
    @SerializedName("filename") val filename: String,
    @SerializedName("byteSize") val byteSize: Long,
    @SerializedName("sizeBytes") val sizeBytes: Long
)

data class MediaUploadHeadersDto(
    @SerializedName(value = "contentType", alternate = ["Content-Type", "content-type"])
    val contentType: String? = null
)

data class MediaUploadTargetDto(
    @SerializedName("url") val url: String? = null,
    @SerializedName("method") val method: String? = null,
    @SerializedName("headers") val headers: MediaUploadHeadersDto? = null
)

data class MediaUploadMediaDto(
    @SerializedName(value = "mediaId", alternate = ["id"]) val mediaId: String? = null,
    @SerializedName("key") val key: String? = null,
    @SerializedName(value = "publicUrl", alternate = ["url"]) val publicUrl: String? = null,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String? = null
)

data class MediaUploadSessionDto(
    @SerializedName(value = "mediaId", alternate = ["id"]) val mediaId: String? = null,
    @SerializedName("key") val key: String? = null,
    @SerializedName("imageKey") val imageKey: String? = null,
    @SerializedName("publicUrl") val publicUrl: String? = null,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerializedName("uploadUrl") val uploadUrl: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("strategy") val strategy: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("headers") val headers: MediaUploadHeadersDto? = null,
    @SerializedName("upload") val upload: MediaUploadTargetDto? = null,
    @SerializedName("media") val media: MediaUploadMediaDto? = null,
    @SerializedName("expiresIn") val expiresIn: Int? = null
) {
    fun resolvedUploadUrl(): String? =
        listOfNotNull(upload?.url, uploadUrl, url).firstOrNull { it.isNotBlank() }

    fun resolvedUploadMethod(): String =
        upload?.method?.takeIf { it.isNotBlank() }?.uppercase() ?: "PUT"

    fun resolvedContentType(): String? =
        listOfNotNull(upload?.headers?.contentType, headers?.contentType)
            .firstOrNull { it.isNotBlank() }

    /** Storage key — this is the value the chat API treats as `mediaId`. */
    fun resolvedMediaId(): String? =
        listOfNotNull(key, imageKey, media?.key, mediaId, media?.mediaId)
            .firstOrNull { it.isNotBlank() }

    fun resolvedPublicUrl(): String? =
        listOfNotNull(publicUrl, media?.publicUrl).firstOrNull { it.isNotBlank() }

    fun resolvedThumbnailUrl(): String? =
        listOfNotNull(thumbnailUrl, media?.thumbnailUrl).firstOrNull { it.isNotBlank() }
}

data class MediaUploadResponseDto(
    // Kept strict on purpose: some deployments answer `status: "pending"` here,
    // which Gson cannot coerce to Boolean.
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: MediaUploadSessionDto? = null,
    // Some deployments answer flat instead of wrapping in `data`.
    @SerializedName("mediaId") val mediaId: String? = null,
    @SerializedName("key") val key: String? = null,
    @SerializedName("publicUrl") val publicUrl: String? = null,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerializedName("uploadUrl") val uploadUrl: String? = null
) {
    fun resolvedSession(): MediaUploadSessionDto? {
        data?.let { return it }
        if (mediaId == null && key == null && uploadUrl == null && publicUrl == null) return null
        return MediaUploadSessionDto(
            mediaId = mediaId,
            key = key,
            publicUrl = publicUrl,
            thumbnailUrl = thumbnailUrl,
            uploadUrl = uploadUrl
        )
    }
}

/** Successful chat attachment upload. */
data class ChatMediaUpload(
    val mediaId: String,
    val publicUrl: String?,
    val thumbnailUrl: String?
)
