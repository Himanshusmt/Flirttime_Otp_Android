package com.flirttime.feature.profile.data.model

import com.google.gson.annotations.SerializedName

data class UpdateProfileRequest(
    @SerializedName("firstName") val firstName: String,
    @SerializedName("lastName") val lastName: String = "",
    @SerializedName("nickName") val nickName: String,
    @SerializedName("dateOfBirth") val dateOfBirth: String,
    @SerializedName("gender") val gender: String,
    @SerializedName("location") val location: String? = null,
    @SerializedName("about") val about: String
)
