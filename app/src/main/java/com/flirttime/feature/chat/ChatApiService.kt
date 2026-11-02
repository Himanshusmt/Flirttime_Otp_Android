package com.flirttime.feature.chat

import com.flirttime.feature.chat.model.ArchiveConversationRequest
import com.flirttime.feature.chat.model.BlockUserRequest
import com.flirttime.feature.chat.model.ChatActionResponseDto
import com.flirttime.feature.chat.model.ConversationsResponseDto
import com.flirttime.feature.chat.model.DeleteConversationRequest
import com.flirttime.feature.chat.model.DeleteMessageRequest
import com.flirttime.feature.chat.model.EditMessageRequest
import com.flirttime.feature.chat.model.ForwardMessagesRequest
import com.flirttime.feature.chat.model.ForwardMessagesResponseDto
import com.flirttime.feature.chat.model.ForwardRecipientsResponseDto
import com.flirttime.feature.chat.model.MediaUploadRequest
import com.flirttime.feature.chat.model.MediaUploadResponseDto
import com.flirttime.feature.chat.model.MessagesResponseDto
import com.flirttime.feature.chat.model.PinConversationRequest
import com.flirttime.feature.chat.model.PinMessageRequest
import com.flirttime.feature.chat.model.ReactionRequest
import com.flirttime.feature.chat.model.SendMessageRequest
import com.flirttime.feature.chat.model.SendMessageResponseDto
import com.flirttime.feature.chat.model.SimpleResponseDto
import com.flirttime.feature.chat.model.UpdateSettingsRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit contract for FlirtTime chat (`/api/v1/chats`).
 *
 * Responses are modelled as lenient envelopes instead of `ApiResponse<T>` because the
 * inbox payload has shipped in several shapes (`data.rows`, bare list, `chats`,
 * `conversations`) and a strict envelope would reject otherwise-valid responses.
 */
interface ChatApiService {

    @GET("api/v1/chats")
    suspend fun getConversations(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30,
        @Query("search") search: String? = null,
        @Query("archived") archived: Boolean? = null
    ): ConversationsResponseDto

    @GET("api/v1/chats/{conversationId}/messages")
    suspend fun getMessages(
        @Path("conversationId") conversationId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30,
        @Query("before") before: String? = null
    ): MessagesResponseDto

    @POST("api/v1/chats/{conversationId}/messages")
    suspend fun sendMessage(
        @Path("conversationId") conversationId: String,
        @Body body: SendMessageRequest
    ): SendMessageResponseDto

    @POST("api/v1/chats/{conversationId}/read")
    suspend fun markConversationRead(
        @Path("conversationId") conversationId: String,
        @Body body: Map<String, String>
    ): SimpleResponseDto

    /** Edits the text of an existing message (`PATCH` with `{ content }`). */
    @PATCH("api/v1/chats/{conversationId}/messages/{messageId}")
    suspend fun editMessage(
        @Path("conversationId") conversationId: String,
        @Path("messageId") messageId: String,
        @Body body: EditMessageRequest
    ): ChatActionResponseDto

    /** Soft-deletes for me or for everyone (`DELETE` with `{ deleteType }`). */
    @DELETE("api/v1/chats/{conversationId}/messages/{messageId}")
    suspend fun deleteMessage(
        @Path("conversationId") conversationId: String,
        @Path("messageId") messageId: String,
        @Body body: DeleteMessageRequest
    ): SimpleResponseDto

    /** Toggles the caller's reaction for one emoji. */
    @POST("api/v1/chats/{conversationId}/messages/{messageId}/reactions")
    suspend fun reactToMessage(
        @Path("conversationId") conversationId: String,
        @Path("messageId") messageId: String,
        @Body body: ReactionRequest
    ): ChatActionResponseDto

    /** Pins or unpins a single message for the caller. */
    @POST("api/v1/chats/{conversationId}/messages/{messageId}/pin")
    suspend fun pinMessage(
        @Path("conversationId") conversationId: String,
        @Path("messageId") messageId: String,
        @Body body: PinMessageRequest
    ): ChatActionResponseDto

    /** Forwards one or more messages to a set of recipients. */
    @POST("api/v1/chats/messages/forward")
    suspend fun forwardMessages(@Body body: ForwardMessagesRequest): ForwardMessagesResponseDto

    /** Recipient list for the forward picker. */
    @GET("api/v1/chats/forward/recipients")
    suspend fun getForwardRecipients(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("search") search: String? = null
    ): ForwardRecipientsResponseDto

    /** Per-user thread preferences: mute / pin / archive plus labels. */
    @PATCH("api/v1/chats/{conversationId}/settings")
    suspend fun updateConversationSettings(
        @Path("conversationId") conversationId: String,
        @Body body: UpdateSettingsRequest
    ): SimpleResponseDto

    /** Pins / unpins the whole thread in the caller's inbox. */
    @PATCH("api/v1/chats/{conversationId}/pin")
    suspend fun pinConversation(
        @Path("conversationId") conversationId: String,
        @Body body: PinConversationRequest
    ): SimpleResponseDto

    /** Primary archive route (`/chat/conversations/{id}/archive`). */
    @PATCH("api/v1/chat/conversations/{conversationId}/archive")
    suspend fun archiveConversation(
        @Path("conversationId") conversationId: String,
        @Body body: ArchiveConversationRequest
    ): SimpleResponseDto

    /** Archive route used by older builds (`/chats/{id}/archive`). */
    @PATCH("api/v1/chats/{conversationId}/archive")
    suspend fun archiveConversationAlt(
        @Path("conversationId") conversationId: String,
        @Body body: ArchiveConversationRequest
    ): SimpleResponseDto

    /** Flags the thread as unread again in the caller's inbox. */
    @POST("api/v1/chats/{conversationId}/unread")
    suspend fun markConversationUnread(
        @Path("conversationId") conversationId: String,
        @Body body: Map<String, String>
    ): SimpleResponseDto

    /** Deletes the thread for me (`deleteType = me`) or for everyone. */
    @DELETE("api/v1/chats/{conversationId}")
    suspend fun deleteConversation(
        @Path("conversationId") conversationId: String,
        @Body body: DeleteConversationRequest
    ): SimpleResponseDto

    @POST("api/v1/users/{userId}/block")
    suspend fun blockUser(
        @Path("userId") userId: String,
        @Body body: BlockUserRequest
    ): SimpleResponseDto

    @DELETE("api/v1/users/{userId}/block")
    suspend fun unblockUser(@Path("userId") userId: String): SimpleResponseDto

    /** Opens a presigned attachment upload session (`purpose = chat`). */
    @POST("api/v1/media/uploads")
    suspend fun createMediaUpload(@Body body: MediaUploadRequest): MediaUploadResponseDto
}
