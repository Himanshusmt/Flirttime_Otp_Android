package com.flirttime.feature.moments

import com.flirttime.Resource
import com.flirttime.feature.moments.comments.CommentItem
import com.flirttime.feature.moments.gift.GiftItem
import kotlinx.coroutines.flow.Flow
import okhttp3.MultipartBody

interface MomentsRepository {
    fun getMomentsFeed(page: Int = 1, limit: Int = 10, type: String = "feed"): Flow<Resource<MomentsFeedResult>>
    fun getMyMoments(page: Int = 1, limit: Int = 4): Flow<Resource<UserMomentsResponseData>>
    fun getUserMoments(userId: String, page: Int = 1, limit: Int = 20): Flow<Resource<UserMomentsResponseData>>
    fun toggleLike(momentId: String, isLiked: Boolean? = null): Flow<Resource<MomentLikeResponseData?>>
    fun deleteMoment(momentId: String): Flow<Resource<Unit>>
    fun reportMoment(momentId: String, reason: String, description: String? = null): Flow<Resource<Unit>>
    fun blockUser(userId: String, reason: String = "Blocked from Moments"): Flow<Resource<Unit>>
    fun createMoment(
        caption: String?,
        audience: String,
        location: String?,
        imageParts: List<MultipartBody.Part>
    ): Flow<Resource<Unit>>

    // Comments
    fun getMomentComments(momentId: String, page: Int = 1, limit: Int = 20): Flow<Resource<List<CommentItem>>>
    fun postMomentComment(momentId: String, comment: String): Flow<Resource<Unit>>

    // Gifts
    fun getMomentGiftsCatalog(momentId: String): Flow<Resource<Pair<Int, List<GiftItem>>>>
    fun sendMomentGift(momentId: String, giftCode: String, quantity: Int = 1, message: String? = null): Flow<Resource<SendGiftResponseData>>
    fun getReceivedGifts(momentId: String, page: Int = 1, limit: Int = 20): Flow<Resource<MomentReceivedGiftsResponseData>>
}
