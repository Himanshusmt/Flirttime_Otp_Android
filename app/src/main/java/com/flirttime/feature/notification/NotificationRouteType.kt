package com.flirttime.feature.notification

enum class NotificationRouteType {
    LIKE,
    FAVORITE,
    MATCH,
    COMPLIMENT,
    MESSAGE,
    CALL,
    MISSED_CALL,
    MOMENT_LIKE,
    MOMENT_COMMENT,
    MOMENT_COMMENT_LIKE,
    MOMENT_GIFT,
    MOMENT_REACTION,
    PROFILE_VISIT,
    GESTURE_VERIFIED,
    GESTURE_UNVERIFIED,
    PROFILE_IMAGE_VERIFIED,
    PROFILE_IMAGE_UNVERIFIED,
    IMAGE_VERIFIED,
    IMAGE_UNVERIFIED,
    INCOMPLETE,
    MEMBERSHIP_UPGRADE,
    UNKNOWN;

    companion object {
        private val aliases = mapOf(
            "like" to LIKE,
            "favorite" to FAVORITE,
            "super_like" to FAVORITE,
            "superlike" to FAVORITE,
            "super-like" to FAVORITE,
            "match" to MATCH,
            "new_match" to MATCH,
            "compliment" to COMPLIMENT,
            "message" to MESSAGE,
            "new_message" to MESSAGE,
            "chat_message" to MESSAGE,
            "chat" to MESSAGE,
            "call" to CALL,
            "incoming_call" to CALL,
            "incoming-call" to CALL,
            "voice_call" to CALL,
            "video_call" to CALL,
            "missedcall" to MISSED_CALL,
            "missed_call" to MISSED_CALL,
            "moment_like" to MOMENT_LIKE,
            "moment-like" to MOMENT_LIKE,
            "moment" to MOMENT_LIKE,
            "moment_comment" to MOMENT_COMMENT,
            "moment-comment" to MOMENT_COMMENT,
            "moment_comment_reply" to MOMENT_COMMENT,
            "comment_reply" to MOMENT_COMMENT,
            "reply" to MOMENT_COMMENT,
            "moment_comment_like" to MOMENT_COMMENT_LIKE,
            "moment-comment-like" to MOMENT_COMMENT_LIKE,
            "comment_like" to MOMENT_COMMENT_LIKE,
            "moment_gift" to MOMENT_GIFT,
            "moment-gift" to MOMENT_GIFT,
            "moment_reaction" to MOMENT_REACTION,
            "moment-reaction" to MOMENT_REACTION,
            "quick_moment" to MOMENT_REACTION,
            "quick-moment" to MOMENT_REACTION,
            "quick_moment_reaction" to MOMENT_REACTION,
            "profile_visit" to PROFILE_VISIT,
            "profile-visit" to PROFILE_VISIT,
            "visit" to PROFILE_VISIT,
            "gesture-verified" to GESTURE_VERIFIED,
            "gesture_verified" to GESTURE_VERIFIED,
            "gesture-unverified" to GESTURE_UNVERIFIED,
            "gesture_unverified" to GESTURE_UNVERIFIED,
            "profile-image-verified" to PROFILE_IMAGE_VERIFIED,
            "profile_image_verified" to PROFILE_IMAGE_VERIFIED,
            "profile_verified" to PROFILE_IMAGE_VERIFIED,
            "verified" to PROFILE_IMAGE_VERIFIED,
            "profile-image-unverified" to PROFILE_IMAGE_UNVERIFIED,
            "profile_image_unverified" to PROFILE_IMAGE_UNVERIFIED,
            "image-verified" to IMAGE_VERIFIED,
            "image_verified" to IMAGE_VERIFIED,
            "photo_approved" to IMAGE_VERIFIED,
            "image-unverified" to IMAGE_UNVERIFIED,
            "image_unverified" to IMAGE_UNVERIFIED,
            "photo_rejected" to IMAGE_UNVERIFIED,
            "photo_not_approved" to IMAGE_UNVERIFIED,
            "rejected" to IMAGE_UNVERIFIED,
            "incomplete" to INCOMPLETE,
            "profile_incomplete" to INCOMPLETE,
            "membership-upgrade" to MEMBERSHIP_UPGRADE,
            "membership_upgrade" to MEMBERSHIP_UPGRADE,
            "subscription" to MEMBERSHIP_UPGRADE,
            "premium" to MEMBERSHIP_UPGRADE
        )

        fun fromPayload(values: List<String?>): NotificationRouteType {
            var resolved = UNKNOWN
            for (raw in values) {
                val key = raw?.lowercase()?.trim().orEmpty()
                if (key.isEmpty()) continue
                val mapped = aliases[key] ?: aliases[key.replace("-", "_")]
                if (mapped == null) continue
                if (mapped == PROFILE_IMAGE_VERIFIED || mapped == GESTURE_VERIFIED || mapped == IMAGE_VERIFIED) {
                    return mapped
                }
                if (resolved == UNKNOWN) resolved = mapped
            }
            return resolved
        }
    }
}
