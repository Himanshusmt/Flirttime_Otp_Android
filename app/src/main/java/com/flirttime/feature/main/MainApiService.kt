package com.flirttime.feature.main

import com.flirttime.core.network.ApiResponse
import com.flirttime.feature.auth.AuthUserDto
import com.flirttime.feature.main.explore.InteractionsResponseData
import com.flirttime.feature.main.matches.ConnectionRequest
import com.flirttime.feature.main.matches.ConnectionResponseData
import com.flirttime.feature.main.matches.DiscoveryFiltersDto
import com.flirttime.feature.main.matches.DiscoveryFiltersResponseData
import com.flirttime.feature.main.matches.DiscoverySearchRequest
import com.flirttime.feature.main.matches.DiscoverySearchResponseData
import com.flirttime.feature.main.matches.UserLimitsResponseData
import com.flirttime.feature.main.matches.WalletBalanceData
import com.flirttime.feature.moments.MomentCommentsResponseData
import com.flirttime.feature.moments.MomentGiftsCatalogResponseData
import com.flirttime.feature.moments.MomentLikeResponseData
import com.flirttime.feature.moments.MomentReceivedGiftsResponseData
import com.flirttime.feature.moments.MomentsFeedResponseData
import com.flirttime.feature.moments.PostCommentRequest
import com.flirttime.feature.moments.SendGiftResponseData
import com.flirttime.feature.moments.SendMomentGiftRequest
import com.flirttime.feature.moments.ToggleLikeRequest
import com.flirttime.feature.moments.UserMomentsResponseData
import com.flirttime.feature.profile.AudioIntroResponseData
import com.flirttime.feature.profile.DiscoveryActionRequest
import com.flirttime.feature.profile.DiscoveryActionResponseData
import com.flirttime.feature.profile.DiscoveryFilterOptionsData
import com.flirttime.feature.profile.DiscoveryPeopleResponseData
import com.flirttime.feature.profile.InterestsCatalogResponseData
import com.flirttime.feature.profile.MoreAboutMeResponseData
import com.flirttime.feature.profile.MyProfileEnvelopeData
import com.flirttime.feature.profile.NotificationCountsResponseData
import com.flirttime.feature.profile.OnboardingInterestsResponseData
import com.flirttime.feature.profile.OtherUserProfileResponseData
import com.flirttime.feature.profile.PresignedUrlResponseData
import com.flirttime.feature.profile.UpdateInterestsRequest
import com.flirttime.feature.profile.UpdateProfileRequest
import com.flirttime.feature.profile.UserPhotosResponseData
import com.flirttime.feature.quickmoments.CreateTextQuickMomentRequest
import com.flirttime.feature.quickmoments.MyQuickMomentsResponseData
import com.flirttime.feature.quickmoments.QuickMomentReactionRequest
import com.flirttime.feature.quickmoments.QuickMomentReplyRequest
import com.flirttime.feature.quickmoments.QuickMomentSlideDto
import com.flirttime.feature.quickmoments.QuickMomentsTrayResponseData
import com.flirttime.feature.quickmoments.QuickMomentViewersResponseData
import com.flirttime.feature.quickmoments.QuickMomentReactionsResponseData
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * MainApiService handles all network endpoints for features under `feature/main`
 */
interface MainApiService {

    // ==========================================
    // 1. MATCHES / DISCOVER SCREEN
    // ==========================================

    @POST("api/v1/discovery/search")
    suspend fun searchDiscoveryCards(
        @Body request: DiscoverySearchRequest
    ): ApiResponse<DiscoverySearchResponseData>

    @GET("api/v1/discovery/people")
    suspend fun getDiscoveryPeople(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 10,
        @Query("interestId") interestId: String? = null
    ): ApiResponse<DiscoveryPeopleResponseData>

    @POST("api/v1/discovery/actions")
    suspend fun submitDiscoveryAction(
        @Body request: DiscoveryActionRequest
    ): ApiResponse<DiscoveryActionResponseData>

    @POST("api/v1/connections/request")
    suspend fun sendConnectionRequest(
        @Body request: ConnectionRequest
    ): ApiResponse<ConnectionResponseData>

    @GET("api/v1/users/{userId}/limits")
    suspend fun getUserLimits(
        @Path("userId") userId: String
    ): ApiResponse<UserLimitsResponseData>

    @GET("api/v1/discovery/filters/options")
    suspend fun getDiscoveryFilterOptions(): ApiResponse<DiscoveryFilterOptionsData>

    @GET("api/v1/discovery/filters")
    suspend fun getDiscoveryFilters(): ApiResponse<DiscoveryFiltersResponseData>

    @PUT("api/v1/discovery/filters")
    suspend fun saveDiscoveryFilters(
        @Body filters: DiscoveryFiltersDto
    ): ApiResponse<Any>

    @POST("api/v1/discovery/filters/reset")
    suspend fun resetDiscoveryFilters(
        @Body emptyBody: Map<String, String> = emptyMap()
    ): ApiResponse<Any>

