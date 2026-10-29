package com.flirttime.feature.main.matches

import android.util.Log
import com.flirttime.Resource
import com.flirttime.core.network.safeApiCall
import com.flirttime.feature.main.MainApiService
import com.flirttime.feature.profile.DiscoveryActionRequest
import com.flirttime.feature.profile.DiscoveryActionResponseData
import com.flirttime.feature.profile.DiscoveryFilterOptionsData
import com.flirttime.feature.profile.DiscoveryPeopleResponseData
import com.flirttime.feature.profile.InterestsCatalogResponseData
import com.flirttime.feature.quickmoments.QuickMomentsTrayResponseData
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
            genders = f.genders?.takeIf { it.isNotEmpty() },
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
        val result = safeApiCall { mainApiService.saveDiscoveryFilters(filters) }
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
}
