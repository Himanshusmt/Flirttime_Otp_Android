package com.flirttime.feature.main.presentation.explore

import com.flirttime.feature.main.presentation.explore.model.ExploreProfileItem

object MockExploreDataProvider {

    fun getExploreProfiles(): List<ExploreProfileItem> {
        return listOf(
            ExploreProfileItem(
                id = "1",
                username = "dummy_armaan",
                age = 27,
                hasRibbonBadge = true,
                distance = null,
                location = "Mumbai, Maharashtra",
                imageUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=800&fit=crop",
                isPlaceholder = false
            ),
            ExploreProfileItem(
                id = "2",
                username = "dummy_rishabh",
                age = 28,
                hasRibbonBadge = true,
                distance = null,
                location = "Delhi, NCR",
                imageUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=800&fit=crop",
                isPlaceholder = false
            ),
            ExploreProfileItem(
                id = "3",
                username = "aasif123",
                age = 18,
                hasRibbonBadge = true,
                distance = "7 km away",
                location = "Mumbai",
                imageUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=800&fit=crop",
                isPlaceholder = false
            ),
            ExploreProfileItem(
                id = "4",
                username = "testuser__1791867494770",
                age = null,
                hasRibbonBadge = false,
                distance = "7 km away",
                location = "Mumbai",
                imageUrl = null,
                isPlaceholder = true
            ),
            ExploreProfileItem(
                id = "5",
                username = "kabir_singh",
                age = 26,
                hasRibbonBadge = true,
                distance = "12 km away",
                location = "Bangalore, Karnataka",
                imageUrl = "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=800&fit=crop",
                isPlaceholder = false
            ),
            ExploreProfileItem(
                id = "6",
                username = "ananya_sharma",
                age = 23,
                hasRibbonBadge = true,
                distance = "3 km away",
                location = "Pune, Maharashtra",
                imageUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=800&fit=crop",
                isPlaceholder = false
            )
        )
    }
}
