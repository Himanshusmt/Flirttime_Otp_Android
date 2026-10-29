package com.flirttime.feature.moments

data class MomentPost(
    val id: String,
    val userId: String? = null,
    val username: String,
    val avatarUrl: String? = null,
    val avatarRes: Int? = null,
    val isVerified: Boolean = true,
    val locationAndTime: String,
    val caption: String,
    val mediaUrl: String? = null,
    val mediaRes: Int? = null,
    val mediaUrls: List<String> = emptyList(),
    var currentMediaIndex: Int = 0,
    var likeCount: Int = 0,
    var isLiked: Boolean = false,
    var commentCount: Int = 0,
    var giftCount: Int = 0,
    var isOwnMoment: Boolean = false
) {
    fun getAllMediaUrls(): List<String> {
        if (mediaUrls.isNotEmpty()) return mediaUrls
        if (!mediaUrl.isNullOrEmpty()) return listOf(mediaUrl)
        return emptyList()
    }
}