    @POST("api/v1/users/{userId}/block")
    suspend fun blockUser(
        @Path("userId") userId: String,
        @Body body: Map<String, String>
    ): ApiResponse<Any>

    @POST("api/v1/users/{userId}/report")
    suspend fun reportUser(
        @Path("userId") userId: String,
        @Body body: Map<String, Any?>
    ): ApiResponse<Any>

    @GET("api/v1/wallet/balance")
    suspend fun getWalletBalance(): ApiResponse<WalletBalanceData>

    @GET("api/v1/notifications/counts")
    suspend fun getNotificationCounts(): ApiResponse<NotificationCountsResponseData>

    @GET("api/v1/interests")
    suspend fun getDiscoveryInterests(): ApiResponse<InterestsCatalogResponseData>

    @GET("api/v1/quick-moments/tray")
    suspend fun getQuickMomentsTray(): ApiResponse<QuickMomentsTrayResponseData>


    // ==========================================
    // 2. EXPLORE SCREEN
    // ==========================================

    @GET("api/v1/interactions")
    suspend fun getInteractions(
        @Query("type") type: String,
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: String? = null
    ): ApiResponse<InteractionsResponseData>

    @GET("api/v1/interactions/counts")
    suspend fun getInteractionCounts(): ApiResponse<com.flirttime.feature.main.explore.InteractionCountsData>

    @POST("api/v1/interactions/{id}/read")
    suspend fun markInteractionRead(
        @Path("id") id: String
    ): ApiResponse<Any>


    // ==========================================
    // 3. MOMENTS SCREEN (Permanent Feed Posts)
    // ==========================================

    @GET("api/v1/moments")
    suspend fun getMomentsFeed(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 10,
        @Query("type") type: String = "feed"
    ): ApiResponse<MomentsFeedResponseData>

    @GET("api/v1/moments/me")
    suspend fun getMyMoments(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 4
    ): ApiResponse<UserMomentsResponseData>

