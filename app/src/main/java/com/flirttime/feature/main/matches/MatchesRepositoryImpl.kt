package com.flirttime.feature.main.matches

import android.content.Context
import android.net.Uri
import android.util.Log
import com.flirttime.Resource
import com.flirttime.core.common.MultipartHelper
import com.flirttime.core.network.safeApiCall
import com.flirttime.feature.main.MainApiService
import com.flirttime.feature.profile.DiscoveryActionRequest
import com.flirttime.feature.profile.DiscoveryActionResponseData
import com.flirttime.feature.profile.DiscoveryFilterOptionsData
import com.flirttime.feature.profile.DiscoveryPeopleResponseData
import com.flirttime.feature.profile.InterestsCatalogResponseData
import com.flirttime.feature.quickmoments.CreateTextQuickMomentRequest
import com.flirttime.feature.quickmoments.MyQuickMomentsResponseData
import com.flirttime.feature.quickmoments.QuickMomentReactionRequest
import com.flirttime.feature.quickmoments.QuickMomentReplyRequest
import com.flirttime.feature.quickmoments.QuickMomentSlideDto
import com.flirttime.feature.quickmoments.QuickMomentsTrayResponseData
import com.flirttime.feature.quickmoments.QuickMomentViewersResponseData
import com.flirttime.feature.quickmoments.QuickMomentReactionsResponseData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class MatchesRepositoryImpl(
    private val mainApiService: MainApiService
) : MatchesRepository {

    // 1. Discovery Cards & Search
    override fun searchDiscoveryCards(
        page: Int,
        limit: Int,
        countOnly: Boolean,
        filters: DiscoveryFiltersDto?
    ): Flow<Resource<DiscoverySearchResponseData>> = flow {
        emit(Resource.Loading)
        val f = filters ?: DiscoveryFiltersDto()
        val request = DiscoverySearchRequest(
            page = page,
            limit = limit,
            genders = f.normalizedGenders,
            ageMin = f.ageMin,
            ageMax = f.ageMax,
            maxDistanceKm = f.maxDistanceKm,
            lookingFor = f.lookingFor?.takeIf { it.isNotEmpty() },
            interestIds = f.interestIds?.takeIf { it.isNotEmpty() },
            verifiedOnly = f.verifiedOnly ?: false,
            onlineOnly = f.onlineOnly ?: false
        )
        val result = safeApiCall {
            mainApiService.searchDiscoveryCards(request)
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun getDiscoveryPeople(
        page: Int,
        limit: Int,
        interestId: String?
    ): Flow<Resource<DiscoveryPeopleResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getDiscoveryPeople(page = page, limit = limit, interestId = interestId)
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    // 2. Actions & Connections
    override fun submitDiscoveryAction(
        targetUserId: String,
        action: String
    ): Flow<Resource<DiscoveryActionResponseData>> = flow {
        val result = safeApiCall {
            mainApiService.submitDiscoveryAction(
                DiscoveryActionRequest(targetUserId = targetUserId, action = action)
            )
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun sendConnectionRequest(
        targetUserId: String,
        reason: String?
    ): Flow<Resource<ConnectionResponseData>> = flow {
        val result = safeApiCall {
            mainApiService.sendConnectionRequest(
                ConnectionRequest(targetUserId = targetUserId, reason = reason)
            )
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    // 3. User Limits
    override fun getUserLimits(userId: String): Flow<Resource<UserLimitsResponseData>> = flow {
        val result = safeApiCall {
            mainApiService.getUserLimits(userId)
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    // 4. Filters Sheet
    override fun getDiscoveryFilterOptions(): Flow<Resource<DiscoveryFilterOptionsData>> = flow {
        emit(safeApiCall { mainApiService.getDiscoveryFilterOptions() })
    }.flowOn(Dispatchers.IO)

    override fun getDiscoveryFilters(): Flow<Resource<DiscoveryFiltersDto>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall { mainApiService.getDiscoveryFilters() }
        when (result) {
            is Resource.Success -> {
                val effective = result.data?.toEffectiveFilters() ?: DiscoveryFiltersDto()
                emit(Resource.Success(effective))
            }
            is Resource.Error -> emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
            Resource.Loading -> emit(Resource.Loading)
        }
    }.flowOn(Dispatchers.IO)

    override fun saveDiscoveryFilters(filters: DiscoveryFiltersDto): Flow<Resource<Unit>> = flow {
        val sanitized = filters.copy(genders = filters.normalizedGenders)
        val result = safeApiCall { mainApiService.saveDiscoveryFilters(sanitized) }
        when (result) {
            is Resource.Success -> emit(Resource.Success(Unit))
            is Resource.Error -> emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
            Resource.Loading -> emit(Resource.Loading)
        }
    }.flowOn(Dispatchers.IO)

    override fun resetDiscoveryFilters(): Flow<Resource<Unit>> = flow {
        val result = safeApiCall { mainApiService.resetDiscoveryFilters() }
        when (result) {
            is Resource.Success -> emit(Resource.Success(Unit))
            is Resource.Error -> emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
            Resource.Loading -> emit(Resource.Loading)
        }
    }.flowOn(Dispatchers.IO)

    // 5. Safety
    override fun blockUser(userId: String, reason: String): Flow<Resource<Unit>> = flow {
        val result = safeApiCall { mainApiService.blockUser(userId, mapOf("reason" to reason)) }
        when (result) {
            is Resource.Success -> emit(Resource.Success(Unit))
            is Resource.Error -> emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
            Resource.Loading -> emit(Resource.Loading)
        }
    }.flowOn(Dispatchers.IO)

    override fun reportUser(
        userId: String,
        reason: String,
        description: String?,
        conversationId: String?
    ): Flow<Resource<Unit>> = flow {
        val body = mutableMapOf<String, Any?>("reason" to reason)
        if (!description.isNullOrBlank()) body["description"] = description
        if (!conversationId.isNullOrBlank()) body["conversationId"] = conversationId
        val result = safeApiCall { mainApiService.reportUser(userId, body) }
        when (result) {
            is Resource.Success -> emit(Resource.Success(Unit))
            is Resource.Error -> emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
            Resource.Loading -> emit(Resource.Loading)
        }
    }.flowOn(Dispatchers.IO)

    // 6. Header Quick Info
    override fun getWalletBalance(): Flow<Resource<WalletBalanceData>> = flow {
        emit(safeApiCall { mainApiService.getWalletBalance() })
    }.flowOn(Dispatchers.IO)

    override fun getNotificationCounts(): Flow<Resource<Int>> = flow {
        val result = safeApiCall { mainApiService.getNotificationCounts() }
        when (result) {
            is Resource.Success -> {
                emit(Resource.Success(result.data.effectiveCount))
            }
            is Resource.Error -> emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
            Resource.Loading -> emit(Resource.Loading)
        }
    }.flowOn(Dispatchers.IO)

    // 7. Interests & Quick Moments
    override fun getDiscoveryInterests(): Flow<Resource<InterestsCatalogResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getDiscoveryInterests()
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun getQuickMomentsTray(): Flow<Resource<QuickMomentsTrayResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getQuickMomentsTray()
        }
        when (result) {
            is Resource.Success -> emit(Resource.Success(result.data))
            is Resource.Error -> {
                Log.e("MatchesRepo", "Failed to fetch quick moments tray: ${result.message}")
                emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
            }
            Resource.Loading -> emit(Resource.Loading)
        }
    }.flowOn(Dispatchers.IO)

    override fun getMyQuickMoments(): Flow<Resource<MyQuickMomentsResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getMyQuickMoments()
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun createTextQuickMoment(
        text: String,
        backgroundColor: String
    ): Flow<Resource<QuickMomentSlideDto>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.createTextQuickMoment(
                CreateTextQuickMomentRequest(
                    type = "text",
                    text = text,
                    backgroundColor = backgroundColor,
                    textColor = "#ffffff"
                )
            )
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun createImageQuickMoment(
        context: Context,
        imageUri: Uri,
        caption: String?
    ): Flow<Resource<QuickMomentSlideDto>> = flow {
        emit(Resource.Loading)
        val part = MultipartHelper.createPartFromUri(context, imageUri, "image")
        if (part == null) {
            emit(Resource.Error("Unable to process selected image."))
            return@flow
        }
        val captionPart = caption?.takeIf { it.isNotBlank() }?.let { MultipartHelper.createTextPart(it) }
        val result = safeApiCall {
            mainApiService.createImageQuickMoment(
                image = part,
                caption = captionPart
            )
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun deleteQuickMoment(momentId: String): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.deleteQuickMoment(momentId)
        }
        when (result) {
            is Resource.Success -> emit(Resource.Success(Unit))
            is Resource.Error -> emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
            Resource.Loading -> emit(Resource.Loading)
        }
    }.flowOn(Dispatchers.IO)

    override fun reactToQuickMoment(
        momentId: String,
        reaction: String
    ): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.reactToQuickMoment(momentId, QuickMomentReactionRequest(reaction))
        }
        when (result) {
            is Resource.Success -> emit(Resource.Success(Unit))
            is Resource.Error -> emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
            Resource.Loading -> emit(Resource.Loading)
        }
    }.flowOn(Dispatchers.IO)

    override fun replyToQuickMoment(
        momentId: String,
        message: String
    ): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.replyToQuickMoment(momentId, QuickMomentReplyRequest(message))
        }
        when (result) {
            is Resource.Success -> emit(Resource.Success(Unit))
            is Resource.Error -> emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
            Resource.Loading -> emit(Resource.Loading)
        }
    }.flowOn(Dispatchers.IO)

    override fun markQuickMomentViewed(momentId: String): Flow<Resource<Unit>> = flow {
        val result = safeApiCall {
            mainApiService.markQuickMomentViewed(momentId)
        }
        when (result) {
            is Resource.Success -> emit(Resource.Success(Unit))
            is Resource.Error -> emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
            Resource.Loading -> emit(Resource.Loading)
        }
    }.flowOn(Dispatchers.IO)

    override fun getQuickMomentViewers(
        momentId: String,
        page: Int,
        limit: Int
    ): Flow<Resource<QuickMomentViewersResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getQuickMomentViewers(momentId = momentId, page = page, limit = limit)
        }
        when (result) {
            is Resource.Success -> emit(Resource.Success(result.data))
            is Resource.Error -> {
                Log.e("MatchesRepository", "[getQuickMomentViewers] failed: code=${result.code} msg=${result.message}")
                emit(Resource.Error(message = result.message, code = result.code, cause = result.cause, errorBody = result.errorBody))
            }
            is Resource.Loading -> Unit
        }
    }.flowOn(Dispatchers.IO)

    override fun getQuickMomentReactions(
        momentId: String,
        page: Int,
        limit: Int
    ): Flow<Resource<QuickMomentReactionsResponseData>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            mainApiService.getQuickMomentReactions(momentId = momentId, page = page, limit = limit)
        }
        when (result) {
            is Resource.Success -> emit(Resource.Success(result.data))
            is Resource.Error -> {
                Log.e("MatchesRepository", "[getQuickMomentReactions] failed: code=${result.code} msg=${result.message}")
                emit(Resource.Error(message = result.message, code = result.code, cause = result.cause, errorBody = result.errorBody))
            }
            is Resource.Loading -> Unit
        }
    }.flowOn(Dispatchers.IO)
}