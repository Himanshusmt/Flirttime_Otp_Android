package com.flirttime.feature.moments.data.network

import com.flirttime.core.network.model.ApiResponse
import com.flirttime.feature.moments.data.model.MomentsFeedResponseData
import com.flirttime.feature.moments.data.model.ToggleLikeRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface MomentsApiService {

    @GET("api/v1/moments")
    suspend fun getMomentsFeed(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("type") type: String = "feed"
    ): ApiResponse<MomentsFeedResponseData>

    @POST("api/v1/moments/{momentId}/like")
    suspend fun toggleLike(
        @Path("momentId") momentId: String,
        @Body request: ToggleLikeRequest? = null
    ): ApiResponse<Any>

    @Multipart
    @POST("api/v1/moments")
    suspend fun createMoment(
        @Part("caption") caption: RequestBody?,
        @Part("audience") audience: RequestBody?,
        @Part("location") location: RequestBody?,
        @Part("locationLabel") locationLabel: RequestBody?,
        @Part images: List<MultipartBody.Part>
    ): ApiResponse<Any>
}
