package com.flirttime.feature.coins

import com.flirttime.Resource
import com.flirttime.core.network.safeApiCall
import com.flirttime.feature.main.MainApiService

class CoinRepository(
    private val mainApiService: MainApiService
) {
    suspend fun getPackages(): Resource<List<CoinPackDto>> = safeApiCall {
        mainApiService.getCoinPackages()
    }.let { result ->
        when (result) {
            is Resource.Success -> Resource.Success(
                result.data.orEmpty()
                    .filter { it.isActive != false }
                    .sortedBy { it.sortOrder ?: Int.MAX_VALUE }
            )
            is Resource.Error -> result
            Resource.Loading -> Resource.Loading
        }
    }

    suspend fun verifyPurchase(body: CoinPurchaseVerifyRequest): Resource<CoinPurchaseData> =
        safeApiCall { mainApiService.verifyCoinPurchase(body) }
}