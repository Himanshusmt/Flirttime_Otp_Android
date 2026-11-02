package com.flirttime.feature.profile.settings

enum class FAQCategory {
    ACCOUNT,
    MATCHES,
    PREMIUM,
    SAFETY;

    val title: String
        get() = when (this) {
            ACCOUNT -> "Account & profile"
            MATCHES -> "Matches & chat"
            PREMIUM -> "Premium & coins"
            SAFETY -> "Safety & privacy"
        }

    companion object {
        fun fromKey(key: String?): FAQCategory? =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) }
    }
}

data class FAQItem(
    val category: FAQCategory,
    val question: String,
    val answer: String
)

object FAQCatalog {
    val items: List<FAQItem> = listOf(
        FAQItem(
            FAQCategory.ACCOUNT,
            "How do I edit my profile?",
            "Open the Profile tab and tap Edit Profile. You can update your photos, bio, interests and the details people see on your card."
        ),
        FAQItem(
            FAQCategory.ACCOUNT,
            "Why should I verify my profile?",
            "Verified profiles get a badge on their card, which tells people your photos really match you. Verified members get noticeably more matches."
        ),
        FAQItem(
            FAQCategory.ACCOUNT,
            "Can I change my email address?",
            "You can add an email from Settings > Personal details if you don't have one yet. Once it's saved, it can't be changed."
        ),
        FAQItem(
            FAQCategory.ACCOUNT,
            "How do I delete my account?",
            "Go to Settings > Personal details and tap Delete account. This removes your profile, matches and messages, and can't be undone."
        ),
        FAQItem(
            FAQCategory.MATCHES,
            "How does matching work?",
            "When you like someone and they like you back, it's a match. You can then start chatting right away."
        ),
        FAQItem(
            FAQCategory.MATCHES,
            "Why can't I message someone?",
            "You can chat with people once you've matched. If a conversation disappears, the other person may have unmatched or blocked you."
        ),
        FAQItem(
            FAQCategory.MATCHES,
            "What are Moments?",
            "Moments are photos and videos you share on your profile. People can react, comment and send gifts on them, which is a great way to start a conversation."
        ),
        FAQItem(
            FAQCategory.PREMIUM,
            "What do I get with Premium?",
            "Premium unlocks unlimited likes, shows you everyone who liked you, lets you read compliments and removes ads."
        ),
        FAQItem(
            FAQCategory.PREMIUM,
            "How do I cancel my subscription?",
            "Subscriptions are managed by Google Play. Open the Play Store, tap your profile, then Payments & subscriptions, and choose Flirt Time."
        ),
        FAQItem(
            FAQCategory.PREMIUM,
            "What are coins used for?",
            "Coins let you send gifts on Moments and unlock extra features. You can top them up anytime from the Coins shop."
        ),
        FAQItem(
            FAQCategory.SAFETY,
            "How do I block or report someone?",
            "Open their profile, tap the menu in the top corner and choose Block or Report. Our team reviews every report."
        ),
        FAQItem(
            FAQCategory.SAFETY,
            "How do I unblock someone?",
            "Go to Settings > Blocked & reported, find the person under Blocked and tap Unblock."
        ),
        FAQItem(
            FAQCategory.SAFETY,
            "Tips for meeting someone in person",
            "Meet in a public place, tell a friend where you're going, arrange your own transport and never send money to someone you haven't met."
        )
    )

    fun items(inCategory: FAQCategory?): List<FAQItem> =
        if (inCategory == null) items else items.filter { it.category == inCategory }
}
