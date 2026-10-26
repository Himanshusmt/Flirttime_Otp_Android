package com.flirttime.feature.main.presentation.matches.interest

data class InterestPersonItem(
    val id: String,
    val name: String,
    val city: String,
    val imageUrl: String,
    val isVerified: Boolean = true,
    val interestId: String = "art"
)
