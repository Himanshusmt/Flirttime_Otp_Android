package com.flirttime.feature.main.explore

/**
 * Represents a profile card item in the Explore 2x2 grid, matching iOS UserInteractionCVC.
 */
data class ExploreProfileItem(
    val id: String,
    val interactionId: String? = null,
    val username: String,
    val age: Int? = null,
    val hasRibbonBadge: Boolean = true,
    val isVerified: Boolean = false,
    val isOnline: Boolean = false,
    val isSuperLike: Boolean = false,
    val isMatched: Boolean = false,
    val distance: String? = null,
    val location: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val isLocked: Boolean = false,
    val isPlaceholder: Boolean = false
)
