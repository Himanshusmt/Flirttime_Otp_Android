package com.flirttime.feature.onboarding

import com.google.gson.annotations.SerializedName

data class GestureVerificationDto(
    @SerializedName("verificationId") val verificationId: String? = null,
    @SerializedName("verificationType") val verificationType: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("faceDetected") val faceDetected: Boolean? = null,
    @SerializedName("reasonMessage") val reasonMessage: String? = null,
    @SerializedName("rejectionReason") val rejectionReason: String? = null,
    @SerializedName("reasonCode") val reasonCode: String? = null,
    @SerializedName("currentStep") val currentStep: String? = null,
    @SerializedName("isProfileComplete") val isProfileComplete: Boolean? = null,
    @SerializedName("isFaceVerified") val isFaceVerified: Boolean? = null
) {
    val isApproved: Boolean
        get() = status.equals("approved", ignoreCase = true)

    val failureMessage: String
        get() = reasonMessage?.takeIf { it.isNotBlank() }
            ?: rejectionReason?.takeIf { it.isNotBlank() }
            ?: if (faceDetected == false) "No face detected. Please try again." else "Verification failed. Please try again."

    fun didPass(): Boolean {
        val step = OnboardingStep.resolve(
            currentStep = currentStep,
            isProfileComplete = null,
            fallback = if (isApproved) OnboardingStep.COMPLETED else OnboardingStep.GESTURE
        )
        return step == OnboardingStep.COMPLETED
    }
}
