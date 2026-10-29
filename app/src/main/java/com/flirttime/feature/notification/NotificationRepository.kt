package com.flirttime.feature.notification

import com.flirttime.Resource
import com.flirttime.core.network.safeApiCall
import com.flirttime.feature.notification.NotificationItem
import com.flirttime.feature.notification.NotificationListItem
import com.flirttime.feature.notification.NotificationListResponseData
import com.flirttime.feature.notification.NotificationSection
import com.flirttime.feature.notification.NotificationApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.TimeZone

interface NotificationRepository {
    fun getNotifications(limit: Int = 20, cursor: String? = null): Flow<Resource<NotificationListResponseData>>
    fun getUnreadCount(): Flow<Resource<Int>>
    fun markAsRead(id: String): Flow<Resource<Unit>>
    fun markAllAsRead(): Flow<Resource<Unit>>
    fun deleteNotification(id: String): Flow<Resource<Unit>>
}

class NotificationRepositoryImpl(
    private val apiService: NotificationApiService
) : NotificationRepository {

    override fun getNotifications(
        limit: Int,
        cursor: String?
    ): Flow<Resource<NotificationListResponseData>> = flow {
        emit(Resource.Loading)
        val tz = TimeZone.getDefault().id
        val result = safeApiCall {
            apiService.getNotifications(limit = limit, tz = tz, cursor = cursor)
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun getUnreadCount(): Flow<Resource<Int>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            apiService.getUnreadCount()
        }
        if (result is Resource.Success) {
            emit(Resource.Success(result.data.getEffectiveUnread()))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun markAsRead(id: String): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            apiService.markAsRead(id)
        }
        if (result is Resource.Success) {
            emit(Resource.Success(Unit))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun markAllAsRead(): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            apiService.markAllAsRead()
        }
        if (result is Resource.Success) {
            emit(Resource.Success(Unit))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)

    override fun deleteNotification(id: String): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            apiService.deleteNotification(id)
        }
        if (result is Resource.Success) {
            emit(Resource.Success(Unit))
        } else if (result is Resource.Error) {
            emit(Resource.Error(result.message, result.code, result.cause, result.errorBody))
        }
    }.flowOn(Dispatchers.IO)
}
