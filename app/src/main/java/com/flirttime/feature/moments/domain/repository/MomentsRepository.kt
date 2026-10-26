package com.flirttime.feature.moments.domain.repository

import com.flirttime.Resource
import com.flirttime.feature.moments.data.model.MomentsFeedResult
import kotlinx.coroutines.flow.Flow
import okhttp3.MultipartBody

interface MomentsRepository {
    fun getMomentsFeed(page: Int = 1, limit: Int = 20, type: String = "feed"): Flow<Resource<MomentsFeedResult>>
    fun toggleLike(momentId: String, isLiked: Boolean? = null): Flow<Resource<Unit>>
    fun createMoment(
        caption: String?,
        audience: String,
        location: String?,
        imageParts: List<MultipartBody.Part>
    ): Flow<Resource<Unit>>
}
