package com.flirttime.feature.main.matches

import com.flirttime.R
import com.flirttime.core.common.MediaUrlHelper
import com.flirttime.feature.main.matches.interest.InterestPersonItem
import com.flirttime.feature.profile.DiscoveryUserDto

fun DiscoveryUserDto.toMatchProfile(): MatchProfile {
    val photo = MediaUrlHelper.resolveMediaUrl(effectivePhoto)
    val distance = distanceText ?: distanceKm?.let { "${it.toInt()} km away" }
    val loc = if (!city.isNullOrBlank() && !distance.isNullOrBlank()) {
        "$city · $distance"
    } else {
        city ?: distance ?: "Nearby"
    }

    val matchPct = compatibility?.interestMatchPercentage?.takeIf { it > 0 }
        ?: vibeScore?.takeIf { it > 0 }
        ?: 85

    val interestItems = mutableListOf<InterestItem>()
    val shared = sharedInterests.orEmpty().filter { it.isNotBlank() }
    val tags = interestTags.orEmpty().filter { it.isNotBlank() }

    // 1. Shared interests (strings like "Coffee", "Travelling")
    shared.forEach { interestName ->
        interestItems.add(
            InterestItem(
                emoji = getEmojiForInterestTag(interestName),
                label = interestName,
                isShared = true
            )
        )
    }

    // 2. Add remaining interest tags if any
    tags.forEach { tag ->
        if (interestItems.none { it.label.equals(tag, ignoreCase = true) }) {
            interestItems.add(
                InterestItem(
                    emoji = getEmojiForInterestTag(tag),
                    label = tag,
                    isShared = false
                )
            )
        }
    }

    val goal = lookingFor?.takeIf { it.isNotBlank() }?.let {
        when (it.lowercase()) {
            "long_term" -> "Long-term relationship"
            "casual" -> "Casual dating"
            "not_sure" -> "Not sure yet"
            else -> it.replace("_", " ").replaceFirstChar { c -> c.uppercase() }
        }
    } ?: "Dating"

    return MatchProfile(
        id = effectiveId,
        name = effectiveName,
        age = age ?: 20,
        isVerified = isVerified ?: false,
        location = loc,
        momentsCount = totalMoments ?: momentCount ?: 0,
        matchPercentage = matchPct,
        relationshipGoal = goal,
        sharedInterestsCount = shared.size,
        interests = interestItems,
        imageUrl = photo?.ifEmpty { null },
        imageResId = R.drawable.img_login
    )
}

fun DiscoveryUserDto.toInterestPersonItem(selectedInterestId: String): InterestPersonItem {
    return InterestPersonItem(
        id = effectiveId,
        name = effectiveName,
        city = city ?: "Nearby",
        imageUrl = MediaUrlHelper.resolveMediaUrl(effectivePhoto).orEmpty(),
        isVerified = isVerified ?: false,
        interestId = selectedInterestId
    )
}

fun getEmojiForInterestTag(tag: String): String = when (tag.lowercase().trim()) {
    "travelling", "travel" -> "✈️"
    "sports", "sport" -> "⚽"
    "shopping" -> "🛍️"
    "party" -> "🎉"
    "gaming", "games" -> "🎮"
    "photography", "photo" -> "📸"
    "art", "drawing" -> "🎨"
    "reading", "books" -> "📚"
    "cooking", "food" -> "🍳"
    "exercise", "fitness", "gym" -> "💪"
    "music", "lyrics music", "karaoke" -> "🎵"
    "dance" -> "💃"
    "drinks", "bar" -> "🍹"
    "pets", "dogs", "cats" -> "🐶"
    "coffee" -> "☕"
    "movies", "cinema" -> "🎬"
    else -> "✨"
}
