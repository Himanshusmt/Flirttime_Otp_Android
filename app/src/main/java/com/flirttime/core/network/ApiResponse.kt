package com.flirttime.core.network

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("status") val status: Any? = null,
    @SerializedName("statusCode") val statusCode: Int? = null,
    @SerializedName("code") val code: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: T? = null,
    @SerializedName("error") val error: Any? = null,
    @SerializedName("errors") val errors: List<String>? = null
) {
    val isSuccess: Boolean
        get() {
            if (success == true) return true
            when (status) {
                is Boolean -> if (status) return true
                is Number -> if (status.toInt() in 200..299) return true
                is String -> if (status.equals("true", ignoreCase = true) || status.equals("success", ignoreCase = true)) return true
            }
            if (statusCode in 200..299) return true
            if (code in 200..299) return true
            return (data != null || message != null) && error == null && errors.isNullOrEmpty()
        }
}

