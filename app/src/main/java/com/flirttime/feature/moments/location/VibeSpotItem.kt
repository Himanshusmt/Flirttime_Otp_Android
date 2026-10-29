package com.flirttime.feature.moments.location

import androidx.annotation.DrawableRes

enum class SpotCategory {
    ALL,
    RESTAURANT,
    CAFE,
    DRINKS,
    CINEMA,
    OUTDOORS
}

data class VibeSpotItem(
    val id: String,
    val name: String,
    val address: String,
    val distance: String,
    val category: SpotCategory,
    @field:DrawableRes val iconRes: Int,
    @field:DrawableRes val backgroundRes: Int
)
