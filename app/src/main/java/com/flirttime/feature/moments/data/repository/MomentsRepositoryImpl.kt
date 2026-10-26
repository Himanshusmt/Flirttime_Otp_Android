package com.flirttime.feature.moments.data.repository

import com.flirttime.Resource
import com.flirttime.core.common.MultipartHelper
import com.flirttime.core.network.safeApiCall
import com.flirttime.feature.moments.data.model.MomentsFeedResult
import com.flirttime.feature.moments.data.model.ToggleLikeRequest
import com.flirttime.feature.moments.data.model.toUiItem
import com.flirttime.feature.moments.data.network.MomentsApiService
import com.flirttime.feature.moments.domain.repository.MomentsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MultipartBody

class MomentsRepositoryImpl(
    private val momentsApiService: MomentsApiService
) : MomentsRepository {

    override fun getMomentsFeed(
        page: Int,
        limit: Int,
        type: String
    ): Flow<Resource<MomentsFeedResult>> = flow {
        emit(Resource.Loading)
        val result = safeApiCall {
            momentsApiService.getMomentsFeed(page = page, limit = limit, type = type)
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

    override fun toggleLike(momentId: String, isLiked: Boolean?): Flow<Resource<Unit>> = flow {
        val body = isLiked?.let { ToggleLikeRequest(isLiked = it) }
        val result = safeApiCall {
            momentsApiService.toggleLike(momentId = momentId, request = body)
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
            momentsApiService.createMoment(
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
}
