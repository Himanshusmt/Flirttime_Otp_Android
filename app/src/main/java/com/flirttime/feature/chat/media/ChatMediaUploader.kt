package com.flirttime.feature.chat.media

import com.flirttime.Resource
import com.flirttime.feature.chat.ChatApiService
import com.flirttime.feature.chat.model.ChatMediaUpload
import com.flirttime.feature.chat.model.MediaUploadRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Uploads a chat attachment the way the iOS client does:
 * `POST /api/v1/media/uploads` → `PUT <presigned url>` → the storage `key` becomes `mediaId`.
 *
 * A bare [OkHttpClient] is used on purpose: the shared client injects the API bearer token and
 * an offline-cache interceptor, which must never be sent to S3 (it breaks the presigned signature).
 */
class ChatMediaUploader(
    private val api: ChatApiService
) {

    private val uploadClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun upload(
        kind: String,
        contentType: String,
        fileName: String,
        bytes: ByteArray
    ): Resource<ChatMediaUpload> = withContext(Dispatchers.IO) {
        try {
            val response = api.createMediaUpload(
                MediaUploadRequest(
                    kind = kind,
                    contentType = contentType,
                    mimeType = contentType,
                    fileName = fileName,
                    filename = fileName,
                    byteSize = bytes.size.toLong(),
                    sizeBytes = bytes.size.toLong()
                )
            )

            if (response.success == false) {
                return@withContext Resource.Error(response.message ?: "Could not start the upload")
            }

            val session = response.resolvedSession()
                ?: return@withContext Resource.Error(response.message ?: "Upload session missing")

            val uploadUrl = session.resolvedUploadUrl()
                ?: return@withContext Resource.Error("Upload URL missing from media/uploads")

            val resolvedContentType = session.resolvedContentType() ?: contentType
            val body = bytes.toRequestBody(resolvedContentType.toMediaTypeOrNull())
            val builder = Request.Builder()
                .url(uploadUrl)
                .header("Content-Type", resolvedContentType)

            val request = if (session.resolvedUploadMethod() == "POST") {
                builder.post(body).build()
            } else {
                builder.put(body).build()
            }

            uploadClient.newCall(request).execute().use { putResponse ->
                if (!putResponse.isSuccessful) {
                    val errorBody = runCatching { putResponse.body?.string() }.getOrNull()
                    val detail = errorBody?.take(INLINE_ERROR_LIMIT)?.let { ": $it" }.orEmpty()
                    return@withContext Resource.Error(
                        "Media upload failed (HTTP ${putResponse.code})$detail"
                    )
                }
            }

            val mediaId = session.resolvedMediaId()
                ?: return@withContext Resource.Error("Media id missing from media/uploads")

            Resource.Success(
                ChatMediaUpload(
                    mediaId = mediaId,
                    publicUrl = session.resolvedPublicUrl(),
                    thumbnailUrl = session.resolvedThumbnailUrl()
                )
            )
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Media upload failed", cause = e)
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 30L
        const val UPLOAD_TIMEOUT_SECONDS = 120L
        const val INLINE_ERROR_LIMIT = 120
    }
}
