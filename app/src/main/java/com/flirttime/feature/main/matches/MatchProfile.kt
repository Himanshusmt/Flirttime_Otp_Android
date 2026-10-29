package com.flirttime.feature.main.matches

data class MatchProfile(
    val id: String,
    val name: String,
    val age: Int,
    val isVerified: Boolean = true,
    val location: String,
    val momentsCount: Int,
    val matchPercentage: Int,
    val relationshipGoal: String,
    val sharedInterestsCount: Int,
    val interests: List<InterestItem>,
    val imageUrl: String? = null,
    val imageResId: Int? = null
)

data class InterestItem(
    val emoji: String,
    val label: String,
    val isShared: Boolean = false
)
