package com.flirttime.feature.main.matches

import com.flirttime.Resource
import com.flirttime.feature.profile.DiscoveryActionResponseData
import com.flirttime.feature.profile.DiscoveryFilterOptionsData
import com.flirttime.feature.profile.DiscoveryPeopleResponseData
import com.flirttime.feature.profile.InterestsCatalogResponseData
import com.flirttime.feature.quickmoments.QuickMomentsTrayResponseData
import kotlinx.coroutines.flow.Flow

interface MatchesRepository {
    // 1. Discovery Cards & Search
    fun searchDiscoveryCards(page: Int = 1, limit: Int = 10, countOnly: Boolean = false, filters: DiscoveryFiltersDto? = null): Flow<Resource<DiscoverySearchResponseData>>
    fun getDiscoveryPeople(page: Int = 1, limit: Int = 10, interestId: String? = null): Flow<Resource<DiscoveryPeopleResponseData>>

    // 2. Actions & Connections
    fun submitDiscoveryAction(targetUserId: String, action: String): Flow<Resource<DiscoveryActionResponseData>>
    fun sendConnectionRequest(targetUserId: String, reason: String? = null): Flow<Resource<ConnectionResponseData>>

    // 3. User Limits
    fun getUserLimits(userId: String): Flow<Resource<UserLimitsResponseData>>

    // 4. Filters Sheet
    fun getDiscoveryFilterOptions(): Flow<Resource<DiscoveryFilterOptionsData>>
    fun getDiscoveryFilters(): Flow<Resource<DiscoveryFiltersDto>>
    fun saveDiscoveryFilters(filters: DiscoveryFiltersDto): Flow<Resource<Unit>>
    fun resetDiscoveryFilters(): Flow<Resource<Unit>>

    // 5. Safety
    fun blockUser(userId: String, reason: String = "Blocked from Discover"): Flow<Resource<Unit>>
    fun reportUser(userId: String, reason: String, description: String? = null, conversationId: String? = null): Flow<Resource<Unit>>

    // 6. Header Quick Info
    fun getWalletBalance(): Flow<Resource<WalletBalanceData>>
    fun getNotificationCounts(): Flow<Resource<Int>>

    // 7. Interests & Quick Moments
    fun getDiscoveryInterests(): Flow<Resource<InterestsCatalogResponseData>>
    fun getQuickMomentsTray(): Flow<Resource<QuickMomentsTrayResponseData>>
}
