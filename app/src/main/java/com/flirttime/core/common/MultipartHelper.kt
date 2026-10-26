package com.flirttime.core.common

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object MultipartHelper {

    fun createPartFromFile(file: File, partName: String): MultipartBody.Part {
        val mediaType = getMimeType(file).toMediaTypeOrNull()
        val requestBody = file.asRequestBody(mediaType)
        return MultipartBody.Part.createFormData(partName, file.name, requestBody)
    }

    fun createPartFromUri(context: Context, uri: Uri, partName: String): MultipartBody.Part? {
        val file = getFileFromUri(context, uri) ?: return null
        return createPartFromFile(file, partName)
    }

    fun createTextPart(value: String): RequestBody {
        return value.toRequestBody("text/plain".toMediaTypeOrNull())
    }

    private fun getFileFromUri(context: Context, uri: Uri): File? {
        return try {
            if (uri.scheme == "file") {
                uri.path?.let { File(it) }
            } else {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri)
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
                val tempFile = File(context.cacheDir, "upload_temp_${System.currentTimeMillis()}.$extension")
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val outputStream = FileOutputStream(tempFile)
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getMimeType(file: File): String {
        val extension = file.extension
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "image/jpeg"
    }
}
