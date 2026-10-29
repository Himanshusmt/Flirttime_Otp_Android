package com.flirttime.feature.moments

import android.util.Log
import com.flirttime.Resource
import com.flirttime.core.common.MultipartHelper
import com.flirttime.core.network.safeApiCall
import com.flirttime.feature.moments.comments.CommentItem
import com.flirttime.feature.moments.gift.GiftItem
import com.flirttime.feature.moments.MomentsFeedResult
import com.flirttime.feature.moments.MomentReceivedGiftsResponseData
import com.flirttime.feature.quickmoments.MyQuickMomentsResponseData
import com.flirttime.feature.moments.PostCommentRequest
import com.flirttime.feature.quickmoments.QuickMomentReactionRequest
import com.flirttime.feature.quickmoments.QuickMomentReplyRequest
import com.flirttime.feature.moments.SendGiftResponseData
import com.flirttime.feature.moments.SendMomentGiftRequest
import com.flirttime.feature.moments.ToggleLikeRequest
import com.flirttime.feature.moments.UserMomentsResponseData
import com.flirttime.feature.moments.toUiItem
import com.flirttime.feature.main.MainApiService
import com.flirttime.feature.moments.MomentsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MultipartBody

class MomentsRepositoryImpl(
    private val mainApiService: MainApiService
) : MomentsRepository {

    override fun getMomentsFeed(
        page: Int,
        limit: Int,
        type: String
    ): Flow<Resource<MomentsFeedResult>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getMomentsFeed(page = page, limit = limit, type = type)
        }
        when (result) {
            is Resource.Success -> {
                val data = result.data
                val uiList = data.moments.orEmpty().map { it.toUiItem() }
                val feedResult = MomentsFeedResult(
                    moments = uiList,
                    page = data.page ?: page,
                    totalPages = data.totalPages ?: 1,
                    totalCount = data.totalCount ?: uiList.size
                )
                emit(Resource.Success(feedResult))
            }
            is Resource.Error -> {
                emit(Resource.Error(message = result.message, code = result.code, cause = result.cause, errorBody = result.errorBody))
            }
            is Resource.Loading -> Unit
        }
    }.flowOn(Dispatchers.IO)

    override fun getMyMoments(
        page: Int,
        limit: Int
    ): Flow<Resource<UserMomentsResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getMyMoments(page = page, limit = limit)
        }
        when (result) {
            is Resource.Success -> {
                emit(Resource.Success(result.data))
            }
            is Resource.Error -> {
                emit(Resource.Error(message = result.message, code = result.code, cause = result.cause, errorBody = result.errorBody))
            }
            is Resource.Loading -> Unit
        }
    }.flowOn(Dispatchers.IO)

    override fun getUserMoments(
        userId: String,
        page: Int,
        limit: Int
    ): Flow<Resource<UserMomentsResponseData>> = flow {
        emit(Resource.Loading)
        val targetUserId = if (userId.isNotBlank()) userId else "me"
        val result = safeApiCall {
            mainApiService.getUserMoments(userId = targetUserId, page = page, limit = limit)
        }
        when (result) {
            is Resource.Success -> {
                emit(Resource.Success(result.data))
            }
            is Resource.Error -> {
                emit(Resource.Error(message = result.message, code = result.code, cause = result.cause, errorBody = result.errorBody))
            }
            is Resource.Loading -> Unit
        }
    }.flowOn(Dispatchers.IO)

    override fun toggleLike(momentId: String, isLiked: Boolean?): Flow<Resource<Unit>> = flow {
        val body = isLiked?.let { ToggleLikeRequest(isLiked = it) }
        val result = safeApiCall {
            mainApiService.toggleLike(momentId = momentId, request = body)
        }
        when (result) {
            is Resource.Success -> emit(Resource.Success(Unit))
            is Resource.Error -> emit(Resource.Error(message = result.message, code = result.code, cause = result.cause, errorBody = result.errorBody))
            is Resource.Loading -> Unit
        }
    }.flowOn(Dispatchers.IO)

    override fun deleteMoment(momentId: String): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.deleteMoment(momentId = momentId)
        }
        when (result) {
            is Resource.Success -> emit(Resource.Success(Unit))
            is Resource.Error -> emit(Resource.Error(message = result.message, code = result.code, cause = result.cause, errorBody = result.errorBody))
            is Resource.Loading -> Unit
        }
    }.flowOn(Dispatchers.IO)

    override fun createMoment(
        caption: String?,
        audience: String,
        location: String?,
        imageParts: List<MultipartBody.Part>
    ): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val captionPart = caption?.takeIf { it.isNotBlank() }?.let { MultipartHelper.createTextPart(it) }
        val audiencePart = MultipartHelper.createTextPart(audience)
        val locationPart = location?.takeIf { it.isNotBlank() }?.let { MultipartHelper.createTextPart(it) }
        val locationLabelPart = location?.takeIf { it.isNotBlank() }?.let { MultipartHelper.createTextPart(it) }

        val result = safeApiCall {
            mainApiService.createMoment(
                caption = captionPart,
                audience = audiencePart,
                location = locationPart,
                locationLabel = locationLabelPart,
                images = imageParts
            )
        }
        when (result) {
            is Resource.Success -> emit(Resource.Success(Unit))
            is Resource.Error -> emit(Resource.Error(message = result.message, code = result.code, cause = result.cause, errorBody = result.errorBody))
            is Resource.Loading -> Unit
        }
    }.flowOn(Dispatchers.IO)

    override fun getMomentComments(
        momentId: String,
        page: Int,
        limit: Int
    ): Flow<Resource<List<CommentItem>>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getMomentComments(momentId = momentId, page = page, limit = limit)
        }
        when (result) {
            is Resource.Success -> {
                val commentsDtoList = result.data.comments.orEmpty()
                val domainItems = commentsDtoList.map { dto ->
                    val userObj = dto.user ?: dto.commenter ?: dto.author
                    val username = userObj?.username?.takeIf { it.isNotBlank() }
                        ?: userObj?.fullName?.takeIf { it.isNotBlank() }
                        ?: userObj?.name?.takeIf { it.isNotBlank() }
                        ?: "User"
                    val avatarUrl = userObj?.profilePicture?.takeIf { it.isNotBlank() }
                        ?: userObj?.profilePhoto?.takeIf { it.isNotBlank() }
                    val content = dto.comment?.takeIf { it.isNotBlank() }
                        ?: dto.text.orEmpty()
                    val time = dto.timeAgo?.takeIf { it.isNotBlank() } ?: "Just now"

                    CommentItem(
                        id = dto.id ?: dto.commentId ?: System.currentTimeMillis().toString(),
                        userId = userObj?.id ?: userObj?.userId,
                        username = username,
                        timeAgo = time,
                        text = content,
                        avatarResId = com.flirttime.R.drawable.img_gallery_demo_girl,
                        avatarUrl = avatarUrl,
                        likeCount = dto.likesCount ?: 0,
                        replyCount = dto.repliesCount ?: dto.replyCount ?: 0
                    )
                }
                emit(Resource.Success(domainItems))
            }
            is Resource.Error -> {
                emit(Resource.Error(message = result.message, code = result.code, cause = result.cause, errorBody = result.errorBody))
            }
            is Resource.Loading -> Unit
        }
    }.flowOn(Dispatchers.IO)

    override fun postMomentComment(
        momentId: String,
        comment: String
    ): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.postMomentComment(
                momentId = momentId,
                request = PostCommentRequest(comment = comment)
            )
        }
        when (result) {
            is Resource.Success -> emit(Resource.Success(Unit))
            is Resource.Error -> emit(Resource.Error(message = result.message, code = result.code, cause = result.cause, errorBody = result.errorBody))
            is Resource.Loading -> Unit
        }
    }.flowOn(Dispatchers.IO)

    override fun getMomentGiftsCatalog(momentId: String): Flow<Resource<Pair<Int, List<GiftItem>>>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getMomentGiftsCatalog(momentId)
        }
        val defaultGifts = listOf(
            GiftItem("gift_rose", "Rose", 1, "🌹", "rose"),
            GiftItem("gift_heart", "Heart", 5, "💖", "heart"),
            GiftItem("gift_chocolate", "Chocolate", 10, "🍫", "chocolate"),
            GiftItem("gift_teddy", "Teddy", 20, "🧸", "teddy"),
            GiftItem("gift_bouquet", "Bouquet", 50, "💐", "bouquet"),
            GiftItem("gift_ring", "Ring", 100, "💍", "ring"),
            GiftItem("gift_crown", "Crown", 200, "👑", "crown"),
            GiftItem("gift_sports_car", "Sports car", 500, "🏎️", "sports_car")
        )
        when (result) {
            is Resource.Success -> {
                val data = result.data
                val balance = data.coinBalance ?: data.coins ?: data.balance ?: 500
                val serverGifts = data.gifts.orEmpty().mapNotNull { dto ->
                    val code = dto.giftCode ?: dto.code ?: return@mapNotNull null
                    val name = dto.name ?: code.replaceFirstChar { it.uppercase() }
                    val price = dto.coinPrice ?: dto.coins ?: dto.price ?: 10
                    val emoji = dto.emoji ?: when (code.lowercase()) {
                        "rose" -> "🌹"
                        "heart" -> "💖"
                        "chocolate" -> "🍫"
                        "teddy" -> "🧸"
                        "bouquet" -> "💐"
                        "ring" -> "💍"
                        "crown" -> "👑"
                        "sports_car" -> "🏎️"
                        else -> "🎁"
                    }
                    GiftItem(id = dto.id ?: "gift_$code", name = name, coinPrice = price, emoji = emoji, giftCode = code)
                }
                val finalGifts = if (serverGifts.isNotEmpty()) serverGifts else defaultGifts
                emit(Resource.Success(Pair(balance, finalGifts)))
            }
            is Resource.Error -> {
                emit(Resource.Success(Pair(500, defaultGifts)))
            }
            is Resource.Loading -> Unit
        }
    }.flowOn(Dispatchers.IO)

    override fun sendMomentGift(
        momentId: String,
        giftCode: String,
        quantity: Int,
        message: String?
    ): Flow<Resource<SendGiftResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.sendMomentGift(
                momentId = momentId,
                request = SendMomentGiftRequest(giftCode = giftCode, quantity = quantity, message = message)
            )
        }
        when (result) {
            is Resource.Success -> emit(Resource.Success(result.data))
            is Resource.Error -> emit(Resource.Error(message = result.message, code = result.code, cause = result.cause, errorBody = result.errorBody))
            is Resource.Loading -> Unit
        }
    }.flowOn(Dispatchers.IO)

    override fun getReceivedGifts(
        momentId: String,
        page: Int,
        limit: Int
    ): Flow<Resource<MomentReceivedGiftsResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getReceivedGifts(momentId = momentId, page = page, limit = limit)
        }
        when (result) {
            is Resource.Success -> emit(Resource.Success(result.data))
            is Resource.Error -> emit(Resource.Error(message = result.message, code = result.code, cause = result.cause, errorBody = result.errorBody))
            is Resource.Loading -> Unit
        }
    }.flowOn(Dispatchers.IO)

    // Quick Moments (24h Ephemeral Stories - Profile & Single Story Actions)
    override fun getMyQuickMoments(): Flow<Resource<MyQuickMomentsResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getMyQuickMoments()
        }
        when (result) {
            is Resource.Success -> emit(Resource.Success(result.data))
            is Resource.Error -> {
                Log.e("MomentsRepository", "getMyQuickMoments failed: ${result.message}")
                emit(Resource.Error(message = result.message, code = result.code, cause = result.cause, errorBody = result.errorBody))
            }
            is Resource.Loading -> Unit
        }
    }.flowOn(Dispatchers.IO)

    override fun getUserQuickMoments(userId: String): Flow<Resource<MyQuickMomentsResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getUserQuickMoments(userId = userId)
        }
        when (result) {
            is Resource.Success -> emit(Resource.Success(result.data))
            is Resource.Error -> {
                Log.e("MomentsRepository", "getUserQuickMoments failed: ${result.message}")
                emit(Resource.Error(message = result.message, code = result.code, cause = result.cause, errorBody = result.errorBody))
            }
            is Resource.Loading -> Unit
        }
    }.flowOn(Dispatchers.IO)

    override fun deleteQuickMoment(momentId: String): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.deleteQuickMoment(momentId = momentId)
        }
        when (result) {
            is Resource.Success -> emit(Resource.Success(Unit))
            is Resource.Error -> {
                Log.e("MomentsRepository", "deleteQuickMoment failed: ${result.message}")
                emit(Resource.Error(message = result.message, code = result.code, cause = result.cause, errorBody = result.errorBody))
            }
            is Resource.Loading -> Unit
        }
    }.flowOn(Dispatchers.IO)

    override fun reactToQuickMoment(momentId: String, reaction: String): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.reactToQuickMoment(momentId = momentId, request = QuickMomentReactionRequest(reaction = reaction))
        }
        when (result) {
            is Resource.Success -> emit(Resource.Success(Unit))
            is Resource.Error -> {
                Log.e("MomentsRepository", "reactToQuickMoment failed: ${result.message}")
                emit(Resource.Error(message = result.message, code = result.code, cause = result.cause, errorBody = result.errorBody))
            }
            is Resource.Loading -> Unit
        }
    }.flowOn(Dispatchers.IO)

    override fun replyToQuickMoment(momentId: String, message: String): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.replyToQuickMoment(momentId = momentId, request = QuickMomentReplyRequest(message = message))
        }
        when (result) {
            is Resource.Success -> emit(Resource.Success(Unit))
            is Resource.Error -> {
                Log.e("MomentsRepository", "replyToQuickMoment failed: ${result.message}")
                emit(Resource.Error(message = result.message, code = result.code, cause = result.cause, errorBody = result.errorBody))
            }
            is Resource.Loading -> Unit
        }
    }.flowOn(Dispatchers.IO)

    override fun markQuickMomentViewed(momentId: String): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.markQuickMomentViewed(momentId = momentId)
        }
        when (result) {
            is Resource.Success -> emit(Resource.Success(Unit))
            is Resource.Error -> {
                Log.e("MomentsRepository", "markQuickMomentViewed failed: ${result.message}")
                emit(Resource.Error(message = result.message, code = result.code, cause = result.cause, errorBody = result.errorBody))
            }
            is Resource.Loading -> Unit
        }
    }.flowOn(Dispatchers.IO)
}
