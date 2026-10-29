package com.flirttime.feature.notification

import com.flirttime.core.network.ApiResponse
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.TimeZone

interface NotificationApiService {

    @GET("api/v1/notifications")
    suspend fun getNotifications(
        @Query("limit") limit: Int = 20,
        @Query("tz") tz: String = TimeZone.getDefault().id,
        @Query("cursor") cursor: String? = null
    ): ApiResponse<NotificationListResponseData>

    @GET("api/v1/notifications/counts")
    suspend fun getUnreadCount(): ApiResponse<NotificationCountsData>

    @PATCH("api/v1/notifications/{id}/read")
    suspend fun markAsRead(
        @Path("id") id: String
    ): ApiResponse<Any>

    @PATCH("api/v1/notifications/read-all")
    suspend fun markAllAsRead(): ApiResponse<Any>

    @DELETE("api/v1/notifications/{id}")
    suspend fun deleteNotification(
        @Path("id") id: String
    ): ApiResponse<Any>
}
