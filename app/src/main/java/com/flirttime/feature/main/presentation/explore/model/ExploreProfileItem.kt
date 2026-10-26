package com.flirttime.feature.main.presentation.explore.model

/**
 * Represents a profile card item in the Explore 2x2 grid.
 */
data class ExploreProfileItem(
    val id: String,
    val username: String,
    val age: Int? = null,
    val hasRibbonBadge: Boolean = true,
    val distance: String? = null,
    val location: String,
    val imageUrl: String? = null,
    val isPlaceholder: Boolean = false
)
