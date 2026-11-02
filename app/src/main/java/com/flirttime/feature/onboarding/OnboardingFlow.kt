package com.flirttime.feature.onboarding

import com.flirttime.core.navigation.Destination
import com.google.gson.annotations.SerializedName

enum class OnboardingStep(val raw: String) {
    BASIC_INFO("basicInfo"),
    PHOTOS("photos"),
    GESTURE("gesture"),
    COMPLETED("completed");

    companion object {
        fun from(raw: String?): OnboardingStep? {
            val value = raw?.trim().orEmpty()
            if (value.isEmpty()) return null
            return entries.firstOrNull { it.raw.equals(value, ignoreCase = true) }
        }

        fun resolve(
            currentStep: String?,
            isProfileComplete: Boolean?,
            fallback: OnboardingStep = BASIC_INFO
        ): OnboardingStep {
            from(currentStep)?.let { return it }
            if (isProfileComplete == true) return COMPLETED
            return fallback
        }
    }
}

data class OnboardingProgress(
    val currentStep: String? = null,
    val isProfileComplete: Boolean? = null
) {
    val resolvedStep: OnboardingStep
        get() = OnboardingStep.resolve(currentStep, isProfileComplete, OnboardingStep.BASIC_INFO)

    val destination: Destination
        get() = OnboardingFlow.destination(resolvedStep)
}

data class OnboardingProgressUser(
    @SerializedName("currentStep") val currentStep: String? = null,
    @SerializedName("isProfileComplete") val isProfileComplete: Boolean? = null
)

data class OnboardingProgressData(
    @SerializedName("currentStep") val currentStep: String? = null,
    @SerializedName("isProfileComplete") val isProfileComplete: Boolean? = null,
    @SerializedName("user") val user: OnboardingProgressUser? = null
) {
    fun toProgress(): OnboardingProgress {
        val step = currentStep?.trim()?.takeIf { it.isNotEmpty() }
            ?: user?.currentStep?.trim()?.takeIf { it.isNotEmpty() }
        val complete = isProfileComplete ?: user?.isProfileComplete
        return OnboardingProgress(currentStep = step, isProfileComplete = complete)
    }
}

data class OnboardingStepRequest(
    @SerializedName("step") val step: String
)

object OnboardingFlow {
    fun destination(step: OnboardingStep): Destination = when (step) {
        OnboardingStep.COMPLETED -> Destination.Main()
        OnboardingStep.PHOTOS -> Destination.ProfileVerification
        OnboardingStep.GESTURE -> Destination.GestureVerification
        OnboardingStep.BASIC_INFO -> Destination.IntroduceYourself
    }
}
