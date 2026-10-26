package com.flirttime.feature.main.presentation.matches

object MockMatchProfileProvider {

    fun getMockProfiles(): List<MatchProfile> {
        return listOf(
            MatchProfile(
                id = "1",
                name = "Nisha",
                age = 28,
                isVerified = true,
                location = "Hyderabad",
                momentsCount = 61,
                matchPercentage = 83,
                relationshipGoal = "Long-term",
                sharedInterestsCount = 2,
                interests = listOf(
                    InterestItem(emoji = "💪", label = "Fitness", isShared = true),
                    InterestItem(emoji = "🐾", label = "Pets", isShared = true),
                    InterestItem(emoji = "💃", label = "Dance", isShared = false)
                ),
                imageUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=1080&fit=crop"
            ),
            MatchProfile(
                id = "2",
                name = "Priya",
                age = 25,
                isVerified = true,
                location = "Bengaluru",
                momentsCount = 42,
                matchPercentage = 91,
                relationshipGoal = "Dating",
                sharedInterestsCount = 4,
                interests = listOf(
                    InterestItem(emoji = "☕", label = "Coffee"),
                    InterestItem(emoji = "✈️", label = "Travel"),
                    InterestItem(emoji = "🎵", label = "Music")
                ),
                imageUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=1080&fit=crop"
            ),
            MatchProfile(
                id = "3",
                name = "Ananya",
                age = 26,
                isVerified = true,
                location = "Mumbai",
                momentsCount = 78,
                matchPercentage = 88,
                relationshipGoal = "Long-term",
                sharedInterestsCount = 3,
                interests = listOf(
                    InterestItem(emoji = "📚", label = "Reading"),
                    InterestItem(emoji = "🎨", label = "Art"),
                    InterestItem(emoji = "🍕", label = "Foodie")
                ),
                imageUrl = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=1080&fit=crop"
            ),
            MatchProfile(
                id = "4",
                name = "Riya",
                age = 24,
                isVerified = true,
                location = "Delhi",
                momentsCount = 55,
                matchPercentage = 95,
                relationshipGoal = "Casual",
                sharedInterestsCount = 5,
                interests = listOf(
                    InterestItem(emoji = "📸", label = "Photography"),
                    InterestItem(emoji = "🎬", label = "Movies"),
                    InterestItem(emoji = "🧘", label = "Yoga")
                ),
                imageUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=1080&fit=crop"
            )
        )
    }
}
