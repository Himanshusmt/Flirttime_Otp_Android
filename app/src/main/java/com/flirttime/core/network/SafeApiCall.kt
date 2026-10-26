package com.flirttime.core.network

import com.flirttime.Resource
import com.flirttime.core.network.model.ApiResponse
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

suspend fun <T> safeApiCall(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    apiCall: suspend () -> ApiResponse<T>
): Resource<T> {
    return withContext(dispatcher) {
        try {
            val response = apiCall()
            if (response.success && response.data != null) {
                Resource.Success(response.data)
            } else if (response.success && response.data == null) {
                @Suppress("UNCHECKED_CAST")
                Resource.Success(Unit as T)
            } else {
                val errorMsg = response.message ?: response.error ?: "Operation failed"
                Resource.Error(
                    message = errorMsg,
                    errorBody = response.error
                )
            }
        } catch (e: HttpException) {
            val code = e.code()
            val errorBodyString = e.response()?.errorBody()?.string()
            val parsedMessage = parseErrorMessage(errorBodyString) ?: "Server error ($code): ${e.message()}"
            Resource.Error(
                message = parsedMessage,
                code = code,
                cause = e,
                errorBody = errorBodyString
            )
        } catch (e: SocketTimeoutException) {
            Resource.Error(
                message = "Connection timed out. Please try again.",
                cause = e
            )
        } catch (e: UnknownHostException) {
            Resource.Error(
                message = "Unable to reach server. Please check your internet connection.",
                cause = e
            )
        } catch (e: IOException) {
            Resource.Error(
                message = "Network error. Please check your internet connection.",
                cause = e
            )
        } catch (e: Exception) {
            Resource.Error(
                message = e.localizedMessage ?: "An unexpected error occurred",
                cause = e
            )
        }
    }
}

suspend fun <T> safeRawApiCall(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    apiCall: suspend () -> T
): Resource<T> {
    return withContext(dispatcher) {
        try {
            val result = apiCall()
            Resource.Success(result)
        } catch (e: HttpException) {
            val code = e.code()
            val errorBodyString = e.response()?.errorBody()?.string()
            val parsedMessage = parseErrorMessage(errorBodyString) ?: "Server error ($code): ${e.message()}"
            Resource.Error(
                message = parsedMessage,
                code = code,
                cause = e,
                errorBody = errorBodyString
            )
        } catch (e: SocketTimeoutException) {
            Resource.Error(
                message = "Connection timed out. Please try again.",
                cause = e
            )
        } catch (e: UnknownHostException) {
            Resource.Error(
                message = "Unable to reach server. Please check your internet connection.",
                cause = e
            )
        } catch (e: IOException) {
            Resource.Error(
                message = "Network error. Please check your internet connection.",
                cause = e
            )
        } catch (e: Exception) {
            Resource.Error(
                message = e.localizedMessage ?: "An unexpected error occurred",
                cause = e
            )
        }
    }
}

private fun parseErrorMessage(errorBody: String?): String? {
    if (errorBody.isNullOrBlank()) return null
    return try {
        val json = JsonParser.parseString(errorBody).asJsonObject
        val message = if (json.has("message") && !json.get("message").isJsonNull) {
            json.get("message").asString
        } else null

        val errorElement = json.get("error")
        if (errorElement != null && !errorElement.isJsonNull) {
            if (errorElement.isJsonArray) {
                val array = errorElement.asJsonArray
                val messages = mutableListOf<String>()
                for (elem in array) {
                    if (elem.isJsonObject) {
                        val obj = elem.asJsonObject
                        if (obj.has("message") && !obj.get("message").isJsonNull) {
                            messages.add(obj.get("message").asString)
                        }
                    } else if (elem.isJsonPrimitive) {
                        messages.add(elem.asString)
                    }
                }
                if (messages.isNotEmpty()) {
                    return messages.joinToString("\n")
                }
            } else if (errorElement.isJsonPrimitive) {
                return errorElement.asString
            }
        }
        message
    } catch (_: Exception) {
        null
    }
}
