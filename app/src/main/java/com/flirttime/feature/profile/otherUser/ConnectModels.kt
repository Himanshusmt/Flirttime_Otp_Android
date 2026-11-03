package com.flirttime.feature.profile.otherUser

/** Optional "Connect because…" reasons sent with a connection request (iOS `ConnectReason`). */
enum class ConnectReason(val apiValue: String) {
    LOVE_THEIR_PROFILE("Love their profile"),
    SAME_INTERESTS("Same interests"),
    LIKED_THEIR_MOMENT("Liked their Moment"),
    GREAT_ENERGY("Great energy"),
    WANT_TO_CHAT("Want to chat");

    companion object {
        fun available(
            hasSharedInterests: Boolean,
            hasMoments: Boolean
        ): List<ConnectReason> = entries.filter { reason ->
            when (reason) {
                SAME_INTERESTS -> hasSharedInterests
                LIKED_THEIR_MOMENT -> hasMoments
                else -> true
            }
        }
    }
}

/** Reaction to one part of a profile; travels with the connection as context. */
data class MomentsReaction(val target: Target) {

    sealed class Target {
        data class Photo(val index: Int) : Target()
        data object WhyVibe : Target()
        data object OwnWords : Target()
        data object Voice : Target()
        data object Moments : Target()
    }

    val title: String
        get() = when (target) {
            is Target.Photo -> "Liked your photo"
            Target.WhyVibe -> "Loved why we match"
            Target.OwnWords -> "Liked your words"
            Target.Voice -> "Loved your voice intro"
            Target.Moments -> "Liked your Moments"
        }

    val analyticsValue: String
        get() = when (target) {
            is Target.Photo -> "photo_${target.index}"
            Target.WhyVibe -> "why_vibe"
            Target.OwnWords -> "own_words"
            Target.Voice -> "voice"
            Target.Moments -> "moments"
        }
}
