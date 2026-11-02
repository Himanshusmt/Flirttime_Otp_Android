package com.flirttime.feature.profile.otherUser

import com.flirttime.feature.profile.UserSearchItemDto

data class WhyMatchReason(
    val emoji: String,
    val text: String
)

object WhyYouMatchHelper {

    fun reasonsFor(user: UserSearchItemDto): List<WhyMatchReason> {
        val explanation = user.vibeExplanation?.trim().orEmpty()
            .ifBlank { user.compatibility?.explanation?.trim().orEmpty() }
        if (explanation.isNotBlank()) {
            return listOf(WhyMatchReason(emojiFor(explanation), explanation))
        }

        val shared = user.sharedInterests.orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val reasons = mutableListOf<WhyMatchReason>()
        shared.getOrNull(0)?.let {
            reasons.add(WhyMatchReason(emojiFor(it), "You both love ${it.lowercase()}"))
        }
        shared.getOrNull(1)?.let {
            reasons.add(WhyMatchReason(emojiFor(it), "You both selected ${it.lowercase()}"))
        }
        if (shared.size > 2) {
            val extra = shared.size - 2
            val text = if (extra == 1) "1 more interest in common" else "$extra more interests in common"
            reasons.add(WhyMatchReason("💞", text))
        }
        return reasons
    }

    private fun emojiFor(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("both in") -> "📍"
            lower.contains("in common") -> "💞"
            lower.contains("cook") || lower.contains("food") -> "🍳"
            lower.contains("read") || lower.contains("book") -> "📚"
            lower.contains("fit") || lower.contains("gym") -> "💪"
            lower.contains("pet") || lower.contains("dog") || lower.contains("cat") -> "🐾"
            lower.contains("music") -> "🎵"
            lower.contains("travel") -> "✈️"
            else -> "✨"
        }
    }
}
