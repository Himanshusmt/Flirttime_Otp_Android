package com.flirttime.feature.search.data

import com.flirttime.feature.main.presentation.explore.model.ExploreProfileItem

object MockSearchDataProvider {

    private val allProfiles = listOf(
        ExploreProfileItem(
            id = "search_1",
            username = "tushar",
            age = 19,
            hasRibbonBadge = true,
            distance = null,
            location = "Indore, Madhya Pradesh, I...",
            imageUrl = "https://images.unsplash.com/photo-1635805737707-575885ab0820?w=800&fit=crop",
            isPlaceholder = false
        ),
        ExploreProfileItem(
            id = "search_2",
            username = "tushar12344",
            age = 18,
            hasRibbonBadge = true,
            distance = "7 km away",
            location = "Mumbai",
            imageUrl = "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=800&fit=crop",
            isPlaceholder = false
        ),
        ExploreProfileItem(
            id = "search_3",
            username = "dummy_armaan",
            age = 27,
            hasRibbonBadge = true,
            distance = null,
            location = "Mumbai, Maharashtra",
            imageUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=800&fit=crop",
            isPlaceholder = false
        ),
        ExploreProfileItem(
            id = "search_4",
            username = "dummy_rishabh",
            age = 28,
            hasRibbonBadge = true,
            distance = null,
            location = "Delhi, NCR",
            imageUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=800&fit=crop",
            isPlaceholder = false
        ),
        ExploreProfileItem(
            id = "search_5",
            username = "aasif123",
            age = 18,
            hasRibbonBadge = true,
            distance = "7 km away",
            location = "Mumbai",
            imageUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=800&fit=crop",
            isPlaceholder = false
        ),
        ExploreProfileItem(
            id = "search_6",
            username = "kabir_singh",
            age = 26,
            hasRibbonBadge = true,
            distance = "12 km away",
            location = "Bangalore, Karnataka",
            imageUrl = "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=800&fit=crop",
            isPlaceholder = false
        ),
        ExploreProfileItem(
            id = "search_7",
            username = "ananya_sharma",
            age = 23,
            hasRibbonBadge = true,
            distance = "3 km away",
            location = "Pune, Maharashtra",
            imageUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=800&fit=crop",
            isPlaceholder = false
        )
    )

    fun searchProfiles(query: String): List<ExploreProfileItem> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) {
            return allProfiles.take(2)
        }
        return allProfiles.filter {
            it.username.lowercase().contains(trimmed) ||
                    it.location.lowercase().contains(trimmed)
        }
    }
}