    @GET("api/v1/users/{userId}/moments")
    suspend fun getUserMoments(
        @Path("userId") userId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<UserMomentsResponseData>

    @POST("api/v1/moments/{momentId}/like")
    suspend fun toggleLike(
        @Path("momentId") momentId: String,
        @Body request: ToggleLikeRequest? = null
    ): ApiResponse<MomentLikeResponseData>

    @DELETE("api/v1/moments/{momentId}")
    suspend fun deleteMoment(
        @Path("momentId") momentId: String
    ): ApiResponse<Any>

    @POST("api/v1/moments/{momentId}/report")
    suspend fun reportMoment(
        @Path("momentId") momentId: String,
        @Body body: Map<String, String>
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

    // Moments Comments
    @GET("api/v1/moments/{momentId}/comments")
    suspend fun getMomentComments(
        @Path("momentId") momentId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<MomentCommentsResponseData>

    @POST("api/v1/moments/{momentId}/comments")
    suspend fun postMomentComment(
        @Path("momentId") momentId: String,
        @Body request: PostCommentRequest
    ): ApiResponse<Any>

    // Moments Gifts
    @GET("api/v1/moments/{momentId}/gifts")
    suspend fun getMomentGiftsCatalog(
        @Path("momentId") momentId: String
    ): ApiResponse<MomentGiftsCatalogResponseData>

    @POST("api/v1/moments/{momentId}/gifts")
    suspend fun sendMomentGift(
        @Path("momentId") momentId: String,
        @Body request: SendMomentGiftRequest
    ): ApiResponse<SendGiftResponseData>

    @GET("api/v1/moments/{momentId}/gifts/received")
    suspend fun getReceivedGifts(
        @Path("momentId") momentId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): ApiResponse<MomentReceivedGiftsResponseData>


    // ==========================================
    // 4. QUICK MOMENTS (24h Ephemeral Stories)
    // ==========================================

    @GET("api/v1/quick-moments/me")
    suspend fun getMyQuickMoments(): ApiResponse<MyQuickMomentsResponseData>

    @GET("api/v1/quick-moments/user/{userId}")
    suspend fun getUserQuickMoments(
        @Path("userId") userId: String
    ): ApiResponse<MyQuickMomentsResponseData>

    @DELETE("api/v1/quick-moments/{momentId}")
    suspend fun deleteQuickMoment(
        @Path("momentId") momentId: String
    ): ApiResponse<Any>

    @POST("api/v1/quick-moments/{momentId}/react")
    suspend fun reactToQuickMoment(
        @Path("momentId") momentId: String,
        @Body request: QuickMomentReactionRequest
    ): ApiResponse<Any>

    @POST("api/v1/quick-moments/{momentId}/reply")
    suspend fun replyToQuickMoment(
        @Path("momentId") momentId: String,
        @Body request: QuickMomentReplyRequest
    ): ApiResponse<Any>

    @POST("api/v1/quick-moments/{momentId}/view")
    suspend fun markQuickMomentViewed(
        @Path("momentId") momentId: String
    ): ApiResponse<Any>

    @POST("api/v1/quick-moments")
    suspend fun createTextQuickMoment(
        @Body request: CreateTextQuickMomentRequest
    ): ApiResponse<QuickMomentSlideDto>

    @GET("api/v1/quick-moments/{momentId}/viewers")
    suspend fun getQuickMomentViewers(
        @Path("momentId") momentId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30
    ): ApiResponse<QuickMomentViewersResponseData>

    @GET("api/v1/quick-moments/{momentId}/reactions")
    suspend fun getQuickMomentReactions(
        @Path("momentId") momentId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): ApiResponse<QuickMomentReactionsResponseData>

    @Multipart
    @POST("api/v1/quick-moments")
    suspend fun createImageQuickMoment(
        @Part image: MultipartBody.Part,
        @Part("caption") caption: RequestBody? = null,
        @Part("locationLabel") locationLabel: RequestBody? = null
    ): ApiResponse<QuickMomentSlideDto>


    // ==========================================
    // 5. PROFILE SCREEN (Own Profile & Edit Profile)
    // ==========================================

    @GET("api/v1/users/profile")
    suspend fun getMyProfile(): ApiResponse<MyProfileEnvelopeData>

    @PATCH("api/v1/users/profile")
    suspend fun updateProfile(
        @Body body: UpdateProfileRequest
    ): ApiResponse<AuthUserDto>

    @PATCH("api/v1/users/profile")
    suspend fun updateProfileRaw(
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): ApiResponse<AuthUserDto>

    @GET("api/v1/users/{userId}/profile")
    suspend fun getOtherUserProfile(
        @Path("userId") userId: String
    ): ApiResponse<OtherUserProfileResponseData>

    // Profile Photos
    @GET("api/v1/onboarding/photos")
    suspend fun getOnboardingPhotos(): ApiResponse<UserPhotosResponseData>

    @Multipart
    @POST("api/v1/onboarding/photos")
    suspend fun uploadOnboardingPhotos(
        @Part photos: List<MultipartBody.Part>
    ): ApiResponse<UserPhotosResponseData>

    @Multipart
    @PUT("api/v1/users/photos")
    suspend fun updateGalleryPhotos(
        @Part photos: List<MultipartBody.Part>
    ): ApiResponse<UserPhotosResponseData>

    @PUT("api/v1/users/photos")
    suspend fun updateGalleryPhotoKeys(
        @Body body: Map<String, List<String>>
    ): ApiResponse<Any>

    @POST("api/v1/onboarding/profile-photo")
    suspend fun setProfilePhotoKey(
        @Body body: Map<String, String>
    ): ApiResponse<Any>

    @Multipart
    @POST("api/v1/onboarding/profile-photo")
    suspend fun uploadProfilePhoto(
        @Part profilePhoto: MultipartBody.Part
    ): ApiResponse<AuthUserDto>

    @DELETE("api/v1/onboarding/photos/{photoId}")
    suspend fun deleteOnboardingPhoto(
        @Path("photoId") photoId: String
    ): ApiResponse<Any>

    @PATCH("api/v1/onboarding/photos/{photoId}/primary")
    suspend fun setPrimaryPhoto(
        @Path("photoId") photoId: String
    ): ApiResponse<Any>

    @PUT("api/v1/onboarding/photos/{photoId}/primary")
    suspend fun setPrimaryPhotoPut(
        @Path("photoId") photoId: String
    ): ApiResponse<Any>

    // Profile Voice / Audio Intro
    @Multipart
    @POST("api/v1/onboarding/audio")
    suspend fun uploadAudioIntro(
        @Part audio: MultipartBody.Part,
        @Part("duration") duration: RequestBody
    ): ApiResponse<AudioIntroResponseData>

    @GET("api/v1/onboarding/audio")
    suspend fun getAudioIntro(): ApiResponse<AudioIntroResponseData>

    @DELETE("api/v1/onboarding/audio")
    suspend fun deleteAudioIntro(): ApiResponse<Any>

    // Profile Interests
    @GET("api/v1/onboarding/interests")
    suspend fun getSavedInterests(): ApiResponse<OnboardingInterestsResponseData>

    @PUT("api/v1/onboarding/interests")
    suspend fun updateInterestsPut(
        @Body body: UpdateInterestsRequest
    ): ApiResponse<Any>

    @POST("api/v1/onboarding/interests")
    suspend fun updateInterestsPost(
        @Body body: UpdateInterestsRequest
    ): ApiResponse<Any>

    @GET("api/v1/interests")
    suspend fun getInterestsCatalog(): ApiResponse<InterestsCatalogResponseData>

    // Profile More About Me
    @GET("api/v1/users/more-about-me")
    suspend fun getMoreAboutMe(): ApiResponse<MoreAboutMeResponseData>

    @PATCH("api/v1/users/more-about-me")
    suspend fun updateMoreAboutMe(
        @Body body: Map<String, String?>
    ): ApiResponse<MoreAboutMeResponseData>

    // Media Presigned URLs
    @POST("api/v1/media/presigned-url")
    suspend fun getPresignedUrl(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): ApiResponse<PresignedUrlResponseData>

    @POST("api/v1/media/upload-url")
    suspend fun getUploadUrlAlias(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): ApiResponse<PresignedUrlResponseData>
}
